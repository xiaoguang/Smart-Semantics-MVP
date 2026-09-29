package org.sourceanalysis.app.capture.preparation;

import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;

/**
 * One closed Step 2 capture handed to publication without exposing its source root for rereading.
 */
public record CapturedSourcePreparation(
    SourcePreparationRequest request,
    SourcePreparationResult result,
    SourcePreparationBlobReader acceptedBytes,
    SourcePreparationToolIdentity toolIdentity) {

  public CapturedSourcePreparation {
    Objects.requireNonNull(request, "source-preparation request");
    Objects.requireNonNull(result, "source-preparation result");
    Objects.requireNonNull(acceptedBytes, "accepted source bytes");
    Objects.requireNonNull(toolIdentity, "source-preparation tool identity");
  }
}
