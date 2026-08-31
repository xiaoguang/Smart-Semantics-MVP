# Progress: stage03-deterministic-replay-tests

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Stage03 public-seam deterministic replay tests only; no production/design changes
- Approved inputs: frozen Stage03 implementation, DESIGN.md replay contract, docs/stages/03-nine-section-generation.md §3.5
- Current branch/worktree: shared workspace; preserve unrelated changes

## Completed

- Read repository, backend-agent, and GitHub source AGENTS.md guidance.
- Read the Stage03 deterministic replay design and existing Stage03 seam/fixture files.
- Added `Stage03DeterministicReplayTest` as the minimal one-Flow public-seam RED slice: generation transcript capture, replay equality/no-Provider API proof, and grouped task/response/round/registry/Stage02 drift cases.
- Ran the new selector and confirmed the expected compile-time RED against the not-yet-implemented replay seam.

## Current state

- `Stage03DeterministicReplay`, `Stage03ReplayRequest`, and `CanonicalFlowRound` are designed but absent from production code.
- Existing generation seam exposes tasks and scripted responses through `Stage03Fixtures`/`Stage03ScenarioBridge`.
- The new test currently cannot compile until the designed replay records and replay class exist.
- No production source or design document was changed.

## Changed files

- This progress file.
- `src/test/java/com/linguan/codemd/stage03/Stage03DeterministicReplayTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03DeterministicReplayTest test` | RED (intentional) | Maven compiled 177 production files, then test-compile failed with 32 errors because `Stage03DeterministicReplay`, `Stage03ReplayRequest`, `CanonicalFlowRound`, and replay failure enum constants are not yet present. No test executed. |
| `git status --short` | PASS | Only the pre-existing outer worktree changes plus this source tree's untracked work are present; no production/design path was edited by this task. |
| `git diff --check --no-index /dev/null <new test/progress file>` | PASS | No whitespace diagnostics (no-index exit 1 is expected for new content). |

## Decisions

- Write one minimal representative vertical public-seam slice first: generation transcript capture plus replay equality/zero-provider API proof, then focused drift/failure assertions.
- Keep all test-only transcript construction in the Stage03 test bridge; do not add production APIs or scripted-provider replay shortcuts.
- Keep all transcript construction in the test-only capture wrapper; replay receives no `StructuredModelProvider`.

## Blockers

- None yet.

## Exact next action

- Stage03 implementation owner should add the designed replay records/class and enum values, then rerun only `mvn -Dtest=Stage03DeterministicReplayTest test`; do not broaden to the repository suite.

## Resume checks

- Re-run `git status --short`; inspect this file and the targeted test before further edits.
