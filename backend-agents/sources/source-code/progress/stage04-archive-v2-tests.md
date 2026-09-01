# Progress: Stage 04 archive-v2 RED tests

- Status: COMPLETE
- Agent role: TDD test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Maintain the bounded archive-v2 RED contract and migrate the four dependent Stage04 tests/fixtures at the public CandidateAssembler -> FilesystemCandidateStore -> CandidateValidationService seam; no production/design changes.
- Approved inputs: Frozen test fixtures and the existing Stage 04 public seam only.
- Current branch/worktree: Shared working tree; preserve unrelated changes.

## Completed

- Read root, backend-agents, and github-code AGENTS.md instructions.
- Read Stage 04 archive layout, validation contract, and archive-v1 to archive-v2 TDD migration sections.
- Read the existing Stage04CandidateAssemblerTest, Stage04CandidateStoreTest, Stage04ValidationTraceTest, and the public CandidateAssembler, FilesystemCandidateStore, and CandidateValidationService seams.

## Current state

Added the bounded `Stage04ArchiveV2Test` class and migrated `Stage04CandidateAssemblerTest`, `Stage04CandidateStoreTest`, `Stage04ValidationTraceTest`, and `Stage04TypedTraceTest` to the archive-v2 contract. The shared `Stage04CandidateFixture` drives normal candidate paths through a real Stage03 replay and sealed transcript.

The shared fixture runs a real `Stage03Generator` through `LifecycleProviderBridge`: Scenario canonical rounds are used only as the scripted adapter response source, `bridge.seal(generated)` supplies the transcript, and the transcript is passed to the eight-argument `CandidateAssemblyRequest` together with the full `Stage03Request`. Archive model-round and generation-receipt assertions read from that transcript; they are not reconstructed from final interpretations or a direct-provider lifecycle result. Its non-zero Flow transcript is asserted non-empty, so no empty or synthetic transcript can make the normal path pass.

The store-only test now expects the exact 19 non-manifest archive-v2 artifact names. Its direct byte fixture remains intentionally storage-only and never enters validation as a claimed non-zero Flow; semantic non-zero Flow closure comes from the shared real replay fixture.

## Changed files

- `progress/stage04-archive-v2-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04ArchiveV2Test.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04CandidateFixture.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04CandidateAssemblerTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04CandidateStoreTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04ValidationTraceTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04TypedTraceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing Stage 04 worktree changes preserved. |
| `mvn -Dtest=Stage04ArchiveV2Test test` | RED (intentional compile gate) | Exact compiler RED: `Stage04ArchiveV2Test.java:[284,45] no suitable constructor found for CandidateAssemblyRequest(CandidateSeriesRequest,CandidateLineage,RoundSlotView,Stage01Result,Stage02Result,Stage03Request,Stage03Result,Stage03RunTranscript)`; the six-argument and seven-argument `(Stage03Request, Stage03Result)` candidates are both inapplicable. No tests run. |
| `mvn -Dtest=Stage04CandidateStoreTest,Stage04CandidateAssemblerTest,Stage04ValidationTraceTest,Stage04TypedTraceTest test` | RED with production-parallel failures | Compilation succeeded. `Stage04CandidateStoreTest`: 6 run, 0 failures. `Stage04CandidateAssemblerTest`: 2 run, 0 failures. `Stage04ValidationTraceTest`: failure at line 56, `an honest assembled Candidate validates in a fresh service instance`, expected `true` but was `false`. `Stage04TypedTraceTest`: failure at line 81/139, `FACT_SENTENCE must resolve through the typed Trace seam`, unexpected `M8Exception: TRACE_CLOSURE_BROKEN`. Total: 12 run, 2 failures, 0 errors. |

## Decisions

- Keep the archive-v2 coverage in one test class; use only minimal fixture extensions if the public seam requires them.
- Run only the new test selector and record the exact expected RED failure.
- Use the current public reservation fixture (one non-zero Flow/Capsule); no public zero-Capsule bridge is available, so the two JSONL 0-byte checks are deferred and must remain explicitly documented as such.
- Require `CandidateAssemblyRequest` to receive the full `Stage03Request`; archive model rounds must be checked against `Stage03Result.canonicalRounds`, never inferred from final interpretations.
- Require the fixture to use the real `LifecycleProviderBridge` transcript seam: canonical rounds may script adapter responses only, while `bridge.seal(generated)` is the sole source for archived model-round and generation-receipt expectations.
- Migrate normal dependent-test paths through the shared real-replay fixture and require exact 19-artifact archive-v2 names; keep the direct CandidateStore byte fixture isolated to storage semantics.

## Blockers

- At the selector run, the parallel production store/assembler path was v2, but `CandidateValidationTrace` still recognized the old 17-artifact/archive-manifest-v1 contract. Consequently the honest v2 candidate was rejected by fresh validation and TypedTrace then surfaced `TRACE_CLOSURE_BROKEN`. No test was weakened to mask this; the validation/trace production migration remains outside this test-only task.

## Exact next action

Hand the exact selector failures and transcript/full-request contract to the parallel archive-v2 validation/trace implementation; after that production work lands, rerun only the affected Stage04 selectors (including `Stage04ArchiveV2Test` where applicable).

## Resume checks

- Re-run `git status --short` and inspect this file before continuing.
