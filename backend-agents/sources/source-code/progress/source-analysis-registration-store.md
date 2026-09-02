# Progress: source-analysis-registration-store

- Status: COMPLETE
- Agent role: implementation coordinator; capture-registry and persisted M1-publication slice
- Model: gpt-5.6-terra / xhigh implementation, guided by published Sol/ultra design
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add the private, nofollow-safe bridge from a captured source registration to the formal M1 persistence boundary. This is the follow-on to the merged pure-admission core, not a rewrite of POC or a new source capture.
- Approved inputs: Published target architecture, verified-source-inventory design, exact synthetic capture fixtures. No real jshERP capture, customer Maven, network source, or live Provider.
- Current branch/worktree: `codex/source-analysis-registration-store` at `/private/tmp/linguan-source-analysis-registration-store`

## Completed

- Started from pushed `main` commit `fd7c6a4` with a clean worktree.
- Re-read the capture storage layout and M1 contract.
- Confirmed current capture persists rootless registration, capture receipt, manifest and blobs, but exposes only a reference and has no private registry-reopen seam for M1.
- Added `LocalGitSourceRegistry`, `RegisteredSourceCapture`, and `RegisteredSourceFile`. The registry fresh-reopens and canonical-verifies a registration, receipt and complete manifest by content identifier while returning no local workspace, blob path or raw source bytes.
- Corrected the registry's receipt verification: receipt identity is deliberately computed before its self-ID field is written, while its SHA is computed over the stored final document. Manifest identity remains direct content addressing.
- Formatted the changed Java sources and completed the direct tests plus the local quality profile.

## Current state

- The preceding M1 pure-admission core is merged and has six direct tests; it accepts only injected path-free views and does not publish a module artifact.
- This work introduces no public filesystem path in analysis requests, registration views, receipts, identities or errors.
- The registry's public seam was verified with a synthetic commit containing both text and binary files; binary disposition remains `NON_ANALYZABLE_MEDIA`.

## Changed files

- `progress/source-analysis-registration-store.md`
- `src/test/java/org/sourceanalysis/app/capture/localgit/LocalGitSourceRegistryTest.java`
- `src/main/java/org/sourceanalysis/app/capture/localgit/LocalGitSourceRegistry.java`
- `src/main/java/org/sourceanalysis/app/capture/localgit/RegisteredSourceCapture.java`
- `src/main/java/org/sourceanalysis/app/capture/localgit/RegisteredSourceFile.java`
- `src/main/java/org/sourceanalysis/app/capture/localgit/SourceRegistrationRegistryException.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Fresh worktree was clean before this progress file. |
| capture-storage inspection | PASS | Registration is stored under private `registrations/<id>/`; snapshot receipt, manifest and blobs are content-addressed under `snapshots/<snapshotId>/`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitSourceRegistryTest test` | PASS | 1 direct test passed after correcting the receipt self-ID/content-SHA distinction. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | All Java formatting checks passed after `spotless:apply`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitSourceRegistryTest,SourceAnalysisArchitectureTest test` | PASS | 4 tests passed; registry seam and target dependency boundary remain valid. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipUTs=true verify` | PASS | Enforcer, Java 17 compilation, SpotBugs and PMD passed. |

## Decisions

- Preserve the existing capture as an independent raw-Git maintenance adapter. This delivery only reads its sealed outputs through a new private registry boundary.
- Build the registry and M1 module publication against synthetic captures. The final semantic Step 1 still also requires M2 byte verification and M3 step publication.
- Keep receipt self-ID validation distinct from direct content-address identity: both are integrity checks, but their preimage differs by design.

## Blockers

- None for this completed registry delivery. The current capture reference lacks the registration-document SHA, so the registry fresh-reads and verifies that private document before exposing any view; no caller-provided locator may compensate for it.

## Exact next action

- Commit and push this bounded registry delivery. A later source-inventory publication must wait for confirmation of the shared expected-origin enum and stable `fileId` formula; it must not invent either contract locally.

## Resume checks

- Read this file, run `git status --short`, verify the branch includes this registry delivery, and rerun the direct registry selector before changing this seam.
