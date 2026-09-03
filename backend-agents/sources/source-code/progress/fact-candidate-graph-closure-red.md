# Progress: fact-candidate-graph-closure-red

- Status: BLOCKED
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Establish the M1 graph-closure RED for exact entry/boundary/call/argument/control/evidence joins.
- Approved inputs: Step 04 M1 contract, ProgramGraphs public publication wire, existing public-wire test, current M1 candidate slice.
- Current branch/worktree: codex/source-analysis-proven-code-facts

## Completed

- Audited the required M1 input path against the actual ProgramGraphs public publication code.
- Confirmed that no valid graph-closure RED fixture can currently be built through the required public and canonical stores without inventing fields or bypassing the public wire.
- Did not add a test, fixture, production type, design change, or modify an existing assertion.

## Current state

The requested test must start from a freshly reopened ApplicationDiscovery publication and a
complete freshly reopened five-graph ProgramGraphs publication. The latter does not expose the
boundary relation data required by the M1 contract:

- `ProgramGraphSetPublicationSpecifier.nodeValue(...)` at
  `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java:720-733`
  converts `DataFlowNode` to a `PublicNode` containing only node ID, kind, canonical value, and
  owning entries.
- `PublicProgramGraph.payload(...)` at lines `964-972` writes only those common node fields plus
  `evidenceNodeIds` to the public graph JSON.
- The `DataFlowNode.boundaryInvocation()` payload—which contains `invocationCallId`,
  `callTargetEdgeId`, ordered argument node IDs, Java-local origins, and control context—is not
  written to the public `data-flow-graph.json`.
- The complete boundary details exist only in the draft/module wire
  (`src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphWire.java:98-138`), while
  `ProgramGraphsReference` exposes only the analysis-step publication reference and does not
  authorize a fact test to reopen a draft or a private module payload.

Therefore a two-entry/two-boundary test using the required public path cannot assert the specified
exact joins. A hand-written JSON object containing `boundaryInvocation` would be an unsupported
public wire and a test that reads draft/module internals would violate the M1 seam. Both would hide
the actual production gap rather than establish a valid RED.

## Changed files

- `progress/fact-candidate-graph-closure-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Source audit with `nl -ba`/`rg` | BLOCKED | Public graph serializes no `boundaryInvocation`; only draft `DataFlowGraphWire` does. |
| Maven | NOT RUN | Parent instruction was to stop test writing and avoid heavy Maven while the public wire is insufficient. |
| `git diff --check` | PENDING | Run after the coordinator inspects this progress-only change. |

## Decisions

- Do not create synthetic public graph fields, use reduced graph-index fixtures, or access private
  draft/module state to force the RED.
- The appropriate next task is a Stage 03 public-wire RED/design decision to expose the already
  computed generic boundary metadata in the final public data-flow graph. Once that seam is
  published and merged, rerun this task from fresh canonical stores with two disjoint entry-owned
  boundary paths.

## Blockers

- `ProgramGraphsReference` cannot currently provide the M1-required boundary invocation relation
  through the complete public five-graph publication.
- This is a cross-step contract gap (Stage 03 public wire → Stage 04 candidate input), not a
  test-only fixture problem. It requires Sol/ultra design authority and the coordinator's next
  task assignment before any M1 graph-closure test can be valid.

## Exact next action

- Coordinator should record this cross-step blocker and request a Stage 03 public-wire contract
  correction. Do not write the M1 graph-closure RED until a published public wire carries the
  required boundary invocation metadata or a new approved public reader seam is provided.

## Resume checks

- Read this file first.
- Verify that the public ProgramGraphs schema/version and reader seam now expose
  `invocationCallId`, `callTargetEdgeId`, ordered arguments, and control context.
- Build the two-entry/two-boundary fixture only from real canonical ApplicationDiscovery and
  ProgramGraphs stores; assert exact pairwise joins and per-pair `NOT_APPLICABLE` mutations.
