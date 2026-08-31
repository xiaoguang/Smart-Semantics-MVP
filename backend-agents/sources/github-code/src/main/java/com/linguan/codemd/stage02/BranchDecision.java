package com.linguan.codemd.stage02;

/** An explicit binary branch decision on an OutcomePath. */
public record BranchDecision(String guardNodeId, String conditionAtomId, String polarity,
                             String normalizedCondition) {
}
