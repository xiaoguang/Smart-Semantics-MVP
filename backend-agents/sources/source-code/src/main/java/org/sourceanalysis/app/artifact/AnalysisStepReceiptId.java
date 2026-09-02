package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated content identity for one semantic analysis-step receipt. */
public record AnalysisStepReceiptId(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("analysis-step-receipt:[0-9a-f]{64}");

  public AnalysisStepReceiptId {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException("analysis-step receipt ID must be canonical");
    }
  }

  /** Parses a canonical analysis-step receipt identifier. */
  public static AnalysisStepReceiptId parse(String value) {
    return new AnalysisStepReceiptId(value);
  }
}
