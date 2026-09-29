package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.nio.file.Path;
import org.sourceanalysis.app.artifact.AnalysisRunId;

/** Opens a private, run-isolated staged-byte boundary after service preflight succeeds. */
@FunctionalInterface
interface SourcePreparationStagingFactory {

  SourcePreparationStaging open(Path privateOutputRoot, AnalysisRunId runId) throws IOException;
}
