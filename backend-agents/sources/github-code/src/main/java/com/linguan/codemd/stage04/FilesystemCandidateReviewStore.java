package com.linguan.codemd.stage04;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Append-only §4.1 review-finding store.  Candidate validation stays behind
 * the public agent boundary; this store adds no source or model execution.
 */
final class FilesystemCandidateReviewStore implements CandidateReviewStore {
    private static final CandidateStoreLimits ARCHIVE_LIMITS = new CandidateStoreLimits(1_000_000, 200_000);
    private static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final Path workspace;
    private final CodeToMarkdownAgent agent;

    FilesystemCandidateReviewStore(Path workspace, CodeToMarkdownAgent agent) {
        if (workspace == null || agent == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        this.workspace = workspace.toAbsolutePath().normalize();
        requireNoSymlinkAncestors(this.workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
        this.agent = agent;
    }

    @Override
    public synchronized CandidateReviewFinding record(CandidateReviewFindingDraft draft) {
        CandidateReviewFinding finding;
        try {
            finding = CandidateReviewFinding.from(draft);
        } catch (M8Exception invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        ParentContext parent = validatedParent(finding.candidateId(), finding.validationReceiptId());
        requireReferences(parent, finding);
        byte[] bytes = CandidateValidationSupport.canonicalBytes(finding);
        writeFinding(parent.candidateId(), finding.findingId(), bytes);
        return finding;
    }

    @Override
    public synchronized ReviewFindingSet resolveExact(String roundOneCandidateId, List<String> findingIds) {
        if (!FilesystemCandidateStore.digestIdentifier(roundOneCandidateId, "candidate:") || findingIds == null
                || findingIds.isEmpty()) {
            throw parentInvalid();
        }
        Set<String> requested = new HashSet<>();
        for (String findingId : findingIds) {
            if (!FilesystemCandidateStore.digestIdentifier(findingId, "finding:") || !requested.add(findingId)) {
                throw parentInvalid();
            }
        }
        ParentContext parent = validatedParent(roundOneCandidateId, null);
        List<CandidateReviewFinding> resolved = new ArrayList<>();
        for (String findingId : requested) {
            CandidateReviewFinding finding = readFinding(parent.candidateId(), findingId);
            if (!roundOneCandidateId.equals(finding.candidateId())
                    || !parent.validation().validationReceiptId().equals(finding.validationReceiptId())
                    || !"APPROVED_FOR_ROUND_2".equals(finding.disposition())) {
                throw parentInvalid();
            }
            requireReferences(parent, finding);
            resolved.add(finding);
        }
        resolved.sort(Comparator.comparing(CandidateReviewFinding::findingId));
        try {
            return ReviewFindingSet.resolved(roundOneCandidateId, resolved);
        } catch (M8Exception invalid) {
            throw parentInvalid();
        }
    }

    private ParentContext validatedParent(String candidateId, String expectedValidationReceiptId) {
        CandidateReference reference;
        ValidationReceipt validation;
        CandidateArchive archive;
        try {
            requireNoSymlinkAncestors(workspace, M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK);
            requireNoSymlinkAncestors(workspace.resolve("candidates")
                    .resolve(CandidateArchive.candidateDigest(candidateId)), M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            reference = CandidateArchive.referenceFor(workspace, candidateId);
            if (reference.readerCandidateRound() != 1) {
                throw parentInvalid();
            }
            validation = agent.validateCandidate(reference);
            if (validation == null || !validation.valid() || !candidateId.equals(validation.candidateId())
                    || (expectedValidationReceiptId != null
                    && !expectedValidationReceiptId.equals(validation.validationReceiptId()))) {
                throw parentInvalid();
            }
            archive = CandidateArchive.open(workspace, reference, ARCHIVE_LIMITS);
            if (!archive.checks().stream().allMatch(check -> "PASS".equals(check.result()))) {
                throw parentInvalid();
            }
        } catch (M8Exception failure) {
            if (M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK.name().equals(failure.failureCode())
                    || M8FailureCode.CANDIDATE_DESTINATION_SYMLINK.name().equals(failure.failureCode())) {
                throw failure;
            }
            throw parentInvalid();
        } catch (RuntimeException failure) {
            throw parentInvalid();
        }
        return new ParentContext(reference, validation, archive);
    }

    private void requireReferences(ParentContext parent, CandidateReviewFinding finding) {
        Set<String> flowIds = archivedFlowIds(parent.archive());
        if (!flowIds.containsAll(finding.flowSliceIds())) {
            throw parentInvalid();
        }
        PlanReferences plan = archivedPlanReferences(parent.archive());
        if (!plan.readerItemKeys().containsAll(finding.readerItemKeys())
                || !plan.sectionNumbers().containsAll(finding.sectionNumbers())) {
            throw parentInvalid();
        }
        for (String itemKey : finding.readerItemKeys()) {
            Integer section = plan.itemSections().get(itemKey);
            if (section == null || (!finding.sectionNumbers().isEmpty()
                    && !finding.sectionNumbers().contains(section))) {
                throw parentInvalid();
            }
        }
    }

    private static Set<String> archivedFlowIds(CandidateArchive archive) {
        JsonNode root = archive.json("flow-slices.json");
        JsonNode flows = root == null ? null : root.get("flowSlices");
        if (root == null || !root.isObject() || !"candidate-flow-slices-v1".equals(text(root, "schemaVersion"))
                || flows == null || !flows.isArray()) {
            throw parentInvalid();
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode flow : flows) {
            String id = text(flow, "flowSliceId");
            if (!FilesystemCandidateStore.digestIdentifier(id, "flow-slice:") || !ids.add(id)) {
                throw parentInvalid();
            }
        }
        return Set.copyOf(ids);
    }

    private static PlanReferences archivedPlanReferences(CandidateArchive archive) {
        JsonNode root = archive.json("nine-section-plan.json");
        JsonNode sections = root == null ? null : root.get("sections");
        if (root == null || !root.isObject() || !"nine-section-plan-v1".equals(text(root, "schemaVersion"))
                || sections == null || !sections.isArray() || sections.size() != 9) {
            throw parentInvalid();
        }
        Set<Integer> sectionNumbers = new HashSet<>();
        Set<String> itemKeys = new HashSet<>();
        java.util.Map<String, Integer> itemSections = new java.util.HashMap<>();
        for (int index = 0; index < sections.size(); index++) {
            JsonNode section = sections.get(index);
            JsonNode items = section == null ? null : section.get("items");
            int sectionNumber = index + 1;
            if (section == null || !section.isObject() || items == null || !items.isArray()
                    || !sectionNumbers.add(sectionNumber)) {
                throw parentInvalid();
            }
            for (JsonNode item : items) {
                String itemKey = text(item, "readerItemKey");
                if (item == null || !item.isObject() || itemKey == null || !itemKey.startsWith("reader:")
                        || itemKey.length() == "reader:".length() || !itemKeys.add(itemKey)
                        || itemSections.put(itemKey, sectionNumber) != null) {
                    throw parentInvalid();
                }
            }
        }
        return new PlanReferences(Set.copyOf(itemKeys), Set.copyOf(sectionNumbers), java.util.Map.copyOf(itemSections));
    }

    private void writeFinding(String candidateId, String findingId, byte[] expected) {
        Path directory = findingsDirectory(candidateId);
        ensureDirectory(directory);
        Path target = directory.resolve(findingId.substring("finding:".length()) + ".json");
        requireNoSymlinkAncestors(target, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            verifyExisting(target, expected, M8FailureCode.CANDIDATE_IDENTITY_COLLISION);
            return;
        }

        Path staging = null;
        try {
            staging = Files.createTempFile(directory, ".finding-", ".staging");
            if (Files.isSymbolicLink(staging) || !Files.isRegularFile(staging, LinkOption.NOFOLLOW_LINKS)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
            writeForced(staging, expected);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                staging = null;
            } catch (java.nio.file.FileAlreadyExistsException collision) {
                verifyExisting(target, expected, M8FailureCode.CANDIDATE_IDENTITY_COLLISION);
            } catch (AtomicMoveNotSupportedException unsupported) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_ATOMIC_MOVE_UNSUPPORTED);
            }
            verifyExisting(target, expected, M8FailureCode.CANDIDATE_IDENTITY_COLLISION);
        } catch (M8Exception failure) {
            throw failure;
        } catch (IOException failure) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        } finally {
            deleteStaging(staging);
        }
    }

    private CandidateReviewFinding readFinding(String candidateId, String findingId) {
        Path target = findingsDirectory(candidateId).resolve(findingId.substring("finding:".length()) + ".json");
        try {
            requireNoSymlinkAncestors(target, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
                throw parentInvalid();
            }
            byte[] bytes = readNoFollow(target);
            JsonNode node = CandidateValidationSupport.parseCanonicalJson(CandidateValidationSupport.strictUtf8(bytes), bytes);
            CandidateReviewFinding finding = JSON.treeToValue(node, CandidateReviewFinding.class);
            if (!findingId.equals(finding.findingId())
                    || !java.util.Arrays.equals(bytes, CandidateValidationSupport.canonicalBytes(finding))) {
                throw parentInvalid();
            }
            return finding;
        } catch (M8Exception failure) {
            if (M8FailureCode.CANDIDATE_WORKSPACE_SYMLINK.name().equals(failure.failureCode())
                    || M8FailureCode.CANDIDATE_DESTINATION_SYMLINK.name().equals(failure.failureCode())) {
                throw failure;
            }
            throw parentInvalid();
        } catch (IOException | RuntimeException failure) {
            throw parentInvalid();
        }
    }

    private Path findingsDirectory(String candidateId) {
        String digest = CandidateArchive.candidateDigest(candidateId);
        return workspace.resolve("reviews").resolve(digest).resolve("findings");
    }

    private void ensureDirectory(Path directory) {
        try {
            requireNoSymlinkAncestors(directory, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            Files.createDirectories(directory);
            requireNoSymlinkAncestors(directory, M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
                throw Stage04Validation.failure(M8FailureCode.CANDIDATE_DESTINATION_SYMLINK);
            }
        } catch (M8Exception failure) {
            throw failure;
        } catch (IOException failure) {
            throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
        }
    }

    private static void verifyExisting(Path target, byte[] expected, M8FailureCode collisionCode) {
        try {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)
                    || !java.util.Arrays.equals(expected, readNoFollow(target))) {
                throw Stage04Validation.failure(collisionCode);
            }
        } catch (M8Exception failure) {
            throw failure;
        } catch (IOException failure) {
            throw Stage04Validation.failure(collisionCode);
        }
    }

    private static byte[] readNoFollow(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size < 0 || size > Integer.MAX_VALUE) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
            }
            ByteBuffer bytes = ByteBuffer.allocate((int) size);
            while (bytes.hasRemaining() && channel.read(bytes) >= 0) {
                // Drain the immutable regular file before comparing its canonical bytes.
            }
            if (bytes.hasRemaining()) {
                throw Stage04Validation.failure(M8FailureCode.ARCHIVE_WRITE_FAILED);
            }
            return bytes.array();
        }
    }

    private static void writeForced(Path staging, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer remaining = ByteBuffer.wrap(bytes);
            while (remaining.hasRemaining()) {
                channel.write(remaining);
            }
            channel.force(true);
        }
    }

    private static void deleteStaging(Path staging) {
        if (staging == null) {
            return;
        }
        try {
            Files.deleteIfExists(staging);
        } catch (IOException ignored) {
            // A private staging cleanup failure never makes an uninstalled finding visible.
        }
    }

    private void requireNoSymlinkAncestors(Path requested, M8FailureCode failure) {
        Path normalized = requested.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspace)) {
            throw Stage04Validation.failure(failure);
        }
        // The platform may expose a system alias such as /var -> /private/var.
        // Only the configured workspace itself and descendants are part of this
        // store's trust boundary; reject every link at or below that boundary.
        CandidateArchive.requireNoSymlinkAncestor(workspace, failure);
        Path current = workspace;
        for (Path part : workspace.relativize(normalized)) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw Stage04Validation.failure(failure);
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static M8Exception parentInvalid() {
        return Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID);
    }

    private record ParentContext(CandidateReference reference, ValidationReceipt validation, CandidateArchive archive) {
        String candidateId() {
            return reference.candidateId();
        }
    }

    private record PlanReferences(Set<String> readerItemKeys, Set<Integer> sectionNumbers,
                                  java.util.Map<String, Integer> itemSections) {
    }
}
