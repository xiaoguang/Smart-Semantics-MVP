# Progress: business process interpretation mixed-shard implementation

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Implement only the frozen M8 whole-M7 mixed-shard execution publication: one `MODEL_SAFE` P1/P2 KEEP terminal plus two `NO_MODEL` terminals, receipt-last aggregate checkpoint, and fresh-reopen verification.
- Approved inputs: Scoped `AGENTS.md`, `docs/DESIGN.md`, Step 06 §§6.4–7.2, `progress/m8-multi-shard-publication-design.md`, the completed Luna RED, and the current M6/M7/M8 seams.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the frozen mixed-shard design and the confirmed assertion-only RED.
- Confirmed the shared worktree is dirty from other active work and will be preserved.
- Added the bounded aggregate request/publisher, receipt-last checkpoint-set type registration, and
  the one required fixture policy entry after production registration correctly exposed
  `ARTIFACT_POLICY_NOT_FOUND`.
- Passed the mixed three-shard public seam and the two retained singular M8 regression paths after
  the Sol rulings aligned the test oracle with the existing module-reference and unique-gap contracts.

## Current state

- The mixed M7 fixture has three relation-owner shards: one `MODEL_SAFE` and two `NO_MODEL`.
- The aggregate execution invokes the strict P1/P2 runner exactly once for the safe shard and
  records two no-model terminals with their M7-owned Gap IDs and zero Provider calls.
- The checkpoint-set receipt installs last, uses the sorted unique union of terminal Gap IDs, and
  fresh-reopens the aggregate payload before returning its module publication reference.

## Changed files

- `progress/business-process-interpretation-mixed-shard-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (one approved
  exact artifact-policy entry only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Pre-edit design, test, and source inspection | PASS | Frozen contract and existing M7/M8 seams verified. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationExecutionPublisherTest#publishesOneModelSafeAndTwoNoModelShardsAsOneClosedM8Result test` | Compile RED | Two local type errors were isolated: generic comparator inference and policy-reference serialization. |
| Same direct selector after compile correction | Expected policy failure | `ARTIFACT_POLICY_NOT_FOUND` for the exact new checkpoint-set pair. |
| Same direct selector after approved policy entry | Design/test mismatch | Production emits the design-required unique `gapRefs` union; initial RED expected the shared Gap twice. |
| Same direct selector after Sol module-reference ruling | PASS | 1 test; 0 failures, 0 errors, 0 skipped. |
| Direct mixed selector plus two singular M8 checkpoint regressions after Spotless | PASS | 3 tests; 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=<four changed production paths>` | PASS | Scoped production formatting applied. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=<four changed production paths>` | PASS | Scoped production formatting is clean. |
| `git diff --check -- <owned paths>` | PASS | No whitespace errors in the bounded change set. |

## Decisions

- The aggregate publisher will invoke the existing strict single-shard runner only for the unique model-safe shard.
- No-model terminals will copy their M7-owned gap values without Provider calls or source reparse.
- The new checkpoint-set wire is registered with the same M8 address/file and a new exact type/schema;
  the only fixture change is the matching sealed artifact-policy registration authorized by the parent.
- Do not make receipt `gapRefs` duplicate an upstream Gap: the frozen design requires the sorted
  unique union.
- Do not add an invented `artifactId` member to the strict M7 module-publication reference.
- Sol corrected the test oracle to compare the exact existing three-field
  `ModulePublicationReference` and to assert that no `artifactId` is serialized.

## Blockers

- None within this bounded implementation. Cross-flow M9/Step07 work is intentionally outside scope.

## Exact next action

- Parent may integrate this completed M8 vertical slice with the broader Step 06 closeout work.

## Resume checks

- Do not modify tests, docs, schemas, fixtures, M6/M7 behavior, Provider adapters, or public product APIs.
- Before Maven, verify no other heavy Maven command is running.
