# Progress: flow-counter-compiler-debug

- Status: COMPLETE
- Agent role: Sol/xhigh bounded root-cause diagnosis; no architecture authority or production-fix ownership
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Diagnose the original compile failure in `emitsCounterConditionOnlyForProofClosedTypedBoundaryGuardContext` from the persisted public fixture/code path, then determine whether the post-enumerator-GREEN `pathContainsNode` failure is an invalid test oracle or a persisted path defect. Report the smallest independently justified test correction without changing production, tests, design, or graph contracts.
- Approved inputs: root/backend/source-code `AGENTS.md`; systematic-debugging and root-cause-tracing guidance; Step 05 §8.1.1 counter rule and §8.6/current audit; current compiler, ProvenCodeFacts candidate enumerator, ProgramGraphs builder/public fixture, focused test/report; the prior root-released temporary diagnostic; and root's reported 4/4 enumerator GREEN plus exact counter failure at `EntryRootedFlowCompilerTest:839`. No Maven/JVM for the follow-up unless separately released.
- Current branch/worktree: `codex/source-analysis-flow-signals-implementation` / `/private/tmp/linguan-source-analysis-process-design`; baseline `main` at `21bba98638dd37386829f14bd1c8cb8176908ff1`.

## Completed

- Closed the follow-up failure as an invalid test oracle, not a persisted path defect: the raw BFS cannot model the compiler's call-frame return semantics and can both miss real returned paths and invent premature returns.
- Separated Terra's fixed masking defect from the original guarded-else failure: mutable upstream Flow Gaps now preserve the entry rejection instead of throwing `UnsupportedOperationException` while appending it.
- Reproduced only the guarded-else public fixture through one temporary package-matched diagnostic test, then removed the temporary source, compiled class, and Surefire report.
- Observed the exact rejected entry result: `approve` becomes `GAP` with `reasonCode=FLOW_CONDITION_ATOM_UNPROVEN`; `cancel` remains `COMPILED`.
- Traced the missing condition backward from `EntryRootedFlowCompiler.branchDecision` to `FactCandidateEnumerator.exactGuardPath` and the real ProgramGraphs topology.
- Compared the two source shapes and identified the exact cross-graph endpoint mismatch.
- Root reports the independent ProvenCodeFacts M1 RED and minimal lookup correction are now GREEN 4/4; this diagnosis did not author or execute that change.

## Current state

- After the minimal ProvenCodeFacts cross-graph target lookup correction, root reports two compiled Flows, a typed FALSE boundary, a CLOSED guard Fact, and both Flow outcomes. The remaining failure is solely the test helper's raw CFG-only `pathContainsNode(entryRoot, falseTerminal, invocationCallId)` returning false before signal assertions.
- Persisted RETURN edges are keyed from the callee method node to the caller call site. A semantically valid return occurs only after traversal reaches `CALLEE_RETURN_TERMINAL` while holding the matching call frame. Production therefore excludes RETURN edges from ordinary structural successors and, at the terminal, uses the frame's callee/caller IDs to apply the matching persisted RETURN edge.
- The test BFS follows every edge directly, holds no call stack, and has no terminal-return transition. On the guarded FALSE path it sees the boundary invocation, reaches the service's `CALLEE_RETURN_TERMINAL`, then dead-ends before the outer entry terminal: a false negative. Conversely, from a callee method node it may follow the persisted RETURN edge before traversing the method body: a false-positive bypass.
- Smallest independently justified test correction: remove the four end-to-end `pathContainsNode` assertions and the now-unused `pathContainsNode`/`PathState` helper. Strengthen the existing direct persisted premise by selecting all TRUE/FALSE edges from the exact guard to `boundary.invocationCallId()` and asserting the set is a singleton whose kind/polarity are both FALSE. Retain the non-null typed boundary/FALSE context, unique CLOSED guard Fact/condition atom, both Flow outcomes with that same atom, shared basic-block identity checks, and exact expected counter signal/basis.
- This test correction does not weaken the production counter rule. Step 05 still requires complete membership/exclusion over private `TraversalPath.nodeIds`; the counter-signal expectation proves that production gate passed, while the test independently verifies the persisted direct guarded-call premise without copying the compiler traversal.
- The exact `if/else` fixture has one top-level `IfStmt` block. ProgramGraphs correctly writes the FALSE guard edge directly to the boundary invocation `CALL_SITE`, so DataFlow persists non-null guard/FALSE context for the boundary.
- `FactCandidateEnumerator.exactGuardPath`, however, accepts a guard branch only when `edge.toNodeId()` is found in `CONTROL_FLOW.nodesById`. The direct target is a node declared by the CALL graph, so the valid FALSE edge is discarded. Only the TRUE-to-return-terminal edge remains, `GUARD_BRANCHES` is not closed, and no `JAVA_GUARD_CONDITION` / `CONTROL_CONDITION` Fact is admitted for `approve`.
- The Flow compiler then traverses the real FALSE branch, finds zero matching condition atoms, and deterministically converts `approve` to `FLOW_CONDITION_ATOM_UNPROVEN`.
- The working early-return shape differs only at this seam: its FALSE edge targets a continuation `BASIC_BLOCK` declared by CONTROL_FLOW, followed by an unguarded NEXT edge to the call site. The guard candidate therefore closes, but the boundary's typed guard context is null, which correctly prevents a counter signal.
- Smallest correction: in ProvenCodeFacts M1, validate each guard branch target's same-entry ownership against the already verified complete public program-node union (at least CONTROL_FLOW plus CALL for this case), rather than CONTROL_FLOW-local nodes only. Preserve the exact TRUE/FALSE, guard ID/polarity, evidence, and two-branch uniqueness gates. Do not change ProgramGraphs, synthesize a Flow atom, or relax Step 05 counter premises.

## Changed files

- `backend-agents/sources/source-code/progress/flow-counter-compiler-debug.md` (owned progress record only)
- No production or test source remains changed by this diagnosis. The temporary diagnostic test and its generated class/report were removed.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Root exact counter selector after Terra's list GREEN | EXPECTED PREREQUISITE FAILURE | 1 test, 1 failure, 0 errors; returned only the compiled `cancel` Flow, proving `approve` was converted to an entry Gap. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCounterGapDiagnosticTest test` (one root-authorized temporary harness) | PASS / DIAGNOSTIC | 1 test, 0 failures/errors/skips; `approve` disposition was `GAP`, `reasonCode=FLOW_CONDITION_ATOM_UNPROVEN`; `cancel` was `COMPILED`. |
| Static persisted-path trace (`ControlFlowGraphBuilder`, `PersistedFactCandidateInputReader`, `FactCandidateEnumerator`, `EntryRootedFlowCompiler`) | ROOT CAUSE CLOSED | Program graph endpoint validation explicitly permits cross-graph node references; guard enumeration alone incorrectly resolves branch targets only in `CONTROL_FLOW.nodesById`. |
| Root-reported ProvenCodeFacts cross-graph target selector after minimal enumerator correction | PASS | 4 tests, 0 failures/errors/skips; the guarded-else fixture now supplies the CLOSED guard Fact consumed by Flow compilation. |
| Follow-up static oracle trace (`EntryRootedFlowCompilerTest#pathContainsNode` vs compiler `traverse`/`returnFromCallee`) | ROOT CAUSE CLOSED | Test BFS has no call stack/terminal-return transition and follows RETURN as an ordinary edge; it is not a valid oracle for an entry-rooted interprocedural Flow path. No Maven/JVM was run for the follow-up. |

## Decisions

- Treat `FLOW_CONDITION_ATOM_UNPROVEN` as the original compiler failure and the prior immutable-list `UnsupportedOperationException` only as a masking regression already fixed by Terra.
- Classify the defect at ProvenCodeFacts M1 guard-candidate enumeration, not the Flow compiler and not the ProgramGraphs contract.
- Keep the correction bounded to cross-graph target ownership resolution. The Step 04 design already says the complete five-graph input is verified and cross-graph endpoints are legitimate; Step 05 explicitly fixes the natural `if/else` shape, so no counter-contract redesign is indicated.
- The corresponding RED/GREEN now proves that the guarded-else public fixture admits its independent guard Fact; no further ProvenCodeFacts correction is indicated by this follow-up.
- Treat the post-enumerator-GREEN failure as test-only. Do not change ProgramGraphs or production traversal to make a raw graph BFS succeed.
- Prefer direct, exclusive guard-to-invocation branch assertions over a second implementation of private call-aware traversal in the test.

## Blockers

- None for root-cause diagnosis.

## Exact next action

- Root/Luna should apply the bounded test-oracle correction, rerun only the exact counter selector, and use its next failure or GREEN result as the counter implementation signal.

## Resume checks

- Preserve all root-, Luna-, and Terra-owned worktree changes.
- Rerun only the exact counter compiler selector after the test-oracle correction; do not run a full suite or alter ProgramGraphs/Step 05 contracts.
- Confirm the corrected premise fails if a TRUE edge also directly targets the invocation call site; do not add a test-side clone of production call-frame traversal.
