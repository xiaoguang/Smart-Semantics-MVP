# Progress: Program graph public-wire delivery

- Status: COMPLETE
- Agent role: Delivery coordinator
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Repair the approved M6 public data-flow projection so formal ProgramGraphs output retains frozen-Java boundary variants and downstream Fact analysis never reads drafts or infers external effects.
- Approved inputs: `origin/main` at `3a3e787`, ProgramGraphs M4/M6 design, published current-audit correction, and existing real-store public-wire test fixture.
- Current branch/worktree: `codex/source-analysis-program-graph-public-wire` at `/private/tmp/linguan-source-analysis-program-graph-public-wire`.

## Completed

- Confirmed the upstream design contract and current serializer mismatch.
- Published the docs-only M6 audit correction `3a3e787` to `origin/main` before changing code or tests.
- Established the public-seam RED with the existing real-store M6 fixture: `ProgramGraphPublicWireTest` finds a genuine `JAVA_BOUNDARY_INVOCATION` node, but its formal JSON has no `boundaryInvocation` object.
- Terra applied the minimal typed M6 projection. The direct public-wire selector is green: one test, no failures, errors, or skips; formatter and whitespace checks are green.
- Luna review found no production defect but required complete variant coverage. The expanded real-store fixture initially stopped at `ARTIFACT_POLICY_MISMATCH`: the consumed-return fixture produces a valid evidence module above this test's 100 KB artifact ceiling. The policy registry and production mapping were unchanged; raising only this test fixture's store budget to the already-used 1 MB/4 MB graph-test limits let it exercise both variants.
- The expanded selector now verifies field-by-field `JavaBoundaryInvocationV1` and `UnknownBoundaryReturnV1` projection, required-nullable closure for every data-flow node, DATA_FLOW-only placement, and no draft evidence reference leakage; it passes.
- To prove the expanded regression actually detects the publication defect, the DATA_FLOW projection was temporarily removed through a reversible source patch: the selector then failed at the required-nullable boundary assertion. The exact typed projection was restored, and the direct M6 regression set passed again.

## Current state

- This bounded corrective slice is complete and ready to commit. Variant identity mutation remains part of the existing broader M6 mutation backlog; this slice did not alter identity construction or add a compatibility reader.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphWire.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`
- `docs/analysis-steps/03-program-graphs.md`
- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Static source comparison | PASS | M4 draft has variant fields; M6 public node currently drops them. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | EXPECTED RED | 1 test; `boundaryInvocation` is absent from a genuine public data-flow boundary node. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | PASS | 1 test; 0 failures, errors, or skips after the typed M6 projection. |
| Changed-file Spotless / `git diff --check` | PASS | Formatter and whitespace validation passed. |
| Luna read-only review | PARTIAL | No P0 or production defect; P1 requires complete public-variant regression coverage before integration. |
| Expanded `ProgramGraphPublicWireTest` (first run) | EXPECTED FIXTURE ERROR | Consumed-return fixture exceeded only this test's 100 KB artifact budget before reaching M6 projection. |
| Expanded `ProgramGraphPublicWireTest` (after test budget alignment) | PASS | 1 test; 0 failures, errors, or skips, covering both public variants and placement/closure. |
| Expanded selector with DATA_FLOW projection removed | EXPECTED RED | 1 test; required-nullable boundary assertion failed, proving the regression detects the omitted public variant. |
| `ProgramGraphPublicWireTest,ProgramGraphsPublicationSpecifierTest,ProgramGraphsExecutionTest` | PASS | 3 tests; 0 failures, errors, or skips after restoring typed projection. |
| Spotless / `git diff --check` | PASS | Changed Java/test files are formatted and whitespace-clean. |

## Decisions

- Public data flow will carry frozen-Java call identity, arguments, control, locator, and rule only. It will not create SQL, mapper-execution, or other external-effect facts.

## Blockers

- None.

## Exact next action

- Commit the verified M6 public-wire corrective slice, fast-forward it to `origin/main`, then resume the Fact candidate graph-closure RED from the new main base.

## Resume checks

- Verify `origin/main` contains the corrective commit before fast-forwarding the Fact worktree; do not overwrite its uncommitted M1 files.
