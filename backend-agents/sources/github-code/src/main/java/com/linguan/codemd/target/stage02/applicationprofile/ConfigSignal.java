package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.contracts.SourceExcerptV1;

/** One frozen configuration signal; a nullable value never implies an inferred runtime value. */
public record ConfigSignal(
        ConfigSignalKind kind,
        SourceExcerptV1 sourceExcerpt,
        String value,
        SignalDisposition disposition,
        String reasonCode) {
    public ConfigSignal {
        if (kind == null || sourceExcerpt == null || disposition == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "config signal fields are required");
        }
        if ((value != null && value.isBlank()) || (reasonCode != null && reasonCode.isBlank())) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "config signal text must be canonical");
        }
    }
}
