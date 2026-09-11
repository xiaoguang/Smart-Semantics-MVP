# Progress: M7 process Gap oracle reconciliation

- Status: COMPLETE
- Agent role: Luna/xhigh test-oracle maintainer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Reconcile only stale BusinessProcessTaskCompilerTest expectations after the accepted M7 no-model Gap carrier and model-safe limitation carrier contracts.
- Approved inputs: Scoped AGENTS.md, `progress/m7-no-model-gap-carrier-design.md`, `progress/m7-safe-limitation-gap-design.md`, current M7 compiler tests and implementation progress.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the accepted two-lane conservation contract: `P = O ⊎ L`, where `O` is NO_MODEL ownership and `L` is MODEL_SAFE limitation reference.
- Identified four stale expectations in `BusinessProcessTaskCompilerTest`: two MODEL_SAFE cases asserting empty `processGaps`, one NO_MODEL case asserting empty `processGaps`, and the mixed case asserting all process Gaps are NO_MODEL-owned.

## Current state

- Replaced the two MODEL_SAFE `processGaps.isEmpty()` expectations, the NO_MODEL empty-process-Gap expectation, and the mixed-shard NO_MODEL-only equality with one exact lane-accounting helper.
- The helper asserts sorted unique process-Gap IDs, `P = O ⊎ L`, and `O ∩ L = ∅`, where `O` is `NO_MODEL.modelIneligibilityGapIds` and `L` is `MODEL_SAFE` `LIMITATION` binding references.
- Preserved all existing carrier field, upstream Gap, shard ownership, packet, and relation assertions.

## Changed files

- `progress/business-process-task-process-gap-oracle-reconciliation.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only source/design inspection | PASS | Four stale assertions match the accepted M7 conservation correction. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest test` | PASS | 7 tests, 0 failures, 0 errors, 0 skipped. |
| Scoped Spotless apply/check | PASS | BusinessProcessTaskCompilerTest formatted and clean. |
| `git diff --check` | PASS | No whitespace errors in the owned test/progress scope. |

## Decisions

- Replace only obsolete emptiness/equality assumptions with exact lane accounting: process Gap IDs are the sorted unique union of NO_MODEL-owned IDs and MODEL_SAFE LIMITATION binding IDs; the two sets are disjoint.
- Keep the NO_MODEL test's budget-carrier assertions intact; its expected set remains the exact NO_MODEL-owned budget Gap.
- Do not weaken the tests to merely assert non-empty Gaps, and do not change production, design, Schema, fixtures, or Provider behavior.

## Blockers

- None known.

## Exact next action

- No further action in this bounded oracle task. Terra/M8 may consume the corrected M7 expectations.

## Resume checks

- Preserve all unrelated shared-worktree changes.
- If the selector exposes a production or fixture failure rather than a stale oracle, stop and report it; do not broaden the patch.
