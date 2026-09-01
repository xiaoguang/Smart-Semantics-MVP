# Progress: target Stage01 frozen request admission

- Status: IN_PROGRESS
- Agent role: Terra/xhigh implementation owner for the Stage01 M1 public seam
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Establish and implement the first `FrozenRequestAdmission` vertical slice: strict `analysis-run-request-v2` parsing, exact capture/frozen-request binding, deterministic admitted-file partitioning, and a rootless `AdmittedSourceRequest` result.
- Approved inputs: User-approved full implementation plan; `docs/DESIGN.md` §§8.2–8.5 and 13.2–13.3; `docs/stages/01-freeze-source.md` M1/§8.1.1; scoped `AGENTS.md`; the newly implemented test-only local capture result.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`

## Completed

- Read the M1 field, identity, failure and public-test contracts; confirmed this module is program-only and has zero LLM calls.
- Confirmed the preceding local capture slice returns rootless source registration, receipt and manifest records and can fresh-reopen its private store.
- Established the expected M1 RED: 12 test-compilation errors, all for missing admission/view/value types.
- Implemented the first M1 pure-admission slice. It strictly parses canonical analysis/frozen request JSON, verifies the frozen-reference bytes, origin/revision/capture/snapshot/profile/budget bindings, scopes, file limits and text/media partitions, then creates sorted rootless `AdmittedSourceRequest` files and deterministic file IDs.

## Current state

- No request-admission production package or public seam exists. The first test will use a complete-capture synthetic result and exact canonical request bytes; it will verify admitted files are UTF-8-path ordered and partitioned without allowing an absolute path into the result.
- The first public-seam test is now written. It requires the missing admission/view/value types and should fail at test compilation only for those absent types.
- The direct `admit` seam is GREEN. The generic module-envelope writer/parser is now available and is enforced by the shared Store, but M1 has not yet supplied its own exact payload writer/parser or an installed module reference for M2.
- M1 now has an exact payload writer/parser and `admitAndInstall` seam. It binds eight explicit upstream references, installs one `MODULE_ARTIFACT_JSON`, fresh-reopens it and compares the reconstructed request before returning a `ModulePublicationReference`.
- A first replay mismatch was investigated before repair: `inventoryScope.declaredPathCount` was serialized at the payload root but reconstructed as `1`. The parser now reuses the root count, and the full publication/reopen test is green.
- The parser now also recomputes the exact text/media partition from every reconstructed file and rejects any persisted partition that does not close to that file set. It also checks that completion eligibility follows the declared scope.

## Changed files

- `progress/target-stage01-request-admission.md` (this continuity record)
- `src/test/java/com/linguan/codemd/target/stage01/requestadmission/Stage01FrozenRequestAdmissionTest.java` (new public-seam RED)
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/AdmissionException.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/CaptureReceiptView.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/ProfileView.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/InventoryScope.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/AdmittedSourceFile.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/AdmittedSourceRequest.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/FrozenRequestAdmission.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/FrozenRequestAdmissionPublicationInput.java`
- `src/main/java/com/linguan/codemd/target/stage01/requestadmission/AdmittedSourceRequestArtifact.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitCaptureResult.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | PASS | Preceding private capture prerequisite: 2 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | EXPECTED RED | 12 test-compilation errors, all missing M1 public seam types. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | PASS | 1 test, 0 failures/errors/skips; complete capture admits a sorted rootless text/media partition. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | EXPECTED RED | New publication test first failed at test compilation only for its three absent public persistence types. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | PASS | 2 tests, 0 failures/errors/skips; installed M1 reopens to the same typed request, with eight exact upstream refs. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | EXPECTED RED | A payload with an empty text partition was accepted, exposing missing semantic closure after JSON parse. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | PASS | 3 tests, 0 failures/errors/skips; reconstructed file partitions and scope eligibility must close before M2 can consume M1. |

## Decisions

- The M1 seam accepts raw canonical request bytes and read-only capture/profile boundary views. It neither receives a filesystem path nor opens source blobs.
- The first GREEN is limited to strict structural/binding admission. Immediate module-store installation is the next M1 vertical slice; no result will be advertised as a Stage01 publication before that exists.

## Blockers

- None.

## Exact next action

- Begin M2 `VerifiedSourceIndexer` RED work. Its input must be only the M1 reopened artifact plus a path-free, read-only snapshot handle.

## Resume checks

- Re-read this file, the Stage01 M1 contract, and scoped `AGENTS.md`.
- Verify the test input is synthetic and rootless, that the selector is exact, and that no source parser or Provider is invoked.
