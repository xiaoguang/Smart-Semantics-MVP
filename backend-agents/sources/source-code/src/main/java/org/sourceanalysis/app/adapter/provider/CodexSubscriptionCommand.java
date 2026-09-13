package org.sourceanalysis.app.adapter.provider;

import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One replaceable process boundary; tests use it to avoid any live Codex request. */
@FunctionalInterface
public interface CodexSubscriptionCommand {

  /** Verifies the declared local subscription identity before any job is dispatched. */
  default void preflight(CodexSubscriptionProfile profile) {}

  ImmutableBytes execute(
      CodexSubscriptionProfile profile, String prompt, ImmutableBytes outputJsonSchema);
}
