package org.sourceanalysis.app.analysis.discovery;

import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;

/** Private composition boundary that reopens parser-safe text from a verified source inventory. */
@FunctionalInterface
interface VerifiedSourceContentHandle {

  /** Returns only exact verified text members for the given persisted source inventory. */
  VerifiedSourceTextSet reopen(VerifiedSourceInventoryReference frozenSource);
}
