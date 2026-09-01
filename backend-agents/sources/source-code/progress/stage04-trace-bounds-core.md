# Progress: Stage04 trace references and directory bounds

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Close the three exact typed-Trace references and Candidate-directory pre-collect bound asserted by `Stage04TraceReferenceTest` and `Stage04ReopenAndDirectoryBoundsTest`. Preserve the current ledger/addendum and source-reopen gates.
- Approved inputs: scoped `AGENTS.md`; Stage04 design §§11–14; the two current RED selector contracts and their test progress.
- Current branch/worktree: Shared dirty worktree. Preserve all existing production, test, design, and other-agent progress changes.

## Completed

- Read scoped repository constraints, TDD guidance, Stage04 Trace/validation design, current test-contract progress, and the direct RED contracts.
- Created this owned progress record before production edits.
- Reproduced the direct RED: 6 tests, 4 failures, 0 errors. The three Trace assertions are missing exact registry/task/provenance hops; the Candidate archive directory overflow currently becomes a later manifest failure instead of the required early resource failure.
- Closed the admitted-term vertical slice: the resolver now selects the term only from the exact frozen business-term registry and returns both that registry ID and the selected entry priority. `Stage04TraceReferenceTest` is now 1 green / 2 remaining RED, so this reference is not inferred from display text.
- Closed the technical-fallback vertical slice: the resolver binds the frozen policy's full resolution order and every archived task spec that contains exactly one matching anchor, rejecting duplicate task anchors or mismatched proven bindings. `Stage04TraceReferenceTest` is now 2 green / 1 remaining RED.
- Implemented the Gap provenance slice. The FlowGap fixture's install limit is now correctly aligned to 300KB, but its `trace(CandidateFixture, ...)` helper still constructs `CandidateTraceResolver` with the ordinary 200KB limit. Exact validation evidence for the legitimate FlowGap candidate is: `CANDIDATE_ARTIFACT_SIZE=FAIL/CANDIDATE_SIZE_LIMIT_EXCEEDED`, then derived archive/sidecar/document/source/replay failures, including `SOURCE_REOPEN=FAIL/SNAPSHOT_REOPEN_MISMATCH`. This is a second fixture-only limit propagation gap, not source registration or replay preimage drift; production must keep rejecting an archive reopened under an insufficient hard cap.
- Closed the Candidate-directory pre-collect bound: `CandidateArchive.open` now bounds the directory stream at 256 entries before materializing paths and emits `CANDIDATE_SIZE_LIMIT_EXCEEDED` for the 257th entry. `Stage04ReopenAndDirectoryBoundsTest` is 3/3 GREEN; its existing source-registry and durable-ledger bounds remain green.
- Removed the remaining recursive Gap-presence fallback: Gap references now resolve only through the explicit Stage01 ledger, Flow Interpretation, or Stage02 FlowGap records. This preserves fail-closed behavior without a generic archive-text container lookup.
- Restored compatibility with valid Stage01 Gap reader items that retain their exact Stage01 question key but have no selected Stage03 question-registry entry. Their Trace closes over the exact ledger/reason/absence/scope evidence; registry/template hops are emitted only when the frozen registry has the one exact matching question. Registered question paths remain strict and are covered by `Stage04TraceReferenceTest`.
- Completed the fixture-only limit and missing-ledger setup corrections without changing production admission: the final direct regression set is 23/23 GREEN, including the strict no-ledger lifecycle check.

## Current state

- Production and directly related regressions are complete. No production limit or lifecycle gate was weakened for fixture alignment.

## Changed files

- `progress/stage04-trace-bounds-core.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateArchive.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04TraceReferenceTest,Stage04ReopenAndDirectoryBoundsTest test` | Expected RED | 6 tests, 4 failures, 0 errors: three missing typed Trace references and Candidate directory overflow reaches manifest validation instead of `CANDIDATE_SIZE_LIMIT_EXCEEDED`. |
| `mvn -Dtest=Stage04TraceReferenceTest test` | Partial GREEN | 3 tests, 2 failures, 0 errors. Admitted-term registry/priority test is green; fallback task and Gap provenance remain red. |
| `mvn -Dtest=Stage04TraceReferenceTest test` | Partial GREEN | 3 tests, 1 failure, 0 errors. Fallback task-spec/resolution-order test is now green; Gap provenance remains red. |
| `mvn -Dtest=Stage04TraceReferenceTest test` | BLOCKED (fixture resource mismatch) | 3 tests, 0 failures, 1 error. The FlowGap archive build fails before Trace with `CANDIDATE_SIZE_LIMIT_EXCEEDED` at its test-local 200KB sidecar limit. |
| `mvn -Dtest=Stage04ReopenAndDirectoryBoundsTest test` | GREEN | 3 tests, 0 failures, 0 errors. The Candidate 257-entry overflow is a bounded pre-collect `CANDIDATE_SIZE_LIMIT_EXCEEDED`; source registry and ledger bounds remain green. |
| `mvn -Dtest=Stage04TraceReferenceTest test` | BLOCKED (second fixture limit propagation) | 3 tests, 0 failures, 1 error. Term and fallback references are green. FlowGap install succeeds at 300KB, but Trace reopens with the helper's 200KB `LIMITS`, so validation first reports `CANDIDATE_ARTIFACT_SIZE_LIMIT_EXCEEDED` and then derived `SNAPSHOT_REOPEN_MISMATCH`; no source/replay preimage mismatch is present. |
| `mvn -Dtest=Stage04TraceReferenceTest,Stage04ReopenAndDirectoryBoundsTest,Stage04TypedTraceTest,Stage04LedgerAddendumHardeningTest test` | Partial GREEN | TraceReference 3/3, directory bounds 3/3, and TypedTrace 2/2 GREEN. LedgerAddendum 4/5 GREEN: its missing-ledger test fixture installs a complete durable ledger, then expects validation to fail as if it did not exist. |
| `mvn -Dtest=Stage04TraceReferenceTest,Stage04ReopenAndDirectoryBoundsTest,Stage04TypedTraceTest,Stage04FinalAuditTraceTest,Stage04ResidualP1Test,Stage04LedgerAddendumHardeningTest,Stage04Round2AddendumTest test` | GREEN | 23 tests, 0 failures, 0 errors. Target selectors 6/6; TypedTrace 2/2; FinalAuditTrace 2/2; ResidualP1 4/4; LedgerAddendum 5/5; Round2Addendum 4/4. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Trace references must be derived from frozen archive controls/registry/task data, never fixture labels or reader prose.
- Directory admission must fail before collecting an unbounded path list.

## Exact next action

- Hand off the completed bounded Stage04 Trace/bounds slice.

## Resume checks

- Preserve this bounded Stage04-only scope and rerun the two direct selectors before claiming completion.
