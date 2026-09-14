# Progress: material checkpoint reader tests

- Status: COMPLETE
- Agent role: TDD RED test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: add direct tests for reopening the M10 business-material checkpoint from a complete publication reference and exporting repository-run-state-v2 to v3; do not modify production code.
- Approved inputs: scoped source-code checkout, existing frozen fixtures/canonical stores, model-job-execution.md §7
- Current branch/worktree: codex/material-model-batch-reuse / shared source-code worktree

## Completed

- Created this progress file before test/code work.
- Read repository, backend-agent, and source-code scoped instructions.
- Read model-job-execution.md §7 and TDD guidance.

## Current state

The direct typed-reader test is GREEN. Production now has the typed checkpoint reader; v3 export is covered separately by `MaterialCheckpointStateV3Test`.

## Changed files

- progress/material-checkpoint-reader-tests.md
- src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialCheckpointReaderTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=BusinessMaterialCheckpointReaderTest test` | RED (exit 1) | Tests run: 2, Failures: 2, Errors: 0; both failed at `Class.forName(...)` before production existed. |
| `mvn -q -t .mvn/toolchains.xml -o -Dtest=BusinessMaterialCheckpointReaderTest test` | GREEN | Tests run: 2, failures/errors/skips: 0. |

## Decisions

- Use small deterministic fixtures and canonical stores; do not copy the 326-material fixture.
- Assert observable material/coverage reopening from the complete M10 reference and reject absent/invalid M10 references; export assertions remain to be added after the reader RED is handed off.
- Keep v2 state untouched in export tests.

## Blockers

- The invalid-reference assertion is intentionally downstream of the missing reader in this RED run; once the reader exists it must expose `BUSINESS_MATERIAL_CHECKPOINT_INVALID` for non-M10/missing M10 references.

## Exact next action

No further action; the production reader and the separate v3 export path are implemented.

## Resume checks

- Run `git status --short`.
- Confirm only this progress file and the intended test file changed.
