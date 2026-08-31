# Progress: Stage 02 code review

- Status: COMPLETE
- Agent role: Independent Stage 02 code-review agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Review Stage 02 flow-compilation implementation and tests against its design and Stage 01 CFG/proof seam; no production, test, or design edits.
- Approved inputs: `docs/stages/02-flow-compilation.md`, Stage 02 source/tests/fixtures, Stage 01 CFG/proof seam diff, applicable AGENTS.md files.
- Current branch/worktree: shared worktree; preserve all pre-existing changes.

## Completed

- Read root, `linguan-prototype-v2`, `backend-agents`, and `sources/github-code` `AGENTS.md` instructions.
- Read `docs/stages/02-flow-compilation.md`, Stage 01 flow/proof seam classes, and Stage 02 implementation/tests/fixtures.
- Identified a likely hard-coded fixture dependency in `Stage02Compiler` step-kind mapping and semantic span selection.
- Confirmed the four priority checks: `steps()` adds each input fact once but groups by hard-coded kind and sorts by hashed step ID; each outcome receives all flow atoms/proofs; `hasRejectedFactFor` ignores its node set and blocks on any global rejection; outcome coverage uses only control-flow terminal lists and omits entries with no control flow.
- Found additional closure, cross-entry ownership, CFG edge, capsule-gap, span-minimality, and budget enforcement risks for the final report.
- Completed read-only review and recorded findings below; no production, test, or design files were modified.

## Current state

Read-only review is complete. No P0 was found; P1/P2 findings are listed below.

## Changed files

- `progress/stage02-code-review.md` (review progress only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared worktree already contains unrelated Stage 01/02 changes; preserved. |
| `mvn -Dtest=Stage02CompilerTest,Stage01FlowViewContractTest,JshErpStage02AcceptanceTest test` | PASS | 20 tests, 0 failures/errors/skips; this is positive/fixture coverage and does not exercise the identified mutation gaps. |

## Findings

### P1 — CFG terminal/call edges are ignored, so broken closure can compile

- `src/main/java/com/linguan/codemd/stage02/Stage02Compiler.java:167-170` traverses only `CFG_ENTRY`, `CFG_NEXT`, `CFG_TRUE`, and `CFG_FALSE`; the required `CFG_CALL`, `CFG_RETURN`, and `CFG_TERMINAL` edges are never consumed.
- `:219-222` checks only the set of terminal node IDs, not terminal-edge reachability or call/return pairing. Removing a terminal self-edge or call/return pair from a flow view therefore leaves the same complete outcomes whenever the remaining branch edges still reach the terminal. This contradicts Stage 02 design §6.2 and TDD matrix rows 4–5.
- Related Stage 01 seam risk: `src/main/java/com/linguan/codemd/stage01/RepositoryCompiler.java:608-625` selects only `firstTargets` and emits a synthetic `CFG_ENTRY` to one target; a controller with multiple exact calls is not represented as a complete call sequence.

### P1 — Every Outcome claims every atom and proof

- `Stage02Compiler.java:279-281` computes `allAtoms`/`allProofs`; `:303-307` puts both lists into every `OutcomePath.requiredAtomIds/requiredProofIds`. For the positive fixture, the invalid-quantity path consequently claims A01–A20 and all 20 proofs even though it terminates before inventory read/update. This is a path-closure overclaim, not just redundant storage, and can inflate capsule obligations/budgets.

### P1 — Fact rejection scope is global, not entry-owned

- `Stage02Compiler.java:256-261` ignores `ownedNodes` and returns `!rejections.isEmpty()`. One rejected candidate anywhere in the Stage 01 `GapLedger` forces every entry with any facts through `FLOW_FACT_NOT_ADMITTED` at `:125-127`, violating the design's entry-isolated Gap rule. Reproduce by adding a rejected candidate in one entry while retaining an independently complete second entry; both are forced to GAP.

### P1 — Outcome denominator can shrink when Stage 01 has no ControlFlow

- `Stage01 RepositoryCompiler.java:613-617` omits a control flow when the method exceeds the Stage 01 CFG budget (while retaining the discovered entry and an over-limit site). `Stage02Compiler.compileEntry` then emits an entry GAP at `Stage02Compiler.java:114-119`.
- `coverage()` nevertheless computes `outcomeDenominator` only from `view.controlFlows()` at `:570`; the gapped entry's terminal candidates are absent, yielding e.g. `0/0` instead of preserving its discovered outcome denominator. No accounting invariant checks this.

### P1 — Shared child nodes/facts can be borrowed across entries and overcount coverage

- `Stage02Compiler.java:224-248` recursively expands all method-contained nodes and both endpoints of every binding edge touching an entry closure, without an entry-owned call/return seam. Two entries calling the same Service therefore receive the same Service Facts/atoms.
- `coverage()` sums `flowFacts`/`flowAtoms` across flows (`:573-576`) against one global candidate denominator and `validateResult()` (`:699-715`) never enforces unique Fact/atom ownership. A second controller calling the same reservation Service can yield successful flows with numerators greater than denominators and Capsules containing another entry's facts.

### P1 — Projection uses unproven text matching and merges unrelated Java bodies

- `hasDirectSemantics()` (`:359-373`) accepts a request-body fact if any record node's `canonicalValue()` contains the atom text. `semanticNode()` (`:451-459`) then selects the first matching record by node ID, even if it is not the Proof root or Fact subject. A declared decoy record with the same text can become the model span, violating proof/atom binding.
- `CandidateSpan.include()` and `candidateKey()` (`:461-489`, `:836-841`) merge every non-record/non-return Java semantic node in one path into `java-body:<path>` and do not require adjacency or same method/Flow. This can include unrelated methods/comments and is not the design's adjacent-span merge. `requireInclusionMinimal()` only checks bookkeeping (`:553-560`), not byte semantic ownership.

### P1 — Proof revalidation does not close ProofPack edges or pack identity

- `ProofIndex.closedFor()` (`:881-894`) checks only existence of proof IDs, root/node IDs, and edge IDs. It does not check `FactAtom.proofPackId()` against the current pack, root membership in required nodes, ProofEdge endpoints in the required closure, or `ProofEdge.repositoryEdgeId()` against the flow graph.
- `revalidate()` (`:923-945`) rehashes required node spans only; no ProofEdge closure is reopened/validated. A malformed or drifted edge graph can therefore pass the M4 gate even with unchanged source bytes.

### P1 — Capsule drops all Stage 01 Gap provenance

- `capsule()` returns `List.of()` for `allowedGaps` at `Stage02Compiler.java:405-408`, and compiled `FlowSlice` always has `List.of()` gap IDs at `:151-154`. The Stage 02 design requires relevant capability/expectation Gap provenance (the synthetic flow has five expectation Gaps), so the result silently loses known uncertainty.

### P2 — `maxFlowEdges` is accepted but never enforced

- `validateRequest()` validates it positive (`:617-623`), but `enforceBudgets()` (`:690-697`) never counts graph/traversal edges against it, and `enumerate()` has no edge counter. A request with `maxFlowEdges=1` can still traverse/return the full CFG, violating the advertised hard budget.

### P2 — Flow steps are deterministic by hash, not control-flow order

- `steps()` groups all facts by hard-coded fixture kinds (`:331-340`) and returns `steps.stream().sorted(Comparator.comparing(FlowStep::flowStepId))` at `:356`. The hashed ID order is unrelated to entry → guard → read → calculate → write → result order, and all groups are placed in `sharedSteps`, including branch-only/terminal facts. The positive tests only assert set membership, so this remains undetected.

### P2 — Stage 02's semantic registry is fixture-specific and unbound to profile

- The `switch` at `Stage02Compiler.java:331-338` names only the synthetic reservation Fact kinds; no versioned Fact registry/profile is part of `FlowCompilationProfileRef` or checked at runtime. A new admitted Stage 01 Fact kind reaches `FLOW_GRAPH_REFERENCE_BROKEN` rather than a declared `FlowGap`, while text checks at `:363-369`/`:453-457` assume `ReservationRequest`.

### P2 — Coverage identity omits dispositions and flow identities

- `coverage()` identity at `Stage02Compiler.java:578-580` hashes only scalar metrics and span count. It omits entry/disposition IDs, Gap IDs, Flow IDs, and Capsule IDs despite the design requiring disposition identities. Distinct disposition assignments with equal counts can share `coverageReportId` (even though the outer result ID later includes the report object).

## Decisions

- Review findings will be reported as P0/P1/P2 with exact file/line and reproducible evidence.

## Blockers

## Exact next action

Review complete; parent agent should triage the findings. No implementation changes are authorized in this review scope.

## Resume checks

- `git status --short`
- Confirm the progress file remains the only file created by this agent.
