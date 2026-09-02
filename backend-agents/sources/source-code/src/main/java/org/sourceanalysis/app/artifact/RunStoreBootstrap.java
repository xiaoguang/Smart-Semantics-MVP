package org.sourceanalysis.app.artifact;

import java.nio.file.Path;

/** The only bootstrap seam that accepts a configured or test-only store root. */
public final class RunStoreBootstrap {

  private RunStoreBootstrap() {}

  /** Opens one configured production store root. */
  public static RunStoreHandle open(Path configuredStoreRoot) {
    throw new UnsupportedOperationException("filesystem store bootstrap is not implemented");
  }

  /** Opens an existing empty temporary directory for a real filesystem test. */
  public static RunStoreHandle openForTest(Path emptyTemporaryDirectory) {
    return FileSystemRunStoreHandle.openEmptyTemporaryDirectory(emptyTemporaryDirectory);
  }
}
