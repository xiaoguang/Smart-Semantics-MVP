# Progress: spring-http-entry-discovery

- Status: COMPLETE
- Agent role: Luna/xhigh RED then Terra/xhigh GREEN
- Model: gpt-5.6-luna / xhigh; gpt-5.6-terra / xhigh
- Started: 2026-09-02T08:24:00Z
- Last updated: 2026-09-02T11:22:00Z
- Scope: Implement only M2 static Spring MVC HTTP-entry discovery from the persisted M1 profile and verified parser bytes. Do not start Mapper catalog, M4 publication, program graphs, facts, flows, Provider, or adapters.
- Approved inputs: Published Stage 02 design gate `5d177fa`; approved shard rules; completed M1 profile detector/reader/publisher; synthetic frozen source fixtures only.
- Current branch/worktree: `codex/source-analysis-application-discovery-implementation` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the M2 contract in `docs/analysis-steps/02-application-discovery.md` and the completed M1 progress record.

## Current state

- M2 is complete for the approved static Spring MVC scope: it composes class/method routes,
  rejects dynamic or ambiguous mappings as typed Gaps, prevents duplicate shard membership, and
  persists one receipt-last HTTP-entry draft with exact annotation excerpts. The finished
  M1→persisted-profile→M2/M3→M4 fixture chain has also been verified. This does not claim a full
  jshERP entry-denominator acceptance run.

## Changed files

- `progress/spring-http-entry-discovery.md` — this recovery record only.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/SpringHttpEntryDiscovererTest.java` —
  static route composition and dynamic-route site green; shard-closure RED pending.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| M2 contract inspection | PASS | M2 requires a static Spring MVC entry denominator, composed class/method routes, typed evidence, a unique site disposition, and no model call. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SpringHttpEntryDiscovererTest test` | PASS | 3 tests, including static route composition, dynamic-route Gap, shard overlap rejection and per-shard accounting. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply && mvn -t .mvn/toolchains.xml -o spotless:check && git diff --check` | PASS | Java format and diff whitespace clean. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SpringHttpEntryDiscovererTest test` | PASS | 10 tests: positive GET/POST/explicit RequestMapping and explicit unsupported/ambiguous/no-false-positive cases. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=HttpEntryDiscoveryModulePublisherTest test` | PASS | 2 tests: path-free public seam and receipt-last M2 module publication/fresh reopen. |
| M1–M4 direct discovery/store selectors | PASS | 55 tests; 0 failures, errors or skips. |

## Decisions

- M2 will use the existing private `VerifiedSourceContentHandle` boundary rather than a caller path or raw bytes. It must later read the persisted M1 draft, not duplicate profile detection.
- Resource shards will be explicit input/output accounting when M2 moves beyond this first single-fixture vertical slice; a shard only partitions the complete denominator and cannot hide an entry.

## Blockers

- None.

## Exact next action

- Do not reopen M2 in this delivery. The next work unit is the separately designed program-graph
  step, after the application-discovery delivery is published.

## Resume checks

- Read this file, confirm the completed M1 progress remains unchanged, run `git status --short`, and rerun the exact M2 selector before changing the test or production code.
