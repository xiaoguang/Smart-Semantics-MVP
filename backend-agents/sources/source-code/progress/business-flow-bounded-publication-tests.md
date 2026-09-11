# Progress: business-flow bounded public publication tests

- Status: COMPLETE (bounded public closure RED captured; production remains out of scope)
- Agent role: Luna/xhigh bounded public-seam test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add one public-seam regression for a bounded `BOUNDED_PATH_SET` source through real Fact, M1 Flow, M2 Capsule, and M3 BusinessFlows publication. Preserve all existing fixture behavior and assert repository closure remains false while local Flow/Capsule publication is nonempty and structurally closed.
- Approved inputs: Published Step 02/03/04/05 contracts, completed bounded closure diagnosis and Fact handoff progress, existing `ProgramGraphsPublicFixture.createWithBoundedPathSet`, real Fact/M1/M2/M3 publishers, and the reader-injected `FlowPublicationSpecifier` constructor. No production, design, schema, source, Provider, or unrelated test changes.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Added one public-seam `BoundedBusinessFlowPublicationTest` using the existing `createWithBoundedPathSet` source-boundary fixture and the real Fact/M1 Flow/M2 Capsule/M3 BusinessFlows publishers.
- The test proves discovery repository coverage is a present boolean `false` with a nonempty exact entry denominator, M1 local coverage is a present boolean `true` with nonempty Flow IDs, M2 capsules are nonempty and bijective with those Flow IDs, and final public entry/Flow/Capsule IDs match their actual predecessors.
- The final public coverage assertion requires a present boolean `closed=false`; no raw JSON, source mutation, fabricated artifact, profile, schema, or existing fixture behavior was added.

## Current state

- The bounded fixture and real upstream handoff are already available. The new test must prove discovery `repositoryEntryCoverage.closed=false`, a nonempty entry denominator, M1 local `coverage.closed=true` with nonempty Flow IDs, and M2 nonempty one-to-one capsules before asserting the final public `flow-coverage.closed=false` contract.
- The selector reached the intended final public assertion. All bounded discovery/Fact/M1/M2/M3 premises passed, but current production emitted `flow-coverage.closed=true`; the test failed only at `BoundedBusinessFlowPublicationTest.java:167` when expecting `false`.

## Changed files

- `progress/business-flow-bounded-publication-tests.md` (owned; created before Java edit)
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BoundedBusinessFlowPublicationTest.java` (owned; one public behavior test and local persisted-artifact helpers)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Absolute one-file Spotless apply | PASS | Numeric exit 0; exactly the owned `BoundedBusinessFlowPublicationTest.java` selected and changed to clean. |
| Absolute one-file Spotless check | PASS | Numeric exit 0; exactly the owned test selected and clean. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BoundedBusinessFlowPublicationTest test` | EXPECTED RED | Numeric exit 1; `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; all upstream/public identity premises passed, then line 167 observed `closed=true` instead of required `false`. |
| Scoped diff check | PASS | `git diff --check` clean for the owned progress and test. |

## Decisions

- Use only the existing `createWithBoundedPathSet` source-boundary fixture and real Fact/M1/M2/M3 publishers; do not fabricate JSON, mutate source bytes, or add a profile/schema variant.
- Keep local M1 closure distinct from repository closure. The final public coverage must be explicitly present as boolean `false`, with nonempty predecessor and public entry/Flow/Capsule sets and exact predecessor ID mappings.
- Preserve the valid RED as a production contract failure: the test does not soften the final assertion or claim an omitted field is acceptable.

## Blockers

- Current `FlowPublicationSpecifier` publishes public `flow-coverage.closed=true` for the bounded source even though discovery reports `repositoryEntryCoverage.closed=false`; the bounded test isolates this after real Fact/M1/M2/M3 publication. Production repair is outside this test slice.

## Exact next action

- Release the Maven lease with this complete bounded RED. Any production conjunction/closure repair and rerun belong to the separately authorized implementation owner.

## Resume checks

- Do not modify `ProgramGraphsPublicFixture`, production, existing tests, or schemas. If Fact/M1/M2/M3 fails before the final bounded closure assertion, preserve and report that exact upstream failure without weakening assertions.
- Java is frozen; no additional selector, full suite, or post-RED implementation work is authorized in this slice.
