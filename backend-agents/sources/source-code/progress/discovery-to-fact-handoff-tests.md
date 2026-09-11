# Progress: discovery-to-fact handoff RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded integration test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One real ApplicationDiscovery → ProgramGraphs → PersistedFactCandidateInputReader handoff test using synthetic verified source bytes and canonical stores.
- Approved inputs: Existing public `ApplicationDiscoveryExecutor`, `ProgramGraphsExecution`, `PersistedFactCandidateInputReader`, canonical policy/store contracts, and a test-local verified-source predecessor.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the source-scoped instructions, both source-code implementation plans, TDD guidance, and completed closeout contract ruling.
- Confirmed the test must use real ApplicationDiscovery and ProgramGraphs producers; existing handcrafted discovery fixtures are not valid inputs for this slice.
- Inspected the public execution APIs and preserved the first actual cross-stage failure rather than assuming a later Fact-reader failure.
- Created this progress file before the new Java test and kept it intent-to-added throughout the slice.

## Current state

- One test-local synthetic `VerifiedSourceTextSet` uses Spring MVC source, two explicit Controller→Service→Mapper calls, and MyBatis XML; source predecessor artifacts carry matching byte/file/SHA references and controls.
- The test reopens the real discovery capability and asserts `repositoryEntryCoverage.closed=true`, positive entry count, and positive HTTP site/shard accounting before entering the real graph execution.
- The original graph-publication RED was an invalid test bootstrap: the four standalone public graph policies omitted the production `program-graphs-` prefixes. Those four test-local strings were corrected without changing draft/module policies, assertions, payloads, or production code.
- After that correction, the real ProgramGraphs publication and fresh graph reopening complete. The first downstream failure is now at the public Fact reader: `FactCandidateReferenceException: PROOF_PACK_REFERENCE_BROKEN` from `PersistedFactCandidateInputReader.parseDiscovery` line 191, reached by the test at line 143. The test's assertion reports this at line 145; no fabricated graph or Fact payload was introduced.

## Changed files

- `progress/discovery-to-fact-handoff-tests.md` (owned; intent-to-add staged)
- `src/test/java/org/sourceanalysis/app/analysis/graph/DiscoveryToFactHandoffTest.java` (owned; one integration behavior test)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress creation + `git add -N -- progress/discovery-to-fact-handoff-tests.md` | PASS | Completed before the Java test edit; progress remains intent-to-add. |
| Exact new test selector (pre-format, session 13742) | RED / exit 1 | Maven testCompile succeeded; Tests run 1, Failures 0, Errors 1, Skipped 0. First failure: `GraphReferenceException: GRAPH_REFERENCE_BROKEN` at `ProgramGraphSetPublicationSpecifier.specifyGraphSet` line 157, called by `ProgramGraphsExecution.execute` line 207. |
| Absolute one-file Spotless apply (session 40958) | PASS / exit 0 | Spotless selected exactly 1 absolute file and changed it to clean. |
| Absolute one-file Spotless check (session 18076) | PASS / exit 0 | Spotless selected exactly 1 absolute file; 0 needed changes. |
| Post-format exact selector (session 42731) | RED / exit 1 | Tests run 1, Failures 0, Errors 1, Skipped 0; testCompile succeeded. Same first failure: `GraphReferenceException: GRAPH_REFERENCE_BROKEN` at `ProgramGraphSetPublicationSpecifier.specifyGraphSet` line 157, called by `ProgramGraphsExecution.execute` line 207; no Fact-reader call occurs. |
| Four-prefix test-bootstrap correction | PASS | Only the four standalone public graph policy prefixes changed to `program-graphs-code-structure-graph`, `program-graphs-control-flow-graph`, `program-graphs-data-flow-graph`, and `program-graphs-evidence-graph`; draft/module policy prefixes remained unchanged. |
| Corrected exact method selector (session 73430) | RED / exit 1 | Numeric exit 1; `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`. First downstream failure: `FactCandidateReferenceException: PROOF_PACK_REFERENCE_BROKEN` at `PersistedFactCandidateInputReader.parseDiscovery` line 191, reached from the test at line 143 and reported by the assertion at line 145. |
| Absolute one-file Spotless apply (session 6642) | PASS / exit 0 | `-DspotlessFiles` used the absolute owned test path; exactly 1 file selected, 0 changed, 1 already clean. |
| Absolute one-file Spotless check (session 64521) | PASS / exit 0 | Exactly 1 absolute file selected; 0 needed changes. |
| Corrected post-format exact method selector (session 75083) | RED / exit 1 | Numeric exit 1; `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`. Same `PROOF_PACK_REFERENCE_BROKEN` at `PersistedFactCandidateInputReader.parseDiscovery` line 191; graph publication bootstrap remains fixed and no Fact reader workaround was added. |
| Scoped `git diff --check` | PASS | No whitespace errors in the owned progress and test paths. |

## Decisions

- Build only the verified-source predecessor locally in the test; do not claim real Capture/Inventory integration and do not modify existing graph fixture factories.
- Let ApplicationDiscovery and ProgramGraphs publish their actual canonical artifacts, fresh-reopen those publications, and pass their typed references to the public Fact reader.
- Stop at the first genuine reader failure, preserving exact root-cause evidence; do not hand-build or repair discovery/graph payloads in this test.

## Blockers

- The original graph publication failure was invalid test bootstrap and is retained above as historical evidence. The corrected test now reaches the intended first public producer→consumer RED at `PersistedFactCandidateInputReader` with `PROOF_PACK_REFERENCE_BROKEN`; the Fact-reader contract mismatch is not repaired in this slice.

## Exact next action

- Root follow-up: diagnose the real Fact-reader `PROOF_PACK_REFERENCE_BROKEN` contract mismatch in its own bounded task. Do not weaken this test, fabricate a Fact payload, or expand into the separate mapper-signature discrepancy without precise evidence.

## Resume checks

- Maven lease is released after the corrected post-format selector; exact command sessions 73430 and 75083 both completed with numeric exit 1.
- Java scope remained the new test file only; exactly four test-local standalone policy prefixes changed. No production, design, schema, fixture-factory, customer-source, network, Provider, commit, or push changes were made.
- Preserve this first-reader RED evidence; do not weaken the test to bypass graph publication or jump to fabricated Fact inputs.
