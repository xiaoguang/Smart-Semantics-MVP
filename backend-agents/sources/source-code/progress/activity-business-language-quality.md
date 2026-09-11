# Progress: Activity business-language quality

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-luna / xhigh for targeted prompt-contract test; no additional product Provider call
- Started: 2026-09-11 17:14 UTC
- Last updated: 2026-09-11 17:19 UTC
- Scope: Use the completed real `POST /user/registerUser` DRAFT+REVIEW sample to make a narrow prompt-contract correction: when business text names an HTTP trigger, it must reproduce the exact packet trigger; it should prefer human business terms over Java type/variable names when the snippet supplies enough meaning. This changes no source analysis, evidence mapping, schema, Provider policy, or existing live output.
- Approved inputs: Current business-first Step06 design; the successful user-registration sample; existing versioned activity prompt resources.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Inspected the successful user-registration DRAFT+REVIEW output. It correctly identifies the registration sequence and refuses to claim persistence or runtime success, but it shortened the exact trigger from `/user/registerUser` to `/registerUser` and exposed Java identifiers such as `UserEx` and `ue` in business-object text.
- Added a focused prompt-resource RED requiring both DRAFT and REVIEW instructions to require the complete HTTP method/path and business-first terminology.
- Revised only the versioned Chinese Prompt resources and their human-readable contract. DRAFT/REVIEW now forbid truncating, rewriting or inventing a named HTTP path, and require business or neutral natural language unless a Java identifier is necessary to explain a code boundary.
- Re-ran the prompt selector: green with no product Provider call.

## Current state

- The prompt-quality correction is complete. The already completed user-registration candidate remains an immutable diagnostic sample; a future different-domain product task must use the updated prompt and its own authorization.

## Changed files

- progress/activity-business-language-quality.md
- src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-draft-v1.txt
- src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-review-v1.txt
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPromptContractTest.java
- docs/analysis-steps/06-flow-interpretation.md
- docs/references/semantic-interpretation-prompts.md
- docs/DESIGN.md
- README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| approved `LiveLunaAutomaticMaterialIT` for `automatic-user-registration` | PASS | One real Luna/high DRAFT+REVIEW completed in 55.51 seconds; output is saved only in `.workspace`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ActivityPromptContractTest test` before resource change | RED | 1 expected assertion failure: neither prompt required exact trigger preservation or business-first terminology. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ActivityPromptContractTest test` after resource change | PASS | 1 test, 0 failures/errors/skips; no product Provider call. |

## Decisions

- The response is useful enough to validate the chain: it states a registration purpose, ordered validation/registration calls, and limitations. The exact-path and Java-identifier issues are prompt-quality defects, not reasons to rebuild Steps01–05 or retry the completed candidate.

## Blockers

- None for the prompt-contract correction. A new real model candidate would need separate authorization.

## Exact next action

- Complete. Next work is separately scoped process/repository-document integration, not another call for this candidate.

## Resume checks

- Read this file; do not rerun the completed user-registration product candidate as part of this correction.
