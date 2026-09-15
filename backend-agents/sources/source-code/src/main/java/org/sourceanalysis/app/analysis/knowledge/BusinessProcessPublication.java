package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The four reader-visible outputs of repository business-process discovery. */
public record BusinessProcessPublication(
    RepositoryBusinessProcessCatalog catalog,
    ProcessCoverage coverage,
    String businessProcessesMarkdown,
    List<SourceReference> sourceReferences,
    ModulePublicationReference checkpoint) {

  public BusinessProcessPublication {
    if (catalog == null
        || coverage == null
        || businessProcessesMarkdown == null
        || businessProcessesMarkdown.isBlank()) {
      throw new IllegalArgumentException("business process publication is incomplete");
    }
    sourceReferences = List.copyOf(sourceReferences);
  }

  BusinessProcessPublication(
      RepositoryBusinessProcessCatalog catalog,
      ProcessCoverage coverage,
      String businessProcessesMarkdown,
      List<SourceReference> sourceReferences) {
    this(catalog, coverage, businessProcessesMarkdown, sourceReferences, null);
  }
}
