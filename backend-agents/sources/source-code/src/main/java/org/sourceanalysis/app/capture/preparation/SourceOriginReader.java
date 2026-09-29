package org.sourceanalysis.app.capture.preparation;

import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;

/**
 * Internal origin adapter that records preparation facts while streaming accepted bytes to staging.
 */
interface SourceOriginReader {

  /** Reads one immutable request without publishing a receipt or starting downstream analysis. */
  SourcePreparationResult read(
      SourcePreparationRequest request, SourcePreparationBlobSink blobSink);

  /**
   * Reads only the literal refresh targets and returns only the facts that replace those targets.
   */
  SourcePreparationTargetFragment readTargets(
      SourcePreparationRequest request, SourcePreparationBlobSink blobSink);
}
