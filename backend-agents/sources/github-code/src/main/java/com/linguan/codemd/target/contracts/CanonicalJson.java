package com.linguan.codemd.target.contracts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Canonical UTF-8 JSON and JSONL bytes used by target artifact identities. */
public final class CanonicalJson {
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .disable(JsonWriteFeature.ESCAPE_NON_ASCII)
            .build());

    private CanonicalJson() {
    }

    /**
     * Produces compact UTF-8 JSON with every object recursively ordered by key.
     * Array order remains semantic and is therefore unchanged.
     */
    public static byte[] canonicalize(JsonNode value) {
        if (value == null) {
            throw new IllegalArgumentException("canonical JSON value must not be null");
        }
        try {
            return JSON.writeValueAsBytes(canonicalTree(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("cannot canonicalize JSON value", exception);
        }
    }

    /**
     * Produces key-sorted canonical JSONL records in semantic-key order.
     * A non-empty stream has one, and only one, final LF.
     */
    public static byte[] canonicalizeJsonl(List<JsonNode> values, String semanticKey) {
        if (values == null) {
            throw new IllegalArgumentException("JSONL values must not be null");
        }
        requireCanonicalText(semanticKey, "semanticKey");

        List<JsonlRecord> records = new ArrayList<>(values.size());
        Set<String> observedKeys = new HashSet<>();
        for (JsonNode value : values) {
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException("JSONL record must be a non-null object");
            }
            JsonNode keyNode = value.get(semanticKey);
            if (keyNode == null || !keyNode.isTextual()) {
                throw new IllegalArgumentException("JSONL record is missing a textual semantic key");
            }
            String key = requireCanonicalText(keyNode.textValue(), "semantic key");
            if (!observedKeys.add(key)) {
                throw new IllegalArgumentException("duplicate JSONL semantic key");
            }
            records.add(new JsonlRecord(key, value));
        }

        records.sort(Comparator.comparing(JsonlRecord::semanticKey));
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (JsonlRecord record : records) {
            result.writeBytes(canonicalize(record.value()));
            result.write('\n');
        }
        return result.toByteArray();
    }

    static String requireCanonicalText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || containsControl(value)) {
            throw new IllegalArgumentException(field + " must be nonblank canonical text");
        }
        return value;
    }

    private static JsonNode canonicalTree(JsonNode value) {
        if (value.isMissingNode() || value.isBinary() || value.isPojo()) {
            throw new IllegalArgumentException("canonical JSON does not allow non-JSON nodes");
        }
        if (value.isObject()) {
            ObjectNode canonical = JSON.createObjectNode();
            Map<String, JsonNode> sorted = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey() == null || field.getValue() == null) {
                    throw new IllegalArgumentException("canonical JSON object contains a null field");
                }
                sorted.put(field.getKey(), canonicalTree(field.getValue()));
            }
            sorted.forEach(canonical::set);
            return canonical;
        }
        if (value.isArray()) {
            ArrayNode canonical = JSON.createArrayNode();
            for (JsonNode item : value) {
                if (item == null) {
                    throw new IllegalArgumentException("canonical JSON array contains a null item");
                }
                canonical.add(canonicalTree(item));
            }
            return canonical;
        }
        if (!value.isValueNode()) {
            throw new IllegalArgumentException("canonical JSON contains an unsupported node");
        }
        return value.deepCopy();
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint));
    }

    private record JsonlRecord(String semanticKey, JsonNode value) {
        private JsonlRecord {
            Objects.requireNonNull(semanticKey, "semanticKey");
            Objects.requireNonNull(value, "value");
        }
    }
}
