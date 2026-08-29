package com.linguan.codemd.mvp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicBaselineGenerationTest {
    @Test
    void generatesTraceableNineSectionBaselineWithoutCallingAModel() throws Exception {
        MvpFixtures.Fixture fixture = MvpFixtures.valid(Files.createTempDirectory("baseline-"));
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent();

        CandidateReference baseline = agent.generateBaseline(new BaselineGenerationRequest(
                fixture.manifest(), fixture.snapshotRoot()));

        assertEquals(9, baseline.markdown().lines().filter(line -> line.startsWith("## ")).count());
        assertTrue(baseline.markdown().contains("确定性基线"));
        assertTrue(baseline.markdown().contains("POST /orders/approve"));
        assertTrue(baseline.markdown().contains("id != null"));
        assertTrue(baseline.markdown().contains("orders.status"));
        assertTrue(baseline.markdown().contains("APPROVED"));
        assertTrue(agent.validate(baseline).valid());
        assertEquals(3, agent.trace(new TraceQuery(baseline.candidateId(), MvpFixtures.TRACE_ITEM_KEY))
                .evidence().size());
    }
}
