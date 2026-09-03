# Progress: fact candidate raw seam retirement

- Status: COMPLETE
- Agent role: Luna/xhigh test cleanup
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Remove only the obsolete raw-JSON Fact M1 test and its three raw test resources. No production, POM, design, new typed RED test, graph fixture, or other progress changes.
- Approved inputs: Sol/ultra cleanup work unit; typed exact-path RED is the replacement M1 test.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Removed `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java`.
- Removed `src/test/resources/analysis/fact/candidates/application-discovery-reopened.json`.
- Removed `src/test/resources/analysis/fact/candidates/fact-registry.json`.
- Removed `src/test/resources/analysis/fact/candidates/program-graphs-reopened.json`.
- Left the typed persisted-input `FactCandidateExactPathTest` and its real canonical graph fixture untouched.

## Current state

The obsolete raw-JSON candidate seam has no remaining test entry or resource set in this worktree. The replacement test targets the formal `FactCandidateInputs` + `FactRegistry` + `PersistedFactCandidateInputReader` contract and cannot be bypassed by the old JSON enumerator.

## Changed files

- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java (removed)
- src/test/resources/analysis/fact/candidates/application-discovery-reopened.json (removed)
- src/test/resources/analysis/fact/candidates/fact-registry.json (removed)
- src/test/resources/analysis/fact/candidates/program-graphs-reopened.json (removed)
- progress/fact-candidate-raw-seam-retirement.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Confirmed only the assigned uncommitted Fact work and progress were present before edits |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- This is test-only retirement, not a production compatibility change.
- No compatibility reader or alias remains for the obsolete raw JSON M1 seam; Terra must implement the typed persisted public seam specified by Step 04.

## Blockers

- None for this cleanup unit. The typed exact-path test remains an expected RED until the formal Fact M1 production types exist.

## Exact next action

- Terra should implement the typed Fact M1 reader, registry, exact path join, and full candidate/disposition wire, then rerun `FactCandidateExactPathTest`.

## Resume checks

- Read this file first; verify the four listed raw seam paths remain absent and the typed exact-path test/fixture remain present.
