# Progress: Stage04 security RED tests

- Status: COMPLETE
- Agent role: Stage04 security TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add a minimal public-seam RED test slice for source-registry and completed-candidate recovery security boundaries.
- Approved inputs: Stage04 public Java seams, frozen fixtures, direct targeted Maven test only.
- Current branch/worktree: codex/github-code-design-walkthrough in /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2

## Completed

- Read the repository, backend, source-scoped and Stage04 instructions before editing.
- Added three bounded public-seam security tests in one test class.
- Confirmed all three tests are intentional RED cases; no production implementation was changed.

## Current state

The security RED slice is complete and ready for Terra production implementation.

## Changed files

- progress/stage04-security-tests.md
- src/test/java/com/linguan/codemd/stage04/Stage04SecurityTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04SecurityTest test` | RED (expected) | 3 tests, 3 assertion failures, 0 errors/skips: ancestor registry symlink accepted; duplicate equivalent registrations silently selected; tampered completed Candidate returned |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Modify no production code, existing tests, design documents, or Round2 files.
- Add at most one test class and three test methods; preserve expected RED behavior.

## Blockers

- None. RED is the planned missing production behavior, not an execution or permission blocker.

## Exact next action

Terra should implement the three protections in production, then rerun only `Stage04SecurityTest` and preserve the failure codes defined by the Stage04 contract.

## Resume checks

- Read this progress file before continuing.
- Check `git status --short` and confirm only this progress file and the new security test are owned by this task.
- Run only the new Stage04 security selector.
- Do not modify this test as part of the production GREEN cycle.
