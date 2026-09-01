package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Produces the compact, recursively key-ordered JSON bytes used by target artifacts. */
public final class CanonicalJsonCodec {
    private static final ObjectMapper JSON =
            new ObjectMapper(JsonFactory.builder().disable(JsonWriteFeature.ESCAPE_NON_ASCII).build());

    public CanonicalJsonCodec() {}

    public ImmutableBytes canonicalize(JsonNode value) {
        if (value == null) {
            throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "JSON value must not be null");
        }
        try {
            return ImmutableBytes.copyOf(JSON.writeValueAsBytes(canonicalTree(value)));
        } catch (JsonProcessingException exception) {
            throw new ArtifactStoreException(
                    "MODULE_PAYLOAD_NOT_CANONICAL", "JSON value cannot be encoded", exception);
        }
    }

    private JsonNode canonicalTree(JsonNode value) {
        if (value.isMissingNode() || value.isBinary() || value.isPojo()) {
            throw new ArtifactStoreException(
                    "MODULE_PAYLOAD_NOT_CANONICAL", "JSON contains a non-canonical node");
        }
        if (value.isObject()) {
            return canonicalObject((ObjectNode) value);
        }
        if (value.isArray()) {
            return canonicalArray((ArrayNode) value);
        }
        if (value.isValueNode()) {
            return value.deepCopy();
        }
        throw new ArtifactStoreException("MODULE_PAYLOAD_NOT_CANONICAL", "JSON contains an unsupported node");
    }

    private ObjectNode canonicalObject(ObjectNode value) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = value.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> field = iterator.next();
            if (field.getKey() == null || field.getValue() == null) {
                throw new ArtifactStoreException(
                        "MODULE_PAYLOAD_NOT_CANONICAL", "JSON object contains a null field");
            }
            fields.add(field);
        }
        fields.sort(Comparator.comparing(Map.Entry::getKey));
        ObjectNode canonical = JSON.createObjectNode();
        for (Map.Entry<String, JsonNode> field : fields) {
            canonical.set(field.getKey(), canonicalTree(field.getValue()));
        }
        return canonical;
    }

    private ArrayNode canonicalArray(ArrayNode value) {
        ArrayNode canonical = JSON.createArrayNode();
        for (JsonNode item : value) {
            if (item == null) {
                throw new ArtifactStoreException(
                        "MODULE_PAYLOAD_NOT_CANONICAL", "JSON array contains a null item");
            }
            canonical.add(canonicalTree(item));
        }
        return canonical;
    }
}
