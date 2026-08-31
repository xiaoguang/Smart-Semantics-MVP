package com.linguan.codemd.stage01;

import java.util.List;

/** Non-factual capability, rejection, and profile-owned expectation gaps. */
public record GapLedger(String gapLedgerId, GapProfile profile, List<CapabilityGap> capabilityGaps,
                        List<FactRejection> factRejections,
                        List<ExpectationGap> expectationGaps) {
    public GapLedger {
        capabilityGaps = List.copyOf(capabilityGaps);
        factRejections = List.copyOf(factRejections);
        expectationGaps = List.copyOf(expectationGaps);
    }
}
