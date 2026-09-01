package com.linguan.codemd.target.artifacts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses the one canonical policy-registry document used by an analysis run. */
final class ArtifactPolicyRegistryLoader {
    private static final ObjectMapper JSON =
            new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final String SCHEMA_VERSION = "artifact-policy-registry-v2";
    private static final String ID_DOMAIN = "artifact-policy-registry-id-v2";

    private ArtifactPolicyRegistryLoader() {}

    static CanonicalArtifactPolicyRegistry load(ImmutableBytes document, CanonicalJsonCodec canonicalJson) {
        if (document == null || canonicalJson == null) {
            throw ArtifactPolicyValidation.invalid("registry document and canonical JSON codec are required");
        }
        byte[] sourceBytes = document.copyToByteArray();
        ObjectNode root = parseObject(sourceBytes);
        requireExactFields(root, List.of("artifactPolicyRegistryId", "policies", "schemaVersion"), "registry");
        String schemaVersion = requiredText(root, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw ArtifactPolicyValidation.invalid("schemaVersion must be " + SCHEMA_VERSION);
        }
        String registryId = requiredText(root, "artifactPolicyRegistryId");
        ArtifactPolicyValidation.registryArtifactId(registryId);
        ArrayNode policiesNode = requiredArray(root, "policies");
        List<CanonicalArtifactPolicy> policies = parsePolicies(policiesNode);
        verifySortedUnique(policies);

        byte[] canonicalDocument = canonicalJson.canonicalize(root).copyToByteArray();
        if (!Arrays.equals(sourceBytes, canonicalDocument)) {
            throw ArtifactPolicyValidation.invalid("registry document is not canonical JSON");
        }
        ObjectNode withoutId = root.deepCopy();
        withoutId.remove("artifactPolicyRegistryId");
        String expectedId = "artifact-policy-registry:" + identityDigest(canonicalJson.canonicalize(withoutId));
        if (!expectedId.equals(registryId)) {
            throw ArtifactPolicyValidation.invalid("artifactPolicyRegistryId does not match the canonical document");
        }

        Map<ArtifactPolicyKey, CanonicalArtifactPolicy> byKey = new LinkedHashMap<>();
        for (CanonicalArtifactPolicy policy : policies) {
            byKey.put(policy.key(), policy);
        }
        return new ParsedRegistry(
                new ArtifactPolicyRegistryReference(registryId, sha256(canonicalDocument)), Map.copyOf(byKey));
    }

    private static ObjectNode parseObject(byte[] sourceBytes) {
        try {
            JsonNode root = JSON.readTree(sourceBytes);
            if (root == null || !root.isObject()) {
                throw ArtifactPolicyValidation.invalid("registry document must be one JSON object");
            }
            return (ObjectNode) root;
        } catch (ArtifactStoreException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ArtifactStoreException(
                    "ARTIFACT_POLICY_REGISTRY_INVALID", "registry document cannot be parsed", exception);
        }
    }

    private static List<CanonicalArtifactPolicy> parsePolicies(ArrayNode policiesNode) {
        List<CanonicalArtifactPolicy> policies = new ArrayList<>();
        for (JsonNode policyNode : policiesNode) {
            if (!policyNode.isObject()) {
                throw ArtifactPolicyValidation.invalid("every policy must be one JSON object");
            }
            ObjectNode policy = (ObjectNode) policyNode;
            requireExactFields(
                    policy,
                    List.of(
                            "artifactIdPrefix",
                            "artifactType",
                            "emptyJsonlAllowed",
                            "envelopeKind",
                            "mediaType",
                            "publicContentExposure",
                            "schemaVersion"),
                    "policy");
            JsonNode emptyJsonlAllowed = policy.get("emptyJsonlAllowed");
            if (emptyJsonlAllowed == null || !emptyJsonlAllowed.isBoolean()) {
                throw ArtifactPolicyValidation.invalid("emptyJsonlAllowed must be boolean");
            }
            policies.add(
                    new CanonicalArtifactPolicy(
                            new ArtifactPolicyKey(
                                    requiredText(policy, "artifactType"),
                                    requiredText(policy, "schemaVersion")),
                            requiredText(policy, "artifactIdPrefix"),
                            requiredText(policy, "mediaType"),
                            requiredText(policy, "envelopeKind"),
                            emptyJsonlAllowed.booleanValue(),
                            requiredText(policy, "publicContentExposure")));
        }
        return List.copyOf(policies);
    }

    private static void verifySortedUnique(List<CanonicalArtifactPolicy> policies) {
        List<CanonicalArtifactPolicy> sorted = new ArrayList<>(policies);
        sorted.sort(
                Comparator.comparing(
                                (CanonicalArtifactPolicy policy) -> policy.key().artifactType())
                        .thenComparing(policy -> policy.key().schemaVersion()));
        if (!sorted.equals(policies)) {
            throw ArtifactPolicyValidation.invalid("policies must be sorted by artifact type and schema version");
        }
        for (int index = 1; index < policies.size(); index++) {
            if (policies.get(index - 1).key().equals(policies.get(index).key())) {
                throw ArtifactPolicyValidation.invalid("policy keys must be unique");
            }
        }
    }

    private static void requireExactFields(ObjectNode value, List<String> expected, String subject) {
        List<String> observed = new ArrayList<>();
        value.fieldNames().forEachRemaining(observed::add);
        if (!observed.containsAll(expected) || observed.size() != expected.size()) {
            throw ArtifactPolicyValidation.invalid(subject + " fields do not match the exact schema");
        }
    }

    private static String requiredText(ObjectNode value, String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isTextual()) {
            throw ArtifactPolicyValidation.invalid(field + " must be textual");
        }
        return ArtifactPolicyValidation.text(node.textValue(), field);
    }

    private static ArrayNode requiredArray(ObjectNode value, String field) {
        JsonNode node = value.get(field);
        if (node == null || !node.isArray()) {
            throw ArtifactPolicyValidation.invalid(field + " must be an array");
        }
        return (ArrayNode) node;
    }

    private static String identityDigest(ImmutableBytes canonicalDocumentWithoutId) {
        return sha256(frame(ID_DOMAIN.getBytes(StandardCharsets.UTF_8)), frame(canonicalDocumentWithoutId.copyToByteArray()));
    }

    private static String sha256(byte[]... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] part : parts) {
                digest.update(part);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static byte[] frame(byte[] bytes) {
        byte[] framed = new byte[Long.BYTES + bytes.length];
        long length = bytes.length;
        for (int offset = Long.BYTES - 1; offset >= 0; offset--) {
            framed[offset] = (byte) length;
            length >>>= Byte.SIZE;
        }
        System.arraycopy(bytes, 0, framed, Long.BYTES, bytes.length);
        return framed;
    }

    private record ParsedRegistry(
            ArtifactPolicyRegistryReference reference,
            Map<ArtifactPolicyKey, CanonicalArtifactPolicy> policies)
            implements CanonicalArtifactPolicyRegistry {
        @Override
        public CanonicalArtifactPolicy resolve(ArtifactPolicyKey key) {
            if (key == null) {
                throw new ArtifactStoreException("ARTIFACT_POLICY_NOT_FOUND", "policy key must not be null");
            }
            CanonicalArtifactPolicy policy = policies.get(key);
            if (policy == null) {
                throw new ArtifactStoreException("ARTIFACT_POLICY_NOT_FOUND", "exact policy key is not registered");
            }
            return policy;
        }
    }
}
