# Progress: M8 mixed-shard execution publication RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add the single frozen public-seam RED for one MODEL_SAFE plus two NO_MODEL M7 shards.
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, Step 06 §§6.4–7.2, `progress/m8-multi-shard-publication-design.md`, and existing M6/M7/M8 tests.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

## Current state

The exact A=3, S=1 fixture and aggregate M8 assertions are written. The test builds the real chained-Java M7 fixture with three shards, one model-safe shard and two no-model shards, scripts exactly P1 then P2 for the safe packet, and checks fresh-reopened aggregate accounting, terminals, receipt status and gap union. No production code or existing test was changed.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationExecutionPublisherTest#publishesOneModelSafeAndTwoNoModelShardsAsOneClosedM8Result test` | RED: test compile | Four static method-reference errors in the new test; no production or fixture error. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationExecutionPublisherTest#publishesOneModelSafeAndTwoNoModelShardsAsOneClosedM8Result test` | RED: assertion-only | 1 test, 1 failure, 0 errors/skips; `PROCESS_INTERPRETATION_EXECUTION_PUBLISHER_NOT_IMPLEMENTED`; fixture and P1/P2 setup completed. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java` | PASS | Spotless applied. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java` | PASS | New test is formatted. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java` | PASS | No whitespace errors. |

## Decisions

- Use the existing chained-Java-call fixture with capsule budget 350 and max two Flow contexts per shard.
- Reuse existing test seams/helpers by reflection where they are private; do not create a second production contract.
- The RED must fail because the aggregate execution publisher is absent, not because the fixture or P1/P2 oracle is invalid.

## Blockers

None.

## Exact next action

Hand this assertion-only RED to Terra/xhigh for the frozen aggregate execution publisher. Do not modify the test contract while implementing the production seam.

## Resume checks

- Do not modify production, docs, schemas, fixtures, or existing tests.
- Confirm exactly three M7 shards, exactly one MODEL_SAFE shard, two NO_MODEL shards, and three unique relation owners before invoking the aggregate seam.
- Do not call a live Provider or customer source.
