# Progress: mapper-capability-catalog

- Status: COMPLETE
- Agent role: Luna/xhigh RED then Terra/xhigh GREEN
- Model: gpt-5.6-luna / xhigh; gpt-5.6-terra / xhigh
- Started: 2026-09-02T10:15:00Z
- Last updated: 2026-09-02T11:22:00Z
- Scope: Implement only M3 MyBatis Mapper Java/XML capability catalog from M1 verified parser bytes. Do not bind Java callers to Mapper statements, publish M4, construct program graphs, or start facts/flows/Provider/adapters.
- Approved inputs: Published Stage 02 design gate `5d177fa`; completed M1 profile detector/reader/publisher; active M2 static-entry work; synthetic frozen source fixtures only.
- Current branch/worktree: `codex/source-analysis-application-discovery-implementation` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read M3 contract, security rules and test guidance in `docs/analysis-steps/02-application-discovery.md`.

## Current state

- M3 is complete for the approved static Mapper-catalog scope: it reads only persisted verified
  Java/XML bytes, records Java method and XML statement candidates with exact excerpts, permits
  the normal MyBatis DOCTYPE without external resolution, and turns unsafe or mismatched inputs
  into typed Gaps. It deliberately does not claim a Java-to-XML call binding; that belongs to
  program graphs.

## Changed files

- `progress/mapper-capability-catalog.md` — this recovery record only.
- `src/main/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCataloger.java` and
  `MapperCatalog*` records — static M3 candidate discovery only.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/MapperCapabilityCatalogerTest.java` —
  public seam, standard-DOCTYPE candidate, mismatch and external-entity coverage.
- `src/main/java/org/sourceanalysis/app/analysis/discovery/MapperCatalogModulePublisher.java` and
  `MapperCatalogDraftReference` — receipt-last M3 module publication.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/MapperCatalogModulePublisherTest.java`
  — path-free M3 seam and empty-catalog fresh-reopen behavior.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| M3 contract inspection | PASS | M3 may catalog verified Mapper Java/XML/config candidates and exact evidence only; Java-to-XML binding remains a later program-graph responsibility. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=MapperCapabilityCatalogerTest test` | PASS | 4 tests: path-free seam, standard DOCTYPE positive candidate, namespace mismatch Gaps and external-entity rejection. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=MapperCatalogModulePublisherTest test` | PASS | 2 tests: path-free seam and receipt-last empty M3 module publication/fresh reopen. |
| M1–M4 direct discovery/store selectors | PASS | 55 tests; 0 failures, errors or skips. |

## Decisions

- Use only `VerifiedSourceContentHandle` / `VerifiedSourceTextSet` parser bytes; no caller filesystem path, Maven execution, network, external XML resolver or model call.
- First RED will prove the minimal DepotHead-shaped Mapper interface plus matching XML namespace/statement is catalogued as candidates, never as a proven call binding.

## Blockers

- None.

## Exact next action

- Do not add binding logic here. The next work unit is the separately designed program-graph
  step, after the application-discovery delivery is published.

## Resume checks

- Read this file, the active M2 progress record and M3 contract; confirm `git status --short`; run only the exact M3 selector before changing production code.
