# Progress: FactCandidate defensive-copy implementation

- Status: COMPLETE
- Agent role: Terra/xhigh bounded production implementation owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One existing-contract GREEN for `FactCandidate` caller-owned `orderedArgumentEdgeIds` and `orderedArguments` lists in canonical non-boundary candidates.
- Approved inputs: frozen `FactCandidateDefensiveCopyTest` RED, current Step 04 v3 contract, current `FactCandidateSet` implementation, and the closeout quality diagnosis.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve all unrelated shared-worktree changes.

## Completed

- Read repository, backend-agent, and source-code-agent instructions; both implementation plans; TDD and root-cause workflows; the frozen RED progress; the quality diagnosis; and the Step 04 v3 FactCandidate contract.
- Confirmed the diagnosis: `orderedArgumentEdgeIds` and `orderedArguments` are normalized only for `JAVA_BOUNDARY_INVOCATION`, leaving valid empty mutable caller lists aliased for `JAVA_GUARD_CONDITION` and `JAVA_EXACT_CALL`.
- Created this owner-only progress file before the production edit.
- Reproduced the frozen direct RED against the real guarded and exact-call enumerations.
- Implemented the one-file GREEN: normalize/copy both argument-list components before kind dispatch and remove the boundary-only duplicate assignments.
- Completed the approved direct selector bundle before and after the exact production-file formatting pass.

## Current state

- The frozen test reconstructs real guard and exact-call candidates with mutable empty argument lists, mutates the caller lists, and expects accessors to remain empty and unmodifiable.
- `FactCandidateSet.java` now makes direct immutable copies of the two argument-list components before kind dispatch, while retaining `orderedDistinctIds` normalization and deleting the boundary-only duplicate assignments.
- The frozen regression and every directly related allowed selector are GREEN. Only this defensive-copy slice is complete; full Step 05 remains outside this scope and is not accepted because its closure, provenance, replay, actual-source, and domain gates remain unresolved.

## Changed files

- `progress/fact-candidate-defensive-copy-implementation.md` (owned; intent-to-add staged before production edit)
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java` (authorized production file; unconditional defensive list normalization only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress creation + `git add -N -- progress/fact-candidate-defensive-copy-implementation.md` | PASS; numeric exit 0 | Intent-to-add completed before the production edit. |
| Exact `FactCandidateDefensiveCopyTest` selector (pre-fix) | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; both guard and exact-call list accessors became `[null]` after caller mutation. |
| Exact `FactCandidateDefensiveCopyTest` selector (post-fix) | PASS; numeric exit 0 | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. |
| Direct related selectors (pre-format) | PASS; numeric exit 0 | `FactCandidateIdentityTest` 1, `FactCandidateEnumeratorTest` 4, `GuardConditionFactCandidateTest` 1, and `FactCandidateDefensiveCopyTest` 1: aggregate `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. |
| Absolute production-file Spotless apply | PASS; numeric exit 0 | `-DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java`; one selected file changed clean. The Java runtime emitted its known terminal-deprecation warning from Spotless internals. |
| Absolute production-file Spotless check | PASS; numeric exit 0 | Exactly one selected file clean; zero files need changes. |
| Exact `FactCandidateDefensiveCopyTest` selector (post-format) | PASS; numeric exit 0 | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. |
| Direct related selectors (post-format) | PASS; numeric exit 0 | Same four allowed classes: aggregate `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`. |

## Decisions

- Normalize both list components unconditionally before kind dispatch with visible immutable copies; retain boundary ID/order/null validation and remove the branch-local duplicate normalization.
- Do not alter schemas, wire behavior, tests, fixtures, POM, design, or unrelated production files.

## Blockers

- None known.

## Exact next action

- This agent is released. Root may incorporate this scoped defensive-copy result into the coherent durable implementation audit; full Step 05 remains unaccepted and requires separate resolution of its outstanding gates.

## Resume checks

- The shared worktree is intentionally dirty; only this progress file and `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java` are authorized for this task.
- The sole Maven-heavy-command lease has been released after the post-format direct selector bundle; no full suite, network, Provider, customer build, commit, or push was run. Do not infer this bounded GREEN as acceptance of full Step 05.
- Only this progress file and `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java` were changed by this task.
