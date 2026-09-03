# Progress: Fact candidate missing-path fixture fix

- Status: COMPLETE
- Agent role: Luna/xhigh test-fixture correction
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Align the persisted missing-argument-evidence fixture with the base fixture's exact artifact policy registry and controls; do not change production, design, POM, or other tests.
- Approved inputs: Scoped `AGENTS.md`, `progress/proven-code-facts-delivery.md`, `FactCandidateMissingPathTest`, `ProgramGraphsPublicFixture`, and the M1 same-controls contract.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` / `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Patched `FactCandidateMissingPathTest` so the mutation helper uses `base.artifactPolicies()` and `base.artifactControls()` for the fresh copied source, discovery, and graph publications.
- Removed the now-unused local policy/control reconstruction and its graph-policy imports; the mutation path now has one canonical policy/control identity shared with the base fixture.

## Current state

The mutation helper now reuses the base fixture's exact policy registry and controls. The intended mutation remains limited to the approval `ARGUMENT_TO_BOUNDARY` edge's evidence IDs.

## Changed files

- progress/fact-candidate-missing-path-fixture-fix.md
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateMissingPathTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateMissingPathTest test` | PASS | 1 test, 0 failures/errors/skips; approval is not applicable and cancellation remains a candidate |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleReaderTest,FactCandidateMissingPathTest test` | PASS | 2 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateMissingPathTest.java spotless:check` | PASS | owned test formatting is clean |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Reuse exactly `base.artifactPolicies()` and `base.artifactControls()` for the copied publications and stores.
- Do not alter the data-flow evidence mutation or canonical identity recomputation.

## Blockers

## Exact next action

- Parent may now run the full M1 selector; this fixture no longer creates a false same-controls failure.

## Resume checks

- Confirm no production/design/POM/other-test file changed.
- Confirm the mutation still clears only the approval argument edge evidence and that cancellation remains unaffected.
