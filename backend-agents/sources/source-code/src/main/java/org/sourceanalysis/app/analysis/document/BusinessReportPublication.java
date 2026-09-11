package org.sourceanalysis.app.analysis.document;

import java.util.List;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/**
 * Review-approved business JSON, deterministic Markdown, and the source map retained by the
 * program.
 */
public record BusinessReportPublication(
    BusinessReport businessReport,
    String documentMarkdown,
    List<SourceReference> sourceReferences,
    BusinessReportValidation validation,
    ModulePublicationReference checkpoint) {

  public BusinessReportPublication {
    if (businessReport == null
        || documentMarkdown == null
        || documentMarkdown.isBlank()
        || validation == null) {
      throw new IllegalArgumentException("business report publication is incomplete");
    }
    sourceReferences = List.copyOf(sourceReferences);
  }

  /** Returns an unpersisted preview publication for narrow in-memory callers and unit tests. */
  public BusinessReportPublication(
      BusinessReport businessReport,
      String documentMarkdown,
      List<SourceReference> sourceReferences,
      BusinessReportValidation validation) {
    this(businessReport, documentMarkdown, sourceReferences, validation, null);
  }
}
