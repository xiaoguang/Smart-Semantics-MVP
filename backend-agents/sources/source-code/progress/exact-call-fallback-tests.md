# Progress: exact-call fallback negative regression

- Status: COMPLETE
- Agent role: Luna/xhigh bounded TDD test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Prepare one Step05 §8.6 public-seam negative test in `EntryRootedFlowCompilerTest` for the published exact-call fallback contract; preserve all existing assertions.
- Approved inputs: the existing `ProgramGraphsPublicFixture.createWithSharedJavaCall` real graph, the real Step03 facts and M1/M2/M3 flow chain, `ProofRuleRegistry.standardJavaBoundary`, `AtomicProofBuilder`, and public compiler/publisher seams. No production, fixture, design, other test, or Step06 changes.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve unrelated shared-worktree changes.

## Completed

- Created and intent-to-added this owned progress file before Java edits.
- No fixture modifications have been made for this slice.

## Read-only preparation

- `ProgramGraphsPublicFixture.createWithSharedJavaCall` is a real persisted graph with three exact-call candidates. The `dispatch` entry's `approvalClient.record` call also has a real `JAVA_BOUNDARY_INVOCATION` candidate with the same invocation/target tuple.
- `AtomicProofBuilder` requires `CALL_TARGET + METHOD` for exact-call `STATIC_TARGET_*` atoms, while boundary `STATIC_TARGET_*` atoms require only `CALL_TARGET`. Removing only the real standard registry's `METHOD` allowance therefore rejects exact-call facts with `PROOF_NOT_CLOSED` while leaving the matching boundary independently admissible and closed; the other boundary allowances remain intact.
- The existing candidate, proof, and Fact-ledger publishers fresh-reopen their inputs. `EntryRootedFlowCompiler` fresh-reopens the M3/graph/discovery inputs and implements the published priority: no admitted exact fact plus a closed boundary tuple yields one boundary `EXPLICIT_CALL` fallback.

## Current state

- One new public-seam test method is written and verified. It builds the real candidate set, proves with a registry derived from the standard registry minus only `METHOD`, asserts exact rejection and matching boundary admission/closed Proofs, publishes and fresh-reopens M1/M2/M3, then compiles the real Flow chain and asserts the single boundary-backed `EXPLICIT_CALL` fallback basis.
- The authorized direct selectors and exact-file formatting checks are complete. No production, fixture, design, or other-test changes were made for this slice.

## Changed files

- `progress/exact-call-fallback-tests.md` (owned; intent-to-added)
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompilerTest.java` (owned; complete)

## Decisions

- Use the real shared-Java-call fixture and its public graph/fact/flow chain; never hand-construct or mutate persisted facts/proofs as a substitute for a valid source.
- The candidate negative must prove: exact-call basis is rejected with the METHOD-less boundary; target/static boundary basis remains independently admitted and closed; the Flow compiler therefore uses the only valid `EXPLICIT_CALL` fallback if the real chain supports it.
- If the real chain produces only a Gap, or the boundary/API cannot be constructed without inventing unsupported input, stop and report the precise reason rather than changing the target or relaxing assertions.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only fixture/rule inspection | PASS | Three real exact candidates; matching boundary tuple; METHOD-less registry rejects exact basis while boundary basis remains independently closable by the published rules. |
| New test preparation and scoped `git diff --check` | COMPILE_READY | One new test method plus the METHOD-less registry/tuple helpers. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EntryRootedFlowCompilerTest#fallsBackToBoundaryExplicitCallWhenExactCallMethodProofIsUnavailable test` | PASS, numeric exit 0 (session 47975) | Tests 1, Failures 0, Errors 0, Skipped 0. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest#rejectsCrossFlowSpanOwnershipAndBareEvidenceNodeMutationsBeforeInstall test` | PASS, numeric exit 0 (session 73083) | Tests 1, Failures 0, Errors 0, Skipped 0. |
| Exact-file Spotless apply for `EntryRootedFlowCompilerTest.java` and `CapsuleProjectionModulePublisherTest.java` | PASS, numeric exit 0 (session 21581) | Two owned files processed; one changed (`EntryRootedFlowCompilerTest.java`). |
| Exact-file Spotless check for the same two files | PASS, numeric exit 0 (session 40202) | Two files checked; 0 needing changes. |
| Post-format fallback exact selector | PASS, numeric exit 0 (session 93483) | Tests 1, Failures 0, Errors 0, Skipped 0. |
| Post-format capsule exact selector | PASS, numeric exit 0 (session 26389) | Tests 1, Failures 0, Errors 0, Skipped 0. |

## Blockers

- Handoff complete. Do not run broader tests, modify production/design/fixtures/other tests, commit, or push from this slice.

## Exact next action

- Re-read this note only if the slice is resumed; no further work is authorized here.

## Resume checks

- Keep scope to this progress file and `EntryRootedFlowCompilerTest.java`; do not modify production, fixtures, design, other tests, or Step06. Do not run further Maven from this slice.
