package org.sourceanalysis.app.analysis.flow.publish;

/** Stable M3 failure when Flow and Capsule accounting cannot be closed. */
public final class FlowPublicationException extends IllegalArgumentException {

  FlowPublicationException(String code) {
    super(code);
  }
}
