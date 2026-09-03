# Progress: Program graph public-wire design correction

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Record the discovered mismatch between the approved M4/M6 public data-flow contract and the current M6 serializer, before a bounded ProgramGraphs public-wire RED/GREEN repair. No production or test changes in this task.
- Approved inputs: ProgramGraphs target design, `ProgramGraphSetPublicationSpecifier`, `DataFlowGraphWire`, ProgramGraphs public-wire tests, and the published Step 04 M1 candidate-input contract.
- Current branch/worktree: `codex/source-analysis-program-graph-public-wire-design` at `/private/tmp/linguan-source-analysis-program-graph-public-wire-design`.

## Completed

- Confirmed that M4 draft `DataFlowGraphWire` preserves `JavaBoundaryInvocationV1` and its ordered arguments/control context.
- Confirmed that M6 `ProgramGraphSetPublicationSpecifier.PublicNode` serializes only ID, kind, canonical value, owners, and evidence IDs, so public `data-flow-graph.json` discards the required-nullable boundary/unknown-return variants.
- Confirmed independently that a Fact M1 public-seam test cannot legally construct its required persisted input while this M6 loss remains: hand-writing unknown public fields or reopening an M4 draft would violate the existing contract.
- Corrected the M6 target projection/test guidance and current maturity audit. The change preserves the existing generic Java boundary and adds no external-effect semantics.

## Current state

- The documentation correction is complete pending local checks and a docs-only publication. The next code slice is a bounded M6 public-wire RED/GREEN, then M1 candidate enumeration can resume.

## Changed files

- `docs/analysis-steps/03-program-graphs.md`
- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Source comparison | PASS | `DataFlowGraphWire` has `boundaryInvocation`; `PublicNode` has no variant field. |
| Independent Fact-seam review | PASS | Complete public ProgramGraphs artifacts cannot carry M1's exact boundary join until M6 projects variants. |

## Decisions

- External behavior remains outside the graph: the public variant must carry only frozen-Java invocation identity, ordered Java arguments, control context, locator, and rule. It must not add SQL effects or a technology-specific boundary.

## Blockers

- None.

## Exact next action

- Run documentation consistency/whitespace checks, publish the docs-only correction, then request one M6 public-wire RED test.

## Resume checks

- Confirm the published commit is on `origin/main`; then create the isolated M6 code branch from it and keep the Red test limited to public data-flow variant projection.
