package org.sourceanalysis.app.analysis.inventory;

import java.nio.file.Path;

/** A normal directory origin, which deliberately has no fabricated Git commit. */
public record DirectorySourceOrigin(String logicalIdentity, Path canonicalRoot)
    implements SourceOrigin {

  public DirectorySourceOrigin {
    SourceOriginChecks.requireLogicalIdentity(logicalIdentity);
    SourceOriginChecks.requireCanonicalAbsoluteRoot(canonicalRoot);
  }

  @Override
  public Kind kind() {
    return Kind.DIRECTORY;
  }
}
