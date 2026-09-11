# Progress: live Luna automatic packet quality check

- Status: IN_PROGRESS
- Agent role: Primary implementation agent
- Model: gpt-5.6-luna / high, one explicit product-quality activity packet
- Started: 2026-09-11 03:35 NDT
- Last updated: 2026-09-11 03:43 NDT
- Scope: Add and run one opt-in integration seam that reads one previously inspected automatic
  BusinessMaterial record, sends only its clean model packet to the logged-in Codex Luna provider,
  and saves the reviewed activity plus program-side source references for human inspection.
- Approved inputs: User explicitly requested Luna/high activity interpretation; inspected automatic
  DepotHead packet from fixed commit `8c30ce7861570458920175e200bb2a6442713580`; provider cap is
  one material, one DRAFT and one REVIEW, three-minute timeout each.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Verified `codex login status` returns `Logged in using ChatGPT`.
- Inspected the automatic DepotHead model packet after generic source selection improvements.
- Added and compiled the opt-in integration seam; the source JSONL record is reconstructed on the
  program side, while the provider receives only its `ModelActivityPacket`.
- Started exactly one DRAFT request. The local Codex process exited before structured output and
  the ActivityExplainer recorded `ACTIVITY_PROVIDER_FAILED_AFTER_START`; no REVIEW, output file,
  retry, alternate provider, or expanded material call occurred.

## Current state

- This product-quality execution is terminally failed. Login preflight succeeded, but the provider
  returned only the bounded category `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`; its private
  temporary stdout/stderr is deleted by the current adapter, so this run cannot establish whether
  the immediate failure was model configuration, CLI transport or service availability.

## Changed files

- `progress/live-luna-automatic-packet-quality-check.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaAutomaticMaterialIT.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `/Applications/ChatGPT.app/Contents/Resources/codex login status` | PASS | Logged in using ChatGPT. |
| materials-only fixed-repository planner | PASS | Current automatic DepotHead packet was inspected before this live call. |
| opt-in live seam compilation | PASS | `mvn test-compile`, 144 test sources compiled. |
| one automatic DepotHead Luna/high DRAFT | FAILED, no retry | `ACTIVITY_PROVIDER_FAILED_AFTER_START`; no output file was written. |

## Decisions

- This is a single quality gate, not an all-entry batch. A failed request is recorded as failed and
  will not be retried or replaced by another provider.
- Do not interpret the earlier manually curated live-Luna result as validation of the automatic
  packet. This automatic package currently has no live response because its sole permitted call
  failed before a structured response.

## Blockers

- Provider transport diagnostics are insufficient because the adapter removes private command
  output after reducing the failure to `UNKNOWN`. The failed task itself must not be replayed.

## Exact next action

- Continue zero-provider work on the remaining business modules. Before a separately authorized
  future live task, improve the adapter's non-secret failure receipt so a new task can be diagnosed
  without retaining raw prompts or source.

## Resume checks

- Do not re-use the failed task identity. Any future live attempt needs a new inspected packet and
  current authorization; it must retain only a bounded, non-secret diagnostic category/receipt.
