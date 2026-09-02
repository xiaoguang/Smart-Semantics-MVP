package org.sourceanalysis.app.analysis.inventory;

/** Reopens parser-safe text only from one published verified source inventory. */
@FunctionalInterface
public interface VerifiedSourceTextReader {

  /** Returns only exact verified text members for the given persisted source inventory. */
  VerifiedSourceTextSet reopen(VerifiedSourceInventoryReference frozenSource);
}
