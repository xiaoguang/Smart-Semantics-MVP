# Progress: JDT-first Java code engine implementation

- Status: IN_PROGRESS
- Agent role: Root coordinator
- Model: GPT-5
- Started: 2026-09-12T16:23:18Z
- Last updated: 2026-09-12T16:27:00Z
- Scope: Implement the approved JDT-first engine, then adapt the preserved JavaParser engine to the frozen neutral contract.
- Approved inputs: Java engine module designs, fixed jshERP snapshot, installed JDT LS 1.61.0/tool JDK, existing source-analysis code and tests.
- Current branch/worktree: codex/jdtls-source-navigation-feasibility at /private/tmp/linguan-source-analysis-process-design

## Completed

- Verified this is an existing linked worktree on the approved implementation branch.
- Reviewed the approved plan against the current runtime, discovery, Step03-Step05, Builder, storage and CLI seams.
- Confirmed the current runtime still hard-requires program graphs, Facts and strict Flow before business material generation.
- Collected the complete task-scoped research and design diff for the recoverable baseline; staged diff validation passed.

## Current state

- Feasibility research and detailed design exist; production engine configuration, JDT Core helper, neutral engine contracts and pipeline integration do not yet exist.
- Existing research and design changes are staged for the recoverable baseline commit. This checkpoint does not claim Java or research tests pass.

## Changed files

- progress/jdt-java-engine-implementation.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse --git-dir; git rev-parse --git-common-dir; git branch --show-current` | PASS | Existing linked worktree; branch `codex/jdtls-source-navigation-feasibility` |
| Read-only design/code audit | PASS | Reuse boundaries and mandatory Step02-Step05 wire changes identified |
| `git diff --cached --check` | PASS | No staged whitespace errors across the 47-file research/design baseline |

## Decisions

- Execute exactly two phases: JDT independently first; JavaParser current-capability adapter second.
- Existing ActivityExplainer, ProcessExplainer, BusinessReportPublisher, store bootstrap and public Agent remain reusable and are not redevelopment tasks.
- First observable checkpoint is a real JDT-only complete Service body, before full persistence integration.

## Blockers

- None.

## Exact next action

- Save the existing research and design work as the Step 1.1 recoverable baseline without making a test-passing claim.

## Resume checks

- Read this progress file and the four documents under `docs/modules/java-code-engines/`.
- Confirm branch and worktree path before any edit.
- Inspect Git status; do not overwrite historical research output or other agents' progress files.
- Resume at the first incomplete numbered implementation step.
