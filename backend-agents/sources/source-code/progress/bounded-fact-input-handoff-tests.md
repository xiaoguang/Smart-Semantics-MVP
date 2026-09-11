# Progress: bounded Fact input handoff tests

- Status: COMPLETE
- Agent role: Luna/xhigh bounded public-seam test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add one explicit `BOUNDED_PATH_SET` constructed source variant before the real ApplicationDiscovery and ProgramGraphs publishers, then add one public Fact-reader handoff test proving the bounded closure/accounting premises and exercising the current first RED.
- Approved inputs: Completed `progress/bounded-flow-closure-handoff-diagnosis.md`, Step 02/03/04 public contracts, existing `ProgramGraphsPublicFixture` source boundary, real discovery/graph publishers, and `PersistedFactCandidateInputReader`. No production, design, schema, source, policy, existing fixture behavior, M3 Flow test, Maven, network, Provider, commit, or subagent action is authorized.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this progress record before editing the bounded fixture or test.
- Read the completed bounded closure diagnosis and confirmed the intended first RED is the current unconditional graph coverage `closed=true` validation, with the later index `G ∪ S` versus local `G` mismatch recorded separately.
- Added `ProgramGraphsPublicFixture.createWithBoundedPathSet(...)`, which preserves the existing two-entry documents/entry IDs and invokes the same real M1–M4 discovery and ProgramGraphs publishers with typed `BOUNDED_PATH_SET` / `repositoryCompletionEligible=false` source input and bounded-derived identities.
- Added one public `BoundedFactInputHandoffTest` that asserts discovery/graph/index/receipt/evidence closure premises and then calls `PersistedFactCandidateInputReader.reopen(...)`.
- The exact selector reached the public Fact reader after all bounded `S`/`G`/index/receipt/closure premises passed. It produced the intended first RED: `FactCandidateReferenceException: PROOF_PACK_REFERENCE_BROKEN` from `PersistedFactCandidateInputReader.validateCoverage` at line 1182, called by `parseProgramGraph` line 657 and the test line 102.
- Java is frozen; no production, design, schema, source, policy, fixture behavior, or downstream Flow changes were made.

## Current state

- The bounded factory changes only typed source scope/eligibility plus identities derived from the bounded fixture key before the real discovery and graph publishers run. The test validates a nonempty shared scope-gap set `S`, local graph-gap rows `G`, `G ∪ S` in graph index/receipt, structural index closure, Evidence closure, and public Fact-reader admission. No post-hoc JSON mutation or hand-built downstream artifact is used.

## Changed files

- `progress/bounded-fact-input-handoff-tests.md` (owned; intent-to-add before Java edits)
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (owned; one bounded-source factory and necessary source-boundary identity derivation only)
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/BoundedFactInputHandoffTest.java` (owned; one public handoff behavior test)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only diagnosis/contract/fixture inspection | PASS | Diagnosis read; bounded source boundary and public discovery/graph/Fact seams mapped. |
| Exact two-file Spotless apply | PASS (exit 0) | Absolute `-DspotlessFiles` selected exactly the owned fixture and test files; both formatted. |
| Exact two-file Spotless check | PASS (exit 0) | Absolute `-DspotlessFiles` selected exactly the owned fixture and test files; no changes required. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BoundedFactInputHandoffTest#handsBoundedGraphPublicationToFactReaderWithSharedScopeAndGapClosure test` | RED (numeric exit 1) | `Tests run: 1, Failures: 0, Errors: 1, Skipped: 0`; intended `PROOF_PACK_REFERENCE_BROKEN` at `PersistedFactCandidateInputReader.validateCoverage:1182`, after all test preconditions passed. |
| Scoped diff check | PASS | `git diff --check` clean for the three owned paths. |

## Decisions

- Keep the existing complete/default fixture factories unchanged and add one explicit bounded factory.
- Validate real persisted discovery/graph artifacts before invoking the public Fact reader: discovery `closed=false`; shared nonempty source scope `S` and graph `closed=false`; local graph Gap rows `G`; index/receipts contain `G ∪ S`; structural index/evidence closure remains true.
- Use actual persisted IDs and descriptors from reopened artifacts. Do not invent Gap IDs, flip raw JSON, hand-build graph artifacts, or proceed past missing prerequisites.

## Blockers

- The current public Fact reader rejects the valid bounded graph publication at its unconditional coverage validation (`PROOF_PACK_REFERENCE_BROKEN`, `PersistedFactCandidateInputReader.validateCoverage:1182`). This is the bounded handoff RED under test; the later graph-index `G ∪ S` versus local `G` issue was not reached and was not softened.

## Exact next action

- Release the Maven lease and hand the bounded public-seam RED to root. Do not add a Flow/M3 test or modify the Fact reader/production implementation in this slice.

## Resume checks

- Preserve the original complete fixture behavior and all existing source snippets/entry IDs except identities necessarily derived from the bounded typed source input.
- If the bounded public pipeline cannot produce truthful `S`/`G`/coverage closure, stop and report the exact first bootstrap failure rather than fabricating downstream artifacts.
- Preserve this bounded RED as the final state for the slice. Any implementation repair or follow-up index-closure behavior belongs to a separately authorized task.
