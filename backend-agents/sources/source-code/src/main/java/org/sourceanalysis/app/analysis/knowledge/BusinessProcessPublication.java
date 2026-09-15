package org.sourceanalysis.app.analysis.knowledge;

import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** The five reader-visible outputs of repository business-process discovery. */
public record BusinessProcessPublication(
    RepositoryBusinessProcessCatalog catalog,
    ProcessCoverage coverage,
    String businessProcessesMarkdown,
    List<SourceReference> sourceReferences,
    String sourcesMarkdown,
    ModulePublicationReference checkpoint) {

  public BusinessProcessPublication {
    if (catalog == null
        || coverage == null
        || businessProcessesMarkdown == null
        || businessProcessesMarkdown.isBlank()
        || sourcesMarkdown == null
        || sourcesMarkdown.isBlank()) {
      throw new IllegalArgumentException("business process publication is incomplete");
    }
    sourceReferences = List.copyOf(sourceReferences);
  }

  BusinessProcessPublication(
      RepositoryBusinessProcessCatalog catalog,
      ProcessCoverage coverage,
      String businessProcessesMarkdown,
      List<SourceReference> sourceReferences,
      String sourcesMarkdown) {
    this(catalog, coverage, businessProcessesMarkdown, sourceReferences, sourcesMarkdown, null);
  }
}
