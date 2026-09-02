package org.sourceanalysis.app.artifact;

/** The closed provenance variants by which an analysis step may publish its semantic artifacts. */
public sealed interface AnalysisStepPublicationProvenance
    permits AnalysisStepPublisherModuleProvenance {}
