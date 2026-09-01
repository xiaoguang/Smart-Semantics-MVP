package com.linguan.codemd.target.artifacts;

/** Closed, path-free address of a stage's reader-visible publication. */
public record StagePublicationAddress(String runId, int stageNumber, String stageKey) {
    public StagePublicationAddress {
        runId = ArtifactValues.contentId(runId, "runId", "analysis-run");
        StageDefinitions.require(stageNumber, stageKey);
    }
}
