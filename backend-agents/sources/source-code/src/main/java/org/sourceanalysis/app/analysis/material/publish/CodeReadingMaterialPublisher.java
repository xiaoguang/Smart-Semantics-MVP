package org.sourceanalysis.app.analysis.material.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
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
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/** Contract seam for the one-file Step 05 reference-only reading-material publication. */
public final class CodeReadingMaterialPublisher {

  public static final String FILE_NAME = "code-reading-materials.jsonl";
  public static final String ARTIFACT_TYPE = "CODE_READING_MATERIAL_SET";
  public static final String SCHEMA_VERSION = "code-reading-material-set-v1";
  public static final String TECHNICAL_SCHEMA_VERSION = "code-reading-material-set-v2";
  private static final String MODULE_VERSION = "v1";
  private static final String PRODUCER = "code-reading-materials-v1";
  private static final String TECHNICAL_MODULE_VERSION = "v2";
  static final String TECHNICAL_PRODUCER = "code-reading-materials-v2";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER = CodeReadingMaterialPublisher::compareUtf8;

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalAnalysisStepArtifactStore sourceSteps;

  public CodeReadingMaterialPublisher(
      CanonicalModuleArtifactStore modules, CanonicalAnalysisStepArtifactStore steps) {
    this(modules, steps, steps);
  }

  /** Uses the source-preparation store only for technical R0 receipt validation. */
  public CodeReadingMaterialPublisher(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      CanonicalAnalysisStepArtifactStore sourceSteps) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
    this.sourceSteps = Objects.requireNonNull(sourceSteps, "source analysis-step artifact store");
  }

  /** Installs a Step 05 publication from already assembled immutable reading material. */
  public AnalysisStepPublicationReference publish(
      ApplicationDiscoveryReference discovery,
      ArtifactControls controls,
      CodeReadingMaterialSet set) {
    try {
      Objects.requireNonNull(discovery, "application discovery");
      Objects.requireNonNull(controls, "artifact controls");
      Objects.requireNonNull(set, "code reading material set");
      ReopenedAnalysisStepPublication source =
          reopen(
              set.header().sourceInventory().publication(),
              AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication navigation =
          reopen(
              set.header().navigationPublication().publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication persistence =
          reopen(set.header().persistencePublication(), AnalysisStepKey.PROVEN_CODE_FACTS);
      requireSameRunAndControls(source, discoveryStep, navigation, persistence, controls);

      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              source.reference().address().runId(),
              AnalysisStepKey.BUSINESS_FLOWS,
              4,
              "code-reading-materials");
      CanonicalModulePayload payload = legacyPayload(discovery, set);
      List<ArtifactReference> upstream =
          upstreamPayloadReferences(source, discoveryStep, navigation, persistence);
      var module =
          modules.install(
              new ModuleInstallRequest(
                  address,
                  MODULE_VERSION,
                  upstream,
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      var step =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      address.runId(), AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      source.reference(),
                      discoveryStep.reference(),
                      navigation.reference(),
                      persistence.reference()),
                  controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(stepPayload(payload)),
                  null));
      ReopenedAnalysisStepPublication reopened = steps.reopen(step.reference());
      if (!reopened.reference().equals(step.reference())
          || reopened.semanticPayloads().size() != 1) {
        throw invalid();
      }
      return step.reference();
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "CODE_READING_MATERIAL_PUBLICATION_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  /**
   * Installs the technical Step05 v2 producer owned by R3 from exact R0/R1/R2 predecessors.
   *
   * <p>R0 is always reopened through its source-preparation policy store. R1 and R2 remain under
   * the technical policy store, with their distinct receipt controls verified before the new R3
   * publication is installed.
   */
  public AnalysisStepPublicationReference publishTechnical(
      AnalysisRunId destinationRunId,
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference navigation,
      AnalysisStepPublicationReference persistence,
      ArtifactControls r1Controls,
      ArtifactControls r2Controls,
      ArtifactControls r3Controls,
      CodeReadingMaterialSet set) {
    try {
      Objects.requireNonNull(destinationRunId, "R3 destination run ID");
      Objects.requireNonNull(source, "verified source inventory");
      Objects.requireNonNull(discovery, "application discovery");
      Objects.requireNonNull(navigation, "program graphs");
      Objects.requireNonNull(persistence, "persistence material publication");
      Objects.requireNonNull(r1Controls, "R1 execution controls");
      Objects.requireNonNull(r2Controls, "R2 execution controls");
      Objects.requireNonNull(r3Controls, "R3 execution controls");
      Objects.requireNonNull(set, "code reading material set");
      if (!set.header().sourceInventory().equals(source)
          || !set.header().navigationPublication().equals(navigation)
          || !set.header().persistencePublication().equals(persistence)) {
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
      requireTechnicalPredecessors(
          destinationRunId,
          sourceStep,
          discoveryStep,
          navigationStep,
          persistenceStep,
          r1Controls,
          r2Controls);

      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              destinationRunId, AnalysisStepKey.BUSINESS_FLOWS, 4, "code-reading-materials");
      CanonicalModulePayload payload = technicalPayload(discovery, set);
      List<ArtifactReference> upstream =
          upstreamPayloadReferences(sourceStep, discoveryStep, navigationStep, persistenceStep);
      var module =
          modules.install(
              new ModuleInstallRequest(
                  address,
                  TECHNICAL_MODULE_VERSION,
                  upstream,
                  r3Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      var step =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      destinationRunId, AnalysisStepKey.BUSINESS_FLOWS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      navigationStep.reference(),
                      persistenceStep.reference()),
                  r3Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(stepPayload(payload)),
                  null));
      ReopenedAnalysisStepPublication reopened = steps.reopen(step.reference());
      if (!reopened.reference().equals(step.reference())
          || reopened.semanticPayloads().size() != 1
          || !reopened.receipt().controls().equals(r3Controls)) {
        throw invalid();
      }
      return step.reference();
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "CODE_READING_MATERIAL_PUBLICATION_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private void requireSameRunAndControls(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence,
      ArtifactControls controls) {
    if (!source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.reference().address().runId().equals(navigation.reference().address().runId())
        || !source.reference().address().runId().equals(persistence.reference().address().runId())
        || !source.receipt().controls().equals(controls)
        || !discovery.receipt().controls().equals(controls)
        || !navigation.receipt().controls().equals(controls)
        || !persistence.receipt().controls().equals(controls)
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

  private static void requireTechnicalPredecessors(
      AnalysisRunId destinationRunId,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence,
      ArtifactControls r1Controls,
      ArtifactControls r2Controls) {
    if (source.reference().address().runId().equals(discovery.reference().address().runId())
        || !discovery.reference().address().runId().equals(navigation.reference().address().runId())
        || persistence.reference().address().runId().equals(source.reference().address().runId())
        || persistence.reference().address().runId().equals(discovery.reference().address().runId())
        || destinationRunId.equals(source.reference().address().runId())
        || destinationRunId.equals(discovery.reference().address().runId())
        || destinationRunId.equals(persistence.reference().address().runId())
        || !discovery.receipt().controls().equals(r1Controls)
        || !navigation.receipt().controls().equals(r1Controls)
        || !persistence.receipt().controls().equals(r2Controls)
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

  private ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedStep) {
    return reopen(reference, expectedStep, steps);
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

  private CanonicalModulePayload legacyPayload(
      ApplicationDiscoveryReference discovery, CodeReadingMaterialSet set) {
    if (set.header().frontendPublication() != null
        || !set.frontendCoverage().isEmpty()
        || set.packets().stream()
            .anyMatch(packet -> !packet.frontendSelection().sourceUnits().isEmpty())) {
      throw invalid();
    }
    return materialPayload(discovery, set, PRODUCER, SCHEMA_VERSION);
  }

  /** Encodes the distinct technical v2 payload; strict R0/R1/R2/R3 checks stay at publication. */
  CanonicalModulePayload technicalPayload(
      ApplicationDiscoveryReference discovery, CodeReadingMaterialSet set) {
    Objects.requireNonNull(discovery, "application discovery");
    Objects.requireNonNull(set, "code reading material set");
    if (!isFrontendModule(set.header().frontendPublication())) {
      throw invalid();
    }
    return materialPayload(discovery, set, TECHNICAL_PRODUCER, TECHNICAL_SCHEMA_VERSION);
  }

  private CanonicalModulePayload materialPayload(
      ApplicationDiscoveryReference discovery,
      CodeReadingMaterialSet set,
      String producer,
      String schemaVersion) {
    StringBuilder content = new StringBuilder();
    for (RecordLine line : records(discovery, set, producer, schemaVersion)) {
      ObjectNode envelope = JsonNodeFactory.instance.objectNode();
      envelope.put("schemaVersion", schemaVersion);
      envelope.put("recordType", line.type());
      envelope.put("key", line.key());
      envelope.set("payload", line.payload());
      content.append(
          new String(
              new CanonicalJsonCodec().encodeCanonical(envelope).copyToByteArray(),
              StandardCharsets.UTF_8));
      content.append('\n');
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    String prefix =
        modules
            .resolveArtifactPolicy(new ArtifactPolicyKey(ARTIFACT_TYPE, schemaVersion))
            .artifactIdPrefix();
    ArtifactId artifactId =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    concatenate(
                        frame("canonical-jsonl-artifact-id-v1"),
                        frame(schemaVersion),
                        frame(ARTIFACT_TYPE),
                        frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        schemaVersion,
        artifactId,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static List<RecordLine> records(
      ApplicationDiscoveryReference discovery,
      CodeReadingMaterialSet set,
      String producer,
      String schemaVersion) {
    List<RecordLine> records = new ArrayList<>();
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("producer", producer);
    header.set("sourceInventory", MAPPER.valueToTree(set.header().sourceInventory()));
    header.set("applicationDiscovery", MAPPER.valueToTree(discovery));
    header.set("navigationPublication", MAPPER.valueToTree(set.header().navigationPublication()));
    header.set("persistencePublication", MAPPER.valueToTree(set.header().persistencePublication()));
    header.put("sourceSnapshotId", set.header().sourceSnapshotId());
    header.set("profile", MAPPER.valueToTree(set.header().profile()));
    if (TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
      header.set(
          "frontendPublication", frontendPublicationNode(set.header().frontendPublication()));
    }
    records.add(new RecordLine("HEADER", "header", header));
    set.packets().forEach(packet -> records.add(packet(packet, schemaVersion)));
    set.coverage().forEach(coverage -> records.add(coverage(coverage)));
    if (TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
      set.frontendCoverage().forEach(coverage -> records.add(frontendCoverage(coverage)));
    }
    requireUniqueKeys(records);
    return List.copyOf(records);
  }

  private static RecordLine packet(CodeReadingMaterialSet.Packet packet, String schemaVersion) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("packetId", packet.packetId());
    payload.set(
        "entryIds",
        MAPPER.valueToTree(packet.entries().stream().map(entry -> entry.entryId()).toList()));
    payload.set(
        "methodKeys",
        MAPPER.valueToTree(packet.methods().stream().map(method -> method.methodKey()).toList()));
    ArrayNode callKeys = payload.putArray("callKeys");
    for (CodeReadingMaterialSet.EntryCall call : packet.calls()) {
      callKeys.addObject().put("entryId", call.entryId()).put("callKey", call.call().callKey());
    }
    payload.set("persistence", persistence(packet.persistence()));
    payload.set("sourceReferences", MAPPER.valueToTree(packet.sourceReferences()));
    payload.set("unselectedUnits", MAPPER.valueToTree(packet.unselectedUnits()));
    payload.set("limitations", MAPPER.valueToTree(packet.limitations()));
    payload.put("selfContainedUtf8Bytes", packet.selfContainedUtf8Bytes());
    if (TECHNICAL_SCHEMA_VERSION.equals(schemaVersion)) {
      payload.set("frontendSelection", MAPPER.valueToTree(packet.frontendSelection()));
    }
    return new RecordLine("PACKET", packet.packetId(), payload);
  }

  private static ObjectNode persistence(CodeReadingMaterialSet.PersistenceSelection selection) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.set(
        "resourcePaths",
        MAPPER.valueToTree(
            selection.resources().stream()
                .map(PersistenceMaterialIndex.Resource::resourcePath)
                .toList()));
    payload.set(
        "statementRefs",
        MAPPER.valueToTree(
            selection.statements().stream()
                .map(PersistenceMaterialIndex.Statement::statementRef)
                .toList()));
    payload.set(
        "bindingMethodKeys",
        MAPPER.valueToTree(
            selection.bindings().stream()
                .map(PersistenceMaterialIndex.JavaBinding::methodKey)
                .toList()));
    payload.set(
        "sqlAnalysisStatementRefs",
        MAPPER.valueToTree(
            selection.sqlAnalyses().stream()
                .map(PersistenceMaterialIndex.SqlAnalysis::statementRef)
                .toList()));
    payload.set(
        "diagnosticKeys",
        MAPPER.valueToTree(
            selection.diagnostics().stream()
                .map(CodeReadingMaterialPublisher::diagnosticKey)
                .toList()));
    return payload;
  }

  private static RecordLine coverage(CodeReadingMaterialSet.EntryCoverage coverage) {
    return new RecordLine(
        "ENTRY_COVERAGE", coverage.entryId(), (ObjectNode) MAPPER.valueToTree(coverage));
  }

  private static RecordLine frontendCoverage(CodeReadingMaterialSet.FrontendCoverage coverage) {
    return new RecordLine(
        "FRONTEND_COVERAGE", coverage.requestId(), (ObjectNode) MAPPER.valueToTree(coverage));
  }

  private static ObjectNode frontendPublicationNode(ModulePublicationReference reference) {
    if (!isFrontendModule(reference)) {
      throw invalid();
    }
    AnalysisStepModuleAddress address = (AnalysisStepModuleAddress) reference.address();
    ObjectNode node = MAPPER.createObjectNode();
    node.putObject("address")
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
    node.put("moduleArtifactRoot", reference.moduleArtifactRoot().value());
    node.put("moduleReceiptId", reference.moduleReceiptId().value());
    node.put("moduleReceiptSha256", reference.moduleReceiptSha256().value());
    return node;
  }

  private static boolean isFrontendModule(ModulePublicationReference reference) {
    return reference != null
        && reference.address() instanceof AnalysisStepModuleAddress address
        && address.analysisStepKey() == AnalysisStepKey.APPLICATION_DISCOVERY
        && address.moduleNumber() == 6
        && "frontend-http-discovery".equals(address.moduleKey());
  }

  private static void requireUniqueKeys(List<RecordLine> records) {
    java.util.HashSet<String> identifiers = new java.util.HashSet<>();
    for (RecordLine record : records) {
      if (!identifiers.add(record.type() + "\u0000" + record.key())) {
        throw invalid();
      }
    }
  }

  private static List<ArtifactReference> upstreamPayloadReferences(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence) {
    Map<String, ArtifactReference> unique = new LinkedHashMap<>();
    java.util.stream.Stream.of(source, discovery, navigation, persistence)
        .flatMap(step -> step.semanticPayloads().stream())
        .forEach(
            payload -> {
              ArtifactReference reference =
                  new ArtifactReference(
                      payload.descriptor().artifactId(), payload.descriptor().sha256());
              unique.put(reference.artifactId().value(), reference);
            });
    return unique.values().stream()
        .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
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

  static String diagnosticKey(PersistenceMaterialIndex.Diagnostic diagnostic) {
    return "diagnostic:"
        + sha256(
            diagnostic.code()
                + "\u0000"
                + diagnostic.subjectRef()
                + "\u0000"
                + diagnostic.detail());
  }

  private static String sha256(String value) {
    return sha256(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
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
    ByteBuffer result = ByteBuffer.allocate(size);
    for (byte[] value : values) result.put(value);
    return result.array();
  }

  private static int compareUtf8(String first, String second) {
    return java.util.Arrays.compareUnsigned(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("CODE_READING_MATERIAL_PUBLICATION_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("CODE_READING_MATERIAL_PUBLICATION_INVALID", cause);
  }

  private record RecordLine(String type, String key, ObjectNode payload) {}
}
