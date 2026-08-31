package com.linguan.codemd.stage04;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, finite diagnosis material admissible for a fatal Round-2 finding. */
public record CorrectiveAddendum(String schemaVersion, String correctiveAddendumId, String parentCandidateId,
                                 String validationReceiptId, String reviewFindingSetSha256,
                                 CorrectiveAddendumDiagnosisReceipt diagnosisReceipt,
                                 List<CorrectiveAddendumDirective> directives) {
    /**
     * The v1 intake shape remains readable only so an append-only store can
     * archive it into the complete receipt-bound v2 form. It is never admitted
     * by {@link #requireExact(String, ReviewFindingSet)}.
     */
    public CorrectiveAddendum(String schemaVersion, String correctiveAddendumId, String parentCandidateId,
                              String validationReceiptId, List<CorrectiveAddendumDirective> directives) {
        this(schemaVersion, correctiveAddendumId, parentCandidateId, validationReceiptId, null, null, directives);
    }

    public CorrectiveAddendum {
        Stage04Validation.require("candidate-corrective-addendum-v1".equals(schemaVersion)
                || "candidate-corrective-addendum-v2".equals(schemaVersion));
        Stage04Validation.identifier(parentCandidateId, "candidate:");
        Stage04Validation.identifier(validationReceiptId, "validation-receipt:");
        Stage04Validation.require(directives != null && !directives.isEmpty());
        Set<String> findingIds = new HashSet<>();
        List<CorrectiveAddendumDirective> normalized = new ArrayList<>();
        for (CorrectiveAddendumDirective directive : directives) {
            Stage04Validation.require(directive != null && findingIds.add(directive.findingId()));
            normalized.add(directive);
        }
        normalized.sort(Comparator.comparing(CorrectiveAddendumDirective::findingId));
        directives = List.copyOf(normalized);
        boolean legacy = reviewFindingSetSha256 == null && diagnosisReceipt == null;
        if (legacy) {
            Stage04Validation.require("candidate-corrective-addendum-v1".equals(schemaVersion));
            String material = schemaVersion + "\n" + parentCandidateId + "\n" + validationReceiptId + "\n"
                    + directives;
            Stage04Validation.require(("addendum:" + Stage04Validation.sha256(material)).equals(correctiveAddendumId));
        } else {
            Stage04Validation.require("candidate-corrective-addendum-v2".equals(schemaVersion)
                    && ReviewFindingCanonical.sha256(reviewFindingSetSha256) && diagnosisReceipt != null);
            diagnosisReceipt.requireExact(parentCandidateId, validationReceiptId, reviewFindingSetSha256);
            Stage04Validation.require(fullId(parentCandidateId, validationReceiptId, reviewFindingSetSha256,
                    diagnosisReceipt, directives).equals(correctiveAddendumId));
        }
    }

    void requireExact(String parentCandidateId, ReviewFindingSet findings) {
        Stage04Validation.require(!legacy() && this.parentCandidateId.equals(parentCandidateId)
                && reviewFindingSetSha256.equals(findings.reviewFindingSetSha256()));
        diagnosisReceipt.requireExact(parentCandidateId, validationReceiptId, reviewFindingSetSha256);
        requireDirectiveClosure(findings);
    }

    static CorrectiveAddendum archive(CorrectiveAddendum intake, ReviewFindingSet findings) {
        Stage04Validation.require(intake != null && intake.legacy());
        intake.requireDirectiveClosure(findings);
        Stage04Validation.require(intake.parentCandidateId.equals(findings.roundOneCandidateId()));
        CorrectiveAddendumDiagnosisReceipt receipt = CorrectiveAddendumDiagnosisReceipt.issued(
                intake.parentCandidateId, intake.validationReceiptId, findings.reviewFindingSetSha256());
        List<CorrectiveAddendumDirective> directives = intake.directives();
        String id = fullId(intake.parentCandidateId, intake.validationReceiptId, findings.reviewFindingSetSha256(),
                receipt, directives);
        return new CorrectiveAddendum("candidate-corrective-addendum-v2", id, intake.parentCandidateId,
                intake.validationReceiptId, findings.reviewFindingSetSha256(), receipt, directives);
    }

    boolean legacy() {
        return reviewFindingSetSha256 == null && diagnosisReceipt == null;
    }

    private void requireDirectiveClosure(ReviewFindingSet findings) {
        Stage04Validation.require(findings != null && parentCandidateId.equals(findings.roundOneCandidateId()));
        Set<String> expected = new HashSet<>();
        for (CandidateReviewFinding finding : findings.findings()) {
            if (finding.correctiveAddendumRequired()) {
                Stage04Validation.require(validationReceiptId.equals(finding.validationReceiptId()));
                expected.add(finding.findingId());
            }
        }
        Set<String> actual = directives.stream().map(CorrectiveAddendumDirective::findingId)
                .collect(java.util.stream.Collectors.toSet());
        Stage04Validation.require(expected.equals(actual));
        for (CorrectiveAddendumDirective directive : directives) {
            CandidateReviewFinding finding = findings.findings().stream()
                    .filter(value -> value.findingId().equals(directive.findingId())).findFirst()
                    .orElseThrow(() -> Stage04Validation.failure(M8FailureCode.IMPROVEMENT_PARENT_INVALID));
            Stage04Validation.require(directive.findingCode().equals(finding.findingCode())
                    && directive.permittedCorrection().equals(finding.permittedCorrection()));
        }
    }

    private static String fullId(String parentCandidateId, String validationReceiptId, String findingSetSha,
                                 CorrectiveAddendumDiagnosisReceipt receipt,
                                 List<CorrectiveAddendumDirective> directives) {
        byte[] material = CandidateValidationSupport.canonicalBytes(Map.of(
                "diagnosisReceipt", receipt,
                "directives", directives,
                "parentCandidateId", parentCandidateId,
                "reviewFindingSetSha256", findingSetSha,
                "schemaVersion", "candidate-corrective-addendum-v2",
                "validationReceiptId", validationReceiptId));
        return "addendum:" + CandidateValidationSupport.sha256(
                CandidateValidationSupport.concat("candidate-corrective-addendum-v2\n", material));
    }
}
