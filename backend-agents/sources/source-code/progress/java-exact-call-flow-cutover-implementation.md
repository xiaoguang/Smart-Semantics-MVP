# Progress: exact-call Step05 flow cutover implementation

- Status: COMPLETE (bounded Step05 M1 core positive)
- Agent role: Terra/xhigh M1 core implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-09
- Scope: Approved Step05 M1-core positive exact-call cutover only: fresh Step04 v3 input plus unique exact-call signal priority in the flow compiler. No publisher, Capsule, public, Step06, test, design, dependency, or Git changes.
- Approved inputs: Step05 §§8.1.2, 8.4, and 8.6; the existing Step04 v3 M1/M2/M3 publication contracts; and the current flow-compilation/module-contract seams.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` from main `9e690af0086400b9c6a8c3fd7b627c66dbf43db4`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record from `progress/TEMPLATE.md` before any implementation work.
- Read Step05 §§8.1.2, 8.4, and 8.6, the active flow-signal handoff and implementation plans, and the named current M1/M2/M3/public-reader module contracts.
- Mapped the M1 v3 input seam: `PersistedFlowCompilationInputReader` currently requires the complete Step04 v2 four-file set and only admits boundary/guard Fact subjects. It already validates the full ProgramGraphs node/edge ID union and Evidence closure, but must retain typed node kind/canonical metadata for `JAVA_EXACT_CALL`'s `CALL_SITE` and target `METHOD`, validate the four exact atoms and their closed Proof/edge/Evidence basis, and replace—not supplement—the Step04 v2 schema expectations with v3.
- Mapped the M1 projection seam: `EntryRootedFlowCompiler.compileProcessJoinSignals(...)` currently emits boundary type, call, external-effect, and counter material per boundary Fact. The smallest v3 change is an admitted exact-call extractor that validates the four exact atoms and closed basis, emits the existing `EXPLICIT_CALL/CALL_TARGET/INVOKES/GENERIC_TECHNICAL/FROZEN_JAVA` family with the exact `type + "#" + signature` key, and selects it only when exactly one exact Fact matches `(entryId, INVOCATION_CALL_ID, anchorKey)`. Matching boundary call emission is then suppressed; boundary type, counter, and external-effect paths remain unchanged. Zero admitted exact matches is the valid independent boundary fallback (or no call signal if neither basis closes); duplicate exact tuples, exact/boundary tuple conflicts, or non-unique selected closure are `PROCESS_JOIN_SIGNAL_BASIS_INVALID`.
- Read Luna's frozen `prioritizesPersistedExactCallBasisForEachEligibleSharedCallTuple` RED: all three persisted exact Facts/Proofs/METHOD premises pass, then the unchanged reader rejects the v3 material before compiler projection. The shared boundary owner check is correctly `contains(entryId)`, not a singleton-owner requirement.
- Mapped the M2 v6 span follow-on: `EvidenceCapsuleProjector.selectSourceSpans(...)` currently derives `spanId` from bare `evidenceNodeId` and globally indexes selected spans by it, so `SpanBuilder.requireOwningFlow(...)` rejects a second legitimate Flow. Replace that selection/identity key with `(flowSliceId,evidenceNodeId)` and the Step05 v2 span-ID preimage. Keep support sets Capsule-local; do not add a `flowSliceId` field to `ModelEvidenceSpan`, merge support across Flows, or change obligation/capsule field shapes.
- Mapped the replacement-only version cascade and downstream readers below.
- Completed the bounded M1 v3 reader/compiler cutover. The reader now consumes the four complete Step04 v3 payloads, preserves typed ProgramGraph and Evidence closure data for admitted exact Facts, and validates the required call-site/call-target/METHOD Proof pairs by atom family. The compiler projects every unique exact tuple and applies exact-over-same-tuple-boundary call priority without changing the boundary type, counter, external-gap, or no-exact fallback paths.
- Corrected one reader-only closure mapping found by the authorized positive selector: `INVOCATION_CALL_ID` requires both the call-site and call-target-edge Java pairs; each `STATIC_TARGET_*` Proof requires the call-target-edge Java pair and the target-METHOD parser pair. This mirrors the frozen M2 selection, preserves the public typed atom values, and introduces no new schema or rule.

## Current state

- The M1 reader accepts only the complete Step04 v3 four-file set, retains typed Program node/edge/Evidence material, validates the admitted exact Fact's four typed atoms and closed Proof basis, and accepts a shared boundary owner when it contains the compiled entry. `EntryRootedFlowCompiler` extracts every unique admitted exact tuple, emits its existing exact-basis `EXPLICIT_CALL`, and suppresses only a same-tuple boundary call while preserving boundary type, counter, external-gap, and no-exact fallback behavior.
- The authorized selector is green both before and after formatting. The public target type/method/signature values match the prescribed target canonical splitting; the closure mapping uses call-site plus call-target-edge Java pairs for `INVOCATION_CALL_ID`, and call-target-edge Java plus METHOD parser pairs for each `STATIC_TARGET_*` Proof. No Maven or formatter process is active. The implementation preserves the existing `FlowCompilation.ProcessJoinSignalV1` record and M2/M3 deep-copy closure: exact-call changes the selected Fact/atom/Proof/Evidence basis and therefore its ID, not the signal wire fields or five-public-file shape.

## Changed files

- `progress/java-exact-call-flow-cutover-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java` (bounded v3 reader and exact closure mapping)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java` (bounded exact tuple projection/priority)

## Concrete file and interface map

| Cutover responsibility | Current seam | Smallest eventual production change |
| --- | --- | --- |
| M1 fresh Step04 v3 facts | `analysis/flow/compiler/PersistedFlowCompilationInputReader.java` | Replace four Step04 v2 payload schemas with v3 and extend the persisted graph/fact view only enough to validate exact `CALL_SITE` + `METHOD`, target canonical value, four typed atoms, and the existing closed Proof/Evidence/program-edge references. |
| M1 exact-call priority | `analysis/flow/compiler/EntryRootedFlowCompiler.java` | Add exact-call basis extraction before boundary `EXPLICIT_CALL` emission; use the unique same-entry tuple priority, retain boundary type/counter/gap logic, and preserve all existing signal-budget/identity/flow-closure gates. |
| M1 v3 artifact | `analysis/flow/compiler/FlowCompilationModulePublisher.java` | Replace the sole M1 payload schema registration with `business-flows-flow-compilation-v3`; retain one payload, predecessor set, receipt-last sequence, and rebuild-equality check. |
| M1 policy | `artifact/AtomicCanonicalPublicationEngine.java` | Replace only the M1 flow-compilation policy schema v2 → v3. |
| M2 v6 M1/Fact readers and spans | `analysis/flow/capsule/EvidenceCapsuleProjector.java` | Require M1 v3 and Step04 v3, retain generic Fact/Proof projection, and make span selection/ID per `(flowSliceId,evidenceNodeId)` using the mandated v2 span domain/preimage. |
| M2 v6 artifact | `analysis/flow/capsule/CapsuleProjectionModulePublisher.java` | Replace the sole M2 projection schema v5 → v6; serialize the existing complete Capsule/spans/obligations without new fields. |
| M2 policy | `artifact/AtomicCanonicalPublicationEngine.java` | Replace only the M2 capsule-projection policy schema v5 → v6. |
| M3 public projection | `analysis/flow/publish/FlowPublicationSpecifier.java` | Require M1 v3/M2 v6; replace `flow-slices` v2 → v3 and `evidence-capsules` v3 → v4 while preserving coverage/entry/gap v1, five semantic payloads, receipt/root, exact Flow/Capsule signal equality, and Capsule-local span/obligation closure. |
| M3 public policies | `artifact/AtomicCanonicalPublicationEngine.java` | Replace only `flow-slices` v2 → v3 and `evidence-capsules` v3 → v4 policy schemas; leave the other three public file policies v1. |
| Step06 R0 reader | `analysis/interpretation/proposal/RegistryProposalTaskCompiler.java` | Replace public Flow v2/Capsule v3 descriptor and line expectations with v3/v4; retain complete capsule view, eligibility, closure, identities, and zero-task behavior. |
| Step06 M4 reader | `analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java` | Replace only public Capsule v3 descriptor/line expectations with v4; retain its complete deep-copied Capsule input, registry/basis checks, and R1/R2 task identity/cardinality. |

The public `BusinessFlowsReference`, `FlowCompilation`, and `CapsuleProjection` record field shapes need no new adapter or compatibility type. Their existing signal/span IDs naturally change from the mandated new basis/identity values.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| None | Not run | Preparation is read-only; Maven remains owned by Luna. |
| Luna/root `EntryRootedFlowCompilerTest#prioritizesPersistedExactCallBasisForEachEligibleSharedCallTuple` | RED (confirmed) | 1 test, 1 failure, 0 errors: persisted v3 exact Fact/Proof/METHOD premises pass; the unchanged reader rejects the v3 input before projection with `UPSTREAM_ARTIFACT_REPLAY_MISMATCH`. |
| Maven/Spotless after initial M1 reader edit | HELD (historical) | Root's brief design-publication hold; no command started. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest#prioritizesPersistedExactCallBasisForEachEligibleSharedCallTuple test` | RED (historical) | Main and test compilation completed; Surefire: 1 test, 1 failure, 0 errors. `UPSTREAM_ARTIFACT_REPLAY_MISMATCH` at `PersistedFlowCompilationInputReader.requireExactCallClosure` after all persisted exact Fact/Proof/METHOD setup passes. Source inspection identified the static-atom call-site/edge Evidence-subject mapping mismatch. |
| Same exact selector after the closure-mapping correction | PASS | Surefire: 1 test, 0 failures, 0 errors, 0 skipped. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute two owned Java paths> spotless:apply` | PASS | Spotless 3.10.1 selected 2 files; 2 changed to clean, 0 already clean, 0 cache-skipped. |
| Same absolute two-file `spotless:check` | PASS | Spotless 3.10.1 selected 2 files; 0 need changes, 0 already clean, 2 cache-skipped. |
| Same exact selector after formatting | PASS | Surefire raw report: 1 test, 0 failures, 0 errors, 0 skipped. |
| `git diff --check -- <two owned Java paths> progress/java-exact-call-flow-cutover-implementation.md` | PASS | No whitespace errors. |

## Decisions

- Preserve the frozen exact Proof topology: `INVOCATION_CALL_ID` requires call-site and call-target-edge Java pairs; each `STATIC_TARGET_*` atom requires the call-target-edge Java pair and the METHOD parser pair. This does not broaden accepted material.
- Treat all four upgrade points as a coordinated replacement: v3 M1, v6 M2, public Flow v3, and public Capsule v4. No v2/v3 or v5/v6 reader, alias, fixture path, or mixed execution is permitted.
- Do not alter `FlowCompilation.ProcessJoinSignalV1`, its signal-ID formula, the exact 5-file public output, boundary counter/effect logic, or the existing generic Fact/Proof projection merely to implement exact-call priority.

## Blockers

- This bounded M1 core scope has no blocker. Publisher, Capsule, public, and Step06 work remain held for separately confirmed REDs.
- No contract ambiguity blocks the bounded implementation. Step05 specifies payload/public schema replacement and the M2 span-ID preimage; existing `MODULE_VERSION` strings are not named by the Step05 wire contract, so they must only change if the frozen RED fixture/receipt contract explicitly requires it rather than by inference.

## Exact next action

- Release Maven to root for the frozen registry/Fact regressions. Do not run the un-migrated full M1 class or start another Step05 slice without a confirmed RED and separate brief.

## Resume checks

- Do not modify test sources, publisher/Capsule/public/Step06 code, graph/source contracts, dependencies, or Git state. Do not invent a parser fallback, clone shared source, drop legitimate shared owners, or preserve old reader compatibility.
