package org.sourceanalysis.app.analysis.inventory;

import java.nio.file.Path;

/** A local Git origin fixed to one exact commit rather than a mutable working tree. */
public record GitCommitSourceOrigin(String logicalIdentity, Path canonicalRoot, String commitId)
    implements SourceOrigin {

  public GitCommitSourceOrigin {
    SourceOriginChecks.requireLogicalIdentity(logicalIdentity);
    SourceOriginChecks.requireCanonicalAbsoluteRoot(canonicalRoot);
    if (commitId == null || !commitId.matches("[0-9a-f]{40}")) {
      throw new IllegalArgumentException("Git source origin requires a full lowercase commit ID");
    }
  }

  @Override
  public Kind kind() {
    return Kind.GIT_COMMIT;
  }
}
