package com.linguan.codemd.target.stage02.publish;

import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.StagePublicationReference;

/** Fully installed Stage02 result: M4 provenance plus the immutable five-file stage publication. */
public record Stage02Reference(
        ModulePublicationReference publisherModuleReference, StagePublicationReference stagePublicationReference) {
    public Stage02Reference {
        if (publisherModuleReference == null || stagePublicationReference == null) {
            throw new Stage02PublicationException("STAGE02_REQUEST_INVALID", "Stage02 reference is invalid");
        }
    }
}
