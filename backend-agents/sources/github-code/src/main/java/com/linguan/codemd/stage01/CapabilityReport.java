package com.linguan.codemd.stage01;

import java.util.List;
import java.util.Objects;

/** Complete capability accounting for every reachable semantic site in M2. */
public record CapabilityReport(String capabilityReportId, CapabilityProfileRef profile,
                               List<CapabilitySite> sites, CapabilityCoverage coverage) {
    public CapabilityReport {
        profile = Objects.requireNonNull(profile, "profile");
        sites = List.copyOf(sites);
        coverage = Objects.requireNonNull(coverage, "coverage");
    }
}
