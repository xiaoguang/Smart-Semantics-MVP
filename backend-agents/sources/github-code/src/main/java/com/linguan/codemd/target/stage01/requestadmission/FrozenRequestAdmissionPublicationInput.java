package com.linguan.codemd.target.stage01.requestadmission;

import com.linguan.codemd.target.artifacts.ArtifactControls;
import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.StageModuleAddress;

/** Path-free inputs which bind M1's installed artifact to one run and its frozen controls. */
public record FrozenRequestAdmissionPublicationInput(
        StageModuleAddress address,
        String moduleVersion,
        ArtifactReference analysisRunRequestRef,
        ArtifactControls controls) {
    public FrozenRequestAdmissionPublicationInput {
        if (address == null
                || address.stageNumber() != 1
                || !"freeze-source".equals(address.stageKey())
                || address.moduleNumber() != 1
                || !"request-admission".equals(address.moduleKey())
                || moduleVersion == null
                || moduleVersion.isBlank()
                || analysisRunRequestRef == null
                || controls == null) {
            throw new AdmissionException("REQUEST_SCHEMA_INVALID", "M1 publication input is invalid");
        }
    }
}
