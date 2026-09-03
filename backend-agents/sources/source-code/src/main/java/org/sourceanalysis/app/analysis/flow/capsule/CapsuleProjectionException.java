package org.sourceanalysis.app.analysis.flow.capsule;

/** Stable failure for an unprovable or malformed M2 evidence projection. */
public final class CapsuleProjectionException extends IllegalArgumentException {

  CapsuleProjectionException(String code) {
    super(code);
  }
}
