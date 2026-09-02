# Progress: call graph input design

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority for the Program Graphs M2 input interface
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Resolve the M2 CallGraphBuilder source-byte/interface contradiction in target design text only; no Java, test, fixture, schema, commit, or push
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md`, existing design history, and read-only inspection of the M1 implementation branch
- Current branch/worktree: `codex/source-analysis-graph-provenance-design` at `/private/tmp/linguan-source-analysis-graph-provenance-design/backend-agents/sources/source-code`

## Completed

- Read the repository instructions and the Program Graphs target contract around persisted input reopening, M1, M2, module upstream references, source evidence, and path-free execution.
- Confirmed the contradiction: M2 requires verified call-site bytes, while `buildCalls(structure, entries, mapperCatalog)` cannot receive them and permits detached candidate lists.
- Chose the minimum typed input interface: one `CallGraphInputs` value containing the fresh-reopened M1 `CodeStructureGraphDraft` and the same `ReopenedProgramGraphInputs`.
- Updated the M2 exact-input contract, public test seam, and Terra implementation guide to use `buildCalls(CallGraphInputs, CallGraphProfile)` exclusively.

## Current state

- The target contract now makes `inputs.reopened.source` the only source-byte input, `inputs.reopened.discovery` the only Stage 2 entry/Mapper-candidate input, and `inputs.structure` the fresh-reopened M1 endpoint catalog. It forbids Path, detached lists, free source strings, worktree scans, and string fallback.

## Changed files

- `progress/call-graph-input-design.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check` | PASS | Exit 0; no whitespace errors in the tracked design diff. |
| `awk '/[[:blank:]]+$/{...}' progress/call-graph-input-design.md` | PASS | Exit 0; no trailing whitespace in the new progress file. |
| `git status --short` | PASS | Only the Program Graphs design and this task-owned progress file are changed. |

## Decisions

- The public M2 test seam is `buildCalls(CallGraphInputs inputs, CallGraphProfile profile)`.
- `CallGraphInputs` is an immutable in-process builder input, not a wire schema, module artifact, path-bearing adapter, or second source reader.

## Blockers

- None.

## Exact next action

- Parent inspects this docs-only diff, then commits and pushes it before M2 test or implementation work begins.

## Resume checks

- Confirm the branch/worktree are unchanged, inspect `git status --short`, and verify no Java/test/schema file is modified.
