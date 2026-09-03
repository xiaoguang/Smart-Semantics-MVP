# Progress: control flow graph

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the published Program Graphs M3 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Build the bounded entry-rooted control-flow draft from sealed M1/M2 graphs and same frozen inputs. Start with exact entry, branch polarity, and terminal behavior only.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `dd5c4c1`, both implementation plans, and the sealed M1/M2 reopening contracts.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Confirmed M3 may consume only `ReopenedCodeStructureGraph`, `ReopenedCallGraph`, and the same `ReopenedProgramGraphInputs`; no raw drafts or worktree paths.
- Rebased this worktree onto the published M3 contract. The contract fixes the entry/callee
  return distinction, bidirectional M2 call-pair projection, per-entry reachability, and typed
  profile-stop disposition rules before production code begins.
- Added the public M3 records and first linear control-flow builder slice only after the expected
  absent-seam RED. The test builds M1/M2 through the real canonical store and feeds only sealed
  fresh-reopened aggregates into M3.

## Current state

- M2 closure is now installed and its direct tests pass. The linear M3 seam is green: it
  fresh-reopens M1/M2 from the canonical store, checks shared basis/profile/structure lineage, and
  projects the exact M2 call/return pairs into one entry-rooted traversal.
- A second direct test is green for `if (status == null) { return; }`: the supported condition is
  represented by one typed guard, a TRUE edge to the callee return terminal, and a FALSE edge to
  the normal continuation. It is no longer downgraded to a profile-stop Gap.
- The next RED uses `if (status == null) { throw ...; }`: the current builder downgrades it to a
  profile-stop terminal, so the test cannot find the required typed guard/throw path. This is the
  intended bounded gap before the explicit-throw slice.
- The explicit-throw slice is green: that source shape now has a TRUE guard edge to a
  `THROW_TERMINAL` backed by the exact throw statement span, while the FALSE edge remains the
  normal continuation. It does not infer a catch handler or transaction effect.
- The next RED proves that M3 is still only an in-memory draft: the public test cannot compile
  because no typed control-flow publication/reference/reopen seam exists. The next work item is
  to make this graph independently persistent before extending its accepted Java shapes.
- M3 now has a typed publisher, canonical wire mapping, and fresh-reopen reader. Its module has
  an explicit one-file contract (`control-flow-draft.json`) and records both M1 and M2 payloads as
  sealed upstream artifacts; the persistence test is green.
- A bounded `if/else` is green when exactly one branch is a terminal: the continuation edge uses
  its real TRUE or FALSE polarity and must land on the M2 call-site before the projected CALL edge.
  The other branch can be a typed explicit-throw terminal. Nested, multi-guard, dual-terminal and
  looping forms remain profile-stop Gap shapes.
- A persisted but malformed M2 graph with a missing `CALL_RETURN` pair is rejected by M3 before it
  returns a draft. This is a fail-closed predecessor-integrity gate rather than a best-effort path.
- Sol's published `72adf40` correction distinguishes structural M2 `RETURN` frame links from
  executable continuation. The direct-throw fixture is green: it retains a structural RETURN but
  creates neither a call-site continuation nor an invented controller normal-return terminal.
- Added the next bounded RED fixture: a service has one explicit-throw branch and one explicit
  normal-return branch. The intended graph must retain both branch terminals and only permit the
  caller to continue through the normal branch.
- GREEN: the bounded guard compiler now accepts exactly that mixed terminal shape. It emits both
  guarded terminal paths, preserves the M2 structural return frame, and allows the caller's normal
  continuation only because the callee has a proven normal-return branch. A loop now also blocks
  that continuation rather than turning its static return frame into a claimed execution path.
- Added the published order-determinism acceptance check: two builds from the same sealed M1/M2
  predecessors and profile must produce the same immutable control-flow draft.

## Changed files

- `progress/control-flow-graph.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlow*.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected initial RED | Missing public M3 builder/types. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Blocked by M2 predecessor | Fresh M2 call-site evidence reference has no declared provenance; M3 fails closed with `GRAPH_REFERENCE_BROKEN`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 1 test, 0 failures/errors/skips; one frozen entry exposes typed entry, basic/callee/entry terminals and exact M2 call/return projections. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 2 tests, 0 failures/errors/skips; adds a supported no-else return guard with explicit TRUE/FALSE polarity. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected RED | 3 tests; explicit `throw` guard has no typed `GUARD`/`THROW_TERMINAL` path because the current bounded builder emits a profile-stop Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 3 tests, 0 failures/errors/skips; explicit no-else throw guard yields a typed terminal with no outgoing edge. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected RED | Test compilation fails for absent `ControlFlowGraphModulePublisher`, typed reference, and persisted reader. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips; control-flow draft installs and fresh-reopens only against the same sealed M1/M2 predecessors. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest,CallGraphBuilderTest,CallGraphModulePublisherTest test` | PASS | 18 tests, 0 failures/errors/skips; includes exact if/else polarity-to-call-site coverage. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 6 tests, 0 failures/errors/skips; missing M2 call-return pair is rejected before a control-flow draft exists. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Design-blocked RED | 8 tests with 1 failure: a direct throw callee still leaves an entry normal-return terminal through M2's static `CALL_RETURN`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 8 tests, 0 failures/errors/skips; direct throw preserves structural RETURN but has no normal continuation. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected RED | 9 tests; mixed `throw/return` guard was collapsed to a profile-stop shape, so no typed guard exists. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 9 tests, 0 failures/errors/skips; mixed normal-return/throw branches and no-continuation profile stop are covered. |

## Decisions

- M3 will be implemented in vertical slices; it will not execute customer code or infer business outcomes.
- M3 will not recreate or relax M2 evidence; M2 is repaired at its producing seam before this test
  may become green.
- The first vertical slice sends unsupported `if` constructs to a typed profile-stop Gap. It does
  not yet claim general guard polarity, catch handling, loop accounting, multi-entry traversal
  ownership, module publication, or full DFS/reachability closure. One no-else guard whose then
  branch returns is supported; the explicit-throw guard is the next bounded implementation slice;
  all other branch shapes remain a typed profile-stop Gap.

## Blockers

- None. The next RED is mixed normal-return/throw continuation, which must activate post-call
  flow only from the normal branch.

## Exact next action

- Run the determinism selector. Then begin DataFlow only after its docs-only contract is committed
  and published to `origin/main`.

## Resume checks

- Read this file, run `git status --short`, confirm `259b03d` is an ancestor, validate the M2
  closure selector, then run the direct M3 selector recorded above.
