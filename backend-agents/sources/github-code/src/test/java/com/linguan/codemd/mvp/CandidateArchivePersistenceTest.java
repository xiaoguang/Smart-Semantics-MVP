package com.linguan.codemd.mvp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateArchivePersistenceTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> EXPECTED_ARTIFACTS = Set.of(
            "document.md",
            "candidate.json",
            "evidence-pack.json",
            "r1-interpretation.json",
            "r2-precision-review.json",
            "trace-index.json",
            "generation-receipt.json",
            "validation-receipt.json");
    private static final List<String> SECTION_TITLES = List.of(
            "文档说明", "业务目标", "业务对象", "业务活动", "字段与维度",
            "对象关系", "指标口径", "示例问题", "待确认事项");

    @Test
    void archiveWritesOnlyJsonSidecarsAndAStableNineSectionDocument() throws Exception {
        Path root = Files.createTempDirectory("mvp-archive-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        Path workspace = root.resolve("workspace");
        CandidateArchiveService archive = new CandidateArchiveService(workspace);

        CandidateReference first = archive.archive(request(fixture));

        assertEquals(EXPECTED_ARTIFACTS, workspaceFiles(workspace));
        for (String name : EXPECTED_ARTIFACTS) {
            if (!name.equals("document.md")) {
                JsonNode sidecar = parseJson(workspace.resolve(name));
                assertTrue(sidecar.isObject(), () -> name + " must be a JSON object");
            }
        }

        JsonNode candidate = parseJson(workspace.resolve("candidate.json"));
        assertEquals(first.candidateId(), candidate.path("candidateId").asText());
        assertEquals(first.candidateContentId(), candidate.path("candidateContentId").asText());

        JsonNode trace = parseJson(workspace.resolve("trace-index.json"));
        assertEquals(first.candidateId(), trace.path("candidateId").asText());
        assertTrue(trace.toString().contains(MvpFixtures.TRACE_ITEM_KEY),
                "trace index must retain a usable item locator");

        JsonNode evidencePack = parseJson(workspace.resolve("evidence-pack.json"));
        assertTrue(evidencePack.toString().contains("evidence:controller-approve"),
                "evidence pack must retain stable evidence IDs");
        assertTrue(parseJson(workspace.resolve("r1-interpretation.json"))
                        .path("interpretations").isArray());
        assertTrue(parseJson(workspace.resolve("r2-precision-review.json"))
                        .path("reviews").isArray());

        String markdown = Files.readString(workspace.resolve("document.md"), StandardCharsets.UTF_8);
        assertEquals(SECTION_TITLES, h2Titles(markdown));
        assertNoInternalIds(markdown);

        Map<String, String> beforeRepeat = artifactBytes(workspace);
        CandidateReference repeated = archive.archive(request(fixture));
        assertEquals(first, repeated, "same content must resolve to the same candidate");
        assertEquals(beforeRepeat, artifactBytes(workspace),
                "repeated archival must not overwrite or alter the existing candidate");
    }

    @Test
    void validateReturnsAValidReceiptForAnUntamperedArchivedCandidate() throws Exception {
        Path root = Files.createTempDirectory("mvp-validation-valid-");
        MvpFixtures.Fixture fixture = MvpFixtures.valid(root);
        CandidateArchiveService archive = new CandidateArchiveService(root.resolve("workspace"));

        CandidateReference candidate = archive.archive(request(fixture));
        ValidationReceipt receipt = archive.validate(candidate);

        assertNotNull(receipt);
        assertTrue(receipt.valid());
        assertEquals(candidate.candidateId(), receipt.candidateId());
        assertTrue(parseJson(root.resolve("workspace/validation-receipt.json"))
                .path("valid").asBoolean());
    }

    @Test
    void validateFailsClosedWhenMarkdownBytesOrSectionShapeAreTampered() throws Exception {
        Path hashRoot = Files.createTempDirectory("mvp-validation-hash-tamper-");
        MvpFixtures.Fixture hashFixture = MvpFixtures.valid(hashRoot);
        CandidateArchiveService hashArchive = new CandidateArchiveService(hashRoot.resolve("workspace"));
        CandidateReference hashCandidate = hashArchive.archive(request(hashFixture));
        Path hashDocument = hashRoot.resolve("workspace/document.md");
        Files.writeString(hashDocument,
                Files.readString(hashDocument, StandardCharsets.UTF_8) + "篡改\n",
                StandardCharsets.UTF_8);

        assertFalse(hashArchive.validate(hashCandidate).valid(),
                "changing Markdown bytes must invalidate its recorded SHA");

        Path sectionRoot = Files.createTempDirectory("mvp-validation-section-tamper-");
        MvpFixtures.Fixture sectionFixture = MvpFixtures.valid(sectionRoot);
        CandidateArchiveService sectionArchive = new CandidateArchiveService(
                sectionRoot.resolve("workspace"));
        CandidateReference sectionCandidate = sectionArchive.archive(request(sectionFixture));
        Path sectionDocument = sectionRoot.resolve("workspace/document.md");
        String original = Files.readString(sectionDocument, StandardCharsets.UTF_8);
        Files.writeString(sectionDocument,
                original.replace("## 待确认事项\n", "## 非法额外章节\n\n## 待确认事项\n"),
                StandardCharsets.UTF_8);

        assertFalse(sectionArchive.validate(sectionCandidate).valid(),
                "changing the nine-section shape must fail closed");
    }

    private static GenerationRequest request(MvpFixtures.Fixture fixture) {
        return new GenerationRequest(fixture.manifest(), fixture.snapshotRoot(),
                MvpFixtures.validProvider());
    }

    private static JsonNode parseJson(Path path) throws IOException {
        JsonNode node = JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
        assertNotNull(node, () -> path + " must contain JSON");
        return node;
    }

    private static Set<String> workspaceFiles(Path workspace) throws IOException {
        try (Stream<Path> paths = Files.walk(workspace)) {
            return paths.filter(Files::isRegularFile)
                    .map(workspace::relativize)
                    .map(Path::toString)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static Map<String, String> artifactBytes(Path workspace) throws IOException {
        Map<String, String> result = new TreeMap<>();
        for (String name : workspaceFiles(workspace)) {
            result.put(name, HexFormat.of().formatHex(Files.readAllBytes(workspace.resolve(name))));
        }
        return result;
    }

    private static List<String> h2Titles(String markdown) {
        return Arrays.stream(markdown.split("\\R"))
                .filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3))
                .toList();
    }

    private static void assertNoInternalIds(String markdown) {
        for (String internalToken : List.of(
                "candidate:", "flow:", "fact:", "evidence:", "sha256",
                "taskSpecId", "capsuleId", "prompt")) {
            assertFalse(markdown.contains(internalToken),
                    () -> "reader-facing Markdown must not contain " + internalToken);
        }
    }
}
