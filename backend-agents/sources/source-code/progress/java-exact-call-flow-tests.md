# Progress: exact-call business-flow compiler tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-09
- Scope: Migrate the existing bounded Step05 `EntryRootedFlowCompilerTest` to the published v3 exact-call accounting; no new feature or target-METHOD negative is in scope.
- Approved inputs: Step05 §8.1.2, §8.4, and §8.6; Terra's exact-call flow cutover progress; real `ProgramGraphsPublicFixture.createWithSharedJavaCall`; current public v3 Fact/Flow APIs only; no production/design/fixture/schema/Provider/customer/network changes.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress note and applied the earlier shared-call positive test.
- Reopened the note before the approved existing-class migration and captured the stale-oracle RED before changing its assertions.
- Completed the existing-class v3 migration, exact class verification, and one-file format/check.

## Current state

- The existing Flow test class is migrated for v3 exact-call cardinality and is green. No fixture/schema/production or new feature changes were made.
- Read-only mapping is complete. `createWithSharedJavaCall` persists two HTTP entries (`approve` and `dispatch`) and three exact rows: the caller-owned `OrderService#dispatch(java.lang.String)` target matching the second entry's actual handler METHOD, plus the shared `ApprovalClient#record(java.lang.String)` call-site rows owned by both entries.
- Existing tests now derive Flow Fact ownership from freshly reopened v3 candidates/Facts, retain boundary/gap/guard/counter premises, and validate one exact-basis `EXPLICIT_CALL` per actual exact Fact instead of collapsing repeated signal kinds into one map entry. The current public expectations are 4/4 standard guarded signals, 4/3 shared-flow signals, and 5/4 counter-flow signals; the later target-METHOD negative remains out of scope.

## Changed files

- `progress/java-exact-call-flow-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompilerTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress/template setup | PASS | Created this note before Java/Maven preparation. |
| Step05 §8.1.2/§8.4/§8.6 and Terra cutover mapping inspection | PASS | Confirmed exact-call priority, same-entry tuple ownership, signal identity/basis closure, per-Flow span ownership, and the required two-entry exact positive/target-proof negative. |
| Earlier shared-call exact selector | RED (current reader gate) | 1 test, 1 failure, 0 errors, 0 skips; persisted v3 exact Facts/3 rows and closure premises passed before `UPSTREAM_ARTIFACT_REPLAY_MISMATCH` at `PersistedFlowCompilationInputReader.boundaryInvocation:438`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest test` (pre-migration class baseline) | RED (stale v3 oracles) | 6 tests, 3 failures, 0 errors, 0 skips: standard Flow Fact IDs were 1→3, guarded signal count 3→4, and counter approve signal count 4→5. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest test` (post-migration, before formatting) | PASS | 6 tests, 0 failures, 0 errors, 0 skips. |
| Pinned one-file Spotless apply | PASS | Selected exactly 1 owned Java file; 1 changed to clean. |
| Pinned one-file Spotless check | PASS | Selected exactly 1 owned Java file; 0 needed changes and the build succeeded. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest test` (post-format final verification) | PASS | 6 tests, 0 failures, 0 errors, 0 skips. |
| `git diff --check -- progress/java-exact-call-flow-tests.md src/test/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompilerTest.java` | PASS | No whitespace errors. |

## Decisions

- Migrate only existing `EntryRootedFlowCompilerTest` assertions: derive exact tuple ownership from reopened candidates/Facts, preserve boundary type/external-gap and guard/counter evidence closure, and match each repeated `EXPLICIT_CALL` by its Fact basis rather than by signal kind alone.
- The exact caller tuple is independently identified from the persisted `JAVA_EXACT_CALL` Fact whose target canonical METHOD equals the second entry's actual handler FQN/signature; no target string or graph ID is fabricated.
- Preserve the existing two-entry/two-Flow and counter fixtures; use the published v3 cardinalities (standard 4/4, shared 4/3, counter 5/4; standard Fact accounting 3 per Flow and 6 unique) without dropping exact rows or changing graph builders/production behavior.
- The target-METHOD permitted-proof removal is a later bounded negative only after a new gate; do not invent a mutation or schema contract if the current public seam cannot express it.

## Blockers

- No blocker remains. The earlier positive RED remains historical evidence; this migration is test-only, and shared persisted owner sets remain actual sets rather than forced singletons.

## Exact next action

- Release Maven to root/Terra. Do not add the speculative negative or Step06 work.

## Resume checks

- This bounded slice is complete; do not edit fixtures, production, schemas, design, or Git, and do not start Step06 or the speculative negative.
