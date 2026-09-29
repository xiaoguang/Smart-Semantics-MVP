package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;
import org.sourceanalysis.app.artifact.ArtifactId;

/**
 * The exact Step02 backend-entry relationship, or its explicit unresolved status, for one request.
 */
public record FrontendEntryLinkRecord(
    String requestId, Resolution resolution, List<ArtifactId> entryIds) {

  public FrontendEntryLinkRecord {
    if (requestId == null || requestId.isBlank() || resolution == null) {
      throw new IllegalArgumentException("frontend entry link identity is invalid");
    }
    entryIds = List.copyOf(entryIds);
    if ((resolution == Resolution.MATCHED_UNIQUE && entryIds.size() != 1)
        || (resolution != Resolution.MATCHED_UNIQUE
            && resolution != Resolution.MATCHED_MULTIPLE
            && !entryIds.isEmpty())
        || (resolution == Resolution.MATCHED_MULTIPLE && entryIds.size() < 2)) {
      throw new IllegalArgumentException(
          "frontend entry link resolution does not match its entries");
    }
  }

  public enum Resolution {
    MATCHED_UNIQUE,
    MATCHED_MULTIPLE,
    NO_MATCH,
    UNRESOLVED_REQUEST,
    BACKEND_DISCOVERY_NOT_RUN
  }
}
