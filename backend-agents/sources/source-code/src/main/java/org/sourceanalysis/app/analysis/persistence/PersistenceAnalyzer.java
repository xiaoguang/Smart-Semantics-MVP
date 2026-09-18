package org.sourceanalysis.app.analysis.persistence;

/** Optional, source-only persistence enrichment behind the Step 04 deep-module seam. */
public interface PersistenceAnalyzer {

  /** Analyzes only the immutable inputs supplied by the technical workflow. */
  PersistenceMaterialIndex analyze(PersistenceAnalysisRequest request);
}
