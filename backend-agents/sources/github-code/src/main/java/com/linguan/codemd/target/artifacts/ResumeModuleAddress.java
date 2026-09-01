package com.linguan.codemd.target.artifacts;

public record ResumeModuleAddress(
        String runId,
        String resumeDecisionId,
        int moduleNumber,
        String moduleKey) implements ModulePublicationAddress {
    public ResumeModuleAddress {
        runId = ArtifactValues.token(runId, "runId");
        resumeDecisionId = ArtifactValues.token(resumeDecisionId, "resumeDecisionId");
        StageModuleAddress.validateModule(moduleNumber, moduleKey);
    }
}
