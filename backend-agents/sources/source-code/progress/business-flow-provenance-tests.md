# Progress: business-flow provenance tests

- Status: COMPLETE (compiler-only Gap normalization RED captured; production implementation remains out of scope)
- Agent role: Luna/xhigh bounded public-seam test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add standalone public-seam tests proving fresh-reopened M2 Fact/source-Gap/budget-Gap provenance, the positive M3 public carrier, and the §8.3 self-consistent rehashed-M2 semantic-replay negative; mechanically migrate all M3 test consumers to Terra's reader-injected public constructor. Retain only the approved v7 M2 fixture policy and the explicitly authorized v5/v2 public registrations.
- Approved inputs: Step 05 §8.1.3, existing public graph-to-Fact-to-Flow-to-Capsule publishers and tests, and the constructed `ProgramGraphsPublicFixture`. The follow-up authorizes only its existing `BUSINESS_FLOWS_CAPSULE_PROJECTION` policy schema string to move from v6 to v7; no dual policy, ID/rule/source/counter/semantic changes, or unrelated version migration is authorized.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this progress record before editing the standalone test.
- Added one standalone `BusinessFlowProvenanceTest` behavior test. It publishes the real graph→Fact→Flow→Capsule chain, fresh-reopens `proven-facts.json` and the M2 capsule projection, derives the exact Step 04 descriptor reference, checks that provenance first, and then compares the persisted Fact view ID/kind/subjects/nested atom values to the exact source Fact.
- Kept the test artifact-level: no Java record constructor migration, fixture/policy rewrite, source change, Gap variant, or version-only expectation was added.
- Performed the approved one-string fixture policy cutover from `business-flows-capsule-projection-v6` to `business-flows-capsule-projection-v7`; prefix, artifact type, media type, policy kind, ID rules, sources, counters, and test expectations are unchanged.
- Added exactly one source-Gap behavior test. It derives nonempty `EXTERNAL_EFFECT`/`DATA_FLOW_BINDING_UNPROVEN` rows from reopened `gap-ledger.json`, verifies every row's evidence node exists in the reopened EvidenceGraph, then checks the matching reopened M2 Gap view against the exact ledger and EvidenceGraph descriptors.
- Reopened this progress record for the single budget-Gap provenance behavior; that bounded method is now covered by the recorded GREEN class runs.
- Added exactly one budget-Gap provenance method to the existing `BusinessFlowProvenanceTest.java`. It reuses the guarded-approve fixture and real Fact→Flow→M2 publishers, forces source-byte overflow with `maxCapsuleUtf8Bytes=1`, fresh-reopens the persisted capsule projection, and checks the typed `CAPSULE_PROJECTION` carrier; it is covered by the recorded GREEN class runs.
- Added exactly one compiler-only Gap normalization method to the existing `BusinessFlowProvenanceTest.java`. It reuses the complete fixture and real zero-Flow traversal-budget route; its source-ledger/ENTRY ownership assertions are frozen pending the authorized combined selector.

## Current state

- Existing Fact-origin, source-Gap, budget-Gap, positive M3 carrier, and replay methods remain recorded in the verification table; root/Terra report the five existing provenance methods GREEN after the published constructor and carrier cutovers.
- Added one compiler-only Gap normalization method using the complete `ProgramGraphsPublicFixture.create(...)` and a real traversal-depth-1 `FlowCompilationProfile`. It requires two entry-owned `ENTRY` resource-limit Gaps, zero M1/M2 Flows/Capsules, exact source-ledger plus compiler-only Gap closure, and the final five-file public carrier.
- The method deliberately exercises the current public normalizer, which still rejects raw compiler `ENTRY` scope; this is the intended bounded RED until the separately owned production normalization is corrected.
- No fixture, source, schema, version, existing assertion, or production edit is included in this slice.

## Changed files

- `progress/business-flow-provenance-tests.md` (owned; intent-to-add before Java edit)
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/BusinessFlowProvenanceTest.java` (owned; one behavior test)
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (owned follow-up; two existing public carrier policy schema strings only)
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/SyntheticReplenishmentBusinessFlowsTest.java` (mechanical M3 reader argument)
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilerTest.java` (mechanical M3 reader argument)
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java` (mechanical M3 reader argument)
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsPublicationSpecifierTest.java` (mechanical reflective constructor signature/argument)
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/BusinessFlowProvenanceTest.java` (one compiler-only Gap normalization behavior test; reopened for this bounded slice)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only contract/test inspection | PASS | Step 05 §8.1.3 and existing M2 public graph→Fact→Flow→Capsule seams read. |
| Initial exact selector | TEST-COMPILE RED | Numeric exit 1; compilation reached the owned test but AssertJ's direct `JsonNode` assertions selected iterable overloads. No tests executed; corrected only the owned boolean node predicates. |
| Valid exact selector (session `624c4d`) | EXPECTED RED | Numeric exit 1; `1` run, `1` failure, `0` errors, `0` skipped. Real producer chain completed; first semantic assertion failed at `BusinessFlowProvenanceTest.java:113`: Fact view `code-fact:9608a608634bf2a131971af41e394b1880189f4e78fcb9203d4939489d7fa4c4` lacks `originFactArtifactRef`. |
| Absolute one-file Spotless apply | PASS | Numeric exit 0; exactly one owned Java file selected and changed to clean. |
| Absolute one-file Spotless check | PASS | Numeric exit 0; exactly one owned Java file selected, no changes needed. |
| Post-format exact selector | EXPECTED RED | Numeric exit 1; `1` run, `1` failure, `0` errors, `0` skipped. Same missing-origin assertion, now at formatted line 107; no bootstrap or upstream handoff error. |
| v7-only fixture policy migration | PASS | Changed exactly `business-flows-capsule-projection-v6` to `business-flows-capsule-projection-v7` in the existing policy registration; no other fixture line or policy was changed. |
| Exact selector after v7 cutover (session `42072`) | GREEN | Numeric exit 0; `1` run, `0` failures, `0` errors, `0` skipped. Real M2 publication and provenance assertions completed successfully. |
| Absolute one-file Spotless apply/check on `ProgramGraphsPublicFixture.java` | PASS | Both numeric exits 0; one changed fixture file selected, apply found it already clean, check required no changes. |
| Exact post-format selector | GREEN | Numeric exit 0; `1` run, `0` failures, `0` errors, `0` skipped. |
| Scoped diff check | PASS | `git diff --check` clean for the owned progress, provenance test, and fixture policy file. |
| Initial new source-Gap selector | TEST-COMPILE RED | Numeric exit 1; no tests executed because the owned test initially missed `java.util.List`; corrected only that import. |
| Valid source-Gap selector | EXPECTED RED (historical) | Numeric exit 1; `1` run, `1` failure, `0` errors, `0` skipped. Historical first semantic failure was missing `originKind` before its production implementation. |
| Source-Gap test absolute one-file Spotless apply/check | PASS (historical) | Both numeric exits 0; exactly one owned `BusinessFlowProvenanceTest.java` selected. |
| Source-Gap post-format selector | EXPECTED RED (historical) | Historical missing-`originKind` RED; the current 3-method class selector is GREEN after implementation. |
| Read-only budget-Gap seam/contract inspection | PASS | Step 05 §8.1.3 priority 3 and the existing over-budget projector test/production path were inspected. The existing public projector path supplies a truthful budget Gap without a source-ledger row; no fixture extension is proposed. |
| Budget-Gap method self-review | PASS | One method only; uses actual persisted M2 shape, no fabricated ledger/evidence references, and references only existing fixture/publisher/profile APIs. |
| Absolute one-file Spotless apply | PASS (exit 0) | Exactly one owned `BusinessFlowProvenanceTest.java` selected and changed to clean. |
| Absolute one-file Spotless check | PASS (exit 0) | Exactly one owned `BusinessFlowProvenanceTest.java` selected; no changes required. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest test` | GREEN (numeric exit 0) | `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`; Fact-origin, source-Gap, and budget-Gap methods all completed. |
| M3 positive method self-review | PASS | One new public `FlowPublicationSpecifier` method; only actual M1/M2/public predecessor references and persisted descriptor derivation; no replay negative or fabricated artifact. |
| Initial M3 exact selector | TEST-COMPILE RED | Numeric exit 1; no tests executed. Owned `jsonLines` helper used a `String` method reference where `CanonicalJsonCodec` requires `ImmutableBytes`; this was corrected only in the owned test. |
| M3 exact selector after owned compile correction | EXPECTED RED (current carrier gate) | Numeric exit 1; `Tests run: 1, Failures: 0, Errors: 1, Skipped: 0`. `FlowPublicationException: FLOW_ACCOUNTING_INVARIANT_BROKEN` at `FlowPublicationSpecifier.requireModule:563` from `specify:117`; test line 333. The real M3 seam rejected M2 v7 before public installation. |
| M3 non-vacuity correction | READY (not rerun) | Added only persisted M1/public Flow and Fact-set nonempty/equality assertions; prior carrier-gate RED is preserved as the current execution result. |
| Read-only §8.3 semantic-replay preparation | PASS | Inspected `CapsuleProjectionModulePublisher`, `AtomicCanonicalPublicationEngine`, `ProgramGraphsPublicFixture`, and `AtomicAnalysisStepPublicationEngine`; confirmed deterministic two-root identities, public install/reopen validation, exact projection/envelope rehash formulas, and receipt-last path. No Java or Maven action taken. |
| Replay-negative Java self-review | READY (not run) | One new method and three narrow helpers only; runtime basis equality, generic install/reopen, exact canonical rehash, public M3 rejection, and no-Step-05-artifact assertions are present. |
| Absolute one-file Spotless apply/check | PASS | Both numeric exits 0; only `BusinessFlowProvenanceTest.java` selected, apply changed it to clean and check then passed. |
| Initial replay exact selector | TEST-COMPILE RED | Numeric exit 1; `1` run, `0` failures, `1` error, `0` skipped. Owned assertion used unavailable AssertJ `hasMessageNotBlank()`; corrected to `getMessage().isNotBlank()`. |
| Replay exact selector after compile correction | TEST-HELPER ERROR | Numeric exit 1; `1` run, `0` failures, `1` error, `0` skipped. Owned `frame(byte[])` allocated only eight bytes and threw `BufferOverflowException` during rehash; corrected to allocate length plus payload. |
| Replay exact selector after helper correction | EXPECTED PRODUCTION RED | Numeric exit 1; `1` run, `1` failure, `0` errors, `0` skipped. Runtime basis equality, public M2 install, and fresh reopen passed; M3 returned normally, so the assertion at formatted line 605 found a null `FlowPublicationException` instead of the required semantic-replay rejection. |
| Read-only constructor/path audit | PASS | All exact `FlowPublicationSpecifier` consumers are accounted for; the new constructor's third argument is the existing verified-source reader in each site. `AnalysisStepKey.BUSINESS_FLOWS` confirms directory `05-business-flows`, receipt `business-flows-receipt.json`, and module 3 key `publish`/directory `03-publish`, matching the replay helper path. |
| Constructor migration Spotless apply | PASS | Numeric exit 0; exactly the five listed test paths selected, one changed to clean and four already clean. |
| Constructor migration Spotless check | PASS | Numeric exit 0; exactly the five listed test paths selected and all clean. No Maven test execution performed while Terra owns production. |
| Read-only zero-Flow route inspection | PASS | Existing `EntryRootedFlowCompilerTest#returnsZeroFlowsAndGapDispositionsWhenEveryEntryExceedsTraversalBudget` proves a real max-traversal-depth-1 compilation with two `ENTRY` resource-limit Gaps and zero Flows. |
| Compiler-gap method absolute one-file Spotless apply/check | PASS | Numeric exit 0 for both commands; only `BusinessFlowProvenanceTest.java` was selected, and the final post-helper check required no changes. |
| First authorized combined selector | TEST-COMPILE RED | Numeric exit 1; no tests executed because the new method passed `List<JsonNode>` to the existing `JsonNode` helper at lines 616 and 628. Added one owned list overload; no production or assertion change. |
| Authorized combined selector after helper correction (session `31212`) | EXPECTED RED | Numeric exit 1; `9` tests run, `1` failure, `0` errors, `0` skipped. `BusinessFlowProvenanceTest` ran 6 with 1 failure/5 passes; `BoundedBusinessFlowPublicationTest` ran 1 pass; both graph methods ran 1 each and passed after Terra's predicate-only fix. The new compiler-gap test reached all M1/M2/source-ledger preconditions, then failed at line 596 because `FlowPublicationSpecifier.normalizeCompilerGap:596` rejects raw `ENTRY` with `FLOW_ACCOUNTING_INVARIANT_BROKEN`. |

## Decisions

- Test only the public persisted seam: publish real graph→Fact→Flow→Capsule artifacts, fresh-reopen M2 and Step 04 payloads, then compare exact descriptor and Fact/atom values.
- Use one behavior test and no guessed artifact IDs, manual Fact/Gap objects, source edits, or additional provenance variants.
- Compare persisted M2 atoms using their published nested `value.type`/`value.canonical` wire shape against Step 04's nested value object; do not conflate it with the Java `FlowAtomView` record fields.
- Treat the v7 policy change as a fixture contract alignment only; do not migrate existing v6 assertions/readers or introduce a compatibility policy.
- Keep the source-Gap test grounded in actual persisted rows and descriptors: `originGapLedgerRef` comes from the reopened ledger descriptor, `evidenceRefs` must point to the actual EvidenceGraph descriptor, and affected IDs come directly from `affectedCandidateDenominatorKeys`; its current implementation is GREEN and must not be regressed.
- For the budget variant, reuse `ProgramGraphsPublicFixture.createWithGuardedApprove`, `publishProvenFacts`, the existing Flow publisher, `EvidenceCapsuleProjector`, and `CapsuleProjectionModulePublisher`; use a tight `CapsuleProjectionProfile` (the existing `profile(1)` shape) to force actual source-byte overflow. Fresh-reopen `capsule-projection.json` and assert nonempty retained spans, `modelEligibility=INELIGIBLE`, profile/budget overflow, one matching `CAPSULE_BUDGET_NO_SAFE_SPLIT` Gap per ineligible capsule, `affectedSemanticIds=[flowSliceId]`, `scope=FLOW`, `originKind=CAPSULE_PROJECTION`, `originGapLedgerRef=null`, `evidenceRefs=[]`, and the Gap ID's membership in `modelIneligibilityGapIds`. Do not invent a Step 04 row, ledger reference, EvidenceGraph reference, or a second fixture.
- For the later replay negative, use two independent deterministic guarded-approve roots and real public M1/M2 publishers. Rehash only the canonical M2 projection body and envelope with the exact published framing/domain formulas, install through `CanonicalModuleArtifactStore.install`, fresh-reopen the changed M2, then invoke the existing two-argument M3 seam. Keep the expected replay error code provisional until the positive M3 path is GREEN; require rejection before any Step 05 module/receipt and preserve the original upstream/reference equality assertions.
- The constructor migration is mechanical only: direct calls receive the existing fixture/IT verified-source reader, and the reflection helper resolves the exact three-argument public constructor with `VerifiedSourceTextReader.class`. Do not add compatibility overloads, alter assertions, or change any wire/fixture value.
- The constructor migration is mechanical only: direct calls receive the existing fixture/IT verified-source reader, and the reflection helper resolves the exact three-argument public constructor with `VerifiedSourceTextReader.class`. Do not add compatibility overloads, alter assertions, or change any wire/fixture value. The five migrated paths are frozen.

## Blockers

- The compiler-only Gap method has a valid bounded production RED after all upstream assertions pass: current `FlowPublicationSpecifier.normalizeCompilerGap` rejects the compiler's raw `ENTRY` resource-limit scope with `FLOW_ACCOUNTING_INVARIANT_BROKEN`. The required normalization/contract decision remains outside this test-only slice.
- The combined selector otherwise passed all bounded tests: the five existing provenance methods, bounded public closure test, and both graph coverage methods were GREEN. No production, fixture, design, or schema edit was made here.

## Exact next action

- Release the Maven lease and keep this test slice frozen. Hand the valid compiler-only `ENTRY`-scope normalization RED to root; any production normalization change requires a separate authorized Terra slice and a fresh targeted rerun.

## Resume checks

- Preserve the existing five provenance methods and all helper/fixture/wire behavior; the combined selector already verified them alongside the bounded closure and graph methods.
- Preserve the first-failure classification: the compiler-gap method passed M1/M2/source-ledger preconditions and failed only at public `normalizeCompilerGap` for raw `ENTRY` scope.
- Do not modify production, fixture, design, schema, or run other selectors from this completed test slice.
