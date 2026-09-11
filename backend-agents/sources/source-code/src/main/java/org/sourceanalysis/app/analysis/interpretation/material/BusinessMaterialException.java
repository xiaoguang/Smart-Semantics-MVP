package org.sourceanalysis.app.analysis.interpretation.material;

/** Stable failure from the technical-material projection boundary. */
public final class BusinessMaterialException extends RuntimeException {

  public BusinessMaterialException(String code) {
    super(code);
  }

  public BusinessMaterialException(String code, Throwable cause) {
    super(code, cause);
  }
}
