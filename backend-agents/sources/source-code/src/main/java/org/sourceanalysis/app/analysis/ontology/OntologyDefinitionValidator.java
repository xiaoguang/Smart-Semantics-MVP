package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Structural and source-reference checks shared by live responses and saved ontology results. */
final class OntologyDefinitionValidator {
  private static final String ROOT = "/org/sourceanalysis/app/analysis/ontology/";
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  ImmutableBytes schema(Stage stage) {
    String name =
        stage == Stage.EXTRACT ? "task-candidate-v1.schema.json" : "task-review-v1.schema.json";
    try (InputStream input = getClass().getResourceAsStream(ROOT + name)) {
      if (input == null) {
        throw new IllegalStateException("ONTOLOGY_TASK_RESOURCE_MISSING");
      }
      return json.canonicalizeStrictJson(ImmutableBytes.copyOf(input.readAllBytes()));
    } catch (IOException unreadable) {
      throw new IllegalStateException("ONTOLOGY_TASK_RESOURCE_UNREADABLE", unreadable);
    }
  }

  JsonNode validate(
      ImmutableBytes actual,
      OntologyTaskRunner.TaskKind kind,
      Stage stage,
      OntologyReadingPacket packet,
      int maxOutputBytes) {
    if (actual.size() > maxOutputBytes) {
      throw new IllegalArgumentException("ONTOLOGY_MODEL_RESPONSE_TOO_LARGE");
    }
    JsonNode document = json.parseStrictJson(actual);
    validate(document, kind, stage, packet);
    return document;
  }

  void validate(
      JsonNode document,
      OntologyTaskRunner.TaskKind kind,
      Stage stage,
      OntologyReadingPacket packet) {
    ImmutableBytes schemaBytes = schema(stage);
    Schema schema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(json.parseCanonical(schemaBytes));
    String version =
        stage == Stage.EXTRACT ? "ontology-task-candidate-v1" : "ontology-task-review-v1";
    if (!schema.validate(document).isEmpty()
        || !version.equals(document.path("schemaVersion").asText())
        || !kind.name().equals(document.path("taskKind").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_MODEL_RESPONSE_INVALID");
    }
    Set<String> localIds = new HashSet<>();
    String field = stage == Stage.EXTRACT ? "definitions" : "finalDefinitions";
    for (JsonNode definition : document.path(field)) {
      if (!kind.name().equals(definition.path("kind").asText())
          || !localIds.add(definition.path("localId").asText())) {
        throw new IllegalArgumentException("ONTOLOGY_MODEL_DEFINITION_INVALID");
      }
      for (JsonNode claim : definition.path("claims")) {
        for (JsonNode ref : claim.path("evidenceRefs")) {
          packet.resolve(ref.asText());
        }
      }
    }
  }

  void validatePair(
      JsonNode draft,
      JsonNode review,
      OntologyTaskRunner.TaskKind kind,
      OntologyReadingPacket packet) {
    validate(draft, kind, Stage.EXTRACT, packet);
    validate(review, kind, Stage.REVIEW, packet);
    Map<String, JsonNode> known = new HashMap<>();
    draft
        .path("definitions")
        .forEach(definition -> known.put(definition.path("localId").asText(), definition));
    Set<String> corrected = new HashSet<>();
    for (JsonNode correction : review.path("corrections")) {
      String target = correction.path("targetLocalId").asText();
      if (!known.containsKey(target)) {
        throw new IllegalArgumentException("ONTOLOGY_CORRECTION_TARGET_UNKNOWN");
      }
      corrected.add(target);
    }
    Set<String> finalIds = new HashSet<>();
    boolean added = false;
    for (JsonNode definition : review.path("finalDefinitions")) {
      String id = definition.path("localId").asText();
      finalIds.add(id);
      JsonNode prior = known.get(id);
      if (prior == null) {
        added = true;
      } else if (!prior.equals(definition) && !corrected.contains(id)) {
        throw new IllegalArgumentException("ONTOLOGY_REVIEW_CHANGE_UNRECORDED");
      }
    }
    for (String id : known.keySet()) {
      if (!finalIds.contains(id) && !corrected.contains(id)) {
        throw new IllegalArgumentException("ONTOLOGY_REVIEW_CHANGE_UNRECORDED");
      }
    }
    if (added && corrected.isEmpty()) {
      throw new IllegalArgumentException("ONTOLOGY_REVIEW_CHANGE_UNRECORDED");
    }
  }

  enum Stage {
    EXTRACT,
    REVIEW
  }
}
