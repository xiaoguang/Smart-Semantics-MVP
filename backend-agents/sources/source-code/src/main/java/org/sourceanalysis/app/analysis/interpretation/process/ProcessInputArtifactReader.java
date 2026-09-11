package org.sourceanalysis.app.analysis.interpretation.process;

import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Reopens one already-verified, content-addressed analysis input without exposing a filesystem
 * path.
 */
@FunctionalInterface
public interface ProcessInputArtifactReader {

  /** Returns the exact canonical UTF-8 bytes registered for {@code reference}. */
  ImmutableBytes reopen(ArtifactReference reference);
}
