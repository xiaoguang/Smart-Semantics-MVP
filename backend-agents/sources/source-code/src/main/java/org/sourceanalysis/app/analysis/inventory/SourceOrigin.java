package org.sourceanalysis.app.analysis.inventory;

import java.nio.file.Path;

/** The tagged private source origin used to derive and validate a preparation request. */
public sealed interface SourceOrigin permits DirectorySourceOrigin, GitCommitSourceOrigin {

  /** The two supported origin variants. */
  enum Kind {
    DIRECTORY,
    GIT_COMMIT
  }

  Kind kind();

  String logicalIdentity();

  Path canonicalRoot();
}
