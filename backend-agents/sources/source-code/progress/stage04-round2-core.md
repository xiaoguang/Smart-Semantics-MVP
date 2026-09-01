# Progress: stage04-round2-core

- Status: COMPLETE
- Agent role: Stage 04 Round-2 Slice 3 production vertical
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Implement the bounded one-Flow Round-2 finding/overlay path through existing Stage 01–04 production seams, retaining zero-provider deterministic archive validation and old Stage 03 behavior.
- Approved inputs: scoped `AGENTS.md`; Stage 04 §§4.2/9.2; `progress/stage04-round2-tests.md`; `Stage04Round2Test`; current Stage 03 task/generator/replay and Stage 04 lifecycle/archive/validation/ledger implementation.
- Current branch/worktree: shared dirty worktree; preserve unrelated changes and every other agent's progress.

## Completed

- Read the scoped guidance, §4.2/9.2 contract, full Round-2 RED test, and existing review-store, Stage 03 task/replay, lifecycle, archive, validation, and agent seams.
- Confirmed the test requires a public five-argument agent construction seam and validates both task-envelope and archive/replay lineage rather than only a candidate ID.

## Current state

- The five-argument Agent seam now runs the real Round-2 orchestration: it validates the Round-1 parent, resolves approved findings before slot reservation, replays Stage 01/02, creates finite per-Flow overlays, drives the normal Stage 03 R1/R2 protocol through lifecycle capture, then installs and validates the candidate.
- Stage 03 generation/replay accepts only validated per-Flow `FlowImprovementOverlay` records. The overlay is appended to the canonical task input and the archive records both the base-input and overlay digests. Fresh validation reconstructs the overlay from archived task bytes and passes it into zero-Provider deterministic replay.
- The corrected Round-2 test is GREEN. It confirms one Flow performs exactly two new lifecycle-backed Provider rounds, the parent artifact remains byte-immutable, every improved task preserves the exact base input and carries only the closed overlay, and repeated/conflicting/round-two-parent requests do not consume Provider work.

## Changed files

- `progress/stage04-round2-core.md` (this file)
- `src/main/java/com/linguan/codemd/stage03/FlowImprovementOverlay.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03ReplayRequest.java`
- `src/main/java/com/linguan/codemd/stage04/DefaultCodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04Round2Test test` | RED (expected) | Initial 1/1 failure: absent five-argument constructor/`ROUND_2_IMPROVEMENT_NOT_IMPLEMENTED`. |
| `mvn -Dtest=Stage04Round2Test test` | Compile RED | First implementation compile found only an invalid `CandidateSeriesRequest.seriesId()` call; corrected by using the existing ledger identity seam. |
| `mvn -Dtest=Stage04Round2Test test` | BLOCKED | 1/1 assertion failure at `Stage04Round2Test:204`: improved task artifact uses object `inputJson`, so `asText()` returns an empty string and the test cannot see its otherwise provider-verified overlay. |
| `mvn -Dtest=Stage04Round2Test test` | GREEN | 1 test, 0 failures, 0 errors after the test began reading the archive-v2 object task representation. |
| `mvn -Dtest=Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04Round2Test test` | GREEN | 3 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Keep the overlay closed to canonical finding ID/code/permitted correction and archived flow/reader/section identifiers; it never carries prose, source, fact, proof, registry, or new policy material.
- Use the existing Stage 03 parser/admission/renderer and Stage 04 transcript/archive/replay seams rather than fabricating a Round-2 Candidate or accepting unvalidated model rounds.
- Preserve archive-v2's JSON-object task representation. Changing it to text would fail existing `Stage04ArchiveV2Test` and break `CandidateValidationService`'s canonical no-Provider replay.
- The production implementation retains all existing Stage 03 parser/admission/renderer semantics. The only task change is an additive, canonical `improvementOverlay`; replay reuses the exact same generator execute path and compares the full archived task bytes.

## Blockers

- None.

## Exact next action

- Complete.

## Resume checks

- Verify only the listed Stage 03/04 production files plus this progress file are changed by this vertical.
