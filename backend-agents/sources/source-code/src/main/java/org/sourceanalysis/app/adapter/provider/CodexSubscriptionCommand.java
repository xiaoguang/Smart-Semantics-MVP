package org.sourceanalysis.app.adapter.provider;

import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One replaceable process boundary; tests use it to avoid any live Codex request. */
@FunctionalInterface
public interface CodexSubscriptionCommand {

  ImmutableBytes execute(
      CodexSubscriptionProfile profile, String prompt, ImmutableBytes outputJsonSchema);
}
