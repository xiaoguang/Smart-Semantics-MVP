# Progress: Codex subscription provider

- Status: COMPLETE
- Agent role: Root coordinator; shared Luna/high production Provider adapter
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add the smallest `StructuredModelProvider` adapter that invokes the locally logged-in Codex CLI with a bounded JSON input and JSON Schema output contract. Automated tests use a fake command boundary only; this work does not invoke a live model.
- Approved inputs: Scoped `AGENTS.md`; Step 06/07/08 designs; official Codex CLI help; current `StructuredModelProvider` seam.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Verified local Codex exposes non-interactive `exec`, model selection, read-only sandbox, `--output-schema`, `--output-last-message`, `--ephemeral`, and `login status` commands without a model call.
- Confirmed the configured `model_reasoning_effort="high"` override is accepted by the local CLI help path with strict config enabled; product execution will still record the declared rather than assumed upstream identity.
- Added the minimal shared Provider profile, adapter and direct command boundary. It sends the versioned instructions and untrusted JSON through stdin, gives Codex the caller schema through `--output-schema`, uses an empty private working directory with read-only sandbox, and returns only the final JSON response.
- Added a fake-command public-seam test: it verifies `gpt-5.6-luna`/`high`, read-only identity, bounded input/schema forwarding, and no API credential text; it makes no live Provider request.

## Current state

- The adapter is green with a fake command boundary. A real Luna/high quality run is intentionally still pending the runnable business-pipeline entry and a declared small input package.

## Changed files

- `progress/codex-subscription-provider.md`
- `src/main/java/org/sourceanalysis/app/adapter/provider/CodexSubscription*.java`
- `src/main/java/org/sourceanalysis/app/adapter/provider/ProcessCodexSubscriptionCommand.java`
- `src/test/java/org/sourceanalysis/app/adapter/provider/CodexSubscriptionStructuredProviderTest.java`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `codex exec --help` | PASS | Non-interactive, model, read-only, output-schema and output-last-message options are available. |
| `codex login --help` | PASS | Local login status command is available. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=CodexSubscriptionStructuredProviderTest test` | PASS | 1 test; fake command only, 0 live Provider calls. |

## Decisions

- The adapter relies on the existing login session only; credentials are neither read nor saved by Java.
- Test doubles will replace the one command boundary. No automated test makes a product model request.

## Blockers

- None for adapter implementation. A real Luna/high quality run remains a separate deliberate action after the runtime path supplies a small frozen input package.

## Exact next action

- Build the minimal runtime composition that can hand a frozen Step 05 publication to the completed business modules.

## Resume checks

- Re-read this file and Step 06 provider contract. Do not invoke Codex `exec` without a user-visible product-run decision.
