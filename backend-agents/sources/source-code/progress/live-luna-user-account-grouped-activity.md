# Progress: live Luna user account grouped activity

- Status: BLOCKED
- Agent role: primary implementation and bounded semantic-quality validation
- Model: `gpt-5.6-luna / high` for one explicitly approved product activity DRAFT and REVIEW
- Started: 2026-09-11T21:58:00Z
- Last updated: 2026-09-11T22:35:00Z
- Scope: Add test-only request/response diagnostic capture to the existing automatic-material live harness, then execute one unstarted current-plan user-account material package. It must not replay a prior candidate.
- Approved inputs: Fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; saved automatic material plan; user authorization for Luna/high local business interpretation.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Inspected the saved current-plan material `material:8be00d…`: it includes four independently discovered user-account endpoints and short source snippets for delete, session retrieval, registration and logout.
- Confirmed it has not been used by the prior saved activity candidates; those candidates used earlier material identities.
- The one user-account DRAFT started, returned, and was rejected as `ACTIVITY_DRAFT_INVALID`.
  It covered E1/E2 but not E3/E4, while the same packet instructed separate explanations and
  its profile admitted only two activities. REVIEW did not start and the candidate is terminal.

## Current state

- The existing live activity harness saves only accepted reviewed activities. It must persist the clean request before transport and returned response before `ActivityExplainer` validation, matching the process diagnostic harness, before this new candidate is started.
- The harness now wraps its Provider in a test-only recorder. It writes the clean DRAFT/REVIEW
  request before transport and the raw returned JSON before ActivityExplainer validates it.
- `mvn test-compile` has compiled the changed test sources without running a model or test.
- The user-account diagnostic directory now contains the exact clean DRAFT input and returned
  response. This made the coverage/profile contradiction observable without a retry.
- A two-entry serial-number package is selected for a new candidate: it is a smaller, unstarted
  material with a compatible two-activity profile and code excerpts for a serial-number lookup and
  batch addition. The named selector is now GREEN.
- The first serial-number launch stopped before material selection because the test selector used
  the wrong fallback-context marker. No request file or Provider call occurred. The exact saved
  context was checked and the selector now uses the stable route fragment; its direct test passes.
- The corrected serial-number launch was rejected by the external-execution safety control before
  process creation. No source packet, request, response or credentials left the workstation, and
  this candidate remains unstarted.

## Changed files

- progress/live-luna-user-account-grouped-activity.md
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaAutomaticMaterialIT.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/LiveLunaAutomaticMaterialSelectionTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| saved material inspection | PASS | Four current-plan user-account entry contexts; no previous candidate identity reused. |
| `mvn test-compile` | PASS | Main and 149 test sources compiled; no test or Provider call. |
| one bounded user-account activity candidate | TERMINAL | 28.27s; saved DRAFT omitted E3/E4 and failed `ACTIVITY_DRAFT_INVALID`; REVIEW did not start and no retry is allowed. |
| `mvn -Dtest=LiveLunaAutomaticMaterialSelectionTest test` | RED → PASS | The expected unsupported sample error was followed by 1 passing selection test; no Provider call. |
| first serial-number launch | PRE-FLIGHT FAILED | Named marker did not match the saved fallback context; no input file or Provider call was made. |
| `mvn -Dtest=LiveLunaAutomaticMaterialSelectionTest test` | PASS | 1 test; the corrected marker selects the saved two-entry material without a Provider call. |
| corrected serial-number activity launch | BLOCKED | External safety control rejected sending this source packet to Luna; no Provider call or diagnostic files were created. |

## Decisions

- The model may return separate activities or conservative uncertainty. The test will not require an invented serial-number sequence.
- The diagnostic files stay under an ignored `.workspace/` directory and contain the already-approved clean model packet, not hashes, host paths or credentials.
- The four-entry grouping/profile mismatch is a design and implementation review finding, not a
  reason to replay the terminal user-account candidate. The bounded new candidate uses two entries
  to validate the current process module before that general grouping policy is changed.

## Blockers

- A fresh exact authorization is required to send the selected two-entry jshERP serial-number
  material to the logged-in Luna session. The terminal user-account candidate cannot be reused.

## Exact next action

- Review the current code/design. If the user explicitly reauthorizes this exact source packet for
  the logged-in Luna session, run one DRAFT plus one REVIEW, then create the matching bounded
  process harness; otherwise do not send it.

## Resume checks

- Confirm the saved material ID is `material:8be00d5562743218931b721c547d915076a08b7200bc06e415d1248c5ea663eb` and no model call has started for it.
