package com.linguan.codemd.stage04;

import java.util.List;

/** Unpersisted, closed review-finding content; the store assigns its content-addressed ID. */
public record CandidateReviewFindingDraft(
        String schemaVersion,
        String candidateId,
        String validationReceiptId,
        String severity,
        String category,
        String permittedCorrection,
        String findingCode,
        List<String> flowSliceIds,
        List<String> readerItemKeys,
        List<Integer> sectionNumbers,
        String disposition,
        boolean correctiveAddendumRequired) {

    public CandidateReviewFindingDraft {
        ReviewFindingCanonical.Normalized normalized = ReviewFindingCanonical.normalize(schemaVersion, candidateId,
                validationReceiptId, severity, category, permittedCorrection, findingCode, flowSliceIds,
                readerItemKeys, sectionNumbers, disposition, correctiveAddendumRequired);
        schemaVersion = normalized.schemaVersion();
        candidateId = normalized.candidateId();
        validationReceiptId = normalized.validationReceiptId();
        severity = normalized.severity();
        category = normalized.category();
        permittedCorrection = normalized.permittedCorrection();
        findingCode = normalized.findingCode();
        flowSliceIds = normalized.flowSliceIds();
        readerItemKeys = normalized.readerItemKeys();
        sectionNumbers = normalized.sectionNumbers();
        disposition = normalized.disposition();
    }
}
