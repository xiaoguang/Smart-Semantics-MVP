# Progress: M3 control-flow Gap carrier GREEN

- Owner: Terra/xhigh production worker.
- Scope: The direct M3 `ControlFlowGraphGapCarrierTest` GREEN only. Implement the
  approved v3 `ControlFlowGraphDraft.gapDrafts` shared carrier through its strict
  wire, M3 publication/reopen validation, coverage/module closure, and graph
  identity. Do not modify M1/M2/M4/M5/M6, public final schemas, fixtures,
  providers, or authoritative design documents.
- Authority read: `AGENTS.md`, both implementation plans,
  `docs/analysis-steps/03-program-graphs.md` (M3, 8.0.1, 8.0.2, 8.4--8.6), and
  `progress/control-flow-gap-carrier-tests.md`.
- Expected RED: `ControlFlowGraphGapCarrierTest` does not compile because the
  pre-GREEN `ControlFlowGraphDraft` exposes no `gapDrafts()` method.
- Required GREEN selector: `mvn -t .mvn/toolchains.xml -o
  -Dtest=ControlFlowGraphGapCarrierTest test`.
- Formatting/verification: run `spotless:apply`, repeat the exact selector, then
  run `git diff --check`.

## Current state

- Begun source and contract inspection. The shared worktree already contains
  unrelated dirty and untracked changes; this slice must preserve them.
- RED established with the required selector on 2026-09-02: test compilation
  failed only because `ControlFlowGraphDraft` has no `gapDrafts()` accessor
  (three references in `ControlFlowGraphGapCarrierTest`).
- Implemented the M3 v3 carrier end to end: `ControlFlowGraphDraft` owns
  sorted `gapDrafts`; the strict wire carries it; publisher and reader enforce
  v3 module `gapRefs` closure; and graph identity binds the complete ordered
  carrier material.
- `GraphGapDraft` is now the shared, validated generic carrier. Its local
  occurrence helper produces the specified versioned framed identity and
  requires nonempty ordered entry/candidate identifiers plus a locator.
- M3 profile stops create the shared carrier and reuse its identifier in the
  matching coverage and terminal dispositions. The test owner corrected the
  temporary-root fixture and removed the unsupported cross-step M4 setter
  expectation; neither change altered production M3 semantics.
- The first GREEN selector passed. After `spotless:apply`, the mandated
  repeated selector also passed on 2026-09-02: one test run, zero failures and
  zero errors. `git diff --check` also passed with no output.

## Next action

Complete: the bounded M3 GREEN implementation and required verification are
finished; do not broaden this change to other program-graph profiles.
