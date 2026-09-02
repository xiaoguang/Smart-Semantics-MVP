# Progress: source-analysis-frozen-request-admission

- Status: COMPLETE
- Agent role: implementation coordinator; target-contract-first vertical slice
- Model: gpt-5.6-terra / xhigh implementation, guided by published Sol/ultra design
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Implement only M1 `FrozenRequestAdmission` in the formal `analysis.inventory` step, with a public-seam RED/GREEN test and no changes to historic POC code.
- Approved inputs: Published target architecture and verified-source-inventory design; synthetic fixtures only. No customer source capture, customer Maven, network source, or live Provider call.
- Current branch/worktree: `codex/source-analysis-request-admission` at `/private/tmp/linguan-source-analysis-request-admission`

## Completed

- Reopened the fresh worktree at `origin/main` commit `7b874cf` and confirmed it is clean.
- Read the root, backend, and source-scoped instructions; the two implementation plans; the M1 contract; and the current artifact-store seam.
- Confirmed the directory/package Wire Reset is historical and complete. This work must neither modify nor import the removed POC.

## Current state

- The independent local-Git capture seam is complete but does not implement M1 request admission.
- The shared module store already registers the M1 payload shape. M1 still needs a strict, path-free request-to-registered-capture admission seam and its first direct public test.
- Added the first contract test and an eight-path DepotHead-shaped synthetic fixture. It deliberately names only the new M1 public types; the next targeted Maven run is expected to fail during test compilation because those types do not yet exist.
- The initial happy-path test is now GREEN. A second, one-behavior public-seam test has been added for path traversal; it is intentionally expected to remain RED until path validation moves from input construction to M1 admission.
- The traversal test is GREEN after moving its validation to M1. The next one-behavior RED checks that an empty inventory is rejected by M1 rather than by an injected boundary record.
- Empty inventory is now rejected by M1. Added capture-identity mismatch coverage before extending the request parser to the remaining schema and determinism cases.
- Capture identity mismatch is GREEN. Added a strict valid `ROUND_2` request case: source admission must keep the exact frozen source binding while accepting the top-level run contract needed by the later reader-candidate lifecycle.
- Valid `ROUND_2` is GREEN. Added deterministic replay coverage: an unordered capture inventory must produce the identical admitted request, before M2 receives it.
- M1 direct selector is GREEN with six behaviors. The stage document's current-maturity audit now records this as an unpublished pure-admission core, not as a completed source-inventory step.
- SpotBugs, PMD, Enforcer and packaging are GREEN after M1 stopped accepting an injected codec instance.

## Changed files

- `progress/source-analysis-frozen-request-admission.md`
- `src/test/resources/analysis/inventory/request-admission/depothead-eight-paths.txt`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FrozenRequestAdmissionTest.java`
- `docs/analysis-steps/01-verified-source-inventory.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Fresh worktree was clean before this progress file. |
| scoped-design inspection | PASS | M1 contract, registered module address, payload fields, failure codes, and no-POC boundary confirmed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` | EXPECTED RED | Test compilation reports the missing M1 public types only: `FrozenRequestAdmission`, `AdmittedSourceRequest`, capture/profile views, inventory scope/file/disposition records. |
| initial happy-path GREEN | PASS | 1 test, 0 failures/errors/skips after adding the minimal M1 seam. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (path test) | EXPECTED RED | 2 tests compiled; the traversal case errors at `CapturedRegularFile` construction, proving path validation sat in the wrong boundary rather than M1. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (path GREEN) | PASS | 2 tests, 0 failures/errors/skips; M1 now reports `SOURCE_PATH_INVALID`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (empty test) | EXPECTED RED | 3 tests compiled; empty inventory was rejected by `CaptureReceiptView` construction instead of M1, so its boundary is now narrowed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (empty GREEN) | PASS | 3 tests, 0 failures/errors/skips; M1 now returns `REQUEST_SCHEMA_INVALID` for empty inventory. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (capture binding) | PASS | 4 tests, 0 failures/errors/skips; M1 returns `CAPTURE_IDENTITY_INVALID` for a registration mismatch. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (valid ROUND_2) | EXPECTED RED | 5 tests compiled; the valid R2 request failed because M1 still applied ROUND_1 parent/finding constraints. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (valid ROUND_2 GREEN) | PASS | 5 tests, 0 failures/errors/skips; R2 accepts a parent and sorted approved finding references without changing source binding. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (inventory ordering) | PASS | 6 tests, 0 failures/errors/skips; unordered capture input produces the same admitted request. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | EXPECTED FORMAT RED | Four new M1 files need the repository formatter; no semantic or compiler failure was reported. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipUTs=true verify` | EXPECTED QUALITY RED | SpotBugs reports `EI_EXPOSE_REP2`: M1 stored a caller-supplied codec. The constructor is being narrowed to own its fixed canonical codec. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` (post-quality correction) | PASS | 6 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 0 format violations. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipUTs=true verify` (post-quality correction) | PASS | Enforcer, JDK 17 compile, SpotBugs, PMD and packaging passed; tests intentionally skipped by this static-quality command. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SourceAnalysisArchitectureTest,FrozenRequestAdmissionTest test` | PASS | 9 tests, 0 failures/errors/skips; the new core stays inside the approved semantic package and the M1 contract remains green. |

## Decisions

- Treat historical POC only as Git-history evidence; no code, fixture, API, artifact, or compatibility dependency may be introduced from it.
- Keep this delivery bounded to the M1 pure-admission core. Its formal module payload/receipt installation, M2 source-byte verification and M3 semantic publication remain separate deliveries.
- The implementation will use the existing canonical module store rather than bypassing persistence with in-memory handoff.
- M1 owns its canonical JSON codec; callers may prepare canonical request bytes but cannot inject or substitute the parser/identity implementation.

## Blockers

- None for this bounded pure-admission core. A formal M1 publication still needs the private source-registration registry boundary and the already-designed receipt-last module install wiring; this does not make the current code a completed source-inventory step.

## Exact next action

- Commit and fast-forward push this explicit M1 pure-admission core. Start a fresh branch from `origin/main` for the private registration lookup and persisted M1 module publication, then continue toward M2 byte verification.

## Resume checks

- Read this file, run `git status --short`, verify `origin/main` remains `7b874cf` or a descendant, and rerun the M1 selector after any implementation change.
