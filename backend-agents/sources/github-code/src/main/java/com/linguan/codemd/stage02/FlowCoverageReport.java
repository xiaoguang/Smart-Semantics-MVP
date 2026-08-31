package com.linguan.codemd.stage02;

/** Stage 02 coverage accounting across entries, outcomes, facts, atoms, and projection spans. */
public record FlowCoverageReport(String coverageReportId, CoverageMetric discoveredEntryCoverage,
                                 CoverageMetric supportedOutcomeCoverage,
                                 CoverageMetric exactCrossLayerCallCoverage,
                                 CoverageMetric exactMapperStatementCoverage,
                                 CoverageMetric flowFactCoverage,
                                 CoverageMetric flowAtomCoverage,
                                 CoverageMetric evidenceProjectionMinimality) {
}
