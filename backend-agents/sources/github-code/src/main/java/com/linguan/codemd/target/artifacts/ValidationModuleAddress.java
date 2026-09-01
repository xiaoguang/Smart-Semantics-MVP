package com.linguan.codemd.target.artifacts;

public record ValidationModuleAddress(
        String runId,
        String validationId,
        int moduleNumber,
        String moduleKey) implements ModulePublicationAddress {
    public ValidationModuleAddress {
        runId = ArtifactValues.contentId(runId, "runId", "analysis-run");
        validationId = ArtifactValues.contentId(validationId, "validationId", "validation");
        if (moduleNumber != 1 || !"run-validator".equals(moduleKey)) {
            throw new ArtifactStoreException(
                    "MODULE_PUBLICATION_REQUEST_INVALID",
                    "validation publication must use module 01-run-validator");
        }
    }
}
