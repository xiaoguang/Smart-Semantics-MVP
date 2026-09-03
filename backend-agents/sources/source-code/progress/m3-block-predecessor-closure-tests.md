# Progress: M3 block predecessor closure tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer for the M3 AST-block predecessor closure
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add one public-seam control-flow test proving that an activated post-guard statement block has an executable predecessor and successor in the entry traversal.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, `progress/m3-statement-block-green.md`, and the existing `ControlFlowGraphBuilderTest` fixture.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

## Current state

The focused test is added and its intended RED is confirmed. It requires the guarded service's
first BASIC_BLOCK to reach the GUARD, the GUARD's nonterminal FALSE edge to reach the post-if
BASIC_BLOCK, and that block's NEXT edge to reach the existing service Mapper call site.

## Changed files

- `progress/m3-block-predecessor-closure-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Compile failure | Test-only mistake: AssertJ `singleElement()` was called on a Java `Stream`; no test assertions ran. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected RED | 12 tests, 1 failure, 0 errors, 0 skipped. `connectsEveryActivatedServiceBlockThroughExecutableGuardAndCallPredecessors` failed because the current graph has the guard's FALSE edge targeting the call site, not the post-if BASIC_BLOCK. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The test will use the guarded service fixture (`if (status == null) return;` followed by the mapper call).
- The test will identify the post-if BASIC_BLOCK by its exact parsed statement span and the contained call by its existing M2 call-site provenance.
- The assertions will require the guard's nonterminal polarity to target the post-if block and a `NEXT` edge from that block to the call site. They will not accept block membership alone.
- Existing tests encoding the old direct guard-to-call-site shape will remain unchanged; Terra must update the implementation while preserving their broader branch assertions.

## Blockers

None.

## Exact next action

Terra should update only the M3 control-flow implementation so the nonterminal guard branch reaches
the post-if BASIC_BLOCK and that block reaches the call site; then rerun the existing
`ControlFlowGraphBuilderTest` selector. This test writer will not alter production code or old
direct guard-to-call assertions.

## Resume checks

Read this progress file, run `git status --short`, and confirm the RED remains attributed to the
guard-to-post-block predecessor closure before Terra begins the GREEN change.
