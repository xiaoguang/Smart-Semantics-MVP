package org.sourceanalysis.app.analysis.material.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialMarkdown;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialProfile;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.analysis.persistence.publish.PersistenceMaterialReader;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Fresh-reopens Step 05 reading materials without selecting, parsing, or analyzing material again.
 */
public final class CodeReadingMaterialReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> RECORD_TYPES = Set.of("HEADER", "PACKET", "ENTRY_COVERAGE");
  private static final String PRODUCER = "code-reading-materials-v1";

  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public CodeReadingMaterialReader(CanonicalAnalysisStepArtifactStore steps) {
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
  }

  /** Hydrates saved references through Step 03/04 readers without rebuilding material selection. */
  public CodeReadingMaterialSet reopen(AnalysisStepPublicationReference reference) {
    try {
      Objects.requireNonNull(reference, "code reading material publication");
      ReopenedAnalysisStepPublication material = steps.reopen(reference);
      if (!material.reference().equals(reference)
          || reference.address().analysisStepKey() != AnalysisStepKey.BUSINESS_FLOWS
          || material.semanticPayloads().size() != 1) {
        throw invalid();
      }
      VerifiedCanonicalPayload payload = material.semanticPayloads().get(0);
      if (!CodeReadingMaterialPublisher.FILE_NAME.equals(payload.descriptor().fileName())
          || !CodeReadingMaterialPublisher.ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
          || !CodeReadingMaterialPublisher.SCHEMA_VERSION.equals(
              payload.descriptor().schemaVersion())
          || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
        throw invalid();
      }

      SavedMaterial saved = parse(payload.canonicalUtf8());
      ReopenedAnalysisStepPublication source =
          reopen(
              saved.header().sourceInventory().publication(),
              AnalysisStepKey.VERIFIED_SOURCE_INVENTORY);
      ReopenedAnalysisStepPublication discovery =
          reopen(saved.discovery().publication(), AnalysisStepKey.APPLICATION_DISCOVERY);
      ReopenedAnalysisStepPublication navigation =
          reopen(
              saved.header().navigationPublication().publication(), AnalysisStepKey.PROGRAM_GRAPHS);
      ReopenedAnalysisStepPublication persistence =
          reopen(saved.header().persistencePublication(), AnalysisStepKey.PROVEN_CODE_FACTS);
      requireUpstreamChain(material, source, discovery, navigation, persistence);

      JavaCodeIndex javaIndex =
          new JavaCodeIndexReader(steps).reopen(saved.header().navigationPublication(), navigation);
      PersistenceMaterialIndex persistenceIndex =
          new PersistenceMaterialReader(steps)
              .reopen(saved.header().persistencePublication(), persistence);
      if (!saved.header().sourceSnapshotId().equals(javaIndex.snapshotId())
          || !saved.header().sourceSnapshotId().equals(persistenceIndex.header().sourceSnapshotId())
          || !saved
              .header()
              .navigationPublication()
              .equals(persistenceIndex.header().navigationPublication())) {
        throw invalid();
      }
      return hydrate(saved, javaIndex, persistenceIndex);
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "CODE_READING_MATERIAL_SET_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
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

  private static void requireUpstreamChain(
      ReopenedAnalysisStepPublication material,
      ReopenedAnalysisStepPublication source,
      ReopenedAnalysisStepPublication discovery,
      ReopenedAnalysisStepPublication navigation,
      ReopenedAnalysisStepPublication persistence) {
    if (!sameRun(source, discovery, navigation, persistence, material)
        || !sameControls(source, discovery, navigation, persistence, material)
        || !discovery.receipt().upstreamAnalysisStepReferences().equals(List.of(source.reference()))
        || !navigation
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference()))
        || !persistence
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(List.of(source.reference(), discovery.reference(), navigation.reference()))
        || !material
            .receipt()
            .upstreamAnalysisStepReferences()
            .equals(
                List.of(
                    source.reference(),
                    discovery.reference(),
                    navigation.reference(),
                    persistence.reference()))) {
      throw invalid();
    }
  }

  private static boolean sameRun(
      ReopenedAnalysisStepPublication first, ReopenedAnalysisStepPublication... rest) {
    return java.util.stream.Stream.of(rest)
        .allMatch(
            value ->
                first.reference().address().runId().equals(value.reference().address().runId()));
  }

  private static boolean sameControls(
      ReopenedAnalysisStepPublication first, ReopenedAnalysisStepPublication... rest) {
    return java.util.stream.Stream.of(rest)
        .allMatch(value -> first.receipt().controls().equals(value.receipt().controls()));
  }

  private CodeReadingMaterialSet hydrate(
      SavedMaterial saved, JavaCodeIndex javaIndex, PersistenceMaterialIndex persistenceIndex) {
    Map<String, EntrySeed> entries = entrySeeds(javaIndex);
    Map<String, EntryCodeContext.MethodCode> methods = methods(javaIndex);
    Map<EntryCallKey, EntryCodeContext.CallSite> calls = calls(javaIndex);
    PersistenceReferences persistence = PersistenceReferences.from(persistenceIndex);

    List<CodeReadingMaterialSet.Packet> packets = new ArrayList<>();
    for (SavedPacket packet : saved.packets()) {
      List<EntrySeed> packetEntries =
          packet.entryIds().stream().map(key -> required(entries, key)).toList();
      List<EntryCodeContext.MethodCode> packetMethods =
          packet.methodKeys().stream().map(key -> required(methods, key)).toList();
      List<CodeReadingMaterialSet.EntryCall> packetCalls =
          packet.callKeys().stream()
              .map(
                  key ->
                      new CodeReadingMaterialSet.EntryCall(
                          key.entryId(),
                          required(calls, new EntryCallKey(key.entryId(), key.callKey()))))
              .toList();
      CodeReadingMaterialSet.PersistenceSelection selection =
          new CodeReadingMaterialSet.PersistenceSelection(
              packet.persistence().resourcePaths().stream()
                  .map(key -> required(persistence.resources(), key))
                  .toList(),
              packet.persistence().statementRefs().stream()
                  .map(key -> required(persistence.statements(), key))
                  .toList(),
              packet.persistence().bindingMethodKeys().stream()
                  .map(key -> required(persistence.bindings(), key))
                  .toList(),
              packet.persistence().sqlAnalysisStatementRefs().stream()
                  .map(key -> required(persistence.sqlAnalyses(), key))
                  .toList(),
              packet.persistence().diagnosticKeys().stream()
                  .map(key -> required(persistence.diagnostics(), key))
                  .toList());
      CodeReadingMaterialSet.Packet hydrated =
          new CodeReadingMaterialSet.Packet(
              packet.packetId(),
              packetEntries,
              packetMethods,
              packetCalls,
              selection,
              packet.sourceReferences(),
              packet.unselectedUnits(),
              packet.limitations(),
              packet.selfContainedUtf8Bytes());
      if (CodeReadingMaterialMarkdown.renderPacket(hydrated).getBytes(StandardCharsets.UTF_8).length
          != hydrated.selfContainedUtf8Bytes()) {
        throw invalid();
      }
      packets.add(hydrated);
    }
    Map<String, Set<String>> entriesByPacket = new LinkedHashMap<>();
    for (CodeReadingMaterialSet.Packet packet : packets) {
      entriesByPacket.put(
          packet.packetId(),
          packet.entries().stream()
              .map(EntrySeed::entryId)
              .collect(java.util.stream.Collectors.toSet()));
    }
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    for (CodeReadingMaterialSet.EntryCoverage value : saved.coverage()) {
      required(entries, value.entryId());
      if (value.packetIds().stream()
          .anyMatch(
              packetId ->
                  !entriesByPacket.containsKey(packetId)
                      || !entriesByPacket.get(packetId).contains(value.entryId()))) {
        throw invalid();
      }
      coverage.add(value);
    }
    if (!coverage.stream()
        .map(CodeReadingMaterialSet.EntryCoverage::entryId)
        .collect(java.util.stream.Collectors.toSet())
        .equals(entries.keySet())) {
      throw invalid();
    }
    return new CodeReadingMaterialSet(saved.header(), packets, coverage);
  }

  private SavedMaterial parse(ImmutableBytes bytes) {
    List<Line> lines = lines(bytes);
    if (lines.isEmpty() || !"HEADER".equals(lines.get(0).type())) {
      throw invalid();
    }
    Line headerLine = only(lines, "HEADER");
    if (!"header".equals(headerLine.key())) {
      throw invalid();
    }
    requireFields(
        headerLine.payload(),
        Set.of(
            "producer",
            "sourceInventory",
            "applicationDiscovery",
            "navigationPublication",
            "persistencePublication",
            "sourceSnapshotId",
            "profile"));
    if (!PRODUCER.equals(text(headerLine.payload(), "producer"))) {
      throw invalid();
    }
    CodeReadingMaterialSet.Header header =
        new CodeReadingMaterialSet.Header(
            convert(
                headerLine.payload().get("sourceInventory"),
                VerifiedSourceInventoryReference.class),
            convert(
                headerLine.payload().get("navigationPublication"), ProgramGraphsReference.class),
            convert(
                headerLine.payload().get("persistencePublication"),
                AnalysisStepPublicationReference.class),
            text(headerLine.payload(), "sourceSnapshotId"),
            convert(headerLine.payload().get("profile"), CodeReadingMaterialProfile.class));
    ApplicationDiscoveryReference discovery =
        convert(
            headerLine.payload().get("applicationDiscovery"), ApplicationDiscoveryReference.class);

    List<SavedPacket> packets = new ArrayList<>();
    List<CodeReadingMaterialSet.EntryCoverage> coverage = new ArrayList<>();
    boolean seenCoverage = false;
    for (int index = 1; index < lines.size(); index++) {
      Line line = lines.get(index);
      if ("PACKET".equals(line.type())) {
        if (seenCoverage) {
          throw invalid();
        }
        packets.add(parsePacket(line));
      } else if ("ENTRY_COVERAGE".equals(line.type())) {
        seenCoverage = true;
        CodeReadingMaterialSet.EntryCoverage value =
            convert(line.payload(), CodeReadingMaterialSet.EntryCoverage.class);
        if (!line.key().equals(value.entryId())) {
          throw invalid();
        }
        coverage.add(value);
      } else {
        throw invalid();
      }
    }
    return new SavedMaterial(header, discovery, List.copyOf(packets), List.copyOf(coverage));
  }

  private SavedPacket parsePacket(Line line) {
    requireFields(
        line.payload(),
        Set.of(
            "packetId",
            "entryIds",
            "methodKeys",
            "callKeys",
            "persistence",
            "sourceReferences",
            "unselectedUnits",
            "limitations",
            "selfContainedUtf8Bytes"));
    String packetId = text(line.payload(), "packetId");
    if (!line.key().equals(packetId)) {
      throw invalid();
    }
    List<CallKey> callKeys = new ArrayList<>();
    for (JsonNode call : array(line.payload().get("callKeys"))) {
      if (!(call instanceof ObjectNode key)) {
        throw invalid();
      }
      requireFields(key, Set.of("entryId", "callKey"));
      callKeys.add(new CallKey(text(key, "entryId"), text(key, "callKey")));
    }
    return new SavedPacket(
        packetId,
        strings(line.payload().get("entryIds")),
        strings(line.payload().get("methodKeys")),
        List.copyOf(callKeys),
        persistence(line.payload().get("persistence")),
        convertList(
            line.payload().get("sourceReferences"), CodeReadingMaterialSet.SourceReference.class),
        convertList(
            line.payload().get("unselectedUnits"), CodeReadingMaterialSet.UnselectedUnit.class),
        strings(line.payload().get("limitations")),
        nonNegativeLong(line.payload().get("selfContainedUtf8Bytes")));
  }

  private static SavedPersistence persistence(JsonNode value) {
    if (!(value instanceof ObjectNode node)) {
      throw invalid();
    }
    requireFields(
        node,
        Set.of(
            "resourcePaths",
            "statementRefs",
            "bindingMethodKeys",
            "sqlAnalysisStatementRefs",
            "diagnosticKeys"));
    return new SavedPersistence(
        strings(node.get("resourcePaths")),
        strings(node.get("statementRefs")),
        strings(node.get("bindingMethodKeys")),
        strings(node.get("sqlAnalysisStatementRefs")),
        strings(node.get("diagnosticKeys")));
  }

  private List<Line> lines(ImmutableBytes bytes) {
    String content = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (content.isEmpty() || !content.endsWith("\n")) {
      throw invalid();
    }
    List<Line> result = new ArrayList<>();
    Set<String> identities = new HashSet<>();
    for (String text : content.substring(0, content.length() - 1).split("\n", -1)) {
      if (text.isBlank()) {
        throw invalid();
      }
      JsonNode parsed =
          json.parseCanonical(ImmutableBytes.copyOf(text.getBytes(StandardCharsets.UTF_8)));
      if (!(parsed instanceof ObjectNode line)) {
        throw invalid();
      }
      requireFields(line, Set.of("schemaVersion", "recordType", "key", "payload"));
      String type = text(line, "recordType");
      String key = text(line, "key");
      if (!CodeReadingMaterialPublisher.SCHEMA_VERSION.equals(text(line, "schemaVersion"))
          || !RECORD_TYPES.contains(type)
          || !(line.get("payload") instanceof ObjectNode payload)
          || !identities.add(type + "\u0000" + key)) {
        throw invalid();
      }
      result.add(new Line(type, key, payload));
    }
    return List.copyOf(result);
  }

  private static Map<String, EntrySeed> entrySeeds(JavaCodeIndex index) {
    Map<String, EntrySeed> result = new LinkedHashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      putUnique(result, entry.seed().entryId(), entry.seed());
    }
    return result;
  }

  private static Map<String, EntryCodeContext.MethodCode> methods(JavaCodeIndex index) {
    Map<String, EntryCodeContext.MethodCode> result = new LinkedHashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      if (entry.context() == null) {
        continue;
      }
      for (EntryCodeContext.MethodCode method : entry.context().methods()) {
        putConsistent(result, method.methodKey(), method);
      }
    }
    return result;
  }

  private static Map<EntryCallKey, EntryCodeContext.CallSite> calls(JavaCodeIndex index) {
    Map<EntryCallKey, EntryCodeContext.CallSite> result = new HashMap<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      if (entry.context() == null) {
        continue;
      }
      for (EntryCodeContext.CallSite call : entry.context().calls()) {
        putConsistent(result, new EntryCallKey(entry.seed().entryId(), call.callKey()), call);
      }
    }
    return result;
  }

  private static <K, V> void putUnique(Map<K, V> values, K key, V value) {
    if (values.put(key, value) != null) {
      throw invalid();
    }
  }

  private static <K, V> void putConsistent(Map<K, V> values, K key, V value) {
    V previous = values.putIfAbsent(key, value);
    if (previous != null && !previous.equals(value)) {
      throw invalid();
    }
  }

  private static <K, V> V required(Map<K, V> values, K key) {
    V value = values.get(key);
    if (value == null) {
      throw invalid();
    }
    return value;
  }

  private static Line only(List<Line> lines, String type) {
    List<Line> matches = lines.stream().filter(line -> type.equals(line.type())).toList();
    if (matches.size() != 1) {
      throw invalid();
    }
    return matches.get(0);
  }

  private static void requireFields(ObjectNode node, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    node.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw invalid();
    }
  }

  private static String text(ObjectNode node, String name) {
    JsonNode value = node.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw invalid();
    }
    return value.textValue();
  }

  private static ArrayNode array(JsonNode value) {
    if (!(value instanceof ArrayNode array)) {
      throw invalid();
    }
    return array;
  }

  private static List<String> strings(JsonNode value) {
    List<String> result = new ArrayList<>();
    for (JsonNode item : array(value)) {
      if (!item.isTextual() || item.textValue().isBlank()) {
        throw invalid();
      }
      result.add(item.textValue());
    }
    return List.copyOf(result);
  }

  private static long nonNegativeLong(JsonNode value) {
    if (value == null
        || !value.isIntegralNumber()
        || !value.canConvertToLong()
        || value.longValue() < 0L) {
      throw invalid();
    }
    return value.longValue();
  }

  private static <T> T convert(JsonNode value, Class<T> type) {
    try {
      return MAPPER.treeToValue(value, type);
    } catch (JsonProcessingException | IllegalArgumentException failure) {
      throw invalid();
    }
  }

  private static <T> List<T> convertList(JsonNode value, Class<T> type) {
    List<T> result = new ArrayList<>();
    for (JsonNode item : array(value)) {
      result.add(convert(item, type));
    }
    return List.copyOf(result);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("CODE_READING_MATERIAL_SET_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("CODE_READING_MATERIAL_SET_INVALID", cause);
  }

  private record Line(String type, String key, ObjectNode payload) {}

  private record EntryCallKey(String entryId, String callKey) {}

  private record CallKey(String entryId, String callKey) {}

  private record SavedPersistence(
      List<String> resourcePaths,
      List<String> statementRefs,
      List<String> bindingMethodKeys,
      List<String> sqlAnalysisStatementRefs,
      List<String> diagnosticKeys) {}

  private record SavedPacket(
      String packetId,
      List<String> entryIds,
      List<String> methodKeys,
      List<CallKey> callKeys,
      SavedPersistence persistence,
      List<CodeReadingMaterialSet.SourceReference> sourceReferences,
      List<CodeReadingMaterialSet.UnselectedUnit> unselectedUnits,
      List<String> limitations,
      long selfContainedUtf8Bytes) {}

  private record SavedMaterial(
      CodeReadingMaterialSet.Header header,
      ApplicationDiscoveryReference discovery,
      List<SavedPacket> packets,
      List<CodeReadingMaterialSet.EntryCoverage> coverage) {}

  private record PersistenceReferences(
      Map<String, PersistenceMaterialIndex.Resource> resources,
      Map<String, PersistenceMaterialIndex.Statement> statements,
      Map<String, PersistenceMaterialIndex.JavaBinding> bindings,
      Map<String, PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses,
      Map<String, PersistenceMaterialIndex.Diagnostic> diagnostics) {

    private static PersistenceReferences from(PersistenceMaterialIndex index) {
      return new PersistenceReferences(
          map(index.resources(), PersistenceMaterialIndex.Resource::resourcePath),
          map(index.statements(), PersistenceMaterialIndex.Statement::statementRef),
          map(index.bindings(), PersistenceMaterialIndex.JavaBinding::methodKey),
          map(index.sqlAnalyses(), PersistenceMaterialIndex.SqlAnalysis::statementRef),
          map(index.diagnostics(), CodeReadingMaterialPublisher::diagnosticKey));
    }

    private static <T> Map<String, T> map(List<T> values, Function<T, String> key) {
      Map<String, T> result = new LinkedHashMap<>();
      for (T value : values) {
        putUnique(result, key.apply(value), value);
      }
      return result;
    }
  }
}
