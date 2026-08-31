package com.linguan.codemd.stage04;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 04 C1 RED tracer for the immutable filesystem Candidate store.
 *
 * <p>All bytes are synthetic and supplied directly to the proposed store seam.
 * The test does not invoke Stage 03, a Provider, HTTP, or a customer source
 * tree.</p>
 */
class Stage04CandidateStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();
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
    private static final Set<String> COMPLETE_ARTIFACT_SET = Stream.concat(
                    NON_MANIFEST_ARTIFACTS.stream(), Stream.of("archive-manifest.json"))
            .collect(Collectors.toUnmodifiableSet());

    @TempDir
    Path tempWorkspace;

    @Test
    void installWritesExactRegularArtifactSetAndGeneratedManifestClosure() throws IOException {
        Path workspace = tempWorkspace.resolve("install");
        Fixture fixture = fixture();
        CandidateReference installed = new FilesystemCandidateStore(workspace, limits())
                .install(fixture.bundle());
        Path candidateDirectory = installedDirectory(workspace, installed);

        assertEquals(installed, fixture.reference());
        assertEquals(installed.candidateId().substring("candidate:".length()),
                candidateDirectory.getFileName().toString());
        assertFalse(Files.isSymbolicLink(candidateDirectory));
        Set<String> actualNames;
        try (Stream<Path> paths = Files.walk(candidateDirectory)) {
            actualNames = paths.filter(path -> !path.equals(candidateDirectory))
                    .peek(path -> assertFalse(Files.isSymbolicLink(path)))
                    .peek(path -> assertTrue(Files.isRegularFile(path)))
                    .map(candidateDirectory::relativize)
                    .map(Path::toString)
                    .collect(Collectors.toUnmodifiableSet());
        }
        assertEquals(COMPLETE_ARTIFACT_SET, actualNames,
                "the store must install the exact §10 artifact set plus its generated manifest");

        // This is a storage-only seam fixture: it supplies bytes directly and
        // never claims a non-zero Flow is replayable.  Semantic archive-v2
        // closure is covered by Stage04CandidateFixture and Stage04ArchiveV2Test.
        for (String artifact : NON_MANIFEST_ARTIFACTS) {
            assertArrayEquals(fixture.artifacts().get(artifact),
                    Files.readAllBytes(candidateDirectory.resolve(artifact)), artifact);
        }
        assertManifestClosure(candidateDirectory, fixture.artifacts());
    }

    @Test
    void sameCandidateIdentityAndBytesAreIdempotentAcrossStoreInstances() throws IOException {
        Path workspace = tempWorkspace.resolve("idempotent");
        Fixture fixture = fixture();
        FilesystemCandidateStore firstStore = new FilesystemCandidateStore(workspace, limits());
        CandidateReference first = firstStore.install(fixture.bundle());
        CandidateReference repeated = new FilesystemCandidateStore(workspace, limits())
                .install(fixture.bundle());

        assertEquals(first, repeated);
        assertEquals(1, candidateDirectories(workspace).size());
        assertArrayEquals(fixture.artifacts().get("document.md"),
                Files.readAllBytes(installedDirectory(workspace, first).resolve("document.md")));
    }

    @Test
    void sameCandidateIdentityWithChangedByteCollidesWithoutOverwritingOriginal() throws IOException {
        Path workspace = tempWorkspace.resolve("collision");
        Fixture original = fixture();
        FilesystemCandidateStore store = new FilesystemCandidateStore(workspace, limits());
        store.install(original.bundle());
        byte[] originalProofPack = Files.readAllBytes(installedDirectory(workspace, original.reference())
                .resolve("proof-pack.json"));
        Fixture changed = original.with("proof-pack.json", "{\"x\":1}".getBytes(StandardCharsets.UTF_8));

        M8Exception collision = assertThrows(M8Exception.class, () -> store.install(changed.bundle()));
        assertEquals("CANDIDATE_IDENTITY_COLLISION", collision.failureCode());
        assertEquals(1, candidateDirectories(workspace).size());
        assertArrayEquals(originalProofPack, Files.readAllBytes(installedDirectory(workspace, original.reference())
                .resolve("proof-pack.json")), "collision must not overwrite the immutable Candidate");
    }

    @Test
    void traversalAbsoluteUnlistedAndMissingArtifactsFailClosedWithoutPartialCandidate() throws IOException {
        assertStoreFailure(tempWorkspace.resolve("traversal"), "CANDIDATE_ARTIFACT_PATH_INVALID", () -> {
            Fixture fixture = fixture();
            return fixture.with("../escape.json", "{}".getBytes(StandardCharsets.UTF_8)).bundle();
        });
        assertStoreFailure(tempWorkspace.resolve("absolute"), "CANDIDATE_ARTIFACT_PATH_INVALID", () -> {
            Fixture fixture = fixture();
            return fixture.with("/escape.json", "{}".getBytes(StandardCharsets.UTF_8)).bundle();
        });
        assertStoreFailure(tempWorkspace.resolve("unlisted"), "CANDIDATE_ARTIFACT_SET_INVALID", () -> {
            Fixture fixture = fixture();
            return fixture.with("unlisted.txt", "{}".getBytes(StandardCharsets.UTF_8)).bundle();
        });
        assertStoreFailure(tempWorkspace.resolve("missing"), "CANDIDATE_ARTIFACT_SET_INVALID", () -> {
            Fixture fixture = fixture();
            return fixture.without("trace.jsonl").bundle();
        });
    }

    @Test
    void workspaceAndCandidateSymlinksAreRejectedBeforeInstallation() throws IOException {
        Path realWorkspace = tempWorkspace.resolve("real-workspace");
        Files.createDirectories(realWorkspace);
        Path workspaceLink = tempWorkspace.resolve("workspace-link");
        Files.createSymbolicLink(workspaceLink, realWorkspace);
        Fixture workspaceFixture = fixture();
        M8Exception workspaceFailure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(workspaceLink, limits()).install(workspaceFixture.bundle()));
        assertEquals("CANDIDATE_WORKSPACE_SYMLINK", workspaceFailure.failureCode());
        assertEquals(0, candidateDirectories(realWorkspace).size());

        Path workspace = tempWorkspace.resolve("candidate-link");
        Path candidateRoot = workspace.resolve("candidates");
        Files.createDirectories(candidateRoot);
        Path outside = tempWorkspace.resolve("candidate-outside");
        Files.createDirectories(outside);
        Fixture candidateFixture = fixture();
        Path candidateLink = candidateRoot.resolve(candidateFixture.reference().candidateId()
                .substring("candidate:".length()));
        Files.createSymbolicLink(candidateLink, outside);
        M8Exception candidateFailure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(workspace, limits()).install(candidateFixture.bundle()));
        assertEquals("CANDIDATE_DESTINATION_SYMLINK", candidateFailure.failureCode());
        assertTrue(Files.isSymbolicLink(candidateLink));
        assertFalse(Files.exists(outside.resolve("document.md")));
    }

    @Test
    void invalidUtf8NonCanonicalJsonAndByteBudgetFailClosedWithoutPartialCandidate() throws IOException {
        Path encodingWorkspace = tempWorkspace.resolve("encoding");
        Fixture encoding = fixture().with("proven-facts.json", new byte[]{(byte) 0xc3, (byte) 0x28});
        M8Exception encodingFailure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(encodingWorkspace, limits()).install(encoding.bundle()));
        assertEquals("CANDIDATE_UTF8_INVALID", encodingFailure.failureCode());
        assertNoCandidate(encodingWorkspace, encoding.reference());

        Path jsonWorkspace = tempWorkspace.resolve("canonical-json");
        Fixture json = fixture().with("candidate.json", "{\"z\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8));
        M8Exception jsonFailure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(jsonWorkspace, limits()).install(json.bundle()));
        assertEquals("CANDIDATE_CANONICAL_JSON_INVALID", jsonFailure.failureCode());
        assertNoCandidate(jsonWorkspace, json.reference());

        Path budgetWorkspace = tempWorkspace.resolve("budget");
        Fixture budget = fixture();
        M8Exception budgetFailure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(budgetWorkspace, new CandidateStoreLimits(16, 4096))
                        .install(budget.bundle()));
        assertEquals("CANDIDATE_SIZE_LIMIT_EXCEEDED", budgetFailure.failureCode());
        assertNoCandidate(budgetWorkspace, budget.reference());
    }

    private void assertStoreFailure(Path workspace, String expectedCode, BundleSupplier supplier)
            throws IOException {
        M8Exception failure = assertThrows(M8Exception.class,
                () -> new FilesystemCandidateStore(workspace, limits()).install(supplier.get()));
        assertEquals(expectedCode, failure.failureCode());
        assertNoCandidate(workspace, fixture().reference());
    }

    private static CandidateStoreLimits limits() {
        return new CandidateStoreLimits(1_000_000, 100_000);
    }

    private static Fixture fixture() {
        CandidateReference reference = new CandidateReference(
                "series:" + "a".repeat(64), 1,
                "candidate:" + "b".repeat(64),
                "candidate-content:" + "c".repeat(64),
                sha256("候选文档\n".getBytes(StandardCharsets.UTF_8)),
                "UNPUBLISHED_CANDIDATE");
        Map<String, byte[]> artifacts = new LinkedHashMap<>();
        byte[] document = "候选文档\n".getBytes(StandardCharsets.UTF_8);
        artifacts.put("document.md", document);
        artifacts.put("candidate.json", ("{\"candidateContentId\":\"" + reference.candidateContentId()
                + "\",\"candidateId\":\"" + reference.candidateId()
                + "\",\"documentSha256\":\"" + reference.documentSha256()
                + "\",\"status\":\"" + reference.status() + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        for (String artifact : NON_MANIFEST_ARTIFACTS) {
            artifacts.putIfAbsent(artifact, artifact.endsWith(".jsonl")
                    ? "{}\n".getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8));
        }
        return new Fixture(reference, artifacts);
    }

    private static Path installedDirectory(Path workspace, CandidateReference reference) throws IOException {
        Path expected = workspace.resolve("candidates")
                .resolve(reference.candidateId().substring("candidate:".length()));
        assertTrue(Files.isDirectory(expected), "candidate digest directory must be installed");
        return expected;
    }

    private static List<Path> candidateDirectories(Path workspace) throws IOException {
        Path candidates = workspace.resolve("candidates");
        if (!Files.isDirectory(candidates, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(candidates)) {
            return paths.toList();
        }
    }

    private static void assertNoCandidate(Path workspace, CandidateReference reference) {
        Path candidate = workspace.resolve("candidates")
                .resolve(reference.candidateId().substring("candidate:".length()));
        assertFalse(Files.exists(candidate, LinkOption.NOFOLLOW_LINKS),
                "a rejected bundle must not leave a partial candidate directory");
    }

    private static void assertManifestClosure(Path candidateDirectory, Map<String, byte[]> artifacts)
            throws IOException {
        byte[] manifestBytes = Files.readAllBytes(candidateDirectory.resolve("archive-manifest.json"));
        String manifestText = new String(manifestBytes, StandardCharsets.UTF_8);
        JsonNode manifest = JSON.readTree(manifestBytes);
        assertNotNull(manifest);
        for (String artifact : NON_MANIFEST_ARTIFACTS) {
            byte[] bytes = artifacts.get(artifact);
            JsonNode entry = findManifestEntry(manifest, artifact);
            assertNotNull(entry, () -> "archive manifest lacks " + artifact);
            assertEquals(bytes.length, number(entry, "size", "bytes"), artifact + " size");
            assertEquals(sha256(bytes), text(entry, "sha256", "sha"), artifact + " SHA");
            assertTrue(manifestText.contains(artifact), "manifest must name " + artifact);
        }
    }

    private static JsonNode findManifestEntry(JsonNode node, String artifact) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            for (String pathField : List.of("path", "relativePath", "name", "file")) {
                JsonNode path = node.get(pathField);
                if (path != null && artifact.equals(path.asText())) {
                    return node;
                }
            }
            var fields = node.elements();
            while (fields.hasNext()) {
                JsonNode found = findManifestEntry(fields.next(), artifact);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findManifestEntry(child, artifact);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static long number(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
        }
        return Long.MIN_VALUE;
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(bytes));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    @FunctionalInterface
    private interface BundleSupplier {
        CandidateBundle get();
    }

    private record Fixture(CandidateReference reference, Map<String, byte[]> artifacts) {
        CandidateBundle bundle() {
            return new CandidateBundle(reference, artifacts);
        }

        Fixture with(String name, byte[] bytes) {
            Map<String, byte[]> changed = new LinkedHashMap<>(artifacts);
            changed.put(name, bytes);
            return new Fixture(reference, changed);
        }

        Fixture without(String name) {
            Map<String, byte[]> changed = new LinkedHashMap<>(artifacts);
            changed.remove(name);
            return new Fixture(reference, changed);
        }
    }
}
