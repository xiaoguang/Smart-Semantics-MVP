# Progress: Step05 flow closeout regression tests

- Status: COMPLETE
- Agent role: Luna/xhigh bounded test-only migration owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Finish the bounded Step05 version-expectation migration in `BusinessFlowCoverageTest` and `FlowSignalPublicationIntegrityTest` only; preserve all mutation, admission, identity, coverage, eligibility, basis, privacy, and signal-count checks.
- Approved inputs: Published Step05 M1 v3 / M2 v6 / public Flow v3 / public Capsule v4 contract, current test implementations, and the exact ten-class selector result supplied by root; no production, design, fixture, helper, or Step06 changes.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve unrelated shared-worktree changes.

## Completed

- Read the repository root, backend, and source-code scoped instructions.
- Read both implementation plans and the relevant published Step05 v3/v6/v4 contract sections.
- Confirmed root's exact selector result: 32 tests, 2 failures, 0 errors; failures are stale version expectations only.
- Created and intent-to-added this progress file before editing Java tests.

## Current state

- `BusinessFlowCoverageTest` now expects the published `business-flows-evidence-capsule-v4` schema.
- `FlowSignalPublicationIntegrityTest` now expects the published `business-flows-flow-compilation-v3` schema.
- The coverage oracle requires exactly 4 signals per capsule: 2 `EXPLICIT_CALL`, 1 `JAVA_TYPE_ANCHOR`, and 1 `EXTERNAL_EFFECT_GAP`, with non-empty Fact/atom/Proof/evidence basis arrays and same-Flow ownership preserved.

## Changed files

- `progress/flow-closeout-regression-tests.md` (owned)
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowCoverageTest.java` (owned; pending)
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowSignalPublicationIntegrityTest.java` (owned; pending)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Exact ten-class selector supplied by root | RED before this slice | 32 tests, 2 failures, 0 errors, 0 skips; only the two stale schema-version expectations described above have failed so far. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowCoverageTest,FlowSignalPublicationIntegrityTest test` (after version edits) | RED, contract-directed next failure | 2 tests, 1 failure, 0 errors, 0 skips; `FlowSignalPublicationIntegrityTest` passed, and `BusinessFlowCoverageTest` exposed the documented 3→4 standard signal count. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowCoverageTest,FlowSignalPublicationIntegrityTest test` (final, after count migration and formatting) | PASS | 2 tests, 0 failures, 0 errors, 0 skips; numeric exit 0. |
| Exact two-file Spotless apply | PASS | 2 selected files; 0 changed, 2 already clean. |
| Exact two-file Spotless check | PASS | 2 selected files; 0 need changes. |
| Scoped `git diff --check` for this progress note and the two named tests | PASS | No whitespace errors. |

## Decisions

- Change only the two stale version literals to the already-published contract versions: Capsule v3 → v4 and M1 compilation v2 → v3.
- Preserve every existing mutation/admission/identity/coverage/eligibility/basis/privacy assertion.
- If the next failure is only the documented standard signal count, use the exact Step05 rule: approve/cancel 4/4 = 2 `EXPLICIT_CALL` + 1 `JAVA_TYPE_ANCHOR` + 1 `EXTERNAL_EFFECT_GAP`, and match the reopened actual FlowFact/Proof basis. Any other behavior ambiguity is a stop-and-report condition.

## Blockers

- None. The bounded migration is complete; no production, design, fixture, helper, or Step06 files changed.

## Exact next action

- Release Maven to root with the final exact test/format/diff results. No commit, push, or new Step06 feature is authorized in this slice.

## Resume checks

- Re-read this note before continuing. Keep the file scope to this progress note and the two named Java tests. Do not modify production, design, fixture, helper, or Step06 files; do not commit or push.
