package org.sourceanalysis.app.analysis.code.jdt;

import java.time.Duration;

/** Host safety limits for one entry collection; limits never silently truncate output. */
record CollectionBudget(int maxDepth, int maxMethods, long maxSourceChars, Duration maxElapsed) {

  CollectionBudget {
    if (maxDepth < 0 || maxMethods < 1 || maxSourceChars < 1L) {
      throw new IllegalArgumentException("JDT collection limits must be positive");
    }
    if (maxElapsed == null || maxElapsed.isZero() || maxElapsed.isNegative()) {
      throw new IllegalArgumentException("JDT collection elapsed limit must be positive");
    }
  }

  static CollectionBudget standard() {
    return new CollectionBudget(128, 100_000, 100_000_000L, Duration.ofMinutes(30));
  }
}
