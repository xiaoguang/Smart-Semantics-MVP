package com.linguan.codemd.target.artifacts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class CanonicalArtifactPolicyRegistryTest {
    @Test
    void loadsAnExactCanonicalPolicyAndReturnsItsContentAddressedReference() throws Exception {
        String policy =
                "{\"artifactIdPrefix\":\"fixture-policy\",\"artifactType\":\"FIXTURE_JSON\","
                        + "\"emptyJsonlAllowed\":false,\"envelopeKind\":\"STANDALONE_JSON\","
                        + "\"mediaType\":\"application/json\",\"publicContentExposure\":\"METADATA_ONLY\","
                        + "\"schemaVersion\":\"fixture-json-v1\"}";
        String withoutId =
                "{\"policies\":[" + policy + "],\"schemaVersion\":\"artifact-policy-registry-v2\"}";
        String registryId = "artifact-policy-registry:" + identityDigest(withoutId);
        String document =
                "{\"artifactPolicyRegistryId\":\""
                        + registryId
                        + "\",\"policies\":["
                        + policy
                        + "],\"schemaVersion\":\"artifact-policy-registry-v2\"}";

        CanonicalArtifactPolicyRegistry registry =
                ArtifactPolicyRegistryLoader.load(
                        ImmutableBytes.copyOf(document.getBytes(StandardCharsets.UTF_8)),
                        new CanonicalJsonCodec());

        assertEquals(
                new ArtifactPolicyRegistryReference(registryId, sha256(document)), registry.reference());
        assertEquals(
                "fixture-policy",
                registry.resolve(new ArtifactPolicyKey("FIXTURE_JSON", "fixture-json-v1"))
                        .artifactIdPrefix());
    }

    private static String identityDigest(String canonicalDocumentWithoutId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(frame("artifact-policy-registry-id-v2".getBytes(StandardCharsets.UTF_8)));
        digest.update(frame(canonicalDocumentWithoutId.getBytes(StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(String document) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] frame(byte[] bytes) {
        return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
    }
}
