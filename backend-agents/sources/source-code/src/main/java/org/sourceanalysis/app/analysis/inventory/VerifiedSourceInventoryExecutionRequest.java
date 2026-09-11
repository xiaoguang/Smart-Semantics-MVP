package org.sourceanalysis.app.analysis.inventory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;

/** Closed inputs for one production execution of verified-source-inventory M1 through M3. */
public record VerifiedSourceInventoryExecutionRequest(
    AnalysisRunId runId,
    ArtifactReference analysisRunRequestRef,
    ImmutableBytes analysisRunRequestBytes,
    ArtifactReference frozenRepositoryRequestRef,
    ImmutableBytes frozenRepositoryRequestBytes,
    ArtifactReference sourceRegistrationRef,
    ArtifactReference verificationPolicyRef,
    ArtifactReference capabilityProfileRef,
    CaptureReceiptView captureReceipt,
    ProfileView profile) {

  public VerifiedSourceInventoryExecutionRequest {
    Objects.requireNonNull(runId, "run ID");
    require(analysisRunRequestRef, analysisRunRequestBytes, "analysis run request");
    require(frozenRepositoryRequestRef, frozenRepositoryRequestBytes, "frozen repository request");
    Objects.requireNonNull(sourceRegistrationRef, "source registration");
    Objects.requireNonNull(verificationPolicyRef, "verification policy");
    Objects.requireNonNull(capabilityProfileRef, "capability profile");
    Objects.requireNonNull(captureReceipt, "capture receipt");
    Objects.requireNonNull(profile, "profile");
    if (!sourceRegistrationRef.artifactId().equals(captureReceipt.sourceRegistrationId())
        || !frozenRepositoryRequestRef.equals(captureReceipt.frozenRepositoryRequestRef())) {
      throw new IllegalArgumentException("SOURCE_INVENTORY_EXECUTION_INPUT_INVALID");
    }
  }

  private static void require(
      ArtifactReference reference, ImmutableBytes bytes, String description) {
    Objects.requireNonNull(reference, description + " reference");
    Objects.requireNonNull(bytes, description + " bytes");
    if (!reference.sha256().value().equals(sha256(bytes.copyToByteArray()))) {
      throw new IllegalArgumentException("SOURCE_INVENTORY_EXECUTION_INPUT_INVALID");
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
