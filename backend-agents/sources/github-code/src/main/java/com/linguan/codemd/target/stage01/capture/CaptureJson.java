package com.linguan.codemd.target.stage01.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Exact canonical JSON/JSONL serialization for the local capture wire records. */
final class CaptureJson {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final CanonicalJsonCodec CANONICAL = new CanonicalJsonCodec();

    private CaptureJson() {}

    static byte[] manifestBytes(List<LocalGitSnapshotEntry> entries) {
        StringBuilder output = new StringBuilder();
        for (LocalGitSnapshotEntry entry : entries) {
            output.append(new String(canonical(entryNode(entry)), StandardCharsets.UTF_8)).append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    static byte[] receiptBytes(LocalGitCaptureReceipt receipt) {
        return canonical(receiptNode(receipt));
    }

    static byte[] receiptBytesWithoutId(
            String declaredRepositoryIdentity,
            String commitId,
            String treeObjectId,
            String snapshotId,
            ArtifactReference snapshotManifestRef,
            long regularFileCount,
            long analyzableTextFileCount,
            long nonAnalyzableMediaFileCount,
            ArtifactReference capturePolicyRef) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", "local-git-capture-receipt-v1");
        node.put("declaredRepositoryIdentity", declaredRepositoryIdentity);
        node.put("objectFormat", "SHA1");
        node.put("commitId", commitId);
        node.put("treeObjectId", treeObjectId);
        node.put("snapshotId", snapshotId);
        node.set("snapshotManifestRef", referenceNode(snapshotManifestRef));
        node.put("regularFileCount", regularFileCount);
        node.put("analyzableTextFileCount", analyzableTextFileCount);
        node.put("nonAnalyzableMediaFileCount", nonAnalyzableMediaFileCount);
        node.put("unsupportedTreeEntryCount", 0);
        node.put("worktreeRead", "FORBIDDEN");
        node.put("networkAccess", "DISABLED");
        node.set("capturePolicyRef", referenceNode(capturePolicyRef));
        return canonical(node);
    }

    static byte[] registrationBytes(SourceRegistration registration) {
        return canonical(registrationNode(registration));
    }

    static byte[] registrationBytesWithoutId(
            String declaredRepositoryIdentity,
            String commitId,
            String snapshotId,
            ArtifactReference snapshotManifestRef,
            ArtifactReference captureReceiptRef,
            long regularFileCount) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", "source-registration-v1");
        node.put("declaredRepositoryIdentity", declaredRepositoryIdentity);
        node.put("commitId", commitId);
        node.put("snapshotId", snapshotId);
        node.set("snapshotManifestRef", referenceNode(snapshotManifestRef));
        node.set("captureReceiptRef", referenceNode(captureReceiptRef));
        node.put("regularFileCount", regularFileCount);
        return canonical(node);
    }

    static String captureReceiptId(byte[] receiptWithoutId) {
        return "capture-receipt:" + framedDigest("local-git-capture-receipt-id-v1", receiptWithoutId);
    }

    static String sourceRegistrationId(byte[] registrationWithoutId) {
        return "source-registration:" + framedDigest("source-registration-id-v1", registrationWithoutId);
    }

    static String sha256Of(byte[] bytes) {
        return sha256(bytes);
    }

    static LocalGitCaptureResult parse(byte[] manifestBytes, byte[] receiptBytes, byte[] registrationBytes) {
        try {
            List<LocalGitSnapshotEntry> entries = new ArrayList<>();
            String manifest = new String(manifestBytes, StandardCharsets.UTF_8);
            if (manifest.isEmpty() || !manifest.endsWith("\n")) {
                throw invalid("snapshot manifest must be nonempty canonical JSONL");
            }
            for (String line : manifest.substring(0, manifest.length() - 1).split("\n", -1)) {
                JsonNode node = JSON.readTree(line);
                if (!line.equals(new String(canonical(node), StandardCharsets.UTF_8))) {
                    throw invalid("snapshot manifest line is not canonical");
                }
                entries.add(entry(node));
            }
            JsonNode receiptNode = JSON.readTree(receiptBytes);
            JsonNode registrationNode = JSON.readTree(registrationBytes);
            if (!java.util.Arrays.equals(receiptBytes, canonical(receiptNode))
                    || !java.util.Arrays.equals(registrationBytes, canonical(registrationNode))) {
                throw invalid("capture documents are not canonical");
            }
            LocalGitCaptureReceipt receipt = receipt(receiptNode);
            SourceRegistration registration = registration(registrationNode);
            LocalGitCaptureResult result = new LocalGitCaptureResult(receipt, registration, entries);
            verifyCrossDocumentBindings(result, manifestBytes, receiptBytes, registrationBytes);
            return result;
        } catch (java.io.IOException exception) {
            throw new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", "capture document cannot be parsed", exception);
        }
    }

    private static void verifyCrossDocumentBindings(
            LocalGitCaptureResult result, byte[] manifestBytes, byte[] receiptBytes, byte[] registrationBytes) {
        if (!result.receipt().snapshotManifestRef().sha256().equals(sha256(manifestBytes))
                || !result.registration().snapshotManifestRef().equals(result.receipt().snapshotManifestRef())
                || !result.registration().captureReceiptRef().artifactId().equals(result.receipt().captureReceiptId())
                || !result.registration().captureReceiptRef().sha256().equals(sha256(receiptBytes))
                || !result.registration().snapshotId().equals(result.receipt().snapshotId())
                || !result.registration().commitId().equals(result.receipt().commitId())
                || !result.registration().declaredRepositoryIdentity().equals(result.receipt().declaredRepositoryIdentity())
                || result.registration().regularFileCount() != result.snapshotManifest().size()) {
            throw invalid("capture document bindings are inconsistent");
        }
        String receiptId = captureReceiptId(receiptBytesWithoutId(
                result.receipt().declaredRepositoryIdentity(),
                result.receipt().commitId(),
                result.receipt().treeObjectId(),
                result.receipt().snapshotId(),
                result.receipt().snapshotManifestRef(),
                result.receipt().regularFileCount(),
                result.receipt().analyzableTextFileCount(),
                result.receipt().nonAnalyzableMediaFileCount(),
                result.receipt().capturePolicyRef()));
        String registrationId = sourceRegistrationId(registrationBytesWithoutId(
                result.registration().declaredRepositoryIdentity(),
                result.registration().commitId(),
                result.registration().snapshotId(),
                result.registration().snapshotManifestRef(),
                result.registration().captureReceiptRef(),
                result.registration().regularFileCount()));
        if (!receiptId.equals(result.receipt().captureReceiptId())
                || !registrationId.equals(result.registration().sourceRegistrationId())) {
            throw invalid("capture identity is inconsistent");
        }
    }

    private static LocalGitSnapshotEntry entry(JsonNode node) {
        requireFields(node, Set.of(
                "schemaVersion", "path", "gitMode", "blobObjectId", "sizeBytes", "sha256", "mediaType", "analysisDisposition", "textEncoding"));
        if (!"local-git-snapshot-entry-v1".equals(text(node, "schemaVersion"))) {
            throw invalid("snapshot entry schemaVersion is invalid");
        }
        JsonNode textEncoding = node.get("textEncoding");
        return new LocalGitSnapshotEntry(
                text(node, "path"),
                text(node, "gitMode"),
                text(node, "blobObjectId"),
                whole(node, "sizeBytes"),
                text(node, "sha256"),
                text(node, "mediaType"),
                AnalysisDisposition.valueOf(text(node, "analysisDisposition")),
                textEncoding == null || textEncoding.isNull() ? null : text(node, "textEncoding"));
    }

    private static LocalGitCaptureReceipt receipt(JsonNode node) {
        requireFields(node, Set.of(
                "schemaVersion", "captureReceiptId", "declaredRepositoryIdentity", "objectFormat", "commitId", "treeObjectId", "snapshotId", "snapshotManifestRef", "regularFileCount", "analyzableTextFileCount", "nonAnalyzableMediaFileCount", "unsupportedTreeEntryCount", "worktreeRead", "networkAccess", "capturePolicyRef"));
        if (!"local-git-capture-receipt-v1".equals(text(node, "schemaVersion"))) {
            throw invalid("capture receipt schemaVersion is invalid");
        }
        return new LocalGitCaptureReceipt(
                text(node, "captureReceiptId"),
                text(node, "declaredRepositoryIdentity"),
                text(node, "objectFormat"),
                text(node, "commitId"),
                text(node, "treeObjectId"),
                text(node, "snapshotId"),
                reference(node, "snapshotManifestRef"),
                whole(node, "regularFileCount"),
                whole(node, "analyzableTextFileCount"),
                whole(node, "nonAnalyzableMediaFileCount"),
                whole(node, "unsupportedTreeEntryCount"),
                text(node, "worktreeRead"),
                text(node, "networkAccess"),
                reference(node, "capturePolicyRef"));
    }

    private static SourceRegistration registration(JsonNode node) {
        requireFields(node, Set.of(
                "schemaVersion", "sourceRegistrationId", "declaredRepositoryIdentity", "commitId", "snapshotId", "snapshotManifestRef", "captureReceiptRef", "regularFileCount"));
        if (!"source-registration-v1".equals(text(node, "schemaVersion"))) {
            throw invalid("source registration schemaVersion is invalid");
        }
        return new SourceRegistration(
                text(node, "sourceRegistrationId"),
                text(node, "declaredRepositoryIdentity"),
                text(node, "commitId"),
                text(node, "snapshotId"),
                reference(node, "snapshotManifestRef"),
                reference(node, "captureReceiptRef"),
                whole(node, "regularFileCount"));
    }

    private static ObjectNode entryNode(LocalGitSnapshotEntry entry) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", "local-git-snapshot-entry-v1");
        node.put("path", entry.path());
        node.put("gitMode", entry.gitMode());
        node.put("blobObjectId", entry.blobObjectId());
        node.put("sizeBytes", entry.sizeBytes());
        node.put("sha256", entry.sha256());
        node.put("mediaType", entry.mediaType());
        node.put("analysisDisposition", entry.analysisDisposition().name());
        if (entry.textEncoding() == null) {
            node.putNull("textEncoding");
        } else {
            node.put("textEncoding", entry.textEncoding());
        }
        return node;
    }

    private static ObjectNode receiptNode(LocalGitCaptureReceipt receipt) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", "local-git-capture-receipt-v1");
        node.put("captureReceiptId", receipt.captureReceiptId());
        node.put("declaredRepositoryIdentity", receipt.declaredRepositoryIdentity());
        node.put("objectFormat", receipt.objectFormat());
        node.put("commitId", receipt.commitId());
        node.put("treeObjectId", receipt.treeObjectId());
        node.put("snapshotId", receipt.snapshotId());
        node.set("snapshotManifestRef", referenceNode(receipt.snapshotManifestRef()));
        node.put("regularFileCount", receipt.regularFileCount());
        node.put("analyzableTextFileCount", receipt.analyzableTextFileCount());
        node.put("nonAnalyzableMediaFileCount", receipt.nonAnalyzableMediaFileCount());
        node.put("unsupportedTreeEntryCount", receipt.unsupportedTreeEntryCount());
        node.put("worktreeRead", receipt.worktreeRead());
        node.put("networkAccess", receipt.networkAccess());
        node.set("capturePolicyRef", referenceNode(receipt.capturePolicyRef()));
        return node;
    }

    private static ObjectNode registrationNode(SourceRegistration registration) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", "source-registration-v1");
        node.put("sourceRegistrationId", registration.sourceRegistrationId());
        node.put("declaredRepositoryIdentity", registration.declaredRepositoryIdentity());
        node.put("commitId", registration.commitId());
        node.put("snapshotId", registration.snapshotId());
        node.set("snapshotManifestRef", referenceNode(registration.snapshotManifestRef()));
        node.set("captureReceiptRef", referenceNode(registration.captureReceiptRef()));
        node.put("regularFileCount", registration.regularFileCount());
        return node;
    }

    private static ObjectNode referenceNode(ArtifactReference reference) {
        ObjectNode node = JSON.createObjectNode();
        node.put("artifactId", reference.artifactId());
        node.put("sha256", reference.sha256());
        return node;
    }

    private static ArtifactReference reference(JsonNode node, String field) {
        JsonNode reference = node.get(field);
        requireFields(reference, Set.of("artifactId", "sha256"));
        return new ArtifactReference(text(reference, "artifactId"), text(reference, "sha256"));
    }

    private static void requireFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject()) {
            throw invalid("record must be an object");
        }
        Set<String> actual = new LinkedHashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(actual::add);
        if (!actual.equals(fields)) {
            throw invalid("record fields are not exact");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid(field + " must be text");
        }
        return value.textValue();
    }

    private static long whole(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || !value.isIntegralNumber()) {
            throw invalid(field + " must be an integer");
        }
        return value.longValue();
    }

    private static byte[] canonical(JsonNode value) {
        return CANONICAL.canonicalize(value).copyToByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String framedDigest(String domain, byte[] bytes) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        writeFrame(output, domain.getBytes(StandardCharsets.UTF_8));
        writeFrame(output, bytes);
        return sha256(output.toByteArray());
    }

    private static void writeFrame(java.io.ByteArrayOutputStream output, byte[] bytes) {
        output.writeBytes(java.nio.ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        output.writeBytes(bytes);
    }

    private static LocalGitCaptureException invalid(String detail) {
        return new LocalGitCaptureException("LOCAL_GIT_OBJECT_CORRUPT", detail);
    }
}
