package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.MapperCatalogEntry;
import org.sourceanalysis.app.analysis.discovery.MapperMethodCandidate;
import org.sourceanalysis.app.analysis.discovery.MapperStatementCandidate;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.FileSystemCanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.FileSystemCanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.RunStoreBootstrap;
import org.sourceanalysis.app.artifact.RunStoreHandle;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Test-only persisted graph fixture for Fact M1.
 *
 * <p>The fixture deliberately uses the current discovery and program-graph publishers. It is not
 * a JSON fixture and does not expose graph drafts to the Fact test. Two HTTP handlers each call a
 * different Java interface boundary, so the published data-flow graph has two distinct boundary
 * invocation nodes with disjoint entry ownership.
 */
public final class ProgramGraphsPublicFixture implements AutoCloseable {

  private static final String CONTROLLER_PATH = "src/main/java/com/example/OrderController.java";
  private static final String MAPPER_PATH = "src/main/java/com/example/OrderMapper.java";
  private static final String MAPPER_XML_PATH = "src/main/resources/mapper/OrderMapper.xml";
  private static final String SNAPSHOT_ID = "snapshot:" + digest("fact-two-entry-snapshot");

  private final RunStoreHandle handle;
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalAnalysisStepArtifactStore stepArtifacts;
  private final VerifiedSourceInventoryReference sourceInventory;
  private final ApplicationDiscoveryReference applicationDiscovery;
  private final ProgramGraphsReference programGraphs;
  private final VerifiedSourceTextReader sourceReader;
  private final CanonicalArtifactPolicyRegistry artifactPolicies;
  private final ArtifactControls artifactControls;

  private ProgramGraphsPublicFixture(
      RunStoreHandle handle,
      CanonicalModuleArtifactStore moduleArtifacts,
      CanonicalAnalysisStepArtifactStore stepArtifacts,
      VerifiedSourceInventoryReference sourceInventory,
      ApplicationDiscoveryReference applicationDiscovery,
      ProgramGraphsReference programGraphs,
      VerifiedSourceTextReader sourceReader,
      CanonicalArtifactPolicyRegistry artifactPolicies,
      ArtifactControls artifactControls) {
    this.handle = handle;
    this.moduleArtifacts = moduleArtifacts;
    this.stepArtifacts = stepArtifacts;
    this.sourceInventory = sourceInventory;
    this.applicationDiscovery = applicationDiscovery;
    this.programGraphs = programGraphs;
    this.sourceReader = sourceReader;
    this.artifactPolicies = artifactPolicies;
    this.artifactControls = artifactControls;
  }

  /** Creates one real canonical source/discovery/graph publication with two entries and bounds. */
  public static ProgramGraphsPublicFixture create(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, false);
  }

  /** Creates the persisted two-entry fixture with one real Java guard in {@code approve}. */
  public static ProgramGraphsPublicFixture createWithGuardedApprove(Path emptyTemporaryDirectory) {
    return create(emptyTemporaryDirectory, true);
  }

  private static ProgramGraphsPublicFixture create(
      Path emptyTemporaryDirectory, boolean guardedApprove) {
    createEmptyTestStoreDirectory(emptyTemporaryDirectory);
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    CanonicalArtifactPolicyRegistry policies = policies(canonicalJson);
    ArtifactControls controls = controls(policies);
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("fact-two-entry-run"));
    RunStoreHandle handle = RunStoreBootstrap.openForTest(emptyTemporaryDirectory);
    try {
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(
              handle,
              canonicalJson,
              policies,
              new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24));
      SourceMaterial source = source(controls, guardedApprove);
      List<CanonicalModulePayload> sourcePayloads = sourcePayloads(canonicalJson);
      InstalledModulePublication sourceModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 3, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  sourcePayloads));
      var sourceStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY),
                  new AnalysisStepPublisherModuleProvenance(sourceModule.reference()),
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(sourcePayloads),
                  null));

      List<CanonicalModulePayload> discoveryPayloads =
          discoveryPayloads(
              canonicalJson,
              controls,
              source,
              sourceArtifact(sourcePayloads, "source-inventory.jsonl"),
              sourceArtifact(sourcePayloads, "verified-snapshot.json"));
      InstalledModulePublication discoveryModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.APPLICATION_DISCOVERY, 4, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  discoveryPayloads));
      var discoveryStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(runId, AnalysisStepKey.APPLICATION_DISCOVERY),
                  new AnalysisStepPublisherModuleProvenance(discoveryModule.reference()),
                  List.of(sourceStep.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  toStepPayloads(discoveryPayloads),
                  null));

      VerifiedSourceInventoryReference sourceReference =
          new VerifiedSourceInventoryReference(sourceStep.reference());
      ApplicationDiscoveryReference discoveryReference =
          new ApplicationDiscoveryReference(discoveryStep.reference());
      ArtifactReference graphProfile = reference("graph-profile", "fact-two-entry-profile");
      ProgramGraphsReference graphReference =
          new ProgramGraphsExecution(
                  source.reader(), modules, steps)
              .execute(sourceReference, discoveryReference, graphProfile, controls);
      return new ProgramGraphsPublicFixture(
          handle,
          modules,
          steps,
          sourceReference,
          discoveryReference,
          graphReference,
          source.reader(),
          policies,
          controls);
    } catch (RuntimeException failure) {
      handle.close();
      throw failure;
    }
  }

  private static void createEmptyTestStoreDirectory(Path emptyTemporaryDirectory) {
    try {
      Files.createDirectory(emptyTemporaryDirectory);
    } catch (java.nio.file.FileAlreadyExistsException alreadyExists) {
      // The store bootstrap below verifies that an existing directory is empty and not a symlink.
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("cannot create test store directory", failure);
    }
  }

  public CanonicalAnalysisStepArtifactStore stepArtifacts() {
    return stepArtifacts;
  }

  /** Returns the exact module store that owns the fixture's persisted predecessor modules. */
  public CanonicalModuleArtifactStore moduleArtifacts() {
    return moduleArtifacts;
  }

  public VerifiedSourceInventoryReference sourceInventory() {
    return sourceInventory;
  }

  public ApplicationDiscoveryReference applicationDiscovery() {
    return applicationDiscovery;
  }

  public ProgramGraphsReference programGraphs() {
    return programGraphs;
  }

  public VerifiedSourceTextReader sourceReader() {
    return sourceReader;
  }

  /** Returns the exact policy registry used to publish every fixture predecessor. */
  public CanonicalArtifactPolicyRegistry artifactPolicies() {
    return artifactPolicies;
  }

  /** Returns the exact controls recorded by the fixture's source/discovery/graph receipts. */
  public ArtifactControls artifactControls() {
    return artifactControls;
  }

  /**
   * Reinstalls a complete public graph publication after a test-only JSON graph mutation.
   *
   * <p>The mutation receives only the five graph payloads, not Fact JSON or graph drafts. Source
   * and discovery are copied into a fresh run so the Fact reader must reopen the mutated public
   * publication through the same canonical stores as production.
   */
  public static PersistedGraphMutation republishMutatedGraphs(
      ProgramGraphsPublicFixture base,
      Path mutationRoot,
      BiFunction<List<CanonicalModulePayload>, CanonicalJsonCodec, List<CanonicalModulePayload>> mutation) {
    try {
      Files.createDirectory(mutationRoot);
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(json);
      ArtifactControls controls = controls(policies);
      RunStoreHandle handle = RunStoreBootstrap.openForTest(mutationRoot);
      ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, json, policies, limits);
      ReopenedAnalysisStepPublication source =
          base.stepArtifacts.reopen(base.sourceInventory.publication());
      ReopenedAnalysisStepPublication discovery =
          base.stepArtifacts.reopen(base.applicationDiscovery.publication());
      ReopenedAnalysisStepPublication graph =
          base.stepArtifacts.reopen(base.programGraphs.publication());
      AnalysisRunId runId =
          AnalysisRunId.parse("analysis-run:" + digest("fact-graph-mutation:" + mutationRoot));
      InstalledAnalysisStepPublication sourceStep =
          copyPublication(source, runId, 3, modules, steps, List.of(), controls);
      InstalledAnalysisStepPublication discoveryStep =
          copyPublication(
              discovery,
              runId,
              4,
              modules,
              steps,
              List.of(sourceStep.reference()),
              controls);
      List<CanonicalModulePayload> original = modulePayloads(graph.semanticPayloads());
      List<CanonicalModulePayload> changed =
          List.copyOf(mutation.apply(original, json));
      InstalledModulePublication graphModule =
          modules.install(
              new ModuleInstallRequest(
                  new AnalysisStepModuleAddress(
                      runId, AnalysisStepKey.PROGRAM_GRAPHS, 6, "publish"),
                  "v1",
                  List.of(),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  changed));
      InstalledAnalysisStepPublication graphStep =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(runId, AnalysisStepKey.PROGRAM_GRAPHS),
                  new AnalysisStepPublisherModuleProvenance(graphModule.reference()),
                  List.of(sourceStep.reference(), discoveryStep.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  changed.stream().map(ProgramGraphsPublicFixture::stepPayload).toList(),
                  null));
      return new PersistedGraphMutation(
          handle,
          steps,
          new VerifiedSourceInventoryReference(sourceStep.reference()),
          new ApplicationDiscoveryReference(discoveryStep.reference()),
          new ProgramGraphsReference(graphStep.reference()),
          base.sourceReader);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("cannot create persisted graph mutation fixture", failure);
    }
  }

  /**
   * Reinstalls a complete public discovery and graph publication after a test-only discovery
   * payload mutation.
   *
   * <p>The graph payloads are copied unchanged, but are published downstream of the mutated
   * discovery publication. This keeps the publication roots, controls, schemas, and graph
   * lineage valid while allowing a test to isolate a discovery-to-source reference mismatch.
   */
  public static PersistedGraphMutation republishMutatedDiscovery(
      ProgramGraphsPublicFixture base,
      Path mutationRoot,
      BiFunction<List<CanonicalModulePayload>, CanonicalJsonCodec, List<CanonicalModulePayload>> mutation) {
    try {
      Files.createDirectory(mutationRoot);
      CanonicalJsonCodec json = new CanonicalJsonCodec();
      CanonicalArtifactPolicyRegistry policies = policies(json);
      ArtifactControls controls = controls(policies);
      RunStoreHandle handle = RunStoreBootstrap.openForTest(mutationRoot);
      ArtifactStoreLimits limits = new ArtifactStoreLimits(16, 2_000_000, 8_000_000, 24);
      CanonicalModuleArtifactStore modules =
          new FileSystemCanonicalModuleArtifactStore(handle, json, policies, limits);
      CanonicalAnalysisStepArtifactStore steps =
          new FileSystemCanonicalAnalysisStepArtifactStore(handle, json, policies, limits);
      ReopenedAnalysisStepPublication source =
          base.stepArtifacts.reopen(base.sourceInventory.publication());
      ReopenedAnalysisStepPublication discovery =
          base.stepArtifacts.reopen(base.applicationDiscovery.publication());
      ReopenedAnalysisStepPublication graph =
          base.stepArtifacts.reopen(base.programGraphs.publication());
      AnalysisRunId runId =
          AnalysisRunId.parse("analysis-run:" + digest("fact-discovery-mutation:" + mutationRoot));
      InstalledAnalysisStepPublication sourceStep =
          copyPublication(source, runId, 3, modules, steps, List.of(), controls);
      List<CanonicalModulePayload> changedDiscovery =
          List.copyOf(mutation.apply(modulePayloads(discovery.semanticPayloads()), json));
      InstalledAnalysisStepPublication discoveryStep =
          copyPublication(
              runId,
              discovery.reference().address().analysisStepKey(),
              4,
              modules,
              steps,
              List.of(sourceStep.reference()),
              controls,
              changedDiscovery);
      InstalledAnalysisStepPublication graphStep =
          copyPublication(
              graph,
              runId,
              6,
              modules,
              steps,
              List.of(sourceStep.reference(), discoveryStep.reference()),
              controls);
      return new PersistedGraphMutation(
          handle,
          steps,
          new VerifiedSourceInventoryReference(sourceStep.reference()),
          new ApplicationDiscoveryReference(discoveryStep.reference()),
          new ProgramGraphsReference(graphStep.reference()),
          base.sourceReader);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("cannot create persisted discovery mutation fixture", failure);
    }
  }

  /** Rebuilds a standalone graph JSON payload after a test mutation. */
  public static CanonicalModulePayload rebuildStandaloneGraphPayload(
      CanonicalModulePayload original, ObjectNode document, CanonicalJsonCodec json) {
    String prefix =
        original.artifactId().value().substring(0, original.artifactId().value().lastIndexOf(':'));
    ObjectNode withoutId = document.deepCopy();
    withoutId.remove("artifactId");
    String artifactId =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(original.schemaVersion()),
                    frame(original.artifactType()),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    document.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        original.fileName(),
        original.artifactType(),
        original.schemaVersion(),
        ArtifactId.parse(artifactId),
        original.mediaType(),
        json.encodeCanonical(document));
  }

  /** Returns a new index payload whose graph references match the supplied graph payloads. */
  public static CanonicalModulePayload rebuildGraphIndex(
      CanonicalModulePayload originalIndex,
      List<CanonicalModulePayload> graphPayloads,
      CanonicalJsonCodec json) {
    ObjectNode document = (ObjectNode) json.parseCanonical(originalIndex.canonicalUtf8());
    for (JsonNode descriptor : document.path("graphs")) {
      String fileName = descriptor.path("fileName").asText();
      graphPayloads.stream()
          .filter(payload -> payload.fileName().equals(fileName))
          .findFirst()
          .ifPresent(
              payload -> {
                ObjectNode reference = (ObjectNode) descriptor.path("artifactRef");
                reference.put("artifactId", payload.artifactId().value());
                reference.put("sha256", digest(payload.canonicalUtf8().copyToByteArray()));
              });
    }
    return rebuildStandaloneGraphPayload(originalIndex, document, json);
  }

  private static InstalledAnalysisStepPublication copyPublication(
      ReopenedAnalysisStepPublication original,
      AnalysisRunId runId,
      int moduleNumber,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      List<org.sourceanalysis.app.artifact.AnalysisStepPublicationReference> upstream,
      ArtifactControls controls) {
    return copyPublication(
        runId,
        original.reference().address().analysisStepKey(),
        moduleNumber,
        modules,
        steps,
        upstream,
        controls,
        modulePayloads(original.semanticPayloads()));
  }

  private static InstalledAnalysisStepPublication copyPublication(
      AnalysisRunId runId,
      AnalysisStepKey stepKey,
      int moduleNumber,
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      List<org.sourceanalysis.app.artifact.AnalysisStepPublicationReference> upstream,
      ArtifactControls controls,
      List<CanonicalModulePayload> payloads) {
    InstalledModulePublication module =
        modules.install(
            new ModuleInstallRequest(
                new AnalysisStepModuleAddress(runId, stepKey, moduleNumber, "publish"),
                "v1",
                List.of(),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                payloads));
    return steps.install(
        new AnalysisStepInstallRequest(
            new AnalysisStepPublicationAddress(runId, stepKey),
            new AnalysisStepPublisherModuleProvenance(module.reference()),
            upstream,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            List.of(),
            payloads.stream().map(ProgramGraphsPublicFixture::stepPayload).toList(),
            null));
  }

  private static List<CanonicalModulePayload> modulePayloads(
      List<org.sourceanalysis.app.artifact.VerifiedCanonicalPayload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                new CanonicalModulePayload(
                    payload.descriptor().fileName(),
                    payload.descriptor().artifactType(),
                    payload.descriptor().schemaVersion(),
                    payload.descriptor().artifactId(),
                    payload.descriptor().mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static CanonicalAnalysisStepPayload stepPayload(CanonicalModulePayload payload) {
    return new CanonicalAnalysisStepPayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  /** Handles the fresh store and references produced by {@link #republishMutatedGraphs}. */
  public static final class PersistedGraphMutation implements AutoCloseable {
    private final RunStoreHandle handle;
    private final CanonicalAnalysisStepArtifactStore steps;
    private final VerifiedSourceInventoryReference source;
    private final ApplicationDiscoveryReference discovery;
    private final ProgramGraphsReference graphs;
    private final VerifiedSourceTextReader sourceReader;

    private PersistedGraphMutation(
        RunStoreHandle handle,
        CanonicalAnalysisStepArtifactStore steps,
        VerifiedSourceInventoryReference source,
        ApplicationDiscoveryReference discovery,
        ProgramGraphsReference graphs,
        VerifiedSourceTextReader sourceReader) {
      this.handle = handle;
      this.steps = steps;
      this.source = source;
      this.discovery = discovery;
      this.graphs = graphs;
      this.sourceReader = sourceReader;
    }

    public CanonicalAnalysisStepArtifactStore steps() {
      return steps;
    }

    public VerifiedSourceInventoryReference source() {
      return source;
    }

    public ApplicationDiscoveryReference discovery() {
      return discovery;
    }

    public ProgramGraphsReference graphs() {
      return graphs;
    }

    public VerifiedSourceTextReader sourceReader() {
      return sourceReader;
    }

    @Override
    public void close() {
      handle.close();
    }
  }

  @Override
  public void close() {
    handle.close();
  }

  private static SourceMaterial source(ArtifactControls controls, boolean guardedApprove) {
    String controller =
        guardedApprove
            ? """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            orderService.approve(status);
          }

          void cancel(String status) {
            orderService.cancel(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;
          private final CancellationClient cancellationClient = null;

          void approve(String status) {
            if (status == null) {
              return;
            }
            approvalClient.record(status);
          }

          void cancel(String status) {
            cancellationClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }

        interface CancellationClient {
          void record(String status);
        }
        """
            : """
        package com.example;

        class OrderController {
          private final OrderService orderService = new OrderService();

          void approve(String status) {
            %s
            orderService.approve(status);
          }

          void cancel(String status) {
            orderService.cancel(status);
          }
        }

        class OrderService {
          private final ApprovalClient approvalClient = null;
          private final CancellationClient cancellationClient = null;

          void approve(String status) {
            approvalClient.record(status);
          }

          void cancel(String status) {
            cancellationClient.record(status);
          }
        }

        interface ApprovalClient {
          void record(String status);
        }

        interface CancellationClient {
          void record(String status);
        }
        """.formatted(guardedApprove ? "if (status == null) { return; }" : "");
    String mapper =
        """
        package com.example;

        interface OrderMapper {
          void noop(String status);
        }
        """;
    String mapperXml =
        """
        <?xml version="1.0" encoding="UTF-8" ?>
        <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
        <mapper namespace="com.example.OrderMapper">
          <update id="noop">
            UPDATE orders SET status = #{status}
          </update>
        </mapper>
        """;
    Map<String, String> documents =
        Map.of(CONTROLLER_PATH, controller, MAPPER_PATH, mapper, MAPPER_XML_PATH, mapperXml);
    List<VerifiedSourceTextDocument> verifiedDocuments =
        documents.entrySet().stream()
            .map(entry -> verifiedDocument(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(VerifiedSourceTextDocument::path))
            .toList();
    VerifiedSourceTextSet verifiedSource =
        new VerifiedSourceTextSet(
            SNAPSHOT_ID,
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", "fact-two-entry-capability"),
            reference("source-inventory", "fact-two-entry-inventory"),
            reference("verified-snapshot", "fact-two-entry-snapshot"),
            controls,
            verifiedDocuments);
    List<HttpEntryPoint> entries =
        List.of(
            entry(
                "approve",
                "/orders/approve",
                "approve",
                excerpt(documents, CONTROLLER_PATH, "class OrderController"),
                excerpt(documents, CONTROLLER_PATH, "void approve")),
            entry(
                "cancel",
                "/orders/cancel",
                "cancel",
                excerpt(documents, CONTROLLER_PATH, "class OrderController"),
                excerpt(documents, CONTROLLER_PATH, "void cancel")));
    MapperCatalogEntry mapperCatalog =
        new MapperCatalogEntry(
            id("mapper-catalog-entry", "order"),
            "com.example.OrderMapper",
            List.of(
                new MapperMethodCandidate(
                    id("mapper-method", "order-noop"),
                    "noop(java.lang.String)",
                    excerpt(documents, MAPPER_PATH, "void noop(String status);"))),
            MAPPER_XML_PATH,
            "com.example.OrderMapper",
            List.of(
                new MapperStatementCandidate(
                    id("mapper-statement", "order-noop"),
                    "noop",
                    "update",
                    excerpt(documents, MAPPER_XML_PATH, "id=\"noop\""))),
            "CANDIDATE_NOT_YET_BOUND");
    return new SourceMaterial(verifiedSource, entries, mapperCatalog, documents);
  }

  private static VerifiedSourceTextDocument verifiedDocument(String path, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return new VerifiedSourceTextDocument(
        id("file", path),
        path,
        "100644",
        "text/plain",
        bytes.length,
        new Sha256Digest(digest(bytes)),
        ImmutableBytes.copyOf(bytes));
  }

  private static HttpEntryPoint entry(
      String key,
      String route,
      String method,
      SourceExcerptV1 classExcerpt,
      SourceExcerptV1 methodExcerpt) {
    return new HttpEntryPoint(
        id("entry", key),
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "POST",
        route,
        List.of("/orders", "/" + method),
        "com.example.OrderController#" + method,
        List.of("status"),
        List.of(classExcerpt, methodExcerpt));
  }

  private static List<CanonicalModulePayload> sourcePayloads(CanonicalJsonCodec json) {
    return List.of(
        standalonePayload(
            json,
            "source-input.json",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT",
            "verified-source-inventory-source-input-v2",
            "verified-source-inventory-source-input"),
        jsonlPayload(
            json,
            "source-inventory.jsonl",
            "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY",
            "verified-source-inventory-source-inventory-v2",
            "verified-source-inventory-source-inventory",
            List.of(JsonNodeFactory.instance.objectNode())),
        standalonePayload(
            json,
            "verified-snapshot.json",
            "VERIFIED_SNAPSHOT",
            "verified-snapshot-v2",
            "verified-snapshot"));
  }

  private static List<CanonicalModulePayload> discoveryPayloads(
      CanonicalJsonCodec json,
      ArtifactControls controls,
      SourceMaterial source,
      ArtifactReference sourceInventory,
      ArtifactReference verifiedSnapshot) {
    ArtifactId applicationProfileId = id("application-profile", "fact-two-entry");
    ObjectNode profile = JsonNodeFactory.instance.objectNode();
    profile.put("schemaVersion", "application-discovery-application-profile-v2");
    profile.put("artifactType", "APPLICATION_DISCOVERY_APPLICATION_PROFILE");
    profile.put("applicationProfileId", applicationProfileId.value());
    profile.put("snapshotId", SNAPSHOT_ID);
    profile.put("inventoryScopeKind", "COMPLETE_CAPTURE");
    profile.put("repositoryCompletionEligible", true);
    profile.put("language", "JAVA");
    profile.putNull("languageVersion");
    profile.putArray("frameworkSignals");
    profile.putArray("configSignals");
    profile.set("capabilityProfileRef", referenceNode(reference("capability-profile", "fact-two-entry-capability")));
    profile.set("sourceInventoryRef", referenceNode(sourceInventory));
    profile.set("verifiedSnapshotRef", referenceNode(verifiedSnapshot));
    profile.set("controls", controlsNode(controls));
    List<ObjectNode> entryLines = source.entries().stream().map(ProgramGraphsPublicFixture::entryNode).toList();
    ObjectNode capability = JsonNodeFactory.instance.objectNode();
    capability.put("schemaVersion", "application-discovery-capability-report-v2");
    capability.put("artifactType", "APPLICATION_DISCOVERY_CAPABILITY_REPORT");
    capability.put("applicationProfileId", applicationProfileId.value());
    ObjectNode coverage = capability.putObject("repositoryEntryCoverage");
    ArrayNode entryIds = coverage.putArray("entryIds");
    source.entries().stream().map(HttpEntryPoint::entryId).map(ArtifactId::value).sorted().forEach(entryIds::add);
    coverage.putArray("mapperCatalogEntryIds").add(source.mapperCatalog().catalogEntryId().value());
    coverage.put("entryCount", source.entries().size());
    coverage.put("mapperCatalogEntryCount", 1);
    return List.of(
        standaloneBody(
            json,
            "application-profile.json",
            "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
            "application-discovery-application-profile-v2",
            "application-profile",
            profile),
        standaloneBody(
            json,
            "capability-report.json",
            "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
            "application-discovery-capability-report-v2",
            "capability-report",
            capability),
        jsonlBody(
            json,
            "entry-points.jsonl",
            "APPLICATION_DISCOVERY_ENTRY_POINTS",
            "application-discovery-entry-points-v2",
            "entry-points",
            entryLines),
        jsonlBody(
            json,
            "mapper-catalog.jsonl",
            "APPLICATION_DISCOVERY_MAPPER_CATALOG",
            "application-discovery-mapper-catalog-v2",
            "mapper-catalog",
            List.of(mapperNode(source.mapperCatalog()))));
  }

  private static ObjectNode entryNode(HttpEntryPoint value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("entryId", value.entryId().value());
    result.put("kind", value.kind().name());
    result.put("protocol", value.protocol());
    result.put("method", value.method());
    result.put("route", value.route());
    strings(result.putArray("routeParts"), value.routeParts());
    result.put("handlerFqn", value.handlerFqn());
    strings(result.putArray("parameterNames"), value.parameterNames());
    ArrayNode excerpts = result.putArray("routeSourceExcerpts");
    value.routeSourceExcerpts().forEach(valueExcerpt -> excerpts.add(excerptNode(valueExcerpt)));
    return result;
  }

  private static ObjectNode mapperNode(MapperCatalogEntry value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("catalogEntryId", value.catalogEntryId().value());
    result.put("javaInterfaceFqn", value.javaInterfaceFqn());
    ArrayNode methods = result.putArray("javaMethodCandidates");
    value.javaMethodCandidates().forEach(candidate -> methods.add(methodNode(candidate)));
    result.put("xmlResourcePath", value.xmlResourcePath());
    result.put("xmlNamespace", value.xmlNamespace());
    ArrayNode statements = result.putArray("xmlStatementCandidates");
    value.xmlStatementCandidates().forEach(candidate -> statements.add(statementNode(candidate)));
    result.put("bindingState", value.bindingState());
    return result;
  }

  private static ObjectNode methodNode(MapperMethodCandidate value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("methodCandidateId", value.methodCandidateId().value());
    result.put("signature", value.signature());
    result.set("declarationExcerpt", excerptNode(value.declarationExcerpt()));
    return result;
  }

  private static ObjectNode statementNode(MapperStatementCandidate value) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("statementCandidateId", value.statementCandidateId().value());
    result.put("statementId", value.statementId());
    result.put("statementKind", value.statementKind());
    result.set("declarationExcerpt", excerptNode(value.declarationExcerpt()));
    return result;
  }

  private static ObjectNode excerptNode(SourceExcerptV1 value) {
    SourceLocatorV1 locator = value.locator();
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    ObjectNode location = result.putObject("locator");
    location.put("fileId", locator.fileId().value());
    location.put("path", locator.path());
    location.put("startByte", locator.startByte());
    location.put("endByteExclusive", locator.endByteExclusive());
    location.put("startLine", locator.startLine());
    location.put("startColumn", locator.startColumn());
    location.put("endLine", locator.endLine());
    location.put("endColumn", locator.endColumn());
    result.put("rawUtf8", new String(value.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    result.put("rawUtf8Sha256", value.rawUtf8Sha256().value());
    return result;
  }

  private static CanonicalModulePayload standalonePayload(
      CanonicalJsonCodec json, String fileName, String type, String schema, String prefix) {
    return standaloneBody(json, fileName, type, schema, prefix, JsonNodeFactory.instance.objectNode());
  }

  private static CanonicalModulePayload standaloneBody(
      CanonicalJsonCodec json,
      String fileName,
      String type,
      String schema,
      String prefix,
      ObjectNode body) {
    ObjectNode withoutId = body.deepCopy();
    withoutId.put("schemaVersion", schema);
    withoutId.put("artifactType", type);
    String artifactId =
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schema),
                    frame(type),
                    frame(json.encodeCanonical(withoutId).copyToByteArray())));
    withoutId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(withoutId));
  }

  private static CanonicalModulePayload jsonlPayload(
      CanonicalJsonCodec json,
      String fileName,
      String type,
      String schema,
      String prefix,
      List<ObjectNode> rawLines) {
    return jsonlBody(json, fileName, type, schema, prefix, rawLines);
  }

  private static CanonicalModulePayload jsonlBody(
      CanonicalJsonCodec json,
      String fileName,
      String type,
      String schema,
      String prefix,
      List<ObjectNode> rawLines) {
    List<ObjectNode> lines = new ArrayList<>();
    for (ObjectNode raw : rawLines) {
      ObjectNode line = raw.deepCopy();
      line.put("schemaVersion", schema);
      line.put("artifactType", type);
      lines.add(line);
    }
    byte[] bytes =
        lines.stream()
            .sorted(Comparator.comparing(value -> value.toString()))
            .map(value -> json.encodeCanonical(value).copyToByteArray())
            .reduce(
                new byte[0],
                (left, right) -> concatenate(concatenate(left, right), "\n".getBytes(StandardCharsets.UTF_8)));
    String artifactId =
        prefix
            + ":"
            + digest(concatenate(frame("canonical-jsonl-artifact-id-v1"), frame(schema), frame(type), frame(bytes)));
    return new CanonicalModulePayload(
        fileName,
        type,
        schema,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference sourceArtifact(List<CanonicalModulePayload> payloads, String fileName) {
    CanonicalModulePayload payload =
        payloads.stream().filter(value -> value.fileName().equals(fileName)).findFirst().orElseThrow();
    return new ArtifactReference(payload.artifactId(), new Sha256Digest(digest(payload.canonicalUtf8().copyToByteArray())));
  }

  private static List<CanonicalAnalysisStepPayload> toStepPayloads(List<CanonicalModulePayload> payloads) {
    return payloads.stream()
        .map(
            payload ->
                new CanonicalAnalysisStepPayload(
                    payload.fileName(),
                    payload.artifactType(),
                    payload.schemaVersion(),
                    payload.artifactId(),
                    payload.mediaType(),
                    payload.canonicalUtf8()))
        .toList();
  }

  private static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec json) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", "artifact-policy-registry-v2");
    ArrayNode entries = document.putArray("policies");
    policy(entries, "APPLICATION_DISCOVERY_APPLICATION_PROFILE", "application-discovery-application-profile-v2", "application-profile", "application/json", "STANDALONE_JSON", false);
    policy(entries, "APPLICATION_DISCOVERY_CAPABILITY_REPORT", "application-discovery-capability-report-v2", "capability-report", "application/json", "STANDALONE_JSON", false);
    policy(entries, "APPLICATION_DISCOVERY_ENTRY_POINTS", "application-discovery-entry-points-v2", "entry-points", "application/x-ndjson", "CANONICAL_JSONL", true);
    policy(entries, "APPLICATION_DISCOVERY_MAPPER_CATALOG", "application-discovery-mapper-catalog-v2", "mapper-catalog", "application/x-ndjson", "CANONICAL_JSONL", false);
    policy(entries, "PROGRAM_GRAPHS_CALL_GRAPH", "program-graphs-call-graph-v1", "program-graphs-call-graph", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT", CallGraphDraft.SCHEMA_VERSION, "call-graph", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_CODE_STRUCTURE_GRAPH", "program-graphs-code-structure-graph-v1", "program-graphs-code-structure-graph", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_CODE_STRUCTURE_DRAFT", CodeStructureGraphDraft.SCHEMA_VERSION, "code-structure-graph", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_CONTROL_FLOW_GRAPH", "program-graphs-control-flow-graph-v2", "program-graphs-control-flow-graph", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_CONTROL_FLOW_DRAFT", ControlFlowGraphDraft.SCHEMA_VERSION, "control-flow-graph", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_DATA_FLOW_GRAPH", "program-graphs-data-flow-graph-v2", "program-graphs-data-flow-graph", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_DATA_FLOW_DRAFT", DataFlowGraphDraft.SCHEMA_VERSION, "data-flow-graph", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_EVIDENCE_GRAPH", "program-graphs-evidence-graph-v3", "program-graphs-evidence-graph", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_EVIDENCE_GRAPH_DRAFT", EvidenceGraphDraft.SCHEMA_VERSION, "evidence-graph", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROGRAM_GRAPHS_GRAPH_GAP", "program-graphs-graph-gap-v1", "program-graphs-graph-gaps", "application/x-ndjson", "CANONICAL_JSONL", true);
    policy(entries, "PROGRAM_GRAPHS_GRAPH_INDEX", "program-graphs-graph-index-v2", "program-graphs-graph-index", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET", "proven-code-facts-fact-candidate-set-v2", "proven-code-facts-fact-candidate-set", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROVEN_CODE_FACTS_FACT_ACCOUNTING", "proven-code-facts-fact-accounting-v2", "proven-code-facts-fact-accounting", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROVEN_CODE_FACTS_GAP_LEDGER", "proven-code-facts-gap-ledger-v2", "proven-code-facts-gap-ledger", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROVEN_CODE_FACTS_PROOF_PACK", "proven-code-facts-proof-pack-v2", "proven-code-facts-proof-pack", "application/json", "STANDALONE_JSON", false);
    policy(entries, "PROVEN_CODE_FACTS_PROOF_DECISION_SET", "proven-code-facts-proof-decision-set-v2", "proven-code-facts-proof-decision-set", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "PROVEN_CODE_FACTS_PROVEN_FACTS", "proven-code-facts-proven-facts-v2", "proven-code-facts-proven-facts", "application/json", "STANDALONE_JSON", false);
    policy(entries, "BUSINESS_FLOWS_FLOW_COMPILATION", "business-flows-flow-compilation-v1", "business-flows-flow-compilation", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "BUSINESS_FLOWS_CAPSULE_PROJECTION", "business-flows-capsule-projection-v4", "business-flows-capsule-projection", "application/json", "MODULE_ARTIFACT_JSON", false);
    policy(entries, "BUSINESS_FLOWS_FLOW_SLICES", "business-flows-flow-slices-v1", "business-flows-flow-slices", "application/json", "STANDALONE_JSON", false);
    policy(entries, "BUSINESS_FLOWS_FLOW_COVERAGE", "business-flows-flow-coverage-v1", "business-flows-flow-coverage", "application/json", "STANDALONE_JSON", false);
    policy(entries, "BUSINESS_FLOWS_ENTRY_DISPOSITION", "business-flows-entry-disposition-v1", "business-flows-entry-disposition", "application/x-ndjson", "CANONICAL_JSONL", true);
    policy(entries, "BUSINESS_FLOWS_EVIDENCE_CAPSULE", "business-flows-evidence-capsule-v1", "business-flows-evidence-capsule", "application/x-ndjson", "CANONICAL_JSONL", true);
    policy(entries, "BUSINESS_FLOWS_FLOW_GAP", "business-flows-flow-gap-v1", "business-flows-flow-gap", "application/x-ndjson", "CANONICAL_JSONL", true);
    policy(entries, "VERIFIED_SNAPSHOT", "verified-snapshot-v2", "verified-snapshot", "application/json", "STANDALONE_JSON", false);
    policy(entries, "VERIFIED_SOURCE_INVENTORY_SOURCE_INPUT", "verified-source-inventory-source-input-v2", "verified-source-inventory-source-input", "application/json", "STANDALONE_JSON", false);
    policy(entries, "VERIFIED_SOURCE_INVENTORY_SOURCE_INVENTORY", "verified-source-inventory-source-inventory-v2", "verified-source-inventory-source-inventory", "application/x-ndjson", "CANONICAL_JSONL", false);
    List<ObjectNode> ordered = new ArrayList<>();
    entries.forEach(value -> ordered.add((ObjectNode) value));
    ordered.sort(Comparator.comparing(value -> value.get("artifactType").textValue()));
    entries.removeAll();
    ordered.forEach(entries::add);
    document.put("artifactPolicyRegistryId", "artifact-policy-registry:" + digest(concatenate(frame("canonical-artifact-policy-registry-id-v2"), frame(json.encodeCanonical(document).copyToByteArray()))));
    return CanonicalArtifactPolicyRegistry.load(json.encodeCanonical(document), json);
  }

  private static void policy(ArrayNode entries, String type, String schema, String prefix, String media, String envelope, boolean emptyJsonl) {
    entries.addObject().put("artifactType", type).put("schemaVersion", schema).put("artifactIdPrefix", prefix).put("mediaType", media).put("envelopeKind", envelope).put("emptyJsonlAllowed", emptyJsonl).put("publicContentExposure", "PATH_FREE_COMPLETE_UTF8");
  }

  private static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
    return new ArtifactControls(new Sha256Digest(digest("toolchain")), new Sha256Digest(digest("profile")), new Sha256Digest(digest("schema")), null, policies.reference());
  }

  private static ObjectNode controlsNode(ArtifactControls values) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("toolchainSha256", values.toolchainSha256().value());
    result.put("profileSha256", values.profileSha256().value());
    result.put("schemaBundleSha256", values.schemaBundleSha256().value());
    result.putNull("promptBundleSha256");
    result.putObject("artifactPolicyRegistryRef").put("artifactId", values.artifactPolicyRegistryRef().artifactId().value()).put("sha256", values.artifactPolicyRegistryRef().sha256().value());
    return result;
  }

  private static ObjectNode referenceNode(ArtifactReference value) {
    return JsonNodeFactory.instance.objectNode().put("artifactId", value.artifactId().value()).put("sha256", value.sha256().value());
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + digest(value)), new Sha256Digest(digest(value)));
  }

  private static SourceExcerptV1 excerpt(Map<String, String> documents, String path, String token) {
    String source = documents.get(path);
    int startCharacter = source.indexOf(token);
    if (startCharacter < 0) throw new IllegalArgumentException("fixture token is absent: " + token);
    long startByte = source.substring(0, startCharacter).getBytes(StandardCharsets.UTF_8).length;
    int startLine = 1 + (int) source.substring(0, startCharacter).chars().filter(character -> character == '\n').count();
    int lineStart = source.lastIndexOf('\n', startCharacter - 1) + 1;
    int startColumn = startCharacter - lineStart + 1;
    byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(new SourceLocatorV1(id("file", path), path, startByte, startByte + bytes.length, startLine, startColumn, startLine, startColumn + token.length()), ImmutableBytes.copyOf(bytes), new Sha256Digest(digest(bytes)));
  }

  private static void strings(ArrayNode target, List<String> values) {
    values.forEach(target::add);
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length).order(ByteOrder.BIG_ENDIAN).putLong(value.length).put(value).array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) length += value.length;
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private record SourceMaterial(
      VerifiedSourceTextSet verifiedSource,
      List<HttpEntryPoint> entries,
      MapperCatalogEntry mapperCatalog,
      Map<String, String> documents) {

    private SourceMaterial {
      entries = List.copyOf(entries);
      documents = Map.copyOf(documents);
    }

    private VerifiedSourceTextReader reader() {
      return ignored -> verifiedSource;
    }
  }
}
