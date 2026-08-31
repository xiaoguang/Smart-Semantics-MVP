package com.linguan.codemd.stage04;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exact, sorted and eligible review findings resolved for one Round-1 Candidate. */
public record ReviewFindingSet(
        String schemaVersion,
        String roundOneCandidateId,
        List<CandidateReviewFinding> findings,
        String reviewFindingSetSha256) {

    public ReviewFindingSet {
        Stage04Validation.require(ReviewFindingCanonical.FINDING_SET_SCHEMA.equals(schemaVersion));
        Stage04Validation.require(FilesystemCandidateStore.digestIdentifier(roundOneCandidateId, "candidate:"));
        Stage04Validation.require(findings != null && !findings.isEmpty());
        List<CandidateReviewFinding> ordered = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (CandidateReviewFinding finding : findings) {
            Stage04Validation.require(finding != null && roundOneCandidateId.equals(finding.candidateId()));
            Stage04Validation.require("APPROVED_FOR_ROUND_2".equals(finding.disposition()));
            Stage04Validation.require(ids.add(finding.findingId()));
            ordered.add(finding);
        }
        ordered.sort(Comparator.comparing(CandidateReviewFinding::findingId));
        findings = List.copyOf(ordered);
        Stage04Validation.require(ReviewFindingCanonical.sha256(reviewFindingSetSha256));
        Stage04Validation.require(reviewFindingSetSha256.equals(
                ReviewFindingCanonical.findingSetSha256(roundOneCandidateId, findings)));
    }

    static ReviewFindingSet resolved(String roundOneCandidateId, List<CandidateReviewFinding> findings) {
        Stage04Validation.require(findings != null);
        List<CandidateReviewFinding> ordered = new ArrayList<>(findings);
        ordered.sort(Comparator.comparing(CandidateReviewFinding::findingId));
        return new ReviewFindingSet(ReviewFindingCanonical.FINDING_SET_SCHEMA, roundOneCandidateId, ordered,
                ReviewFindingCanonical.findingSetSha256(roundOneCandidateId, ordered));
    }
}
