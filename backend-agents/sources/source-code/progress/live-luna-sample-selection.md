# Progress: Live Luna sample selection

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-luna / xhigh for test design; no product Provider call in this change
- Started: 2026-09-11 16:24 UTC
- Last updated: 2026-09-11 15:44 UTC
- Scope: Make the explicitly opt-in live ActivityExplainer harness select one named, persisted automatic material package. This permits a second distinct sample after the terminal local-state failure of the DepotHead sample. It does not change production analysis behavior, material content, Provider policy or retry rules.
- Approved inputs: User-approved bounded Luna/high quality checks; fixed material JSONL; existing live test harness.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Inspected the persisted `POST /user/registerUser` `FLOW_PREFERRED` material. It contains one complete controller snippet and short refs for login-name normalization, captcha verification, login-name validation and user registration; it has no technical Flow gap.
- Added an offline named-material selector to the explicit live test harness. It accepts only `automatic-depothead` and `automatic-user-registration`, searches the already saved material JSONL by the expected technical observation, and writes the ignored output under the named sample key.
- Added and ran an offline selection test. It first failed because the harness had no named selector; after the minimum harness change it passes without starting a Provider.
- Prepared the one declared `automatic-user-registration` DRAFT+REVIEW invocation. The user has now explicitly approved transmission of this exact material to the logged-in Luna/high Codex subscription.
- The first approved invocation reached the local selector but stopped before the Provider because the selector incorrectly searched a synthetic controller-name observation not present in the saved production packet. No packet was sent. The saved packet's stable `modelPacket.context` contains its HTTP trigger, so the selector is being corrected to use that persisted trigger.
- Corrected the selector to use the exact persisted HTTP trigger and reran the same approved request. One real Luna/high DRAFT plus one complete REVIEW completed successfully in 55.51 seconds. The output has one reviewed local activity, valid short refs, and `ANALYZED` coverage; it is stored only under the ignored `.workspace` output directory.
- Identified the next bounded, distinct automatic sample: `POST /user/login`, a `FLOW_PREFERRED` material with 13 short refs and only the generic bounded-snippet notice. It will check the prompt correction on another local user-lifecycle activity before any cross-activity process call.
- Extended the named selector to that `POST /user/login` material and completed exactly one Luna/high DRAFT plus one complete REVIEW. The run passed in 70.05 seconds and persisted one `ANALYZED` activity under the ignored `live-luna-automatic-user-login-v1` output. It preserved the full `HTTP POST /user/login` trigger, used natural-language business objects, retained the captcha and exception branches, and did not claim a successful login occurred.

## Current state

- The two distinct automatic local-activity checks are complete. Neither earlier candidate will be replayed. The next separately bounded product task is cross-activity process reconstruction using these persisted reviewed activities.

## Changed files

- progress/live-luna-sample-selection.md
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaAutomaticMaterialSelectionTest.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaAutomaticMaterialIT.java
- docs/analysis-steps/06-flow-interpretation.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| persisted material inspection | PASS | `automatic-user-registration` candidate is `FLOW_PREFERRED`, 9 short refs, one explicit budget limitation. |
| `LiveLunaAutomaticMaterialSelectionTest` before production change | RED | The desired named-selection method did not exist. |
| `mvn -o -t .mvn/toolchains.xml -DargLine='-Xmx8g …' -Dtest=LiveLunaAutomaticMaterialSelectionTest test` | PASS | 1 test, 0 failures/errors/skips; selection happens from canonical saved JSONL with zero Provider calls. |
| escalated named user-registration live invocation | BLOCKED_BEFORE_START | Environment requires explicit user approval to transmit this exact source-derived packet to Luna/Codex; no request was sent. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LiveLunaAutomaticMaterialSelectionTest,ProcessCodexSubscriptionCommandTest,BusinessReportCheckpointTest test` | PASS | 5 tests, 0 failures/errors/skips; named selection, subprocess protocol and persisted report-chain behavior remain green. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply` then `spotless:check` | PASS | Formatted six current-branch Java files; all 591 Java files are clean. |
| `git diff --check` | PASS | No whitespace errors. |
| first approved user-registration Maven invocation | STOPPED_BEFORE_PROVIDER | `LIVE_LUNA_SAMPLE_MATERIAL_MISSING`; it exposed a test fixture / persisted-packet selector mismatch and did not transmit a model packet. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LiveLunaAutomaticMaterialSelectionTest test` after persisted-trigger correction | PASS | 1 test, 0 failures/errors/skips; the selector uses the HTTP trigger held in the saved model packet. |
| approved `LiveLunaAutomaticMaterialIT` for `automatic-user-registration` after selector correction | PASS | 1 test, 0 failures/errors/skips; one real Luna/high DRAFT+REVIEW completed in 55.51 seconds. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=LiveLunaAutomaticMaterialSelectionTest test` after user-login selection | PASS | 1 test, 0 failures/errors/skips; the second selector reads only canonical persisted material. |
| approved `LiveLunaAutomaticMaterialIT` for `automatic-user-login` | PASS | 1 test, 0 failures/errors/skips; one real Luna/high DRAFT+REVIEW completed in 70.05 seconds. |

## Decisions

- The new sample is distinct from failed DepotHead request. It is a local user-registration activity, not an industry-specific business rule added to Java.

## Blockers

- None.

## Exact next action

- Preserve this completed activity-sample record. A new process-specific progress record owns any process reconstruction work.

## Resume checks

- Read this file, confirm the DepotHead request remains failed without retry, and use the persisted registration/login activity outputs rather than replaying either Activity DRAFT/REVIEW.
