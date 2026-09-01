package com.linguan.codemd.target.stage01.sourceindex;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguan.codemd.target.artifacts.CanonicalJsonCodec;
import com.linguan.codemd.target.stage01.capture.AnalysisDisposition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Strict canonical writer/parser for the M2 domain payload inside its module envelope. */
public final class VerifiedSourceIndexArtifact {
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final Set<String> ROOT_FIELDS = Set.of(
            "snapshotId",
            "requestArtifactId",
            "verifiedRegularFileCount",
            "analyzableTextFileCount",
            "nonAnalyzableMediaFileCount",
            "verifiedFiles",
            "shardReceipts",
            "sourceIntegrity");
    private static final Set<String> FILE_FIELDS = Set.of(
            "fileId",
            "path",
            "gitMode",
            "mediaType",
            "sizeBytes",
            "sha256",
            "analysisDisposition",
            "textEncoding",
            "lineIndexDigest");
    private static final Set<String> SHARD_FIELDS =
            Set.of("shardId", "denominatorFileIds", "verifiedFileIds", "status", "gapIds");

    private VerifiedSourceIndexArtifact() {}

    public static JsonNode write(VerifiedSourceIndex index) {
        if (index == null) {
            throw new SourceIndexException("REQUEST_SCHEMA_INVALID", "verified source index must not be null");
        }
        ObjectNode root = JSON.createObjectNode();
        root.put("snapshotId", index.snapshotId());
        root.put("requestArtifactId", index.requestArtifactId());
        root.put("verifiedRegularFileCount", index.verifiedRegularFileCount());
        root.put("analyzableTextFileCount", index.analyzableTextFileCount());
        root.put("nonAnalyzableMediaFileCount", index.nonAnalyzableMediaFileCount());
        ArrayNode files = root.putArray("verifiedFiles");
        index.verifiedFiles().forEach(file -> writeFile(files.addObject(), file));
        ArrayNode shards = root.putArray("shardReceipts");
        index.shardReceipts().forEach(shard -> writeShard(shards.addObject(), shard));
        root.put("sourceIntegrity", index.sourceIntegrity());
        return root;
    }

    public static VerifiedSourceIndex parse(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw invalid("M2 payload must be an object");
        }
        ObjectNode root = (ObjectNode) payload;
        exact(root, ROOT_FIELDS, "M2 payload");
        List<VerifiedSourceFile> files = new ArrayList<>();
        for (JsonNode node : array(root, "verifiedFiles")) {
            if (!node.isObject()) {
                throw invalid("verified file must be an object");
            }
            ObjectNode file = (ObjectNode) node;
            exact(file, FILE_FIELDS, "verified file");
            JsonNode encoding = file.get("textEncoding");
            JsonNode lineIndex = file.get("lineIndexDigest");
            try {
                files.add(new VerifiedSourceFile(
                        text(file, "fileId"),
                        text(file, "path"),
                        text(file, "gitMode"),
                        text(file, "mediaType"),
                        whole(file, "sizeBytes"),
                        text(file, "sha256"),
                        AnalysisDisposition.valueOf(text(file, "analysisDisposition")),
                        nullableText(encoding, "textEncoding"),
                        nullableText(lineIndex, "lineIndexDigest")));
            } catch (IllegalArgumentException exception) {
                throw invalid("analysisDisposition is invalid");
            }
        }
        List<SourceShardReceipt> shards = new ArrayList<>();
        for (JsonNode node : array(root, "shardReceipts")) {
            if (!node.isObject()) {
                throw invalid("source shard must be an object");
            }
            ObjectNode shard = (ObjectNode) node;
            exact(shard, SHARD_FIELDS, "source shard");
            shards.add(new SourceShardReceipt(
                    text(shard, "shardId"),
                    texts(array(shard, "denominatorFileIds"), "denominatorFileIds"),
                    texts(array(shard, "verifiedFileIds"), "verifiedFileIds"),
                    text(shard, "status"),
                    texts(array(shard, "gapIds"), "gapIds")));
        }
        return new VerifiedSourceIndex(
                text(root, "snapshotId"),
                text(root, "requestArtifactId"),
                whole(root, "verifiedRegularFileCount"),
                whole(root, "analyzableTextFileCount"),
                whole(root, "nonAnalyzableMediaFileCount"),
                files,
                shards,
                text(root, "sourceIntegrity"));
    }

    public static com.linguan.codemd.target.artifacts.ImmutableBytes canonicalUtf8(VerifiedSourceIndex index) {
        return new CanonicalJsonCodec().canonicalize(write(index));
    }

    private static void writeFile(ObjectNode node, VerifiedSourceFile file) {
        node.put("fileId", file.fileId());
        node.put("path", file.path());
        node.put("gitMode", file.gitMode());
        node.put("mediaType", file.mediaType());
        node.put("sizeBytes", file.sizeBytes());
        node.put("sha256", file.sha256());
        node.put("analysisDisposition", file.analysisDisposition().name());
        nullable(node, "textEncoding", file.textEncoding());
        nullable(node, "lineIndexDigest", file.lineIndexDigest());
    }

    private static void writeShard(ObjectNode node, SourceShardReceipt shard) {
        node.put("shardId", shard.shardId());
        strings(node.putArray("denominatorFileIds"), shard.denominatorFileIds());
        strings(node.putArray("verifiedFileIds"), shard.verifiedFileIds());
        node.put("status", shard.status());
        strings(node.putArray("gapIds"), shard.gapIds());
    }

    private static void nullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void strings(ArrayNode node, List<String> values) {
        values.forEach(node::add);
    }

    private static void exact(ObjectNode node, Set<String> expected, String name) {
        Set<String> actual = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid(name + " has unknown, missing, or duplicate fields");
        }
    }

    private static ArrayNode array(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isArray()) {
            throw invalid(field + " must be an array");
        }
        return (ArrayNode) value;
    }

    private static List<String> texts(ArrayNode values, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                throw invalid(field + " must contain text");
            }
            result.add(value.textValue());
        }
        return List.copyOf(result);
    }

    private static String text(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalid(field + " must be text");
        }
        return value.textValue();
    }

    private static String nullableText(JsonNode value, String field) {
        if (value == null || (!value.isNull() && !value.isTextual())) {
            throw invalid(field + " must be required nullable text");
        }
        return value.isNull() ? null : value.textValue();
    }

    private static long whole(ObjectNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalid(field + " must be an integer");
        }
        return value.longValue();
    }

    private static SourceIndexException invalid(String detail) {
        return new SourceIndexException("REQUEST_SCHEMA_INVALID", detail);
    }
}
