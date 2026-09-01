package com.linguan.codemd.target.stage02.applicationprofile;

/** Complete accounting for one Stage02 capability-site inventory. */
public record CapabilityDenominator(
        int candidateSites, int supported, int unsupported, int ambiguous, int overLimit) {
    public CapabilityDenominator {
        if (candidateSites < 0
                || supported < 0
                || unsupported < 0
                || ambiguous < 0
                || overLimit < 0
                || candidateSites != supported + unsupported + ambiguous + overLimit) {
            throw new ApplicationProfileException("ENTRY_DISCOVERY_INVARIANT_BROKEN", "capability denominator is not closed");
        }
    }
}
