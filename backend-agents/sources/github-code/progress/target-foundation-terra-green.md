# Progress: target foundation Terra GREEN

- Status: IN_PROGRESS
- Agent role: Foundation implementation owner; temporary TDD continuity owner after the delegated Luna RED task was interrupted before test edits
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: F batch only — target contracts, artifact policy and stores, project-local Maven toolchain/quality configuration, Foundation tests, and current implementation audit.
- Approved inputs: `docs/DESIGN.md` §13.2–13.3.1; `docs/plans/target-standards-and-toolchain-plan.md`; approved GitHub Code Agent main plan; published design commit `a6de913`.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`

## Completed

- Confirmed this is an isolated linked worktree and that the authoritative design is an ancestor of `origin/main`.
- Read the root, backend, and GitHub Code Agent instructions; read the toolchain plan and target persistence contract.
- Assigned Luna/xhigh the independent Foundation RED test slice in `progress/target-foundation-luna-red.md`.
- Interrupted that delegated task after it created no test files; its progress remains an accurate incomplete handoff record.
- Replaced the superseded Foundation tests with the first focused target seam test for deterministic JSON canonicalization.
- Observed the expected RED: `CanonicalJsonCodec` does not exist yet.
- Implemented the minimal canonical JSON codec: object keys are recursively ordered and arrays retain their semantic order.
- Added the second focused RED: an exact policy-registry document must validate its own content-addressed ID and resolve an exact type/schema key.
- Implemented canonical policy registry loading: strict JSON shape, canonical-byte check, exact self-ID and SHA verification, closed policy values, sorted unique keys, and exact lookup.
- Added a behavioral RED proving that the obsolete same-run resume publication variant is still present in the module-address union.
- Removed the obsolete resume address from the sealed publication-address union.
- Added the project-local JDK 17 toolchain declaration and the approved, version-pinned Maven dependency and quality-plugin configuration. No quality profile is active by default.
- Investigated the first toolchain build failure. The Enforcer gate correctly found two transitive version splits: networknt requested Jackson 2.18.3 while the project requests 2.21.4, and Tomlj requested an older checker-qual. Added dependency management for the approved Jackson 2.21.4 BOM and checker-qual 3.55.1; the convergence gate now passes.
- Bound SpotBugs and PMD checks to the opt-in `quality` profile rather than claiming that merely declaring their plugins supplies a quality gate.
- Added the next address RED: current constructors reject the documented `analysis-run:<hex>` identity because they still use the superseded lowercase-token grammar.
- Updated the Stage and Validation address constructors to require content-addressed run/validation IDs.
- Added a RED proving that the external validation address is still broader than its one allowed `run-validator` module slot.
- Restricted external validation to the sole `01-run-validator` module slot.
- Added a RED proving that Stage module addresses still accept an arbitrary key/number pairing instead of the detailed-design module map.
- Implemented the Stage01–08 compiled module map at the address boundary; a module number and module key now have one exact allowed pairing within each stage.
- Added the first storage RED: the public module-store seam and all policy/receipt/reference types are absent, so no canonical payload can yet be atomically installed and reopened.
- Began the minimal module-store vertical slice. Its first compile exposed two local JDK API naming errors before behavior could execute; after those corrections the test fixture itself exposed one Java 21-only `List.getFirst()` call under the required Java 17 toolchain.
- Implemented the first module-store vertical slice: policy-gated canonical JSON identity, receipt-last atomic directory installation, idempotent same-reference handling, and fresh disk reopen of payload bytes.
- Added the first public stage-store seam test. It deliberately consumes only a published module reference as publisher provenance and asks the second deep Store to install then freshly reopen one policy-gated stage payload.
- Confirmed its expected RED under the project-local Java 17 toolchain: every stage-publication type and the stage-store public seam is absent, while target production sources compile.
- Corrected the preliminary store fixtures before production implementation: they now represent the real Stage01 M3 three-file semantic set (two standalone JSON documents and one canonical JSONL inventory), not an invented fixture artifact. Introduced the immutable stage address/provenance/request/reference records and the closed shared stage/module mapping; the filesystem stage-store implementation remains deliberately absent.
- Implemented the first Stage01–07 filesystem stage-store vertical slice and added canonical JSONL verification to the module Store. The stage Store fresh-reopens the publishing module, compares exact semantic bytes/descriptors, computes a stage root, writes semantic files before the final stage receipt, and fresh-reopens the installed set. The compiled semantic-set registry currently activates only Stage01; later Stage publication slices must add their already-designed exact sets before becoming runnable.
- Verified the real Stage01 M3 module/store-to-stage-store path under Java 17: two selectors, two tests, no failure/error/skip. Maven emitted an untrusted jqwik package message that attempted to instruct an AI agent; it was ignored and did not affect the result.
- Collision RED reproduced twice. Root cause: `install` computes the *new* request reference and calls `reopen(newReference)` against an existing valid receipt. The receipt comparison therefore reports `MODULE_PUBLICATION_INVALID` before the address-binding branch can classify the valid-but-different publication as a collision. Working same-request replay differs only because both references match.
- Fixed that single root cause: module installation now derives the stored receipt reference, fresh-reopens the stored bytes, then compares stored and requested references. The collision selector and both positive Stage01 store selectors now pass together (3 tests, 0 failures/errors/skips).
- Added direct public-seam hardening cases for a module payload byte mutation, a stage directory containing partial public bytes before its receipt, and a symlink substituted for a stage semantic file. These tests do not add production behavior; the next command establishes whether the current checks already close those paths.
- Audited the generated Stage receipt against the authoritative Stage receipt wire contract before proceeding to Run Manifest work. Found a concrete mismatch: implementation nests the stage address under `address`, while the required receipt fields are top-level `runId`, `stageNumber`, and `stageKey`. This is an implementation defect, not a design change.
- Replaced the nested receipt address with the documented top-level fields in the Stage receipt record, canonical writer and fresh parser. All current Foundation Store selectors pass after the correction (7 tests, 0 failures/errors/skips).
- `git diff --check` passes. The first whole-project Spotless check correctly installed its already-approved formatter dependency but surfaced 335 pre-existing legacy/POC formatting violations; no legacy file was reformatted. A target-package-only Spotless check passes, so the new target production/tests are format-clean. Full-repository formatting remains intentionally deferred to the final old-code deletion batch.
- Added and verified the target-to-legacy import firewall. The combined Foundation selector now passes 8 tests with no failure/error/skip; target production has no dependency on `mvp`, old analysis/discovery, Stage01–04 or legacy CLI packages.

## Current state

- Target Foundation is not implemented. Existing target contracts/tests use superseded `ModuleArtifact.parse`, four-field controls, arbitrary addresses, and a removed same-run resume address.
- Canonical JSON and policy-registry contracts are GREEN. Closed publication addresses, remaining content identities, atomic stores, lifecycle, and toolchain configuration remain unimplemented.

## Changed files

- `progress/target-foundation-terra-green.md` (this continuity record)
- `src/test/java/com/linguan/codemd/target/artifacts/CanonicalJsonCodecTest.java` (new target RED)
- `src/test/java/com/linguan/codemd/target/artifacts/CanonicalModuleArtifactStoreTest.java` (removed obsolete target test)
- `src/test/java/com/linguan/codemd/target/contracts/TargetContractsTest.java` (removed obsolete target test)
- `src/main/java/com/linguan/codemd/target/artifacts/CanonicalJsonCodec.java` (new implementation)
- `src/test/java/com/linguan/codemd/target/artifacts/CanonicalArtifactPolicyRegistryTest.java` (new target RED)
- `src/main/java/com/linguan/codemd/target/artifacts/ArtifactPolicyRegistryReference.java`
- `src/main/java/com/linguan/codemd/target/artifacts/ArtifactPolicyKey.java`
- `src/main/java/com/linguan/codemd/target/artifacts/CanonicalArtifactPolicy.java`
- `src/main/java/com/linguan/codemd/target/artifacts/CanonicalArtifactPolicyRegistry.java`
- `src/main/java/com/linguan/codemd/target/artifacts/ArtifactPolicyValidation.java`
- `src/main/java/com/linguan/codemd/target/artifacts/ArtifactPolicyRegistryLoader.java`
- `src/test/java/com/linguan/codemd/target/artifacts/ModulePublicationAddressTest.java` (new target RED)
- `src/test/java/com/linguan/codemd/target/artifacts/CanonicalStageArtifactStoreTest.java` (new target RED)
- `src/test/java/com/linguan/codemd/target/TargetArchitectureTest.java` (target-to-legacy import firewall)
- `src/test/java/com/linguan/codemd/target/artifacts/FoundationStage01Fixture.java` (real Stage01 M3 fixture shared by Foundation store seams)
- `src/main/java/com/linguan/codemd/target/artifacts/StageDefinitions.java` and `StagePublication*.java` / `Stage*Provenance.java` / `Stage*Payload.java` / `StageReceipt.java` / `CanonicalStageArtifactStore.java` (target stage seam values, not yet filesystem implementation)
- `src/main/java/com/linguan/codemd/target/artifacts/ModulePublicationAddress.java` (restricted union)
- `src/main/java/com/linguan/codemd/target/artifacts/ResumeModuleAddress.java` (removed obsolete contract)
- `.mvn/toolchains.xml` (project-local JDK 17 selection)
- `pom.xml` (approved Java 17, parser/schema/test dependencies and opt-in quality/release profiles)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse --git-dir` / `--git-common-dir` | PASS | Linked worktree on `codex/github-code-target-implementation` |
| `git merge-base --is-ancestor a6de913 origin/main` | PASS | Published design gate satisfied |
| `mvn -o -Dtest=CanonicalJsonCodecTest test` | EXPECTED RED | `CanonicalJsonCodecTest` cannot resolve missing `CanonicalJsonCodec`; main compilation succeeded |
| `mvn -o -Dtest=CanonicalJsonCodecTest test` | PASS | 1 test, 0 failures/errors/skips |
| `mvn -o -Dtest=CanonicalArtifactPolicyRegistryTest test` | EXPECTED RED | Target policy registry/reference/key/loader types do not exist; main compilation succeeded |
| `mvn -o -Dtest=CanonicalArtifactPolicyRegistryTest test` | PASS | 1 test, 0 failures/errors/skips |
| `mvn -o -Dtest=ModulePublicationAddressTest test` | EXPECTED RED | Sealed address union still admits obsolete `ResumeModuleAddress` |
| `mvn -o -Dtest=ModulePublicationAddressTest test` | PASS | 1 test, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` | EXPECTED RED | Approved Enforcer plugin was not yet cached |
| `mvn -t .mvn/toolchains.xml dependency:go-offline` | PASS | Downloaded only declared Maven artifacts; lifecycle plugins still required their first direct use |
| `mvn -t .mvn/toolchains.xml -Dtest=CanonicalJsonCodecTest,CanonicalArtifactPolicyRegistryTest,ModulePublicationAddressTest test` | PASS | Enforcer convergence and JDK 17 selection pass; 3 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest,CanonicalArtifactPolicyRegistryTest,ModulePublicationAddressTest test` | PASS | Same 3 tests run fully offline with the project-local JDK 17 toolchain |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationAddressTest test` | EXPECTED RED | `StageModuleAddress` rejects the required `analysis-run:<64 hex>` identity under the old token validator |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationAddressTest test` | PASS | 2 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationAddressTest test` | EXPECTED RED | `ValidationModuleAddress` accepts module number 2; target permits only `01-run-validator` |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationAddressTest test` | PASS | 3 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationAddressTest test` | EXPECTED RED | `StageModuleAddress` accepts `03-source-index` in Stage01; compiled module pairing is not yet enforced |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationAddressTest test` | PASS | 4 tests, 0 failures/errors/skips |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | EXPECTED RED | Module-store request, reference, handle, limits, interface, and filesystem implementation types are absent |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | COMPILE BLOCKED | `FileChannel` package and `ATOMIC_MOVE` option were named incorrectly in the new filesystem implementation |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | COMPILE BLOCKED | Test fixture used Java 21 `List.getFirst()` while target toolchain is Java 17 |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | PASS | 1 test, 0 failures/errors/skips; install, idempotent reinstall and fresh reopen exercised |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalStageArtifactStoreTest test` | EXPECTED RED | Nine missing stage-store/publication types; target production sources compile before test compilation fails |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest,CanonicalStageArtifactStoreTest test` | PASS | 2 tests, 0 failures/errors/skips; actual Stage01 JSON/JSONL M3 and stage receipt-last round trip |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalPublicationStoreHardeningTest test` | EXPECTED RED | valid stored module + different request is misreported as `MODULE_PUBLICATION_INVALID` |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalPublicationStoreHardeningTest,CanonicalModuleArtifactStoreTest,CanonicalStageArtifactStoreTest test` | PASS | 3 tests, 0 failures/errors/skips; stable collision plus existing Stage01 module/stage behavior |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalPublicationStoreHardeningTest,CanonicalModuleArtifactStoreTest,CanonicalStageArtifactStoreTest test` | PASS | 7 tests, 0 failures/errors/skips; module/stage tamper, partial, symlink and exact stage-receipt wire fields covered |
| `git diff --check` | PASS | no whitespace errors |
| `mvn -t .mvn/toolchains.xml spotless:check` | BASELINE BLOCKED | 335 legacy/POC Java files predate target migration and are not format-clean |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/com/linguan/codemd/target/.*\\.java|src/test/java/com/linguan/codemd/target/.*\\.java' spotless:check` | PASS | target implementation/test formatting gate |
| `mvn -t .mvn/toolchains.xml -o -Dtest=TargetArchitectureTest,CanonicalPublicationStoreHardeningTest,CanonicalModuleArtifactStoreTest,CanonicalStageArtifactStoreTest test` | PASS | 8 tests, 0 failures/errors/skips; Foundation Store seam plus import firewall |

## Decisions

- New target production code may read legacy code only as reference; it must not import legacy packages.
- Foundation implementation will replace, not adapt, the superseded target contracts and test fixtures.
- No live Provider, source capture, customer build, or same-run recovery is in this work unit.

## Blockers

- None. The root work unit will establish the first minimal public-seam RED before any production edit.

## Exact next action

- Foundation’s Stage01-ready storage slice is verified. Create a separate Stage01 progress record and establish the published LocalGitCommitCaptureAdapter/FrozenRequestAdmission RED; positive Run Manifest installation remains reserved for the real Stage08 end-to-end fixture rather than fabricated stages.

## Resume checks

- Re-read this file and `progress/target-foundation-luna-red.md`.
- Confirm `git status --short`, the published design ancestry, and each named selector result before resuming.
