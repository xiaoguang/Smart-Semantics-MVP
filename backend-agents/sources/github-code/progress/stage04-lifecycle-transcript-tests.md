# Progress: Stage04 lifecycle transcript public-seam RED tests

- Status: COMPLETE
- Agent role: TDD test-writing sub-agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Stage04 §7–§9 and §10.3 lifecycle/transcript public-seam RED tests only
- Approved inputs: Stage04 design, Stage03 canonical rounds, existing Stage04 lifecycle/ledger tests
- Current branch/worktree: codex/rag-frontend-phase-one

## Completed

- Read repository, backend-agent, and github-code scoped AGENTS instructions.
- Read Stage04 lifecycle/slot/archive-v2 contracts and relevant Stage03 generation/canonical-round code.
- Confirmed current bridge exposes only a single-round `StructuredModelProvider` seam and does not expose a sealed `Stage03RunTranscript`.
- Completed the intentional RED handoff; the parent implementation owns the
  missing lifecycle attempt/transcript seam and the subsequent GREEN cycle.

## Current state

- Added `Stage04LifecycleTranscriptTest` with two public-seam RED tests. The first
  runs the frozen reservation Flow through Stage03 R1/R2 and requires a sealed
  transcript with one model round/lifecycle receipt per canonical round, exact
  task/response/runtime closure, real preflight/attempt/started references,
  ACK-before-content order, and one `RESERVED -> STARTED_CONSUMED` transition
  followed only by same-state audit append. The second drives a started R2
  content failure and requires terminal state without prestart rollback while
  retaining both started events.
- The focused selector reaches test compilation and fails at the intended seam:
  the provider preflight record has no attempt ID and the bridge has no
  `seal(Stage03Result)` transcript factory.
- Fixture correction (2026-08-30): changed the first-consumption count to only
  match `fromState != STARTED_CONSUMED && toState == STARTED_CONSUMED`; the
  following same-state audit assertion now remains independent and can count
  subsequent `STARTED_CONSUMED -> STARTED_CONSUMED` events.

## Changed files

- This progress file.
- `src/test/java/com/linguan/codemd/stage04/Stage04LifecycleTranscriptTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short -- linguan-prototype-v2/backend-agents/sources/github-code` | PASS | No pre-existing changes in assigned source scope before edits. |
| `mvn -Dtest=Stage04LifecycleTranscriptTest test` | RED (expected) | Test compilation reaches the new lifecycle seam: `ProviderPreflightReceipt` only accepts `(boolean, String)` at lines 53/152, and `LifecycleProviderBridge` has no `seal(Stage03Result)` at line 79. Main compilation succeeds; no test executes. |
| `git diff --check -- src/test/java/com/linguan/codemd/stage04/Stage04LifecycleTranscriptTest.java progress/stage04-lifecycle-transcript-tests.md` | PASS | No whitespace diagnostics. |
| Fixture correction before rerun | RECORDED | First-consumption assertion excludes same-state audit events; production files remain untouched. |
| `mvn -Dtest=Stage04LifecycleTranscriptTest test` (after fixture correction) | RED (expected) | Main compilation succeeds; test compilation reports exactly 3 intended seam errors: two `(boolean, String, String)` `ProviderPreflightReceipt` constructor calls (lines 53/153) and missing `LifecycleProviderBridge.seal(Stage03Result)` (line 79). No test executes. |

## Decisions

- Do not modify production classes or existing design files.
- Use only frozen synthetic Stage03 fixtures and scripted lifecycle adapters; no network/live model/customer build.
- Run only the new test selector and record exact RED output here after test creation.
- Keep adapter-provided lifecycle IDs visibly non-synthetic from the bridge's
  perspective; assertions reject deriving started IDs from task IDs and require
  persisted slot-event references.

## Blockers

- None for this test-only handoff. Production lifecycle/transcript symbols are
  intentionally absent until the parent implementation cycle.

## Exact next action

- Parent implementation should add the lifecycle attempt/transcript public seam,
  then rerun only `mvn -Dtest=Stage04LifecycleTranscriptTest test`.

## Resume checks

- Re-run `git status --short` and inspect this file before continuing.
