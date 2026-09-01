package com.linguan.codemd.target.stage01.sourceindex;

import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.StageModuleAddress;

/** Validated destination and frozen controls for Stage01 M2. */
public record SourceIndexPublicationInput(StageModuleAddress address, String moduleVersion, ArtifactControls controls) {
    public SourceIndexPublicationInput {
        if (address == null
                || address.stageNumber() != 1
                || address.moduleNumber() != 2
                || !"freeze-source".equals(address.stageKey())
                || !"source-index".equals(address.moduleKey())
                || moduleVersion == null
                || moduleVersion.isBlank()
                || controls == null) {
            throw new SourceIndexException("REQUEST_SCHEMA_INVALID", "M2 publication input is invalid");
        }
    }
}
