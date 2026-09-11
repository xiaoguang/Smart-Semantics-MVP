# Progress: M7 no-model Gap carrier test oracle correction

- Status: COMPLETE
- Agent role: Luna/xhigh test-oracle maintainer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Correct only the array extraction in the existing no-model upstream Gap carrier test.
- Approved inputs: `progress/m7-no-model-gap-carrier-design.md`, existing M7 public test and its current RED.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Replaced the non-flattening `findValuesAsText("modelIneligibilityGapIds")` call with direct
  iteration over `shard.path("modelIneligibilityGapIds")`, preserving the existing assertions.

## Current state

The test used `findValuesAsText("modelIneligibilityGapIds")`, which does not flatten the named
array and therefore made the oracle read the array node incorrectly.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` (one extraction expression only)
- This progress file

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest#separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` | PASS | Target test formatted. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` | PASS | Target test passes format check. |
| `git diff --check` | PASS | No whitespace errors reported. |

## Decisions

- Read each textual value directly from `shard.path("modelIneligibilityGapIds")`.
- Preserve every existing assertion and the intended M7 production contract.
- No production, design, Schema, fixture, or unrelated test changes.

## Blockers

## Exact next action

Terra may continue with the M7 no-model Gap carrier implementation; this test oracle is green and unchanged in semantic scope.

## Resume checks

- The correction must not change the expected RED/GREEN semantics.
- Do not weaken the wrapper ownership, provenance, sorting, or conservation assertions.
