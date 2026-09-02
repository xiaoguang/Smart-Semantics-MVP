package org.sourceanalysis.app.artifact;

import java.util.regex.Pattern;

/** A validated address for one future exterior-validation module. */
public record ValidationModuleAddress(
    AnalysisRunId runId, String validationId, int moduleNumber, String moduleKey)
    implements ModulePublicationAddress {

  private static final Pattern WIRE_KEY = Pattern.compile("[a-z][a-z0-9-]{0,47}");

  public ValidationModuleAddress {
    if (runId == null
        || validationId == null
        || validationId.isBlank()
        || moduleNumber < 1
        || moduleKey == null
        || !WIRE_KEY.matcher(moduleKey).matches()) {
      throw new IllegalArgumentException("validation module address has invalid required values");
    }
  }
}
