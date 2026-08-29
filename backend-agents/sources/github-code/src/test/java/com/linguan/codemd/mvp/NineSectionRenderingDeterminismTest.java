package com.linguan.codemd.mvp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NineSectionRenderingDeterminismTest {
    private static final List<String> SECTION_TITLES = List.of(
            "文档说明", "业务目标", "业务对象", "业务活动", "字段与维度",
            "对象关系", "指标口径", "示例问题", "待确认事项");

    @Test
    void recordedRoundsRenderExactlyNineCleanSections() throws Exception {
        MvpFixtures.Fixture fixture = MvpFixtures.valid(
                Files.createTempDirectory("mvp-nine-section-"));

        CandidateReference candidate = generate(fixture);
        String markdown = candidate.markdown();

        assertNotNull(markdown);
        assertEquals(SECTION_TITLES, h2Titles(markdown));
        assertFalse(markdown.contains("fact:"), "internal fact IDs must stay in sidecars");
        assertFalse(markdown.contains("evidence:"), "internal evidence IDs must stay in sidecars");
        assertFalse(markdown.contains("sha256"), "hashes must stay in sidecars");
        assertFalse(markdown.contains("taskSpec"), "model task identity must stay in sidecars");
        assertFalse(markdown.contains("PROMPT"), "prompt text must stay in sidecars");
    }

    @Test
    void sameVerifiedRoundsProduceByteIdenticalMarkdownAndContentIdentity() throws Exception {
        MvpFixtures.Fixture fixture = MvpFixtures.valid(
                Files.createTempDirectory("mvp-determinism-"));

        CandidateReference first = generate(fixture);
        CandidateReference second = generate(fixture);

        assertArrayEquals(first.markdown().getBytes(StandardCharsets.UTF_8),
                second.markdown().getBytes(StandardCharsets.UTF_8));
        assertEquals(first.candidateContentId(), second.candidateContentId());
    }

    private static CandidateReference generate(MvpFixtures.Fixture fixture) {
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent();
        return agent.generate(new GenerationRequest(
                fixture.manifest(), fixture.snapshotRoot(), MvpFixtures.validProvider()));
    }

    private static List<String> h2Titles(String markdown) {
        return Arrays.stream(markdown.split("\\R"))
                .filter(line -> line.startsWith("## "))
                .map(line -> line.substring(3))
                .toList();
    }
}
