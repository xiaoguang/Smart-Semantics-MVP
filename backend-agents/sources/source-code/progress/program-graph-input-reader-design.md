# Progress: program graph input reader design

- Status: IN_PROGRESS
- Agent role: Sol/ultra local design refinement for Program Graphs M1–M2 input reopening
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Define the internal, path-free reopener that turns already-published source-inventory and application-discovery artifacts into typed Program Graph inputs; no production implementation
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/01-verified-source-inventory.md`, `02-application-discovery.md`, `03-program-graphs.md`, target implementation plans, and the published M1 maturity audit
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` at `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Confirmed the target M2 contract already requires fresh-reopened verified source bytes plus the full entry-point and Mapper-catalog artifacts.
- Confirmed the current M1 seam intentionally carries only identities/entry IDs, so it cannot itself satisfy M2’s richer reader contract.

## Current state

- The design now defines `PersistedProgramGraphInputReader` as the bounded, non-module composition boundary. It validates exact Stage 1/2 semantic payloads and receipts, reopens registered bytes, and returns typed source/discovery views. It neither adds an output nor changes the five-graph module order.

## Changed files

- `progress/program-graph-input-reader-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest,CodeStructureGraphModulePublisherTest test` | PASS | 8 tests, 0 failures/errors/skips; M1 persisted module boundary remains green. |

## Decisions

- The reopener is an internal stage-3 composition component, not a new public API, analysis step, module artifact, or reader-visible file.

## Blockers

- None.

## Exact next action

- Verify the docs diff, commit, and fast-forward publish this local contract clarification before M2 code/test work.

## Resume checks

- Read this file, confirm this worktree is based on `origin/main` after `5a98f4a`, and verify no target architecture or artifact-count change is proposed.
