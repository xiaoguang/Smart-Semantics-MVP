package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyProviderSchemaTest {
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void everyOntologyOutputSchemaHasExplicitProviderTypesWithoutLosingLocalConstraints() {
    for (String file :
        List.of(
            "survey-v1.schema.json",
            "survey-v2.schema.json",
            "prioritize-v1.schema.json",
            "prioritize-v2.schema.json",
            "prioritize-v3.schema.json",
            "reading-v1.schema.json",
            "reading-v2.schema.json",
            "task-candidate-v1.schema.json",
            "task-review-v1.schema.json")) {
      ImmutableBytes validationSchema =
          json.canonicalizeStrictJson(
              ImmutableBytes.copyOf(
                  OntologyTaskRunner.resource(file).getBytes(StandardCharsets.UTF_8)));
      JsonNode local = json.parseCanonical(validationSchema);
      JsonNode provider = json.parseCanonical(OntologyProviderSchema.from(validationSchema));
      assertTrue(local.toString().contains("\"uniqueItems\""), file);
      assertFalse(provider.toString().contains("\"uniqueItems\""), file);
      assertEquals(
          "string", provider.path("properties").path("schemaVersion").path("type").asText(), file);
      if (file.endsWith("-v2.schema.json") || file.equals("prioritize-v3.schema.json")) {
        assertTrue(local.path("$defs").path("viewRef").isObject(), file);
        assertTrue(provider.path("$defs").path("viewRef").isObject(), file);
        assertEquals("object", provider.path("$defs").path("viewRef").path("type").asText(), file);
        assertTrue(provider.path("properties").toString().contains("#/$defs/viewRef"), file);
      }
      assertTypes(provider, file);
    }
  }

  @Test
  void formalReadingV2ProviderProjectionUsesSupportedCompositionWithoutChangingLocalSchema() {
    ImmutableBytes validationSchema = formalReadingV2Schema();
    JsonNode localBeforeProjection = json.parseCanonical(validationSchema);

    JsonNode provider = json.parseCanonical(OntologyProviderSchema.from(validationSchema));

    assertTrue(localBeforeProjection.path("$defs").path("unresolved").has("allOf"));
    assertTrue(localBeforeProjection.path("properties").path("actions").path("items").has("oneOf"));
    assertFalse(containsKeyword(provider, "allOf"));
    assertFalse(containsKeyword(provider, "if"));
    assertFalse(containsKeyword(provider, "then"));
    assertFalse(containsKeyword(provider, "oneOf"));
    assertTrue(provider.path("properties").path("actions").path("items").path("anyOf").isArray());
    assertEquals(3, provider.path("properties").path("actions").path("items").path("anyOf").size());
    assertEquals(localBeforeProjection, json.parseCanonical(validationSchema));
  }

  @Test
  void originalFormalReadingV2SchemaStillRejectsEmptyExclusionAndInvalidAction() {
    Schema localSchema =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(json.parseCanonical(formalReadingV2Schema()));
    ObjectNode emptyExclusion = validReadingResponse();
    emptyExclusion
        .putArray("unresolved")
        .addObject()
        .put("reason", "The task cannot establish this source.")
        .put("disposition", "EXCLUDED_FROM_TASK")
        .putArray("unitUses");
    ObjectNode invalidAction = validReadingResponse();
    invalidAction.withArray("actions").addObject().put("kind", "READ").put("unitRef", "U1");

    assertFalse(localSchema.validate(emptyExclusion).isEmpty());
    assertFalse(localSchema.validate(invalidAction).isEmpty());
  }

  private ImmutableBytes formalReadingV2Schema() {
    return json.canonicalizeStrictJson(
        ImmutableBytes.copyOf(
            OntologyTaskRunner.resource("formal-reading-v2.schema.json")
                .getBytes(StandardCharsets.UTF_8)));
  }

  private ObjectNode validReadingResponse() {
    ObjectNode response = mapper.createObjectNode();
    response.put("schemaVersion", "reading-response-v4");
    response.put("decision", "NEEDS_MORE_MATERIAL");
    selection(response.putObject("entrySelection"));
    selection(response.putObject("clueSelection"));
    response.putArray("retainedUnitUses");
    response.putArray("requiredUnitUses");
    response.putArray("actions");
    response.putArray("unresolved");
    return response;
  }

  private static void selection(ObjectNode selection) {
    selection.putArray("addRefs");
    selection.putArray("remove");
  }

  private static boolean containsKeyword(JsonNode schema, String keyword) {
    if (schema.isObject()) {
      if (schema.has(keyword)) {
        return true;
      }
      var children = schema.elements();
      while (children.hasNext()) {
        if (containsKeyword(children.next(), keyword)) {
          return true;
        }
      }
    } else if (schema.isArray()) {
      for (JsonNode child : schema) {
        if (containsKeyword(child, keyword)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void assertTypes(JsonNode schema, String file) {
    if (schema.has("$ref")) {
      return;
    }
    assertTrue(schema.has("type"), file + ": " + schema);
    if ("object".equals(schema.path("type").asText())) {
      assertFalse(schema.path("additionalProperties").asBoolean(true), file);
      Set<String> properties = new HashSet<>();
      schema.path("properties").fieldNames().forEachRemaining(properties::add);
      Set<String> required = new HashSet<>();
      schema.path("required").forEach(value -> required.add(value.asText()));
      assertEquals(properties, required, file);
    }
    schema.path("properties").elements().forEachRemaining(child -> assertTypes(child, file));
    schema.path("$defs").elements().forEachRemaining(child -> assertTypes(child, file));
    if (schema.has("items")) {
      assertTypes(schema.path("items"), file);
    }
  }
}
