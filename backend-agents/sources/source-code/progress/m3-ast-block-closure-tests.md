# Progress: M3 AST block provenance and predecessor-closure RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded public-seam test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add a focused RED test for the M3 prerequisite that each activated BASIC_BLOCK maps to one continuous AST statement range and has complete traversal predecessor closure.
- Approved inputs: Root and scoped AGENTS.md; docs/analysis-steps/03-program-graphs.md M3/M4 contract; existing ControlFlowGraphBuilder public fixture/store seam.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Read repository and scoped instructions, the M3/M4 design contract, and the existing control-flow public fixture.
- Confirmed current builder assigns method-wide provenance to BASIC_BLOCK nodes.
- Added one focused two-statement Service fixture and one public-draft assertion for two exact AST statement-range blocks plus traversal membership.
- Corrected the assertion to require one block per individual statement, not a union range.

## Current state

- Focused RED test is present and targeted Maven verification completed with the expected single assertion failure.

## Changed files

- progress/m3-ast-block-closure-tests.md
- src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | RED_EXPECTED | 11 tests; 10 passed, 1 assertion failure; Service has 1 method-wide BASIC_BLOCK instead of 2 statement blocks |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Use a two-statement verified Java method so the current single method-wide BASIC_BLOCK provenance cannot satisfy the two exact-one-statement-range blocks prerequisite.
- Assert only public draft nodes and source-backed provenance exposed through the draft; do not inspect private builder state or alter production behavior.

## Blockers

- Production behavior is not changed; the failure is the intended prerequisite RED.

## Exact next action

- Terra should implement statement-boundary BASIC_BLOCK partitioning and preserve traversal closure, then rerun this selector.

## Resume checks

- Re-check that this task owns only this progress file and the focused additions in ControlFlowGraphBuilderTest; parent M3 WIP changes are pre-existing.
- Do not modify this completed progress file unless recording a direct rerun result.
