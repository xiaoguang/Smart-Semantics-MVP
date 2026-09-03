package org.sourceanalysis.app.analysis.graph;

import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Opaque reference to the sealed M5 evidence-graph module publication. */
public record EvidenceGraphDraftReference(ModulePublicationReference publication) {

  public EvidenceGraphDraftReference {
    if (publication == null)
      throw new IllegalArgumentException("evidence graph publication is required");
  }
}
