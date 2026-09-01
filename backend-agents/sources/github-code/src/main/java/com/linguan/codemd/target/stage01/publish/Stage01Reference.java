package com.linguan.codemd.target.stage01.publish;

import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.StagePublicationReference;

/** Fully installed Stage01 result: M3 module provenance plus its reader-visible stage publication. */
public record Stage01Reference(
        ModulePublicationReference publisherModuleReference, StagePublicationReference stagePublicationReference) {
    public Stage01Reference {
        if (publisherModuleReference == null || stagePublicationReference == null) {
            throw new Stage01PublicationException("REQUEST_SCHEMA_INVALID", "Stage01 reference is invalid");
        }
    }
}
