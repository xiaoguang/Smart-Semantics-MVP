# Progress: Stage04 archive-v2 core

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Scope: archive-v1 to archive-v2 Candidate assembler, store/manifest, identity, and deterministic validation replay only.
- Approved inputs: scoped `AGENTS.md`; Stage04 §§10–§11/§16.2; current Stage03 deterministic replay seam; Stage04 archive-v2 RED test/progress.
- Current branch/worktree: shared and pre-existing dirty; unrelated work is preserved.

## Completed

- Read scoped guidance, Stage04 archive-v2 design/replay boundary, current archive-v2 RED test/progress, and workspace status.
- Confirmed the approved boundary: Stage04 preserves and verifies replay preimages, while `Stage03DeterministicReplay` alone owns R1/R2 parsing, admission, M6/M7, and rendering.
- Reproduced the focused RED with `mvn -Dtest=Stage04ArchiveV2Test test`: test compilation stops at the real fixture migration seam because `CandidateAssemblyRequest` lacks its required complete `Stage03Request` argument. No production archive behavior executed.
- Added the required complete `Stage03Request` argument to `CandidateAssemblyRequest` (with a source-compatible six-argument constructor only so existing callers still compile). The Stage04 archive-v2 test now executes 18 cases.
- Confirmed the archive structure RED: one positive assertion sees the old 17-artifact bundle; the other 17 cases reach the expected filesystem setup but error because the current assembler/store have no `registry-bundle.json` or `model-rounds.jsonl`.
- Confirmed the former lifecycle provenance blocker is resolved by the new bridge-owned immutable `Stage03RunTranscript`: fixture execution now drives `LifecycleProviderBridge` through `Stage03Generator`, then archives only the transcript returned by `bridge.seal(generated)`.
- Reproduced the resumed RED with `mvn -Dtest=Stage04ArchiveV2Test test`: initially one compile error for the missing eighth `Stage03RunTranscript` request argument. Added only that input seam, then re-ran to reach the behavioral RED: 18 tests, 1 exact-layout failure and 17 expected missing-artifact setup errors (`registry-bundle.json`, `model-rounds.jsonl`).
- Added the v2 source-control, public frozen-registry, model-round, and lifecycle-receipt projections; upgraded the store and archive reader to the exact 19-artifact v2 manifest. The focused selector reached one fresh-validation RED. Root cause: Stage03 task-input bytes were construction-order rather than recursive canonical JSON, so archive canonicalization correctly exposed a strict replay task mismatch. Parent approved the minimal Stage03 producer correction; Stage04 will continue to consume, never recreate, those bytes.
- `mvn -Dtest=Stage04ArchiveV2Test test` is GREEN: 18 tests, 0 failures, 0 errors, 0 skipped. The positive case installs the exact 19 non-manifest artifacts and accepts a fresh no-Provider Stage03 deterministic replay. The mutation cases reject missing v2 artifacts, drifted frozen registry content/digests, missing task/schema/response/receipt preimages, and legacy v1/17-artifact manifests.

## Current state

- Archive-v2 assembly, store, fresh validation, and zero-Provider Stage03 replay are complete. The v2 candidate sidecar binds the source-input, frozen registry, model-round, receipt, and trace roots used for candidate content identity.

## Changed files

- `progress/stage04-archive-v2-core.md`
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`
- `src/main/java/com/linguan/codemd/stage04/FilesystemCandidateStore.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java` (approved cross-Stage canonical task/schema producer correction)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ArchiveV2Test test` | RED | Test compilation: `CandidateAssemblyRequest` has six arguments while the migrated fixture supplies the required seventh `Stage03Request`; no test executed. |
| `mvn -Dtest=Stage04ArchiveV2Test test` | RED | 18 tests executed: 1 failure (old 17-artifact output) and 17 errors caused by absent archive-v2 `registry-bundle.json` / `model-rounds.jsonl`; no lifecycle receipt synthesis attempted. |
| `mvn -Dtest=Stage04ArchiveV2Test test` | PASS | 18 tests, 0 failures, 0 errors, 0 skipped; archive-v2 accepts only the complete 19-artifact bundle and replays without a Provider. |
| `mvn -Dtest=Stage04IdentityLedgerTest,Stage04ValidationTraceTest,Stage04ArchiveV2Test test` | PASS | 23 tests, 0 failures, 0 errors, 0 skipped after versioning request/series/candidate/validation identities as v2. |
| `mvn -Dtest=Stage04ArchiveV2Test,Stage04CandidateAssemblerTest,Stage04ValidationTraceTest test` | PASS | 22 tests, 0 failures, 0 errors, 0 skipped after binding the five v2 archive roots in `candidate.json`. |
| `mvn -Dtest='Stage04*Test' test` | PASS | 39 tests, 0 failures, 0 errors, 0 skipped before the final root-sidecar closure refinement. |
| `mvn -Dtest=Stage03DeterministicReplayTest test` | PASS | 10 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest='Stage04*Test' test` | PASS | Final run: 39 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest='Stage03*Test' test` | PASS | Final run: 69 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | PASS | No whitespace diagnostics. |

## Decisions

- Do not introduce archive compatibility: a v1 17-artifact Candidate must fail closed rather than receive a partial v2 interpretation.
- Do not construct, inject, or call a Provider during validation/replay, and do not duplicate Stage03 parser/admission behavior in Stage04.
- Do not manufacture `preflightReceiptId`, `attemptId`, `startedEventId`, or any other lifecycle evidence. `Stage03RunTranscript` is now required and is the sole source for each archived model round and generation receipt.
- The publicly archived effective registry contract is intentionally partial: it retains the complete frozen public `RegistryBundle` plus public profile/budget references. Stage04 does not reproduce Stage03's private registry canonicalizer; deterministic replay revalidates that frozen public input through Stage03.
- Archive-v2 uses `canonical-request-v2`, `candidate-series-v2`, `candidate-v2`, `candidate-validation-policy-v2`, and `validation-receipt-v2`; candidate identity binds the five immutable artifact roots, while the manifest binds the exact 19-artifact set.

## Blockers

- None.

## Exact next action

- Complete; retain the archive-v2 effective-contract boundary unless a future public Stage03 canonical-contract seam is approved.
