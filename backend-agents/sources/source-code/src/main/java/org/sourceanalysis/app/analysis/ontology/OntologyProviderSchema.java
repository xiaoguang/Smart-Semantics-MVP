package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Projects the local validation schema to the subset accepted by the model provider. */
final class OntologyProviderSchema {
  private OntologyProviderSchema() {}

  static ImmutableBytes from(ImmutableBytes validationSchema) {
    CanonicalJsonCodec json = new CanonicalJsonCodec();
    JsonNode providerSchema = json.parseCanonical(validationSchema).deepCopy();
    removeUnsupportedKeywords(providerSchema);
    return json.encodeCanonical(providerSchema);
  }

  private static void removeUnsupportedKeywords(JsonNode node) {
    if (node instanceof ObjectNode object) {
      object.remove("uniqueItems");
      if (!object.has("type") && (object.has("const") || object.has("enum"))) {
        JsonNode values = object.has("const") ? object.path("const") : object.path("enum");
        boolean stringsOnly =
            values.isTextual()
                || (values.isArray()
                    && !values.isEmpty()
                    && java.util.stream.StreamSupport.stream(values.spliterator(), false)
                        .allMatch(JsonNode::isTextual));
        if (!stringsOnly) {
          throw new IllegalArgumentException("ONTOLOGY_PROVIDER_SCHEMA_TYPE_UNSUPPORTED");
        }
        object.put("type", "string");
      }
      object.properties().forEach(entry -> removeUnsupportedKeywords(entry.getValue()));
    } else if (node.isArray()) {
      node.forEach(OntologyProviderSchema::removeUnsupportedKeywords);
    }
  }
}
