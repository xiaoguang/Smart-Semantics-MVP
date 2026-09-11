package org.sourceanalysis.app.runtime;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/**
 * The exact canonical request bytes and typed value freshly reopened for internal runtime
 * composition.
 *
 * <p>This is deliberately path-free: a later analysis step receives the request already saved by
 * {@link org.sourceanalysis.app.RepositoryAnalysisAgent#start(AnalysisRunRequest)}, rather than
 * reserializing a look-alike request or reopening a filesystem location itself.
 */
public record PersistedAnalysisRunRequest(
    AnalysisRunReference analysisRun, AnalysisRunRequest request, ImmutableBytes canonicalJson) {

  public PersistedAnalysisRunRequest {
    if (analysisRun == null || request == null || canonicalJson == null) {
      throw new IllegalArgumentException("persisted analysis run request fields are required");
    }
    if (!new Sha256Digest(sha256(canonicalJson.copyToByteArray()))
        .equals(analysisRun.analysisRunRequestReference().sha256())) {
      throw new IllegalArgumentException(
          "persisted analysis run request bytes do not match identity");
    }
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
