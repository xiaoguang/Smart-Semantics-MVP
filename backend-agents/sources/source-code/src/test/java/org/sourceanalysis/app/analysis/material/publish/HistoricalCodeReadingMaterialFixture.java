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
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
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
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;

/**
 * Test-only installer for a saved historical Step05 v1 record.
 *
 * <p>The configured runtime no longer produces Packet material. Tests which exercise the retained
 * reader/export path install this fixed historical wire shape directly, so they cannot revive a
 * production builder or publisher by accident.
 */
public final class HistoricalCodeReadingMaterialFixture {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  private HistoricalCodeReadingMaterialFixture() {}

  /** Installs a saved v1 material publication for reader-only historical tests. */
  public static AnalysisStepPublicationReference installV1(
      CanonicalModuleArtifactStore modules,
      CanonicalAnalysisStepArtifactStore steps,
      ApplicationDiscoveryReference discovery,
      ArtifactControls controls,
      CodeReadingMaterialSet materials) {
    Objects.requireNonNull(modules, "module artifact store");
    Objects.requireNonNull(steps, "analysis-step artifact store");
    Objects.requireNonNull(discovery, "application discovery");
    Objects.requireNonNull(controls, "artifact controls");
    Objects.requireNonNull(materials, "historical material set");
    if (materials.header().frontendPublication() != null
        || !materials.frontendCoverage().isEmpty()
        || materials.packets().stream()
            .anyMatch(packet -> !packet.frontendSelection().sourceUnits().isEmpty())) {
      throw new IllegalArgumentException("historical v1 fixture cannot contain frontend material");
    }

    ReopenedAnalysisStepPublication source =
        reopen(
            steps,
            materials.header().sourceInventory().publication(),
            AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
    ReopenedAnalysisStepPublication discoveryStep =
        reopen(steps, discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
    ReopenedAnalysisStepPublication navigation =
        reopen(
            steps,
            materials.header().navigationPublication().publication(),
            AnalysisStepKey.PROGRAM_GRAPHS);
    ReopenedAnalysisStepPublication persistence =
        reopen(
            steps, materials.header().persistencePublication(), AnalysisStepKey.PROVEN_CODE_FACTS);
    requireHistoricalPredecessors(source, discoveryStep, navigation, persistence, controls);

    CanonicalModulePayload payload = payload(modules, discovery, materials);
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            source.reference().address().runId(),
            AnalysisStepKey.BUSINESS_FLOWS,
            4,
            "code-reading-materials");
    var module =
        modules.install(
            new ModuleInstallRequest(
                address,
                "v1",
                upstreamPayloadReferences(source, discoveryStep, navigation, persistence),
                controls,
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(payload)));
    return steps
        .install(
            new AnalysisStepInstallRequest(
                new AnalysisStepPublicationAddress(address.runId(), AnalysisStepKey.BUSINESS_FLOWS),
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
                null))
        .reference();
  }

  private static ReopenedAnalysisStepPublication reopen(
      CanonicalAnalysisStepArtifactStore steps,
      AnalysisStepPublicationReference reference,
      AnalysisStepKey expectedStep) {
    ReopenedAnalysisStepPublication reopened = steps.reopen(reference);
    if (!reopened.reference().equals(reference)
        || reference.address().analysisStepKey() != expectedStep) {
      throw new IllegalArgumentException("historical material fixture predecessors are invalid");
    }
    return reopened;
  }

  private static void requireHistoricalPredecessors(
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
      throw new IllegalArgumentException("historical material fixture predecessors are invalid");
    }
  }

  private static CanonicalModulePayload payload(
      CanonicalModuleArtifactStore modules,
      ApplicationDiscoveryReference discovery,
      CodeReadingMaterialSet materials) {
    StringBuilder content = new StringBuilder();
    for (RecordLine line : records(discovery, materials)) {
      ObjectNode envelope = JsonNodeFactory.instance.objectNode();
      envelope.put("schemaVersion", CodeReadingMaterialPublisher.SCHEMA_VERSION);
      envelope.put("recordType", line.type());
      envelope.put("key", line.key());
      envelope.set("payload", line.payload());
      content.append(
          new String(JSON.encodeCanonical(envelope).copyToByteArray(), StandardCharsets.UTF_8));
      content.append('\n');
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    String prefix =
        modules
            .resolveArtifactPolicy(
                new ArtifactPolicyKey(
                    CodeReadingMaterialPublisher.ARTIFACT_TYPE,
                    CodeReadingMaterialPublisher.SCHEMA_VERSION))
            .artifactIdPrefix();
    ArtifactId artifactId =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    concatenate(
                        frame("canonical-jsonl-artifact-id-v1"),
                        frame(CodeReadingMaterialPublisher.SCHEMA_VERSION),
                        frame(CodeReadingMaterialPublisher.ARTIFACT_TYPE),
                        frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        CodeReadingMaterialPublisher.FILE_NAME,
        CodeReadingMaterialPublisher.ARTIFACT_TYPE,
        CodeReadingMaterialPublisher.SCHEMA_VERSION,
        artifactId,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static List<RecordLine> records(
      ApplicationDiscoveryReference discovery, CodeReadingMaterialSet materials) {
    List<RecordLine> records = new ArrayList<>();
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("producer", CodeReadingMaterialPublisher.LEGACY_PRODUCER);
    header.set("sourceInventory", MAPPER.valueToTree(materials.header().sourceInventory()));
    header.set("applicationDiscovery", MAPPER.valueToTree(discovery));
    header.set(
        "navigationPublication", MAPPER.valueToTree(materials.header().navigationPublication()));
    header.set(
        "persistencePublication", MAPPER.valueToTree(materials.header().persistencePublication()));
    header.put("sourceSnapshotId", materials.header().sourceSnapshotId());
    header.set("profile", MAPPER.valueToTree(materials.header().profile()));
    records.add(new RecordLine("HEADER", "header", header));
    materials.packets().forEach(packet -> records.add(packet(packet)));
    materials.coverage().forEach(coverage -> records.add(coverage(coverage)));
    return List.copyOf(records);
  }

  private static RecordLine packet(CodeReadingMaterialSet.Packet packet) {
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
        .sorted(
            Comparator.comparing(
                value -> value.artifactId().value(),
                HistoricalCodeReadingMaterialFixture::compareUtf8))
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
    for (byte[] value : values) {
      size = Math.addExact(size, value.length);
    }
    ByteBuffer result = ByteBuffer.allocate(size);
    for (byte[] value : values) {
      result.put(value);
    }
    return result.array();
  }

  private static int compareUtf8(String first, String second) {
    return java.util.Arrays.compareUnsigned(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }

  private record RecordLine(String type, String key, ObjectNode payload) {}
}
