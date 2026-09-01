package com.linguan.codemd.target.artifacts;

public record ValidationModuleAddress(
        String runId,
        String validationId,
        int moduleNumber,
        String moduleKey) implements ModulePublicationAddress {
    public ValidationModuleAddress {
        runId = ArtifactValues.token(runId, "runId");
        validationId = ArtifactValues.token(validationId, "validationId");
        StageModuleAddress.validateModule(moduleNumber, moduleKey);
    }
}
