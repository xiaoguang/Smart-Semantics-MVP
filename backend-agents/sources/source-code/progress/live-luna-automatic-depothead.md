# Progress: Live Luna automatic DepotHead activity

- Status: BLOCKED
- Agent role: Primary implementation agent
- Model: gpt-5.6-luna / high through the logged-in Codex subscription session
- Started: 2026-09-11 15:50 UTC
- Last updated: 2026-09-11 16:03 UTC
- Scope: Run one bounded product quality check of the automatic `POST /depotHead/batchSetStatus` material produced from the fixed jshERP source. The only model work is one Activity DRAFT and one full Activity REVIEW; no report, process synthesis, retry, model switch, API-key fallback, customer Maven, source capture, or network-source access.
- Approved inputs: User direction to complete flow interpretation using Luna/high; fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; material `material:8bdd553a2b3a7f9625a5e216ef358e7e062e149589372d4df1242e9fe9330492` in the zero-Provider materials-only run.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Verified `codex login status` reports a ChatGPT login.
- Opened the actual persisted packet before any call. It has 24 allowlisted short references, 24 technical observations, 8 explicit limitations and a serialized model packet of 9,444 bytes. The packet identifies one batch-status entry; it includes no model-visible source paths, line numbers, hashes, workspace identifiers, Provider configuration or credentials.
- Started the one declared DRAFT+REVIEW request through `LiveLunaAutomaticMaterialIT`. The attempt stopped before any structured DRAFT was returned with `ACTIVITY_PROVIDER_FAILED_AFTER_START`, whose adapter cause is `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`.
- Did not retry the material, use an API key, switch Provider, or synthesize an activity. The adapter deletes its private stdout/stderr temporary directory on every outcome, so this one execution did not leave enough safe diagnostics to establish a root cause.

## Current state

- The declared DepotHead request is terminally failed for this execution. The work is blocked on diagnosing the subprocess boundary without replaying this material. The visible CLI help and `login status` confirm the executable, flags and ChatGPT login are present, but do not explain the actual process exit.

## Changed files

- progress/live-luna-automatic-depothead.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `codex login status` | PASS | Logged in using ChatGPT. |
| persisted packet inspection | PASS | One selected automatic Step05 packet, within the test's 20,000-byte input budget. |
| `LiveLunaAutomaticMaterialIT` with exact packet | FAILED_AFTER_START | No structured draft returned; `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`; no retry was issued. |
| direct Codex CLI `--version`, `login status`, `exec --help` | PASS | Codex CLI 0.153.4, ChatGPT login, and all adapter command-line flags are recognized. |

## Decisions

- Use the existing explicitly opt-in `LiveLunaAutomaticMaterialIT` seam so that the model sees the exact stored model packet and the output is retained only in the ignored local workspace.
- Treat this as a product-quality sample, not as a claim that the full repository or its nine-section document has been semantically accepted.

## Blockers

- The production adapter removes the only raw subprocess diagnostic before classifying an unrecognized exit. A bounded, non-secret diagnostic improvement must be designed and tested before a different product sample can be attempted.

## Exact next action

- Complete root-cause investigation of the process boundary. Do not retry the DepotHead material; any later diagnostic call must use no customer source and have its own explicit scope and receipt.

## Resume checks

- Read this file, retain the failed status, inspect the process adapter's error lifecycle and direct CLI options, and do not alter the declared one-package DRAFT+REVIEW boundary.
