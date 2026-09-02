package org.sourceanalysis.app.analysis.discovery;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/** One discovered method-level HTTP capability site, including its explicit disposition. */
public record HttpEntrySite(
    ArtifactId siteId,
    SourceExcerptV1 primaryExcerpt,
    List<ArtifactId> affectedEntryIds,
    SignalDisposition disposition,
    String reasonCode,
    ArtifactId gapId) {

  public HttpEntrySite {
    Objects.requireNonNull(siteId, "site ID");
    Objects.requireNonNull(primaryExcerpt, "primary excerpt");
    Objects.requireNonNull(disposition, "site disposition");
    affectedEntryIds = List.copyOf(affectedEntryIds);
    if (disposition == SignalDisposition.SUPPORTED
        && (affectedEntryIds.size() != 1 || reasonCode != null || gapId != null)) {
      throw new IllegalArgumentException("a supported HTTP site must own exactly one entry");
    }
    if (disposition != SignalDisposition.SUPPORTED
        && (affectedEntryIds.size() > 1
            || reasonCode == null
            || reasonCode.isBlank()
            || gapId == null)) {
      throw new IllegalArgumentException("a non-supported HTTP site requires an explicit reason");
    }
  }
}
