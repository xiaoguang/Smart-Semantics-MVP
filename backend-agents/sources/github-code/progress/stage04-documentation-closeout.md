# Progress: Stage 04 documentation closeout

- Status: IN_PROGRESS
- Agent role: Stage 04 design/status closeout owner
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30T17:36:12-02:30
- Last updated: 2026-08-30T17:56:57-02:30
- Scope: Reconcile the target design, Stage 04 detailed design and reader navigation with the bounded implementation that passed direct tests.
- Approved inputs: Current production code, Stage 01–04 detailed designs, direct test results and task progress records; no live Provider, source capture, deployment, commit or push.
- Current branch/worktree: codex/github-code-design-walkthrough in /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2

## Completed

- Confirmed final direct verification: Stage 04 67/67, Stage 03 69/69 and Stage 01/02 79/79 GREEN.
- Identified stale Stage 04 status statements in DESIGN, README and the Stage 04 detailed design.
- Read the root, prototype, backend-Agent and GitHub-Code-Agent scoped instructions in full.
- Reconciled the documented seams against `DefaultCodeToMarkdownAgent`, `FilesystemSourceRegistry`,
  archive-v2 validation/Trace, `CodeMdCli`, `LoopbackHttpServer`, review-store, Round-2 and security tests.
- Calibrated DESIGN, README and the Stage 04 detailed design to the actual archive-v2, replay, Trace,
  recovery, SourceRegistry, Java core, injected CLI and loopback HTTP surfaces.
- Recorded the seven independent-review P1 findings without converting 67/67 GREEN into
  an unconditional bounded acceptance.
- Verified status language, local Markdown targets, balanced code fences and whitespace after the
  provisional calibration.

## Current state

- Documentation reconciliation is complete pending the root review adjudication. Stage 04 is consistently
  described as `IMPLEMENTED / ACCEPTANCE REVIEW IN PROGRESS（bounded v0）`. The target CLI currently
  exposes target `generate`/`validate`/`trace`/`candidate`; the loopback HTTP Adapter currently exposes
  generation runs and Candidate/Markdown/Trace GETs, not the complete future route list.

## Changed files

- `DESIGN.md`
- `README.md`
- `docs/stages/04-runtime-archive-trace-recovery.md`
- `progress/stage04-documentation-closeout.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest='Stage04*Test' test` | PASS | 67 tests, 0 failures/errors/skips |
| `mvn -Dtest='Stage03*Test' test` | PASS | 69 tests, 0 failures/errors/skips |
| Stage 01/02 direct selector | PASS | 79 tests, 0 failures/errors/skips |
| stale Stage 04 status search | PASS | No current `Stage 04 ACCEPTED`, old four-P1 wording or stale partial/not-implemented claim in the four owned documents |
| local Markdown target check | PASS | Every relative file target in DESIGN, README and the Stage 04 detailed design exists |
| code-fence check | PASS | DESIGN 64 and Stage 04 detailed design 34 fence markers; both even/closed |
| `git diff --check` and trailing-whitespace scan | PASS | No whitespace errors |

## Decisions

- Mark the scripted-Provider, local-frozen, bounded Java/Spring MVC/MyBatis target profile as implemented,
  but keep final Stage 04 acceptance pending the independent review adjudication.
- Keep live Codex/OpenAI Provider execution, generic repository breadth, remote Git capture, target runtime
  bootstrap, CLI `improve`/`serve` and future HTTP routes explicitly outside the current profile.
- Do not claim Java/CLI/HTTP full archive byte parity: tests prove shared Candidate/content identity, while
  archive tests separately prove canonical bytes and idempotence.

## Blockers

- Final status depends on closing or explicitly downgrading seven P1 findings: Candidate address redirection,
  coherent archive/identity tamper, typed Trace closure, public recovery dispatch, unaffected-Flow Round-2
  reuse, fatal-finding addendum verification and pre-allocation byte limits.

## Exact next action

- Await the root follow-up after the seven P1 fixes; then re-read production/tests, calibrate final status,
  rerun documentation checks and mark this progress complete only if the acceptance gate closes.

## Resume checks

- Read this file first, then verify the three direct test totals from `progress/target-architecture-implementation.md`.
- Preserve the two unrelated dirty documents outside `backend-agents/sources/github-code`.
