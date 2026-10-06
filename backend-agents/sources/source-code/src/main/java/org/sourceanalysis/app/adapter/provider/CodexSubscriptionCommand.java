package org.sourceanalysis.app.adapter.provider;

import org.sourceanalysis.app.artifact.ImmutableBytes;

/** One replaceable process boundary; tests use it to avoid any live Codex request. */
@FunctionalInterface
public interface CodexSubscriptionCommand {

  /** Verifies the declared local subscription identity before any job is dispatched. */
  default void preflight(CodexSubscriptionProfile profile) {}

  ImmutableBytes execute(
      CodexSubscriptionProfile profile, String prompt, ImmutableBytes outputJsonSchema);

  /**
   * Formal ontology callers may require a bounded completed response. Existing injectable command
   * fakes and all legacy callers retain the three-argument behavior.
   */
  default ImmutableBytes execute(
      CodexSubscriptionProfile profile,
      String prompt,
      ImmutableBytes outputJsonSchema,
      int maxResponseBytes) {
    return execute(profile, prompt, outputJsonSchema);
  }
}
