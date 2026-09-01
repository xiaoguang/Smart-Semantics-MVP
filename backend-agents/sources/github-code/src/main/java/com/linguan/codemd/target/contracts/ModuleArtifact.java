package com.linguan.codemd.target.contracts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Strictly parsed Stage 13.3 success envelope. Its bytes and reference always
 * represent the complete envelope, including the verified artifact ID.
 */
public final class ModuleArtifact<T> {
    private static final ObjectMapper STRICT_JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ARTIFACT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*");
    private static final Pattern ARTIFACT_PREFIX = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "schemaVersion", "artifactType", "artifactId", "producer",
            "upstreamArtifacts", "controls", "completion", "payload");

    private final String schemaVersion;
    private final String artifactType;
    private final String artifactId;
    private final JsonNode payload;
    private final byte[] canonicalBytes;
    private final ArtifactReference reference;

    private ModuleArtifact(String schemaVersion, String artifactType, String artifactId,
                           JsonNode payload, byte[] canonicalBytes) {
        this.schemaVersion = schemaVersion;
        this.artifactType = artifactType;
        this.artifactId = artifactId;
        this.payload = payload.deepCopy();
        this.canonicalBytes = canonicalBytes.clone();
        this.reference = new ArtifactReference(artifactId, sha256(canonicalBytes));
    }

    /** Parses and validates a complete strict envelope. Only JsonNode payloads are currently supported. */
    public static <T> ModuleArtifact<T> parse(byte[] wireBytes, Class<T> payloadType) {
        if (wireBytes == null) {
            throw new IllegalArgumentException("artifact wire bytes must not be null");
        }
        if (payloadType == null || payloadType != JsonNode.class) {
            throw new IllegalArgumentException("only JsonNode payloads are supported by the strict contract parser");
        }

        ObjectNode envelope = parseEnvelope(wireBytes);
        String schemaVersion = requiredText(envelope, "schemaVersion");
        String artifactType = requiredText(envelope, "artifactType");
        if (!ARTIFACT_TYPE.matcher(artifactType).matches()) {
            throw new IllegalArgumentException("artifactType must be a canonical registered-enum token");
        }
        String artifactId = requiredText(envelope, "artifactId");
        validateProducer(requiredObject(envelope, "producer"));
        validateUpstreamArtifacts(requiredArray(envelope, "upstreamArtifacts"));
        validateControls(requiredObject(envelope, "controls"));
        validateCompletion(requiredObject(envelope, "completion"));
        JsonNode payload = requiredValue(envelope, "payload");
        if (payload.isNull()) {
            throw new IllegalArgumentException("payload must not be null");
        }

        validateArtifactIdentity(envelope, schemaVersion, artifactId);
        return new ModuleArtifact<>(schemaVersion, artifactType, artifactId, payload,
                CanonicalJson.canonicalize(envelope));
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String artifactType() {
        return artifactType;
    }

    public String artifactId() {
        return artifactId;
    }

    /** Returns an isolated copy of the payload so callers cannot alter the artifact's verified state. */
    @SuppressWarnings("unchecked")
    public T payload() {
        return (T) payload.deepCopy();
    }

    /** Complete, recursively canonical envelope bytes, including top-level artifactId. */
    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    public ArtifactReference reference() {
        return reference;
    }

    private static ObjectNode parseEnvelope(byte[] wireBytes) {
        try {
            JsonNode parsed = STRICT_JSON.readTree(wireBytes);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("artifact envelope must be one JSON object");
            }
            ObjectNode envelope = (ObjectNode) parsed;
            requireExactFields(envelope, TOP_LEVEL_FIELDS, "artifact envelope");
            return envelope;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("artifact wire JSON must be strict and complete", exception);
        }
    }

    private static void validateProducer(ObjectNode producer) {
        requireExactFields(producer, Set.of("stage", "module", "moduleVersion"), "producer");
        JsonNode stage = requiredValue(producer, "stage");
        if (!stage.isIntegralNumber() || !stage.canConvertToInt() || stage.intValue() < 1 || stage.intValue() > 8) {
            throw new IllegalArgumentException("producer stage must be an integer from 1 through 8");
        }
        requiredText(producer, "module");
        requiredText(producer, "moduleVersion");
    }

    private static void validateUpstreamArtifacts(ArrayNode upstreamArtifacts) {
        String previousArtifactId = null;
        for (JsonNode node : upstreamArtifacts) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("upstream artifact must be an object");
            }
            ObjectNode reference = (ObjectNode) node;
            requireExactFields(reference, Set.of("artifactId", "sha256"), "upstream artifact");
            ArtifactReference artifactReference = new ArtifactReference(
                    requiredText(reference, "artifactId"), requiredSha256(reference, "sha256"));
            if (previousArtifactId != null && previousArtifactId.compareTo(artifactReference.artifactId()) >= 0) {
                throw new IllegalArgumentException("upstream artifacts must be strictly ordered by artifactId");
            }
            previousArtifactId = artifactReference.artifactId();
        }
    }

    private static void validateControls(ObjectNode controls) {
        requireExactFields(controls, Set.of(
                "toolchainSha256", "profileSha256", "schemaBundleSha256", "promptBundleSha256"), "controls");
        requiredSha256(controls, "toolchainSha256");
        requiredSha256(controls, "profileSha256");
        requiredSha256(controls, "schemaBundleSha256");
        JsonNode promptBundleSha256 = requiredValue(controls, "promptBundleSha256");
        if (!promptBundleSha256.isNull()
                && (!promptBundleSha256.isTextual() || !SHA_256.matcher(promptBundleSha256.textValue()).matches())) {
            throw new IllegalArgumentException("promptBundleSha256 must be null or lowercase SHA-256");
        }
    }

    private static void validateCompletion(ObjectNode completion) {
        requireExactFields(completion, Set.of("status", "gapRefs", "failureRef"), "completion");
        String status = requiredText(completion, "status");
        if (!status.equals("SUCCEEDED") && !status.equals("SUCCEEDED_WITH_GAPS")) {
            throw new IllegalArgumentException("completion status is not a success status");
        }
        JsonNode failureRef = requiredValue(completion, "failureRef");
        if (!failureRef.isNull()) {
            throw new IllegalArgumentException("installed success artifact failureRef must be null");
        }
        ArrayNode gapRefs = requiredArray(completion, "gapRefs");
        String previousGapId = null;
        for (JsonNode gapRef : gapRefs) {
            if (!gapRef.isTextual()) {
                throw new IllegalArgumentException("gap reference must be text");
            }
            String gapId = CanonicalJson.requireCanonicalText(gapRef.textValue(), "gap reference");
            if (previousGapId != null && previousGapId.compareTo(gapId) >= 0) {
                throw new IllegalArgumentException("gap references must be sorted and unique");
            }
            previousGapId = gapId;
        }
    }

    private static void validateArtifactIdentity(ObjectNode envelope, String schemaVersion, String artifactId) {
        int separator = artifactId.indexOf(':');
        if (separator < 1 || separator != artifactId.lastIndexOf(':')) {
            throw new IllegalArgumentException("artifactId must contain one canonical type prefix");
        }
        String typePrefix = artifactId.substring(0, separator);
        String declaredHash = artifactId.substring(separator + 1);
        if (!ARTIFACT_PREFIX.matcher(typePrefix).matches() || !SHA_256.matcher(declaredHash).matches()) {
            throw new IllegalArgumentException("artifactId must be type-prefix followed by lowercase SHA-256");
        }
        ObjectNode withoutArtifactId = envelope.deepCopy();
        withoutArtifactId.remove("artifactId");
        String expectedArtifactId = typePrefix + ":" + sha256(
                (schemaVersion + "\n").getBytes(StandardCharsets.UTF_8),
                CanonicalJson.canonicalize(withoutArtifactId));
        if (!artifactId.equals(expectedArtifactId)) {
            throw new IllegalArgumentException("artifactId does not match the complete canonical envelope");
        }
    }

    private static void requireExactFields(ObjectNode object, Set<String> expectedFields, String fieldName) {
        Set<String> observedFields = new HashSet<>();
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            observedFields.add(fields.next());
        }
        if (!observedFields.equals(expectedFields)) {
            throw new IllegalArgumentException(fieldName + " has missing or unknown fields");
        }
    }

    private static ObjectNode requiredObject(ObjectNode parent, String field) {
        JsonNode value = requiredValue(parent, field);
        if (!value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (ObjectNode) value;
    }

    private static ArrayNode requiredArray(ObjectNode parent, String field) {
        JsonNode value = requiredValue(parent, field);
        if (!value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return (ArrayNode) value;
    }

    private static JsonNode requiredValue(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String requiredText(ObjectNode parent, String field) {
        JsonNode value = requiredValue(parent, field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return CanonicalJson.requireCanonicalText(value.textValue(), field);
    }

    private static String requiredSha256(ObjectNode parent, String field) {
        String value = requiredText(parent, field);
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }

    private static String sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }
}
