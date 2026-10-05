package org.sourceanalysis.app.analysis.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

final class OntologyProviderSchemaTest {
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

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
