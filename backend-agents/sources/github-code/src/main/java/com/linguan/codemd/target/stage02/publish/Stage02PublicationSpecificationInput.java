package com.linguan.codemd.target.stage02.publish;

import com.linguan.codemd.target.artifacts.ModulePublicationReference;
import com.linguan.codemd.target.artifacts.StagePublicationAddress;
import com.linguan.codemd.target.stage01.publish.Stage01Reference;

/** Complete path-free M4 input closure; M4 receives only persisted upstream publications. */
public record Stage02PublicationSpecificationInput(
        StagePublicationAddress destination,
        Stage01Reference frozenSource,
        ModulePublicationReference applicationProfilePublication,
        ModulePublicationReference httpEntryPublication,
        ModulePublicationReference mapperCatalogPublication) {
    public Stage02PublicationSpecificationInput {
        if (destination == null
                || destination.stageNumber() != 2
                || !"discover-application-and-entries".equals(destination.stageKey())
                || frozenSource == null
                || applicationProfilePublication == null
                || httpEntryPublication == null
                || mapperCatalogPublication == null) {
            throw new Stage02PublicationException("STAGE02_REQUEST_INVALID", "M4 input is invalid");
        }
    }
}
