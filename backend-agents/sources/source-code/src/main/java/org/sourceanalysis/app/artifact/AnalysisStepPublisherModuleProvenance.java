package org.sourceanalysis.app.artifact;

/** Provenance for the first seven steps: their installed publisher specification module. */
public record AnalysisStepPublisherModuleProvenance(
    ModulePublicationReference publisherSpecificationModuleReference)
    implements AnalysisStepPublicationProvenance {

  public AnalysisStepPublisherModuleProvenance {
    if (publisherSpecificationModuleReference == null) {
      throw new IllegalArgumentException("publisher specification module reference is required");
    }
  }
}
