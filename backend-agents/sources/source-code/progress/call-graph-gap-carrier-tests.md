# Progress: M2 call-graph shared gap carrier RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02T21:56:17-0230
- Last updated: 2026-09-02T22:00:31-0230
- Scope: Add one public-seam RED test for the M2 CallGraphBuilder shared GraphGapDraft carrier. No production, design, existing-test, or other graph-step changes.
- Approved inputs: docs/analysis-steps/03-program-graphs.md M2 contract; docs/plans/source-analysis-naming-and-delivery-plan.md; docs/plans/target-standards-and-toolchain-plan.md; existing CallGraphBuilderTest fixtures.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Read the scoped AGENTS.md, the M2 program-graph contract, and both implementation plans.
- Located existing real CallGraphBuilder fixtures for overloaded entry, imported decoy, and unresolved Mapper method cases.
- Added one public-seam test using the existing `call-graph` source fixture and the unresolved Mapper method candidate set.
- The test requires one shared `GraphGapDraft` with its five fields, framed identity, exact coverage disposition, source locator, and deterministic rebuild.

## Current state

- The current CallGraphDraft still carries legacy GraphGapDisposition values and does not expose shared GraphGapDraft fields.
- The focused RED is complete and is directly caused by the missing `gapDrafts()` accessor/schema v3 contract.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphGapCarrierTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphGapCarrierTest test` | EXPECTED RED | 1 test, 1 failure, 0 errors/skips; `CALL_GRAPH_DRAFT_V3_REQUIRED: missing gapDrafts() shared carrier`. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphGapCarrierTest.java` | PASS | Formatting applied to the owned test only. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphGapCarrierTest.java progress/call-graph-gap-carrier-tests.md` | PASS | No whitespace errors. |

## Decisions

- Use the existing `call-graph` resource fixture: Stage 2 identifies the Mapper/XML namespace but the test catalog deliberately has no Java method candidate. The M2 builder therefore reports `MAPPER_JAVA_METHOD_UNRESOLVED`.
- The existing helper is private to `CallGraphBuilderTest`; the new test independently rebuilds the same public M1 publication/reopen path from that tracked source fixture rather than modifying the existing test.
- Do not infer M3 behavior or add a new source fixture.

## Blockers

- None.

## Exact next action

- Terra must add the M2 v3 carrier and source-locator implementation before this RED can turn GREEN.

## Resume checks

- Confirm only this progress file and the new test are changed.
- Confirm no production or existing test file was edited.
- Confirm the selector fails because the M2 draft is still legacy/v2 and lacks `GraphGapDraft`, not because of a fixture or compile error.
