# Progress: target-artifact-store-red-tests

- Status: COMPLETE
- Agent role: Luna/xhigh RED-test author for the shared canonical module-artifact store
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Add only `src/test/java/com/linguan/codemd/target/artifacts/CanonicalModuleArtifactStoreTest.java` and this progress file for the DESIGN §13.3.1 store seam.
- Approved inputs: Repository and `backend-agents/sources/github-code/AGENTS.md`; `docs/DESIGN.md` §13.3/§13.3.1; Stage 01 M3; existing `TargetContractsTest`; real JUnit `@TempDir` filesystem and the exact store bootstrap expression from DESIGN.
- Current branch/worktree: `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the repository/scoped AGENTS instructions, the complete DESIGN §13.3/§13.3.1 contract, Stage 01 M3 seam guidance, `TargetContractsTest`, POM, and current target contract sources.
- Confirmed `src/main/java/com/linguan/codemd/target/artifacts/` is not present in the current worktree, so the target production seam is intentionally absent for the RED handoff.
- Audited every DESIGN-listed store record needed by the requested tests: `StageModuleAddress`, `ArtifactReference`, `ArtifactControls`, `ModuleInstallRequest`, `CanonicalModulePayload`, `ModulePublicationReference`, `ArtifactDescriptor`, `InstalledModulePublication`, `VerifiedCanonicalPayload`, and `ReopenedModulePublication`.
- Created this progress file before modifying any test source.
- The design authority resolved the prior ambiguity in DESIGN §13.3.1: `ImmutableBytes` is a public final value type with `copyOf(byte[])`, `size()`, and `copyToByteArray()`; it has content equality and defensive-copy semantics.

## Current state

The exact public seam is now actionable. The test will use `ImmutableBytes.copyOf(byte[])` for request payloads and `copyToByteArray()` for independent reopen assertions, with no reflection or private coupling. Production `target/artifacts` types are still intentionally absent, so the focused selector should compile RED after the test is added.

The requested test class is now added. It uses the real `@TempDir` filesystem and exact `RunStoreBootstrap.openForTest` store constructor expression, handwrites the Stage 01 addressed JSON envelope identity, and exercises JSON/JSONL publication, idempotent replay/collision, invalid JSONL/path rejection, defensive bytes, receipt/root binding, and payload tamper invalidation.

- Corrected one test-only missing `java.util.HexFormat` import after the first compile check; no production/design/POM/old-test changes were made.
- Tightened the malformed JSONL/path assertions to require rejection and no visible module directory without assuming an undocumented failure-code mapping; collision and tamper retain their exact required codes.

## Changed files

- `progress/target-artifact-store-red-tests.md` (this file)
- `src/test/java/com/linguan/codemd/target/artifacts/CanonicalModuleArtifactStoreTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated worktree edits were present and preserved; only this progress and the new target-artifacts test are owned by this slice. |
| `rg -n 'ImmutableBytes|CanonicalModulePayload\\(|ModuleInstallRequest\\(' . --glob '!target/**' --glob '!**/.git/**'` | PASS | Updated DESIGN documents the exact `ImmutableBytes` API used by the test; production target/artifacts types remain absent. |
| `mvn -Dtest=CanonicalModuleArtifactStoreTest test` | RED (expected) | Main compilation succeeds (203 sources); testCompile stops with missing public `com.linguan.codemd.target.artifacts` types (`StageModuleAddress`, `ModuleInstallRequest`, payload/store/receipt records, enums, codec/bootstrap/installer/limits, and `ImmutableBytes`); 0 test methods execute. |
| `git diff --check` | PENDING | Re-run after the final assertion adjustment. |

## Decisions

- Use the authority-resolved `ImmutableBytes` API exactly; do not add a test-only replacement type or infer any additional store API.
- Keep one public-store test class covering the four requested behaviors: valid JSON atomic publication/reopen with defensive bytes and receipt/root binding; exact replay versus different-byte collision; canonical JSONL/path rejection; and installed-payload tamper invalidation.

## Blockers

- Production `target/artifacts` seam is absent in this worktree; the focused selector has the expected missing-public-seam testCompile RED after the final test-only assertion adjustment.

## Exact next action

Production Terra/xhigh can implement the DESIGN §13.3.1 public seam, then rerun only `mvn -Dtest=CanonicalModuleArtifactStoreTest test` against this RED.

## Resume checks

- Re-read this progress file and scoped AGENTS instructions.
- Run `git status --short` and confirm only this progress file plus `CanonicalModuleArtifactStoreTest.java` are owned by this slice.
- Do not modify this completed RED unless the authority changes the public seam; any such change requires a new bounded test update and selector run.
