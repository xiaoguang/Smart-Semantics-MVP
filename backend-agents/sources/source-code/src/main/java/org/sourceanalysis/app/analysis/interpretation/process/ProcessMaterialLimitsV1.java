package org.sourceanalysis.app.analysis.interpretation.process;

/** Frozen M6/M7 process-material limits read from the run's profile and resource budget. */
public record ProcessMaterialLimitsV1(
    int maxFlows,
    int maxRelations,
    int maxSignals,
    int maxRegistryItems,
    int maxInputBytes,
    int maxHypotheses,
    int maxClaimsPerHypothesis,
    int maxReaderSlots) {

  public ProcessMaterialLimitsV1 {
    requirePositive(maxFlows, "max flows");
    requirePositive(maxRelations, "max relations");
    requirePositive(maxSignals, "max signals");
    requirePositive(maxRegistryItems, "max registry items");
    requirePositive(maxInputBytes, "max input bytes");
    requirePositive(maxHypotheses, "max hypotheses");
    requirePositive(maxClaimsPerHypothesis, "max claims per hypothesis");
    requirePositive(maxReaderSlots, "max reader slots");
  }

  private static void requirePositive(int value, String label) {
    if (value < 1) throw new IllegalArgumentException(label + " must be positive");
  }
}
