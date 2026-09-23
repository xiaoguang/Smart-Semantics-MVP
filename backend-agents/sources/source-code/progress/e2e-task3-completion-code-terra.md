# Progress: e2e-task3-completion-code-terra

- Status: IN_PROGRESS
- Agent role: Task 3 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-23
- Last updated: 2026-09-23
- Scope: Typed Activity packet completion v4 and formal offline `--reuse-only` continuation only; no test edits, commits, upstream calls, Task 4 audit, or Step07 algorithm expansion.
- Owning plan: `docs/plans/end-to-end-business-delivery-implementation-plan.md`, Task 3; task brief `.workspace/end-to-end-business-delivery-20260923/task-3-brief.md`
- Approved inputs: Task 3 brief; root API ruling dated 2026-09-23; source-scoped AGENTS; shared test owner will establish RED.
- Current branch/worktree: formal `backend-agents/sources/source-code` checkout; baseline `caa9d56` reported by root.

## Completed

- Read scoped instructions, the Task 3 brief, and governing TDD/implementation workflow.
- Proposed the initial bounded vertical and offline branch boundary to root and the test owner.
- Root approved the `Optional<List<ActivityPacketCompletion>>` historical-presence seam and the six-field completion value type.
- Implemented the first RED→GREEN vertical: both formal CLI parsers accept the literal standalone `--reuse-only` flag and reject invalid reuse-only combinations before a model path.
- Root verified the parser GREEN: session `root85694`, 8 targeted tests passed in about one minute.
- Implemented the typed completion/result vertical against root session `root35638`'s observed missing-symbol RED; root session `7802` then passed its two targeted tests (Luna added a third overlap case after that run).

## Current state

- A recognized reuse-only request now fails explicitly before `requireModelJobsForExecution`, so it cannot silently take the online Provider path while the offline resolver is still absent.
- Root approved `ActivityPacketCompletion` with exactly six public fields: `packetId`, `entryIds`, `completion`, `requiredSliceKeys`, `completedSliceKeys`, `incompleteScopes`.
- Public absence of a v4 completion remains distinguishable from a valid empty collection; saved current scope issues map to `incompleteScopes`.
- The current Step05 publisher will require explicit completion and write M11 v4; v3 is strict reader/test-fixture history only, not a new default writer.
- Root session `69627` observed the formal configured full-CLI behavioral RED: the valid fixture
  reached `ACTIVITY_REUSE_ONLY_OFFLINE_RESOLVER_REQUIRED` instead of completing. Implemented the
  first formal publication vertical and left it source-frozen for root's targeted Maven run.
- The formal branch uses only storage validation, saved Step05 state/source material, the selected
  stopped batch's persisted v5 declaration/output/M11, and the existing provider-free historical
  resolver facade. It creates no Provider, authentication lookup, live capacity, or current prompt
  reconstruction.
- It writes a non-secret v5 `REUSE_ONLY` declaration with `packetIds = []` and
  `maxMaterialsToStart = Integer.MAX_VALUE` as the full-audit/no-live-call marker, then publishes a
  fresh M11 v4 and `activity-batch-result-v2` provenance metadata. The local Agent records output
  before applying FINISHED/FAILED from coverage plus packet completion.
- Root session `35715` verified that first full configured CLI vertical GREEN: the one direct
  historical replay test passed with no failures/errors in 1:36. Production is frozen again pending
  a separate observed RED for second-adoption provenance delegation.
- Root ruled that offline storage access will stay in the existing configuration model: `requireModelJobsForStorage()` performs only parsed locator/existing-directory validation, while execution validation retains Provider/auth checks. The resolver remains internal and uses a selected-batch listing on the existing private result store.
- Root session `22704` observed the canonical publication RED: main compilation passed and test compilation failed only because the new explicit `List<ActivityPacketCompletion>` publisher argument was missing.
- Implemented the bounded publication vertical pending root verification: M11 v4 coverage serializes explicit completion records, the reader strictly distinguishes v2/v3 historical absence from v4 presence, the canonical publication engine recognizes coverage-v4, and the Step05 caller refuses a missing completion list instead of inventing one.
- Root session `11683` verified that publication vertical GREEN: one real canonical M11 install/fresh-reopen test plus three completion-value tests passed (including null-slice-to-`whole-packet` and same-key overlap), without live JDT.
- Registered coverage-v4 in the active portable `tools/repository-run/jdt-artifact-policy-set-v1.json`; historical input and run-snapshot policies stay unchanged.
- Root session `51878` observed the offline storage-boundary RED: valid storage/configuration reached the old resolver-not-ready diagnostic instead of the required normal `MATERIALS_STATE_INVALID` state boundary. Implemented the storage-only accessor and saved Step05 state/source-loading branch; root verification is pending.
- Root session `18560` then passed the storage-boundary test plus eight configured-entry tests. The fixture-only correction supplied the already-created journal/output directories; provider/auth remained untouched and the command reached `MATERIALS_STATE_INVALID`.
- Root session `71939` observed the direct historical-resolver RED: main code compiled and test compilation failed solely because `ActivityHistoricalReuseResolver` did not exist. Implemented that bounded direct-pair vertical: it accepts only the selected source batch's direct v4 result, validates its exact DRAFT/REVIEW saved request, response, stage-success, and attempt provenance against verified Step05 material, retains the historical M11 rows/checkpoint byte-for-byte at the result seam, and derives the verified `whole-packet` completion without a Provider or execution cap. A missing private result becomes explicit historical `UNDETERMINED`; scoped/reused-chain adoption remains a later vertical.
- Root session `65591` compiled all three targeted classes and observed the next value RED: `COMPLETE` incorrectly allowed an empty required-slice set. Added the narrow constructor invariant. The remaining resolver test failures came only from its policy-fixture loader and require no production policy change; the resolver behavior body has not yet run.
- Root session `25734` verified the direct resolver/value/canonical vertical GREEN: 7 targeted tests, 0 failures/errors/skips, 50.303 s. This includes the real direct-pair resolver behavior after Luna corrected the policy fixture loader.
- Root session `8241` observed the explicit-reuse-chain RED: direct/corruption resolver cases passed and the sole chain error was the intentional direct-only rejection of a non-null `reusedFromModelBatchId`. Implemented a same-job/content/identity predecessor walk with source-owner checks, cycle rejection, and terminal-origin stage validation; root session `9444` then passed all 3 resolver tests in 52.262 s. The next narrow follow-up replaces per-hop batch enumeration with guarded exact-job reopening; it awaits the next root-targeted run.
- Root session `30303` observed the pure saved-plan seam RED: main code compiled and test compilation failed only because package-static `ActivityReadingCoordinator.reopenSaved(ActivityMaterialView, ObjectNode)` was absent. Implemented that bounded seam using the same structural reconstruction as online reopening: it validates the saved v1 navigation declarations and actual Step05 packet/body/source maps but never creates a Provider or replays a current capacity/profile. Online reopening retains its existing v1/v2 page and capacity validation. Root session `14274` then passed all six `ActivityReadingPlanPersistenceTest` tests plus all three `ActivityHistoricalReuseResolverTest` tests.
- Root session `44686` observed five bounded follow-up REDs. The current source patch is frozen pending root verification: parser-level `--reuse-only` plus packet selection rejection before configuration load; strict historical v4 rejection of `REUSE_ONLY`; full shown-page union validation in provider-free reopening; structurally complete provider-free v2 reopening without replaying a current profile/capacity; and explicit `activity-batch-result-v2` adoption delegation to its named original M11/private-record source. The delegation verifies batch/Step05/checkpoint identity, exact retained M11 content, named-chain cycles, and actual origin private provenance without a Provider.
- Root session `55139` verified all five of those behaviors GREEN (69 tests: 67 passed; its two remaining failures authorized the next online partial-range vertical). The subsequent typed in-memory partial-range patch carries each actual failed scoped slice's key, source-packet-order entry IDs, and reason through the existing execution loop; scoped completion uses that exact impact rather than deriving a null/all-entry failure, and partial coverage preserves reviewed/legal-unexplained results for entries outside the failed scope. Root session `98975` verified the capacity-preflight half of that patch GREEN (68 passed, 1 remaining fixture-only failure because the test declaration still made the failing scope own both entries). No production change is authorized for that fixture correction.
- Root session `57099` observed the next two resolver REDs after all 17 formal-large tests passed: the selected-batch resolver rejected one scoped aggregate plus its two same-packet slice results, and its retained-content check rejected a canonical top-level Activity ordering difference. Implemented the bounded scoped-complete path: it groups selected-batch records by packet; requires exactly one valid scoped aggregate; follows only its explicit aggregate reuse origin; reopens the actual saved plan through the Provider-free pure seam; and matches each saved DRAFT/REVIEW pair to the exact saved slice packet and `interpretationScope`. It returns COMPLETE only when every frozen final scope has a validated semantic pair. Retained result lists now match by stable top-level identity while requiring each full row, including nested business arrays, to remain exact.
- Root session `55589` verified all five resolver cases and all 17 formal-large cases GREEN (31 targeted tests: 29 passed). Its two new Step07 admission REDs showed missing and explicitly empty packet completion were still admitted. Implemented the narrow `ProcessDiscoveryRequest` Step05-only gate: missing/non-COMPLETE completion fails `PROCESS_DISCOVERY_PARTIAL_ACTIVITY_INPUT`; an explicit complete completion must exactly match actual Step05 packet IDs and per-packet entry-ID sets or fails `PROCESS_DISCOVERY_STEP05_INPUT_INVALID`. Existing coverage/unexplained checks, original `NOT_COLLECTED`, and legacy M10 constructors are unchanged.
- Root session `22406` verified the gate (9/9) and found two bounded remaining REDs (20 targeted tests: 18 passed): provider-free v2 reopening accepted a false saved `finishReading` without its machine issue, and canonical reopen rejected a reversed entry-ID claim. The current patch validates saved v2 finish/navigation machine issues without a current profile, and compares entry IDs as duplicate-free identity sets at the publication and Step07 seams without sorting or rewriting stored arrays. It is frozen for root verification.
- Root session `73054` verified those two fixes GREEN: all 20 targeted gate/plan/checkpoint tests passed in 43.412 s. Production is frozen while Luna establishes the next scoped-history RED.
- Root session `34814` verified actual copied-stage provenance GREEN (7 resolver tests, 29.101 s): the current partial-to-complete producer reuses stage-success evidence and attempt events, not a non-null scoped pair result, so no speculative pair-recovery branch was added.
- Root session `23965` observed the v1 scoped-history RED (9 tests: 8 passed, 1 expected resolver error): a structurally reopened v1 plan has no `finalSliceKeys`. The current patch adds a narrow coordinator-owned final-v1-scope helper: it reuses the already validated saved plan, derives only the last declaration for each key, and matches it to the frozen slice by the existing complete dependency-closure matcher. The resolver then completes only matching saved slices with their existing verified pairs; stale/missing final slices remain incomplete rather than being classified as damaged files. The separately green absent claimed-plan hard failure is unchanged. Source is frozen for root verification.
- Root session `12820` verified the basic v1 final-scope patch GREEN: all 9 targeted resolver tests passed in 43.887 s. Production is frozen while the next same-key and partial-navigation REDs are prepared.

## Changed files

- `progress/e2e-task3-completion-code-terra.md`
- `src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCli.java`
- `src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisExecution.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPacketCompletion.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplanationResult.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPacketCompletionVerification.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityHistoricalReuseResolver.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/main/java/org/sourceanalysis/app/runtime/modeljob/PrivateModelJobResultStore.java`
- `tools/repository-run/jdt-artifact-policy-set-v1.json`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | observed before edit | existing untracked local diagnostic POM and external research docs retained |
| `git diff --check` | passed | no whitespace errors in the first parser vertical |
| root Maven session `root85694` | passed | 8 targeted `SourceAnalysisConfiguredEntryPointTest` tests; verified parser GREEN |
| root Maven session `root35638` | expected RED observed | main compile was current; only the new typed-completion test failed to compile because the type, accessor, and current constructor were absent |
| root Maven session `7802` | passed | 2 targeted `ActivityPacketCompletionTest` tests; Luna's later overlap case awaits the next targeted run |
| root Maven session `22704` | expected RED observed | main compilation passed; test compilation failed only at the new canonical publisher call because its ninth completion-list argument did not exist |
| root Maven session `11683` | passed | 4 targeted tests (canonical M11 fresh-reopen plus completion value invariants), 1:09 |
| `jq empty tools/repository-run/jdt-artifact-policy-set-v1.json` | passed | portable policy remains valid JSON after adding the v4 coverage entry |
| root Maven session `18560` | passed | 9 targeted tests (storage boundary plus configured entry), 28.664 s |
| root Maven session `71939` | expected RED observed | main compilation passed; the sole test-compile error was the missing internal `ActivityHistoricalReuseResolver` |
| `git diff --check` | passed | no whitespace errors after the direct historical-resolver vertical |
| root Maven session `65591` | expected RED observed | 1 value assertion: COMPLETE accepted an empty required scope; 3 resolver fixture-loader errors occurred before resolver behavior |
| root Maven session `25734` | passed | 7 targeted direct resolver, canonical v4, and value-invariant tests; 0 failures/errors/skips, 50.303 s |
| root Maven session `8241` | expected RED observed | 3 resolver tests: direct/corruption passed; explicit `reusedFromModelBatchId` was correctly isolated as the current direct-only rejection |
| root Maven session `9444` | passed | 3 resolver tests, including explicit reused-chain source-stage adoption; 52.262 s |
| root Maven session `30303` | expected RED observed | main production compiled; only the missing package-static pure saved-plan seam blocked test compilation |
| root Maven session `14274` | passed | 6 `ActivityReadingPlanPersistenceTest` + 3 `ActivityHistoricalReuseResolverTest`; static v1 reopening is Provider/current-profile free |
| root Maven session `69627` | expected RED observed | formal literal configured `--reuse-only` fixture reached the offline stub and got exit 2 instead of 0; production full-CLI vertical then implemented, Maven deliberately not run by this worker |
| root Maven session `35715` | passed | 1 formal configured direct reuse-only CLI replay test; 0 failures/errors, 1:36 |
| `git diff --check` | passed | no whitespace errors after the full-CLI production patch |
| root Maven session `44686` | expected RED observed | 68 targeted tests exposed five bounded gaps: v1 page closure, pure v2 reopening, v4 mode strictness, repeated reuse-only adoption, and early packet-selector rejection |
| `git diff --check` | passed | no whitespace errors after the frozen five-fix patch; Maven remains root-owned and has not yet verified it |
| root Maven session `55139` | passed for five-fix vertical | 69 tests: all five observed behavior fixes GREEN; two later online partial-range REDs isolated |
| root Maven session `98975` | partial GREEN | 69 tests: typed preflight scope behavior GREEN; only the E1-only test fixture still declares E2 in the failed scope |
| root Maven session `57099` | expected RED observed | 22 targeted tests: all 17 formal-large tests passed; only scoped aggregate grouping and canonical top-level order remained |
| `git diff --check` | passed | no whitespace errors after the scoped aggregate/order patch; Maven remains root-owned |
| root Maven session `55589` | scoped resolver GREEN / gate RED | 31 targeted tests: 5 resolver + 17 formal-large pass; only missing/empty Step07 completion admission remained |
| root Maven session `22406` | gate GREEN / two REDs observed | 20 targeted tests: gate 9/9 passed; pure-v2 machine-state consistency and order-insensitive entry identity remain for the current root verification |
| root Maven session `73054` | passed | 20 targeted gate/plan/checkpoint tests, 0 failures/errors, 43.412 s; closes the saved-v2 machine-state and entry-identity vertical |
| root Maven session `34814` | passed | 7 resolver tests, 0 failures/errors, 29.101 s; copied stage-success reuse needs no pair-level provenance branch |
| root Maven session `23965` | expected RED observed | 9 resolver tests: only v1 `finalSliceKeys` absence caused a resolver failure; the claimed-plan absence case already passed |
| root Maven session `12820` | passed | 9 targeted resolver tests, 0 failures/errors, 43.887 s; closes the basic v1 final-scope vertical |

## Decisions

- Offline reuse must branch before model-job environment validation, Provider binding, or login; it reuses the existing Agent, registry, canonical stores, and configured journal/output locators.
- Saved-plan and stage/reuse provenance are resolved only for the explicitly selected source model batch inside the configured journal; no prompt-based reconstruction, guessed scope, or Provider fallback.
- Exact Step05 packet-set validation remains at the Publisher, offline resolver, and `ProcessDiscoveryRequest`, where saved materials are already present. The ordinary checkpoint reader checks v4 shape/identity and Activity-to-completion consistency only.
- `--reuse-only` is represented as a standalone flag in both parsers; it requires a reuse batch and is mutually exclusive with retry. Before the resolver vertical exists, the execution service fails with `ACTIVITY_REUSE_ONLY_OFFLINE_RESOLVER_REQUIRED` before model-job environment validation.
- A null Activity slice can evidence the canonical `whole-packet` key only after direct full-packet DRAFT/REVIEW provenance validates. No claimed historical plan is required when none existed; an explicitly claimed missing/corrupt plan is a hard failure, while genuinely absent obligation information is UNDETERMINED.
- `REUSE_ONLY` is an exact v5 execution-scope mode rather than a parallel schema. It records a
  non-secret current declaration for later source validation but cannot provide actual model identity
  or reading-cap evidence; those continue to come only from saved historical records.
- `completedSliceKeys` is a subset claim, not a proof that no current incomplete scope remains. COMPLETE has all required keys and no incomplete scope; INCOMPLETE/UNDETERMINED need explicit scope reasons. An UNDETERMINED packet may keep valid Activities with an empty unreconstructable required-key set.
- COMPLETE additionally needs a nonempty required-slice set; only historical INCOMPLETE/UNDETERMINED may retain an unknown or empty required scope with explicit incomplete reasons.
- The future resolver must not call `ActivityExplainer.reopenStageSuccess` or `readClaimedScopedActivity`: both bind the saved record to the current prompt/schema/provider/request. It instead follows explicit reused-batch provenance to the actual origin and validates stored claims without constructing a Provider.
- Scoped historical complete adoption is packet-grouped rather than one-result-per-material. It uses only the aggregate's saved plan and the origin batch's saved slice stage pairs; its exact request comparison binds each pair to frozen `readingPacket` plus `interpretationScope`, never a regenerated prompt, job key, Provider, or current execution limit. Canonical M11 may reorder top-level Activity/coverage/unexplained rows; their stable IDs map to full row equality, so nested list ordering remains strict.
- Step07 admits current Step05 data only with an explicit, all-COMPLETE completion view whose packet IDs and duplicate-free entry-ID sets exactly match the actual saved Step05 material. Stored arrays are not sorted or rewritten; only entry identity is order-insensitive. Completion absence/noncompletion is a partial Activity input; an explicit mismatched completion claim is an invalid Step05 input. The historical M10 route retains its prior constructors and admission behavior.
- Provider-free v2 reopening validates the saved historical machine state directly: incomplete navigation requires its exact saved-page issue, complete navigation has no navigation issue, and `finishReading` agrees with `READING_NOT_FINISHED`. It does not infer business gaps from unknown prose or recompute former capacity limits.
- For v1 scoped completion, `reopenSaved` remains the sole plan/body validator. A narrow coordinator seam derives final obligations by the last structurally valid declaration per key and compares each saved slice with the existing complete dependency-closure matcher. The resolver uses that result only to classify pair completion; it neither reparses saved source, recomputes capacity, nor guesses cross-key intent from unknown prose.
- Offline locator validation is a narrow `RepositoryRunConfiguration.requireModelJobsForStorage()` / `ModelJobsConfiguration.validateStorageEnvironment()` extension; `requireModelJobsForExecution()` invokes it before its existing Provider/auth checks. Selected-batch enumeration belongs on `PrivateModelJobResultStore` with its existing path guards.
- The approved internal direct-resolver seam is `ActivityHistoricalReuseResolver(Path journalDirectory).resolve(CodeReadingMaterialSet verifiedMaterials, ActivityExplanationResult sourceActivities, AnalysisRunId sourceBatchId)`. It preserves the old source checkpoint and input rows exactly while returning an explicit completion set for every actual packet; CLI later installs the separate new checkpoint.
- The first resolver vertical recognizes only a direct source-owned `model-job-reviewed-result-v4` with null reuse provenance and exactly indexed DRAFT/REVIEW successes. It validates saved event identities, schemas, raw response evidence, retained result rows, and the DRAFT request's deterministic Step05 packet before associating null-slice output with `whole-packet`; it deliberately does not use current prompts, Provider bindings, current execution caps, or `ActivityExplainer.reopenStageSuccess()`.
- Reused direct results retain the same private result body at every explicitly named predecessor: the resolver verifies each run owner and compares job, material, input, runtime, content, and stage-success claims before following the declared chain. Only its terminal direct origin must hold DRAFT/REVIEW files; absent copied local stage paths in an intermediate `completeReused` batch are valid. The active follow-up changes predecessor lookup to an exact guarded job read rather than reopening unrelated Activity result files.
- There is no retained callable Step05 v3 writer. The explicit v4 publisher argument is required; until the online aggregation supplies a packet completion list, the production caller must fail from `result.packetCompletion().orElseThrow(...)` rather than fabricate a complete result. Reader consistency must retain an UNDETERMINED Activity even when no required/current slice set can be established.
- Public v4 reopening is strict about root/packet/scope wire shape, completion status and activity/unexplained-entry association. It intentionally has no new upstream Step05 scan; publisher-only validation checks the exact actual packet and entry sets.

## Blockers

- Root owns the Maven slot needed to observe later resolver REDs and their bounded GREEN verification.

## Exact next action

- Source is frozen after the basic v1 final-scope vertical verified GREEN. Do not alter test
  fixtures, batch-result persistence, or further scoped v1/reused-slice/incomplete behavior unless
  root first reports a new behavior RED.

## Resume checks

- Re-read this file, check source-code Git status, inspect the shared test's RED command/result, and preserve unrelated worktree files.

## Plan closeout destinations

- Durable decisions: root-owned Task 3 design/documentation updates.
- Remaining issues: Task 4 owns the real 325-packet audit; later Task 3 verticals own full historical resolver behavior.
- Verification and output references: `.workspace/end-to-end-business-delivery-20260923/task-3-code-report.md`.
