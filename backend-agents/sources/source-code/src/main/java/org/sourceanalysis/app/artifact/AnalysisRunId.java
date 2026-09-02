package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated analysis-run identifier with a fixed safe wire prefix. */
public record AnalysisRunId(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("analysis-run:[0-9a-f]{64}");

  public AnalysisRunId {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "analysis run ID must use the canonical analysis-run grammar");
    }
  }

  /** Parses a canonical analysis-run identifier. */
  public static AnalysisRunId parse(String wireValue) {
    return new AnalysisRunId(wireValue);
  }

  @Override
  public String toString() {
    return value;
  }
}
