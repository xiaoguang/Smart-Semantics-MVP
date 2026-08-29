package com.linguan.codemd.mvp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory adapter around the deterministic core. Persistence intentionally
 * stays outside this first-day seam.
 */
public final class DefaultCodeToMarkdownAgent implements CodeToMarkdownAgent {
    private final Map<String, MvpGenerationCore.GeneratedCandidate> candidates =
            new ConcurrentHashMap<>();

    @Override
    public CandidateReference generate(GenerationRequest request) {
        MvpGenerationCore.GeneratedCandidate generated = MvpGenerationCore.generate(request);
        candidates.put(generated.reference().candidateId(), generated);
        return generated.reference();
    }

    @Override
    public CandidateReference generateBaseline(BaselineGenerationRequest request) {
        MvpGenerationCore.GeneratedCandidate generated = MvpGenerationCore.generateBaseline(request);
        candidates.put(generated.reference().candidateId(), generated);
        return generated.reference();
    }

    @Override
    public ValidationReceipt validate(CandidateReference candidate) {
        return MvpGenerationCore.validate(candidate);
    }

    @Override
    public TraceView trace(TraceQuery query) {
        MvpGenerationCore.GeneratedCandidate candidate = candidates.get(query.candidateId());
        if (candidate == null) {
            throw MvpGenerationCore.failure("UNKNOWN_CANDIDATE");
        }
        return candidate.trace(query.itemKey());
    }
}
