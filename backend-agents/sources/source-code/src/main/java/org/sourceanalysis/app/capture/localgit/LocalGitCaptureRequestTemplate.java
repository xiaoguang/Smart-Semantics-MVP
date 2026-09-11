package org.sourceanalysis.app.capture.localgit;

import java.nio.file.Path;
import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Bootstrap-owned invariant inputs for one local immutable-commit capture. */
public record LocalGitCaptureRequestTemplate(
    String declaredRepositoryIdentity,
    ArtifactReference capturePolicyRef,
    ArtifactReference resourceBudgetRef) {

  public LocalGitCaptureRequestTemplate {
    if (declaredRepositoryIdentity == null || declaredRepositoryIdentity.isBlank()) {
      throw new IllegalArgumentException("declared repository identity is required");
    }
    Objects.requireNonNull(capturePolicyRef, "capture policy reference");
    Objects.requireNonNull(resourceBudgetRef, "resource budget reference");
  }

  /** Creates capture input from the only two values permitted to vary at the CLI boundary. */
  public LocalGitCaptureRequest create(Path repositoryPath, String commitId) {
    return new LocalGitCaptureRequest(
        declaredRepositoryIdentity, commitId, repositoryPath, capturePolicyRef, resourceBudgetRef);
  }
}
