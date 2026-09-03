# Progress: Fact candidate graph-closure design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Correct the local M1 candidate-input seam so a candidate is joined from one real entry-owned five-graph path, not a cross-product of graph-index catalog items. Documentation only.
- Approved inputs: Step 04 target design, ProgramGraphs public graph records/wire, current M1 RED/GREEN, and the bounded M1 review.
- Current branch/worktree: `codex/source-analysis-fact-candidate-closure-design` at `/private/tmp/linguan-source-analysis-fact-candidate-closure-design`.

## Completed

- Confirmed the review finding: the existing M1 documentation already requires all five graphs, but its abbreviated `enumerate(entries, graphIndex, factRegistry)` test seam failed to carry their endpoint and ownership closure.
- Replaced that abbreviated seam with one persisted `FactCandidateInputs` handoff, and made the entry/call/argument/control/evidence join, applicable/not-applicable denominator, candidate identity, and public artifact fields explicit.
- Preserved the frozen-Java boundary: a candidate records only a Java invocation, its static target, and its Java-local argument provenance. It cannot claim an XML/SQL or external-system effect.

## Current state

- The documentation correction is internally consistent and ready for its required docs-only publication to `origin/main`.

## Changed files

- `docs/analysis-steps/04-proven-code-facts.md`
- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Bounded M1 review | PASS | Found no P0; identified cross-product and missing graph-closure P1 issues before they reached Proof or publication. |
| `git diff --check` | PASS | The Step 04 contract and progress file have no whitespace errors. |
| Boundary cross-check | PASS | ProgramGraphs and ProvenCodeFacts both retain Java-only value flow and external-effect Gap semantics. |

## Decisions

- A boundary candidate describes a frozen-Java invocation path only. Its structure may record the static target and Java argument provenance, but neither the input join nor the candidate can claim an external effect.

## Blockers

- None.

## Exact next action

- Commit and fast-forward publish the docs-only correction, then fast-forward the Fact worktree and request the replacement M1 RED test.

## Resume checks

- Confirm `origin/main` contains the docs-only commit, then ensure the Fact worktree carries it before replacing its temporary M1 seam.
