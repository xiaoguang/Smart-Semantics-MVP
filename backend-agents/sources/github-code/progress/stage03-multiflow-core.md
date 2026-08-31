# Progress: Stage03 multiflow core

- Status: COMPLETE
- Agent role: Stage03 M6 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Make M6 hard-anchor object merging and proven typed relations work for the public two-independent-flow seam, without changing Stage01/02, tests, or design.
- Approved inputs: Scoped AGENTS, Stage03 M6/M7 design sections, current multi-flow contract/progress, public Stage03 model records, and current generator implementation.
- Current branch/worktree: Shared worktree; preserve unrelated changes.

## Completed

- Created this owned progress file before production edits.
- Read scoped AGENTS, the M6/M7 design routing, and the multi-flow RED contract/progress.
- Reproduced `mvn -Dtest=Stage03MultiFlowTest test`: 5 tests, one grouped failure with the expected three assertions.
- Traced the failure through the public Stage01/02-to-Stage03 seam. `CapsuleContext` currently uses generic
  `HTTP_ENTRY`/`INVENTORY_LOAD` as request/record anchor primaries, `assemble` uses
  `flowSliceId + display` as object identity, and relations are hard-coded empty.
- Replaced those synthetic/generic object identities: requests use the exact public entry parameter-to-record
  binding, records prefer the exact `TABLE` atom canonical value, and result identity is content-addressed from
  the complete proven `SUCCESS_RESULT` atom set. Objects merge only on equal kind and hard anchor.
- Added directed `REQUEST → RECORD` and `RECORD → RESULT` ObjectRelation values only when their local Flow
  contains every referenced source/target atom; they do not claim ownership of those atoms.
- Narrow M6 GREEN: `Stage03MultiFlowTest` now passes all five tests.
- Restored the existing anchor visibility contract without weakening M6 identity: record keys retain the
  `INVENTORY_LOAD`/`TABLE` provenance token plus the table canonical, and result keys retain the
  `SUCCESS_RESULT` provenance token plus a complete-result content digest.
- `Stage03AnchorTest,Stage03MultiFlowTest` GREEN: 6 tests, no failures or errors.

## Current state

- COMPLETE. M6 object identity is anchored to public proof-backed structure; all requested selectors and
  whitespace verification are green.

## Changed files

- `progress/stage03-multiflow-core.md`
- `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03MultiFlowTest test` | RED | 5 tests; 1 grouped failure / 3 assertions: RECORD 2 not 1, REQUEST anchor distinct count 1 not 2, relations empty. |
| `mvn -Dtest=Stage03MultiFlowTest test` | GREEN | 5 tests, 0 failures/errors. |
| `mvn -Dtest=Stage03*Test test` | RED | 51 tests; only `Stage03AnchorTest` failed because the table-only record key lacked a task-visible proven token. |
| `mvn -Dtest=Stage03AnchorTest,Stage03MultiFlowTest test` | GREEN | 6 tests, 0 failures/errors. |
| `mvn -Dtest=Stage03*Test test` | GREEN | 51 tests, 0 failures/errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures/errors. |
| `git diff --check` | GREEN | No whitespace errors reported. |

## Decisions

- No display text, route, class-name, or fixture-specific identity may be used for merge decisions. Object identity must be derived from public Stage01/02 proof-backed anchors and basis.
- `ObjectRelation` can express only directed endpoints and exact atom basis. It has no public relation-kind field;
  keep this vertical slice to proof-backed directional links and record that interface limitation rather than
  expanding the contract without design approval.
- The current public `ObjectRelation` type cannot expose the `REQUEST_TO_RECORD` / `RECORD_TO_RESULT` kind used
  to derive its content-addressed ID. This is an explicit M6 interface limitation: the model now exposes
  direction and atom basis only; a future relation-kind field needs an approved design-contract change.

## Blockers

- None for the assigned vertical slice. The missing public relation-kind field is recorded above as a deferred
  interface-contract decision, not silently represented as business prose.

## Exact next action

- None.

## Resume checks

- Confirm only Stage03 production paths and this progress file are changed by this task.
