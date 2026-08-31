# Progress: Stage03 reader density core

- Status: COMPLETE
- Agent role: Stage03 M7 reader-density production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Represent each M6 public object, activity, relation, and answerable question as one
  typed reader item in its fixed section, without duplicating atom ownership.
- Approved inputs: Scoped AGENTS, TDD/systematic-debugging guidance, Stage03 M7 design and
  public reader-density seam, current reader plan/contract/render implementation and public records.
- Current branch/worktree: Shared worktree; preserve all unrelated existing changes.

## Completed

- Created this owned progress file before production edits.
- Read Stage03 M7 §8.2--§8.8, the full public density contract and its test progress, public
  M6/Reader records, plus the planner, renderer, and frozen reader-contract implementation.
- Reproduced the public selector: 1 test / 1 `MultipleFailuresError` with 20 assertions. The
  Stage01→Stage02 preconditions and nonempty M6 objects/activities/relations/questions all pass.
- Traced the missing coverage to `plan`: it emits atom, meaning, Gap, fallback, outcome and
  formula items only; none refers to the M6 object/activity/relation/question records.
- Confirmed the renderer already has a closed typed-slot seam and the empty-section pass runs
  after existing items, so adding valid items before it prevents only the relevant empties.
- Provisional fixed templates and the four M6 plan projections produced narrow GREEN, then were
  reverted after interpreting shared basis as an owner field.
- Reverted the provisional Stage03 production change. The two directly affected legacy contracts
  are restored: `Stage03IntegrityTest,Stage03SemanticTest` passes 12 tests with no failures/errors.
- The parent clarified the established non-owning reference invariant, matching the existing
  Formula correction: M6 item `basisAtomIds` must be empty, while its references include the
  M6 public ID and each current atom-owner ReaderItem key. This is representable without a new
  public record or test migration.
- Restored fixed internal templates/owners and created one typed item per M6 object, activity,
  relation, and answerable question. Object/activity/relation provenance is reference-only;
  question references its own public ID and all validated answer-source public IDs.
- Narrow GREEN under the corrected invariant: `Stage03ReaderDensityTest` passes 1 test with no
  failures/errors.
- Full Stage03 direct selector is GREEN: 57 tests with no failures/errors, including Integrity,
  Semantic, Formula, and the new reader-density contract.
- Stage01/02 direct regression is GREEN: 79 tests with no failures/errors.
- Re-ran both requested selector suites during completion verification: Stage03 57/57 and
  Stage01/02 79/79 with zero failures or errors.

## Current state

- All requested selectors and the final whitespace verification are complete.

## Changed files

- progress/stage03-reader-density-core.md
- src/main/java/com/linguan/codemd/stage03/Stage03Generator.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03ReaderDensityTest test` | RED | 1 test / 1 failure: 20 density assertions missing typed M6 items, section coverage, and M6 display/question rendering. |
| `mvn -Dtest=Stage03ReaderDensityTest test` | provisional GREEN | 1 test, 0 failures/errors before rollback; rejected because it violates existing public ownership semantics. |
| `mvn -Dtest='Stage03*Test' test` | RED | 57 tests / 2 failures: `Stage03IntegrityTest` atom ownership and `Stage03SemanticTest` first nonempty-basis ownership expectation. |
| `mvn -Dtest=Stage03IntegrityTest,Stage03SemanticTest test` | GREEN | 12 tests, 0 failures/errors after rollback. |
| `mvn -Dtest=Stage03ReaderDensityTest test` | GREEN | 1 test, 0 failures/errors with non-owning reference-only M6 items. |
| `mvn -Dtest='Stage03*Test' test` | GREEN | 57 tests, 0 failures/errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | 79 tests, 0 failures/errors. |
| `mvn -Dtest='Stage03*Test' test` | GREEN | Completion verification: 57 tests, 0 failures/errors. |
| `mvn -Dtest=CapabilityAccountingTest,JshErpStage01AcceptanceTest,JshErpStage02AcceptanceTest,ProofMutationTest,ProofSemanticClosureRegressionTest,ProvenFactExtractionTest,RepositoryIntegrityRegressionTest,RepositoryUnderstandingMyBatisTest,RepositoryUnderstandingRouteCallTest,Stage01FlowViewContractTest,Stage02CompilerTest,VerifiedSnapshotContractTest test` | GREEN | Completion verification: 79 tests, 0 failures/errors. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- M6-derived items cite their public records and current atom-owner ReaderItem keys through
  references; their `basisAtomIds` remain empty so only the original atom items own atoms.
- Use fixed internal templates/owners for the four M6 reader kinds, so existing external custom
  registries keep their frozen ownership contract and cannot redirect a fixed M7 section.
- M6 reader items are M6 references, not atom owners; the pre-existing atom disposition map is
  deliberately unchanged and still owns every admitted atom exactly once.
- The approved invariant is reference-only: M6 items keep `basisAtomIds` empty and cite their
  public M6 record plus the existing atom-owner ReaderItem keys. This preserves exact provenance
  without becoming a second atom owner.

## Blockers

- None.

## Exact next action

- None; this vertical slice is complete.
