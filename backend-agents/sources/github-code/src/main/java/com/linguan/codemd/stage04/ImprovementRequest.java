package com.linguan.codemd.stage04;

import java.util.List;

/** Validated public Round-2 request shape. Round-2 generation remains intentionally unavailable. */
public record ImprovementRequest(String schemaVersion, String seriesId, int readerCandidateRound,
                                 String parentCandidateId, String expectedParentCandidateId,
                                 List<String> findingIds, String correctiveAddendumId) {
    public ImprovementRequest {
        Stage04Validation.require("improvement-request-v1".equals(schemaVersion));
        Stage04Validation.identifier(seriesId, "series:");
        Stage04Validation.require(readerCandidateRound == 2);
        Stage04Validation.identifier(parentCandidateId, "candidate:");
        Stage04Validation.require(parentCandidateId.equals(expectedParentCandidateId));
        CandidateLineage lineage = new CandidateLineage(readerCandidateRound, parentCandidateId, findingIds,
                correctiveAddendumId);
        findingIds = lineage.findingIds();
        correctiveAddendumId = lineage.correctiveAddendumId();
    }
}
