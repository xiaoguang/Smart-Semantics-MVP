# Progress: application-discovery-quality-hardening

- Status: COMPLETE
- Agent role: Sol/xhigh quality debug, then Terra/xhigh minimal GREEN
- Model: gpt-5.6-sol / xhigh; gpt-5.6-terra / xhigh
- Started: 2026-09-02T11:25:00Z
- Last updated: 2026-09-02T11:28:00Z
- Scope: Resolve only the Stage 02 and directly touched analysis-step-store SpotBugs findings required by this delivery. Do not alter the application-discovery wire contract or begin program graphs.
- Approved inputs: Published design gate `5d177fa`; current application-discovery implementation; local quality output only.
- Current branch/worktree: `codex/source-analysis-application-discovery-implementation` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Reproduced the quality failure with `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify`.
- Recorded 10 SpotBugs findings: six List representation exposures in the two shard receipts, and four conservative nullable-path findings in `AtomicAnalysisStepPublicationEngine`.

## Current state

- Quality corrections are complete. Shard receipt accessors now defensively return copied lists, and
  the store explicitly rejects missing path components before use. Redundant qualified names and
  now-unused store members were removed. No analysis output, identity formula or discovery rule
  changed.

## Changed files

- `progress/application-discovery-quality-hardening.md` — this recovery record only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | RED | SpotBugs: 10 findings; direct functional tests ran first with 102 passing. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest,CanonicalAnalysisStepArtifactStoreTest,VerifiedSourceInventoryPublicationSpecifierTest,ApplicationProfileDetectorTest,ApplicationProfileModulePublisherTest,PersistedVerifiedSourceContentHandleTest,SpringHttpEntryDiscovererTest,HttpEntryDiscoveryModulePublisherTest,MapperCapabilityCatalogerTest,MapperCatalogModulePublisherTest,ApplicationDiscoveryPublicationSpecifierTest,ApplicationDiscoveryExecutionTest test` | PASS | 55 tests; 0 failures, errors or skips. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS | 102 tests; SpotBugs 0 and PMD 0. |

## Decisions

- Treat the quality findings as code defects until a minimal test-backed correction proves otherwise; do not hide them with a suppression.

## Blockers

- None.

## Exact next action

- Leave this hardening slice closed; include it with the complete application-discovery delivery.

## Resume checks

- Read this file, inspect the four reported source locations, and rerun the direct quality command before claiming the delivery ready.
