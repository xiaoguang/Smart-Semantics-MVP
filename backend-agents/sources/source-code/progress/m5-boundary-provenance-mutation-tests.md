# Progress: M5 boundary provenance mutation tests

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam M5 mutation test proving that a generic Java boundary record cannot be accepted when its source locator or rule identity disagrees with the node provenance.
- Approved inputs: scoped AGENTS.md; both implementation plans; program-graphs design M4/M5 contracts; current M5 RED/GREEN progress; current EvidenceGraphBuilderTest.
- Current branch/worktree: shared source-code worktree at /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code.

## Completed

- Added one public-seam mutation test to `EvidenceGraphBuilderTest`. It keeps
  the original M4 node ID, evidence references, graph coverage, and verified
  source bytes, but replaces only the boundary record's `sourceLocator` with
  another valid frozen-Java provenance locator.

## Current state

- The mutation is structurally constructible and leaves all source bytes and
  provenance token hashes unchanged. The targeted test is ready to establish
  whether M5 rejects the record-to-token mismatch.

- The first targeted run established the intended RED: the current
  `EvidenceGraphBuilder` accepted the mutated draft and produced evidence,
  so the missing check is specifically boundary-record-to-provenance matching.
  Compilation, fixture setup, source reopening, and the assertion preconditions
  all completed.

- Formatting and the targeted rerun reproduced the same intended RED. The
  test is therefore a valid implementation input, not a green behavior that
  needs an artificial production change.

## Changed files

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest#rejectsBoundaryRecordWhenItsLocatorDoesNotMatchItsProvenanceToken test` | RED | 1 test, 1 assertion failure (`Expecting code to raise a throwable`); no compilation/error failures. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless formatted the shared M5 test file; no production files changed by this task. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest#rejectsBoundaryRecordWhenItsLocatorDoesNotMatchItsProvenanceToken test` | RED (reproduced) | 1 test, 1 assertion failure at the expected `assertThatThrownBy`; no errors. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

## Blockers

## Exact next action

- Terra should add the fail-closed M4/M5 boundary-record-to-provenance check
  against this RED without changing source bytes, external-effect semantics,
  or graph count. This test task itself is complete and made no production
  changes.

## Resume checks
