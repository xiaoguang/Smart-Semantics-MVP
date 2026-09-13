package org.sourceanalysis.app.analysis.code.publish;

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
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Fresh-reopens and reconstructs the neutral Java index without rerunning an engine. */
public final class JavaCodeIndexReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> RECORD_TYPES =
      Set.of("ENGINE", "TYPE", "METHOD", "CALL", "ENTRY_MEMBERSHIP", "DIAGNOSTIC");
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public JavaCodeIndexReader(CanonicalAnalysisStepArtifactStore steps) {
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
  }

  /** Returns the exact index stored by the supplied Step 03 reference. */
  public JavaCodeIndex reopen(ProgramGraphsReference reference) {
    try {
      Objects.requireNonNull(reference, "program graphs reference");
      ReopenedAnalysisStepPublication step = steps.reopen(reference.publication());
      if (!step.reference().equals(reference.publication())
          || step.reference().address().analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
          || (step.semanticPayloads().size() != 1 && step.semanticPayloads().size() != 8)) {
        throw invalid();
      }
      List<VerifiedCanonicalPayload> matches =
          step.semanticPayloads().stream()
              .filter(
                  value ->
                      JavaCodeIndexPublicationSpecifier.FILE_NAME.equals(
                          value.descriptor().fileName()))
              .toList();
      if (matches.size() != 1) {
        throw invalid();
      }
      VerifiedCanonicalPayload payload = matches.get(0);
      if (!JavaCodeIndexPublicationSpecifier.FILE_NAME.equals(payload.descriptor().fileName())
          || !JavaCodeIndexPublicationSpecifier.ARTIFACT_TYPE.equals(
              payload.descriptor().artifactType())
          || !JavaCodeIndexPublicationSpecifier.SCHEMA_VERSION.equals(
              payload.descriptor().schemaVersion())
          || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
        throw invalid();
      }
      return parse(payload.canonicalUtf8());
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "JAVA_CODE_INDEX_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid();
    }
  }

  private JavaCodeIndex parse(ImmutableBytes bytes) {
    List<Line> lines = lines(bytes);
    Line engineLine = only(lines, "ENGINE");
    ObjectNode engine = engineLine.payload();
    requireFields(
        engine,
        Set.of(
            "descriptor",
            "snapshotId",
            "snapshotRef",
            "files",
            "annotations",
            "fields",
            "technicalEnhancements"));
    EngineDescriptor descriptor = convert(engine.get("descriptor"), EngineDescriptor.class);
    String snapshotId = text(engine, "snapshotId");
    ArtifactReference snapshotRef = convert(engine.get("snapshotRef"), ArtifactReference.class);
    List<String> files = strings(engine.get("files"));
    List<JavaDeclarationCatalog.AnnotationView> annotations =
        convertList(engine.get("annotations"), JavaDeclarationCatalog.AnnotationView.class);
    List<JavaDeclarationCatalog.FieldDeclarationView> fields =
        convertList(engine.get("fields"), JavaDeclarationCatalog.FieldDeclarationView.class);
    EntryCodeContext.TechnicalEnhancements enhancements =
        convert(engine.get("technicalEnhancements"), EntryCodeContext.TechnicalEnhancements.class);

    List<JavaDeclarationCatalog.TypeDeclaration> types =
        lines.stream()
            .filter(value -> "TYPE".equals(value.type()))
            .map(value -> convert(value.payload(), JavaDeclarationCatalog.TypeDeclaration.class))
            .toList();
    Map<String, MethodRecord> methods = new LinkedHashMap<>();
    for (Line line : lines) {
      if (!"METHOD".equals(line.type())) continue;
      requireFields(line.payload(), Set.of("declaration", "code"));
      JavaDeclarationCatalog.MethodDeclarationView declaration =
          line.payload().get("declaration").isNull()
              ? null
              : convert(
                  line.payload().get("declaration"),
                  JavaDeclarationCatalog.MethodDeclarationView.class);
      EntryCodeContext.MethodCode code =
          line.payload().get("code").isNull()
              ? null
              : convert(line.payload().get("code"), EntryCodeContext.MethodCode.class);
      if ((declaration == null && code == null)
          || (declaration != null && !line.key().equals(declaration.methodKey()))
          || (code != null && !line.key().equals(code.methodKey()))
          || methods.put(line.key(), new MethodRecord(declaration, code)) != null) {
        throw invalid();
      }
    }
    List<JavaDeclarationCatalog.MethodDeclarationView> declarations =
        methods.values().stream().map(MethodRecord::declaration).filter(Objects::nonNull).toList();
    Map<String, EntryCodeContext.CallSite> calls = new HashMap<>();
    for (Line line : lines) {
      if (!"CALL".equals(line.type())) continue;
      EntryCodeContext.CallSite call = convert(line.payload(), EntryCodeContext.CallSite.class);
      if (!line.key().equals(call.callKey()) || calls.put(line.key(), call) != null) {
        throw invalid();
      }
    }
    Map<String, String> diagnostics = new LinkedHashMap<>();
    for (Line line : lines) {
      if (!"DIAGNOSTIC".equals(line.type())) continue;
      requireFields(line.payload(), Set.of("path", "detail"));
      String path = text(line.payload(), "path");
      if (!line.key().equals(path)
          || diagnostics.put(path, text(line.payload(), "detail")) != null) {
        throw invalid();
      }
    }
    JavaDeclarationCatalog catalog =
        new JavaDeclarationCatalog(
            snapshotId, files, types, declarations, annotations, fields, diagnostics);
    List<JavaCodeIndex.EntryCollection> entries = new ArrayList<>();
    for (Line line : lines) {
      if (!"ENTRY_MEMBERSHIP".equals(line.type())) continue;
      ObjectNode membership = line.payload();
      requireFields(
          membership,
          Set.of(
              "seed",
              "collectionStatus",
              "reason",
              "methodKeys",
              "callKeys",
              "supportingSources",
              "limitations"));
      EntrySeed seed = convert(membership.get("seed"), EntrySeed.class);
      if (!line.key().equals(seed.entryId())) throw invalid();
      String status = text(membership, "collectionStatus");
      if ("NOT_COLLECTED".equals(status)) {
        if (!strings(membership.get("methodKeys")).isEmpty()
            || !strings(membership.get("callKeys")).isEmpty()
            || !array(membership.get("supportingSources")).isEmpty()
            || !array(membership.get("limitations")).isEmpty()) {
          throw invalid();
        }
        entries.add(JavaCodeIndex.EntryCollection.notCollected(seed, text(membership, "reason")));
      } else if ("COLLECTED".equals(status) && membership.get("reason").isNull()) {
        List<EntryCodeContext.MethodCode> entryMethods =
            strings(membership.get("methodKeys")).stream()
                .map(key -> requiredCode(methods, key))
                .toList();
        List<EntryCodeContext.CallSite> entryCalls =
            strings(membership.get("callKeys")).stream()
                .map(key -> requiredCall(calls, key))
                .toList();
        EntryCodeContext context =
            new EntryCodeContext(
                EntryCodeContext.SCHEMA_VERSION,
                seed.entryId(),
                seed.methodKey(),
                entryMethods,
                entryCalls,
                convertList(
                    membership.get("supportingSources"), EntryCodeContext.SupportingSource.class),
                convertList(membership.get("limitations"), EntryCodeContext.Limitation.class),
                enhancements);
        entries.add(JavaCodeIndex.EntryCollection.collected(seed, context));
      } else {
        throw invalid();
      }
    }
    return new JavaCodeIndex(descriptor, snapshotId, snapshotRef, catalog, entries, enhancements);
  }

  private List<Line> lines(ImmutableBytes bytes) {
    String content = new String(bytes.copyToByteArray(), StandardCharsets.UTF_8);
    if (content.isEmpty() || !content.endsWith("\n")) throw invalid();
    List<Line> result = new ArrayList<>();
    Set<String> identities = new HashSet<>();
    for (String text : content.substring(0, content.length() - 1).split("\\n", -1)) {
      if (text.isBlank()) throw invalid();
      JsonNode parsed =
          json.parseCanonical(ImmutableBytes.copyOf(text.getBytes(StandardCharsets.UTF_8)));
      if (!(parsed instanceof ObjectNode line)) throw invalid();
      requireFields(line, Set.of("schemaVersion", "recordType", "key", "payload"));
      String type = text(line, "recordType");
      String key = text(line, "key");
      if (!JavaCodeIndexPublicationSpecifier.SCHEMA_VERSION.equals(text(line, "schemaVersion"))
          || !RECORD_TYPES.contains(type)
          || !(line.get("payload") instanceof ObjectNode payload)
          || !identities.add(type + "\u0000" + key)) {
        throw invalid();
      }
      result.add(new Line(type, key, payload));
    }
    return List.copyOf(result);
  }

  private static EntryCodeContext.MethodCode requiredCode(
      Map<String, MethodRecord> methods, String key) {
    MethodRecord method = methods.get(key);
    if (method == null || method.code() == null) throw invalid();
    return method.code();
  }

  private static EntryCodeContext.CallSite requiredCall(
      Map<String, EntryCodeContext.CallSite> calls, String key) {
    EntryCodeContext.CallSite call = calls.get(key);
    if (call == null) throw invalid();
    return call;
  }

  private static Line only(List<Line> lines, String type) {
    List<Line> matches = lines.stream().filter(value -> type.equals(value.type())).toList();
    if (matches.size() != 1) throw invalid();
    return matches.get(0);
  }

  private static void requireFields(ObjectNode node, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    node.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw invalid();
  }

  private static String text(ObjectNode node, String name) {
    JsonNode value = node.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) throw invalid();
    return value.textValue();
  }

  private static ArrayNode array(JsonNode value) {
    if (!(value instanceof ArrayNode array)) throw invalid();
    return array;
  }

  private static List<String> strings(JsonNode value) {
    List<String> result = new ArrayList<>();
    for (JsonNode item : array(value)) {
      if (!item.isTextual() || item.textValue().isBlank()) throw invalid();
      result.add(item.textValue());
    }
    return List.copyOf(result);
  }

  private static <T> T convert(JsonNode value, Class<T> type) {
    try {
      return MAPPER.treeToValue(value, type);
    } catch (JsonProcessingException failure) {
      throw invalid();
    }
  }

  private static <T> List<T> convertList(JsonNode value, Class<T> type) {
    List<T> result = new ArrayList<>();
    for (JsonNode item : array(value)) result.add(convert(item, type));
    return List.copyOf(result);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("JAVA_CODE_INDEX_INVALID");
  }

  private record Line(String type, String key, ObjectNode payload) {}

  private record MethodRecord(
      JavaDeclarationCatalog.MethodDeclarationView declaration, EntryCodeContext.MethodCode code) {}
}
