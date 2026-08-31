package com.linguan.codemd.stage04;

import java.util.List;

/**
 * Durable boundary for immutable review findings.  Implementations own archive
 * validation and append-only storage; this value seam deliberately does not.
 */
public interface CandidateReviewStore {
    CandidateReviewFinding record(CandidateReviewFindingDraft draft);

    ReviewFindingSet resolveExact(String roundOneCandidateId, List<String> findingIds);
}
