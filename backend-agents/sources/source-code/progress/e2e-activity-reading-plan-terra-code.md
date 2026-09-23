# Progress: e2e-activity-reading-plan-terra-code

- Status: FROZEN_AWAITING_ROOT_GREEN
- Agent role: Task 2 production-code repair (Terra/xhigh)
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-23
- Last updated: 2026-09-23
- Scope: Minimal existing `ActivityReadingCoordinator` reopen validation fixes only; no tests, Maven, product calls, scans, commits, staging, or historical-material writes.
- Owning plan: `.workspace/end-to-end-business-delivery-20260923/task-2-brief.md` and Task 2 review brief
- Approved inputs: Astra review findings supplied by root; current formal source-code checkout and existing Sol code handoff.
- Current branch/worktree: Shared formal checkout; preserve all pre-existing source, test, and documentation edits.

## Completed

- Read the Task 2 review brief, current code handoff, and source-scoped instructions.
- Mapped the three review findings to `ActivityReadingCoordinator.reopen` and its existing
  validation branches without adding a persistence, evidence, or public seam.
- Implemented v2 replay verification of the saved selected/unread lists, navigation summary, and
  unit-disposition ledger. Direct plans use their original whole-packet selection and disposition
  shape; paged plans derive the same values after scope replay and full caller closure.
- Implemented v1 saved-slice binding to a legal raw v1 decision definition. The check compares
  key, entries, shared context, and scope, then compares the derived complete caller closure as a
  set. It accepts the real entry/caller-closure historical record rather than treating a raw target
  request as the saved packet's entire required-unit list.
- Split v2 historic replay from current limits: replay retains structural, reference, replacement,
  and cycle checks while allowing old intermediate final-key counts; reopening then applies the
  current `maxSlicesPerPacket` to the saved final effective keys and the existing executable-slice
  limit.

## Current state

- `requireV2SavedScopeState` replays selection and scope state but currently does not compare the
  replayed selected/unread set, page totals/remaining count, or computed dispositions to the saved
  v2 record. The smallest repair computes those existing record values after replay and exact-checks
  them before returning the reopened plan.
- `requireHistoricalPagedV1` validates raw decisions and saved packets independently, but does not
  bind each saved slice's key, entries, required/shared units, and scope text to a legal raw
  decision definition. The comparison must accept the actual legacy fallback: an oversized same-key
  revision is removed and the prior legal definition retained with its entry/caller closure, rather
  than reintroducing the rejected entry-plus-requested-only selection restriction or fabricating v2
  completion.
- v2 reopening uses `validateDecision(..., profile)` for every raw historic response. Its existing
  per-response `maxSlicesPerPacket` check can reject a valid old intermediate scope that was later
  explicitly superseded. Replay must validate historical references/dispositions without applying
  the newly smaller limit to each intermediate response, then enforce the current limit on the
  final effective key set (including keys whose bodies remain incomplete) and saved executable
  slices.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingCoordinator.java`
- `progress/e2e-activity-reading-plan-terra-code.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityReadingCoordinator.java` | Passed | No whitespace errors in this worker's production patch. |
| Maven direct tests | Not run | Root retains the single build slot for Luna's fixture repairs and the one combined GREEN command. |

## Decisions

- Keep the repair inside existing coordinator replay/validation and existing private plan contract; no new storage, evidence ledger, model stage, or public Agent seam.

## Blockers

- Root owns Maven session `53582` for the frozen combined direct tests and Spotless check. No
  compilation or behavioral verification was run by this worker, by instruction.

## Exact next action

- Await root's combined GREEN/Spotless handoff. Do not edit production or tests until root assigns
  a new evidenced scope.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm the combined root test handoff before any further production edit or run.

## Plan closeout destinations

- Durable decisions: Task 2 code report and owning module documents, by root after green verification.
- Remaining issues: Task 2 test report / root handoff.
- Verification and output references: isolated Maven Surefire reports, once root grants the run.
