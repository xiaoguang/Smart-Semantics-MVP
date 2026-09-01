package com.linguan.codemd.target.stage01.publish;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.StagePublicationAddress;

/** The complete M3 input closure; no draft object or filesystem location can cross this boundary. */
public record Stage01PublicationSpecificationInput(
        StagePublicationAddress destination,
        ModulePublicationReference admittedSourceRequestPublication,
        ModulePublicationReference verifiedSourceIndexPublication,
        ArtifactReference analysisRunRequestRef,
        ArtifactReference frozenRepositoryRequestRef) {
    public Stage01PublicationSpecificationInput {
        if (destination == null
                || destination.stageNumber() != 1
                || !"freeze-source".equals(destination.stageKey())
                || admittedSourceRequestPublication == null
                || verifiedSourceIndexPublication == null
                || analysisRunRequestRef == null
                || frozenRepositoryRequestRef == null) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "M3 input is invalid");
        }
    }
}
