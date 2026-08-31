package com.linguan.codemd.stage04;

import java.util.Map;
import java.util.TreeMap;

/**
 * The finite runtime identity captured with an archived corrective addendum.
 * It deliberately carries no prompt, source content, or free-form diagnosis.
 */
public record CorrectiveAddendumDiagnosisReceipt(
        String schemaVersion,
        String diagnosisReceiptId,
        String parentCandidateId,
        String validationReceiptId,
        String reviewFindingSetSha256,
        String expectedModel,
        String observedModel,
        String expectedReasoningEffort,
        String observedReasoningEffort,
        String expectedSandbox,
        String observedSandbox,
        String expectedAccessMode,
        String observedAccessMode) {
    static final String SCHEMA = "corrective-addendum-diagnosis-receipt-v1";
    static final String MODEL = "gpt-5.6-sol";
    static final String REASONING = "ultra";
    static final String SANDBOX = "sandbox-read-only";
    static final String ACCESS_MODE = "READ_ONLY";

    public CorrectiveAddendumDiagnosisReceipt {
        Stage04Validation.require(SCHEMA.equals(schemaVersion));
        Stage04Validation.identifier(parentCandidateId, "candidate:");
        Stage04Validation.identifier(validationReceiptId, "validation-receipt:");
        Stage04Validation.require(ReviewFindingCanonical.sha256(reviewFindingSetSha256));
        Stage04Validation.require(MODEL.equals(expectedModel) && MODEL.equals(observedModel));
        Stage04Validation.require(REASONING.equals(expectedReasoningEffort)
                && REASONING.equals(observedReasoningEffort));
        Stage04Validation.require(SANDBOX.equals(expectedSandbox) && SANDBOX.equals(observedSandbox));
        Stage04Validation.require(ACCESS_MODE.equals(expectedAccessMode) && ACCESS_MODE.equals(observedAccessMode));
        Stage04Validation.require(expectedId(parentCandidateId, validationReceiptId, reviewFindingSetSha256,
                expectedModel, observedModel, expectedReasoningEffort, observedReasoningEffort,
                expectedSandbox, observedSandbox, expectedAccessMode, observedAccessMode).equals(diagnosisReceiptId));
    }

    static CorrectiveAddendumDiagnosisReceipt issued(String parentCandidateId, String validationReceiptId,
                                                      String reviewFindingSetSha256) {
        String id = expectedId(parentCandidateId, validationReceiptId, reviewFindingSetSha256,
                MODEL, MODEL, REASONING, REASONING, SANDBOX, SANDBOX, ACCESS_MODE, ACCESS_MODE);
        return new CorrectiveAddendumDiagnosisReceipt(SCHEMA, id, parentCandidateId, validationReceiptId,
                reviewFindingSetSha256, MODEL, MODEL, REASONING, REASONING, SANDBOX, SANDBOX,
                ACCESS_MODE, ACCESS_MODE);
    }

    void requireExact(String parentCandidateId, String validationReceiptId, String reviewFindingSetSha256) {
        Stage04Validation.require(this.parentCandidateId.equals(parentCandidateId)
                && this.validationReceiptId.equals(validationReceiptId)
                && this.reviewFindingSetSha256.equals(reviewFindingSetSha256));
    }

    private static String expectedId(String parentCandidateId, String validationReceiptId, String findingSetSha,
                                     String expectedModel, String observedModel, String expectedReasoning,
                                     String observedReasoning, String expectedSandbox, String observedSandbox,
                                     String expectedAccess, String observedAccess) {
        Map<String, Object> values = new TreeMap<>();
        values.put("expectedAccessMode", expectedAccess);
        values.put("expectedModel", expectedModel);
        values.put("expectedReasoningEffort", expectedReasoning);
        values.put("expectedSandbox", expectedSandbox);
        values.put("observedAccessMode", observedAccess);
        values.put("observedModel", observedModel);
        values.put("observedReasoningEffort", observedReasoning);
        values.put("observedSandbox", observedSandbox);
        values.put("parentCandidateId", parentCandidateId);
        values.put("reviewFindingSetSha256", findingSetSha);
        values.put("schemaVersion", SCHEMA);
        values.put("validationReceiptId", validationReceiptId);
        byte[] material = CandidateValidationSupport.canonicalBytes(values);
        return "diagnosis-receipt:" + CandidateValidationSupport.sha256(
                CandidateValidationSupport.concat("corrective-addendum-diagnosis-v1\n", material));
    }
}
