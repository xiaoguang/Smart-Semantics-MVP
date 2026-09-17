# Progress: Selection and focused reading RED tests

- Status: COMPLETE — RED tests handed to GREEN owner
- Agent role: bounded test author; tests and own handoff only
- Model: inherited existing execution seat (controller-approved fallback)
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Tasks 1–2 scripted-provider behavioral RED tests
- Owning plan: docs/plans/system-assessment-focused-reading-three-stage-implementation.md
- Approved inputs: frozen in-test fixtures and approved detailed design
- Current branch/worktree: formal source-code checkout; preserve all existing changes

## Completed

- Read current scoped instructions, task brief, design/plan and TDD guidance.
- Inspected real discovery and existing pipeline test seams.
- Added seven behavioral tests and observed each fail at its intended contract assertion.
- Handed back the sole Maven slot and ReadingPipelineTest edit ownership to controller.

## Current state

Seven behavioral tests are RED (7 assertion failures, 0 errors, 0 skipped across
two bounded runs). Maven has ended. Production implementation remains with the
GREEN owner; these failures do not claim implementation acceptance.

## Changed files

- progress/selection-reading-red.md
- src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessReadingPipelineTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | Read | Pre-existing progress/research files preserved |
| Explicit six newly added methods, JDK 26 Maven / Java 17 toolchain | Expected RED | 6 failures, 0 errors, 0 skipped |
| invalidReadingSelectionCannotBeReopenedAsCompletedDecision only | Expected RED | 1 failure, 0 errors, 0 skipped; invalid R999 reopened as COMPLETED |

## Decisions

- Use real discovery with a deterministic scripted Provider and frozen text reader.
- No production edits, live model, JDT, Builder or Activity regeneration.
- Controller granted the sole Maven slot for only newly added test methods.

## Blockers

- None.

## Exact next action

GREEN owner implements Tasks 1–2. This author moves to a separate triple-persistence
RED test class and must request a new Maven slot before executing that batch.

## Resume checks

Read this handoff and git status; preserve unrelated changes.

## Plan closeout destinations

- Durable decisions: approved detailed design and implementation plan
- Remaining issues: controller handoff
- Verification and output references: selection-reading-red-report.md beside task brief
