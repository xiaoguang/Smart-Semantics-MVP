# Progress: Task 3 Capsule wire-field RED retry

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add one public seam regression test for the Task 3 Capsule wire reduction.
- Approved inputs: Task 3 contract in the active cleanup implementation plan; existing Flow/Capsule publisher and reader fixtures.
- Current branch/worktree: shared implementation worktree `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Validated the existing single public-seam `CapsuleWireReductionTest` candidate.
- The test covers projected and public evidence capsules, both retired JSON fields, v9/v7 schema expectations, retained context/facts/gaps/signals/source closure, and rejection of old v8/v6 policies.

## Current state

Scoped rules and the Task 3 contract have been read. Existing worktree contains another untracked Capsule wire test and progress file; those files are outside this task and were not edited.

The existing untracked `CapsuleWireReductionTest` already matches the required single public-seam RED contract, so it was validated instead of creating a duplicate test or modifying another agent's file.

## Changed files

- This progress file only so far.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleWireReductionTest test` | EXPECTED RED | 1 test, 1 failure, 0 errors, 0 skipped; current output remains projection v8/evidence v6, both retired fields are present, and old v8/v6 policies are still accepted. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The RED test will exercise the existing public Flow/Capsule publication path and assert the required v9/v7 schema versions, absence of both retired JSON fields, and preservation of context/facts/gaps/signals/source closure.
- No production code, existing tests, documentation, or another agent's progress file will be modified.
- The smallest GREEN surface is `CapsuleProjection.EvidenceCapsule`, `EvidenceCapsuleProjector`, `CapsuleProjectionModulePublisher`, and the owning artifact policy registrations/readers for projection v9 and evidence capsule v7.

## Blockers

## Exact next action

Parent agent should use the existing `CapsuleWireReductionTest` plus its original progress file as the Task 3 RED deliverable; this retry progress records the validation evidence only.

## Resume checks

- Recheck this file and `git status --short` before any further edit.
- Confirm only this file and the new RED test belong to this task.
