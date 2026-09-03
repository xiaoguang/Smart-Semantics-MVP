# Progress: M6 public graph wire version gates RED

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add the smallest public-seam RED tests for M4/M5/M6 graph wire version acceptance.
- Approved inputs: `AGENTS.md`, `docs/analysis-steps/03-program-graphs.md`, `docs/supplements/program-graphs-implementation-backlog.md`, existing ProgramGraphPublicWire/ProgramGraphsPublicationSpecifier tests.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Added the public-wire version assertion to the existing full M1–M5-backed M6 publication test. It checks the required public data-flow v2, evidence v3, and graph-index v2 values without changing the fixture or production code.
- The targeted RED is established against the current implementation: the full fixture reaches M6 and fails only at the new version assertion, with actual `[data-flow-v1, evidence-v2, graph-index-v1]` versus required `[data-flow-v2, evidence-v3, graph-index-v2]`.

## Current state

The existing public publication fixture is being reused. The production constants currently appear to emit old public data-flow, evidence, and graph-index schema versions; the RED must fail specifically on the missing version gate, not on fixture construction.

## Changed files

- `progress/m6-version-wire-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | RED | 1 test, 1 failure, 0 errors/skips; failure at `ProgramGraphPublicWireTest.java:262` is the expected public schema-version mismatch. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Test only the public wire contract and old/new mixed-version rejection.
- Do not modify production code, documentation, POM files, existing tests, or commits.

## Blockers

## Exact next action

Terra may now update the production v3/v2 version bundle and its fail-closed readers/policies. This test must remain the public output gate; any additional old/mixed-input rejection tests should be added as a separate bounded RED if the public seam exposes a valid persisted mutation path.

## Resume checks

- Re-read this file before modifying anything.
- Check `git diff --name-only` and preserve all unrelated shared-worktree changes.
- Confirm the selected test remains independent of live providers, network, and customer code.
