package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;

/** Opens only accepted staged or frozen source bytes; it never resolves a customer source path. */
@FunctionalInterface
public interface SourcePreparationBlobReader {

  /** Returns a new caller-closed stream for one accepted regular-file entry. */
  InputStream open(SourceEntry entry) throws IOException;
}
