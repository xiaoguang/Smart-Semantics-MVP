package org.sourceanalysis.app.analysis.inventory;

import org.sourceanalysis.app.artifact.ArtifactId;

/** The specific saved source-version entry reused by a derived preparation result. */
public record SourceEntryInheritance(ArtifactId baseSourceVersion, ArtifactId fileId) {

  public SourceEntryInheritance {
    if (baseSourceVersion == null || !baseSourceVersion.value().startsWith("snapshot:")) {
      throw new IllegalArgumentException("inherited source version must be a snapshot identity");
    }
    if (fileId != null && !fileId.value().startsWith("source-file:")) {
      throw new IllegalArgumentException("inherited file must be a source-file identity");
    }
  }
}
