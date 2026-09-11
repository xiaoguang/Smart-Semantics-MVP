# Progress: M7 formal process Gap carrier tests

- Status: COMPLETE
- Agent role: Luna/xhigh public-seam test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add one public M7 publisher/reopen behavior test for shard-owned upstream process Gaps.
- Approved inputs: `docs/analysis-steps/06-flow-interpretation.md` §6.7.2.2, current M7 compiler/publisher, existing mixed eligible/ineligible fixture and tests.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the scoped source-agent rules, Step 06 M7 formal carrier contract, current `BusinessProcessTaskModulePublisher`, `BusinessProcessTaskCompiler`, and mixed-eligibility fixture.
- Created this progress file before modifying the test.
- Added exactly one public-seam test:
  `BusinessProcessTaskModulePublisherTest#persistsFormalShardOwnedUpstreamGapsWithoutModelWork`.
- The test uses the real three-Flow mixed-eligibility fixture, publishes M6, invokes the formal M7 publisher, and fresh-reopens `process-task-shards.json`.
- It independently checks zero Provider calls, two NO_MODEL shards, shard-owned `PROCESS_UPSTREAM_MODEL_INELIGIBLE` wrappers, exact owner/group/context fields, required nullable fields, original nested Capsule `gapView`, source-Flow subset, distinct wrapper ownership, sorted unique process Gap IDs, and the framed Gap identity.

## Current state

- The existing compiler test checked the wrapper values in memory; this task now covers the missing publisher/fresh-reopen seam. Current production already satisfies this bounded formal carrier behavior, so the direct selector is GREEN rather than the anticipated RED. The test reaches the real publisher and verifies persisted values, so GREEN is not vacuous.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskModulePublisherTest.java`
- `progress/m7-process-gap-carrier-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskModulePublisherTest#persistsFormalShardOwnedUpstreamGapsWithoutModelWork test` | PASS | 1 test, 0 failures/errors/skips; main/test compilation and fresh-reopen publisher path passed. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskModulePublisherTest.java` | PASS | Test source formatted. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskModulePublisherTest.java progress/m7-process-gap-carrier-tests.md` | PASS | No whitespace errors. |

## Decisions

- One test method only, using the existing three-Flow mixed-eligibility fixture and real M6/M7 publication path.
- Expected values are derived independently from the fresh M6 Capsule `gapViews`; no raw upstream Gap ID is accepted as a process owner.
- Because the direct selector was already GREEN, no artificial failure was introduced and no production change was requested from Terra.
- No production, design, Schema, fixture, Provider, network, or unrelated test changes.

## Blockers

- None.

## Exact next action

- Parent may review the GREEN carrier seam and decide whether to proceed to the next frozen M7/M8 slice; do not add the ordinal test in this task.

## Resume checks

- Preserve unrelated shared-worktree changes.
- Do not run a Maven aggregate or invoke a live Provider.
