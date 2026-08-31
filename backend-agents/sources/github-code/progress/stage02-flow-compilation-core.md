# Progress: stage02-flow-compilation-core

- Status: COMPLETE
- Agent role: Stage 02 production implementation
- Model: gpt-5.6-terra
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add the narrow Stage 01 flow view and the Stage 02 deterministic compiler records/registry required by the Stage 02 contract tests; resolve the subsequent Stage 02 review-finding RED cycle in production only; do not modify stage design documents, tests, fixtures, or other agents' progress files.
- Approved inputs: `docs/stages/01-proven-source-facts.md`, `docs/stages/02-flow-compilation.md`, existing Stage 01 implementation, and current Stage 01/Stage 02 contract tests.
- Current branch/worktree: existing shared worktree; pre-existing changes preserved.

## Completed

- Checked the worktree before edits and read root, prototype, backend, and GitHub-source scoped instructions.
- Read the Stage 01 and Stage 02 implementation contracts, Stage 01 source, and the completed Stage 01/Stage 02 RED test contracts.
- Recorded the intended RED: the direct Maven selector stopped at test compilation because the Stage 01 flow-view and Stage 02 public records/compiler did not exist.
- Added the narrow Stage 01 flow view, including immutable public records, closed-reference validation, versioned CFG edges with explicit TRUE/FALSE polarity, and call/return/terminal edge kinds while retaining the internal graph records as package-private.
- Reopened the review-finding RED selector (27 tests / 7 failures) and traced each failure to production closure/accounting behavior.
- Made the public flow projection carry every ProofEdge's real public endpoint pair as an immutable `PROOF_BINDING` edge, so a proof's declared repository-edge ID is resolvable without leaking the private graph.
- Bound `REQUEST_BODY` and other typed semantic roots to exact Stage 01 node kinds; projection now follows proof-root locators rather than scanning similarly named declarations.
- Made Outcome atom/Proof requirements profile-stage/path specific and ordered shared steps by the versioned flow profile rather than hash order.
- Preserved in-scope Stage 01 expectation-gap provenance in both Flow and Capsule allowlists.
- Added entry-root ownership allocation: facts require all Proof roots in the entry's forward semantic closure, a compiled flow requires its own HTTP entry Fact, and globally claimed Fact/atom IDs are rejected by result accounting. This prevents a shared service from pulling a second caller backwards through `CALL_TARGET`.
- Enforced the observed `maxFlowEdges` envelope, retained terminal candidates for entries whose Stage 01 CFG is unavailable, and made coverage IDs include disposition/Flow/Capsule identities.
- Strengthened Stage 02 CFG/proof gates: control closures now require entry, paired call/return, branch polarity, and terminal self edges; atom pack identity, Proof root/node/edge endpoints, public repository linkage, and source-span revalidation are all checked before Capsule projection.
- Restored the source-span deletion gate with a profile-completeness check and entry-scoped rejection attribution, so a missing unlocatable required guard remains blocking without treating unrelated rejections as global ownership.
- Stage 01 flow-view contract is GREEN.
- Stage 02 public request/result records, profile checks, replay gate, entry-root CFG traversal, fact/proof closure checks, non-overlapping evidence projection, coverage accounting, and jshERP Gap behavior are implemented.
- Direct Stage 02 synthetic and fixed-jshERP selectors are GREEN.
- Existing direct Stage 01 regression selectors remain GREEN with the flow-ready CFG seam.

## Current state

- Review-finding GREEN cycle complete. All directly affected Stage 01/02 selectors and fixed jshERP acceptance are green; no further expansion is in scope.

## Changed files

- `progress/stage02-flow-compilation-core.md`
- `src/main/java/com/linguan/codemd/stage01/RepositoryGraphTypes.java`
- `src/main/java/com/linguan/codemd/stage01/RepositoryCompiler.java`
- `src/main/java/com/linguan/codemd/stage01/ProvenFactCompiler.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01Analyzer.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01FlowView.java`
- `src/main/java/com/linguan/codemd/stage01/FlowEntryView.java`
- `src/main/java/com/linguan/codemd/stage01/FlowNodeView.java`
- `src/main/java/com/linguan/codemd/stage01/FlowEdgeView.java`
- `src/main/java/com/linguan/codemd/stage01/FlowControlView.java`
- `src/main/java/com/linguan/codemd/stage01/FlowCapabilitySiteView.java`
- `src/main/java/com/linguan/codemd/stage01/Stage01FlowViewCompiler.java`
- `src/main/java/com/linguan/codemd/stage02/*.java` (new public records and compiler)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing Stage 01, Stage 02, design, and progress changes are present and will be preserved. |
| `mvn -q -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest,JshErpStage02AcceptanceTest test` | RED (expected) | Test compilation reported the missing Stage 01 public flow view and all Stage 02 public production types. |
| `mvn -q -DskipTests compile` | PASS | Production Stage 01 flow seam and Stage 02 public records/compiler compile on Java 17. |
| `mvn -q -Dtest=Stage01FlowViewContractTest test` | PASS | 4 direct flow-view contract tests pass. |
| `mvn -q -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest test` | PASS | 19 direct Stage 01 flow-view and Stage 02 synthetic compiler tests pass. |
| `mvn -q -Dtest=JshErpStage02AcceptanceTest test` | PASS | Fixed eight-file bounded jshERP Stage 02 acceptance passes with its honest no-Capsule contract. |
| `mvn -q -Dtest=VerifiedSnapshotContractTest,RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest,RepositoryIntegrityRegressionTest,ProvenFactExtractionTest,ProofMutationTest,ProofSemanticClosureRegressionTest,JshErpStage01AcceptanceTest,Stage01FlowViewContractTest,Stage02CompilerTest,JshErpStage02AcceptanceTest test` | PASS | All directly affected existing Stage 01 regression selectors and new Stage 01/Stage 02 contracts pass. |
| `mvn -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest test` | RED (expected) | 27 tests / 7 assertion failures; established review-finding reproductions before this production cycle. |
| `mvn -q -Dtest=Stage01FlowViewContractTest test` | PASS | Public ProofEdge repository endpoint/linkage contract is green. |
| `mvn -q -Dtest=Stage02CompilerTest#requestBodyDecoyBeforeRealDeclarationCannotRedirectCapsuleSpan test` | PASS | Request-body projection follows the exact record-declaration Proof root. |
| `mvn -q -Dtest=Stage02CompilerTest#eachOutcomeCarriesOnlyThePathSpecificAtomAndProofClosure test` | PASS | Outcome requirements grow only along the profile-defined path stage. |
| `mvn -q -Dtest=Stage02CompilerTest#positiveFlowAndCapsuleCarryAllFiveStage01ExpectationGapProvenance test` | PASS | Five warning gaps reach Flow and Capsule provenance. |
| `mvn -q -Dtest=Stage02CompilerTest#aDeclaredSecondEntryGetsItsOwnDispositionAndCannotBorrowFactsOrSpans test` | PASS | A second controller sharing the service is GAPed and cannot borrow Facts, atoms, or spans. |
| `mvn -q -Dtest=Stage02CompilerTest#maxFlowEdgesAcceptsObservedBoundaryAndRejectsOneBelowIt test` | PASS | Exact control-edge budget passes; one below fails closed. |
| `mvn -q -Dtest=Stage02CompilerTest#entryWithoutControlFlowRetainsGappedOutcomeDenominator test` | PASS | The four discovered service terminals remain in the GAP denominator. |
| `mvn -q -Dtest=Stage02CompilerTest#deletingEachSemanticEvidenceRegionCannotBeHiddenByACompleteCapsule test` | PASS | Each direct-semantic span mutation remains fail-closed. |
| `mvn -q -Dtest=Stage01FlowViewContractTest,Stage02CompilerTest test` | PASS | All 27 review-cycle Stage 01/02 contract tests are green. |
| `mvn -q -Dtest=JshErpStage02AcceptanceTest test` | PASS | Fixed eight-file jshERP remains an honest zero-Capsule GAP result. |
| `mvn -q -Dtest=VerifiedSnapshotContractTest,RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest,RepositoryIntegrityRegressionTest,ProvenFactExtractionTest,ProofMutationTest,ProofSemanticClosureRegressionTest,JshErpStage01AcceptanceTest,Stage01FlowViewContractTest,Stage02CompilerTest,JshErpStage02AcceptanceTest test` | PASS | 79 directly affected Stage 01/02 tests, 0 failures/errors. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Stage 02 will bind facts via declared graph nodes and proof closures; it will not infer paths from fixture fact IDs, source line order, or text similarity.
- The current jshERP bounded inventory has no supported complete flow and must remain an honest Gap with zero Capsules.
- CFG construction uses parsed method-block/branch AST structure, not Stage 01 fact IDs, line positions, or terminal-list order. Stage 02 receives only the public `Stage01FlowView` and public proof/fact records.
- A direct `@PostMapping` without class-level `@RequestMapping` is a supported Spring MVC entry with empty prefix; it receives a distinct entry disposition and cannot borrow another entry's closure.
- When a Stage 01 candidate rejection has no assignable node, a Flow already claiming registry facts is conservatively a blocking `FLOW_FACT_NOT_ADMITTED` Gap rather than a guessed complete Flow.
- This review cycle will keep requirements path-specific, fact ownership entry-scoped, and evidence selection proof/root-locator based; it will not add fixture-specific branches.

## Blockers

- None.

## Exact next action

- None; review-finding GREEN cycle is complete.

## Resume checks

- Re-read this file, run `git status --short`, and rerun the listed direct tests before changing production code.
