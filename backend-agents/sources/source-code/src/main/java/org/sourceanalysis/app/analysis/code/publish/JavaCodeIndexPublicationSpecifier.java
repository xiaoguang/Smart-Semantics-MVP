package org.sourceanalysis.app.analysis.code.publish;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.PreparedProgramGraphSet;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
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
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Publishes one JDT-derived, engine-neutral Java navigation index as Step 03 module 7. */
public final class JavaCodeIndexPublicationSpecifier {

  public static final String FILE_NAME = "java-code-index.jsonl";
  public static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_JAVA_CODE_INDEX";
  public static final String SCHEMA_VERSION = "java-code-index-v1";
  private static final String MODULE_VERSION = "v1";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Comparator<String> UTF8_ORDER =
      JavaCodeIndexPublicationSpecifier::compareUtf8;

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public JavaCodeIndexPublicationSpecifier(
      CanonicalModuleArtifactStore modules, CanonicalAnalysisStepArtifactStore steps) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
  }

  /** Installs and fresh-reopens the one-file JDT Step 03 publication. */
  public ProgramGraphsReference publish(
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ArtifactControls controls,
      JavaCodeIndex index) {
    return publish(source, discovery, controls, index, null);
  }

  /** Publishes JavaParser navigation together with an installed strict graph enhancement set. */
  public ProgramGraphsReference publishWithGraphEnhancements(
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ArtifactControls controls,
      JavaCodeIndex index,
      PreparedProgramGraphSet graphSet) {
    return publish(
        source, discovery, controls, index, Objects.requireNonNull(graphSet, "prepared graph set"));
  }

  private ProgramGraphsReference publish(
      VerifiedSourceInventoryReference source,
      ApplicationDiscoveryReference discovery,
      ArtifactControls controls,
      JavaCodeIndex index,
      PreparedProgramGraphSet graphSet) {
    try {
      Objects.requireNonNull(source, "verified source inventory");
      Objects.requireNonNull(discovery, "application discovery");
      Objects.requireNonNull(controls, "artifact controls");
      Objects.requireNonNull(index, "Java code index");
      ReopenedAnalysisStepPublication sourceStep =
          reopen(source.publication(), AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discoveryStep =
          reopen(discovery.publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      if (!sourceStep
              .reference()
              .address()
              .runId()
              .equals(discoveryStep.reference().address().runId())
          || !sourceStep.receipt().controls().equals(controls)
          || !discoveryStep.receipt().controls().equals(controls)
          || !discoveryStep
              .receipt()
              .upstreamAnalysisStepReferences()
              .equals(List.of(sourceStep.reference()))) {
        throw invalid();
      }
      ArtifactReference snapshotRef = payloadRef(sourceStep, "verified-snapshot.json");
      if (!snapshotRef.equals(index.snapshotRef())) {
        throw invalid();
      }
      requireEntryDenominator(discoveryStep, index);

      CanonicalModulePayload payload = indexPayload(index);
      AnalysisStepModuleAddress address =
          new AnalysisStepModuleAddress(
              sourceStep.reference().address().runId(),
              AnalysisStepKey.PROGRAM_GRAPHS,
              7,
              "java-code-index");
      List<ArtifactReference> upstream =
          new ArrayList<>(upstreamPayloadRefs(sourceStep, discoveryStep));
      if (graphSet != null) {
        var graphPublication = modules.reopen(graphSet.publisher());
        if (!(graphPublication.receipt().address()
                instanceof AnalysisStepModuleAddress graphAddress)
            || graphAddress.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
            || graphAddress.moduleNumber() != 6
            || !"publish".equals(graphAddress.moduleKey())
            || !graphAddress.runId().equals(address.runId())
            || !graphPublication.receipt().controls().equals(controls)
            || graphPublication.receipt().status() != graphSet.completionStatus()
            || !graphPublication.receipt().gapRefs().equals(graphSet.gapRefs())) {
          throw invalid();
        }
        upstream.addAll(graphSet.semanticPayloadReferences());
      }
      upstream =
          upstream.stream()
              .distinct()
              .sorted(Comparator.comparing(value -> value.artifactId().value(), UTF8_ORDER))
              .toList();
      List<String> gaps = new ArrayList<>(gapRefs(index));
      if (graphSet != null) {
        gaps.addAll(graphSet.gapRefs());
      }
      gaps = gaps.stream().distinct().sorted(UTF8_ORDER).toList();
      ModuleCompletionStatus status =
          gaps.isEmpty()
              ? ModuleCompletionStatus.SUCCEEDED
              : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
      List<CanonicalModulePayload> modulePayloads = new ArrayList<>();
      if (graphSet != null) {
        graphSet.semanticPayloads().stream()
            .map(JavaCodeIndexPublicationSpecifier::modulePayload)
            .forEach(modulePayloads::add);
      }
      modulePayloads.add(payload);
      modulePayloads.sort(Comparator.comparing(CanonicalModulePayload::fileName, UTF8_ORDER));
      var module =
          modules.install(
              new ModuleInstallRequest(
                  address, MODULE_VERSION, upstream, controls, status, gaps, modulePayloads));
      List<CanonicalAnalysisStepPayload> stepPayloads =
          modulePayloads.stream().map(JavaCodeIndexPublicationSpecifier::stepPayload).toList();
      var step =
          steps.install(
              new AnalysisStepInstallRequest(
                  new AnalysisStepPublicationAddress(
                      address.runId(), AnalysisStepKey.PROGRAM_GRAPHS),
                  new AnalysisStepPublisherModuleProvenance(module.reference()),
                  List.of(sourceStep.reference(), discoveryStep.reference()),
                  controls,
                  status,
                  gaps,
                  stepPayloads,
                  null));
      ReopenedAnalysisStepPublication reopened = steps.reopen(step.reference());
      if (!reopened.reference().equals(step.reference())
          || reopened.semanticPayloads().size() != (graphSet == null ? 1 : 8)) {
        throw invalid();
      }
      return new ProgramGraphsReference(step.reference());
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "JAVA_CODE_INDEX_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private static CanonicalModulePayload modulePayload(CanonicalAnalysisStepPayload payload) {
    return new CanonicalModulePayload(
        payload.fileName(),
        payload.artifactType(),
        payload.schemaVersion(),
        payload.artifactId(),
        payload.mediaType(),
        payload.canonicalUtf8());
  }

  private CanonicalModulePayload indexPayload(JavaCodeIndex index) {
    List<IndexRecord> records = records(index);
    StringBuilder content = new StringBuilder();
    for (IndexRecord record : records) {
      ObjectNode line = JsonNodeFactory.instance.objectNode();
      line.put("schemaVersion", SCHEMA_VERSION);
      line.put("recordType", record.type());
      line.put("key", record.key());
      line.set("payload", record.payload());
      content.append(
          new String(json.encodeCanonical(line).copyToByteArray(), StandardCharsets.UTF_8));
      content.append('\n');
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    String prefix =
        modules
            .resolveArtifactPolicy(new ArtifactPolicyKey(ARTIFACT_TYPE, SCHEMA_VERSION))
            .artifactIdPrefix();
    ArtifactId id =
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
        id,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private static List<IndexRecord> records(JavaCodeIndex index) {
    List<IndexRecord> records = new ArrayList<>();
    ObjectNode engine = JsonNodeFactory.instance.objectNode();
    engine.set("descriptor", MAPPER.valueToTree(index.engine()));
    engine.put("snapshotId", index.snapshotId());
    engine.set("snapshotRef", MAPPER.valueToTree(index.snapshotRef()));
    engine.set("files", MAPPER.valueToTree(index.catalog().files()));
    engine.set("annotations", MAPPER.valueToTree(index.catalog().annotations()));
    engine.set("fields", MAPPER.valueToTree(index.catalog().fields()));
    engine.set("technicalEnhancements", MAPPER.valueToTree(index.technicalEnhancements()));
    records.add(new IndexRecord("ENGINE", "engine", engine));

    index
        .catalog()
        .types()
        .forEach(
            type -> records.add(new IndexRecord("TYPE", typeKey(type), MAPPER.valueToTree(type))));

    Map<String, EntryCodeContext.MethodCode> codeByMethod = new HashMap<>();
    Map<String, EntryCodeContext.CallSite> callByKey = new HashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      if (entry.context() == null) {
        continue;
      }
      entry.context().methods().forEach(value -> putSame(codeByMethod, value.methodKey(), value));
      entry.context().calls().forEach(value -> putSame(callByKey, value.callKey(), value));
    }
    for (JavaDeclarationCatalog.MethodDeclarationView declaration : index.catalog().methods()) {
      ObjectNode method = JsonNodeFactory.instance.objectNode();
      method.set("declaration", MAPPER.valueToTree(declaration));
      EntryCodeContext.MethodCode code = codeByMethod.remove(declaration.methodKey());
      method.set(
          "code", code == null ? JsonNodeFactory.instance.nullNode() : MAPPER.valueToTree(code));
      records.add(new IndexRecord("METHOD", declaration.methodKey(), method));
    }
    for (EntryCodeContext.MethodCode code : codeByMethod.values()) {
      ObjectNode method = JsonNodeFactory.instance.objectNode();
      method.putNull("declaration");
      method.set("code", MAPPER.valueToTree(code));
      records.add(new IndexRecord("METHOD", code.methodKey(), method));
    }
    callByKey
        .values()
        .forEach(
            call -> records.add(new IndexRecord("CALL", call.callKey(), MAPPER.valueToTree(call))));
    index.entries().forEach(entry -> records.add(membership(entry)));
    index
        .catalog()
        .fileDiagnostics()
        .forEach(
            (path, detail) -> {
              ObjectNode diagnostic =
                  JsonNodeFactory.instance.objectNode().put("path", path).put("detail", detail);
              records.add(new IndexRecord("DIAGNOSTIC", path, diagnostic));
            });
    records.sort(
        Comparator.comparingInt((IndexRecord value) -> recordOrder(value.type()))
            .thenComparing(IndexRecord::key, UTF8_ORDER));
    return List.copyOf(records);
  }

  private static IndexRecord membership(JavaCodeIndex.EntryCollection entry) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.set("seed", MAPPER.valueToTree(entry.seed()));
    payload.put("collectionStatus", entry.collectionStatus());
    if (entry.reason() == null) {
      payload.putNull("reason");
    } else {
      payload.put("reason", entry.reason());
    }
    EntryCodeContext context = entry.context();
    payload.set(
        "methodKeys",
        MAPPER.valueToTree(
            context == null
                ? List.of()
                : context.methods().stream().map(EntryCodeContext.MethodCode::methodKey).toList()));
    payload.set(
        "callKeys",
        MAPPER.valueToTree(
            context == null
                ? List.of()
                : context.calls().stream().map(EntryCodeContext.CallSite::callKey).toList()));
    payload.set(
        "supportingSources",
        MAPPER.valueToTree(context == null ? List.of() : context.supportingSources()));
    payload.set(
        "limitations", MAPPER.valueToTree(context == null ? List.of() : context.limitations()));
    return new IndexRecord("ENTRY_MEMBERSHIP", entry.seed().entryId(), payload);
  }

  private static void requireEntryDenominator(
      ReopenedAnalysisStepPublication discovery, JavaCodeIndex index) {
    VerifiedCanonicalPayload payload =
        discovery.semanticPayloads().stream()
            .filter(value -> "entry-points.jsonl".equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow(JavaCodeIndexPublicationSpecifier::invalid);
    java.util.Set<String> expected = new java.util.HashSet<>();
    String text = new String(payload.canonicalUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    if (!text.isEmpty()) {
      for (String line : text.substring(0, text.length() - 1).split("\\n")) {
        try {
          expected.add(
              new CanonicalJsonCodec()
                  .parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)))
                  .get("entryId")
                  .textValue());
        } catch (RuntimeException failure) {
          throw invalid();
        }
      }
    }
    java.util.Set<String> actual =
        index.entries().stream()
            .map(value -> value.seed().entryId())
            .collect(java.util.stream.Collectors.toSet());
    if (!expected.equals(actual)) {
      throw invalid();
    }
  }

  private ReopenedAnalysisStepPublication reopen(
      org.sourceanalysis.app.artifact.AnalysisStepPublicationReference reference,
      AnalysisStepKey key) {
    ReopenedAnalysisStepPublication reopened = steps.reopen(reference);
    if (!reopened.reference().equals(reference) || reference.address().analysisStepKey() != key) {
      throw invalid();
    }
    return reopened;
  }

  private static ArtifactReference payloadRef(
      ReopenedAnalysisStepPublication step, String fileName) {
    VerifiedCanonicalPayload payload =
        step.semanticPayloads().stream()
            .filter(value -> fileName.equals(value.descriptor().fileName()))
            .findFirst()
            .orElseThrow(JavaCodeIndexPublicationSpecifier::invalid);
    return new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256());
  }

  private static List<ArtifactReference> upstreamPayloadRefs(
      ReopenedAnalysisStepPublication first, ReopenedAnalysisStepPublication second) {
    Map<String, ArtifactReference> unique = new LinkedHashMap<>();
    java.util.stream.Stream.concat(
            first.semanticPayloads().stream(), second.semanticPayloads().stream())
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

  private static ModuleCompletionStatus completion(JavaCodeIndex index) {
    return index.entries().stream().allMatch(value -> value.context() != null)
        ? ModuleCompletionStatus.SUCCEEDED
        : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS;
  }

  private static List<String> gapRefs(JavaCodeIndex index) {
    return index.entries().stream()
        .filter(value -> value.context() == null)
        .map(
            value ->
                "java-code-index-gap:"
                    + sha256(value.seed().entryId().getBytes(StandardCharsets.UTF_8)))
        .sorted(UTF8_ORDER)
        .toList();
  }

  private static String typeKey(JavaDeclarationCatalog.TypeDeclaration type) {
    return (type.qualifiedName() == null ? type.sourcePath() : type.qualifiedName())
        + "@"
        + type.sourceRange().startOffsetUtf16();
  }

  private static int recordOrder(String value) {
    return List.of("ENGINE", "TYPE", "METHOD", "CALL", "ENTRY_MEMBERSHIP", "DIAGNOSTIC")
        .indexOf(value);
  }

  private static <T> void putSame(Map<String, T> values, String key, T value) {
    T existing = values.putIfAbsent(key, value);
    if (existing != null && !existing.equals(value)) {
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
    return new IllegalArgumentException("JAVA_CODE_INDEX_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("JAVA_CODE_INDEX_INVALID", cause);
  }

  private record IndexRecord(String type, String key, ObjectNode payload) {}
}
