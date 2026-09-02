# Progress: program graph M1 maturity

- Status: IN_PROGRESS
- Agent role: Sol/ultra design-audit update for the approved program-graph design
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Record the verified current M1 implementation state in the Program Graphs maturity audit only; no architecture or contract change
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, the approved provenance-draft v2 contract, and direct M1 verification results
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` at `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Confirmed the published design already specifies M1’s v2 provenance-draft contract and static nested configuration behavior.

## Current state

- The maturity audit now distinguishes the implemented M1 builder/module artifact from the still-missing runtime input assembly and all M2–M6 work. It does not claim that Program Graphs is complete.

## Changed files

- `progress/program-graph-m1-maturity.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest,CodeStructureGraphModulePublisherTest test` | PASS | 8 tests, 0 failures/errors/skips in the implementation worktree. |

## Decisions

- This is an implementation-maturity correction, not a target-design change; it does not add outputs, alter the eight-step flow, or change the Stage 3 acceptance gate.

## Blockers

- None.

## Exact next action

- Verify the focused documentation diff, commit, and fast-forward publish the docs correction before continuing graph implementation.

## Resume checks

- Read this file, verify this worktree is clean apart from the listed docs/progress changes, and confirm the direct M1 selector result in the implementation worktree.
