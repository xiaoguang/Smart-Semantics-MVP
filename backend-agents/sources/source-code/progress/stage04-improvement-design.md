# Progress: Stage04 Round-2 improvement design closure

- Status: COMPLETE
- Agent role: Root architecture and detailed-design owner
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30T16:08:00-02:30
- Last updated: 2026-08-30T16:15:00-02:30
- Scope: Close the missing durable review-finding input and immutable-basis contract required before implementing Reader Candidate Round 2. Do not change production code or tests in this work unit.
- Approved inputs: `DESIGN.md`; `docs/stages/04-runtime-archive-trace-recovery.md`; current archive-v2, public core and ledger implementation; user-approved two-candidate rule.
- Current branch/worktree: `codex/github-code-design-walkthrough` in the shared dirty worktree.

## Completed

- Confirmed the existing design requires exact archived finding IDs but does not define the durable review-finding record, append seam, storage identity or lookup closure.
- Rejected a misleading test approach that expected Round 2 to accept IDs that had never been archived.
- Confirmed Round 2 re-executes the same bounded M5 R1/R2 protocol on the frozen basis; it is not a zero-Provider replay.
- Added the canonical `CandidateReviewFinding`, append-only `CandidateReviewStore`, exact finding-set resolution and immutable review storage contract.
- Added the per-Flow `FlowImprovementOverlay` boundary: base task/schema stay frozen, the overlay has an independent digest, unrelated Flows reuse parent rounds and only affected Flows run a new R1/R2 pair.
- Synchronized the stable target architecture and Stage04 detailed design so test and production agents have one implementable contract.

## Current state

- The missing Round-2 input contract is closed. Implementation may now proceed TDD-first without accepting arbitrary finding IDs or blending review prose into evidence.

## Changed files

- `progress/stage04-improvement-design.md`
- `DESIGN.md`
- `docs/stages/04-runtime-archive-trace-recovery.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg` for the four new identity/seam terms | PASS | Both target and Stage04 design contain the review store, overlay and independent digest rules. |
| Markdown fence parity | PASS | DESIGN 64 fences; Stage04 34 fences; both even. |
| `git diff --check -- DESIGN.md docs/stages/04-runtime-archive-trace-recovery.md progress/stage04-improvement-design.md progress/target-architecture-implementation.md` | PASS | No whitespace errors. |

## Decisions

- Finding IDs are not sufficient by themselves. Every ID must resolve to an immutable canonical record bound to the exact Round-1 Candidate and a validated reader/section basis.
- Review findings live outside the immutable Candidate directory in their own append-only ledger; recording a review does not mutate Candidate content.
- Round 2 may consume only explicitly allowed term-selection or section-presentation findings. Evidence/fact/flow/registry expansion is rejected before reserving the Round-2 slot.

## Blockers

- None.

## Exact next action

- Luna should replace the misleading arbitrary-ID RED with a public `CandidateReviewStore`-backed RED, then Terra should implement the bounded ledger/overlay/Round-2 vertical.

## Resume checks

- Read this file, inspect the current Stage04 design diff, and preserve all production/test changes owned by other agents.
