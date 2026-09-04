package org.sourceanalysis.app.analysis.interpretation.model;

/** Typed failure while constructing a finite same-Flow model task. */
public final class FlowModelTaskException extends RuntimeException {
  public FlowModelTaskException(String code) {
    super(code);
  }

  public FlowModelTaskException(String code, Throwable cause) {
    super(code, cause);
  }
}
