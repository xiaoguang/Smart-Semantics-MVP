package com.linguan.codemd.stage03;

/** Deterministic technical display used where a business term is not admitted. */
public record TechnicalDisplayResolution(String anchorKey, String anchorKind,
                                         String policyKey, String resolvedDisplay) {
}
