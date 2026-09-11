# Progress: activity explanation scripted checkpoint

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Verify the existing local activity explanation seam with its scripted Provider, output-schema, prompt-boundary, budget and persistence tests. Do not invoke the Codex subscription adapter or a live model in this work item.
- Approved inputs: Existing business-material checkpoint, Step 06 contract, scripted Provider tests, and provider command test double only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Inspected the existing activity public seam, the two versioned Chinese prompts, the dynamic closed output schema and all six direct test classes.
- Confirmed the design intent: the provider receives the clean `ModelActivityPacket`; Java retains file/line/hash/proof identifiers outside the packet and validates structure, short references and coverage after each DRAFT/REVIEW pair.
- Ran the six direct activity/provider tests: 6 tests passed. The Codex subscription command boundary is replaced by a test double; no live Provider call occurred.

## Current state

- The local-activity scripted checkpoint is green. A live Luna/high quality sample remains an explicitly authorized, separately observable product action rather than an automatic test action.

## Changed files

- `progress/activity-explanation-scripted-checkpoint.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| activity/provider implementation inspection | PASS | Activity explanation has a narrow injected Provider boundary and no Java business dictionary. |
| `ActivityExplainerTest`, `ActivityExplanationBudgetTest`, `ActivityExplanationCheckpointTest`, `ActivityOutputSchemaTest`, `ActivityPromptContractTest`, `CodexSubscriptionStructuredProviderTest` | PASS | 6 tests; clean packets, DRAFT/REVIEW semantics, response schema, checkpoint persistence and command-boundary replacement are green. |

## Decisions

- A real Luna/high small-packet quality run remains a separate product-model action. It must follow, not substitute for, the direct scripted seam verification.

## Blockers

- None for the scripted checkpoint.

## Exact next action

- Hand off to the cross-entry ProcessExplainer scripted checkpoint. Keep the live Luna/high sample separate and inspect its exact small input before its invocation.

## Resume checks

- Verify test providers are in-memory/scripted and do not run `CodexSubscriptionStructuredProvider`'s process command.
