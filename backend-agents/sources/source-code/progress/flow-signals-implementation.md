# Progress: flow-signals-implementation

- Status: IN_PROGRESS
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Bounded M1 proof-closed `processJoinSignals` type/call/external-Gap extraction, traversal-budget and exact guard-target repairs, M1 v2 publication, proof-gated `COUNTER_CONDITION`, M2 Capsule signal projection and v5 publisher persistence, valid zero-Flow projection, M3 public Flow/Capsule handoff, R0/M4 public-reader v2/v3 signal-closure cutovers, and valid public zero-Flow-to-zero-local-task handling; no test edits, upstream graph changes, downstream provider/runner work, or future shared-source ownership changes.
- Approved inputs: published Step 05 design; reviewed implementation handoff; exact Luna RED `emitsExactProofClosedSignalsForTwoPersistedFlowsWithoutCrossFlowBorrowing`; both active implementation plans; source-scoped instructions.
- Current branch/worktree: `codex/source-analysis-process-materials` at main `12005e8` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed the exact Luna RED established the intended missing `FlowSlice.processJoinSignals()` accessor after persisted Fact/Proof/Gap/Evidence and typed-boundary premises passed.
- Read current scoped instructions, both implementation plans, the implementation handoff/progress, and the target Step 05 design.

## Current state

- The bounded type/call/external-Gap compiler slice is GREEN. The first relative-path formatter invocation was ineffective; a later absolute exact-four-file formatter apply and check are GREEN, followed by the required post-format three-method rerun.
- The independent traversal-budget RED is GREEN with the minimal change: preserve the existing sorted upstream Gap contents while returning a mutable `ArrayList`, allowing per-entry budget dispositions to append their two new Gap records.
- The M1 publisher v2 RED is GREEN: the sole FlowCompilation payload now persists both signal budgets, each complete signal on its owning Flow, the sorted coverage signal-ID union, and the signal-bound compilation identity. It fresh-reopens and recompiles fixed predecessor artifacts before installation, rejecting a supplied compilation that differs from the rebuilt value.
- The Fact M1 guard-target RED is GREEN: an exact FALSE branch that targets a same-entry CALL graph node now closes through the uniquely verified public program-node union.
- The counter RED is GREEN: only a typed guard/polarity boundary with one same-Flow guard Fact/`CONTROL_CONDITION`, both recorded polarities, and call-site path exclusivity emits `COUNTER_CONDITION`. The early-return fixture's null typed context remains counter-free.
- The M2 Capsule signal projection RED is GREEN: M2 accepts only persisted M1 v2, reconstructs each complete shared `ProcessJoinSignalV1`, validates same-Flow Fact/atom/Proof/Evidence/Gap/locator closure, and copies it verbatim into its owning Capsule. Every signal has exactly one locator-matched `PROCESS_JOIN_SIGNAL_BASIS` obligation; spans expose their exact sorted signal support and Capsule identity binds signal IDs before span/obligation IDs.
- The M2 v5 publisher RED is GREEN: it writes each Capsule's complete 14-field signal records and each span's direct signal-support ID array, registers only `business-flows-capsule-projection-v5`, and preserves fresh-reopen equality, 14 predecessors, one payload, receipt-last installation, and ineligibility behavior. A test-helper object/array misuse was corrected by its test owner; production keeps the required direct ID-array shape.
- The zero-Flow Capsule RED is GREEN: a valid, freshly reopened M1 v2 compilation with an explicit empty `flowSlices[]` projects to exact empty Capsules, spans, and obligations while retaining its compilation and ProofPack references and all predecessor/control/hash validation. Missing arrays, wrong types, and broken references remain fail-closed.
- The M3 public-handoff RED is GREEN: it requires M1 v2 and M2 v5, publishes public Flow v2/Capsule v3 without aliases, preserves five semantic files, and verifies complete same-Flow signal values byte-for-byte. It keeps span support and signal-basis obligations Capsule-local and publishes the exact sorted signal-ID union in unchanged coverage v1.
- The bounded R0 reader RED is GREEN: `RegistryProposalTaskCompiler` requires public Flow v2/Capsule v3, rebuilds every complete signal record to reject malformed or stale identities, requires byte-identical same-Flow signal arrays, and validates Capsule-local signal span support against exactly one `PROCESS_JOIN_SIGNAL_BASIS` obligation. Its provider-visible `capsuleView` remains a deep copy canonically equal to the owning public Capsule; eligibility, task budgets/identities, and zero-provider behavior are unchanged.
- The bounded M4 reader RED is GREEN: `FiniteKeyFlowTaskCompiler` requires public Capsule v3 and retains the complete same-Flow public Capsule, including its full signal/span/obligation closure, byte-for-byte in each R1/R2 input and the persisted M4 task set. Registry/basis admission, identities, budgets, sessions, and exact two tasks per ready Flow remain unchanged.
- The public zero-Flow-to-zero-local-task RED is GREEN: after normal public descriptor, reference, coverage, registry, and ready-flow checks, R0 and M4 accept only a canonical zero-byte Capsule JSONL as an empty collection. Empty whitespace or malformed nonempty JSONL remains invalid. The zero-Flow publication keeps all five semantic files and its two GAP entry dispositions, while R0/R1/R2 create no tasks, make no Provider call, and persist explicit empty denominators.

## Changed files

- `backend-agents/sources/source-code/progress/flow-signals-implementation.md` (this progress record)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationProfile.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisher.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompiler.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Luna exact selector | EXPECTED RED (reported and persisted by test owner) | 1 test, 1 failure, 0 errors/skips; missing `FlowSlice.processJoinSignals()` only. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest#compilesOneOwnedFlowForEachPersistedEntryAndRetainsItsExternalEffectGap+compilesTheTwoDistinctTerminalPathsOfAnExactJavaGuard+emitsExactProofClosedSignalsForTwoPersistedFlowsWithoutCrossFlowBorrowing test` | COMPILATION FAILURE | One production error: `PersistedFlowCompilationInputReader` passed a one-argument method reference to its two-argument `text` helper. |
| same exact three-method selector | TEST FAILURE | 3 tests, 3 failures, 0 errors/skips; all failed at the local signal extractor's duplicate-sensitive evidence collection, before assertion evaluation. |
| same exact three-method selector | PASS | 3 tests, 0 failures, 0 errors, 0 skipped; the expected type/call/external-Gap signal closure is GREEN. |
| scoped relative-path `spotless:apply` and `spotless:check` | SUPERSEDED | The plugin selected no files; subsequent verbose validation proved the relative comma-list was ineffective. |
| absolute exact-four-file `spotless:check` | FORMAT RED | `Spotless.Java is keeping 4 files clean - 3 needs changes to be clean, 1 were already clean`; only the four owned production paths were selected. |
| absolute exact-four-file `spotless:apply` | PASS | `Spotless.Java is keeping 4 files clean - 3 were changed to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same absolute exact-four-file `spotless:check` | PASS | `Spotless.Java is keeping 4 files clean - 0 needs changes to be clean, 0 were already clean, 4 were skipped because caching determined they were already clean`. |
| same exact three-method selector after formatting | TEST-LOCAL COMPILATION FAILURE | Luna's in-flight counter test references not-yet-added `CounterPremises` and `assertCounterPremises`; no production assertion executed. |
| same exact three-method selector after Luna `COMPILE_READY` | PASS | 3 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (28.460 s). The counter selector remained excluded. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest#returnsZeroFlowsAndGapDispositionsWhenEveryEntryExceedsTraversalBudget+compilesOneOwnedFlowForEachPersistedEntryAndRetainsItsExternalEffectGap+compilesTheTwoDistinctTerminalPathsOfAnExactJavaGuard+emitsExactProofClosedSignalsForTwoPersistedFlowsWithoutCrossFlowBorrowing test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (29.510 s). The counter selector remained excluded. |
| absolute exact-one-file `spotless:apply` | PASS | `Spotless.Java is keeping 1 files clean - 0 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-one-file `spotless:check` | PASS | `Spotless.Java is keeping 1 files clean - 0 needs changes to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same exact four-method selector after formatting | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (12.542 s). The counter selector remained excluded. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationModulePublisherTest test` | PASS | 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (25.342 s). |
| absolute exact-two-file `spotless:apply` | PASS | `Spotless.Java is keeping 2 files clean - 1 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-two-file `spotless:check` | PASS | `Spotless.Java is keeping 2 files clean - 0 needs changes to be clean, 0 were already clean, 2 were skipped because caching determined they were already clean`. |
| same publisher selector after formatting | PASS | 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (26.114 s). |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateEnumeratorTest,GuardConditionFactCandidateTest,GuardConditionAtomicProofTest,GuardConditionFactLedgerTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (27.091 s). The direct FALSE→CALL_SITE guard candidate now closes. |
| absolute exact-one-file `spotless:apply` for `FactCandidateEnumerator.java` | PASS | `Spotless.Java is keeping 1 files clean - 0 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-one-file `spotless:check` | PASS | `Spotless.Java is keeping 1 files clean - 0 needs changes to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same direct Fact selector after formatting | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (10.683 s). |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest#emitsCounterConditionOnlyForProofClosedTypedBoundaryGuardContext test` | INTENDED DIAGNOSTIC RED | 1 test, 1 failure, 0 errors. Its premise helper fails at `EntryRootedFlowCompilerTest:839`: expected a CONTROL_FLOW entry→FALSE-terminal path to contain the CALL-graph invocation node, but it is false. No counter implementation was attempted. |
| Luna exact counter selector after test-oracle correction | EXPECTED RED (reported and independently verified by root) | 1 test, 1 failure, 0 errors; all persisted premises passed, but expected 4 signals and found 3 because `COUNTER_CONDITION` was absent. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest test` | COMPILATION FAILURE | One local production error: the new `Set.of` polarity gate lacked the `java.util.Set` import. |
| same five-method compiler selector | PASS | 5 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (31.454 s). |
| absolute exact-one-file `spotless:apply` for `EntryRootedFlowCompiler.java` | PASS | `Spotless.Java is keeping 1 files clean - 1 were changed to be clean, 0 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-one-file `spotless:check` | PASS | `Spotless.Java is keeping 1 files clean - 0 needs changes to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same five-method compiler selector after formatting | PASS | 5 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (31.588 s). |
| Luna Capsule signal selector | EXPECTED RED (reported and persisted by test owner) | `projectsFullProcessJoinSignalsWithSameFlowBasisAndIdentityBinding` failed only because M2 still required M1 schema v1 after the fixture proved complete persisted v2 signals. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` | TEST FAILURE | 4 tests, 4 failures, 0 errors/skips. New closure validation incorrectly rejected repeated evidence nodes sharing one canonical source locator; no M1 data was malformed. |
| same `EvidenceCapsuleProjectorTest` selector | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. The closure now uses the compiler's exact distinct locator union. |
| absolute exact-two-file `spotless:apply` for CapsuleProjection and EvidenceCapsuleProjector | PASS | `Spotless.Java is keeping 2 files clean - 2 were changed to be clean, 0 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-two-file `spotless:check` | PASS | `Spotless.Java is keeping 2 files clean - 0 needs changes to be clean, 0 were already clean, 2 were skipped because caching determined they were already clean`. |
| same `EvidenceCapsuleProjectorTest` selector after formatting | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| Luna M2 publisher selector | EXPECTED RED (reported and independently verified by root) | 1 test, 1 failure, 0 errors/skips: actual M2 schema v4, required v5 after valid two-Flow, three-signal premises. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest test` | TEST-ONLY FAILURE | 3 tests, 1 failure, 0 errors/skips. `publishesFreshReopenedSignalsAndExactCapsuleBasisClosure` line 170 passes a span object to its array-of-objects `jsonStrings(..., property)` helper, producing blank strings instead of reading the valid direct `supportedProcessJoinSignalIds[]` node. Production is compile-ready; no wire-shape workaround or test edit was made. |
| absolute exact-two-file `spotless:apply` for CapsuleProjectionModulePublisher and AtomicCanonicalPublicationEngine | PASS | `Spotless.Java is keeping 2 files clean - 1 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-two-file `spotless:check` | PASS | `Spotless.Java is keeping 2 files clean - 0 needs changes to be clean, 0 were already clean, 2 were skipped because caching determined they were already clean`. |
| same `CapsuleProjectionModulePublisherTest` selector after Luna's helper correction and formatting | PASS | 3 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| Luna zero-Flow projector selector | EXPECTED RED (reported and independently verified by root) | 1 test, 1 failure, 0 errors/skips: valid M1 v2 zero Flow/GAP accounting reached `parseFlows`, which incorrectly rejected the explicit empty array with `FLOW_OUTCOME_CLOSURE_BROKEN`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` | PASS | 5 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. Valid zero-Flow input now yields an exact empty projection. |
| absolute exact-one-file `spotless:apply` for EvidenceCapsuleProjector | PASS | `Spotless.Java is keeping 1 files clean - 0 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-one-file `spotless:check` | PASS | `Spotless.Java is keeping 1 files clean - 0 needs changes to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same `EvidenceCapsuleProjectorTest` selector after formatting | PASS | 5 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (16.909 s). |
| Luna M3 public-handoff selector | EXPECTED RED (reported and independently verified by root) | 1 test, 1 failure, 0 errors/skips. Valid fresh M1 v2/M2 v5 two-Flow, three-signal material reached the stale M3 reader and failed `FLOW_ACCOUNTING_INVARIANT_BROKEN`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowsPublicationSpecifierTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. M3 v2/v3 accepts and publishes the exact persisted signal closure. |
| absolute exact-two-file `spotless:apply` for FlowPublicationSpecifier and AtomicCanonicalPublicationEngine | PASS | `Spotless.Java is keeping 2 files clean - 1 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-two-file `spotless:check` | PASS | `Spotless.Java is keeping 2 files clean - 0 needs changes to be clean, 0 were already clean, 2 were skipped because caching determined they were already clean`. |
| same `BusinessFlowsPublicationSpecifierTest` selector after formatting | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (13.134 s). |
| Luna R0 public-reader selector | EXPECTED RED (reported and independently verified by root) | 1 test, 1 failure, 0 errors/skips; valid public Flow v2/Capsule v3 signal closure reached the stale descriptor gate and failed `FLOW_INTERPRETATION_INPUT_INVALID`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=RegistryProposalTaskCompilerTest test` | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| absolute exact-one-file `spotless:apply` for `RegistryProposalTaskCompiler.java` | PASS | `Spotless.Java is keeping 1 files clean - 1 were changed to be clean, 0 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-one-file `spotless:check` | PASS | `Spotless.Java is keeping 1 files clean - 0 needs changes to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same `RegistryProposalTaskCompilerTest` selector after formatting | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompiler.java` | PASS | No scoped whitespace errors. |
| same `RegistryProposalTaskCompilerTest` selector after the final progress/diff check | PASS | 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (15.527 s). |
| Luna exact M4 public-reader selector | EXPECTED RED (reported and independently verified by root) | 1 test, 1 failure, 0 errors/skips; valid public Flow v2/Capsule v3 and frozen R0/registry premises reached the stale Capsule v2 descriptor gate. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FiniteKeyFlowTaskCompilerTest#retainsTheFullOwningPublicCapsuleInEveryR1AndR2Task test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (26.436 s). |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FiniteKeyFlowTaskCompilerTest test` | PASS | 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (10.501 s). |
| absolute exact-one-file `spotless:apply` for `FiniteKeyFlowTaskCompiler.java` | PASS | `Spotless.Java is keeping 1 files clean - 0 were changed to be clean, 1 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-one-file `spotless:check` | PASS | `Spotless.Java is keeping 1 files clean - 0 needs changes to be clean, 0 were already clean, 1 were skipped because caching determined they were already clean`. |
| same `FiniteKeyFlowTaskCompilerTest` selector after formatting | PASS | 2 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (10.445 s). |
| Luna zero-Flow local-reader selector | EXPECTED RED (reported and independently verified by root) | 1 test, 0 failures, 1 error; valid M1/M2/M3 zero-Flow material reached R0's zero-byte Capsule JSONL rejection after all public five-file and coverage premises passed. |
| same zero-Flow selector after the R0-only decoder change | EXPECTED RED | 1 test, 1 failure, 0 errors; R0 completed and M4 reached its corresponding zero-byte Capsule JSONL guard at `FiniteKeyFlowTaskCompiler.reopenCapsules:211`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FiniteKeyFlowTaskCompilerTest,RegistryProposalTaskCompilerTest test` | PASS | R0 4 tests and M4 3 tests, all 0 failures/errors/skips (7 total). |
| absolute exact-two-file `spotless:apply` for `RegistryProposalTaskCompiler.java,FiniteKeyFlowTaskCompiler.java` | PASS | `Spotless.Java is keeping 2 files clean - 0 were changed to be clean, 2 were already clean, 0 were skipped because caching determined they were already clean`. |
| same absolute exact-two-file `spotless:check` | PASS | `Spotless.Java is keeping 2 files clean - 0 needs changes to be clean, 0 were already clean, 2 were skipped because caching determined they were already clean`. |
| same combined reader selector after formatting | PASS | 7 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS (24.559 s). |

## Decisions

- Preserve the confirmed early-return approve 3 / cancel 3 fixture and null typed-boundary guard context; it remains explicitly counter-free.
- Treat malformed supported signal integrity as fatal outside the compiler's ordinary per-entry unsupported handling.
- The compiler first catches and propagates a dedicated signal-integrity exception before its existing ordinary per-entry `IllegalArgumentException`→Gap conversion, so malformed supported signal basis cannot silently become an omission/Gap.
- The guarded-else counter positive uses only private `TraversalPath` data: the invocation appears on at least one path with the boundary's recorded polarity and on no opposite-polarity path. The existing containing-block `CONTROL_CONTEXT` validation remains a basis requirement, not a polarity discriminator.
- Keep `upstreamFlowGaps` sorted contents and semantics intact; only its mutability changed so the existing per-entry `IllegalArgumentException`→Gap path can append budget dispositions.
- Replace the M1 artifact-store registration with `business-flows-flow-compilation-v2` without an old alias; keep the M1 module address, sole payload, predecessors, canonical envelope, and receipt-last sequence unchanged.
- Do not change M2/M3 readers or publication contracts in this slice; their version cascade remains a later RED.
- Resolve an exact guard branch target through exactly one node in the already validated complete public program-node union; retain all existing TRUE/FALSE, guard/polarity, same-entry ownership, evidence, and branch-cardinality gates.
- Counter basis is exactly the guard `CONTROL_CONDITION` plus boundary `INVOCATION_CALL_ID`, `STATIC_TARGET_TYPE`, `STATIC_TARGET_METHOD`, `STATIC_TARGET_SIGNATURE`, and `CONTROL_CONTEXT`, with closed Proof/Evidence/locator union and no Gap. Missing supported gates emit no counter; malformed basis remains fatal.
- M2 has no old M1 schema fallback or signal DTO: it rebuilds the persisted record with `FlowCompilation.ProcessJoinSignalV1`, whose own ID and field invariants must hold.
- M2 signal support is locator-based within the owning Flow. Repeated Evidence nodes may share one canonical locator, so source-locator closure is the distinct sorted union while every selected span with that locator supports the signal.
- M2 Capsule IDs bind sorted process-join signal IDs before sorted span and obligation IDs. M2 publisher/schema registration, M3 readers, and the existing zero-Flow projection defect remain separate RED slices.
- M2 publisher v5 preserves the Capsule projection's direct signal arrays: all 14 signal fields and locators are serialized per Capsule, and span support stays a direct `supportedProcessJoinSignalIds[]` array. Do not replace that shape to satisfy the frozen helper's object/array misuse; Luna owns the targeted test-only correction.
- An explicit empty M1 v2 `flowSlices[]` is a valid accounting state, not an outcome-closure error. The projector may return exact empty M2 arrays only after it has completed all normal M1/predessor/source/graph/Fact validation; this does not permit missing or malformed flow fields.
- M3 does not synthesize a public signal representation: public Flow v2 and Capsule v3 deep-copy the validated M1/M2 records. It requires exact canonical equality of each owning Flow/Capsule signal array, checks every Capsule-local signal's support spans and exactly one basis obligation, and derives coverage's sorted union only after that equality holds.
- R0 has no v1/v2 compatibility path: it accepts only public Flow v2/Capsule v3, reconstructs each signal through `ProcessJoinSignalV1` to preserve its complete field/identity invariants, then requires canonical Flow/Capsule array equality. A signal is provider-visible only when its owning Capsule's local spans support it and exactly one local `PROCESS_JOIN_SIGNAL_BASIS` obligation names the same exact span set; `capsuleView` remains the complete deep-copied public Capsule.
- M4 has no Capsule v2 compatibility path: it accepts only public Capsule v3 and uses its existing full `capsule.deepCopy()` in both R1/R2 task inputs. Because the persisted M4 module records those canonical input bytes unchanged, no task/publisher identity, registry/basis, budget, session, or two-Rounds-per-ready-Flow path needs alteration for this descriptor cutover.
- A zero-byte `evidence-capsules.jsonl` is the sole empty JSONL representation accepted by the current R0/M4 readers, and only after they have performed their existing public artifact validation. It maps to an exact empty Capsule collection; blank lines, whitespace, a missing terminal newline, and malformed nonempty JSONL still follow the rejection path. An empty collection does not bypass coverage, registry, ready-flow, task-shard, or Provider gates.

## Blockers

- None for this bounded compiler slice.

## Exact next action

- Release the Maven slot. Await root's next confirmed RED; do not independently begin downstream provider/runner, future shared-source behavior, or another consumer slice.

## Resume checks

- Re-read this record, the relevant RED/progress, and `git status --short --branch`; use only the root-assigned targeted Maven selector and preserve all other owners' work.
