# Progress: M7 no-model upstream Gap carrier tests

- Status: COMPLETE
- Agent role: Luna/xhigh public-seam test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Extend only the existing mixed eligible/ineligible M7 compiler test with the frozen shard-to-process-Gap carrier assertions.
- Approved inputs: `progress/m7-no-model-gap-carrier-design.md`, Step 06 M7/M8 contracts, current `BusinessProcessTaskCompilerTest`, and the existing real M6/M7 public fixture.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Added assertions to `BusinessProcessTaskCompilerTest#separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner`.
- The test now fresh-reopens M6, collects the exact upstream Capsule `gapViews`, and checks the intended M7 contract: every NO_MODEL shard Gap ID resolves to one process Gap; wrapper ownership, group, relation, context Flow, nullable budget fields, message key, upstream source Flow set, and byte-equivalent nested `gapView` are checked.
- The test also checks sorted unique process Gap IDs, shard-to-Gap conservation, and distinct wrapper IDs for the two NO_MODEL shards.

## Current state

- The new test is expected to be RED against the current compiler because it currently emits no process Gap wrappers for upstream model-ineligibility IDs.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java`
- `progress/business-process-task-no-model-gap-carrier-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest#separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner test` | EXPECTED RED | Main/test compile passed; 1 test, 1 assertion failure, 0 errors/skips. Failure is `Expecting actual not to be null` at the new process-Gap lookup, proving current M7 emits no wrapper for the NO_MODEL shard's upstream Gap. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` | PASS | Changed test source formatted. |
| `git diff --check` | PASS | No whitespace errors reported. |

## Decisions

- No production, design, Schema, fixture, Provider, or other test changes.
- The test derives expected upstream Gap values from the fresh M6 publication rather than fabricating a new fixture Gap or weakening the M8 consumer.
- The test keeps the existing mixed three-Flow fixture and existing owner assertions; it adds no second test method.

## Blockers

- None. The intended RED is confirmed.

## Exact next action

- Terra/xhigh may implement only the M7 upstream Gap wrapper projection described in
  `progress/m7-no-model-gap-carrier-design.md`, then rerun this single method and the named M7
  regressions.

## Resume checks

- Confirmed the failure is an assertion failure caused by the missing process-Gap carrier, not compilation or fixture construction.
- Do not modify production code in this task.
