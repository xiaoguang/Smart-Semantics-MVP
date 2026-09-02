package org.sourceanalysis.app.analysis.inventory;

import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/**
 * Composition-only reader for one already-registered immutable analysis input artifact.
 *
 * <p>It deliberately has no path, discovery, write, or publication operation. Callers must give the
 * full content-addressed reference; implementations must reject missing or altered bytes.
 */
@FunctionalInterface
interface AnalysisInputArtifactReader {

  /** Fresh-reopens and identity-verifies the complete canonical bytes of {@code reference}. */
  ImmutableBytes reopen(ArtifactReference reference);
}
