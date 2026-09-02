package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated Merkle-style root over one analysis step's semantic artifacts. */
public record AnalysisStepArtifactRoot(String value) {

  private static final Pattern WIRE_VALUE = Pattern.compile("analysis-step-root:[0-9a-f]{64}");

  public AnalysisStepArtifactRoot {
    if (value == null || !WIRE_VALUE.matcher(value).matches()) {
      throw new IllegalArgumentException("analysis-step artifact root must be canonical");
    }
  }

  /** Parses a canonical analysis-step artifact root. */
  public static AnalysisStepArtifactRoot parse(String value) {
    return new AnalysisStepArtifactRoot(value);
  }
}
