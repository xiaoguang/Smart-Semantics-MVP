package org.sourceanalysis.app.artifact;

import java.util.Set;

/** A stable, safe failure returned by the artifact policy and persistence seams. */
public final class ArtifactStoreException extends RuntimeException {

  private static final Set<String> PUBLISHED_CODES =
      Set.of(
          "ARTIFACT_POLICY_REGISTRY_INVALID",
          "ARTIFACT_POLICY_NOT_FOUND",
          "ARTIFACT_POLICY_MISMATCH",
          "MODULE_INSTALL_REQUEST_INVALID",
          "MODULE_PAYLOAD_NOT_CANONICAL",
          "MODULE_PUBLICATION_COLLISION",
          "MODULE_PUBLICATION_INVALID",
          "ANALYSIS_STEP_INSTALL_REQUEST_INVALID",
          "ANALYSIS_STEP_PUBLICATION_COLLISION",
          "ANALYSIS_STEP_PUBLICATION_INVALID",
          "RUN_MANIFEST_COLLISION",
          "RUN_MANIFEST_INVALID",
          "ATOMIC_MOVE_UNSUPPORTED");

  private final String code;

  /** Creates an exception whose message contains only its stable machine-readable code. */
  public ArtifactStoreException(String code) {
    super(requireCode(code));
    this.code = code;
  }

  /** Returns the stable machine-readable failure code. */
  public String code() {
    return code;
  }

  private static String requireCode(String code) {
    if (code == null || !PUBLISHED_CODES.contains(code)) {
      throw new IllegalArgumentException("artifact store failure code must be a published code");
    }
    return code;
  }
}
