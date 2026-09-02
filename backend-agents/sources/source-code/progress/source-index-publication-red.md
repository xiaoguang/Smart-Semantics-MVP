# Progress: source-index-publication-red

- Status: COMPLETE
- Agent role: source-inventory M2 publication test author
- Model: gpt-5.6-luna / xhigh test design under the published Sol/ultra design
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Complete the verified-source-inventory M1→M2→M3 module chain: M2 must reopen and verify M1 before it reads source bytes; M3 must publish the exact reader-visible four-file analysis-step set. Excludes AST, model calls and the top-level product executor.
- Approved inputs: `docs/analysis-steps/01-verified-source-inventory.md` §8.1 M2 and §8.1.1, plus the completed M1 publication seam.
- Current branch/worktree: `codex/source-analysis-verified-inventory` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the completed M1 publication contract and verified its canonical writer is on remote `main` at `2cb996e`.
- Added the M1-reopen expectation to `AdmittedSourceRequestModulePublisherTest`; first targeted
  run failed at test compilation because `AdmittedSourceRequestModuleReader` did not exist.
- Implemented the narrow reader: it fresh-reopens the M1 publication, verifies its address,
  receipt status, exact payload descriptor and canonical JSON fields, then projects only the
  persisted source declarations plus the three verified upstream capture references needed by M2.
- Refactored the M2 pure verifier to consume that projection, deriving file IDs and text/media
  partitions only after source-byte verification rather than accepting them from M1 memory.
- Added and observed a second RED: callers had no public M1-publication-only M2 method. The
  public `VerifiedSourceIndexer.index(M1 reference, module store, source registry)` seam is now
  present; the internal projection overload is package-private for focused tests only.
- Received explicit approval for the §8.3.2 source-shard formula and added it to the step design.
- Added a full M2 publication RED against a real synthetic local capture and receipt-last M1.
  The final RED named the missing `VerifiedSourceIndexModulePublisher`; its first execution then
  surfaced only two fixture errors (the store directory was not created and the test expected an
  unsorted upstream list), both corrected before evaluating production behavior.
- `VerifiedSourceIndexModulePublisher` now fresh-reopens M1, reuses only the permitted internal
  projection, verifies source bytes, computes one full-inventory shard from the approved
  identity material, and installs `verified-source-index.json` plus the M2 receipt.
- Added a source-shard tamper regression: M3 now rejects syntactically valid M2 shard receipts
  whose shard ID cannot be recomputed from the approved complete-inventory identity material.
- Added a real synthetic local-Git composition test. It runs Capture → FrozenRequestAdmission →
  persisted M1 → fresh-reopened M2 → M3 → AnalysisStep store, including one UTF-8 source file
  and one binary file. The final output is the three semantic files plus store-last receipt.
- Found and fixed a shared-store persistence defect exposed by that chain: AnalysisStep receipts
  previously discarded a non-null prompt bundle hash, making an initial install unreopenable.
  The artifact-store regression test now covers the non-null round trip.

## Current state

- The M1→M2→M3 source-inventory module chain is complete and fresh-reopens its reader-visible
  four-file set. The direct selector gate, Spotless check and `git diff --check` all passed after
  the final formatting pass. A future public executor will orchestrate this chain; it is not part
  of this module publication work.

## Changed files

- `progress/source-index-publication-red.md`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/AdmittedSourceFile.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexInput.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/AdmittedSourceRequestModuleReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexer.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/SourceShardIdentity.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublicationSpecifier.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicAnalysisStepPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/AdmittedSourceRequestModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceIndexModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublicationSpecifierTest.java`
- `src/test/java/org/sourceanalysis/app/artifact/CanonicalAnalysisStepArtifactStoreTest.java`
- `docs/analysis-steps/01-verified-source-inventory.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| M1 reader RED | PASS | `AdmittedSourceRequestModulePublisherTest` failed because the required reader type was absent. |
| M1 reader + M2 core GREEN | PASS | First GREEN: 4 tests, 0 failures/errors/skips. |
| M2 public seam RED/GREEN | PASS | `VerifiedSourceIndexerTest` first failed because no `index(M1 reference, module store, registry)` entry existed; after the smallest wrapper, `mvn -t .mvn/toolchains.xml -o -Dtest=AdmittedSourceRequestModulePublisherTest,VerifiedSourceIndexerTest test` passed 5 tests, 0 failures/errors/skips. |
| M2 publication RED/GREEN | PASS | Initial test compile failed only because `VerifiedSourceIndexModulePublisher` was absent; after implementation `mvn -t .mvn/toolchains.xml -o -Dtest=AdmittedSourceRequestModulePublisherTest,VerifiedSourceIndexerTest,VerifiedSourceIndexModulePublisherTest test` passed 6 tests, 0 failures/errors/skips. |
| M3 shard identity RED/GREEN | PASS | `VerifiedSourceInventoryPublicationSpecifierTest` initially accepted a mismatched syntactically valid shard ID; it now recomputes the approved full-inventory identity and passes 4 tests. |
| Real M1→M2→M3 composition | PASS | The same M3 selector now runs a local Git capture through `FrozenRequestAdmission`, all three persisted modules and the analysis-step store, producing text/media counts 1/1 and a fresh-reopenable four-file set. |
| Prompt-control persistence RED/GREEN | PASS | `CanonicalAnalysisStepArtifactStoreTest` initially failed with `ANALYSIS_STEP_PUBLICATION_INVALID` for a non-null prompt control; serialization now round-trips it and the selector passes 3 tests. |
| Stage-1 direct selectors | PASS | `FrozenRequestAdmissionTest,AdmittedSourceRequestModulePublisherTest,VerifiedSourceIndexerTest,VerifiedSourceIndexModulePublisherTest,VerifiedSourceInventoryPublicationSpecifierTest,CanonicalAnalysisStepArtifactStoreTest`: 19 tests, 0 failures/errors/skips. |
| Formatting and diff | PASS | `spotless:apply`, then `spotless:check` and `git diff --check` passed. |
| Final post-format gate | PASS | Re-ran the six direct selectors after Spotless: 19 tests, 0 failures/errors/skips; `git diff --check` passed. |

## Decisions

- M2 reads `ModulePublicationReference` through a dedicated reader and accepts an opaque
  source-byte registry; it will not accept `AdmittedSourceRequest` or a source path.
- The M1 payload body deliberately has no capture/manifest reference fields. M2 obtains exactly
  one `source-registration`, `capture-receipt`, and `snapshot-manifest` reference from M1's
  verified receipt upstream closure, then independently matches them against the registry.
- The explicitly approved M2 result has one `FULL_INVENTORY_VERIFICATION` logical shard. Its ID
  is deterministic from M1 request artifact ID, sorted complete file-ID denominator, matching
  verified set, `SUCCEEDED`, and no Gap IDs; physical scheduling is excluded.
- M3 treats that shard formula as input validation, rather than trusting a source-index receipt
  only because its listed file sets happen to close.
- Analysis-step receipts preserve the full `ArtifactControls` value, including an optional
  prompt hash, because fresh reopen compares the receipt with the publisher module exactly.

## Blockers

- None.

## Exact next action

- The parent Agent may make one full verified-source-inventory commit and merge it to `main`.
  The next coding task must start from that `main` baseline and use a new progress file.

## Resume checks

- Read this file and its 19-test final gate before changing inventory code. Do not create another
  M1/M2/M3 micro-commit; begin the application-discovery step from the merged `main` baseline.
