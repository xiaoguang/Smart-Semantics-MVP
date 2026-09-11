# Progress: M7 safe-shard limitation Gap conservation RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add one public-seam RED test for separate NO_MODEL ownership and MODEL_SAFE limitation references in the M7 task compiler.
- Approved inputs: `progress/m7-safe-limitation-gap-design.md`, scoped `AGENTS.md`, current M6/M7 compiler records and public fixtures.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Added exactly one public-seam test method,
  `BusinessProcessTaskCompilerTest#conservesNoModelOwnershipAndModelSafeLimitationReferencesSeparately`.
- The test uses the real chained-Java M6 publication and the actual M7 compiler output. It
  reopens M6 `gapViews`, checks all `NO_MODEL` ownership, and requires a same-shard
  `PROCESS_UPSTREAM_LIMITATION` carrier for each safe packet limitation.
- The test asserts `P = O ⊎ L`, complete upstream gap-view preservation, same group/shard/relation/
  context, and zero Provider calls.
- The intentional first RED is the missing safe carrier: the current compiler binds the limitation
  directly to an upstream M6 Gap ID, so the carrier lookup is null. No fixture, Provider, production,
  design, or Schema error occurred.

## Current state

The compiler currently emits NO_MODEL-owned process Gaps, while a MODEL_SAFE packet may expose a limitation binding directly to an upstream Flow Gap. The required safe-shard carrier and disjoint `P = O ⊎ L` law are not yet implemented.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` (planned single test method only)
- `progress/business-process-task-safe-limitation-gap-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` | PASS | Spotless applied successfully |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest#conservesNoModelOwnershipAndModelSafeLimitationReferencesSeparately test` | EXPECTED RED | testCompile passed; 1 test, 1 assertion failure, 0 errors/skips; missing `PROCESS_UPSTREAM_LIMITATION` carrier |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilerTest.java` | PASS | Formatting clean |
| `git diff --check` | PASS | No whitespace errors in tracked changes |

## Decisions

- Use the existing compiler-produced mixed-eligibility M7 material and freshly reopened M6 capsule gap views.
- Do not manufacture a Gap ID, reuse a NO_MODEL wrapper, mutate a shard, or call a Provider.
- Keep the expected RED limited to the missing `PROCESS_UPSTREAM_LIMITATION` carrier/conservation behavior.

## Blockers

## Exact next action

Terra/xhigh may implement only the frozen M7 safe limitation carrier and disjoint ownership law,
then rerun this exact selector followed by the two M7 regression selectors named by the design.

## Resume checks

- Only the single test method and this progress file changed in this slice.
- No production, design, Schema, fixture, or Provider changes.
