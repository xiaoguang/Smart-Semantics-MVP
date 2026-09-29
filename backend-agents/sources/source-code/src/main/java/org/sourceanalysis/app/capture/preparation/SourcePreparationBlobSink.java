package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;

/** Private staging destination for one verified source file's exact bytes. */
interface SourcePreparationBlobSink {

  /** Opens one staged blob; callers complete it only after the source identity remains stable. */
  SourcePreparationBlobWriter begin(String relativePath, long expectedSizeBytes) throws IOException;
}
