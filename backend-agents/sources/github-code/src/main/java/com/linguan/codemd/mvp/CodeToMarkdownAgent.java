package com.linguan.codemd.mvp;

/**
 * Stable entry point for the deterministic MVP flow.
 */
public interface CodeToMarkdownAgent {
    CandidateReference generate(GenerationRequest request);

    CandidateReference generateBaseline(BaselineGenerationRequest request);

    ValidationReceipt validate(CandidateReference candidate);

    TraceView trace(TraceQuery query);
}
