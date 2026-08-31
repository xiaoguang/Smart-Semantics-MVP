package com.linguan.codemd.stage01;

/** Bounded matched-site count; it never claims an enterprise policy is absent. */
public record AbsenceEvidence(String searchRuleId, int matchedNodeCount) {
}
