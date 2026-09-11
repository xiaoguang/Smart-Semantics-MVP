# Progress: exact-call Step05 flow publication implementation

- Status: COMPLETE (bounded Step05 M1 publication v3)
- Agent role: Terra/xhigh M1 publication implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Approved Step05 M1 exact-call flow-compilation v3 publication handoff only. Own the publisher schema descriptor and matching M1 policy replacement; preserve `MODULE_VERSION = "v1"`, the existing payload shape/fresh-reopen behavior, and all non-M1 policies. No test, design, dependency, or Git changes.
- Approved inputs: Step05 §§8.0.1 and 8.1.2; existing `FlowCompilationModulePublisher`, M1 policy registration, and publisher test seam.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record from `progress/TEMPLATE.md` before source reading or any implementation edit.
- Read Step05 §§8.0.1 and 8.1.2, the complete current M1 publisher, the focused M1 policy branch in `AtomicCanonicalPublicationEngine`, and the complete current publisher public-seam test.
- Mapped the minimal v3 publication replacement: change only `FlowCompilationModulePublisher.SCHEMA_VERSION` from `business-flows-flow-compilation-v2` to `business-flows-flow-compilation-v3`, and replace the matching M1 `BUSINESS_FLOWS_FLOW_COMPILATION` policy condition in `AtomicCanonicalPublicationEngine` with v3. Keep the artifact type, module address/key, single `flow-compilation.json` payload, module install sequence, and `MODULE_VERSION = "v1"` unchanged.
- Confirmed fresh-reopen behavior is already at the correct seam: `publish(...)` reopens the complete predecessor set through `PersistedFlowCompilationInputReader`, validates the supplied compilation against it, recompiles from the same fixed artifacts, requires value equality, then derives the canonical envelope/ID and receipt-last install. Its payload already serializes the complete existing `ProcessJoinSignalV1` union and coverage signal IDs without a second wire or compatibility path.

## Current state

- The bounded M1 publication replacement is complete. `FlowCompilationModulePublisher` emits `business-flows-flow-compilation-v3`, and the matching M1 policy predicate admits that descriptor while retaining the same module address, filename, envelope shape, fresh-reopen/recompile equality, and producer `MODULE_VERSION = "v1"`. Luna corrected only the frozen fixture's M1 policy registration to v3; the post-format direct publisher-plus-compiler selector is green.

## Changed files

- `progress/java-exact-call-flow-publication-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisher.java` (approved v3 descriptor replacement)
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (approved matching M1 policy predicate replacement)

## File and interface map

| Responsibility | Existing seam | Authorized GREEN change after RED |
| --- | --- | --- |
| M1 canonical payload descriptor and artifact-ID preimage | `analysis/flow/compiler/FlowCompilationModulePublisher.java` | Replace only `SCHEMA_VERSION` with `business-flows-flow-compilation-v3`; the same constant already controls envelope descriptor and canonical artifact ID. Preserve `MODULE_VERSION = "v1"`, one payload, existing producer/address/upstream/control/completion fields, complete signal serialization, and fresh reopen/recompile equality. |
| M1 policy admission | `artifact/AtomicCanonicalPublicationEngine.java` | Replace only the `BUSINESS_FLOWS_FLOW_COMPILATION` schema predicate v2 → v3; retain the same BusinessFlows module 1, `flow-compiler`, filename, and module-artifact JSON envelope contract. |
| Public seam | `analysis/flow/compiler/FlowCompilationModulePublisherTest.java` | Luna-owned frozen RED/fixture migration must assert v3 descriptor and the existing full fresh-reopened signal/identity behavior. No test change belongs to this preparation. |

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| None | Not run (historical) | Preparation only; Maven was owned by Luna. |
| Luna/root `FlowCompilationModulePublisherTest` before production change | RED (confirmed) | 2 tests, 2 failures, 0 errors/skips. Both assertions expect `business-flows-flow-compilation-v3`; current descriptor/envelope emit v2. Valid 2-Flow/4-exact-call premises passed. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationModulePublisherTest test` after approved replacements | RED (unexpected test-fixture blocker) | Main/test compile passed. Surefire: 2 tests, 2 failures, 0 errors/skips. Both fail only with `ARTIFACT_POLICY_NOT_FOUND` at loaded policy-registry resolution during module install. Test assertions are v3, while `ProgramGraphsPublicFixture` line 1342 still registers only `business-flows-flow-compilation-v2`. |
| Luna/root publisher-plus-compiler selector after fixture correction | PASS | Raw Surefire: 8 tests, 0 failures, 0 errors, 0 skipped (publisher 2; compiler 6). |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute two owned Java paths> spotless:apply` | PASS | Spotless 3.10.1 selected 2 files; 0 changed, 2 already clean, 0 cache-skipped. |
| Same absolute two-file `spotless:check` | PASS | Spotless 3.10.1 selected 2 files; 0 need changes, 0 already clean, 2 cache-skipped. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationModulePublisherTest,EntryRootedFlowCompilerTest test` post-format | PASS | Raw Surefire: 8 tests, 0 failures, 0 errors, 0 skipped (publisher 2; compiler 6). |

## Decisions

- Apply only the prepared schema replacements. Do not infer a producer module-version change from payload schema alone.
- Preserve the completed M1 core progress record unchanged. This handoff covers only publication/fresh-reopen mechanics after the confirmed RED.
- The existing `publish(...)` source-reopen/rebuild check is the required fresh-reopen gate; no additional reader, payload field, adapter, or compatibility path is needed for v3.
- Do not add a v2 policy alias or alter the fixture from this production-only slice. The frozen fixture must register the v3 policy before the selector can exercise the resulting payload.

## Blockers

- No blocker in this bounded publication scope. The fixture policy registration was corrected independently to v3; no additional production change was needed.

## Exact next action

- Release Maven to root. Do not start M2/shared-span or any further Step05 slice without a separately confirmed RED and brief.

## Resume checks

- Do not modify Java, tests, formatter state, design, dependencies, or Git state. Do not start M2, Capsule, public, or Step06 work.
