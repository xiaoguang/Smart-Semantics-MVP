package com.linguan.codemd.stage03;

import java.util.List;

/** The complete typed AST from which the fixed nine headings are rendered. */
public record NineSectionPlan(String schemaVersion, String nineSectionPlanId,
                              String repositoryBusinessModelId, List<ReaderSection> sections,
                              List<AtomDisposition> atomDispositions,
                              List<MeaningDisposition> meaningDispositions,
                              List<GapDisposition> gapDispositions, ReaderCoverage coverage) {
    public NineSectionPlan {
        sections = List.copyOf(sections);
        atomDispositions = List.copyOf(atomDispositions);
        meaningDispositions = List.copyOf(meaningDispositions);
        gapDispositions = List.copyOf(gapDispositions);
    }
}
