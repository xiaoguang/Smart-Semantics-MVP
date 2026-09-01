# Progress: Stage 04 identity debugging

- Status: COMPLETE
- Agent role: Stage 04 systematic debugging and minimal production repair
- Model: gpt-5.6-sol / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Reproduce and root-cause the `Stage04PublicCoreTest` fresh-process idempotence failure; compare snapshot-root-dependent canonical artifacts and Candidate identity projection; implement only the minimal production fix while preserving tamper closure; run the requested focused regressions.
- Approved inputs: scoped `AGENTS.md`; `superpowers:systematic-debugging`, `superpowers:test-driven-development`, and `superpowers:verification-before-completion`; existing failing test and production implementation; `progress/stage04-residual-p1-core.md`.
- Current branch/worktree: `codex/github-code-design-walkthrough`; shared dirty worktree with pre-existing and parallel changes that must be preserved.

## Completed

- Read the repository, prototype, backend-agent, and GitHub-code scoped instructions.
- Read the debugging, TDD, verification, and root-cause-tracing instructions.
- Confirmed the owned progress path was unused and created this record before implementation edits.
- Read the residual P1 handoff: candidate content now commits model-round and generation-receipt roots, while archive validation independently recomputes the detailed lineage formulas.
- Read the failing public-core method. Its final relocated-source assertion requires equal `candidateContentId` across byte-identical snapshots at different roots and registrations, while allowing different series IDs and fresh lifecycle attempts.
- Read the Candidate assembly identity path. The current content formula hashes the full archived `model-rounds.jsonl` and `generation-receipts.jsonl`; a separate `modelRoundContent` projection exists but is not consumed by that formula.
- Reproduced the exact RED selector: 1 test, 1 assertion failure at `Stage04PublicCoreTest:104`; original `candidate-content:631cc3...` differs from relocated `candidate-content:79bd43...`.
- Compared the two installed archives. `source-input.json`, `registry-bundle.json`, and `trace.jsonl` are byte-identical. The first `model-rounds.jsonl` difference is only `generationReceiptId`; the receipt files differ only in `generationReceiptId` and `startedEventId`. Removing those local-link fields makes both projections byte-identical.
- Traced the data flow: the intentionally independent registration changes `canonicalRequestId -> seriesId -> roundSlotId`; `RoundSlotEvent.materialized` hashes those local series fields into `startedEventId`; `generationReceiptId` hashes that event ID; `model-rounds.jsonl` copies the generation receipt ID; the current Candidate formula hashes both complete JSONL roots. Thus series-local audit lineage enters `candidateContentId` twice even though all task, canonical/semantic response, expected/observed runtime, upstream started receipt, source, registry, document, and Trace content are identical.
- Compared design responsibilities. Stage 04 §6 names content, lineage, and runtime location as separate identities; §10.2/§10.3 separates task/response replay preimages from runtime-occurrence receipts, and `modelRoundId` precedes `generationReceiptId` specifically to avoid an identity cycle. The later §6 full-root formula conflicts with that separation and with the already accepted relocated-root public contract by importing a series-derived event into content identity.
- Confirmed the tamper boundary can stay closed without treating local audit identity as product content: the immutable Candidate still retains the complete `model-rounds.jsonl` and `generation-receipts.jsonl`; `candidate.json` records both exact archive roots; the manifest binds their bytes; fresh validation recomputes response/model/receipt IDs and cross-fields in `exactRoundClosure`, while deterministic replay consumes the task/response preimage with zero Provider calls.

## Current state

- Complete. The focused regression and requested four-class combined selector are GREEN; full audit roots remain location-specific while Candidate content is rootless; whitespace validation is clean.
- Generative-model/product-content inventory: none; this debugging task uses only frozen scripted tests and local deterministic artifacts.

## Changed files

- `progress/stage04-identity-debug.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `docs/stages/04-runtime-archive-trace-recovery.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04PublicCoreTest#publicAgentRunsOneCorePathInstallsValidatesAndTracesThenFreshProcessIsIdempotent test` | RED (expected) | 1 test, 1 failure at line 104; relocated Candidate content identity differs. |
| canonical SHA/diff of both archives | ROOT CAUSE EVIDENCE | Source/registry/Trace roots equal; model roots differ only by `generationReceiptId`; receipt roots differ only by `generationReceiptId` and `startedEventId`; stable projections compare byte-identical. |
| `mvn -Dtest=Stage04PublicCoreTest#publicAgentRunsOneCorePathInstallsValidatesAndTracesThenFreshProcessIsIdempotent test` | PASS | 1 test, 0 failures, 0 errors; focused RED is GREEN after the single projection change. |
| `mvn -Dtest=Stage04ResidualP1Test,Stage04ValidationHardeningTest,Stage04PublicCoreTest,Stage04ArchiveV2Test test` | PASS | 36 tests, 0 failures, 0 errors, 0 skipped; coherent receipt/runtime tamper, archive-v2 replay, public core, and hardening checks remain closed. |
| post-fix archive identity comparison | PASS | Original and relocated archives have identical `candidateContentId` but different `seriesId`, `candidateId`, `modelRoundsRoot`, and `generationReceiptsRoot`, proving content/audit separation without erasing audit bytes. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Do not change tests or assertions. The named failing public-core method is the existing RED regression test.
- Do not propose or implement a fix until the first root-dependent canonical difference is identified and traced into the Candidate identity formula.
- Treat the unused `modelRoundContent` projection only as evidence to investigate, not as a presumed repair.
- The absolute `snapshotRoot` is already absent from Candidate artifacts; the leaking value is the series-derived persisted started-event ID, an execution/audit identity whose cause is the separate registration/series required by the test.
- Content identity will bind the existing canonical model-content projection (full task/schema, canonical response, canonical and semantic response digests, expected/observed runtime, and upstream started receipt) rather than the archived record's series-local `generationReceiptId`.
- Full archived model/receipt roots remain in `candidate.json` and the archive manifest. Their formula/cross-link validation remains mandatory and independent of `candidateContentId`; no receipt, event, runtime, response, or test assertion will be removed.
- Single hypothesis: the sole regression is that the residual P1 change replaced the stable model-content root with full model/receipt byte roots. Reverting only that identity projection, and making fresh validation recompute the same stable projection from archived records, will make the relocated-root assertion green while the receipt-tamper regressions remain red-to-validation/GREEN-as-tests.
- Implemented only that hypothesis: assembly and fresh validation now share the canonical `candidate-model-round-content-v1` projection; full audit JSONL files, sidecar roots, manifest entries, formula checks, deterministic replay, and assertions are unchanged.

## Blockers

- None.

## Exact next action

- None; return the root cause, minimal repair, and fresh verification evidence to the parent task.

## Resume checks

- Re-read this record and `git status --short`; verify no parallel changes overlap any file before editing production code.
