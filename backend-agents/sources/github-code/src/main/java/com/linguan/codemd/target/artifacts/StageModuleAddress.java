package com.linguan.codemd.target.artifacts;

public record StageModuleAddress(
        String runId,
        int stageNumber,
        String stageKey,
        int moduleNumber,
        String moduleKey) implements ModulePublicationAddress {
    public StageModuleAddress {
        runId = ArtifactValues.token(runId, "runId");
        if (stageNumber < 1 || stageNumber > 8) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "stageNumber must be 1 through 8");
        }
        stageKey = ArtifactValues.token(stageKey, "stageKey");
        validateModule(moduleNumber, moduleKey);
    }

    static void validateModule(int moduleNumber, String moduleKey) {
        if (moduleNumber < 1 || moduleNumber > 99) {
            throw new ArtifactStoreException("MODULE_PUBLICATION_REQUEST_INVALID", "moduleNumber must be 1 through 99");
        }
        ArtifactValues.token(moduleKey, "moduleKey");
    }
}
