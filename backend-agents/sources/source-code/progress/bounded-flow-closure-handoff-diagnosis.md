# Progress: Bounded flow closure handoff diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh bounded read-only contract debugger
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Trace the first existing-contract blockers for a valid `BOUNDED_PATH_SET` / `repositoryCompletionEligible=false` source through real ProgramGraphs and ProvenCodeFacts publishers to BusinessFlows M3; identify minimum fixes and valid public RED constructions.
- Approved inputs: Published Step 03/04/05 designs, current source/tests/progress, and existing reports only. No Maven, production/test/design/schema edits, new rules, source/network/Provider action, commit, push, or sub-agent.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve all unrelated shared-worktree changes.

## Completed

- Re-read the applicable systematic-debugging workflow and repository/module instructions.
- Checked the shared worktree before editing and confirmed extensive pre-existing design, production, test, and progress changes owned by other tasks.
- Read the removed bounded-flow RED record. Its historical first failure is stale: current `PersistedFactCandidateInputReader.parseDiscovery` now accepts the required capability `repositoryEntryCoverage.closed` field and validates it against the typed source scope (`PersistedFactCandidateInputReader.java:273-287`).
- Confirmed the published Step 03 distinction: local Graph Gaps close discovered candidates; bounded repository incompleteness is the shared nonempty set `S`, with all four program-graph coverages requiring `closed == S.isEmpty()` (`03-program-graphs.md:736,796-824`). Public `graph-gaps.jsonl` contains only local `G`; `graph-index.gapIds` and receipts contain `G ∪ S`, while `graph-index.closed=true` remains valid artifact/catalog closure (`03-program-graphs.md:896-901,930-934`).
- Confirmed the current graph path can carry the bounded closure shape through real graph builders and M6: CODE_STRUCTURE produces a singleton scope set and `closed=false` (`CodeStructureGraphBuilder.java:868-880`); CALL copies it and now uses `scopeGapIds.isEmpty()` (`CallGraphBuilder.java:567-574`); CONTROL_FLOW/DATA_FLOW copy the same set and are false for a bounded source (`ControlFlowGraphBuilder.java:997-1003`, `DataFlowGraphBuilder.java:157-166`); M6 requires the shared set, publishes only local rows, and places `G ∪ S` in index/receipts (`ProgramGraphSetPublicationSpecifier.java:415-518,609-616`). Existing `RepositoryScopeGapTest.java:145-351` exercises that shape, though it is not the requested end-to-end Flow test.
- Traced the exact current Fact blockers. Each public program graph reaches `validateCoverage`, which unconditionally requires `closed=true` (`PersistedFactCandidateInputReader.java:1172-1182`). After that is corrected, `validateIndex` still incorrectly requires `index.gapIds == graph-gaps row IDs` (`:1037-1039`), which compares `G ∪ S` to `G` and therefore rejects every valid bounded publication with nonempty `S`.
- Confirmed Step 04 does not require repository completion. It requires one complete installed seven-artifact ProgramGraphs publication and preserves exact candidate/proof accounting; unresolved/ambiguous calls stay Step 03 Graph Gaps and are not guessed into Fact candidates (`04-proven-code-facts.md:165-181,259-285`). A bounded publication may therefore proceed through Fact M1–M3 once its valid scope accounting is accepted.
- Traced the Flow closure loss. `PersistedFlowCompilationInputReader.parseDiscovery` reads only profile and entry lines and drops capability coverage entirely (`PersistedFlowCompilationInputReader.java:250-274`), although Step 05 requires that boolean (`05-business-flows.md:159-165`). M1 module `coverage.closed=true` is intentional local entry/Flow denominator closure (`FlowCompilationModulePublisher.java:408-471`); it does not mean repository closure. `FlowPublicationSpecifier` reopens discovery but never reads its coverage and writes public `flow-coverage.json.closed=true` unconditionally (`FlowPublicationSpecifier.java:95-137,377-438`), violating the fixed conjunction in Step 05 (`05-business-flows.md:187-215`).

## Current state

- Execution-order classification:
  1. **Fact reader blocker 1:** valid M1–M4 `closed=false` is rejected before any Fact candidate enumeration.
  2. **Fact reader blocker 2:** after blocker 1, valid `index.gapIds=G ∪ S` is rejected against local-only graph-gap rows `G`.
  3. **Flow reader contract loss:** after Facts publish, M1 can execute but its input reader does not validate/carry the required discovery repository-closure bit. This is over-permissive rather than an immediate exception.
  4. **Flow M3 wrong output:** final public coverage is hardcoded true even though the bounded discovery predecessor is false.
- These are production contract underimplementations. A bounded source is not an invented `closed=false` exception: `S` is nonempty and all discovered graph/Fact/entry denominators must still close exactly. Broken refs, unknown candidate denominators, missing Gap rows, or malformed index coverage remain fatal.

## Changed files

- `progress/bounded-flow-closure-handoff-diagnosis.md` (owned diagnosis only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped `git status --short` | Read-only baseline captured | Shared worktree is dirty; no existing change will be modified. |
| Static Step 03→04→05 trace | COMPLETE | Valid bounded closure reaches current Fact graph coverage check, then the index `G ∪ S` versus `G` mismatch; Flow M3 later loses the discovery bit and writes true. |
| Existing raw bounded-flow progress | Historical blocker classified | Prior 1-test run stopped before Fact M1; its capability-field rejection has since been fixed, but no current Maven rerun was authorized. |

## Decisions

- Do not acquire Maven; Luna holds the sole lease for an unrelated selector.
- Use only current published contracts and current real fixture/publisher/reader source. Do not fabricate a graph or weaken any accounting equation.
- **Minimum Fact fix:** change only the graph-coverage/index validation in `analysis/fact/candidates/PersistedFactCandidateInputReader`. Parse each M1–M4 coverage, require its candidate/exact/Gap/exclusion disjoint partition, require all four `scopeGapIds` to equal the same canonical `S`, and require `closed == S.isEmpty()`. Keep Evidence coverage and `graph-index.closed` unconditionally true. Parse local graph-gap IDs as `G` and require `index.gapIds == sortedDistinct(G ∪ S)` plus the published index-coverage projection; never merely permit arbitrary false.
- **Minimum Flow fix:** make `PersistedFlowCompilationInputReader.parseDiscovery` require and cross-check the real capability coverage boolean against profile scope/eligibility and entry denominator, while keeping M1 module `coverage.closed` as local closure. In `FlowPublicationSpecifier`, fresh-read the same discovery boolean, require persisted M1 local coverage and public entry/Flow accounting to close, then write `discoveryClosed && m1Closed && publicAccountingClosed` instead of the literal at line 431. This uses existing fields and schemas.
- Do not add the repository scope Gap to Step 04 Fact Gaps or Step 05 local Flow Gaps. It remains a predecessor repository-coverage bit/set, not a fabricated entry-owned semantic Gap.
- Use one distinct bounded variant of `ProgramGraphsPublicFixture`, created at the source boundary before discovery: its typed source uses `BOUNDED_PATH_SET` and `repositoryCompletionEligible=false`; the existing real ApplicationDiscovery publisher and `ProgramGraphsExecution` then publish the downstream bytes. Do not mutate/republish only discovery and do not hand-build graph JSON.
- Build two focused public REDs from that same fixture, in order:
  1. A Fact handoff test asserts discovery coverage false; the four program graphs share one nonempty `S` and have false coverage; `graph-gaps.jsonl` contains only `G`; index/receipts contain `G ∪ S`; then `PersistedFactCandidateInputReader.reopen(...)` succeeds. Before the Fact fix it fails first at line 1182, then exposes line 1038.
  2. After that test is GREEN, a `BusinessFlowsPublicationSpecifierTest` runs the existing real `publishProvenFacts` path, real Flow compiler/M1 publisher, Capsule projector/M2 publisher, and M3 `specify`; it asserts persisted M1 local coverage remains true and public `flow-coverage.json.closed` is false. This reaches and isolates the literal at `FlowPublicationSpecifier.java:431`.
- The bounded fixture remains a synthetic downstream public-seam fixture, not a claim that the fixed repository capture has completed or that a full Step01 bounded capture was accepted.

## Blockers

- No design/schema ambiguity. Implementation and RED execution belong to Luna/Terra owners with the Maven lease; this task was read-only.

## Exact next action

- Luna adds only the bounded fixture variant and the first Fact-reader public RED. Terra implements the strict `S`/`G ∪ S` reader validation. Once GREEN, Luna adds the final M3 public RED and Terra replaces the hardcoded output with the published three-term conjunction, including required discovery/M1 validation.

## Resume checks

- Maven was not acquired and remains unavailable to this task.
- No production, test, design, schema, source, network, Provider, commit, or push action is authorized.
