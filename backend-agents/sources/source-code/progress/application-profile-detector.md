# Progress: application-profile-detector

- Status: COMPLETE
- Agent role: Luna/xhigh RED then Terra/xhigh GREEN
- Model: gpt-5.6-luna / xhigh; gpt-5.6-terra / xhigh
- Started: 2026-09-02T07:18:00Z
- Last updated: 2026-09-02T08:22:00Z
- Scope: Implement only ApplicationProfileDetector M1 and the source-evidence value primitives it already requires; do not begin HTTP-entry, Mapper, publication, runtime, or Provider work.
- Approved inputs: Published Stage 02 design gate `5d177fa`; approved eight-step/shard rules; synthetic frozen source fixture only.
- Current branch/worktree: `codex/source-analysis-application-discovery-implementation` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the published Stage 02 M1 contract, source-inventory artifacts, stores, and local capture registry.
- Confirmed the design already requires `SourceLocatorV1` and `SourceExcerptV1`, while their `evidence` implementations are absent; they are an M1 precondition, not a newly invented architecture feature.
- Added the first positive public-seam test for a complete frozen source set containing a Java 8 POM with Spring MVC/MyBatis dependency signals and a MyBatis mapper-location configuration signal.
- Implemented the minimal path-free source-text handle, exact source locator/excerpt value objects, closed signal records, Maven Model parsing, and static YAML mapper-location extraction required for that behavior.
- Observed the same selector GREEN for the first behavior.
- Added and passed a stable `APPLICATION_PROFILE_CONFLICT` behavior for conflicting verified Java releases.
- Added and passed the configuration-parent behavior: a `mapper-locations` key is a MyBatis signal only under the nearest enclosing `mybatis:` YAML mapping.
- Added and passed Maven's `maven.compiler.source` fallback when `maven.compiler.release` is absent.
- Added and passed deterministic `applicationProfileId` calculation for equivalent source documents supplied in different orders; the profile carries the verified inventory scope explicitly.
- Added and passed a filename-boundary behavior: only `pom.xml` or `*/pom.xml` is parsed as a Maven model.
- Added the control-identity RED: identical verified bytes with different published toolchain controls
  must not share an `applicationProfileId`.
- Extended the private verified-source text boundary with exact capability/inventory/snapshot
  references and `ArtifactControls`; the profile identity now canonically includes all of those
  immutable inputs and the published profile exposes its exact capability-profile reference.
- Re-ran the detector selector after the control-identity implementation: all seven behaviors are
  green.

## Current state

- Seven M1 detector behaviors are green. The next bounded work is M1 module publication from a
  fresh-reopened verified-source inventory; M1 publication is still absent.
- The next RED is the path-free M1 publication seam. It is intentionally separate from M2/M3:
  those modules must consume a persisted profile reference, not this detector's in-memory result.
- The seam-level RED, minimum GREEN, and canonical draft/receipt publication behavior are
  complete. M1 still lacks the private reader that fresh-reopens the official Stage 1 artifacts
  before invoking the detector.
- Added the persisted-source reader RED using a local one-file Git fixture and an exact,
  fresh-reopen analysis-step store boundary. The fixture intentionally does not execute Maven or
  any captured source.
- Implemented and passed the first reader behavior: it accepts only the three verified-source
  payloads, uses the exact source registration from `source-input.json`, compares every inventory
  member to the registered capture manifest, re-checks bytes/SHA/file identity, and returns only
  UTF-8 analyzable text documents.
- Ran Spotless apply/check for the affected Java tree and the direct M1/foundation regression
  selector; both are green.
- Added the next negative reader test: a source-inventory media-type drift must fail before parser
  bytes are returned.
- Passed the final M1 direct regression (24 tests) and Spotless/diff checks. The only final
  formatting finding was a one-line test wrap; Spotless applied it mechanically and the check is
  now clean.

## Changed files

- `progress/application-profile-detector.md` — this recovery record.
- `src/main/java/org/sourceanalysis/app/analysis/discovery/` — M1 static detector and closed
  source/profile value types, still without a persisted-source reader or module publisher.
- `src/main/java/org/sourceanalysis/app/evidence/` — exact source locator/excerpt value objects
  required by the M1 contract.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationProfileDetectorTest.java` —
  seven public-seam detector behaviors.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationProfileModulePublisherTest.java`
  — M1 publication seam plus canonical receipt-last module-draft behavior.
- `src/test/java/org/sourceanalysis/app/analysis/discovery/PersistedVerifiedSourceContentHandleTest.java`
  — pending persisted source-inventory to parser-bytes RED.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Stage 02 design inspection | PASS | M1 requires `detect(VerifiedSourceInventoryReference, DiscoveryProfile)` and closed Java/Spring MVC/MyBatis/config signals. |
| Inventory implementation inspection | PASS | Persisted source inventory contains `sourceRegistrationId`; local registry can re-open opaque exact blobs; no discovery source reader exists yet. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | Test compilation reported only missing M1 schema/seam types (`ApplicationProfileDetector`, verified-source handle, profile/signal records); no unrelated failure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 1 test, 0 failures/errors/skips; Maven Model and frozen YAML fixture produced Java 8, Spring MVC, MyBatis, and mapper-location signals. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | 2 tests: Java-release conflict reached the existing generic exception rather than the documented stable code. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 2 tests, 0 failures/errors/skips after the stable conflict code was added. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | 3 tests: unrelated YAML `mapper-locations` was incorrectly admitted as a MyBatis signal. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 3 tests, 0 failures/errors/skips after the parent-mapping check. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | 4 tests: `maven.compiler.source` was not read when release was absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 4 tests, 0 failures/errors/skips with compiler-source fallback and `1.x` normalization support. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | Expanded profile schema/identity test did not compile because scope and `applicationProfileId` fields were absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 5 tests, 0 failures/errors/skips; equivalent document order produces identical canonical profile ID. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | 6th test showed `not-a-pom.xml` was incorrectly parsed as Maven. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 6 tests, 0 failures/errors/skips after exact Maven filename recognition. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | EXPECTED RED | Control-identity test did not compile until the private verified-source boundary carried the exact upstream references and controls. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest test` | PASS | 7 tests, 0 failures/errors/skips; changing only the toolchain control changes `applicationProfileId`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileModulePublisherTest test` | PENDING | First M1 module-publication seam RED has been written and is awaiting its bounded selector. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileModulePublisherTest test` | EXPECTED RED | 1 failure: the M1 publisher and typed draft reference were absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileModulePublisherTest test` | PASS | 1 test, 0 failures/errors/skips; the published seam is path-free and has a typed handoff. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileModulePublisherTest test` | EXPECTED RED | Module-draft behavior named the missing `publish` operation and the three required upstream/control fields. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileModulePublisherTest,ApplicationProfileDetectorTest,CanonicalModuleArtifactStoreTest test` | PASS | 22 tests, 0 failures/errors/skips; M1 draft uses the registered application-discovery module contract, canonical UTF-8 upstream order, receipt-last install and fresh reopen. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedVerifiedSourceContentHandleTest test` | EXPECTED RED | The private persisted-source reader type was absent after the test fixture syntax was corrected. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedVerifiedSourceContentHandleTest test` | PASS | 1 test, 0 failures/errors/skips; local frozen Git bytes are returned only after Stage 1/manifest/SHA/file-ID closure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest,ApplicationProfileModulePublisherTest,PersistedVerifiedSourceContentHandleTest,CanonicalModuleArtifactStoreTest test` | PASS | 23 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | All 151 Java files clean after Spotless apply. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationProfileDetectorTest,ApplicationProfileModulePublisherTest,PersistedVerifiedSourceContentHandleTest,CanonicalModuleArtifactStoreTest test` | PASS | 24 tests, 0 failures/errors/skips; M1 detector, persisted reader, publisher and direct store contract are green. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The public detector remains path-free. Its package-private constructor receives a verified-source content handle; tests may fake only this I/O boundary.
- A source text document includes exact repository-relative identity and UTF-8 bytes. The detector must validate those bytes before deriving parser signals and source evidence.
- A POM dependency remains a static capability signal only; it does not prove framework runtime behavior. YAML mapper locations require an enclosing `mybatis:` mapping rather than a key-name match.
- The profile identity is canonical and source-order independent. It includes the verified
  snapshot/inventory/capability references plus toolchain/profile/schema/policy controls, so it
  cannot silently cross-reuse an analysis result under a different published control set.
- M1 is not complete until source-evidence records, static POM/config parsing, conflict/Gap behavior, identity, budget, and the M1 module artifact are independently tested. This first test is only the first vertical slice.

## Blockers

- None. The first direct module-store behavior exposed an incorrect test ordering; the production
  order already followed the documented UTF-8 artifact-ID order and the test was corrected.

## Exact next action

- Start the separately owned M2 HTTP-entry progress file, re-read its exact Stage 02 contract,
  then write its first route-composition RED. Do not alter this completed M1 record.

## Resume checks

- Re-read this completed record only for M1 provenance. Any new work must use its own progress
  file and must not change this one.
