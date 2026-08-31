package com.linguan.codemd.stage04;

/** One diagnosis rule constrained entirely to a referenced, approved finding. */
public record CorrectiveAddendumDirective(String findingId, String findingCode, String permittedCorrection) {
    public CorrectiveAddendumDirective {
        Stage04Validation.identifier(findingId, "finding:");
        Stage04Validation.require(findingCode != null && findingCode.matches("[A-Z][A-Z0-9_]{0,127}"));
        Stage04Validation.require("NARROW_OR_DROP".equals(permittedCorrection)
                || "SELECT_FROZEN_TERM".equals(permittedCorrection)
                || "REORDER_OR_REPHRASE".equals(permittedCorrection));
    }
}
