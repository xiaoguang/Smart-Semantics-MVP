package org.sourceanalysis.app.artifact;

import java.util.Map;
import java.util.regex.Pattern;

/** A validated address for one registered module of an analysis step. */
public record AnalysisStepModuleAddress(
    AnalysisRunId runId, AnalysisStepKey analysisStepKey, int moduleNumber, String moduleKey) {

  private static final Pattern MODULE_KEY = Pattern.compile("[a-z][a-z0-9-]{0,47}");

  private static final Map<AnalysisStepKey, Map<Integer, String>> REGISTERED_MODULES =
      Map.of(
          AnalysisStepKey.VERIFIED_SOURCE_INVENTORY,
          Map.of(1, "request-admission", 2, "source-index", 3, "publish"),
          AnalysisStepKey.APPLICATION_DISCOVERY,
          Map.of(1, "application-profile", 2, "http-entry", 3, "mapper-catalog", 4, "publish"),
          AnalysisStepKey.PROGRAM_GRAPHS,
          Map.of(
              1, "code-structure",
              2, "call-graph",
              3, "control-flow",
              4, "data-flow",
              5, "evidence-graph",
              6, "publish"),
          AnalysisStepKey.PROVEN_CODE_FACTS,
          Map.of(1, "candidates", 2, "proofs", 3, "publish"),
          AnalysisStepKey.BUSINESS_FLOWS,
          Map.of(1, "flow-compiler", 2, "capsule-projector", 3, "publish"),
          AnalysisStepKey.FLOW_INTERPRETATION,
          Map.of(
              1, "registry-task-compiler",
              2, "registry-proposal-runner",
              3, "registry-freezer",
              4, "flow-task-compiler",
              5, "interpretation-runner",
              6, "publish"),
          AnalysisStepKey.REPOSITORY_KNOWLEDGE,
          Map.of(1, "admission", 2, "knowledge-merge", 3, "publish"),
          AnalysisStepKey.NINE_SECTION_DOCUMENT,
          Map.of(1, "planner", 2, "renderer", 3, "trace", 4, "archive"));

  public AnalysisStepModuleAddress {
    if (runId == null) {
      throw new IllegalArgumentException("analysis run ID is required");
    }
    if (analysisStepKey == null) {
      throw new IllegalArgumentException("analysis step key is required");
    }
    if (moduleNumber < 1) {
      throw new IllegalArgumentException("module number must be positive");
    }
    if (moduleKey == null || moduleKey.isBlank() || !MODULE_KEY.matcher(moduleKey).matches()) {
      throw new IllegalArgumentException("module key must use the canonical module-key grammar");
    }

    String registeredModuleKey = REGISTERED_MODULES.get(analysisStepKey).get(moduleNumber);
    if (!moduleKey.equals(registeredModuleKey)) {
      throw new IllegalArgumentException(
          "module number and key must match the registered analysis module");
    }
  }
}
