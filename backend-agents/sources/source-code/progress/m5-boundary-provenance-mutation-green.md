# Progress: M5 boundary provenance mutation green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Fail-closed M5 validation that binds generic Java-boundary records and boundary argument edges to their exact M4 provenance drafts.
- Approved inputs: scoped `AGENTS.md`; both implementation plans; program-graphs M4/M5 contract; `progress/m5-boundary-provenance-mutation-tests.md`; `EvidenceGraphBuilderTest`.
- Current branch/worktree: shared `codex/source-analysis-program-graphs` worktree at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`.

## Completed

- Read the prescribed contract, existing RED, M5 builder, and M4 boundary records.
- Confirmed the RED was caused by `EvidenceGraphBuilder` validating generic provenance existence but not checking that a boundary record's locator and rule were one of that record's own provenance references.
- Added fail-closed M5 input validation for `JAVA_BOUNDARY_INVOCATION`,
  `UNKNOWN_BOUNDARY_RETURN`, and `ARGUMENT_TO_BOUNDARY` records. The matching provenance must be
  carried by the exact node/edge, use the expected rule, and have the asserted Java locator;
  argument edges must also originate from one of the target boundary's declared arguments.

## Current state

- The M5 boundary-provenance closure is green. The implementation only validates existing M4
  records before evidence-node construction; it does not parse source again or add XML, SQL, or
  external-effect behavior.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphBuilder.java`
- `progress/m5-boundary-provenance-mutation-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Luna targeted mutation selector | RED (from upstream progress) | M5 accepted a mutated boundary locator; expected `GRAPH_REFERENCE_BROKEN` was not raised. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest#rejectsBoundaryRecordWhenItsLocatorDoesNotMatchItsProvenanceToken test` | PASS | 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | No source file changes needed after formatting. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 17 tests, 0 failures/errors/skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Validate draft-internal boundary semantics before M5 creates evidence nodes, so a valid source span cannot be substituted for the invocation/return span it claims to prove.

## Blockers

- None.

## Exact next action

- Hand this completed GREEN slice to the coordinator for the next M5/M6 review or RED test.

## Resume checks

- Reopen this file, check the M5 mutation test result, then inspect `EvidenceGraphBuilder` before changing production code.
