# Progress: Stage03 task body tests

- Status: COMPLETE
- Agent role: Stage03 test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: At most two public-seam tests for complete task budget/profile admission and registry path cleanliness.
- Approved inputs: scoped AGENTS, Stage03 design, current Stage03 public records, and scripted fixtures.
- Current branch/worktree: shared worktree; preserve unrelated agent changes.

## Completed

- Created this owned progress file before test edits.

## Current state

- Added exactly two public-seam tests: complete task profiles/budget and
  parameterized arbitrary Unix/Windows/UNC/relative registry-path rejection.
- The RED is assertion-only: test compilation succeeds and no provider or
  fixture error occurs.

## Changed files

- `src/test/java/com/linguan/codemd/stage03/Stage03TaskBodyTest.java`
- `progress/stage03-task-body-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03TaskBodyTest test` | RED | 13 tests, 13 assertion failures, 0 errors, 0 skipped. The task test reports six missing/mismatched fields: knowledgeProfileId, nineSectionProfileId, maxFlowInterpretations, requiredSectionCount, maxDocumentBytes, and maxReaderItems (JSON `asText`/`asInt` observed empty/0). All 12 path cases fail at `assertThrows`: current implementation accepts each path and does not fail before Provider execution. |

## Decisions

- Task assertions derive profile and budget expectations directly from the
  public `Stage03Request` and `Stage03ResourceBudget` records.
- Path cases use arbitrary `.txt`/backslash forms and contain no prompt/provider
  words or source extensions, so a pass cannot rely on existing token filters.

## Blockers

- None.

## Exact next action

- RED evidence is complete. Terra may now implement the missing task projection
  and path safety while preserving the two public-seam contracts.

## Resume checks

- Modify only the owned Stage03 test and this progress file.
