# Progress: Stage 04 Round-2 behavioral RED test

- Status: COMPLETE
- Agent role: Stage 04 Round-2 public-seam TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded `Stage04Round2Test` covering approved review finding admission, frozen Round-2 overlay/task lineage, immutable parent-byte preservation, idempotence, and conflict/parent rejection. No production, design, or existing-test changes.
- Approved inputs: scoped `AGENTS.md`; Stage 04 §§4.2 and 9.2; current public agent, improvement request, lifecycle, archive-v2, and review-store seams/tests.
- Current branch/worktree: Shared dirty worktree; preserve unrelated and parallel changes.

## Completed

- Read the scoped repository guidance, Stage 04 Round-2 contracts, current public agent/improvement/lifecycle/archive implementation and tests, and public review-store types/test.
- Created this progress record before modifying tests.

## Current state

- Corrected the archive-v2 JSON contract: `model-rounds.task.inputJson` is already a JSON object and is inspected directly as a `JsonNode` with explicit object assertions for both parent and improved tasks. Runtime `FlowModelTask.inputJson()` string parsing remains unchanged.

## Changed files

- `progress/stage04-round2-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04Round2Test.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04Round2Test test` | PASS | Test and testCompile succeeded; 1 test ran with 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest=Stage04Round2Test test` | RED (expected) | Test and testCompile succeeded; 1 test ran with 1 assertion failure: `ROUND_2_IMPROVEMENT_NOT_IMPLEMENTED`; 0 errors. |

## Decisions

- Use reflection for the target five-argument `DefaultCodeToMarkdownAgent` constructor so the shared Maven module remains test-compilable while Round-2 orchestration is absent.
- Return one clear behavioral RED if the overload/store/behavior is missing; never create a compile-only RED or invoke a live provider/network/customer build.

## Blockers

- No blocker. The parallel Round-2 implementation is present and the corrected selector passes.

## Exact next action

- None for this contract-correction slice.

## Resume checks

- Re-read this file, run `git status --short`, and ensure only this progress file and the owned test source changed.
