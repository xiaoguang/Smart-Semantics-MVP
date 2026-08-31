# Progress: Stage 01 M1 verified snapshot core

- Status: COMPLETE
- Agent role: Stage 01 M1 production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement only the Stage 01 M1 public snapshot-verification seam under `src/main/java/com/linguan/codemd/stage01/**`; do not implement M2/M3 or change tests, POM, design, or POC code.
- Approved inputs: Repository and source-agent guidance; Stage 01 design §§3–5, 8–9, 11; completed RED test handoff; `VerifiedSnapshotContractTest`; `Stage01Fixtures`; and direct regression test `ManifestEvidenceVerificationTest`.
- Current branch/worktree: Shared dirty worktree. Preserve all existing and other-agent changes. No network, model, source capture, candidate, freeze, package, or publication operation is authorized or planned.

## Completed

- Read the root, prototype, backend-agent, and GitHub-code `AGENTS.md` files; read the Stage 01 M1 design contract and completed test-agent progress record.
- Confirmed the pre-edit `git status --short` contains pre-existing unrelated work and new Stage 01 RED-test files to preserve.
- Read the Stage 01 test fixture and contract test fully; no production Stage 01 source exists yet.
- Ran the required selector before implementation. It reached `testCompile` and failed only for the missing Stage 01 M1 public records and `Stage01Analyzer`, which is the intended RED state.
- Added the M1 request/output records, stable-code exception and failure enum, and the package-private NOFOLLOW snapshot verifier. It validates only explicitly declared files and returns no partial state on failure.
- Ran the first GREEN selector attempt. It compiled and executed all seven tests; two happy-path tests failed with `SOURCE_HASH_MISMATCH`, while five requested fail-closed cases passed. Root-cause investigation is in progress before any further implementation change.
- Compared all six immutable fixture files to their reviewed manifest literals. Five size/SHA pairs match; `InventoryMapper.xml` is 594 bytes and hashes to `ac9b916d8e8b09461f9963c47b022e69cc06444d85e6fd137f2d6ee892f5118b`, while the test manifest declares the distinct final character `...f5118`.
- Reran the requested Stage 01 selector after the fixture owner corrected its SHA literal: all 7 `VerifiedSnapshotContractTest` cases pass.
- Ran the directly covered legacy manifest-evidence regression selector: all 2 `ManifestEvidenceVerificationTest` cases pass.
- Received independent read-only review: no Critical or Important M1 defect; the streamed-versus-buffer SHA cross-check is a harmless defense-in-depth detail and remains within the slice.
- Ran the final scoped `git diff --check`; it returned successfully with no whitespace error.

## Current state

- Stage 01 M1 verified-snapshot core is complete. Both required targeted selectors are GREEN, and no M2/M3 implementation was introduced.
- The fixture owner corrected the SHA manifest literal to the independently verified `...f5118b`; the M1 implementation was not weakened or changed for that correction.

## Changed files

- `progress/stage01-snapshot-core.md`
- `src/main/java/com/linguan/codemd/stage01/`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | EXPECTED RED | `testCompile` fails with 45 missing-symbol errors for the planned Stage 01 request/snapshot types and `Stage01Analyzer`; no unrelated failure observed. |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | FAIL | Compiled and ran 7 tests: 2 happy-path `SOURCE_HASH_MISMATCH` errors, 5 requested negative cases pass. |
| `wc -c` and `shasum -a 256` over `src/test/resources/stage01/reservation-v1/` | FAIL (fixture) | Only `InventoryMapper.xml` conflicts with `fixture-manifest.properties`: manifest `...f5118`, actual `...f5118b`; all other entries match. |
| Read repaired fixture manifest | PASS | `file.5.sha256` now equals the independently observed `ac9b…f5118b`. |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | PASS | 7 tests run; 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest=ManifestEvidenceVerificationTest test` | PASS | 2 tests run; 0 failures, 0 errors, 0 skipped. |
| `git diff --check -- linguan-prototype-v2/backend-agents/sources/github-code/src/main/java/com/linguan/codemd/stage01 linguan-prototype-v2/backend-agents/sources/github-code/progress/stage01-snapshot-core.md` | PASS | Exit 0; no whitespace error output. |

## Decisions

- Public M1 request/value types and `Stage01Analyzer.verify(FrozenRepositoryRequest)` stay inside `com.linguan.codemd.stage01`; no existing MVP surface is reused or changed.
- Failure is fail-closed through a stable-code `Stage01Exception`; no partial snapshot or M2/M3 behavior is produced.

## Blockers

- None.

## Exact next action

- None; hand the completed M1 core to the next explicitly assigned Stage 01 slice.

## Resume checks

- Re-read this file and local `AGENTS.md`, run `git status --short`, confirm test inputs remain unchanged, then rerun only the listed targeted selectors.
