# Progress: M8 all-safe fixture compiler cutover

- Status: COMPLETE
- Agent role: Luna/xhigh public-seam fixture correction
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Replace the invalid M8 all-safe test mutation with an identity-consistent M6 input that lets the real M7 compiler create two MODEL_SAFE shards, including a compiler-created PROCESS_UPSTREAM_LIMITATION carrier, plus one NO_MODEL shard.
- Approved inputs: `progress/m7-safe-limitation-gap-design.md`, `progress/m8-all-safe-shards-design.md`, scoped `AGENTS.md`, current M6/M7 compiler and M8 execution seams.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Added a baseline compiler run in the test to identify the original safe relation and one no-model relation from real M7 output.
- Built a new test-only M6 candidate compilation with two distinct safe relation owners and one retained no-model owner. The duplicate safe relation has a visible `SECOND_SAFE_SHARD_MARKER` relation-use value and a distinct relation identity; the process material limit forces one relation per compiler shard.
- Published that candidate through the real M6 publisher and compiled M7 through the real `BusinessProcessTaskCompiler`.
- Removed the previous invalid path that converted a no-model shard into a model-safe shard while retaining the no-model Gap. No packet, shard, or M7 Gap identity is mutated after compiler publication.
- The resulting M7 publication supplies two compiler-created model-safe packets, each with same-shard `PROCESS_UPSTREAM_LIMITATION` carriers, and one compiler-created no-model terminal with its own resolvable ineligibility Gap.
- Existing assertions still require the ordered calls `safe-1 P1`, `safe-1 P2`, `safe-2 P1`; the second safe P1 returns `Q01` GAP and P2 is not called. Technical transport material and prior responses remain forbidden from the provider packet.

## Current state

- Fixture cutover is complete and the direct selector is green against the current shared M8 implementation.
- The current shared production source already contains finite-shard M8 orchestration, so this run cannot observe the historical one-safe-shard RED. The corrected fixture is suitable for reproducing that RED against the pre-generalization implementation and is a valid GREEN regression for the generalized implementation.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java`
- `progress/business-process-interpretation-all-safe-fixture-cutover-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationExecutionPublisherTest#closesTwoSafeAndOneNoModelShardOneDryPacketAtATime test` | PASS | 1 test, 0 failures/errors/skips; real M6 publisher and real M7 compiler produce `A=3`, `S=2`, `I=1`; provider call sequence is 3. |
| `mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java` | PASS | Target test source formatted. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationExecutionPublisherTest.java progress/business-process-interpretation-all-safe-fixture-cutover-tests.md` | PASS | No whitespace errors. |

## Decisions

- Test-only change; no production, design, schema, or shared fixture edits.
- Use real M7 compiler-created limitation carriers. Do not import a `NO_MODEL` Gap into a `MODEL_SAFE` shard and do not retain stale shard or packet identity.
- Keep the single public selector and all frozen M8 accounting/isolation assertions.

## Blockers

- None for the fixture cutover. Historical RED cannot be re-observed while the shared worktree contains the generalized M8 publisher; that is an execution-state fact, not a fixture defect.

## Exact next action

- Parent agent should use this corrected selector as the M8 finite-cardinality RED when testing the pre-generalized publisher, or retain the current PASS as the regression for the generalized publisher.

## Resume checks

- Preserve unrelated shared-worktree changes.
- Do not revert or edit the generalized M8 production source as part of this test task.
- Do not invoke a live Provider, network, customer source, or customer Maven.
