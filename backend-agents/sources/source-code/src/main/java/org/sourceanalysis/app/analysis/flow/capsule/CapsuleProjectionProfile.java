package org.sourceanalysis.app.analysis.flow.capsule;

import java.util.Objects;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** Content-addressed limits for deterministic evidence-capsule projection. */
public record CapsuleProjectionProfile(
    ArtifactReference profileRef,
    int maxCapsules,
    int maxSpansPerCapsule,
    int maxSpanBytes,
    int maxCapsuleUtf8Bytes) {

  public CapsuleProjectionProfile {
    Objects.requireNonNull(profileRef, "projection profile reference");
    if (maxCapsules < 1 || maxSpansPerCapsule < 1 || maxSpanBytes < 1 || maxCapsuleUtf8Bytes < 1) {
      throw new IllegalArgumentException("CAPSULE_PROJECTION_PROFILE_INVALID");
    }
  }
}
