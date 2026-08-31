package com.linguan.codemd.stage03;

import java.util.List;

/** Atomic M5--M7 result. Partial documents are never returned. */
public record Stage03Result(String schemaVersion, String stage03ResultId, String stage02ResultId,
                            String registryBundleId, List<FlowInterpretationResult> flowInterpretations,
                            RepositoryBusinessModel repositoryBusinessModel,
                            NineSectionPlan nineSectionPlan,
                            RenderedNineSectionDocument renderedDocument,
                            Stage03Validation validation,
                            List<CanonicalFlowRound> canonicalRounds) {
    public Stage03Result {
        flowInterpretations = List.copyOf(flowInterpretations);
        canonicalRounds = List.copyOf(canonicalRounds);
    }

    public Stage03Result(String schemaVersion, String stage03ResultId, String stage02ResultId,
                         String registryBundleId, List<FlowInterpretationResult> flowInterpretations,
                         RepositoryBusinessModel repositoryBusinessModel, NineSectionPlan nineSectionPlan,
                         RenderedNineSectionDocument renderedDocument, Stage03Validation validation) {
        this(schemaVersion, stage03ResultId, stage02ResultId, registryBundleId, flowInterpretations,
                repositoryBusinessModel, nineSectionPlan, renderedDocument, validation, List.of());
    }
}
