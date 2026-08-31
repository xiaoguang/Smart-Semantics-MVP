# Progress: Stage03 multi-flow fixture and hard-anchor/gap isolation seam

- Status: BLOCKED
- Agent role: Sol/ultra design-analysis sub-agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Read-only analysis of Stage01→Stage02 multi-entry admission and Stage03 multi-flow isolation/merge behavior; produce an implementation-ready bounded test/design brief only.
- Approved inputs: Frozen repository files, Stage01/02/03 designs and tests, current Stage03 production, existing capsule review/progress artifacts. No model, network, customer runtime, capture, generation, or source refresh.
- Current branch/worktree: Shared `/Users/yexiaoguang/Documents/ErpMock` worktree; pre-existing unrelated changes are preserved.

## Completed

- Read repository, prototype, backend-agent, and GitHub-code scoped `AGENTS.md` files.
- Read the `codebase-design` skill and its deepening/design-it-twice references.
- Classified the task as a bounded read-only design spike.

## Current state

The bounded analysis did not complete within the parent task's time box. Work stopped without changing production, tests, fixtures, configuration, or design. Narrow evidence established that `Stage02Compiler` requires an entry-root `HTTP_ENTRY` Fact and then one unclaimed Fact for every configured flow stage; the existing second-entry contract deliberately permits at most one compiled Flow, and the completed capsule attempt observed two `FLOW_FACT_NOT_ADMITTED` dispositions with zero Flows/Capsules. The final fixture recipe and hard-anchor Interface decision remain unfinished and must not be treated as approved design.

## Changed files

- `progress/stage03-multiflow-design.md` (this owned progress artifact only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing unrelated modified/untracked paths recorded; no task-owned file existed yet. |

## Decisions

- Test and design through the public Stage01Request→Stage02Compiler seam; do not fabricate Stage01Result, FlowSlice, or EvidenceCapsule objects.
- Prefer fixture-only changes unless the public seam demonstrably cannot expose stable hard-anchor identity.

## Blockers

- Parent time box expired before the implementation-ready fixture and hard-anchor brief could be completed and self-checked.

## Exact next action

Resume from only these narrow files: `src/test/java/com/linguan/codemd/stage02/Stage02CompilerTest.java:400-470`, the flow admission/ownership methods in `src/main/java/com/linguan/codemd/stage02/Stage02Compiler.java`, `src/test/java/com/linguan/codemd/stage03/Stage03CapsuleTest.java`, `progress/stage03-capsule-tests.md`, the public Stage01/02 Flow/Proof/Gap records, and `Stage03Generator.CapsuleContext.of` plus `assemble`. Finish and self-check: (1) exact zero-Flow admission cause, (2) the smallest honest two-independent-flow frozen Java/XML/manifest recipe, (3) whether existing hard anchors suffice, and (4) one exact Luna/xhigh public-seam RED brief. Do not reread broad documents or edit code/tests/design.

## Resume checks

- Re-run `git status --short` and preserve unrelated shared-worktree edits.
- Confirm only this progress file is task-owned.
- Do not run tests or edit production/tests/design for this analysis task.
- Treat all unfinished analysis above as provisional until the resumed brief is completed.
