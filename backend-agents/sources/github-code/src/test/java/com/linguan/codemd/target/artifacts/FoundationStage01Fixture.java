package com.linguan.codemd.target.artifacts;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Strict, small Stage01 M3 fixture shared by Foundation store seams. */
final class FoundationStage01Fixture {
    private static final String RUN_ID = "analysis-run:" + "a".repeat(64);

    private FoundationStage01Fixture() {}

    static CanonicalArtifactPolicyRegistry policies(CanonicalJsonCodec codec) {
        String sourceInputPolicy =
                policy(
                        "stage01-source-input",
                        "STAGE01_SOURCE_INPUT",
                        false,
                        "STANDALONE_JSON",
                        "application/json",
                        "stage01-source-input-v2");
        String inventoryPolicy =
                policy(
                        "stage01-source-inventory",
                        "STAGE01_SOURCE_INVENTORY",
                        false,
                        "CANONICAL_JSONL",
                        "application/x-ndjson",
                        "stage01-source-inventory-v2");
        String snapshotPolicy =
                policy(
                        "verified-snapshot",
                        "VERIFIED_SNAPSHOT",
                        false,
                        "STANDALONE_JSON",
                        "application/json",
                        "verified-snapshot-v2");
        String withoutId =
                "{\"policies\":["
                        + sourceInputPolicy
                        + ","
                        + inventoryPolicy
                        + ","
                        + snapshotPolicy
                        + "],\"schemaVersion\":\"artifact-policy-registry-v2\"}";
        String registryId = "artifact-policy-registry:" + digest("artifact-policy-registry-id-v2", withoutId);
        String document =
                "{\"artifactPolicyRegistryId\":\""
                        + registryId
                        + "\",\"policies\":["
                        + sourceInputPolicy
                        + ","
                        + inventoryPolicy
                        + ","
                        + snapshotPolicy
                        + "],\"schemaVersion\":\"artifact-policy-registry-v2\"}";
        return ArtifactPolicyRegistryLoader.load(
                ImmutableBytes.copyOf(document.getBytes(StandardCharsets.UTF_8)), codec);
    }

    static ArtifactControls controls(CanonicalArtifactPolicyRegistry policies) {
        return new ArtifactControls(
                "1".repeat(64), "2".repeat(64), "3".repeat(64), null, policies.reference());
    }

    static ModuleInstallRequest publisherRequest(CanonicalArtifactPolicyRegistry policies) {
        return new ModuleInstallRequest(
                publisherAddress(),
                "target-v1",
                List.of(),
                controls(policies),
                "SUCCEEDED",
                List.of(),
                stage01SemanticPayloads());
    }

    static StageInstallRequest stageRequest(
            CanonicalArtifactPolicyRegistry policies, ModulePublicationReference publisherReference) {
        return new StageInstallRequest(
                new StagePublicationAddress(RUN_ID, 1, "freeze-source"),
                new StagePublisherModuleProvenance(publisherReference),
                List.of(),
                controls(policies),
                "SUCCEEDED",
                List.of(),
                stage01StagePayloads(),
                null);
    }

    static StageModuleAddress publisherAddress() {
        return new StageModuleAddress(RUN_ID, 1, "freeze-source", 3, "publish");
    }

    static List<CanonicalModulePayload> stage01SemanticPayloads() {
        return List.of(
                standalone(
                        "source-input.json",
                        "STAGE01_SOURCE_INPUT",
                        "stage01-source-input-v2",
                        "stage01-source-input",
                        "{\"source\":\"input\"}"),
                jsonl(
                        "source-inventory.jsonl",
                        "STAGE01_SOURCE_INVENTORY",
                        "stage01-source-inventory-v2",
                        "stage01-source-inventory",
                        "{\"fileId\":\"file:"
                                + "b".repeat(64)
                                + "\",\"path\":\"DepotHead.java\"}\n"),
                standalone(
                        "verified-snapshot.json",
                        "VERIFIED_SNAPSHOT",
                        "verified-snapshot-v2",
                        "verified-snapshot",
                        "{\"snapshot\":\"verified\"}"));
    }

    static List<CanonicalStagePayload> stage01StagePayloads() {
        return stage01SemanticPayloads().stream()
                .map(
                        payload ->
                                new CanonicalStagePayload(
                                        payload.fileName(),
                                        payload.artifactType(),
                                        payload.schemaVersion(),
                                        payload.artifactId(),
                                        payload.mediaType(),
                                        payload.canonicalUtf8()))
                .toList();
    }

    private static CanonicalModulePayload standalone(
            String fileName, String type, String schema, String prefix, String withoutId) {
        String artifactId = prefix + ":" + digest("canonical-standalone-json-artifact-id-v1", schema, type, withoutId);
        byte[] bytes =
                ("{\"artifactId\":\"" + artifactId + "\"," + withoutId.substring(1))
                        .getBytes(StandardCharsets.UTF_8);
        return new CanonicalModulePayload(
                fileName, type, schema, artifactId, "application/json", ImmutableBytes.copyOf(bytes));
    }

    private static CanonicalModulePayload jsonl(
            String fileName, String type, String schema, String prefix, String canonicalJsonl) {
        String artifactId = prefix + ":" + digest("canonical-jsonl-artifact-id-v1", schema, type, canonicalJsonl);
        return new CanonicalModulePayload(
                fileName,
                type,
                schema,
                artifactId,
                "application/x-ndjson",
                ImmutableBytes.copyOf(canonicalJsonl.getBytes(StandardCharsets.UTF_8)));
    }

    private static String policy(
            String prefix,
            String type,
            boolean emptyJsonlAllowed,
            String envelope,
            String mediaType,
            String schema) {
        return "{\"artifactIdPrefix\":\""
                + prefix
                + "\",\"artifactType\":\""
                + type
                + "\",\"emptyJsonlAllowed\":"
                + emptyJsonlAllowed
                + ",\"envelopeKind\":\""
                + envelope
                + "\",\"mediaType\":\""
                + mediaType
                + "\",\"publicContentExposure\":\"METADATA_ONLY\",\"schemaVersion\":\""
                + schema
                + "\"}";
    }

    private static String digest(String domain, String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(frame(domain.getBytes(StandardCharsets.UTF_8)));
            for (String value : values) {
                digest.update(frame(value.getBytes(StandardCharsets.UTF_8)));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] frame(byte[] bytes) {
        return ByteBuffer.allocate(Long.BYTES + bytes.length).putLong(bytes.length).put(bytes).array();
    }
}
