# Progress: program graph public-wire closure tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add one bounded public-seam test for M6 persisted graph payload evidence closure, graph-index closure, and draft-field exclusion. No production or design changes.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, `docs/plans/target-standards-and-toolchain-plan.md`, `docs/plans/source-analysis-naming-and-delivery-plan.md`, and the existing persisted M1–M5 public fixture.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Added the bounded public-wire test in `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java` using real persisted/reopened M1–M5 artifacts and the public M6 specifier seam.
- Corrected one test-only assertion: source evidence IDs are required to be a non-empty subset of
  the persisted evidence graph's source-excerpt IDs; the exact per-subject support set is checked
  separately.
- The direct selector is GREEN after formatting; no production or design files were changed by
  this task.

## Current state

Created the progress file before making test changes. Reading the M6 public-wire contract and existing persisted fixture is complete. The first targeted compile reached test compilation and exposed only a test-side accessor mistake (`VerifiedCanonicalPayload` requires `descriptor().fileName()`); the first test execution then exposed the test's overly strong source-ID set assertion, not a production contract. The corrected direct selector is now GREEN.

## Changed files

- `progress/program-graph-public-wire-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | RED (test compile) | Four test-only errors: used `VerifiedCanonicalPayload.fileName()` instead of `descriptor().fileName()`; no tests ran. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | RED (test assertion) | Test-only assertion required each subject's one or more source IDs to equal the complete source-node set; corrected to subset plus exact subject support set. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | PASS | 1 test, 0 failures/errors/skips; real M1–M5 persistence/reopen, M6 public evidence closure, graph-index identities/catalog closure, and draft-field exclusion. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless formatted the owned public-wire test (and an already-existing untracked graph publication test). |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

The test will use the existing real M1–M5 persisted/reopened fixture and public `ProgramGraphSetPublicationSpecifier` seam. It will assert only requirements explicitly frozen in `docs/analysis-steps/03-program-graphs.md`; no private methods, mocks, or new contract are allowed.

## Blockers

## Exact next action

No further action in this bounded TDD slice. The parent agent can run the combined graph selector when integrating the M6 production work.

## Resume checks

1. Confirm only this progress file and the owned test are modified by this agent.
2. Do not edit production or design files.
3. Run only the owned test selector; do not run a full Maven suite.
