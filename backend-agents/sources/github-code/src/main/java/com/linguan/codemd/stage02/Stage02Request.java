package com.linguan.codemd.stage02;

import com.linguan.codemd.stage01.Stage01Request;

import java.util.Objects;

/** Replayable Stage 02 input; the compiler never accepts a bare directory or graph. */
public record Stage02Request(String schemaVersion, Stage01Request stage01Request,
                             String expectedStage01ResultId,
                             FlowCompilationProfileRef flowCompilationProfileRef,
                             EvidenceProjectionProfileRef evidenceProjectionProfileRef,
                             Stage02ResourceBudget resourceBudget) {
    public Stage02Request {
        stage01Request = Objects.requireNonNull(stage01Request, "stage01Request");
        flowCompilationProfileRef = Objects.requireNonNull(flowCompilationProfileRef,
                "flowCompilationProfileRef");
        evidenceProjectionProfileRef = Objects.requireNonNull(evidenceProjectionProfileRef,
                "evidenceProjectionProfileRef");
        resourceBudget = Objects.requireNonNull(resourceBudget, "resourceBudget");
    }
}
