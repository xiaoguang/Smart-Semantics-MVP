# Progress: Task 7 live Luna four-entry validation

- Status: IN_PROGRESS
- Agent role: Primary implementation coordinator
- Model: GPT-5
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Run exactly one authorized Luna/high Activity DRAFT and one complete REVIEW, at most two
  requests, for the frozen UserController material defined by Task 7. No real process, report,
  whole-repository model work, retry, API-key fallback, customer build, scan, or source refresh.
- Approved inputs: User-approved Task 7 plan and repeated instruction to continue; scoped
  `AGENTS.md`; Task 6 commit `6550eb8`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Task 6 scripted complete/partial and capacity verification was pushed as `6550eb8`.

## Current state

- No live Provider request has started. The next action is a read-only preflight of the exact saved
  material identity, its four ordered entries and refs, the Codex Subscription login state, and the
  Java-to-Codex subprocess contract. Any mismatch records a zero-request stop for this candidate.

## Changed files

- `progress/task7-live-luna-four-entry.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Not started | N/A | No live request has started. |

## Decisions

- DRAFT and REVIEW belong to one candidate. The response must be structurally valid, use only the
  frozen local keys and refs, and leave no entry silent; PARTIAL or fatal output is saved and not
  retried.

## Blockers

- None observed before preflight.

## Exact next action

- Locate the existing exact-material live selector and provider command contract, then run only its
  read-only preflight checks before deciding whether a request may start.

## Resume checks

- Re-read this file, inspect `git status --short`, confirm `6550eb8`, and confirm no Task 7 output
  directory or live Provider receipt exists before the preflight.
