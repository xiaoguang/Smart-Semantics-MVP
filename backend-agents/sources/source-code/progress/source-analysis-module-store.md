# Progress: source-analysis-module-store

- Status: COMPLETE
- Agent role: Delivery orchestrator
- Model: gpt-5.6-sol / ultra (design authority), gpt-5.6-luna / xhigh (RED), gpt-5.6-terra / xhigh (GREEN)
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: First filesystem canonical module-artifact store slice, using only the merged policy registry and published artifact contract.
- Approved inputs: User-approved complete implementation plan; `origin/main` at `c19bf4c`; published foundation design.
- Current branch/worktree: `codex/source-analysis-module-store` at `/private/tmp/linguan-source-analysis-module-store`

## Completed

- Created a fresh branch from the merged policy-registry delivery.
- Read the source-scoped instructions and the published module-store contract.
- Obtained a Sol/ultra bounded brief: implement only the verified-source-inventory request-admission
  `MODULE_ARTIFACT_JSON` publication/reopen path, using a real temporary filesystem and receipt-last
  completion. JSONL, standalone artifacts, validation, analysis-step/run stores, runtime, and
  production-root bootstrap remain out of scope.
- Established the first public-seam RED. The `CanonicalModuleArtifactStore` type is absent, so the
  test fails at the intended contract boundary rather than by compilation or fixture error.
- Added only the exact public module-store records, interface, fixed constructor seam, opaque handle,
  and real empty-temporary-directory bootstrap required by that RED. The public type/bootstrap
  selector is now green; no module publication can yet be installed or reopened.
- Added the receipt-last module publication engine for the single published request-admission JSON
  policy. A real M1 envelope now installs atomically, fresh-reopens with regenerated identity, and
  returns `ALREADY_INSTALLED` only for the same canonical request.
- Added a fail-closed receipt-removal regression: deleting the receipt after a successful install
  produces `MODULE_PUBLICATION_INVALID` and returns no partial payload.
- Ran the local quality gate. It compiled and ran all current tests, but SpotBugs found 19 medium
  findings: mutable lists escaping module record values plus three conservative null-flow warnings
  in the new engine. No commit will be made until those are addressed or an approved, justified
  exclusion exists.
- Established and passed a record-ownership RED/GREEN: install requests, receipts, installed
  publications, and reopened publications now copy list inputs and expose unmodifiable list views.
  The engine's flagged null-flow paths are now explicit failure branches. A fresh quality rerun is
  still required.
- Sol/ultra review reported eight P1 contract gaps in the first store slice. The hardening cycle
  closed them without changing the target contract: request/receipt registry binding, parent-chain
  NOFOLLOW resolution, bounded reads and directory-entry budget, non-empty payload receipts,
  complete channel writes, single-read receipt binding, stable policy/canonicality failures, and
  independent identity/wire golden coverage with legal content IDs.
- Added focused regressions for a foreign policy registry, an existing `runs` symlink, and a
  self-consistent receipt that falsely declares zero payloads. The positive regression now derives
  the payload ID, descriptor, module root, receipt self-ID, complete receipt SHA, and exact on-disk
  directory independently from the published formulas.
- Updated the durable maturity audit in the overall design, step-01 design, and README. It now
  accurately calls this a single M1 JSON persistence vertical slice, not a completed store or
  source-inventory implementation.

## Current state

- This request-admission JSON persistence slice is locally accepted: direct golden/security tests,
  all current module tests, formatter and quality gates pass. Analysis-step/run stores, JSONL,
  standalone artifacts, multi-payload collision hardening, production-root bootstrap and runtime
  remain explicitly out of scope for this delivery.

## Changed files

- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/` — path-free
  module-store public seam, records, opaque test root and private receipt-last publisher.
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/` — store
  contract, independent wire golden and immutable-record ownership tests.
- `backend-agents/sources/source-code/docs/DESIGN.md`, `docs/analysis-steps/01-verified-source-inventory.md`,
  `README.md` — current maturity audit.
- `backend-agents/sources/source-code/progress/source-analysis-module-store.md` and the three
  delegated review/RED progress records.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Fresh worktree is clean before this progress file. |
| `git log -1 --oneline origin/main` | PASS | Base is `c19bf4c feat(source-analysis): add canonical artifact policy registry (#5)`. |
| Sol/ultra module-store brief | PASS | Current design supports one `MODULE_ARTIFACT_JSON` vertical slice without a docs-only change. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | EXPECTED RED | 1 test, 1 assertion failure: missing public `CanonicalModuleArtifactStore`; 0 errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | PASS | 2 tests, 0 failures/errors/skips after the public seam and bootstrap GREEN. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | EXPECTED RED | 3 tests, 1 assertion failure: module install was not implemented; 0 errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | PASS | 3 tests, 0 failures/errors/skips after receipt-last install/reopen/idempotence GREEN. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | PASS | 4 tests, 0 failures/errors/skips; receipt removal rejects fresh reopen. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | RED | Compiled and ran 33 tests, then SpotBugs reported 19 medium findings; no quality PASS claim. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModulePublicationRecordOwnershipTest,CanonicalModuleArtifactStoreTest test` | PASS | 5 tests, 0 failures/errors/skips after ownership and null-flow corrections. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS | 34 tests, 0 SpotBugs findings, PMD PASS; superseded by the subsequent Sol review P1 findings. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | EXPECTED RED | 6 tests, 2 failures: foreign policy registry and symlink parent chain were wrongly accepted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | PASS | 8 tests, 0 failures/errors/skips after all current-slice P1 corrections and the independent golden. |
| `mvn -t .mvn/toolchains.xml -o test` | PASS | 38 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS | 38 tests, 0 SpotBugs findings, PMD PASS. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | All 72 Java files formatted. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- This slice stops at the module store’s first atomic JSON publication/reopen vertical path. It will not implement analysis-step store, run-manifest store, customer analysis, or runtime recovery.
- The fixture must use the published verified-source-inventory request-admission type/schema/filename;
  it must not introduce a synthetic probe artifact.
- Explicitly defer the reviewer’s P2 items to their owning slices: multi-payload receipt ordering,
  validation-only address enforcement, deterministic concurrent-destination collision handling,
  and production `RunStoreBootstrap.open(Path)`.

## Blockers

- None.

## Exact next action

- Commit this accepted bounded delivery, push it and merge it to `main`; then create the next fresh
  delivery branch from the merged `main`.

## Resume checks

- Read this file, inspect `git status --short`, verify the merge commit on `origin/main`, then read
  the next delivery’s detailed design and create a new progress record before any code change.
