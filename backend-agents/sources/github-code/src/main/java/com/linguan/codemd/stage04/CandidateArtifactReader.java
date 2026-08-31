package com.linguan.codemd.stage04;

/**
 * Read-only candidate artifact seam for transport adapters.  It deliberately
 * remains separate from {@link CodeToMarkdownAgent}: generation, validation,
 * and Trace do not imply permission to expose an archived document.
 */
public interface CandidateArtifactReader {
    CandidateReference candidate(String candidateId);

    String markdown(String candidateId);

    /** Keeps package-private Stage 04 value types behind one public adapter seam. */
    static Object candidateValue(CandidateArtifactReader reader, String candidateId) {
        if (reader == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        return reader.candidate(candidateId);
    }

    /** Resolves a Candidate before delegating validation to the public Agent. */
    static Object validate(CodeToMarkdownAgent agent, CandidateArtifactReader reader, String candidateId) {
        if (agent == null || reader == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        return agent.validateCandidate(reader.candidate(candidateId));
    }

    /** Constructs the package-private trace query without widening the Agent contract. */
    static Object trace(CodeToMarkdownAgent agent, String candidateId, String readerItemKey) {
        if (agent == null) {
            throw Stage04Validation.failure(M8FailureCode.M8_REQUEST_INVALID);
        }
        return agent.trace(new TraceQuery(candidateId, readerItemKey));
    }

    /** Converts internal failures to the stable transport-safe code only. */
    static String failureCode(Throwable failure) {
        return failure instanceof M8Exception m8 ? m8.failureCode() : M8FailureCode.M8_REQUEST_INVALID.name();
    }
}
