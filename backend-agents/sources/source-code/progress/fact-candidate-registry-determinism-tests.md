# Progress: Fact candidate registry determinism tests

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add public-seam M1 registry ordering/deletion/required-atom-order tests only.
- Approved inputs: scoped AGENTS.md; proven-code-facts step design; current naming and toolchain plans; frozen ProgramGraphsPublicFixture.
- Current branch/worktree: codex/source-analysis-proven-code-facts / /private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code

## Completed

- Added `FactCandidateRegistryDeterminismTest` with three public-seam tests.
- Verified template collection reorder preserves candidate identity, typed candidates, dispositions,
  and denominator.
- Verified deleting one of two templates leaves exactly its two entry-boundary combinations and
  changes candidate identity.
- Verified required atoms preserve the registry declaration order and that order changes identity.

## Current state

Added one public-seam test class with three behaviors: registry template reorder identity stability,
single-template deletion denominator scoping, and required-atom declaration-order preservation.
The public candidate set exposes identity and denominator but no serialized-byte method, so the test
uses the canonical identity plus complete typed semantics rather than inventing a private serializer.

## Changed files

- progress/fact-candidate-registry-determinism-tests.md
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateRegistryDeterminismTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateRegistryDeterminismTest test` | PASS | 3 tests, 0 failures/errors/skips; BUILD SUCCESS |

## Decisions

Tests use typed `FactCandidateInputs`, `PersistedFactCandidateInputReader`, and
`FactCandidateEnumerator`; no production implementation or design contract changes. The fixture
has two valid Java-boundary entries, so a two-template registry has four applicable combinations.

## Blockers

## Exact next action

Run the scoped formatter and diff check, then hand the test result to the parent agent. No
production implementation is required by this test slice.

## Resume checks

Read this file first; then verify `git status --short` and inspect the changed test/progress paths before continuing.
