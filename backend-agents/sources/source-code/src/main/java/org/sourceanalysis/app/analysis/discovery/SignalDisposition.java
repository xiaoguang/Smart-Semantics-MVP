package org.sourceanalysis.app.analysis.discovery;

/** The deterministic disposition of one discovered framework or configuration signal. */
public enum SignalDisposition {
  SUPPORTED,
  UNSUPPORTED,
  AMBIGUOUS,
  OVER_LIMIT
}
