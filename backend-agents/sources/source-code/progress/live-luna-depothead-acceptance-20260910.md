# Progress: live Luna DepotHead acceptance

- Status: BLOCKED
- Agent role: primary implementation agent
- Model: gpt-5.6-luna / high
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Run one explicitly authorized, frozen DepotHead activity packet through the existing
  Codex Subscription adapter after its offline contracts passed. Do not modify Java, tests, source,
  prompts, schemas, or design.
- Approved inputs: User authorization for Luna/high activity interpretation; fixed jshERP commit
  `8c30ce7861570458920175e200bb2a6442713580`; two allowlisted Java excerpts selected by the
  existing opt-in test.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Direct offline tests for materials, activity explanation, process knowledge, report and runtime
  connections passed before this live product-quality sample.

## Current state

- The first opt-in test preflighted local Codex login, then started one `gpt-5.6-luna`/`high`
  DepotHead request using its clean two-reference packet. It exited without its requested
  structured response, so `ActivityExplainer` recorded `ACTIVITY_PROVIDER_FAILED_AFTER_START`
  with `CODEX_SUBSCRIPTION_EXECUTION_FAILED`.
- After a local command-boundary change had passed scripted tests, a second separately identified
  DepotHead execution used the exact test method (so AccountHead was not selected). It also
  failed after start and returned only the finite category `UNKNOWN`; no response artifact was
  produced and no third provider request was made.
- Non-generative local inspection confirms the bundled client is `codex-cli 0.153.4`; its `exec`
  help advertises the adapter's `--sandbox`, `--model`, `--output-schema`, and
  `--output-last-message` options, and `login status` reports a ChatGPT login. The static command
  surface does not identify why the started request exited, so no second request was used for
  diagnosis.
- Read-only account limits report a Pro plan with remaining subscription capacity and no active
  rate-limit/spend-control block. This rules out an exhausted account as the observed immediate
  process failure, but does not identify a client/model compatibility cause.

## Changed files

- `progress/live-luna-depothead-acceptance-20260910.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| business-module and runtime direct selectors | PASS | 26 offline/scripted tests, 0 failures/errors/skips. |
| one explicit DepotHead Luna/high sample | BLOCKED_AFTER_START | Login preflight passed; the one provider subprocess exited without a structured response. No automatic retry, fallback or second sample. |
| local Codex static capability inspection | PASS | `codex-cli 0.153.4`; documented non-interactive options exist; login status is ChatGPT. No model request made. |
| read-only Codex usage inspection | PASS | Pro subscription has remaining capacity; no active rate-limit or spend-control block. |

## Decisions

- Validate one core activity before a second domain or any repository-wide provider run.
- This sample checks semantic usefulness only; it does not represent a completed whole-repository
  analysis or execute customer code.
- A started provider failure is terminal for this execution. Diagnose the CLI adapter only through
  non-generative local capability inspection before any separately authorized new execution.

## Blockers

- The current local Codex CLI invocation contract is not yet known to be compatible with the
  installed client. The boundary now classifies bounded stdout/stderr transiently but the second
  call remained `UNKNOWN`; raw diagnostics are intentionally not persisted.

## Exact next action

- Keep the live semantic acceptance blocked. Continue offline technical and scripted business
  verification; do not issue another model request until there is a concrete CLI compatibility
  hypothesis and a separately authorized execution.

## Resume checks

- Confirm the output is under `.workspace/`, includes only the declared commit and two source
  references, and contains a reviewed activity with the requested business fields.
