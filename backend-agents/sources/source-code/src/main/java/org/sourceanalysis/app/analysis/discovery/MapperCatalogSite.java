package org.sourceanalysis.app.analysis.discovery;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One Mapper resource capability site and its explicit support or Gap disposition. */
public record MapperCatalogSite(
    ArtifactId siteId,
    SourceExcerptV1 primaryExcerpt,
    SignalDisposition disposition,
    String reasonCode,
    ArtifactId gapId) {

  public MapperCatalogSite {
    Objects.requireNonNull(siteId, "Mapper site ID");
    Objects.requireNonNull(primaryExcerpt, "Mapper site excerpt");
    Objects.requireNonNull(disposition, "Mapper site disposition");
    if (disposition == SignalDisposition.SUPPORTED && (reasonCode != null || gapId != null)) {
      throw new IllegalArgumentException("a supported Mapper site cannot carry a Gap");
    }
    if (disposition != SignalDisposition.SUPPORTED
        && (reasonCode == null || reasonCode.isBlank() || gapId == null)) {
      throw new IllegalArgumentException("a non-supported Mapper site requires a reason and Gap");
    }
  }
}
