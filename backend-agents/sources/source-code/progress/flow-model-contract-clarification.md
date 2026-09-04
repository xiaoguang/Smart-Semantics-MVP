# Progress: Flow model contract clarification

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Publish one bounded Step 06 clarification before R1/R2 implementation: its task compiler receives an immutable profile projected from an already-admitted `analysis-run-request-v2`, rather than accepting caller-chosen model controls. No Java, tests, schemas, or runtime behavior changes.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, and the FlowInterpretation implementation plan.
- Current branch/worktree: `codex/source-analysis-flow-model-contract` at `/private/tmp/linguan-source-analysis-flow-model-contract/backend-agents/sources/source-code`

## Completed

- Verified that the current runtime core does not yet expose an `AnalysisRunRequestReference` to M4, while M1 already uses a documented immutable projection pattern.

## Current state

The clarification now makes the M4 input rule explicit without weakening frozen-request provenance: only the core may construct `FlowModelTaskProfile` after exact request admission; the profile carries both R1/R2 prompt/schema/budget/runtime references and limits.

## Changed files

- `progress/flow-model-contract-clarification.md`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n "AnalysisRunRequest|requestV2" src/main/java docs/analysis-steps/06-flow-interpretation.md` | PASS | The request schema is admitted in inventory, but no Step 06 runtime request reference exists yet. |
| `git diff --check` | PASS | The docs-only clarification has no whitespace errors. |

## Decisions

- This is a local Step 06 implementation seam, not a new public API or a change to the eight-step workflow.

## Blockers

- None.

## Exact next action

Committed and pushed this docs-only clarification to `main` before M4 implementation begins.

## Resume checks

- Confirm the only non-progress change is the M4 contract clarification in `docs/analysis-steps/06-flow-interpretation.md`.
