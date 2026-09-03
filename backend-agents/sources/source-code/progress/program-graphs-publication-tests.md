# Progress: M6 ProgramGraphs publication RED test

- Status: COMPLETE (coherent registry/control fixture verified)
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add the smallest public-seam `ProgramGraphSetPublicationSpecifierTest` for the M6 exact-seven semantic publication contract.
- Approved inputs: The typed seven-field M6 record receives two freshly reopened, store-generated upstream step references plus five freshly reopened M1–M5 module artifacts. The test uses real module and analysis-step stores throughout; no mocked stores or fabricated references are used.
- Current branch/worktree: Shared worktree; unrelated existing M1–M5 edits are preserved.

## Completed

- Read scoped `AGENTS.md`, `docs/DESIGN.md`, both `docs/plans/*`, and the M6 contract in `docs/analysis-steps/03-program-graphs.md`.
- Confirmed M6 must publish exactly five graph JSON payloads plus `graph-index.json` and `graph-gaps.jsonl`; the analysis-step store, not the specifier, owns the analysis-step root and receipt.
- Confirmed the public typed `ProgramGraphsPublicationInputs`, `ProgramGraphsReference`, and `ProgramGraphSetPublicationSpecifier` seams plus persisted M5 publisher/reader are present.
- Added the exact M6 policy registry entries: four code/call/control/data `PROGRAM_GRAPHS_*_GRAPH` v1 policies, the evidence `PROGRAM_GRAPHS_EVIDENCE_GRAPH` v2 policy, `PROGRAM_GRAPHS_GRAPH_INDEX` v1, `PROGRAM_GRAPHS_GRAPH_GAP` v1, and the M5 evidence-draft policy.
- Added a second real module store over the fixture handle, using the expanded policy registry for M5 publish/reopen and M6 specification; the original fixture store remains the source of the preexisting M1–M4 references.
- Corrected the published M6 semantic policy versions: code-structure, call, control-flow, and data-flow are v1; evidence remains v2; graph index and graph gap remain v1.
- Added the seven upstream semantic policies required by the exact source-inventory (three payloads) and application-discovery (four payloads) public sets.
- Added minimal canonical standalone JSON/JSONL payloads with content-derived artifact IDs, installed them through the real M3/M4 publisher module addresses, installed both analysis-step receipts, and freshly reopened their references.
- Updated the exact seven-field `ProgramGraphsPublicationInputs` construction to pass the reopened source-inventory and application-discovery references.
- Rebuilt M1 through M5 in the test with one expanded policy registry and matching `ArtifactControls`, using a distinct store-generated publication run identity so the coherent chain does not collide with the source fixture's preexisting drafts.
- Changed the final M6 call to use the same coherent controls, and derived graph-basis upstream artifact references from the persisted publisher descriptors.

## Current state

- The M6 test is a passing public-seam characterization. It installs and reopens real source-inventory and application-discovery step publications, rebuilds and reopens M1–M4 under the same expanded registry and controls, builds and reopens M5 evidence, and uses the exact seven-field `ProgramGraphsPublicationInputs` shape before invoking M6. It asserts the exact seven semantic filenames and five graph kinds on the returned publication.

## Changed files

- `progress/program-graphs-publication-tests.md` (this file)
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicationSpecifierTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest test` | PASS | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; `BUILD SUCCESS`. The coherent expanded-registry M1–M6 chain completes and the exact seven semantic filenames/five graph kinds assertions pass. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Do not invent a detached fifth draft or modify production, design, POM, existing tests, or existing fixtures.
- Build M1–M5 and M6 from real stores under one expanded policy registry and one matching control tuple; require fresh reopens at each boundary and keep source-inventory before application-discovery in the persisted upstream receipt chain.

## Blockers

- None. The direct selector passes with the coherent persisted fixture.

## Exact next action

- Hand off the passing M6 characterization to Terra; no production changes were made here.

## Resume checks

- Re-run `git status --short`; confirm only this progress file and the task-owned M6 test changed.
