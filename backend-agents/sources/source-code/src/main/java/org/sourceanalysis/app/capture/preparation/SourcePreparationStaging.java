package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;

/** Run-isolated private staging for the accepted bytes of one source-preparation operation. */
interface SourcePreparationStaging extends AutoCloseable {

  SourcePreparationBlobSink sink();

  SourcePreparationBlobReader acceptedBytes();

  @Override
  void close() throws IOException;
}
