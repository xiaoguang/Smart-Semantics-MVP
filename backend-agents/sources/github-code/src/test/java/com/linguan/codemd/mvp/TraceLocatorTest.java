package com.linguan.codemd.mvp;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TraceLocatorTest {
    @Test
    void itemTraceReturnsTheExactFrozenEvidenceLocator() throws Exception {
        MvpFixtures.Fixture fixture = MvpFixtures.valid(
                Files.createTempDirectory("mvp-trace-")
        );
        CodeToMarkdownAgent agent = new DefaultCodeToMarkdownAgent();
        CandidateReference candidate = agent.generate(new GenerationRequest(
                fixture.manifest(), fixture.snapshotRoot(), MvpFixtures.validProvider()));

        TraceView trace = agent.trace(new TraceQuery(
                candidate.candidateId(), MvpFixtures.TRACE_ITEM_KEY));

        assertFalse(trace.evidence().isEmpty(), "trace must contain source evidence");
        TraceEvidence controller = trace.evidence().stream()
                .filter(evidence -> evidence.relativePath()
                        .equals(fixture.controllerEvidence().path()))
                .findFirst()
                .orElseThrow();
        assertEquals(fixture.controllerEvidence().startLine(), controller.startLine());
        assertEquals(fixture.controllerEvidence().endLine(), controller.endLine());
        assertEquals(fixture.controllerEvidence().startColumn(), controller.startColumn());
        assertEquals(fixture.controllerEvidence().endColumn(), controller.endColumn());
        assertEquals(fixture.controllerEvidence().excerptSha256(), controller.excerptSha256());
    }
}
