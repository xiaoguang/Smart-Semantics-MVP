package com.linguan.codemd.stage03;

import com.linguan.codemd.stage02.Stage02Request;

/** Replayable input for the controlled M5--M7 generation stage. */
public record Stage03Request(
        String schemaVersion,
        Stage02Request stage02Request,
        String expectedStage02ResultId,
        RegistryBundle registryBundle,
        FlowInterpretationProfileRef interpretationProfileRef,
        RepositoryKnowledgeProfileRef knowledgeProfileRef,
        NineSectionGenerationProfileRef nineSectionProfileRef,
        ModelRuntimePolicy modelRuntimePolicy,
        Stage03ResourceBudget resourceBudget) {
}
