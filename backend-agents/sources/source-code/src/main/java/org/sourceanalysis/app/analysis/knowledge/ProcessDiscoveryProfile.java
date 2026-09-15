package org.sourceanalysis.app.analysis.knowledge;

/** Per-task limits for repository catalog discovery and detailed process reconstruction. */
public record ProcessDiscoveryProfile(
    int maxCardsPerCatalogShard,
    int maxActivitiesPerCandidate,
    int maxRequestedSourceRefs,
    int maxRequestedSourceChars,
    int maxModelInputBytes,
    int maxModelOutputBytes,
    int maxProcessesPerCandidate,
    int maxValuesPerField,
    int maxTextCharsPerValue) {

  public ProcessDiscoveryProfile {
    if (maxCardsPerCatalogShard < 1
        || maxActivitiesPerCandidate < 1
        || maxRequestedSourceRefs < 1
        || maxRequestedSourceChars < 1
        || maxModelInputBytes < 1
        || maxModelOutputBytes < 1
        || maxProcessesPerCandidate < 1
        || maxValuesPerField < 1
        || maxTextCharsPerValue < 1) {
      throw new IllegalArgumentException("process discovery profile values must be positive");
    }
  }
}
