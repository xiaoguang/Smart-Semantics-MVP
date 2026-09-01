package com.linguan.codemd.target.stage01.capture;

import com.linguan.codemd.target.artifacts.ArtifactReference;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Private-maintenance transport for one exact local Git commit. The repository path never leaves
 * this adapter boundary.
 */
public record LocalGitCaptureRequest(
        String declaredRepositoryIdentity,
        String commitId,
        ArtifactReference capturePolicyRef,
        ArtifactReference resourceBudgetRef,
        Path repositoryPath) {
    private static final Pattern SHA1 = Pattern.compile("[0-9a-f]{40}");

    public LocalGitCaptureRequest {
        requireText(declaredRepositoryIdentity, "declaredRepositoryIdentity");
        if (commitId == null || !SHA1.matcher(commitId).matches()) {
            throw new LocalGitCaptureException(
                    "LOCAL_GIT_REQUEST_INVALID", "commitId must be exactly 40 lowercase hexadecimal characters");
        }
        Objects.requireNonNull(capturePolicyRef, "capturePolicyRef");
        Objects.requireNonNull(resourceBudgetRef, "resourceBudgetRef");
        if (repositoryPath == null || !repositoryPath.isAbsolute()) {
            throw new LocalGitCaptureException(
                    "LOCAL_GIT_REQUEST_INVALID", "repositoryPath must be an absolute local transport path");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new LocalGitCaptureException("LOCAL_GIT_REQUEST_INVALID", field + " must be canonical text");
        }
    }
}
