package com.linguan.codemd.stage04;

import com.linguan.codemd.stage01.FrozenRepositoryRequest;

/** Public M8 entry point; local paths and provider mechanics remain internal. */
public interface CodeToMarkdownAgent {
    CandidateReference generateCandidate(FrozenRepositoryRequest registeredRequest);

    CandidateReference improveCandidate(ImprovementRequest request);

    ValidationReceipt validateCandidate(CandidateReference candidate);

    TraceView trace(TraceQuery query);
}
