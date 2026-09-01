# Progress: Stage 01 snapshot verification RED tests

- Status: COMPLETE
- Agent role: Stage 01 TDD RED test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add the first Stage 01 M1 snapshot-verification behavior tests and their bounded six-file synthetic fixture. No production Java, POM, or design changes.
- Approved inputs: Repository instructions, `docs/stages/01-proven-source-facts.md` sections 3–5/9/11, `DESIGN.md` synthetic reservation fixture, existing JUnit/Maven test style, and local immutable fixture bytes only.
- Current branch/worktree: shared worktree; preserve unrelated changes and other agents' files.

## Completed

- Read the repository-root, prototype, backend-agent, and GitHub-code `AGENTS.md` files.
- Read the Stage 01 M1 seam and the required synthetic six-file fixture contract.
- Read `pom.xml`, `progress/TEMPLATE.md`, and representative existing JUnit tests.
- Confirmed pre-edit `git status --short`; existing unrelated modifications are preserved.
- Created this progress file before adding test state.
- Added the six design-defined reservation source files and a literal test-side size/SHA manifest.
- Independently calculated the manifest SHA-256 values with Java `MessageDigest` and recorded them as reviewed literals.
- Added `Stage01Fixtures` request/fixture helpers and `VerifiedSnapshotContractTest` covering the requested M1 positive and fail-closed cases.
- Corrected the fixture manifest's `file.5.sha256` transposition to the independently verified Java `MessageDigest` value for `InventoryMapper.xml`.

## Current state

- The fixture-only SHA correction is complete. With the parallel Stage 01 verifier implementation present, the exact selector passes all seven M1 contract tests.

## Changed files

- `progress/stage01-snapshot-tests.md`
- `src/test/java/com/linguan/codemd/stage01/Stage01Fixtures.java`
- `src/test/java/com/linguan/codemd/stage01/VerifiedSnapshotContractTest.java`
- `src/test/resources/stage01/reservation-v1/` (six source files and fixture manifest)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Java `MessageDigest` over six fixture files | PASS | Reconfirmed `file.5.sha256=ac9b916d8e8b09461f9963c47b022e69cc06444d85e6fd137f2d6ee892f5118b`; all six literals match fixture bytes. |
| `git diff --check --no-index /dev/null <new-test-file>` | PASS | No whitespace errors. |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | PASS | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`; build success after correcting `file.5.sha256`. |

## Decisions

- Keep all request construction, independent expected SHA/size values, and fixture mutation helpers test-only.
- Use explicit expected fixture metadata and test-side `MessageDigest` only; never call a production hashing helper.
- Test M1 failure as a typed/inspectable failure receipt or stable exception code, with no M2 parser invocation observable from the public behavior.
- The wished-for Java seam is `new Stage01Analyzer().verify(FrozenRepositoryRequest)` returning `VerifiedSnapshot`; request data records are `Origin`, `CaptureProof`, `InventoryScope`, `DeclaredFile`, `ResourceBudget`, and `CapabilityProfileRef`, and snapshot files expose `VerifiedFile` metadata.

Each behavior test records the production bug it is intended to catch:

- `verifiesAllSixDeclaredFilesWithIndependentSizeAndShaMetadata`: omitted declared files, incorrect byte size/SHA, or non-canonical file ordering being admitted.
- `inventoryOrderDoesNotChangeSnapshotIdentityOrCanonicalFileOrder`: inventory iteration order leaking into snapshot identity or output ordering.
- `changedSourceBytesFailClosedWithHashDrift`: trusting the manifest without re-reading and hashing the frozen bytes.
- `relativeParentAndAbsoluteInventoryPathsAreRejected`: path traversal or absolute-path escape reaching source I/O.
- `declaredSymlinkIsRejectedBeforeReadingTargetBytes`: following a declared symlink instead of enforcing NOFOLLOW policy.
- `invalidUtf8IsRejectedEvenWhenDeclaredHashMatchesInvalidBytes`: silently replacing malformed UTF-8 instead of strict decoding.
- `resourceBudgetOverflowIsRejectedBeforeAnyParserCanRun`: opening/parsing source before enforcing the M1 file-count budget.

## Blockers

- Production verifier is present from the parallel implementation task; this fixture-only correction addressed a test-side SHA typo. No test or production logic was changed.

## Exact next action

- Hand the unchanged RED tests and their requested API shape to the Stage 01 production implementer; do not alter tests to make the initial RED compile.

## Resume checks

- Read this file and the nearest `AGENTS.md`; run `git status --short`.
- Ensure only this progress file and new `src/test` Stage 01 files are attributable to this task.
- Hand the corrected manifest and passing targeted selector result to the parent task; preserve all production implementation changes owned by the parallel agent.
- Preserve this file as COMPLETE; the next agent may update only its own progress file while implementing the API.
