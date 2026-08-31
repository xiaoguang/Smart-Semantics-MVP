package com.linguan.codemd.stage02;

import java.util.List;

/** Atomic successful Stage 02 result; individual entries may still be explicit Gaps. */
public record Stage02Result(String schemaVersion, String stage02ResultId, String stage01ResultId,
                            FlowCompilationProfileRef flowCompilationProfileRef,
                            EvidenceProjectionProfileRef evidenceProjectionProfileRef,
                            List<FlowSlice> flowSlices, FlowCoverageReport coverageReport,
                            List<EvidenceCapsule> evidenceCapsules,
                            List<EntryDisposition> entryDispositions,
                            List<FlowGap> flowGaps) {
    public Stage02Result {
        flowSlices = List.copyOf(flowSlices);
        evidenceCapsules = List.copyOf(evidenceCapsules);
        entryDispositions = List.copyOf(entryDispositions);
        flowGaps = List.copyOf(flowGaps);
    }
}
