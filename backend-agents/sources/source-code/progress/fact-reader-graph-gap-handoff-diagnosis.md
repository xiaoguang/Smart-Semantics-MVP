# Progress: Fact reader graph-gap handoff diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh bounded read-only contract debugger
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Determine whether a fully accounted Program Graph with local Gaps and `coverage.closed=false` is valid input to Fact enumeration, classify the current `PersistedFactCandidateInputReader.validateCoverage` rejection, and state the minimum existing-contract correction.
- Approved inputs: Existing Step 03/04 design, current production/test source, and current raw Surefire report. No Maven, code/test/design/schema edits, new rules/records, source/network/Provider action, commit, push, or sub-agent.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve all unrelated shared-worktree changes.

## Completed

- Reused the completely read repository/backend/source instructions and systematic-debugging workflow from the immediately preceding bounded diagnosis.
- Read the current raw `DiscoveryToFactHandoffTest` report before task-local changes: 1 test, 1 failure, 0 errors, 0 skipped; real discovery and graph publication now reach the Fact reader, which rejects CALL `coverage.closed=false` at `PersistedFactCandidateInputReader.java:1159`.
- Confirmed `CallGraphBuilder.finish` persists a complete candidate partition and sets `closed` false when any local Gap exists.
- Confirmed the published Step 03 invariant: a local Gap fully disposes its candidate and can coexist with `coverage.closed=true`; successful M1–M4 drafts require `coverage.closed == scopeGapIds.isEmpty()` (`03-program-graphs.md:796-824`). `closed` therefore reports repository-scope closure, not the absence of semantic local Gaps.
- Confirmed this integration uses `COMPLETE_CAPTURE` with `repositoryCompletionEligible=true` (`DiscoveryToFactHandoffTest.java:74-83`), so its canonical scope set is empty and each program graph must publish `closed=true` even when it has local Gaps.
- Confirmed Step 04 consumes a complete installed ProgramGraphs publication and enumerates only frozen exact joins. An unresolved/ambiguous call has no exact edge, remains a Step 03 Graph Gap, and must not be guessed back into a Fact candidate (`04-proven-code-facts.md:259-283`). Broken graph endpoints/publication/schema/root/controls remain fatal, so accepting this valid local Gap does not weaken reference integrity.
- Compared the builders: CODE_STRUCTURE correctly derives closure from repository scope (`CodeStructureGraphBuilder.java:868-880`), while CALL incorrectly adds `gapDispositions.isEmpty()` (`CallGraphBuilder.java:567-575`). CONTROL_FLOW and DATA_FLOW contain the same adjacent predicate drift (`ControlFlowGraphBuilder.java:997-1003`, `DataFlowGraphBuilder.java:157-166`), but they are not the first observed failure in this bounded diagnosis.

## Current state

- Root cause is a production producer-contract bug, not an invalid test bootstrap and not evidence that the Fact reader should ignore malformed coverage. The CALL graph has a closed, disjoint candidate partition, but `CallGraphBuilder` conflates a fully accounted local Gap with incomplete repository scope and serializes the wrong `closed=false` bit.
- Minimum Terra correction for the observed RED: in `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java:574-575`, replace the local-Gap predicate with closure derived solely from the already-copied scope set, i.e. `inputs.structure().draft().coverage().scopeGapIds().isEmpty()`. Preserve candidates, exact elements, gap dispositions/drafts, owners, evidence, IDs, and all edges unchanged.
- Expected invariant after that correction: with this test's `S=[]`, CALL keeps every unresolved call as a local Graph Gap but publishes `coverage.closed=true`; Fact M1 may enumerate supported exact joins and must not promote that unresolved call to an exact edge or Fact candidate.
- No `PersistedFactCandidateInputReader` relaxation belongs in the immediate fix. Its unconditional `closed=true` happens to be compatible with this complete-capture RED once the producer is corrected. A separate bounded-input test is still required before changing it to accept the only legitimate `closed=false` case (`S` nonempty) while checking `closed == scopeGapIds.isEmpty()` and the existing Step 03 scope/index/Gap equations.

## Changed files

- `progress/fact-reader-graph-gap-handoff-diagnosis.md` (owned diagnosis only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Raw Surefire report and exact failure-source inspection | RED evidence read | 1/1/0/0; CALL graph parse reaches `validateCoverage`, whose only failing predicate is unconditional `closed == true`. |
| Static Step 03/04 and builder/reader contract trace | COMPLETE | Published closure is `closed == S.isEmpty()`; observed CALL writer instead uses `localGaps.isEmpty() && repositoryCompletionEligible`; the test supplies `S=[]`. |

## Decisions

- Do not rerun Maven; the raw report and exact source/design contracts are sufficient for this finite ruling.
- Do not repair or broaden the separate mapper-signature discrepancy unless it is necessary to interpret the persisted Gap semantics.
- Classify the observed failure as producer underimplementation. Do not make the Fact reader accept arbitrary `closed=false`, because local Graph Gaps are already complete dispositions and broken references/accounting remain fatal.
- Keep analogous CONTROL_FLOW/DATA_FLOW predicates and bounded-scope Fact-reader acceptance as explicit adjacent follow-ups with their own direct REDs; do not expand this one observed CALL failure into a graph algorithm or pipeline redesign.

## Blockers

- None for the observed CALL fix. Bounded-scope Fact reader behavior was established by the published Step 03 invariant but was not dynamically proven in this no-Maven task; it needs a separate direct test before implementation.

## Exact next action

- Terra changes only the existing CALL `closed` predicate at `CallGraphBuilder.java:574-575`, then reruns only `DiscoveryToFactHandoffTest#handsRealDiscoveryAndGraphPublicationsToTheFactInputReader`. If a later first RED exposes the identical CONTROL_FLOW/DATA_FLOW drift, fix that producer under its direct evidence rather than relaxing the reader.

## Resume checks

- Maven was not acquired and remains free.
- No production, test, design, schema, source, network, Provider, commit, or push action is authorized.
