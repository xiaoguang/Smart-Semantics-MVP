# Progress: Stage 04 public orchestration/core RED tests

- Status: COMPLETE
- Agent role: Stage 04 public-seam TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded Java public-core RED selector covering the target `CodeToMarkdownAgent` orchestration, filesystem source registration safety, fresh-process idempotency, root-independent Candidate content, and the explicit Round-2 `improveCandidate` contract. No production or design changes.
- Approved inputs: `AGENTS.md`; Stage 04 §§3–5, §9, §16; current Stage 01–04 seams; `Stage04CandidateFixture`; scripted Stage 03 lifecycle adapters only.
- Current branch/worktree: Shared dirty worktree; preserve all unrelated and parallel changes.

## Completed

- Read the scoped repository instructions, Stage 04 public interface/source registry/orchestration/TDD sections, current Stage 01–04 Java seams, and the shared non-zero-flow `Stage04CandidateFixture`.
- Confirmed the test must remain Java/core-only: no CLI/HTTP, network, live model, customer build, or fabricated zero-Capsule success path.
- Created this progress record before modifying test sources.

## Current state

- Added one public-core selector with a real Stage 01 → Stage 02 → Stage 03 lifecycle bridge and filesystem archive assertions. The selector now also uses a separate relocated archive workspace, registration-derived independent series, and fresh scripted R1/R2 Provider counter.

## Changed files

- `progress/stage04-public-core-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04PublicCoreTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04PublicCoreTest test` | GREEN | Main compilation succeeded for 185 sources and test compilation for 61 sources; 9 tests ran with 0 failures and 0 errors. The relocated independent series executed exactly 2 fresh Provider rounds, the same-workspace fresh process consumed 0 extra rounds, and relocated Candidate content matched the original. |

## Decisions

- Keep the end-to-end fixture on the existing honest one-Flow/non-zero-Capsule scenario; zero-Capsule is deferred because the supplied shared fixture does not provide an honest public registration/pipeline construction for it.
- Require the target public API in `com.linguan.codemd.stage04` so the test crosses the M8 package boundary while continuing to use current package-private archive/lifecycle helpers only for fixture setup.
- Assert `improveCandidate` request validation and an explicit stable `NOT_IMPLEMENTED` fatal without implementing Round 2 semantics.

## Blockers

- The fixture correction is complete; no test-side blocker remains. No production workaround or design change was added by this task.

## Exact next action

- Parent agent can retain the bounded selector and use the passing run as the fixture/identity regression gate.

## Resume checks

- Re-read this file, run `git status --short`, and ensure only this progress file and the owned public-core test are changed by this task. Then implement the missing public M8 seam without weakening the assertions.
