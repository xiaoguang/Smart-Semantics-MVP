# Progress: source-analysis-verified-source-inventory

- Status: IN_PROGRESS
- Agent role: verified-source-inventory implementation coordinator
- Model: gpt-5.6-terra / xhigh implementation, following Sol/ultra-published design; Luna/xhigh public-seam RED tests are represented by the existing and new direct selectors
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Complete the first semantic analysis step only: connect registered capture reopening to M1 request admission, implement M2 byte/hash/UTF-8/line-index verification, and publish the three semantic payloads plus the verified-source-inventory receipt. No AST, application discovery, customer capture, customer Maven or Provider work.
- Approved inputs: `c780558` published source-identity contract; verified-source-inventory design; existing synthetic local-Git fixtures; no live source or model invocation.
- Current branch/worktree: `codex/source-analysis-verified-inventory` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Started from clean `origin/main` at `c780558`.
- Re-read root, backend, source-scoped instructions and both required implementation plans.
- Confirmed design-publication gate is satisfied by `c780558`: `ExpectedOriginV2` and stable `file:` formula are now published.

## Current state

- Existing capture, source-registration registry, and pure M1 request-admission core are independent slices. They do not yet produce the four official first-step outputs.
- Added `VerifiedSourceIndexerTest`, a public M2 seam using a local synthetic Git commit. It requires source bytes to be reopened only through the registration, verifies both text/media partitions and `file:` calculation, and corrupts a sealed blob to require `SOURCE_HASH_MISMATCH`.
- Implemented the bounded M2 core: its only byte source is the opaque registered snapshot handle; it rechecks capture/file metadata, exact `file:` identity, byte count and SHA-256, and only then builds strict-UTF-8 line indexes for text files. Media is retained but has no text index.

## Changed files

- `progress/source-analysis-verified-source-inventory.md`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexerTest.java`
- `src/main/java/org/sourceanalysis/app/capture/localgit/RegisteredSourceSnapshot.java`
- `src/main/java/org/sourceanalysis/app/capture/localgit/LocalGitSourceRegistry.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexer.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceFile.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndex.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexException.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Fresh implementation worktree is clean before this progress file. |
| design/publication preflight | PASS | `origin/main` includes `c780558`, the docs-only identity-contract commit. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=VerifiedSourceIndexerTest test` | RED | Test compilation failed only because `VerifiedSourceIndexer`, `VerifiedSourceIndex`, `VerifiedSourceFile` and `VerifiedSourceIndexException` do not yet exist (5 missing symbols). |
| `mvn -t .mvn/toolchains.xml -o -Dtest=VerifiedSourceIndexerTest test` | PASS | 2 tests: verified text/media inventory and same-size sealed-blob drift rejected as `SOURCE_HASH_MISMATCH`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=VerifiedSourceIndexerTest,LocalGitSourceRegistryTest,FrozenRequestAdmissionTest test` | PASS | 9 direct source-inventory/capture tests; no failures, errors, or skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | All Java files formatted after the M2 changes. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS, scope note | Maven ran 56 existing module tests despite `-DskipTests`; SpotBugs and PMD passed. Do not repeat this broad verification for subsequent narrow slices until the POM skip-property mapping is corrected in its dedicated toolchain work item. |

## Decisions

- Implement the source-indexer before publication so byte verification and stable file identities are independently testable.
- The test drives M2 through M1's `AdmittedSourceRequest` shape, but does not pretend that M1 is already connected to a frozen-request reader; that formal composition remains in this work unit.
- No AST is parsed in this work unit. JavaParser first belongs to the next semantic step, application discovery.

## Blockers

- None identified.

## Exact next action

- Publish the factual M2 maturity update as a docs-only commit, then implement the next RED for M1/M2 persisted module publications; do not claim the first analysis step complete before M3 and analysis-step installation.

## Resume checks

- Read this file, run `git status --short`, confirm `HEAD` descends from `c780558`, and run only the inventory selector named here before changing production code.
