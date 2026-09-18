package org.sourceanalysis.app.analysis.persistence.publish;

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
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
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

/** Contract seam for the one-file Step 04 persistence-material publication. */
public final class PersistenceMaterialPublisher {

  public static final String FILE_NAME = "persistence-material-index.jsonl";
  public static final String ARTIFACT_TYPE = "PERSISTENCE_MATERIAL_INDEX";
  public static final String SCHEMA_VERSION = "persistence-material-index-v1";
  private static final String MODULE_VERSION = "v1";
  private static final String PRODUCER = "persistence-analysis-v1";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER = PersistenceMaterialPublisher::compareUtf8;

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;

  public PersistenceMaterialPublisher(
      CanonicalModuleArtifactStore modules, CanonicalAnalysisStepArtifactStore steps) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
  }

  /** Installs one canonical Step 04 index from already analyzed immutable persistence material. */
  public AnalysisStepPublicationReference publish(
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ArtifactControls controls,
      PersistenceMaterialIndex index) {
    try {
      Objects.requireNonNull(source, "verified source inventory");
      Objects.requireNonNull(discovery, "application discovery");
      Objects.requireNonNull(controls, "artifact controls");
      Objects.requireNonNull(index, "persistence material index");
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication navigationStep =
          reopen(
              index.header().navigationPublication().publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      requireSameRunAndControls(sourceStep, discoveryStep, navigationStep, controls);

      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              sourceStep.reference().address().runId(),
              AnalysisStepKey.PROVEN_CODE_FACTS,
              4,
              "persistence-analysis");
      CanonicalModulePayload payload = indexPayload(index);
      List<ArtifactReference> upstream =
          upstreamPayloadReferences(sourceStep, discoveryStep, navigationStep);
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
                      address.runId(), AnalysisStepKey.PROVEN_CODE_FACTS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(
                      sourceStep.reference(),
                      discoveryStep.reference(),
                      navigationStep.reference()),
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
          && "PERSISTENCE_MATERIAL_PUBLICATION_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private void requireSameRunAndControls(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ArtifactControls controls) {
    if (!source.reference().address().runId().equals(discovery.reference().address().runId())
        || !source.reference().address().runId().equals(navigation.reference().address().runId())
        || !source.receipt().controls().equals(controls)
        || !discovery.receipt().controls().equals(controls)
        || !navigation.receipt().controls().equals(controls)
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !navigation
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))) {
      throw invalid();
    }
  }

  private ReopenedAnalysisStepPublication reopen(
      AnalysisStepPublicationReference reference, AnalysisStepKey expectedStep) {
    ReopenedAnalysisStepPublication reopened = steps.reopen(reference);
    if (!reopened.reference().equals(reference)
        || reference.address().analysisStepKey() != expectedStep) {
      throw invalid();
    }
    return reopened;
  }

  private CanonicalModulePayload indexPayload(PersistenceMaterialIndex index) {
    StringBuilder content = new StringBuilder();
    for (IndexRecord record : records(index)) {
      ObjectNode line = JsonNodeFactory.instance.objectNode();
      line.put("schemaVersion", SCHEMA_VERSION);
      line.put("recordType", record.type());
      line.put("key", record.key());
      line.set("payload", record.payload());
      content.append(
          new String(
              new CanonicalJsonCodec().encodeCanonical(line).copyToByteArray(),
              StandardCharsets.UTF_8));
      content.append('\n');
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    String prefix =
        modules
            .resolveArtifactPolicy(new ArtifactPolicyKey(ARTIFACT_TYPE, SCHEMA_VERSION))
            .artifactIdPrefix();
    ArtifactId artifactId =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    concatenate(
                        frame("canonical-jsonl-artifact-id-v1"),
                        frame(SCHEMA_VERSION),
                        frame(ARTIFACT_TYPE),
                        frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        artifactId,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static List<IndexRecord> records(PersistenceMaterialIndex index) {
    List<IndexRecord> records = new ArrayList<>();
    ObjectNode header = JsonNodeFactory.instance.objectNode();
    header.put("producer", PRODUCER);
    header.put("status", index.header().status().name());
    header.put("sourceSnapshotId", index.header().sourceSnapshotId());
    header.set("navigationPublication", MAPPER.valueToTree(index.header().navigationPublication()));
    header.set("tools", MAPPER.valueToTree(index.header().tools()));
    records.add(new IndexRecord("HEADER", "header", header));
    index
        .resources()
        .forEach(value -> records.add(record("RESOURCE", value.resourcePath(), value)));
    index
        .statements()
        .forEach(value -> records.add(record("STATEMENT", value.statementRef(), value)));
    index
        .bindings()
        .forEach(value -> records.add(record("JAVA_BINDING", value.methodKey(), value)));
    index
        .sqlAnalyses()
        .forEach(value -> records.add(record("SQL_ANALYSIS", value.statementRef(), value)));
    index
        .diagnostics()
        .forEach(
            value ->
                records.add(
                    record(
                        "DIAGNOSTIC",
                        "diagnostic:"
                            + sha256(
                                value.code()
                                    + "\u0000"
                                    + value.subjectRef()
                                    + "\u0000"
                                    + value.detail()),
                        value)));
    records.sort(
        Comparator.comparingInt((IndexRecord value) -> recordOrder(value.type()))
            .thenComparing(IndexRecord::key, UTF8_ORDER));
    requireUniqueRecordKeys(records);
    return List.copyOf(records);
  }

  private static IndexRecord record(String type, String key, Object value) {
    return new IndexRecord(type, key, (ObjectNode) MAPPER.valueToTree(value));
  }

  private static void requireUniqueRecordKeys(List<IndexRecord> records) {
    java.util.HashSet<String> seen = new java.util.HashSet<>();
    for (IndexRecord record : records) {
      if (!seen.add(record.type() + "\u0000" + record.key())) {
        throw invalid();
      }
    }
  }

  private static List<ArtifactReference> upstreamPayloadReferences(
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation) {
    Map<String, ArtifactReference> unique = new LinkedHashMap<>();
    java.util.stream.Stream.of(source, discovery, navigation)
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

  private static int recordOrder(String type) {
    return List.of("HEADER", "RESOURCE", "STATEMENT", "JAVA_BINDING", "SQL_ANALYSIS", "DIAGNOSTIC")
        .indexOf(type);
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
    return new IllegalArgumentException("PERSISTENCE_MATERIAL_PUBLICATION_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("PERSISTENCE_MATERIAL_PUBLICATION_INVALID", cause);
  }

  private record IndexRecord(String type, String key, ObjectNode payload) {}
}
