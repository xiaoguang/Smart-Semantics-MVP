package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** One closable staged-blob write; incomplete writes deliberately remain unaccepted. */
interface SourcePreparationBlobWriter extends AutoCloseable {

  void write(ByteBuffer chunk) throws IOException;

  ArtifactReference complete() throws IOException;

  @Override
  void close() throws IOException;
}
