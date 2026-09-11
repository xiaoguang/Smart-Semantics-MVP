# Progress: Live Luna user lifecycle process

- Status: COMPLETE
- Agent role: Primary implementation and quality-validation agent
- Model: gpt-5.6-luna / high for one explicitly authorized process DRAFT and one complete REVIEW; no production-code model call in this task
- Started: 2026-09-11 15:44 UTC
- Last updated: 2026-09-11 15:44 UTC
- Scope: Reconstruct one bounded cross-entry business-process hypothesis from two already persisted, real customer-source local activities: user registration and user login. The task must reuse the completed activity outputs and must not replay either Activity DRAFT or REVIEW.
- Approved inputs: User-approved budget and live Luna/high use; fixed commit `8c30ce7861570458920175e200bb2a6442713580`; two ignored local reviewed-activity outputs; their canonical saved business-material JSONL.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Confirmed the two local activity outputs are real and terminal: registration completed in 55.51 seconds; login completed in 70.05 seconds.
- Confirmed their original persisted business materials have a safe shared technical recall cue (the same frozen controller source file). This only creates a model reading group; it does not prove an execution order or a single business process.
- Confirmed `ProcessExplainer` already accepts reviewed activities plus their material set, performs loose programmatic recall, and sends exactly one `PROCESS_GROUP_DRAFT` followed by one full `PROCESS_GROUP_REVIEW` for an eligible group.
- Added a property-gated test-only harness and first ran its zero-Provider parsing/material-recall check successfully.
- Completed exactly one real Luna/high process DRAFT plus complete REVIEW in 70.94 seconds. The ignored output is `live-luna-automatic-user-lifecycle-process-v1/live-luna-automatic-user-lifecycle-process.json`.
- The model did not invent a registration-before-login process. It returned two complete, direct-code local processes, one for registration and one for login, with confirmation notes. This is an accepted result because the common Controller/verification cues only supported recall, not a business handoff.
- Corrected the live-harness assertion that had incorrectly demanded one two-activity process. The corrected assertion requires complete handling of both activities and accepts the model's conservative independent-process result; it does not replay the started process task.

## Current state

- The bounded real process task is closed. Its source activities and its process output remain ignored diagnostics; no activity or process request will be replayed.

## Changed files

- progress/live-luna-user-lifecycle-process.md
- src/test/java/org/sourceanalysis/app/analysis/knowledge/LiveLunaAutomaticUserLifecycleProcessIT.java
- docs/analysis-steps/07-repository-knowledge.md
- docs/DESIGN.md
- README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| persisted user activity inspection | PASS | Registration and login are both `ANALYZED`; their activity details, source refs and coverage are available without new Provider calls. |
| `ProcessExplainer` source inspection | PASS | A group with at least two reviewed activities calls one DRAFT then one REVIEW; shared source file is a recall cue only. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LiveLunaAutomaticUserLifecycleProcessIT#readsPersistedActivitiesAndTheirOriginalMaterialsWithoutCallingAProvider test` | PASS | 1 test, 0 failures/errors/skips; two saved activity outputs and their original materials reconstruct without any Provider call. |
| approved `LiveLunaAutomaticUserLifecycleProcessIT` | COMPLETE_WITH_CONSERVATIVE_RESULT | One real Luna/high DRAFT+REVIEW completed in 70.94 seconds. It returned two independent local processes, not a fabricated cross-entry sequence. The output was saved before the harness's initially incorrect cardinality assertion failed. |
| zero-Provider recompile after the assertion correction | PASS | 1 parsing test, 0 failures/errors/skips; no live request was replayed. |

## Decisions

- A real input group may yield one, several, or no cross-entry process; the quality condition is complete, truthful handling rather than a forced merge. This sample yielded two separate `DIRECT_CODE_BEHAVIOR` processes and no unmatched activity.
- The older registration activity predates the route/business-language prompt correction. It is retained unchanged as a completed candidate; this process task may narrow or flag its route wording but must not replay or overwrite it.

## Blockers

- None.

## Exact next action

- Preserve this completed process-quality record. Continue the approved implementation plan with the Step05-to-material complete-context work; choose a later real cross-entry group only when its material has an actual handoff cue.

## Resume checks

- Confirm the two input activities and process output remain ignored diagnostics. Do not rerun the completed registration, login, or process model tasks.
