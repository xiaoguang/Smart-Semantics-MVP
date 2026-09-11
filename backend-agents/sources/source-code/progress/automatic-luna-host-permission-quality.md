# Progress: automatic Luna host-permission quality check

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-luna / high
- Started: 2026-09-11 04:09 NDT
- Last updated: 2026-09-11 06:51 NDT
- Scope: Run exactly one previously inspected automatic DepotHead material package through the production ActivityExplainer under the narrowly required host permission for the existing Codex login session.
- Approved inputs: current user instruction to execute the plan; prior explicit Luna/high authorization; fixed commit `8c30ce7861570458920175e200bb2a6442713580`; persisted automatic material packet at `.workspace/automatic-material-validation-1789107664/.../business-materials.jsonl`
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Confirmed the previous non-elevated task could not identify its failure after the adapter discarded private command output.
- Confirmed documented host root cause from prior successful live samples: the Codex CLI needs permission to update its own local session-state runtime; it does not need customer repository write access.
- Ran one no-customer Luna/high connectivity diagnostic under host permission; it returned `READY`.
- Ran one no-customer structured-output diagnostic under host permission; its schema and `--output-last-message` result completed with `{"ok":"READY"}`.
- Repeated that no-customer structured-output diagnostic from a freshly created temporary working directory, matching the production adapter's working-directory shape. It also completed with `{"ok":"READY"}`.

## Current state

- The one automatic DepotHead task remains terminally failed before DRAFT JSON. The generic diagnostics prove that Luna/high, the logged-in session, read-only sandbox, structured-output flags and an empty temporary working directory work when directly invoked with host permission. The remaining failure boundary is Java/Maven's child-process execution of Codex in this environment; it is not a semantic-quality result or a temporary-working-directory defect.

## Changed files

- `progress/automatic-luna-host-permission-quality.md`
- `.workspace/codex-provider-diagnostics/ready-schema.json` (ignored generic diagnostic input)
- `.workspace/codex-provider-diagnostics/ready-output.json` (ignored generic diagnostic output)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Existing non-elevated automatic task | FAILED before structured output | `ACTIVITY_PROVIDER_FAILED_AFTER_START`; no output or REVIEW. |
| Existing elevated manual Luna samples | PASS | The same Codex Subscription profile completed DRAFT+REVIEW for two fixed-source small packets. |
| Elevated automatic DepotHead task | FAILED before structured output | 1 test, 1 error: `ACTIVITY_PROVIDER_FAILED_AFTER_START` caused by `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`; no DRAFT JSON, REVIEW, or output file. |
| Direct no-customer Luna/high check | PASS | Returned `READY` with the same model and read-only sandbox. |
| Direct no-customer structured-output check | PASS | Returned and wrote schema-valid `{"ok":"READY"}`. |
| Direct temporary-working-directory structured-output check | PASS | Returned schema-valid `{"ok":"READY"}`; the adapter's temporary working directory is not the observed cause. |

## Decisions

- Customer source remains read-only and the Provider sandbox remains read-only.
- Do not infer automatic-material quality from a Java-child-process failure. Do not perform another Provider diagnostic or resend the DepotHead packet in this work unit.

## Blockers

- Product-quality acceptance of automatic materials remains unavailable in this Codex execution environment until the Java-child-process boundary can be run on a suitable host. This does not block zero-provider code work.

## Exact next action

- Continue zero-provider business-module implementation. Any future automatic live package must be new, explicitly authorized and executed in an environment where the Java Provider boundary is demonstrably usable.

## Resume checks

- Do not change the material packet, limits, model, or Provider. Verify no pre-existing live result file is overwritten before the one execution.
