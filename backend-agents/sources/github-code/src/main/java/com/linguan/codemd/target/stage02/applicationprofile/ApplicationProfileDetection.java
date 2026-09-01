package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.artifacts.ModulePublicationReference;

/** M1 result: a typed profile plus the immutable module publication that carries the canonical draft. */
public record ApplicationProfileDetection(ApplicationProfile profile, ModulePublicationReference draftPublication) {
    public ApplicationProfileDetection {
        if (profile == null || draftPublication == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "application-profile result is invalid");
        }
    }
}
