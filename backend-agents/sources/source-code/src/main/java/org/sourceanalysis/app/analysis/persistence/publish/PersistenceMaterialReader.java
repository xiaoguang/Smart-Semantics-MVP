package org.sourceanalysis.app.analysis.persistence.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Contract seam for fresh reopening of the one-file Step 04 persistence-material publication. */
public final class PersistenceMaterialReader {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Set<String> RECORD_TYPES =
      Set.of("HEADER", "RESOURCE", "STATEMENT", "JAVA_BINDING", "SQL_ANALYSIS", "DIAGNOSTIC");
  private static final Set<String> PRODUCERS =
      Set.of("persistence-analysis-v1", "persistence-analysis-v2");
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalAnalysisStepArtifactStore sourceSteps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public PersistenceMaterialReader(CanonicalAnalysisStepArtifactStore steps) {
    this(steps, steps);
  }

  /** Uses the source-preparation store only for exact R0 receipt validation. */
  public PersistenceMaterialReader(
      CanonicalAnalysisStepArtifactStore steps, CanonicalAnalysisStepArtifactStore sourceSteps) {
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
    this.sourceSteps = Objects.requireNonNull(sourceSteps, "source analysis-step artifact store");
  }

  /** Reconstructs the saved immutable index without invoking XML, SQL, or Java analysis. */
  public PersistenceMaterialIndex reopen(AnalysisStepPublicationReference reference) {
    return reopen(reference, steps.reopen(reference));
  }

  /** Reconstructs the index from an already reopened and still structurally verified Step 04. */
  public PersistenceMaterialIndex reopen(
      AnalysisStepPublicationReference reference, ReopenedAnalysisStepPublication step) {
    try {
      Objects.requireNonNull(reference, "persistence publication");
      Objects.requireNonNull(step, "reopened persistence publication");
      if (!step.reference().equals(reference)
          || reference.address().analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
          || step.semanticPayloads().size() != 1) {
        throw invalid();
      }
      VerifiedCanonicalPayload payload = step.semanticPayloads().get(0);
      if (!PersistenceMaterialPublisher.FILE_NAME.equals(payload.descriptor().fileName())
          || !PersistenceMaterialPublisher.ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
          || !PersistenceMaterialPublisher.SCHEMA_VERSION.equals(
              payload.descriptor().schemaVersion())
          || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
        throw invalid();
      }
      return parse(payload.canonicalUtf8());
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "PERSISTENCE_MATERIAL_INDEX_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  /**
   * Reopens a cross-run Step04 v2 publication while proving exact R0/R1/R2 ancestry and distinct
   * receipt controls. Historical {@link #reopen(AnalysisStepPublicationReference)} remains a
   * tolerant reader for the retained v1 publication contract.
   */
  public PersistenceMaterialIndex reopenTechnical(
      AnalysisStepPublicationReference reference,
      AnalysisRunId expectedR2,
      VerifiedSourceInventoryReference expectedR0,
      ApplicationDiscoveryReference expectedR1Discovery,
      ProgramGraphsReference expectedR1Navigation,
      ArtifactControls r1Controls,
      ArtifactControls r2Controls) {
    try {
      Objects.requireNonNull(reference, "persistence publication");
      Objects.requireNonNull(expectedR2, "R2 run ID");
      Objects.requireNonNull(expectedR0, "R0 source");
      Objects.requireNonNull(expectedR1Discovery, "R1 discovery");
      Objects.requireNonNull(expectedR1Navigation, "R1 navigation");
      Objects.requireNonNull(r1Controls, "R1 controls");
      Objects.requireNonNull(r2Controls, "R2 controls");
      ReopenedAnalysisStepPublication source = sourceSteps.reopen(expectedR0.publication());
      ReopenedAnalysisStepPublication discovery = steps.reopen(expectedR1Discovery.publication());
      ReopenedAnalysisStepPublication navigation = steps.reopen(expectedR1Navigation.publication());
      ReopenedAnalysisStepPublication persistence = steps.reopen(reference);
      if (!source.reference().equals(expectedR0.publication())
          || source.reference().address().analysisStepKey()
              != AnalysisStepKey.VERIFIED_SOURCE_INVENTORY
          || !discovery.reference().equals(expectedR1Discovery.publication())
          || discovery.reference().address().analysisStepKey()
              != AnalysisStepKey.APPLICATION_DISCOVERY
          || !navigation.reference().equals(expectedR1Navigation.publication())
          || navigation.reference().address().analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
          || !persistence.reference().equals(reference)
          || !reference.address().runId().equals(expectedR2)
          || !persistence.receipt().controls().equals(r2Controls)
          || !discovery.receipt().controls().equals(r1Controls)
          || !navigation.receipt().controls().equals(r1Controls)
          || source.reference().address().runId().equals(discovery.reference().address().runId())
          || !discovery
              .reference()
              .address()
              .runId()
              .equals(navigation.reference().address().runId())
          || expectedR2.equals(source.reference().address().runId())
          || expectedR2.equals(discovery.reference().address().runId())
          || !discovery
              .receipt()
              .upstreamAnalysisStepReferences()
              .equals(List.of(source.reference()))
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
      return reopen(reference, persistence);
    } catch (RuntimeException failure) {
      if (failure instanceof IllegalArgumentException
          && "PERSISTENCE_MATERIAL_INDEX_INVALID".equals(failure.getMessage())) {
        throw failure;
      }
      throw invalid(failure);
    }
  }

  private PersistenceMaterialIndex parse(ImmutableBytes bytes) {
    List<Line> lines = lines(bytes);
    Line headerLine = only(lines, "HEADER");
    if (!"header".equals(headerLine.key())) {
      throw invalid();
    }
    ObjectNode header = headerLine.payload();
    requireFields(
        header, Set.of("producer", "status", "sourceSnapshotId", "navigationPublication", "tools"));
    if (!PRODUCERS.contains(text(header, "producer"))) {
      throw invalid();
    }
    PersistenceMaterialIndex.Status status;
    try {
      status = PersistenceMaterialIndex.Status.valueOf(text(header, "status"));
    } catch (IllegalArgumentException failure) {
      throw invalid();
    }
    PersistenceMaterialIndex.Header indexHeader =
        new PersistenceMaterialIndex.Header(
            status,
            text(header, "sourceSnapshotId"),
            convert(header.get("navigationPublication"), ProgramGraphsReference.class),
            convertList(header.get("tools"), PersistenceMaterialIndex.Tool.class));
    List<PersistenceMaterialIndex.Resource> resources =
        records(
            lines,
            "RESOURCE",
            PersistenceMaterialIndex.Resource.class,
            PersistenceMaterialIndex.Resource::resourcePath);
    List<PersistenceMaterialIndex.Statement> statements =
        records(
            lines,
            "STATEMENT",
            PersistenceMaterialIndex.Statement.class,
            PersistenceMaterialIndex.Statement::statementRef);
    List<PersistenceMaterialIndex.JavaBinding> bindings =
        records(
            lines,
            "JAVA_BINDING",
            PersistenceMaterialIndex.JavaBinding.class,
            PersistenceMaterialIndex.JavaBinding::methodKey);
    List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses =
        records(
            lines,
            "SQL_ANALYSIS",
            PersistenceMaterialIndex.SqlAnalysis.class,
            PersistenceMaterialIndex.SqlAnalysis::statementRef);
    List<PersistenceMaterialIndex.Diagnostic> diagnostics =
        records(
            lines,
            "DIAGNOSTIC",
            PersistenceMaterialIndex.Diagnostic.class,
            value ->
                "diagnostic:"
                    + sha256(
                        value.code() + "\u0000" + value.subjectRef() + "\u0000" + value.detail()));
    requireStructuralReferences(resources, statements, bindings, sqlAnalyses);
    return new PersistenceMaterialIndex(
        indexHeader, resources, statements, bindings, sqlAnalyses, diagnostics);
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
      if (!PersistenceMaterialPublisher.SCHEMA_VERSION.equals(text(line, "schemaVersion"))
          || !RECORD_TYPES.contains(type)
          || !(line.get("payload") instanceof ObjectNode payload)
          || !identities.add(type + "\u0000" + key)) {
        throw invalid();
      }
      result.add(new Line(type, key, payload));
    }
    return List.copyOf(result);
  }

  private static <T> List<T> records(
      List<Line> lines, String type, Class<T> valueType, Function<T, String> expectedKey) {
    List<T> records = new ArrayList<>();
    for (Line line : lines) {
      if (!type.equals(line.type())) {
        continue;
      }
      T value = convert(line.payload(), valueType);
      if (!line.key().equals(expectedKey.apply(value))) {
        throw invalid();
      }
      records.add(value);
    }
    return List.copyOf(records);
  }

  private static void requireStructuralReferences(
      List<PersistenceMaterialIndex.Resource> resources,
      List<PersistenceMaterialIndex.Statement> statements,
      List<PersistenceMaterialIndex.JavaBinding> bindings,
      List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses) {
    Set<String> resourcePaths =
        resources.stream()
            .map(PersistenceMaterialIndex.Resource::resourcePath)
            .collect(java.util.stream.Collectors.toSet());
    if (resources.stream()
            .flatMap(resource -> resource.dependencyResourcePaths().stream())
            .anyMatch(path -> !resourcePaths.contains(path))
        || statements.stream()
            .anyMatch(statement -> !resourcePaths.contains(statement.resourceRef()))) {
      throw invalid();
    }
    Set<String> statementRefs =
        statements.stream()
            .map(PersistenceMaterialIndex.Statement::statementRef)
            .collect(java.util.stream.Collectors.toSet());
    if (bindings.stream()
            .flatMap(binding -> binding.statementRefs().stream())
            .anyMatch(reference -> !statementRefs.contains(reference.statementRef()))
        || sqlAnalyses.stream()
            .anyMatch(analysis -> !statementRefs.contains(analysis.statementRef()))) {
      throw invalid();
    }
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

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("PERSISTENCE_MATERIAL_INDEX_INVALID");
  }

  private static IllegalArgumentException invalid(Throwable cause) {
    return new IllegalArgumentException("PERSISTENCE_MATERIAL_INDEX_INVALID", cause);
  }

  private record Line(String type, String key, ObjectNode payload) {}
}
