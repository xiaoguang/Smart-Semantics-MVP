# Progress: M7 safe-shard limitation Gap carrier implementation

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Implement only the Sol-frozen M7 two-lane Gap conservation law and model-safe upstream-limitation carrier required by the confirmed Luna RED.
- Approved inputs: Scoped `AGENTS.md`, both implementation plans, Step 06 design, `progress/m7-safe-limitation-gap-design.md`, and `progress/business-process-task-safe-limitation-gap-tests.md`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, implementation plans, frozen Sol ruling, Luna RED record, and prior no-model carrier implementation record.
- Confirmed the frozen direct selector RED: a safe limitation binding referenced an upstream M6 Gap and no corresponding M7 `PROCESS_UPSTREAM_LIMITATION` value existed.
- Added the safe-limitation carrier projection, canonical carrier identity, limitation-key remapping, and the separate safe-reference conservation lane.
- Re-ran the direct selector after scoped formatting; it remains green. Receipt-last M7 publication
  also remains green.

## Current state

- This bounded production repair is complete. The direct selector and receipt-last publisher
  regression are green. A separate Luna oracle correction is required before the whole legacy
  compiler class can become green: four old assertions require no M7 process Gaps for safe
  packets (and one requires no Gap for a no-model packet), contradicting both accepted carrier
  rules and the frozen `P = O ⊎ L` behavior. The exact conflict has been sent to the parent; production
  was not weakened to satisfy it.

## Changed files

- `progress/business-process-task-safe-limitation-gap-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompiler.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilation.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/ProcessInterpretationGapV1.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only design/code/test inspection | PASS | Confirmed the RED is a missing M7 safe limitation carrier, not M8 or Provider behavior. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest#conservesNoModelOwnershipAndModelSafeLimitationReferencesSeparately test` (before production) | EXPECTED RED | 1 test, 1 assertion failure, 0 errors/skips; safe binding referred to no M7 carrier. |
| Same direct selector (after production) | PASS | Surefire report: 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest test` | BLOCKED BY STALE ORACLES | 7 tests, 4 assertion failures, 0 errors/skips; all four assert legacy empty `processGaps` behavior. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskModulePublisherTest test` | PASS | 2 tests, 0 failures/errors/skips. |
| Scoped `spotless:apply`, then `spotless:check` | PASS | The three owned production sources are formatted. |
| `git diff --check` | PASS | No whitespace errors in tracked shared-worktree changes. |

## Decisions

- Preserve `NO_MODEL` wrapper ownership exactly; a safe limitation is a read-only carrier, not model ineligibility.
- Do not alter packet-visible fields, model response grammar, output counts, M6, M8, providers, schemas, or fixtures.

## Blockers

- Four existing M7 compiler test assertions must be rewritten by Luna/xhigh to distinguish
  no-model ownership from model-safe limitation references. They cannot all remain true under
  the approved two-lane contract.

## Exact next action

- Luna/xhigh corrects the four stale M7 oracle expectations. Then rerun the compiler regression,
  update the all-safe M8 fixture to consume the compiler-produced carrier, and resume M8 from its
  frozen finite-cardinality RED.

## Resume checks

- Only task-owned production source files and this progress record may be edited.
- Run the direct RED selector, then `BusinessProcessTaskCompilerTest` and `BusinessProcessTaskModulePublisherTest`; do not call a Provider.
