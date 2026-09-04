package org.sourceanalysis.app.analysis.interpretation.registry;

/** Stable failure when the complete R0 denominator cannot be frozen as one registry. */
public final class RegistryFreezeException extends RuntimeException {

  public RegistryFreezeException(String code) {
    super(code);
  }

  public RegistryFreezeException(String code, Throwable cause) {
    super(code, cause);
  }
}
