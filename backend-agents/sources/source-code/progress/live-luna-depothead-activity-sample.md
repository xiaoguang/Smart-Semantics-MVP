# Progress: live Luna DepotHead activity sample

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-luna / high for the explicitly authorized product call; gpt-5.6-terra / xhigh for the opt-in harness
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Run one explicitly opted-in, small, real Luna/high local-activity explanation against two read-only DepotHead Java excerpts from fixed commit `8c30ce7861570458920175e200bb2a6442713580`. Persist only the clean input, reviewed JSON and program-side source map in ignored workspace output.
- Approved inputs: User's prior explicit Luna/high authorization; logged-in local Codex subscription; Controller lines 172–191 and Service lines 742–814 of the fixed complete local Git commit.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the local Codex executable is logged in with the existing ChatGPT session.
- Read exactly the two selected frozen source excerpts via Git object access; no customer build, application, script or network source capture was run.
- Confirmed the existing ActivityExplainer scripted boundary is green before attempting a live call.
- Added a gated `LiveLunaDepotHeadActivityIT` harness. It calls the production ActivityExplainer and Subscription Provider, creates the dynamic response schema in production code, and writes only canonical JSON output below an explicitly supplied ignored workspace directory.

## Current state

- The gated harness compiles (126 test sources) and passes its scoped Spotless check.
- The first explicit live run started the Provider boundary but failed during its first DRAFT command with `CODEX_SUBSCRIPTION_EXECUTION_FAILED`; no response JSON or reviewed activity was produced, and no second REVIEW was attempted.
- Local diagnostic confirmed the Codex CLI supports the requested flags, but the workspace sandbox denies its required local session-state write (`~/.codex/state_5.sqlite`) and in-process app-server initialization. The model was not reached; this is a host permission boundary, not a packet/schema failure.
- The same trivial schema/prompt diagnostic succeeded once with narrowly scoped host permission. It used `gpt-5.6-luna` at `high` and returned the expected JSON, proving the Subscription session and command settings are valid.
- The first escalated harness invocation did not reach Maven test execution because the elevated command started at the repository root rather than the module directory and found no `pom.xml`. It sent no customer packet and made no model call.
- The corrected escalated invocation used the module's absolute POM path and completed both the DRAFT and REVIEW calls in 118.1 seconds. The scoped test passed (1 test, 0 failures/errors/skips) and wrote `live-luna-depothead-activity-sample.json` below the ignored workspace directory.
- The reviewed result is a useful business explanation rather than a method-name translation: it identifies batch audit/reversal purpose, the eligible-document decision, configuration-dependent inventory check, status update call, subsequent inventory/log branches, result mapping, and concrete unanswered operating questions. Its coverage is honestly `ANALYZED_WITH_GAPS` because only two excerpts were supplied.
- Added a second, separately opt-in `account-head` sample to the same gated harness. It reads only the frozen financial-document controller/service excerpts and is designed to test whether the same prompt and response contract transfers to a different domain without adding Java business rules. It has not sent this second packet yet.
- The `account-head` packet completed DRAFT and REVIEW in 93.52 seconds (1 test, 0 failures/errors/skips). It produced a distinct, usable financial-document activity: duplicate bill-number and transfer-account checks, creator/default-status handling, header/detail save calls, a conditional advance-payment update and logging. It retained source limitations instead of claiming payment, bookkeeping or persistence success.

## Changed files

- `progress/live-luna-depothead-activity-sample.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaDepotHeadActivityIT.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| local Codex login preflight | PASS | Logged in using the existing ChatGPT session. |
| frozen-source excerpt inspection | PASS | Controller exposes `POST /batchSetStatus`; service reads IDs/status, applies status/configuration checks, chooses eligible IDs and invokes the Mapper update. |
| `mvn ... test-compile` | PASS | 126 test sources compile, including the gated live sample. |
| scoped Spotless check | PASS | The live sample test meets the configured Java format. |
| opt-in `LiveLunaDepotHeadActivityIT` | BLOCKED_AT_PROVIDER_COMMAND | The production Subscription Provider preflighted successfully, then its first `codex exec` invocation returned nonzero/no output. No model result was saved. |
| direct trivial `codex exec` diagnostic | BLOCKED_AT_HOST_SESSION_STATE | CLI reports `attempt to write a readonly database` for local Codex state and `in-process app-server client: Operation not permitted`; it did not reach a model request. |
| escalated trivial `codex exec` diagnostic | PASS | Luna/high returned schema-valid `{"ok":"ready"}`. The command/session path is usable when its own local state runtime has host permission. |
| escalated opt-in `LiveLunaDepotHeadActivityIT` attempt | NOT_EXECUTED | Elevated Maven started outside the module and failed before test discovery (`MissingProjectException`); no model call occurred. |
| corrected escalated opt-in `LiveLunaDepotHeadActivityIT` | PASS | 1 test, 0 failures/errors/skips; Luna/high completed DRAFT and REVIEW in 118.1 seconds and persisted the reviewed activity JSON. |
| `mvn ... -Dtest=LiveLunaDepotHeadActivityIT test-compile` | PASS | 126 test sources compile after adding the separately gated financial-document sample. |
| repository-wide `spotless:check` | PRE-EXISTING_FAILURE | The check reaches 42 existing Java formatting violations outside this harness; it does not establish a defect in the changed file and is deferred to the already planned formatting cleanup. |
| corrected escalated `account-head` opt-in sample | PASS | 1 test, 0 failures/errors/skips; Luna/high completed DRAFT and REVIEW in 93.52 seconds and persisted the reviewed activity JSON. |

## Decisions

- The model packet will expose only a Chinese technical context, observations, `S1`/`S2` and excerpts. File paths, lines, commit, hashes and proof information remain in the program-side JSON source map.
- This is a quality sample, not a full-repository result. It will not be used to claim all jshERP entries or the full nine-section document are complete.
- Do not retry the model while the command-level failure is opaque. First determine whether the installed Codex executable supports the exact `exec` flags and model settings used by the adapter.
- The exact CLI command and model settings are supported. The remaining blocker is sandbox access to the user's existing local Codex state runtime; retry only under the necessary host permission, with the same trivial diagnostic before resending the customer-derived packet.
- The known minimal host permission is sufficient. The completed customer-derived sample remained limited to the already reviewed two-excerpt packet and two ActivityExplainer rounds.

## Blockers

- None.

## Exact next action

- Keep both activity packets as small-model quality examples. The next semantic validation is a separately bounded cross-activity process task; it must not treat these two unrelated activities as a process merely because they share a repository.

## Resume checks

- Confirm the test only runs when all live-Luna properties are explicitly set and writes output only below the requested ignored workspace directory.
