package org.sourceanalysis.app.analysis.material.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndexModulePublisher;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.EntryEvidenceSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepPayload;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/**
 * Receipt-last publisher for the bounded Step05 entry-evidence directory.
 *
 * <p>It owns only the new v3 module key. Historical code-reading-material packets keep their
 * existing publisher and readers.
 */
public final class EntryEvidencePublisher {

  public static final String MODULE_KEY = "entry-evidence";
  public static final String MODULE_VERSION = "v3";
  public static final String PRODUCER = "entry-evidence-v1";
  public static final String ENTRY_FILE_PREFIX = "entry-";
  public static final String ENTRY_TYPE = "ENTRY_EVIDENCE";
  public static final String ENTRY_SCHEMA = "entry-evidence-v1";
  public static final String INDEX_FILE = "entry-evidence-index.json";
  public static final String INDEX_TYPE = "ENTRY_EVIDENCE_INDEX";
  public static final String INDEX_SCHEMA = "entry-evidence-index-v1";
  public static final String COVERAGE_FILE = "frontend-coverage.jsonl";
  public static final String COVERAGE_TYPE = "FRONTEND_EVIDENCE_COVERAGE";
  public static final String COVERAGE_SCHEMA = "frontend-evidence-coverage-v1";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER = EntryEvidencePublisher::compareUtf8;

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalAnalysisStepArtifactStore sourceSteps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public EntryEvidencePublisher(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      CanonicalAnalysisStepArtifactStore sourceSteps) {
    this.modules = Objects.requireNonNull(modules, "entry-evidence module store");
    this.steps = Objects.requireNonNull(steps, "entry-evidence analysis-step store");
    this.sourceSteps =
        Objects.requireNonNull(sourceSteps, "entry-evidence source analysis-step store");
  }

  /**
   * Installs an R4-owned entry-evidence set after proving its exact R0/R1/R2/R3 predecessor chain.
   */
  public AnalysisStepPublicationReference publishTechnicalV3(
      AnalysisRunId destinationRun,
      VerifiedSourceInventoryReference source,
      SelectedSourceBasis sourceBasis,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistence,
      ModulePublicationReference frontend,
      ArtifactControls frontendControls,
      ArtifactControls backendControls,
      ArtifactControls persistenceControls,
      ArtifactControls r4Controls,
      EntryEvidenceSet set) {
    try {
      Objects.requireNonNull(destinationRun, "R4 destination run");
      Objects.requireNonNull(source, "R0 source inventory");
      Objects.requireNonNull(sourceBasis, "R0 selected source basis");
      Objects.requireNonNull(discovery, "R2 application discovery");
      Objects.requireNonNull(navigation, "R2 Java navigation");
      Objects.requireNonNull(persistence, "R3 persistence publication");
      Objects.requireNonNull(frontend, "R1 frontend publication");
      Objects.requireNonNull(frontendControls, "frontend R1 controls");
      Objects.requireNonNull(backendControls, "R2 controls");
      Objects.requireNonNull(persistenceControls, "R3 controls");
      Objects.requireNonNull(r4Controls, "R4 controls");
      Objects.requireNonNull(set, "entry-evidence set");
      if (!set.header().sourceInventory().equals(source)
          || !set.header().applicationDiscovery().equals(discovery)
          || !set.header().navigationPublication().equals(navigation)
          || !set.header().persistencePublication().equals(persistence)
          || !set.header().frontendPublication().equals(frontend)) {
        throw invalid();
      }
      if (sourceBasis.kind() != SelectedSourceBasis.Kind.PREPARED_V1
          || !sourceBasis.preparedSource().publication().equals(source.publication())
          || !sourceBasis.snapshotId().value().equals(set.header().sourceSnapshotId())) {
        throw invalid();
      }
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, sourceSteps);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY, steps);
      ReopenedAnalysisStepPublication navigationStep =
          reopen(navigation.publication(), AnalysisStepKey.PROGRAM_GRAPHS, steps);
      ReopenedAnalysisStepPublication persistenceStep =
          reopen(persistence, AnalysisStepKey.PROVEN_CODE_FACTS, steps);
      ReopenedModulePublication frontendModule = modules.reopen(frontend);
      if (!(frontend.address() instanceof AnalysisStepModuleAddress frontendAddress)) {
        throw invalid();
      }
      // Do not let a caller combine an otherwise well-shaped module-6 receipt with a different
      // prepared R0. This strict reopen proves the saved frontend header and its upstream
      // artifacts against the exact selected basis before R4 can cite it.
      FrontendHttpIndex frontendIndex =
          new FrontendHttpIndexModulePublisher(modules)
              .reopenV2(frontend, frontendAddress.runId(), sourceBasis, frontendControls);
      requireFrontendDenominator(set, frontendIndex);
      requireTechnicalPredecessors(
          destinationRun,
          sourceStep,
          discoveryStep,
          navigationStep,
          persistenceStep,
          frontendModule,
          frontend,
          frontendControls,
          backendControls,
          persistenceControls);

      List<CanonicalModulePayload> payloads = payloads(set, sourceBasis);
      requireBudget(payloads, set);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              destinationRun, AnalysisStepKey.BUSINESS_FLOWS, 4, MODULE_KEY);
      InstalledModulePublication module =
          modules.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstreamPayloadReferences(
                      sourceStep, discoveryStep, navigationStep, persistenceStep, frontendModule),
                  r4Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads));
      InstalledAnalysisStepPublication step =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      destinationRun, AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      navigationStep.reference(),
                      persistenceStep.reference()),
                  r4Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  payloads.stream().map(EntryEvidencePublisher::stepPayload).toList(),
                  null));
      ReopenedAnalysisStepPublication reopened = steps.reopen(step.reference());
      if (!reopened.reference().equals(step.reference())
          || !reopened.receipt().controls().equals(r4Controls)
          || reopened.semanticPayloads().size() != payloads.size()) {
        throw invalid();
      }
      return step.reference();
    } catch (RuntimeException failure) {
      if (failure instanceof CapacityExceededException
          || (failure instanceof IllegalArgumentException
              && "ENTRY_EVIDENCE_PUBLICATION_INVALID".equals(failure.getMessage()))) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private List<CanonicalModulePayload> payloads(
      EntryEvidenceSet set, SelectedSourceBasis sourceBasis) {
    List<CanonicalModulePayload> payloads = new ArrayList<>();
    ObjectNode header = headerDocument(set.header(), sourceBasis);
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      ObjectNode document = entryDocument(entry, header);
      document.put("schemaVersion", ENTRY_SCHEMA);
      document.put("producer", PRODUCER);
      document.set("header", header.deepCopy());
      payloads.add(
          standalonePayload(entryFileName(entry.entryId()), ENTRY_TYPE, ENTRY_SCHEMA, document));
    }
    ObjectNode index = JsonNodeFactory.instance.objectNode();
    index.put("schemaVersion", INDEX_SCHEMA);
    index.put("producer", PRODUCER);
    index.set("header", header.deepCopy());
    var entries = index.putArray("entries");
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      ObjectNode item = entries.addObject();
      item.put("entryId", entry.entryId());
      item.put("file", entryFileName(entry.entryId()));
      item.put("assemblyStatus", entry.assemblyStatus().name());
      item.put("route", entry.entry().route());
      item.set("methodCondition", MAPPER.valueToTree(entry.entry().methodCondition()));
      item.put("handlerFqn", entry.entry().handlerFqn());
      item.put("methodKey", entry.entry().methodKey());
      item.put("limitationCount", entry.limitations().size());
    }
    payloads.add(standalonePayload(INDEX_FILE, INDEX_TYPE, INDEX_SCHEMA, index));
    payloads.add(coveragePayload(set, header));
    payloads.sort(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER));
    return List.copyOf(payloads);
  }

  /**
   * Writes the self-contained entry contract explicitly instead of relying on a consumer to infer
   * its R0/R1/R2/R3 lineage from the adjacent directory header. The duplicated values are checked
   * by {@link EntryEvidenceReader} on every reopen.
   */
  private static ObjectNode entryDocument(EntryEvidenceSet.Entry entry, ObjectNode header) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("entryId", entry.entryId());
    document.set("entry", httpEntry(entry.entry()));
    document.put("assemblyStatus", entry.assemblyStatus().name());
    document.set("coverage", MAPPER.valueToTree(entry.coverage()));
    document.set("frontend", MAPPER.valueToTree(entry.frontend()));
    document.set("java", MAPPER.valueToTree(entry.java()));
    document.set("persistence", MAPPER.valueToTree(entry.persistence()));
    document.set("limitations", MAPPER.valueToTree(entry.limitations()));
    document.set("sourceBasis", header.get("sourceBasis").deepCopy());
    ObjectNode upstream = document.putObject("upstream");
    upstream.set("applicationDiscovery", header.get("applicationDiscovery").deepCopy());
    upstream.set("navigationPublication", header.get("navigationPublication").deepCopy());
    upstream.set("persistencePublication", header.get("persistencePublication").deepCopy());
    upstream.set("frontendPublication", header.get("frontendPublication").deepCopy());
    // Keep this explicit even though it is also a record component: an entry document is a
    // standalone wire contract, not an accidental Jackson projection of an implementation type.
    document.set("sourceRefs", MAPPER.valueToTree(entry.sourceRefs()));
    return document;
  }

  /**
   * The generic JSON mapper cannot represent {@link ImmutableBytes} source excerpt content. Keep
   * the established source-excerpt wire form so a standalone entry retains the actual route
   * evidence rather than an opaque implementation object.
   */
  private static ObjectNode httpEntry(
      org.sourceanalysis.app.analysis.discovery.HttpEntryPoint entry) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("entryId", entry.entryId().value());
    document.put("kind", entry.kind().name());
    document.put("protocol", entry.protocol());
    document.put("method", entry.method());
    ObjectNode methodCondition = document.putObject("methodCondition");
    methodCondition.put("kind", entry.methodCondition().kind().name());
    var methods = methodCondition.putArray("methods");
    entry.methodCondition().methods().forEach(methods::add);
    document.put("route", entry.route());
    var routeParts = document.putArray("routeParts");
    entry.routeParts().forEach(routeParts::add);
    document.put("handlerFqn", entry.handlerFqn());
    document.put("methodKey", entry.methodKey());
    ObjectNode methodRange = document.putObject("methodRange");
    methodRange.put("startOffsetUtf16", entry.methodRange().startOffsetUtf16());
    methodRange.put("lengthUtf16", entry.methodRange().lengthUtf16());
    methodRange.put("startLine", entry.methodRange().startLine());
    methodRange.put("endLine", entry.methodRange().endLine());
    var parameters = document.putArray("parameterNames");
    entry.parameterNames().forEach(parameters::add);
    var excerpts = document.putArray("routeSourceExcerpts");
    for (SourceExcerptV1 excerpt : entry.routeSourceExcerpts()) {
      excerpts.add(sourceExcerpt(excerpt));
    }
    return document;
  }

  /**
   * Mirrors the established source-excerpt wire contract without exposing {@link ImmutableBytes}.
   */
  private static ObjectNode sourceExcerpt(SourceExcerptV1 excerpt) {
    ObjectNode item = JsonNodeFactory.instance.objectNode();
    ObjectNode locator = item.putObject("locator");
    locator.put("fileId", excerpt.locator().fileId().value());
    locator.put("path", excerpt.locator().path());
    locator.put("startByte", excerpt.locator().startByte());
    locator.put("endByteExclusive", excerpt.locator().endByteExclusive());
    locator.put("startLine", excerpt.locator().startLine());
    locator.put("startColumn", excerpt.locator().startColumn());
    locator.put("endLine", excerpt.locator().endLine());
    locator.put("endColumn", excerpt.locator().endColumn());
    item.put("rawUtf8", new String(excerpt.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    item.put("rawUtf8Sha256", excerpt.rawUtf8Sha256().value());
    return item;
  }

  /**
   * Uses the same explicit, path-free source-basis form as the v2 frontend index. Entry evidence
   * must retain the selected R0 basis itself, rather than substituting the weaker R0 receipt
   * reference held by its assembled-domain header.
   */
  private static ObjectNode sourceBasisNode(SelectedSourceBasis basis) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("kind", basis.kind().name());
    node.put("snapshotId", basis.snapshotId().value());
    node.put("effectiveScopeDigest", basis.effectiveScopeDigest().value());
    if (basis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      PreparedSourceReference prepared = basis.preparedSource();
      ObjectNode preparedNode = node.putObject("preparedSource");
      preparedNode.put("sourceVersionId", prepared.sourceVersionId().value());
      preparedNode.set("publication", publicationNode(prepared));
      preparedNode.set("schemaBundleRef", referenceNode(prepared.schemaBundleRef()));
      preparedNode.set("artifactPolicyRegistryRef", policyNode(prepared));
      node.putNull("legacyCapture");
    } else {
      SourceRegistrationReference legacy = basis.legacyCapture();
      ObjectNode legacyNode = node.putObject("legacyCapture");
      legacyNode.put("sourceRegistrationId", legacy.sourceRegistrationId().value());
      legacyNode.put("snapshotId", legacy.snapshotId());
      legacyNode.set("snapshotManifestRef", referenceNode(legacy.snapshotManifestRef()));
      legacyNode.set("captureReceiptRef", referenceNode(legacy.captureReceiptRef()));
      node.putNull("preparedSource");
    }
    return node;
  }

  private static ObjectNode headerDocument(
      EntryEvidenceSet.Header header, SelectedSourceBasis sourceBasis) {
    ObjectNode document = MAPPER.valueToTree(header);
    document.set("sourceBasis", sourceBasisNode(sourceBasis));
    return document;
  }

  private static ObjectNode publicationNode(PreparedSourceReference prepared) {
    var publication = prepared.publication();
    ObjectNode node = MAPPER.createObjectNode();
    node.putObject("address")
        .put("runId", publication.address().runId().value())
        .put("analysisStepKey", publication.address().analysisStepKey().wireValue());
    node.put("analysisStepArtifactRoot", publication.analysisStepArtifactRoot().value());
    node.put("analysisStepReceiptId", publication.analysisStepReceiptId().value());
    node.put("analysisStepReceiptSha256", publication.analysisStepReceiptSha256().value());
    return node;
  }

  private static ObjectNode policyNode(PreparedSourceReference prepared) {
    return MAPPER
        .createObjectNode()
        .put("artifactId", prepared.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", prepared.artifactPolicyRegistryRef().sha256().value());
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return MAPPER
        .createObjectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private CanonicalModulePayload standalonePayload(
      String fileName, String artifactType, String schemaVersion, ObjectNode document) {
    // Standalone JSON has a self-describing identity. Unlike JSONL coverage, the canonical store
    // verifies these three top-level fields before it admits the module receipt.
    ObjectNode withoutArtifactId = document.deepCopy();
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    String artifactId =
        modules
                .resolveArtifactPolicy(new ArtifactPolicyKey(artifactType, schemaVersion))
                .artifactIdPrefix()
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(json.encodeCanonical(withoutArtifactId).copyToByteArray())));
    withoutArtifactId.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        fileName,
        artifactType,
        schemaVersion,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        json.encodeCanonical(withoutArtifactId));
  }

  private CanonicalModulePayload coveragePayload(EntryEvidenceSet set, ObjectNode headerDocument) {
    StringBuilder content = new StringBuilder();
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("schemaVersion", COVERAGE_SCHEMA);
    header.put("recordType", "HEADER");
    header.put("producer", PRODUCER);
    header.set("header", headerDocument.deepCopy());
    appendJsonLine(content, header);
    for (EntryEvidenceSet.FrontendCoverage coverage : set.frontendCoverage()) {
      ObjectNode line = JsonNodeFactory.instance.objectNode();
      line.put("schemaVersion", COVERAGE_SCHEMA);
      line.put("recordType", "REQUEST_COVERAGE");
      line.set("payload", coverageDocument(coverage));
      appendJsonLine(content, line);
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    return payload(
        COVERAGE_FILE,
        COVERAGE_TYPE,
        COVERAGE_SCHEMA,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes,
        "canonical-jsonl-artifact-id-v1");
  }

  /**
   * A uniquely included request is represented by its verified entry file, not a second copy of its
   * complete frontend evidence. All other resolutions retain the original request and source units
   * because the coverage line is their only durable destination.
   */
  private static ObjectNode coverageDocument(EntryEvidenceSet.FrontendCoverage coverage) {
    if (coverage.resolution()
            == org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord.Resolution
                .MATCHED_UNIQUE
        && coverage.includedEntryIds().size() == 1) {
      ObjectNode document = JsonNodeFactory.instance.objectNode();
      document.put("requestId", coverage.requestId());
      document.put("resolution", coverage.resolution().name());
      var candidates = document.putArray("entryIds");
      coverage.entryIds().forEach(candidates::add);
      var included = document.putArray("includedEntryIds");
      coverage.includedEntryIds().forEach(included::add);
      document.put("entryFile", entryFileName(coverage.includedEntryIds().get(0)));
      return document;
    }
    return MAPPER.valueToTree(coverage);
  }

  private void appendJsonLine(StringBuilder content, ObjectNode line) {
    content.append(
        new String(json.encodeCanonical(line).copyToByteArray(), StandardCharsets.UTF_8));
    content.append('\n');
  }

  private CanonicalModulePayload payload(
      String fileName,
      String artifactType,
      String schemaVersion,
      CanonicalMediaType mediaType,
      ImmutableBytes bytes,
      String identityDomain) {
    String prefix =
        modules
            .resolveArtifactPolicy(new ArtifactPolicyKey(artifactType, schemaVersion))
            .artifactIdPrefix();
    ArtifactId artifactId =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    concatenate(
                        frame(identityDomain),
                        frame(schemaVersion),
                        frame(artifactType),
                        frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        fileName, artifactType, schemaVersion, artifactId, mediaType, bytes);
  }

  private static void requireBudget(List<CanonicalModulePayload> payloads, EntryEvidenceSet set) {
    long total = 0L;
    for (CanonicalModulePayload payload : payloads) {
      long size = payload.canonicalUtf8().size();
      if (ENTRY_TYPE.equals(payload.artifactType())
          && size > set.header().profile().maxEntryUtf8Bytes()) {
        String fileName = payload.fileName();
        String entryId =
            "entry:"
                + fileName.substring(
                    ENTRY_FILE_PREFIX.length(), fileName.length() - ".json".length());
        throw new CapacityExceededException(
            "ENTRY_EVIDENCE_ENTRY_BYTE_LIMIT_EXCEEDED",
            entryId,
            set.header().profile().maxEntryUtf8Bytes(),
            size);
      }
      total = Math.addExact(total, size);
    }
    if (total > set.header().profile().maxPublicationUtf8Bytes()) {
      throw new CapacityExceededException(
          "ENTRY_EVIDENCE_PUBLICATION_BYTE_LIMIT_EXCEEDED",
          "publication",
          set.header().profile().maxPublicationUtf8Bytes(),
          total);
    }
  }

  private static void requireTechnicalPredecessors(
      AnalysisRunId destinationRun,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence,
      ReopenedModulePublication frontend,
      ModulePublicationReference frontendReference,
      ArtifactControls frontendControls,
      ArtifactControls backendControls,
      ArtifactControls persistenceControls) {
    if (!frontend.reference().equals(frontendReference)
        || !(frontend.reference().address() instanceof AnalysisStepModuleAddress frontendAddress)
        || frontendAddress.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || frontendAddress.moduleNumber() != 6
        || !"frontend-http-discovery".equals(frontendAddress.moduleKey())
        || !frontend.receipt().controls().equals(frontendControls)
        || source.reference().address().runId().equals(discovery.reference().address().runId())
        || frontendAddress.runId().equals(source.reference().address().runId())
        || frontendAddress.runId().equals(discovery.reference().address().runId())
        || frontendAddress.runId().equals(persistence.reference().address().runId())
        || frontendAddress.runId().equals(destinationRun)
        || !discovery.reference().address().runId().equals(navigation.reference().address().runId())
        || persistence.reference().address().runId().equals(source.reference().address().runId())
        || persistence.reference().address().runId().equals(discovery.reference().address().runId())
        || destinationRun.equals(source.reference().address().runId())
        || destinationRun.equals(discovery.reference().address().runId())
        || destinationRun.equals(persistence.reference().address().runId())
        || !discovery.receipt().controls().equals(backendControls)
        || !navigation.receipt().controls().equals(backendControls)
        || !persistence.receipt().controls().equals(persistenceControls)
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !navigation
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))
        || !persistence
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference(), navigation.reference()))) {
      throw invalid();
    }
  }

  /**
   * The entry directory may only describe the saved frontend denominator, never a request that a
   * caller constructed beside an otherwise valid module-6 receipt. This also makes a DISABLED index
   * observably empty rather than a vehicle for synthetic unmatched coverage.
   */
  private static void requireFrontendDenominator(EntryEvidenceSet set, FrontendHttpIndex index) {
    if (set.header().frontendStatus() != index.status()
        || !set.header().frontendFiles().equals(index.files())
        || !set.header().frontendDiagnostics().equals(index.diagnostics())) {
      throw invalid();
    }
    Map<String, FrontendHttpRequestRecord> requests = new LinkedHashMap<>();
    for (FrontendHttpRequestRecord request : index.requests()) {
      if (requests.putIfAbsent(request.requestId(), request) != null) {
        throw invalid();
      }
    }
    Map<String, EntryEvidenceSet.FrontendCoverage> coverageByRequest = new LinkedHashMap<>();
    for (EntryEvidenceSet.FrontendCoverage coverage : set.frontendCoverage()) {
      if (coverageByRequest.putIfAbsent(coverage.requestId(), coverage) != null
          || !coverage.request().equals(requests.get(coverage.requestId()))) {
        throw invalid();
      }
    }
    if (!coverageByRequest.keySet().equals(requests.keySet())) throw invalid();
    for (EntryEvidenceSet.Entry entry : set.entries()) {
      if (!usesSavedRequests(entry.frontend().requestUses(), requests)
          || !usesSavedRequests(entry.frontend().candidateRequestUses(), requests)) {
        throw invalid();
      }
    }
  }

  private static boolean usesSavedRequests(
      List<EntryEvidenceSet.RequestUse> uses, Map<String, FrontendHttpRequestRecord> requests) {
    for (EntryEvidenceSet.RequestUse use : uses) {
      if (!use.request().equals(requests.get(use.request().requestId()))) {
        return false;
      }
    }
    return true;
  }

  private static ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference,
      AnalysisStepKey expectedStep,
      CanonicalAnalysisStepArtifactStore store) {
    ReopenedAnalysisStepPublication reopened = store.reopen(reference);
    if (!reopened.reference().equals(reference)
        || reference.address().analysisStepKey() != expectedStep) {
      throw invalid();
    }
    return reopened;
  }

  private static List<ArtifactReference> upstreamPayloadReferences(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence,
      ReopenedModulePublication frontend) {
    Map<String, ArtifactReference> values = new LinkedHashMap<>();
    java.util.stream.Stream.of(source, discovery, navigation, persistence)
        .flatMap(step -> step.semanticPayloads().stream())
        .forEach(
            payload ->
                putReference(
                    values, payload.descriptor().artifactId(), payload.descriptor().sha256()));
    frontend
        .payloads()
        .forEach(
            payload ->
                putReference(
                    values, payload.descriptor().artifactId(), payload.descriptor().sha256()));
    return values.values().stream()
        .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
        .toList();
  }

  private static void putReference(
      Map<String, ArtifactReference> values,
      ArtifactId artifactId,
      org.sourceanalysis.app.artifact.Sha256Digest sha256) {
    ArtifactReference value = new ArtifactReference(artifactId, sha256);
    ArtifactReference prior = values.putIfAbsent(artifactId.value(), value);
    if (prior != null && !prior.equals(value)) {
      throw invalid();
    }
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

  public static String entryFileName(String entryId) {
    if (entryId == null || !entryId.matches("entry:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("entry-evidence entry ID is invalid");
    }
    return ENTRY_FILE_PREFIX + entryId.substring("entry:".length()) + ".json";
  }

  /**
   * A resource failure that callers can report without manufacturing a successful Step05 receipt.
   */
  public static final class CapacityExceededException extends IllegalArgumentException {
    private final String code;
    private final String subject;
    private final long limit;
    private final long actual;

    private CapacityExceededException(String code, String subject, long limit, long actual) {
      super(code);
      this.code = code;
      this.subject = subject;
      this.limit = limit;
      this.actual = actual;
    }

    public String code() {
      return code;
    }

    public String subject() {
      return subject;
    }

    public long limit() {
      return limit;
    }

    public long actual() {
      return actual;
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("ENTRY_EVIDENCE_PUBLICATION_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("ENTRY_EVIDENCE_PUBLICATION_INVALID", cause);
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int size = 0;
    for (byte[] value : values) size = Math.addExact(size, value.length);
    ByteBuffer bytes = ByteBuffer.allocate(size);
    for (byte[] value : values) bytes.put(value);
    return bytes.array();
  }

  private static int compareUtf8(String first, String second) {
    return java.util.Arrays.compareUnsigned(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }
}
