package com.linguan.codemd.stage03;

import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02Result;

import java.util.List;

/** Frozen, no-Provider input for deterministic Stage 03 semantic replay. */
public record Stage03ReplayRequest(
        String schemaVersion,
        Stage02Request stage02Request,
        Stage02Result replayedStage02Result,
        String expectedStage02ResultId,
        RegistryBundle registryBundle,
        FlowInterpretationProfileRef interpretationProfileRef,
        RepositoryKnowledgeProfileRef knowledgeProfileRef,
        NineSectionGenerationProfileRef nineSectionProfileRef,
        ModelRuntimePolicy modelRuntimePolicy,
        Stage03ResourceBudget resourceBudget,
        List<FlowImprovementOverlay> improvementOverlays,
        List<CanonicalFlowRound> canonicalRounds) {
    public Stage03ReplayRequest {
        improvementOverlays = improvementOverlays == null ? null : List.copyOf(improvementOverlays);
        canonicalRounds = canonicalRounds == null ? null : List.copyOf(canonicalRounds);
    }

    /** Compatibility constructor for archives and callers with no Round-2 overlay. */
    public Stage03ReplayRequest(String schemaVersion, Stage02Request stage02Request, Stage02Result replayedStage02Result,
                                String expectedStage02ResultId, RegistryBundle registryBundle,
                                FlowInterpretationProfileRef interpretationProfileRef,
                                RepositoryKnowledgeProfileRef knowledgeProfileRef,
                                NineSectionGenerationProfileRef nineSectionProfileRef,
                                ModelRuntimePolicy modelRuntimePolicy, Stage03ResourceBudget resourceBudget,
                                List<CanonicalFlowRound> canonicalRounds) {
        this(schemaVersion, stage02Request, replayedStage02Result, expectedStage02ResultId, registryBundle,
                interpretationProfileRef, knowledgeProfileRef, nineSectionProfileRef, modelRuntimePolicy,
                resourceBudget, List.of(), canonicalRounds);
    }
}
