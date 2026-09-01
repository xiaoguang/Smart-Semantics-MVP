package com.linguan.codemd.target.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Public target-contract RED tests.
 *
 * <p>The target implementation is intentionally not present yet.  These tests
 * are the executable seam for the cross-stage wire records and canonical byte
 * rules described by DESIGN §13 and the Stage 01/08 contracts.</p>
 */
class TargetContractsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String A64 = "a".repeat(64);
    private static final String B64 = "b".repeat(64);
    private static final String C64 = "c".repeat(64);

    /* Filled from the hand-written canonical preimage, never from production code. */
    private static final String ARTIFACT_ID =
            "test-artifact:6f58487b9dc58f4e7d0ef46b33c4083bf06cbc7093505e0251d188c8738ca4bf";

    @Test
    void canonicalJsonUsesUtf8SortedObjectKeysAndPreservesArrayOrder() throws Exception {
        JsonNode value = JSON.readTree("""
                {"z":[{"value":"第二","id":"second"},{"value":"第一","id":"first"}],
                 "text":"mañana", "a":{"z":2,"a":1}}
                """);

        byte[] canonical = CanonicalJson.canonicalize(value);

        assertArrayEquals(
                "{\"a\":{\"a\":1,\"z\":2},\"text\":\"mañana\",\"z\":[{\"id\":\"second\",\"value\":\"第二\"},{\"id\":\"first\",\"value\":\"第一\"}]}"
                        .getBytes(StandardCharsets.UTF_8),
                canonical,
                "canonical JSON is compact UTF-8 with recursively sorted object keys and ordered arrays");
        assertFalse(new String(canonical, StandardCharsets.UTF_8).contains("\r"));
        assertTrue(new String(canonical, StandardCharsets.UTF_8).contains("第二"),
                "canonical encoding must not replace UTF-8 content with a platform encoding");
    }

    @Test
    void moduleArtifactIdentityHashesTheCompleteEnvelopeAndExposesContentReference() {
        ModuleArtifact<JsonNode> artifact = ModuleArtifact.parse(
                artifactWire(ARTIFACT_ID).getBytes(StandardCharsets.UTF_8), JsonNode.class);

        assertEquals(ARTIFACT_ID, artifact.artifactId());
        assertArrayEquals(canonicalArtifact(ARTIFACT_ID).getBytes(StandardCharsets.UTF_8),
                artifact.canonicalBytes());
        assertEquals(sha256(artifact.canonicalBytes()), artifact.reference().sha256());
        assertEquals(new ArtifactReference(ARTIFACT_ID, sha256(artifact.canonicalBytes())),
                artifact.reference());
    }

    @Test
    void moduleArtifactWireParserRejectsUnknownTopLevelFields() {
        String unknownField = artifactWire(ARTIFACT_ID)
                .replace("\"artifactType\":\"TEST_ARTIFACT\",",
                        "\"artifactType\":\"TEST_ARTIFACT\",\"unknown\":true,");

        assertThrows(RuntimeException.class,
                () -> ModuleArtifact.parse(unknownField.getBytes(StandardCharsets.UTF_8), JsonNode.class));
    }

    @Test
    void moduleArtifactWireParserRejectsDuplicateFieldsInsteadOfLastValueWins() {
        String duplicateField = artifactWire(ARTIFACT_ID)
                .replace("\"artifactType\":\"TEST_ARTIFACT\",",
                        "\"artifactType\":\"TEST_ARTIFACT\",\"artifactType\":\"OTHER\",");

        assertThrows(RuntimeException.class,
                () -> ModuleArtifact.parse(duplicateField.getBytes(StandardCharsets.UTF_8), JsonNode.class));
    }

    @Test
    void sourceLocatorAcceptsRepositoryRelativePathButRejectsAbsolutePath() {
        SourceLocator locator = new SourceLocator(
                "src/main/java/example/Inventory.java", 0, 7, 1, 1, 1, 8);

        assertEquals("src/main/java/example/Inventory.java", locator.path());
        assertThrows(RuntimeException.class,
                () -> new SourceLocator("/tmp/Inventory.java", 0, 7, 1, 1, 1, 8));
    }

    @Test
    void canonicalJsonlSortsByStableSemanticKeyAndUsesUtf8LfRecords() throws Exception {
        JsonNode first = JSON.readTree("{\"value\":\"第一\",\"id\":\"b\"}");
        JsonNode second = JSON.readTree("{\"id\":\"a\",\"value\":\"第二\"}");
        byte[] expected = "{\"id\":\"a\",\"value\":\"第二\"}\n{\"id\":\"b\",\"value\":\"第一\"}\n"
                .getBytes(StandardCharsets.UTF_8);

        byte[] canonical = CanonicalJson.canonicalizeJsonl(List.of(first, second), "id");
        byte[] replay = CanonicalJson.canonicalizeJsonl(List.of(second, first), "id");

        assertArrayEquals(expected, canonical);
        assertArrayEquals(canonical, replay,
                "JSONL identity must not depend on producer iteration order");
        assertEquals(sha256(canonical), sha256(replay));
        assertTrue(canonical[canonical.length - 1] == '\n',
                "canonical JSONL must have a final LF");
    }

    private static String artifactWire(String artifactId) {
        return "{" +
                "\"payload\":{\"items\":[{\"value\":\"第二\",\"id\":\"second\"},{\"value\":\"第一\",\"id\":\"first\"}]}" +
                ",\"controls\":{\"promptBundleSha256\":null,\"schemaBundleSha256\":\"" + C64 +
                "\",\"profileSha256\":\"" + B64 + "\",\"toolchainSha256\":\"" + A64 + "\"}" +
                ",\"artifactId\":\"" + artifactId + "\"" +
                ",\"schemaVersion\":\"stage01-test-v1\"" +
                ",\"producer\":{\"moduleVersion\":\"v1\",\"stage\":1,\"module\":\"TargetContractsTest\"}" +
                ",\"artifactType\":\"TEST_ARTIFACT\"" +
                ",\"upstreamArtifacts\":[{" +
                "\"sha256\":\"" + A64 + "\",\"artifactId\":\"source:" + A64 + "\"},{" +
                "\"sha256\":\"" + B64 + "\",\"artifactId\":\"upstream:" + B64 + "\"}]" +
                ",\"completion\":{\"failureRef\":null,\"gapRefs\":[],\"status\":\"SUCCEEDED\"}" +
                "}";
    }

    private static String canonicalArtifact(String artifactId) {
        return "{" +
                "\"artifactId\":\"" + artifactId + "\"," +
                "\"artifactType\":\"TEST_ARTIFACT\"," +
                "\"completion\":{\"failureRef\":null,\"gapRefs\":[],\"status\":\"SUCCEEDED\"}," +
                "\"controls\":{\"profileSha256\":\"" + B64 + "\",\"promptBundleSha256\":null,\"schemaBundleSha256\":\"" + C64 + "\",\"toolchainSha256\":\"" + A64 + "\"}," +
                "\"payload\":{\"items\":[{\"id\":\"second\",\"value\":\"第二\"},{\"id\":\"first\",\"value\":\"第一\"}]}," +
                "\"producer\":{\"module\":\"TargetContractsTest\",\"moduleVersion\":\"v1\",\"stage\":1}," +
                "\"schemaVersion\":\"stage01-test-v1\"," +
                "\"upstreamArtifacts\":[{" +
                "\"artifactId\":\"source:" + A64 + "\",\"sha256\":\"" + A64 + "\"},{" +
                "\"artifactId\":\"upstream:" + B64 + "\",\"sha256\":\"" + B64 + "\"}]" +
                "}";
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
