package com.linguan.codemd.target.stage02.applicationprofile;

import com.linguan.codemd.target.contracts.SourceExcerptV1;

/** One frozen POM-derived parser capability signal; it does not assert runtime framework behavior. */
public record FrameworkSignal(
        FrameworkSignalKind kind, SourceExcerptV1 sourceExcerpt, SignalDisposition disposition, String reasonCode) {
    public FrameworkSignal {
        if (kind == null || sourceExcerpt == null || disposition == null) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "framework signal fields are required");
        }
        if (reasonCode != null && reasonCode.isBlank()) {
            throw new ApplicationProfileException("STAGE02_REQUEST_INVALID", "framework signal reason must be canonical");
        }
    }
}
