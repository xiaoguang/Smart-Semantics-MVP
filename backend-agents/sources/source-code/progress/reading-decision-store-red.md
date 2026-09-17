# Progress: Reading decision store RED

- Status: GREEN (coordinator verification)
- Agent role: Luna/xhigh RED owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Private single-decision persistence/reuse and saved catalog input reading
- Owning plan: docs/supplements/cross-object-process-reconstruction/module-design.md §7
- Approved inputs: process-reading-decision-v1, existing model-job-reviewed-result-v2 pair, saved catalog batch input
- Current branch/worktree: Formal source-code checkout

## Completed

- Read the scoped source-code instructions, decision/persistence design, PrivateModelJobResultStore, and ReviewedModelJobReuseTest.
- Confirmed the agreed minimal seam: writeDecision(String,ObjectNode), readCompletedDecision(String,String,String,ModelRuntimeIdentityV1), and readCatalogInput(String).

## Current state

- The store now exposes the approved three-method seam for single-decision results and saved catalog
  input, while retaining the reviewed-result pair path. The bounded tests assert exact decision
  identity/fingerprint matching, strict incomplete/corrupt handling, distinct decision/pair paths,
  and catalog input reuse without a new prompt fingerprint.

## Changed files

- This progress record.
- src/test/java/org/sourceanalysis/app/runtime/modeljob/ProcessReadingDecisionStoreTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short --branch | PASS | Preserved concurrent foundation/pipeline worktree changes |
| git diff --check -- progress/reading-decision-store-red.md src/test/java/org/sourceanalysis/app/runtime/modeljob/ProcessReadingDecisionStoreTest.java | PASS | No whitespace errors; no Maven/build/test run |

## Decisions

- Add only ProcessReadingDecisionStoreTest.java under runtime/modeljob; do not modify production or existing tests.
- Use a real runId in every record and assert phase/job/run separation; do not infer catalog provenance in the store.
- Keep the fixture record at the minimum process-reading-decision-v1 fields required by the approved contract.

## Blockers

- Pipeline persistence/reopen wiring remains separate and is not covered by this store result.

## Verification update

Coordinator-owned targeted check (recorded, not run by this agent):
`JAVA_HOME=J17 mvn -q -t .mvn/toolchains.local.xml -Dtest=ProcessReadingDecisionStoreTest surefire:test`
completed with 4 tests passing and 0 failures/errors. This verifies the store seam only; it does
not claim production pipeline save/reopen wiring.

## Exact next action

Keep the store tests unchanged while the coordinator validates pipeline persistence separately.

## Resume checks

- Ensure no existing test or production file is edited.
- Treat missing methods as the intended RED, not as a reason to weaken assertions.

## Plan closeout destinations

- Durable decisions: module-design.md §7
- Remaining issues: acceptance.md
- Verification and output references: parent task handoff
