# Progress: Stage 04 final acceptance review

- Status: COMPLETE
- Agent role: final independent Stage 04 acceptance reviewer (read-only)
- Model: gpt-5.6-sol / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Review all current Stage 04 production and tests against `docs/stages/04-runtime-archive-trace-recovery.md`, `DESIGN.md`, and the four P1 groups in `progress/final-implementation-review.md`. Verify durable ledger/receipt closure, fatal-addendum fail-closed behavior, exact typed Trace provenance for all kinds, bounded source/directory admission, lifecycle/recovery, Candidate identity, and Round 2. Report P0/P1/P2 findings with exact file/line guidance; accept only with no P0/P1.
- Approved inputs: Current local Stage 04 production/tests/documentation/progress records; completed evidence supplied by root (`Stage04 104/104`, `Stage03 69/69`, `Stage01/02 79/79`). At most one narrow Stage 04 selector if essential and if concurrency permits. No production/test/design edits, network, source capture/refresh, live model, product-content generation, candidate generation/freeze/package, deployment, commit, or push. Generative product-content inventory: none.
- Current branch/worktree: `codex/github-code-design-walkthrough` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/github-code`

## Completed

- Re-read the applicable repository review workflow and captured the pre-review working-tree state.
- Created this owned progress record before reviewing implementation state.
- Read the complete Stage 04 contract, the applicable `DESIGN.md` M8/runtime-governance sections, and `progress/final-implementation-review.md`.
- Reviewed residual P1 group 1 (durable ledger and receipt closure).
- Reviewed residual P1 group 2 (typed Trace provenance).
- Reviewed residual P1 group 3 (fatal corrective-addendum boundary).
- Reviewed residual P1 group 4 (bounded source/directory admission).
- Reviewed Candidate identity and Round-2/lifecycle seams for regressions.
- Finalized the independent acceptance disposition without changing production, tests, or design documentation.

## Current state

- **Final disposition: REJECT Stage 04 acceptance.** P0: 0. P1: 4. P2: 1. Acceptance requires no P0/P1.

- Residual P1 group 1 — **PARTIAL / P1 remains**.
  - CLOSED: every archived generation receipt must resolve its exact durable `THREAD_STARTED` event (`CandidateValidationTrace.java:186-206`), and recovery rejects any non-identical/missing start set without synthesizing ledger history (`CandidateSeriesLedger.java:142-199`, `207-255`).
  - OPEN P1: `exactRoundClosure` checks the archived model's duplicate `flowSliceId`, `evidenceCapsuleId`, and `round` only against the receipt copies (`CandidateValidationTrace.java:640-646`), while `modelRoundId` is recomputed from the nested `task` (`:650-675`). It never requires those three model/receipt values to equal `task.flowSliceId`, `task.evidenceCapsuleId`, and `task.flowInterpretationRound`. Because `generationReceiptId` does not bind those fields, a coherently rewritten model+receipt tuple can retain the unchanged Stage 03 task/model identity while claiming a different flow/evidence/round. Minimal correction: compare all three duplicate fields to the typed nested task before accepting the model/receipt pair, and add one adversarial test per field.
- Residual P1 group 2 — **PARTIAL / P1 remains**.
  - CLOSED: runtime `TraceView` resolution now validates and exposes term registry identity/priority (`CandidateValidationTrace.java:897-927`), Flow fallback policy/template/task spec/resolution order and proven basis (`:966-1007`), empty-section accounting (`:1009-1022`), exact-but-optional Stage 01 question-registry refs (`:1059-1101`), and constructible InterpretationGap/FlowGap provenance (`:1025-1057`, `1104-1180`). `Stage04TraceReferenceTest.java:48-170` exercises those formerly missing paths; `Stage04TypedTraceTest.java:65-103,137-275` covers all five kinds and exact source spans.
  - OPEN P1: the persisted typed Trace contract itself is still not implemented. `CandidateAssembler.traceLines` archives only `basis*Ids`, `readerItemKey`, `referencedItemKeys`, and an inferred `traceKind` (`CandidateAssembler.java:398-409`). It omits the required per-kind exact refs and omits `fallbackSubtype`, contrary to the explicit archive contract (`docs/stages/04-runtime-archive-trace-recovery.md:596-604`). The resolver reconstructs those refs from other artifacts at query time, and `Stage04TraceReferenceTest.java:52-61,78-91,116-170` likewise discovers them outside the trace record, so the green tests do not prove the required self-describing typed record. Minimal correction: make the assembler persist an explicit kind-specific trace record (including fallback subtype and exact registry/task/proof/gap refs), validate/replay those fields exactly before `TraceView`, and add record-level omission/substitution tests for each kind.
- Residual P1 group 3 — **CLOSED**.
  - The bounded-v0 filesystem boundary rejects both `record` and `resolveExact` unconditionally (`FilesystemCorrectiveAddendumStore.java:5-29`), and the agent rejects every fatal/addendum-required finding before Round-2 Provider work irrespective of an injected store (`DefaultCodeToMarkdownAgent.java:266-291`). `Stage04LedgerAddendumHardeningTest.java:115-166` verifies both fatal fail-closed and unaffected warning Round 2; `Stage04Round2AddendumTest.java:183-220` independently covers the filesystem capability boundary.
  - P2 (non-blocking): dormant package-local factories still manufacture the formerly invalid self-attested receipt (`CorrectiveAddendum.java:61-71`; `CorrectiveAddendumDiagnosisReceipt.java:45-52`). They are unreachable from the active bounded-v0 path, but should be deleted or replaced only when a genuinely external attestation workflow exists, to prevent accidental reactivation.
- Residual P1 group 4 — **OPEN / P1 remains**.
  - CLOSED portion: Candidate artifact directories stop at 256 entries before collecting the excess entry and enforce aggregate/per-file byte budgets before reads (`CandidateValidationTrace.java:1911-1973`).
  - OPEN P1: Trace source reopen still executes `Files.readAllBytes(source)` before checking the verified size/hash (`CandidateValidationTrace.java:1490-1523`). Source registration enumeration materializes the complete directory before validation (`FilesystemSourceRegistry.java:145-162`); recovery candidate lookup likewise sorts/collects the entire `candidates/` directory (`DefaultCodeToMarkdownAgent.java:376-411`); series enumeration and ledger event replay collect complete directories before validation (`CandidateSeriesLedger.java:448-472`, `532-579`). These are attacker-controlled/untrusted filesystem inputs, so stable post-allocation rejection is not bounded admission.
  - Test gap: `Stage04ReopenAndDirectoryBoundsTest.java:41-89` grows a source by only one byte, and `:91-138` asserts stable failure at 257 entries but cannot demonstrate that `entries.toList()` did not allocate them first. Minimal correction: use a common no-follow source reader capped by the verified size and configured source maximum; iterate every untrusted directory with an explicit count budget and abort before appending/collecting the limit+1 entry. Add sparse/oversized-source and allocation-sentinel/large-cardinality probes.
- Candidate identity — **CLOSED / no regression found**. Validation recomputes content identity from the exact document/plan/stage/source/registry/model-content/trace roots and recomputes Candidate ID from series plus canonical Round-1/Round-2 lineage (`CandidateValidationTrace.java:299-328`; `CandidateAssembler.java:713-742`; `CandidateSeriesLedger.java:61-83`). Addressed reads require the sidecar ID to equal the requested directory ID (`CandidateValidationTrace.java:2008-2028`). `Stage04IdentityLedgerTest.java:21-75` covers rootless stability and parent/finding/addendum lineage sensitivity; `Stage04ValidationHardeningTest.java:45-97` covers sidecar substitution and coherent semantic-root rewrite.
- Round 2 — **PARTIAL / one lifecycle P1 regression**.
  - CLOSED content semantics: findings resolve before reservation, overlays contain only exact finding refs, affected flows receive new R1/R2 calls, unaffected model rounds and receipts are reused, and completed/started recovery remains provider-free (`DefaultCodeToMarkdownAgent.java:139-205`, `301-345`, `413-471`). `Stage04Round2Test.java:55-142,179-217`, `Stage04LifecycleRound2HardeningTest.java:72-232`, and `Stage04Round2AddendumTest.java:49-140` cover idempotency, exact base-task overlay, unaffected-flow reuse, no-start retry, installed recovery, and terminal re-entry.
  - OPEN P1: after `LifecycleProviderBridge` has persisted one or more starts, an assembler/install/runtime failure falls through the outer catches, which call `failBeforeProvider(ledger, reserved)` using the stale initial `RESERVED` view (`DefaultCodeToMarkdownAgent.java:101-124`, `188-211`). `failBeforeProvider` then attempts `DETERMINISTIC_FATAL_BEFORE_PROVIDER` only from that stale view (`:525-533`); `CandidateSeriesLedger.foldPersisted` rejects it because persisted and supplied views differ (`CandidateSeriesLedger.java:357-368`), and the exception is swallowed. The durable slot therefore remains `STARTED_CONSUMED` instead of immediately becoming terminal after a post-start assembler/store failure. Minimal correction: retain the bridge/current slot across the try/catch and route any failure with a current `STARTED_CONSUMED` slot through `failAfterGeneration`; use `failBeforeProvider` only when no start was persisted. Add Round-1 and Round-2 tests that force install failure after successful model rounds and assert durable `FAILED_AFTER_STARTED`/stable re-entry without Provider calls.

## Changed files

- `progress/stage04-final-acceptance-review.md` (owned progress state only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Recorded the shared dirty worktree and extensive pre-existing untracked Stage 04 implementation/tests. |
| Root-supplied completed test evidence | PASS (evidence only) | Stage 04 `104/104`; Stage 03 `69/69`; Stage 01/02 `79/79`. |
| Maven in this review | NOT RUN | Source/test inspection was sufficient and root supplied fresh exclusive suite evidence. |
| Reviewer mutation audit | PASS | This reviewer changed only this owned progress file; no production, test, design, generation, source refresh, deployment, commit, or push action. |

## Decisions

- This is a current-state contract acceptance review, not a review since a supplied Git fixed point. The fixed-point/diff-only branch of the generic code-review workflow is therefore inapplicable; inspect the complete current Stage 04 surface and report standards/spec findings together by severity.
- Treat green suites as evidence only. Independently trace every named P1 contract through production and adversarial tests.
- Acceptance requires zero P0 and zero P1 findings. P2 findings do not block completion but must be reported exactly.

## Blockers

- None.

## Exact next action

- None. Review complete; production correction remains with the implementation owner.

## Resume checks

- Re-read this progress file and `git status --short`.
- Confirm no production, test, or design file was edited by this reviewer.
- Verify all conclusions against current line-numbered source, not only prior progress or test results.
