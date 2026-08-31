package com.linguan.codemd.stage02;

import com.linguan.codemd.stage01.ProofLocator;

import java.util.List;

/** Exact immutable source excerpt selected by the projection profile. */
public record ModelEvidenceSpan(String modelEvidenceSpanId, ProofLocator locator,
                                String sourceFileSha256, String excerpt, String excerptSha256,
                                List<String> supportedAtomIds,
                                List<String> supportedOutcomePathIds) {
    public ModelEvidenceSpan {
        supportedAtomIds = List.copyOf(supportedAtomIds);
        supportedOutcomePathIds = List.copyOf(supportedOutcomePathIds);
    }
}
