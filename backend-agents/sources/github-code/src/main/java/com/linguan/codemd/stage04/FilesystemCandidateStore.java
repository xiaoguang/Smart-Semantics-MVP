package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * C1 immutable Candidate archive store. It receives already assembled bytes;
 * neither Stage03 generation nor Provider execution are reachable from here.
 */
final class FilesystemCandidateStore {
    static final Pattern HEX_64 = Pattern.compile("[0-9a-f]{64}");
    private static final String MANIFEST = "archive-manifest.json";
    private static final List<String> NON_MANIFEST_ARTIFACTS = List.of(
            "document.md",
            "candidate.json",
            "source-input.json",
            "verified-snapshot.json",
            "repository-model.json",
            "capability-report.json",
            "proven-facts.json",
            "proof-pack.json",
            "gap-ledger.json",
            "flow-slices.json",
            "evidence-capsules.json",
            "registry-bundle.json",
            "model-rounds.jsonl",
            "flow-interpretations.json",
            "repository-business-model.json",
            "nine-section-plan.json",
            "trace.jsonl",
            "generation-receipts.jsonl",
            "validation-baseline.json");
    private static final Set<String> ARTIFACT_SET = Set.copyOf(NON_MANIFEST_ARTIFACTS);
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    private final Path workspace;
    private final CandidateStoreLimits limits;

    FilesystemCandidateStore(Path workspace, CandidateStoreLimits limits) {
        Stage04Validation.require(workspace != null && limits != null);
        this.workspace = workspace.toAbsolutePath().normalize();
        this.limits = limits;
    }

    synchronized CandidateReference install(CandidateBundle bundle) {
        ArchiveInput archive = validateBundle(bundle);
        Path candidatesRoot = workspace.resolve("candidates");
        Path stagingRoot = workspace.resolve(".staging");
        Path destination = candidatesRoot.resolve(candidateDigest(archive.reference().candidateId()));
        validateWorkspacePaths(candidatesRoot, stagingRoot, destination);

        try {
            Files.createDirectories(workspace);
            ensureDirectoryNotSymlink(workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
            Files.createDirectories(candidatesRoot);
            ensureDirectoryNotSymlink(candidatesRoot, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            Files.createDirectories(stagingRoot);
            ensureDirectoryNotSymlink(stagingRoot, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);

            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(destination)) {
                    throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
                }
                if (installedExactlyMatches(destination, archive.files())) {
                    return archive.reference();
                }
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_IDENTITY_COLLISION);
            }

            Path staging = Files.createTempDirectory(stagingRoot, "candidate-");
            try {
                ensureDirectoryNotSymlink(staging, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
                writeArchive(staging, archive.files());
                verifyArchive(staging, archive.files());
                moveAtomically(staging, destination);
                verifyArchive(destination, archive.files());
                return archive.reference();
            } catch (M8Exception failure) {
                deleteStaging(staging);
                throw failure;
            } catch (IOException writeFailure) {
                deleteStaging(staging);
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
            }
        } catch (M8Exception failure) {
            throw failure;
        } catch (IOException writeFailure) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        }
    }

    private ArchiveInput validateBundle(CandidateBundle bundle) {
        if (bundle == null || bundle.reference() == null) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        CandidateReference reference = bundle.reference();
        Map<String, byte[]> supplied = bundle.artifacts();
        validateArtifactNames(supplied);
        if (!supplied.keySet().equals(ARTIFACT_SET)) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_ARTIFACT_SET_INVALID);
        }

        TreeMap<String, byte[]> ordered = new TreeMap<>();
        long total = 0;
        for (String artifact : NON_MANIFEST_ARTIFACTS) {
            byte[] bytes = supplied.get(artifact);
            if (bytes == null || bytes.length > limits.maxSidecarBytes()) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
            }
            validateArtifactBytes(artifact, bytes);
            total = addWithinLimit(total, bytes.length);
            ordered.put(artifact, bytes.clone());
        }
        validateReferenceAgainstArtifacts(reference, ordered);

        byte[] manifest = manifestBytes(ordered);
        if (manifest.length > limits.maxSidecarBytes()) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
        }
        total = addWithinLimit(total, manifest.length);
        ordered.put(MANIFEST, manifest);
        return new ArchiveInput(reference, Map.copyOf(ordered));
    }

    private void validateArtifactNames(Map<String, byte[]> supplied) {
        if (supplied == null) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_ARTIFACT_SET_INVALID);
        }
        for (String name : supplied.keySet()) {
            if (!validSimpleArtifactName(name)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_ARTIFACT_PATH_INVALID);
            }
        }
    }

    private static boolean validSimpleArtifactName(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0 || name.startsWith("~") || name.matches("^[A-Za-z]:.*")) {
            return false;
        }
        try {
            Path parsed = Path.of(name);
            return !parsed.isAbsolute() && parsed.getNameCount() == 1 && name.equals(parsed.getFileName().toString());
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    private void validateArtifactBytes(String artifact, byte[] bytes) {
        String text = decodeUtf8(bytes);
        if (artifact.endsWith(".json")) {
            canonicalJson(text, bytes);
        } else if (artifact.endsWith(".jsonl")) {
            canonicalJsonLines(text);
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_UTF8_INVALID);
        }
    }

    private JsonNode canonicalJson(String text, byte[] original) {
        try (JsonParser parser = JSON.getFactory().createParser(text)) {
            JsonNode parsed = JSON.readTree(parser);
            if (parsed == null || parser.nextToken() != null) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
            }
            byte[] canonical = JSON.writeValueAsBytes(canonicalNode(parsed));
            if (!Arrays.equals(original, canonical)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
            }
            return parsed;
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (IOException invalid) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
        }
    }

    private void canonicalJsonLines(String text) {
        if (text.isEmpty()) {
            return;
        }
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
        }
        String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
        for (String line : lines) {
            if (line.isEmpty()) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_CANONICAL_JSON_INVALID);
            }
            canonicalJson(line, line.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void validateReferenceAgainstArtifacts(CandidateReference reference, Map<String, byte[]> artifacts) {
        if (!reference.documentSha256().equals(sha256(artifacts.get("document.md")))) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        JsonNode sidecar = canonicalJson(decodeUtf8(artifacts.get("candidate.json")), artifacts.get("candidate.json"));
        if (!sidecar.isObject() || !reference.candidateId().equals(textField(sidecar, "candidateId"))
                || !reference.candidateContentId().equals(textField(sidecar, "candidateContentId"))
                || !reference.documentSha256().equals(textField(sidecar, "documentSha256"))
                || !reference.status().equals(textField(sidecar, "status"))) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
    }

    private byte[] manifestBytes(Map<String, byte[]> artifacts) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : new TreeMap<>(artifacts).entrySet()) {
            entries.add(Map.of("path", entry.getKey(), "sha256", sha256(entry.getValue()), "size", entry.getValue().length));
        }
        byte[] canonicalEntries = canonicalJsonBytes(Map.of("entries", entries));
        String manifestId = "archive-manifest:" + sha256(concat("archive-manifest-v2\n", canonicalEntries));
        return canonicalJsonBytes(Map.of("archiveManifestId", manifestId, "entries", entries,
                "schemaVersion", "archive-manifest-v2"));
    }

    private long addWithinLimit(long accumulated, int next) {
        try {
            long total = Math.addExact(accumulated, next);
            if (total > limits.maxCandidateBytes()) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
            }
            return total;
        } catch (ArithmeticException overflow) {
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED);
        }
    }

    private void validateWorkspacePaths(Path candidatesRoot, Path stagingRoot, Path destination) {
        rejectNearestExistingSymlink(workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
        rejectNearestExistingSymlink(candidatesRoot, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        rejectNearestExistingSymlink(stagingRoot, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        rejectNearestExistingSymlink(destination, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
    }

    private static void rejectNearestExistingSymlink(Path requested, M8FailureCode failure) {
        Path current = requested;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current != null && Files.isSymbolicLink(current)) {
            throw Stage04Validation.failure(failure);
        }
    }

    private static void ensureDirectoryNotSymlink(Path directory, M8FailureCode failure) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw Stage04Validation.failure(failure);
        }
    }

    private void writeArchive(Path staging, Map<String, byte[]> files) throws IOException {
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path target = staging.resolve(entry.getKey());
            if (!target.getParent().equals(staging) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
            }
            try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(entry.getValue());
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
        }
    }

    private void moveAtomically(Path staging, Path destination) throws IOException {
        try {
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_ATOMIC_MOVE_UNSUPPORTED);
        } catch (java.nio.file.FileAlreadyExistsException collision) {
            if (Files.isSymbolicLink(destination)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
            throw Stage04Validation.failure(M8FailureCode.CANDIDATE_IDENTITY_COLLISION);
        }
    }

    private boolean installedExactlyMatches(Path destination, Map<String, byte[]> expected) {
        try {
            verifyArchive(destination, expected);
            return true;
        } catch (M8Exception mismatch) {
            if (M8FailureCode.CANDIDATE_SIZE_LIMIT_EXCEEDED.name().equals(mismatch.failureCode())) {
                throw mismatch;
            }
            return false;
        }
    }

    private void verifyArchive(Path directory, Map<String, byte[]> expected) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        List<Path> actual = CandidateValidationSupport.readBoundedDirectory(directory,
                CandidateValidationSupport.DEFAULT_UNTRUSTED_DIRECTORY_ENTRIES,
                M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        if (actual.size() != expected.size() || actual.stream().anyMatch(Files::isSymbolicLink)
                || actual.stream().anyMatch(path -> !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        Set<String> names = actual.stream().map(path -> path.getFileName().toString())
                .collect(java.util.stream.Collectors.toSet());
        if (!names.equals(expected.keySet())) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            byte[] installed = CandidateValidationSupport.readBoundedRegular(directory.resolve(entry.getKey()),
                    limits.maxSidecarBytes(), M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            if (!Arrays.equals(entry.getValue(), installed)) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
            }
        }
    }

    private static void deleteStaging(Path staging) {
        try (Stream<Path> paths = Files.walk(staging)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A failed cleanup never weakens the immutable destination rule.
                }
            });
        } catch (IOException ignored) {
            // The staging area is private and never treated as an installed Candidate.
        }
    }

    private static String textField(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = JSON.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((name, value) -> object.set(name, canonicalNode(value)));
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = JSON.createArrayNode();
            for (JsonNode value : node) {
                array.add(canonicalNode(value));
            }
            return array;
        }
        return node;
    }

    private static byte[] canonicalJsonBytes(Object value) {
        try {
            return JSON.writeValueAsBytes(canonicalNode(JSON.valueToTree(value)));
        } catch (IOException | RuntimeException impossible) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    private static byte[] concat(String prefix, byte[] bytes) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(prefixBytes, prefixBytes.length + bytes.length);
        System.arraycopy(bytes, 0, result, prefixBytes.length, bytes.length);
        return result;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw Stage04Validation.failure(M8FailureCode.CANONICALIZATION_FAILED);
        }
    }

    static boolean digestIdentifier(String value, String prefix) {
        return value != null && value.startsWith(prefix)
                && HEX_64.matcher(value.substring(prefix.length())).matches();
    }

    private static String candidateDigest(String candidateId) {
        if (!digestIdentifier(candidateId, "candidate:")) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_MANIFEST_INVALID);
        }
        return candidateId.substring("candidate:".length());
    }

    private record ArchiveInput(CandidateReference reference, Map<String, byte[]> files) {
    }
}

record CandidateReference(String seriesId, int readerCandidateRound, String candidateId, String candidateContentId,
                          String documentSha256, String status) {
    CandidateReference {
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(seriesId, "series:"));
        Stage04Validation.require(readerCandidateRound == 1 || readerCandidateRound == 2);
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(candidateId, "candidate:"));
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(candidateContentId, "candidate-content:"));
        Stage04Validation.require(documentSha256 != null && FilesystemCandidateStore.HEX_64.matcher(documentSha256).matches());
        Stage04Validation.require("UNPUBLISHED_CANDIDATE".equals(status));
    }
}

final class CandidateBundle {
    private final CandidateReference reference;
    private final Map<String, byte[]> artifacts;

    CandidateBundle(CandidateReference reference, Map<String, byte[]> artifacts) {
        Stage04Validation.require(reference != null && artifacts != null);
        LinkedHashMap<String, byte[]> copied = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : artifacts.entrySet()) {
            Stage04Validation.require(entry.getKey() != null && entry.getValue() != null);
            copied.put(entry.getKey(), entry.getValue().clone());
        }
        this.reference = reference;
        this.artifacts = Map.copyOf(copied);
    }

    CandidateReference reference() {
        return reference;
    }

    Map<String, byte[]> artifacts() {
        LinkedHashMap<String, byte[]> copied = new LinkedHashMap<>();
        artifacts.forEach((name, bytes) -> copied.put(name, bytes.clone()));
        return Map.copyOf(copied);
    }
}

record CandidateStoreLimits(long maxCandidateBytes, long maxSidecarBytes) {
    CandidateStoreLimits {
        Stage04Validation.require(maxCandidateBytes > 0 && maxSidecarBytes > 0);
    }
}
