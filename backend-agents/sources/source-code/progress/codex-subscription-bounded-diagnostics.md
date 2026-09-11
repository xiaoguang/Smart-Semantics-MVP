# Progress: Codex Subscription bounded diagnostics

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add a bounded, non-persistent diagnostic category to a failed local Codex CLI process so
  a future explicitly authorized execution can distinguish invocation, authentication, capacity,
  timeout, schema and unknown failures. Do not retain raw stderr, prompts, source, credentials or
  model responses; do not issue a model request in this work unit before the command contract has
  a direct automated test.
- Approved inputs: The user explicitly authorized Luna/high product interpretation and continuing
  implementation. The active design permits local logged-in Codex only and prohibits retrying the
  already-started failed activity execution.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the prior DepotHead execution preflighted login and began one request, but the process
  boundary redirected stderr to discard; its stable failure code therefore cannot distinguish
  client-contract failures from model/runtime failures.
- The process boundary now redirects stdout and stderr only to its private temporary directory,
  maps at most the first 4 KiB of each to a finite category, then deletes both files before the
  method returns.
- A second, explicitly authorized DepotHead execution was made after the local command tests. It
  still failed after start, now as `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`; it did not
  produce an activity and was not retried.

## Current state

- The command classifier covers model configuration, authentication, capacity, sandbox,
  missing structured output and unknown failures. It does not claim to expose raw service
  diagnostics.

## Changed files

- `progress/codex-subscription-bounded-diagnostics.md`
- `src/main/java/org/sourceanalysis/app/adapter/provider/ProcessCodexSubscriptionCommand.java`
- `src/test/java/org/sourceanalysis/app/adapter/provider/ProcessCodexSubscriptionCommandTest.java`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| prior explicit DepotHead Luna/high sample | BLOCKED_AFTER_START | Process exited without the requested structured output; no retry was issued. |
| stdout-category test before implementation | RED | A fake CLI message written only to stdout was classified as `UNKNOWN`. |
| command boundary selectors | PASS | 4 tests, 0 failures/errors/skips; stderr/stdout model errors map to `MODEL_CONFIGURATION` without raw text exposure. |
| second explicit DepotHead Luna/high sample | BLOCKED_AFTER_START | The process again returned no structured output; bounded category is `UNKNOWN`. No third call was issued. |
| `spotless:check && git diff --check` | PASS | 0 formatting or whitespace errors. |

## Decisions

- Diagnostics may classify a bounded stderr stream transiently but must never write raw stderr to
  a source artifact, a checkpoint, a receipt or a progress file.

## Blockers

- The installed Codex CLI's unknown execution failure is external to the offline/scripted path.
  Further live attempts require a distinct authorization and a concrete client-side hypothesis.

## Exact next action

- Continue offline business-material and repository-knowledge work. Do not make another live
  Provider request solely to turn `UNKNOWN` into a more specific category.

## Resume checks

- A test-only fake executable must prove no raw error text is retained or surfaced; live model
  execution remains a separately identified attempt.
