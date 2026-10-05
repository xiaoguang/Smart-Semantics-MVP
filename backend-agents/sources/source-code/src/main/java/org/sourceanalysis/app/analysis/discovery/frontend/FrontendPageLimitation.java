package org.sourceanalysis.app.analysis.discovery.frontend;

/** A bounded, source-context limitation; it never manufactures a frontend execution result. */
public record FrontendPageLimitation(String code, String detail, String unitRef) {

  public FrontendPageLimitation {
    if (code == null
        || code.isBlank()
        || detail == null
        || detail.isBlank()
        || (unitRef != null && unitRef.isBlank())) {
      throw new IllegalArgumentException("frontend page limitation is invalid");
    }
  }
}
