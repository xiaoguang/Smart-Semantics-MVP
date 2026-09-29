package org.sourceanalysis.app.analysis.discovery.frontend;

/** One ordered frontend call argument; expressions are preserved and never evaluated by Java. */
public record FrontendArgumentBinding(
    int parameterIndex, String parameterName, String expression, Disposition disposition) {

  public FrontendArgumentBinding {
    if (parameterIndex < 0
        || parameterName == null
        || parameterName.isBlank()
        || disposition == null
        || (disposition == Disposition.PASSED && (expression == null || expression.isBlank()))
        || (disposition == Disposition.NOT_PASSED && expression != null)) {
      throw new IllegalArgumentException("frontend argument binding is invalid");
    }
  }

  public enum Disposition {
    PASSED,
    NOT_PASSED
  }
}
