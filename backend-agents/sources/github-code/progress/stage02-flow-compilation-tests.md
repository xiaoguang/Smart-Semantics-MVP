# Progress: Stage 02 flow compilation tests

- Status: COMPLETE
- Agent role: Stage 02 RED test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add only Stage 01 flow-ready seam tests and Stage 02 compiler behavior tests/fixtures/helpers; do not modify production code or design documents.
- Approved inputs: `docs/stages/01-proven-source-facts.md`, `docs/stages/02-flow-compilation.md`, accepted Stage 01 public implementation/tests, frozen synthetic reservation fixture, fixed jshERP eight-file inventory.
- Current branch/worktree: shared workspace; preserve existing unrelated changes.

## Completed

- Read root, prototype, backend, and source-scoped AGENTS instructions.
- Read Stage 01 and Stage 02 design documents and inspected Stage 01 public records/tests/fixtures.
- Confirmed `Stage01Analyzer` has no flow-ready seam and Stage 02 production package is absent; tests will be intentionally RED against the designed public API.

## Current state

First-batch public `flowView` contract tests and second-batch `Stage02Compiler.compile` behavior tests are authored with root-independent frozen fixture helpers. The parent implementation now lets these tests compile. The review-finding RED cycle added executable ownership, provenance, projection, budget, denominator, CFG, and Proof closure contracts; all source mutations preserve their Stage 01 request identity.

## Changed files

- `progress/stage02-flow-compilation-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage01/Stage01FlowViewContractTest.java`
- `src/test/java/com/linguan/codemd/stage02/Stage02Fixtures.java`
- `src/test/java/com/linguan/codemd/stage02/Stage02CompilerTest.java`
- `src/test/java/com/linguan/codemd/stage02/JshErpStage02AcceptanceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated changes preserved before test edits. |
| `mvn -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest test` | RED (expected) | Maven reaches `testCompile`, then javac reports missing `Stage01FlowView`/typed flow records, `Stage02Compiler`, Stage 02 request/result/profile/budget/exception records. No test execution occurs. A Java 17-incompatible `List.reversed()` in the test was corrected afterward. |
| `git diff --check -- src/test/java/com/linguan/codemd/stage01/Stage01FlowViewContractTest.java src/test/java/com/linguan/codemd/stage02 progress/stage02-flow-compilation-tests.md` | PASS | No whitespace errors in test-only changes. |
| `rg -n -C 3 'missingFlowReady|@Test|@ParameterizedTest|void ' src/test/java/com/linguan/codemd/stage02/Stage02CompilerTest.java src/test/java/com/linguan/codemd/stage01/Stage01FlowViewContractTest.java` | PASS | Contradictory missing-flow-ready test removed; remaining tests use distinct input/mutation contracts. |

| `mvn -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest,JshErpStage02AcceptanceTest test` | RED (expected) | After removing the contradictory test, javac still stops only on the not-yet-created Stage 01 flow-ready and Stage 02 public types; no test execution occurs. |
| `rg -n 'requestWithExpectedResult' src/test/java/com/linguan/codemd/stage02/*.java` | PASS | Mutated tests use the Stage01Request overload; only unchanged baseline/replay helpers use path reconstruction. |
| `mvn -Dtest=Stage02CompilerTest test` | TEST EXECUTION | Test compilation succeeds with the current parent implementation; 15 tests ran, with one additional-entry expectation failure and one production `EVIDENCE_PROJECTION_INVARIANT_BROKEN` error. The corrected XML mutations no longer fail with `mutation did not change`; production failures were not modified per task scope. |

## Decisions

- Tests will use public typed records and `Stage01Analyzer.flowView(Stage01Result)` only; no reflection or package-private graph records.
- Stage 02 tests will invoke only `Stage02Compiler.compile(Stage02Request)` and use test-local fixture construction.
- Expected RED is compile failure while the designed Stage 01 flow seam and Stage 02 public types are not yet implemented; exact compiler output will be recorded after the targeted Maven run.
- Removed the contradictory `missingFlowReadyEdgesFailsClosedBeforeTheCompilerCanGuessFromGuardLists` test: it used the exact positive request while expecting the opposite result.
- Added `requestWithExpectedResult(Stage01Request, String)` and updated proof/span mutation, prompt-injection, and additional-entry tests to preserve mutated declarations/hashes instead of rebuilding the default six-file request. Rechecked all `requestWithExpectedResult` call sites; no other mutated request loses identity.
- Corrected the two XML semantic-span mutation strings to match the frozen fixture exactly: the SELECT projection now removes `on_hand AS onHand, reserved_qty AS reserved, version`, and the UPDATE SET line now removes `, version = version + 1`. No production code was changed.
- Removed `missingFlowReadyEdgesFailsClosedBeforeTheCompilerCanGuessFromGuardLists`: it passed the unchanged positive request, so it asserted a contradictory result. No public `compile` input can independently delete a flow edge without a production/test hook; the legitimate prerequisite is covered by the Stage 01 flow-view seam contract.
- Reviewed remaining tests for same-input opposite outcomes; none found. Positive, mutated-source, profile/replay, budget, root-order, extra-entry, and fixed-jshERP requests have distinct contracts.

## Blockers

- The targeted suite executes against the parent implementation but remains intentionally RED on seven implementation findings listed below. This test-only task does not change production behavior or unrelated tests.

## Review-finding RED cycle

This cycle adds public-seam regressions for the findings in `progress/stage02-code-review.md`:

- outcome paths must carry path-specific atom/proof requirements;
- positive Flow/Capsule output must preserve all five Stage 01 expectation-gap IDs;
- a second controller calling the same service must not duplicate ownership of the eight facts;
- a decoy/comment before the request record must not redirect Capsule spans or admit prompt text;
- `maxFlowEdges` must accept the observed boundary and reject one below it;
- an entry/control-flow gap must retain a nonzero outcome denominator;
- Stage 01 CFG call/return/terminal edges and Stage 02 terminal output must remain closed through public IDs.

Only tests and the frozen synthetic fixture helper will be changed. No production code or design document is in scope.

### Added regression contracts

- `Stage02CompilerTest.eachOutcomeCarriesOnlyThePathSpecificAtomAndProofClosure` requires the four synthetic paths to accumulate strictly increasing atom and Proof closures (6/11/18/20-shaped path ownership), rather than copying all 20 atoms/proofs to every Outcome.
- `positiveFlowAndCapsuleCarryAllFiveStage01ExpectationGapProvenance` binds the positive Flow/Capsule to the five public Stage 01 expectation-gap IDs and the stable `EXPECTATION_GAP_IN_FLOW_SCOPE` reason.
- `aDeclaredSecondEntryGetsItsOwnDispositionAndCannotBorrowFactsOrSpans` now uses a second annotated Controller that calls the same service, checks two entry dispositions, non-duplicated Fact/atom ownership, closed denominators, and permits only the conservative one-compiled-entry/one-GAP shape.
- `requestBodyDecoyBeforeRealDeclarationCannotRedirectCapsuleSpan` inserts a prompt comment plus a `ReservationRequestShadow` before the real declaration and requires the exact real-record byte span with neither decoy nor prompt content.
- `maxFlowEdgesAcceptsObservedBoundaryAndRejectsOneBelowIt` derives the public flow-edge count, accepts the exact budget boundary, and requires one below it to fail with `STAGE02_RESOURCE_LIMIT_EXCEEDED`.
- `entryWithoutControlFlowRetainsGappedOutcomeDenominator` uses the public Stage 01 control-flow budget to retain the discovered entry while GAPing CFG support; the supported-outcome numerator must be zero while the four candidate terminals remain in the denominator.
- `Stage01FlowViewContractTest.callReturnAndTerminalEdgesHavePublicEndpointAndControlFlowClosure` and `Stage02CompilerTest.compiledOutputRemainsClosedOverStage01CallReturnAndTerminalEdges` assert endpoint, pairing, terminal, and output-node closure through public IDs only.
- `Stage01FlowViewContractTest.proofEdgesCloseThroughPublicRepositoryEndpointsAndOneProofPack` directly checks public ProofNode/ProofEdge endpoint closure, repository-edge linkage, required closures, and one ProofPack identity.

### Verification for this cycle

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest test` | RED (expected) | Test compilation succeeded; 27 tests ran, 7 failures, 0 errors. Passing regressions include typed CFG view closure, Stage02 output terminal/call/return closure, existing positive shape, mutation gates, replay/profile guards, and budget/canonical identity checks. |
| `git diff --check -- src/test/java/com/linguan/codemd/stage01/Stage01FlowViewContractTest.java src/test/java/com/linguan/codemd/stage02/Stage02CompilerTest.java src/test/java/com/linguan/codemd/stage02/Stage02Fixtures.java progress/stage02-flow-compilation-tests.md` | PASS | No whitespace errors. |
| `rg -n 'requestWithExpectedResult' src/test/java/com/linguan/codemd/stage02/*.java` | PASS | All mutation/extra-controller/decoy requests preserve their mutated `Stage01Request`; unchanged baseline requests use path reconstruction only. |
| `git status --short` | PASS | This cycle touched only the owned Stage01/Stage02 test sources, Stage02 fixture helper, and this progress file; existing parent/other-agent changes remain preserved. |

### RED failure summary

- Every Outcome still receives the same all-20 atom/Proof lists.
- Positive Capsule and Flow still carry no expectation-gap provenance.
- The same-service second entry still receives a second compiled Flow claiming the same eight Facts/20 atoms.
- Request-body projection selects the decoy record span (`startByte=686`) instead of the real declaration (`startByte=747`).
- `maxFlowEdges` one-below-boundary is not rejected.
- Missing control flow currently yields supported-outcome coverage `0/0` instead of preserving the gapped four-terminal denominator.
- Existing Stage01 ProofEdges use derived repository-edge IDs not linked to public flow edges, so the direct endpoint/linkage regression fails closed.

No production code, design document, or test hook was changed. Parent implementation may now consume this RED suite; the remaining failures are implementation findings, not test-helper mutation errors.

## Exact next action

Parent implementation agent should handle the reported production failures separately. The two XML mutations now match the frozen fixture and are ready for rerun after production fixes.

## Resume checks

- Re-read this file, run `git status --short`, and verify all changed paths remain test/progress-only before continuing.
