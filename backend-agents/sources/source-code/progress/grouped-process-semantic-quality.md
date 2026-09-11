# Progress: grouped process semantic quality

- Status: IN_PROGRESS
- Agent role: primary implementation and quality-validation agent
- Model: `gpt-5.6-sol / ultra` for approved architecture; `gpt-5.6-luna / high` only for one bounded process DRAFT plus complete REVIEW after offline preparation
- Started: 2026-09-11T19:31:00Z
- Last updated: 2026-09-11T21:55:00Z
- Scope: Reuse the terminal account-balance activity result from the grouped-material quality checkpoint and its original material, without replaying ActivityExplainer. Verify or minimally extend the existing process quality harness, then run exactly one Luna/high ProcessExplainer DRAFT plus complete REVIEW. A valid result may preserve two independent activities; it must not invent execution order, business roles, report formulas or shared identity.
- Approved inputs: User authorization for Luna/high business/process interpretation; fixed commit `8c30ce7861570458920175e200bb2a6442713580`; saved account-balance grouped activity output and material plan.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- The predecessor grouped activity quality checkpoint is complete: two actual account-query activities have saved entry-level coverage and no candidate may be replayed.
- The grouped-material input reader is covered by a direct RED→GREEN test and can preserve two
  entry-level activities from one material package without a Provider activity call.
- The account process attempt passed all local preflight checks and reached the live DRAFT. Its
  response was rejected as `PROCESS_GROUP_DRAFT_INVALID`, so REVIEW did not start and no process
  file was saved. This started candidate is terminal and will not be replayed.
- Process DRAFT/REVIEW instructions now explicitly name the exact complete JSON members and state
  the safe alternatives for unrelated activities. This was driven by a focused RED→GREEN contract
  test; the clean-input test continues to prove that model input omits local paths and technical
  identity fields.
- The persisted, scripted production workflow is GREEN again: grouped material → complete activity
  DRAFT/REVIEW → process DRAFT/REVIEW → whole-report DRAFT/REVIEW → durable nine-section document.
  Its obsolete scripted fixture was corrected to cover each packet's entry keys without exceeding
  its configured two-activity bound.
- The next opt-in live-process harness writes each clean model request before transport and each
  returned response before semantic validation. This is test-only diagnostic capture in its ignored
  workspace; it does not replay the terminal account-balance candidate.

## Current state

- The saved account-balance group contains two reviewed activities backed by one material packet.
  The existing live-process fixture selects two separate registration/login materials, so the next
  small RED verified that the loader can preserve one grouped material and both covered activities
  without replaying activity calls. The process prompt now names its required JSON members and the
  safe representation of unrelated activities. The existing runtime already composes materials →
  activities → processes → report; the next direct scripted verification checks that this complete
  content hand-off still holds after the prompt change. The first direct run exposed one obsolete
  scripted test response: it hard-coded only E1 after material grouping became multi-entry. The
  replacement fixture now splits the grouped packet's entry-key set across at most two activities,
  matching the production response contract and retaining a meaningful ProcessExplainer hand-off.
  The second RED identified a fixture-local array replacement: only the last key survived; the
  array is now created once before all keys are added.

## Changed files

- progress/grouped-process-semantic-quality.md
- src/test/java/org/sourceanalysis/app/analysis/knowledge/LiveLunaAutomaticUserLifecycleProcessIT.java
- src/test/java/org/sourceanalysis/app/analysis/knowledge/LiveLunaAutomaticAccountBalanceProcessIT.java
- src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java
- src/main/resources/org/sourceanalysis/app/analysis/knowledge/process-group-draft-v1.txt
- src/main/resources/org/sourceanalysis/app/analysis/knowledge/process-group-review-v1.txt
- src/test/java/org/sourceanalysis/app/analysis/knowledge/ProcessPromptContractTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| process harness inspection | PENDING | Must show that activity results and original clean material are reused without a new Activity Provider call. |
| `mvn -Dtest=LiveLunaAutomaticUserLifecycleProcessIT test` | RED | 3 tests; new test reaches `LIVE_LUNA_PROCESS_REQUIRED_MATERIALS_MISSING`, proving the old test-only loader rejected one grouped material. |
| `mvn -Dtest=LiveLunaAutomaticUserLifecycleProcessIT test` | PASS | 3 tests, 0 failures/errors, 1 opt-in test skipped; grouped material and two activity coverage now load without Provider activity calls. |
| `mvn -Dtest=LiveLunaAutomaticAccountBalanceProcessIT test` | PASS | 1 opt-in test compiled successfully and was skipped without its explicit property; no Provider call. |
| `mvn -Dtest=LiveLunaAutomaticAccountBalanceProcessIT …liveLunaAccountBalanceProcess=true test` | PRE-FLIGHT FAILED | Output directory did not exist; failure occurred before `ProcessExplainer` or any Provider call. |
| `mvn -Dtest=LiveLunaAutomaticAccountBalanceProcessIT …liveLunaAccountBalanceProcess=true test` | TERMINAL | 30.33s; live DRAFT response reached `PROCESS_GROUP_DRAFT_INVALID`; no REVIEW/provider retry; no process output was saved. |
| `mvn -Dtest=ProcessPromptContractTest,ProcessMaterialRecallTest test` | PASS | 2 tests, 0 failures/errors; exact process JSON instructions and clean model input contract hold. |
| `mvn -Dtest=PersistedBusinessRunExecutorTest test` | RUNNING | Direct scripted material→activity→process→nine-section report verification; no live Provider. |
| `mvn -Dtest=PersistedBusinessRunExecutorTest test` | RED | First run reached `ACTIVITY_DRAFT_INVALID`: the old scripted provider emitted only E1 for a grouped material. |
| `mvn -Dtest=PersistedBusinessRunExecutorTest test` | RED | First correction emitted more activities than the legacy test profile permits. This confirmed production checks both complete entry coverage and material activity bounds. |
| `mvn -Dtest=PersistedBusinessRunExecutorTest test` | RED | The one-activity correction created a new `entryKeys` array on each loop iteration, retaining only the last key; production correctly rejected incomplete coverage. |
| `mvn -Dtest=PersistedBusinessRunExecutorTest test` | RED | After complete coverage, the former test expected two materials but the grouped builder correctly produces one. The test now preserves two local activities within its existing two-activity bound to exercise process hand-off. |
| `mvn -Dtest=PersistedBusinessRunExecutorTest test` | PASS | 2 tests, 0 failures/errors; material → activity → process → report hand-off and nine H2 sections are durable under the grouped-material contract. |
| `mvn -Dtest=LiveLunaAutomaticAccountBalanceProcessIT test` | RUNNING | Compile the diagnostic-writing live harness with its property absent; no Provider call. |
| `mvn -Dtest=LiveLunaAutomaticAccountBalanceProcessIT test` | PASS | 1 test, 0 failures/errors, 1 opt-in test skipped; the request/response diagnostic wrapper compiles and does not invoke a Provider without its explicit live property. |
| direct grouped activity/process/report selector | PASS | 8 tests, 0 failures/errors, 2 explicit live tests skipped; grouped-material prompt, clean recall, persisted complete business chain and diagnostic harness hold together. |

## Decisions

- A process is a model interpretation over bounded activities, not a Java-derived ordering claim.
- No new process is required if the material supports only separate activities; full coverage with independent-process conclusions is acceptable.
- This test harness must save each future process request/response in its ignored workspace before
  `ProcessExplainer` validates it, so a schema rejection can be diagnosed without a second model call.

## Blockers

- None known.

## Exact next action

- Select a distinct, unstarted automatic two-activity packet; inspect its complete saved material
  and use the opt-in diagnostic harness only after its input is acceptable. Do not replay the
  terminal account-balance request.

## Resume checks

- Read this file; confirm the account-balance activity result exists and contains two terminal reviewed activities before any ProcessExplainer work.
