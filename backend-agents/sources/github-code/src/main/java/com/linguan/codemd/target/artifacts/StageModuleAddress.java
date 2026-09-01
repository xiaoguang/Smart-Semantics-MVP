package com.linguan.codemd.target.artifacts;

public record StageModuleAddress(
        String runId,
        int stageNumber,
        String stageKey,
        int moduleNumber,
        String moduleKey) implements ModulePublicationAddress {
    public StageModuleAddress {
        runId = ArtifactValues.contentId(runId, "runId", "analysis-run");
        StageDefinitions.requireModule(stageNumber, stageKey, moduleNumber, moduleKey);
    }
}
