# Progress: fact candidate exact path RED

- Status: BLOCKED
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test for persisted Fact candidate enumeration using fresh-reopened ApplicationDiscovery and complete ProgramGraphs publications. Do not modify production code, existing tests, fixtures, design, or another Agent's progress.
- Approved inputs: Published Step 04 candidate contract, published Step 03 public graph wire, frozen Java-only boundary rule, current target branch.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code

## Completed

- Read scoped AGENTS.md, DESIGN.md, Step 04 design, both implementation plans, delivery progress, and current candidate/graph code.
- Confirmed the current candidate enumerator accepts raw JSON fragments and does not expose the required persisted-input seam.
- Inspected all existing graph-package fixture entry points and the public graph publication seam.

## Current state

- No test was added because the required independent public fixture cannot be constructed from the current test/public seams without violating the task constraints.
- The formal M6 public wire now carries typed `boundaryInvocation` and `unknownBoundaryReturn` variants for `DATA_FLOW`; the missing capability is a valid two-entry/two-distinct-boundary persisted fixture, not a missing data-flow variant field.

## Changed files

- progress/fact-candidate-exact-path-red.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Worktree contains only pre-existing Fact M1 files/progress plus this owned progress file |
| `rg -n "createWithTwoEntries|createWithConsumedAuditClientReturn|createWithMapperAndAuditClient" src/test/java/org/sourceanalysis/app/analysis/graph` | PASS | Existing two-entry fixtures share one handler/one boundary; existing two-boundary fixture has one entry |

## Decisions

- The test will not read graph drafts, parse `canonicalValue`, pass arbitrary `JsonNode`, or use filesystem paths as analysis inputs.
- The test will assert a Java-only boundary invocation and its exact internal path; it will not assert SQL or external effect.

## Blockers

- BLOCKED: no existing public/persisted fixture provides TWO distinct discovered HTTP entry IDs together with TWO distinct boundary invocation nodes. `ControlFlowGraphBuilderTest.Fixture.createWithTwoEntriesSharedHandler` supplies two entries but one shared `depotHeadMapper.updateStatus` boundary; `createWithConsumedAuditClientReturn`/`createWithMapperAndAuditClient` supply boundary nodes but only one entry. Their factory and graph composition types are package-private in `org.sourceanalysis.app.analysis.graph`, so a test in `org.sourceanalysis.app.analysis.fact.candidates` cannot reuse them through a public seam.
- Building a fixture by hand from raw public JSON, parsing `canonicalValue`, passing drafts, or extending another test would violate the assigned contract. The exact missing upstream test seam is a reusable public persisted two-entry/two-boundary graph fixture (or a public graph-fixture builder); no formal production-wire field is being invented.

## Exact next action

- Sol/ultra or the parent agent must provide/approve a public two-entry/two-boundary fixture seam before this RED can be written. After that, add one test targeting `PersistedFactCandidateInputReader` plus typed `FactCandidateInputs`/`FactCandidateEnumerator`, then run only its selector.

## Resume checks

- Re-read this file, run `git status --short`, and verify only the owned test/progress may be changed. Do not create synthetic graph JSON to bypass the blocker.
