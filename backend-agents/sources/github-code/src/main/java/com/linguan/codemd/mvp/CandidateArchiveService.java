package com.linguan.codemd.mvp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Content-addressed, fail-closed projection of one verified candidate into one
 * workspace. The workspace has no mutable cache or hidden subdirectories.
 */
public final class CandidateArchiveService {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> ARTIFACT_NAMES = List.of(
            "document.md",
            "candidate.json",
            "evidence-pack.json",
            "r1-interpretation.json",
            "r2-precision-review.json",
            "trace-index.json",
            "generation-receipt.json",
            "validation-receipt.json");
    private static final Set<String> ARTIFACT_SET = Set.copyOf(ARTIFACT_NAMES);
    private static final List<String> SECTION_TITLES = List.of(
            "文档说明", "业务目标", "业务对象", "业务活动", "字段与维度",
            "对象关系", "指标口径", "示例问题", "待确认事项");

    private final Path workspace;

    public CandidateArchiveService(Path workspace) {
        this.workspace = requireWorkspacePath(workspace);
    }

    /** Generates once through the supplied provider, then stores exactly the eight contract files. */
    public CandidateReference archive(GenerationRequest request) {
        WorkspaceState existing = inspectWorkspaceForArchive();
        MvpGenerationCore.GeneratedCandidate generated = MvpGenerationCore.generate(request);
        return archiveGenerated(existing, generated);
    }

    /** Generates and seals one provider-free deterministic baseline candidate. */
    public CandidateReference archive(BaselineGenerationRequest request) {
        WorkspaceState existing = inspectWorkspaceForArchive();
        MvpGenerationCore.GeneratedCandidate generated = MvpGenerationCore.generateBaseline(request);
        return archiveGenerated(existing, generated);
    }

    private CandidateReference archiveGenerated(WorkspaceState existing,
                                                MvpGenerationCore.GeneratedCandidate generated) {
        CandidateReference candidate = generated.reference();
        Map<String, byte[]> artifacts = artifacts(generated);

        if (existing == WorkspaceState.COMPLETE) {
            if (matchesExistingArtifacts(artifacts)) {
                return candidate;
            }
            throw failure("WORKSPACE_CONFLICT");
        }
        installNewWorkspace(artifacts, existing);
        return candidate;
    }

    /** Revalidates the archived document without ever rewriting its Markdown bytes. */
    public ValidationReceipt validate(CandidateReference candidate) {
        ensureExistingWorkspace();
        Set<String> findings = new TreeSet<>();
        if (!hasExactArtifactNames()) {
            findings.add("WORKSPACE_LAYOUT_INVALID");
        }

        JsonNode recordedCandidate = readObjectQuietly(workspace.resolve("candidate.json"));
        if (recordedCandidate == null) {
            findings.add("CANDIDATE_SIDECAR_INVALID");
        } else {
            if (!candidate.candidateId().equals(text(recordedCandidate, "candidateId"))) {
                findings.add("CANDIDATE_ID_MISMATCH");
            }
            if (!candidate.candidateContentId().equals(text(recordedCandidate,
                    "candidateContentId"))) {
                findings.add("CANDIDATE_CONTENT_ID_MISMATCH");
            }
            if (!candidate.candidateContentId().equals(text(recordedCandidate,
                    "documentSha256"))) {
                findings.add("RECORDED_DOCUMENT_HASH_MISMATCH");
            }
        }

        String documentSha256 = "";
        String markdown = null;
        Path document = workspace.resolve("document.md");
        if (!isRegularNonLink(document)) {
            findings.add("DOCUMENT_MISSING");
        } else {
            try {
                byte[] bytes = Files.readAllBytes(document);
                documentSha256 = sha256(bytes);
                markdown = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                findings.add("DOCUMENT_READ_FAILURE");
            }
        }

        if (markdown != null) {
            if (!candidate.candidateContentId().equals(documentSha256)) {
                findings.add("DOCUMENT_HASH_MISMATCH");
            }
            if (!SECTION_TITLES.equals(sectionTitles(markdown))) {
                findings.add("NINE_SECTION_SHAPE_INVALID");
            }
        }

        ValidationReceipt receipt = new ValidationReceipt(findings.isEmpty(), candidate.candidateId(),
                candidate.candidateContentId(), documentSha256, List.copyOf(findings));
        writeValidationReceipt(receipt);
        return receipt;
    }

    /** Resolves an archived trace after a process restart; no in-memory candidate state is used. */
    public TraceView trace(TraceQuery query) {
        ensureExistingWorkspace();
        JsonNode candidate = readObject(workspace.resolve("candidate.json"), "CANDIDATE_SIDECAR_INVALID");
        if (!query.candidateId().equals(text(candidate, "candidateId"))) {
            throw failure("UNKNOWN_CANDIDATE");
        }
        JsonNode traceIndex = readObject(workspace.resolve("trace-index.json"), "TRACE_INDEX_INVALID");
        if (!query.candidateId().equals(text(traceIndex, "candidateId"))) {
            throw failure("UNKNOWN_CANDIDATE");
        }
        JsonNode items = traceIndex.get("items");
        if (items == null || !items.isObject()) {
            throw failure("TRACE_INDEX_INVALID");
        }
        JsonNode evidence = items.get(query.itemKey());
        if (evidence == null || !evidence.isArray()) {
            throw failure("UNKNOWN_TRACE_ITEM");
        }

        List<TraceEvidence> locators = new ArrayList<>();
        for (JsonNode entry : evidence) {
            String relativePath = requiredText(entry, "relativePath", "TRACE_INDEX_INVALID");
            rejectEscapingRelativePath(relativePath, "TRACE_INDEX_INVALID");
            locators.add(new TraceEvidence(relativePath,
                    requiredPositiveInt(entry, "startLine", "TRACE_INDEX_INVALID"),
                    requiredPositiveInt(entry, "endLine", "TRACE_INDEX_INVALID"),
                    requiredPositiveInt(entry, "startColumn", "TRACE_INDEX_INVALID"),
                    requiredPositiveInt(entry, "endColumn", "TRACE_INDEX_INVALID"),
                    requiredText(entry, "excerptSha256", "TRACE_INDEX_INVALID")));
        }
        if (locators.isEmpty()) {
            throw failure("UNKNOWN_TRACE_ITEM");
        }
        return new TraceView(query.candidateId(), query.itemKey(), locators);
    }

    private Map<String, byte[]> artifacts(MvpGenerationCore.GeneratedCandidate generated) {
        CandidateReference candidate = generated.reference();
        ValidationReceipt initialReceipt = new ValidationReceipt(true, candidate.candidateId(),
                candidate.candidateContentId(), candidate.candidateContentId(), List.of());
        Map<String, byte[]> result = new LinkedHashMap<>();
        result.put("document.md", candidate.markdown().getBytes(StandardCharsets.UTF_8));
        result.put("candidate.json", canonicalBytes(candidateNode(candidate)));
        result.put("evidence-pack.json", canonicalBytes(evidencePackNode(generated)));
        result.put("r1-interpretation.json", canonicalBytes(roundNode(generated.roundOneResponses())));
        result.put("r2-precision-review.json", canonicalBytes(roundNode(generated.roundTwoResponses())));
        result.put("trace-index.json", canonicalBytes(traceNode(candidate, generated.traceIndex())));
        result.put("generation-receipt.json", canonicalBytes(generationReceiptNode(generated)));
        result.put("validation-receipt.json", canonicalBytes(receiptNode(initialReceipt)));
        if (!result.keySet().equals(ARTIFACT_SET)) {
            throw failure("ARCHIVE_ARTIFACT_CONTRACT_INVALID");
        }
        return result;
    }

    private ObjectNode candidateNode(CandidateReference candidate) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", 1);
        node.put("candidateId", candidate.candidateId());
        node.put("candidateContentId", candidate.candidateContentId());
        node.put("documentSha256", candidate.candidateContentId());
        return node;
    }

    private ObjectNode evidencePackNode(MvpGenerationCore.GeneratedCandidate generated) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("candidateId", generated.reference().candidateId());
        root.put("snapshotContentId", generated.snapshotContentId());
        ArrayNode evidence = root.putArray("evidence");
        generated.verifiedEvidence().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    TraceEvidence locator = entry.getValue();
                    ObjectNode node = evidence.addObject();
                    node.put("evidenceId", entry.getKey());
                    node.put("relativePath", locator.relativePath());
                    node.put("startLine", locator.startLine());
                    node.put("endLine", locator.endLine());
                    node.put("startColumn", locator.startColumn());
                    node.put("endColumn", locator.endColumn());
                    node.put("excerptSha256", locator.excerptSha256());
                });
        return root;
    }

    private JsonNode roundNode(List<MvpGenerationCore.RecordedResponse> responses) {
        if (responses.size() == 1) {
            return responses.get(0).response();
        }
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        ArrayNode recorded = root.putArray("recordedResponses");
        responses.stream().sorted(Comparator.comparing(MvpGenerationCore.RecordedResponse::flowId))
                .forEach(response -> {
                    ObjectNode entry = recorded.addObject();
                    entry.put("flowId", response.flowId());
                    entry.set("response", response.response());
                });
        return root;
    }

    private ObjectNode traceNode(CandidateReference candidate,
                                 Map<String, List<TraceEvidence>> traceIndex) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("candidateId", candidate.candidateId());
        ObjectNode items = root.putObject("items");
        traceIndex.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ArrayNode evidence = items.putArray(entry.getKey());
            for (TraceEvidence locator : entry.getValue()) {
                ObjectNode node = evidence.addObject();
                node.put("relativePath", locator.relativePath());
                node.put("startLine", locator.startLine());
                node.put("endLine", locator.endLine());
                node.put("startColumn", locator.startColumn());
                node.put("endColumn", locator.endColumn());
                node.put("excerptSha256", locator.excerptSha256());
            }
        });
        return root;
    }

    private ObjectNode generationReceiptNode(MvpGenerationCore.GeneratedCandidate generated) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("candidateId", generated.reference().candidateId());
        root.put("candidateContentId", generated.reference().candidateContentId());
        root.put("snapshotContentId", generated.snapshotContentId());
        ArrayNode rounds = root.putArray("recordedRounds");
        addRoundReceipt(rounds, 1, generated.roundOneResponses());
        addRoundReceipt(rounds, 2, generated.roundTwoResponses());
        return root;
    }

    private void addRoundReceipt(ArrayNode rounds, int round,
                                 List<MvpGenerationCore.RecordedResponse> responses) {
        ObjectNode entry = rounds.addObject();
        entry.put("round", round);
        entry.put("responseCount", responses.size());
        entry.put("responseSha256", sha256(canonicalBytes(roundNode(responses))));
    }

    private ObjectNode receiptNode(ValidationReceipt receipt) {
        ObjectNode node = JSON.createObjectNode();
        node.put("schemaVersion", 1);
        node.put("candidateId", receipt.candidateId());
        node.put("candidateContentId", receipt.candidateContentId());
        node.put("documentSha256", receipt.documentSha256());
        node.put("valid", receipt.valid());
        ArrayNode findings = node.putArray("findings");
        receipt.findings().forEach(findings::add);
        return node;
    }

    private void installNewWorkspace(Map<String, byte[]> artifacts, WorkspaceState existing) {
        Path parent = verifiedWorkspaceParent();
        Path staging = null;
        try {
            staging = Files.createTempDirectory(parent, ".candidate-archive-");
            for (String name : ARTIFACT_NAMES) {
                Files.write(staging.resolve(name), artifacts.get(name));
            }
            if (existing == WorkspaceState.EMPTY) {
                Files.delete(workspace);
            }
            Files.move(staging, workspace, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unavailable) {
            throw failure("ARCHIVE_ATOMIC_MOVE_UNSUPPORTED", unavailable);
        } catch (IOException writeFailure) {
            throw failure("ARCHIVE_WRITE_FAILURE", writeFailure);
        } finally {
            if (staging != null) {
                deleteEmptyStaging(staging);
            }
        }
    }

    private void writeValidationReceipt(ValidationReceipt receipt) {
        Path receiptPath = workspace.resolve("validation-receipt.json");
        if (Files.exists(receiptPath, LinkOption.NOFOLLOW_LINKS)
                && !isRegularNonLink(receiptPath)) {
            throw failure("VALIDATION_RECEIPT_PATH_INVALID");
        }
        Path parent = verifiedWorkspaceParent();
        Path staging = null;
        try {
            staging = Files.createTempFile(parent, ".validation-receipt-", ".json");
            Files.write(staging, canonicalBytes(receiptNode(receipt)));
            Files.move(staging, receiptPath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unavailable) {
            throw failure("VALIDATION_RECEIPT_ATOMIC_MOVE_UNSUPPORTED", unavailable);
        } catch (IOException writeFailure) {
            throw failure("VALIDATION_RECEIPT_WRITE_FAILURE", writeFailure);
        } finally {
            if (staging != null) {
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException ignored) {
                    // A failed cleanup never changes the archived document and is not recoverable here.
                }
            }
        }
    }

    private WorkspaceState inspectWorkspaceForArchive() {
        if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            verifiedWorkspaceParent();
            return WorkspaceState.MISSING;
        }
        ensureExistingWorkspace();
        try (var paths = Files.list(workspace)) {
            List<Path> entries = paths.toList();
            if (entries.isEmpty()) {
                return WorkspaceState.EMPTY;
            }
            if (entries.size() != ARTIFACT_NAMES.size()
                    || !entries.stream().allMatch(path -> ARTIFACT_SET.contains(path.getFileName().toString()))
                    || !entries.stream().allMatch(CandidateArchiveService::isRegularNonLink)) {
                throw failure("WORKSPACE_NOT_EMPTY_OR_CONFORMING");
            }
            for (Path entry : entries) {
                if (!entry.getFileName().toString().equals("document.md")
                        && readObjectQuietly(entry) == null) {
                    throw failure("WORKSPACE_NOT_EMPTY_OR_CONFORMING");
                }
            }
            return WorkspaceState.COMPLETE;
        } catch (IOException unreadable) {
            throw failure("WORKSPACE_NOT_EMPTY_OR_CONFORMING", unreadable);
        }
    }

    private boolean matchesExistingArtifacts(Map<String, byte[]> expected) {
        for (String name : ARTIFACT_NAMES) {
            try {
                if (!Arrays.equals(expected.get(name), Files.readAllBytes(workspace.resolve(name)))) {
                    return false;
                }
            } catch (IOException unreadable) {
                return false;
            }
        }
        return true;
    }

    private boolean hasExactArtifactNames() {
        try (var paths = Files.list(workspace)) {
            List<Path> entries = paths.toList();
            return entries.size() == ARTIFACT_NAMES.size()
                    && entries.stream().allMatch(path -> ARTIFACT_SET.contains(path.getFileName().toString()))
                    && entries.stream().allMatch(CandidateArchiveService::isRegularNonLink);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private void ensureExistingWorkspace() {
        if (Files.isSymbolicLink(workspace)
                || !Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)) {
            throw failure("WORKSPACE_INVALID");
        }
        verifiedWorkspaceParent();
    }

    private Path verifiedWorkspaceParent() {
        Path parent = workspace.getParent();
        if (parent == null) {
            throw failure("WORKSPACE_PATH_INVALID");
        }
        try {
            Path realParent = parent.toRealPath();
            if (!Files.isDirectory(realParent)) {
                throw failure("WORKSPACE_PATH_INVALID");
            }
            return realParent;
        } catch (IOException invalid) {
            throw failure("WORKSPACE_PATH_INVALID", invalid);
        }
    }

    private static Path requireWorkspacePath(Path candidate) {
        if (candidate == null) {
            throw new NullPointerException("workspace");
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        if (normalized.getParent() == null || normalized.getFileName() == null
                || normalized.getFileName().toString().isBlank()) {
            throw failure("WORKSPACE_PATH_INVALID");
        }
        return normalized;
    }

    private static boolean isRegularNonLink(Path path) {
        return !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static JsonNode readObject(Path path, String errorCode) {
        try {
            if (!isRegularNonLink(path)) {
                throw failure(errorCode);
            }
            JsonNode node = JSON.readTree(Files.readAllBytes(path));
            if (node == null || !node.isObject()) {
                throw failure(errorCode);
            }
            return node;
        } catch (JsonProcessingException invalid) {
            throw failure(errorCode);
        } catch (IOException invalid) {
            throw failure(errorCode, invalid);
        }
    }

    private static JsonNode readObjectQuietly(Path path) {
        try {
            return readObject(path, "SIDECAR_INVALID");
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isTextual() ? value.textValue() : "";
    }

    private static String requiredText(JsonNode object, String field, String errorCode) {
        String value = text(object, field);
        if (value.isBlank()) {
            throw failure(errorCode);
        }
        return value;
    }

    private static int requiredPositiveInt(JsonNode object, String field, String errorCode) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || !value.canConvertToInt() || value.intValue() < 1) {
            throw failure(errorCode);
        }
        return value.intValue();
    }

    private static void rejectEscapingRelativePath(String path, String errorCode) {
        if (path.isBlank() || path.indexOf('\\') >= 0) {
            throw failure(errorCode);
        }
        final Path relative;
        try {
            relative = Path.of(path);
        } catch (RuntimeException invalid) {
            throw failure(errorCode);
        }
        if (relative.isAbsolute() || relative.getNameCount() == 0) {
            throw failure(errorCode);
        }
        for (Path part : relative) {
            if (part.toString().equals(".") || part.toString().equals("..") || part.toString().isBlank()) {
                throw failure(errorCode);
            }
        }
    }

    private static List<String> sectionTitles(String markdown) {
        return markdown.lines()
                .filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3))
                .toList();
    }

    private static byte[] canonicalBytes(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw failure("JSON_OBJECT_REQUIRED");
        }
        try {
            return JSON.writeValueAsBytes(canonicalize(node));
        } catch (JsonProcessingException impossible) {
            throw failure("JSON_SERIALIZATION_FAILURE", impossible);
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> result.set(name, canonicalize(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static RuntimeException failure(String errorCode) {
        return MvpGenerationCore.failure(errorCode);
    }

    private static RuntimeException failure(String errorCode, Exception cause) {
        return new IllegalStateException(errorCode, cause);
    }

    private static void deleteEmptyStaging(Path staging) {
        try (var paths = Files.list(staging)) {
            if (paths.findAny().isEmpty()) {
                Files.deleteIfExists(staging);
                return;
            }
        } catch (IOException ignored) {
            return;
        }
        try (var paths = Files.list(staging)) {
            for (Path entry : paths.toList()) {
                Files.deleteIfExists(entry);
            }
            Files.deleteIfExists(staging);
        } catch (IOException ignored) {
            // A staging cleanup failure cannot change the candidate workspace.
        }
    }

    private enum WorkspaceState {
        MISSING,
        EMPTY,
        COMPLETE
    }
}
