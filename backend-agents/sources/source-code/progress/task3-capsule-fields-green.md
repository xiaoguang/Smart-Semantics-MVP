# Progress: Task 3 capsule field reduction GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Approved cleanup Task 3 only: remove the two retired registry-proposal basis fields from Capsule projection/publication/read paths, advance their owning wire versions v8→v9 and v6→v7, and preserve all current business-material inputs.
- Approved inputs: `docs/plans/code-cleanup-and-scalable-activity-coverage-design.md` §4.4; `docs/plans/coherent-code-context-implementation-plan.md` Task 3; the existing Luna RED `CapsuleWireReductionTest`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the scoped instructions, the approved Task 3 contract, and the existing public RED test.
- Confirmed the worktree starts with only Task 3 Luna RED/progress files.
- Observed the supplied RED: v8/v6 remain registered and emitted, and both retired fields remain in projection and public Capsule JSON.
- Removed `registryProposalBasisAtomIds` and `registryProposalBasisGapIds` from the Capsule
  record, projector construction and canonical projection JSON.
- Advanced only the owning schema registrations and direct consumers to capsule-projection v9
  and evidence-capsule v7. Retired v8/v6 policies are no longer registered.
- Confirmed the RED test now publishes/reopens v9/v7, rejects both retired policies and retains
  entry context, facts, gaps, outcomes, process-join signals and source-span closure.

## Current state

- Task 3 wire reduction is complete. The retired fields and versions occur only in the Luna RED
  test's negative assertions.
- One existing full provenance-class test remains independently red:
  `BusinessFlowProvenanceTest#rejectsSelfConsistentRehashedFactMutationBeforeStep05Receipt`.
  It exercises unchanged `FlowPublicationSpecifier.validateFactOrigins`, which currently checks
  a fact ID and origin reference but not an embedded atom value. The focused provenance selector
  that verifies the v7 published carrier passes. This task does not alter that broader Fact
  replay behavior.

## Changed files

- `progress/task3-capsule-fields-green.md`
- Capsule projection/publication source, owning artifact policy registry, and direct tests listed
  in the approved Task 3 scope.
- `ProgramGraphsPublicFixture` and direct version assertions for the two changed wire contracts.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only Task 3 Luna RED/progress files existed before this progress file. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleWireReductionTest test` | RED | 1 test, 12 expected assertions: v8/v6 remain, retired fields remain, and retired policies resolve. |
| `mvn -o -t .mvn/toolchains.xml clean test -Dtest=CapsuleWireReductionTest` | PASS | 1 test; v9/v7 publish/reopen and v8/v6 rejection pass. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` | PASS | 7 tests. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest test` | PASS | 4 tests. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowsPublicationSpecifierTest,BusinessFlowCoverageTest,BusinessFlowProvenanceTest test` | PARTIAL | Publication 4/4 and coverage 1/1 pass; provenance 5/6 pass with one unchanged stale-atom replay failure outside Task 3. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest#publishesFreshReopenedM3FactAndSourceGapProvenanceWithPublishedCarrierVersions test` | PASS | 1 test; confirms v7 carrier provenance. |
| Targeted `spotless:check` for Task 3 main/test paths | PASS | All changed Java source/test paths clean. |
| Whole `spotless:check` | EXISTING FAILURE | 20 non-Task-3 files require formatting; no broad formatting applied. |
| `git diff --check` and `git diff --cached --check` | PASS | No whitespace errors. |

## Decisions

- Preserve `entryContext`, fact/gap/outcome views, process-join signals, source spans and all other Capsule content.
- Do not touch Activity, Process, Report, documents, or the Task 2 progress file.
- Advance only the two owning wire schemas: projection v8→v9 and public evidence capsule v6→v7.

## Blockers

- None.

## Exact next action

- Return the Task 3 result to the parent agent; do not commit or push.

## Resume checks

- Re-read this file and check `git status --short`. Do not reopen completed Task 3 unless a
  review identifies a wire-specific defect.
