package org.sourceanalysis.app.capture.localgit;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** CLI transport input for a local, exact-commit capture. */
public record LocalGitCaptureRequest(
    String declaredRepositoryIdentity,
    String commitId,
    Path repositoryPath,
    ArtifactReference capturePolicyRef,
    ArtifactReference resourceBudgetRef) {

  private static final Pattern COMMIT_ID = Pattern.compile("[0-9a-f]{40}");

  public LocalGitCaptureRequest {
    if (declaredRepositoryIdentity == null || declaredRepositoryIdentity.isBlank()) {
      throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID");
    }
    if (commitId == null || !COMMIT_ID.matcher(commitId).matches()) {
      throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID");
    }
    if (repositoryPath == null || !repositoryPath.isAbsolute()) {
      throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID");
    }
    Objects.requireNonNull(capturePolicyRef, "capturePolicyRef");
    Objects.requireNonNull(resourceBudgetRef, "resourceBudgetRef");
  }
}
