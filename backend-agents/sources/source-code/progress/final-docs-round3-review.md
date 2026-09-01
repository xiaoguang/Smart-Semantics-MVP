# Progress: final docs round 3 review

- Status: COMPLETE
- Agent role: Independent target-design contract reviewer
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01T05:22:07Z
- Last updated: 2026-09-01T06:37:00Z
- Scope: Read-only Round-3 review of target DESIGN and Stage 06/08 contracts; this progress file is the only writable path.
- Approved inputs: Applicable AGENTS files, docs/DESIGN.md, docs/stages/06-interpret-one-flow-at-a-time.md, docs/stages/08-build-nine-section-document-and-archive.md, and read-only mechanical searches.
- Current branch/worktree: codex/github-code-target-implementation at /private/tmp/linguan-github-code-target-implementation

## Completed

- Read the repository, prototype/backend and GitHub Code Agent scoped instructions.
- Confirmed the shared worktree contains unrelated in-progress design, source and test changes that must be preserved.
- Read `docs/DESIGN.md`, `docs/stages/06-interpret-one-flow-at-a-time.md` and `docs/stages/08-build-nine-section-document-and-archive.md` completely.
- Re-read the current `RoundSlotOutcomeV1` and `ArtifactView` records after their concurrent precision repairs; the review below is against the final SHAs recorded in Verification.
- Reconciled the `E + 2R` planned-slot lifecycle, R1 non-success dependency skip, nullable fields, attempt reservation, upstream reference, fold and recovery rules across all three documents.
- Reconciled Stage08 M1's one named Stage06 registry byte preimage with its explicit no-reopen rule for Stage01–05 and the remaining Stage06 bytes.
- Mechanically recounted the eight public stage output tables as `4 + 5 + 8 + 5 + 6 + 10 + 6 + 8 = 52`.
- Checked the public `ArtifactView` record, stable-code declarations, repository-document cardinality, raw Trace exposure policy, and receipt/accounting ownership.

## Current state

- Review complete with no P0, one P1 and one P2. No design, source, test, POM or other progress file was edited by this reviewer.

### Findings

- **P1 — Stage06's public handoff cannot replay the promised planned-slot/terminal-outcome equation.** `docs/stages/06-interpret-one-flow-at-a-time.md:61-63` says the ten reader-visible files themselves preserve full slot/lifecycle accounting and that every one of the `E + 2R` planned slots is matched to one durable terminal outcome. The exact public records in the same document contain tasks, started rounds/receipts and final Flow dispositions, but none contains planned slot IDs, `RoundSlotLedgerReference`, or `RoundSlotOutcomeReference`; notably `RegistryProposalDisposition`, `GenerationReceipt` and `FlowInterpretationDisposition` at lines 300-350 omit them. The only exact holders are the private M2/M5 module payloads' `roundSlotLedgerRef` and `consumedOutcomeRefs[]` at lines 229 and 232. This contradicts both the stated Stage07 ten-file pure-replay guarantee and DESIGN line 172, which forbids moving denominators/equations into the stage receipt. The non-started R2 dependency-skip case makes the loss concrete: the public round set has no R2 round, while its public final disposition has no typed link to the skipped R2 outcome or the same-Flow R1 `upstreamTerminalOutcomeRef`. Minimum repair: put the sorted planned-slot IDs and exact terminal outcome references (or a typed immutable accounting reference whose bytes are one of the ten public semantic payloads) in a named Stage06 semantic accounting payload, then carry those sets into `RepositoryCoverageLedgerDraftV2`; do not put the equation in `stage-receipt.json`.
- **P2 — DESIGN's stable runtime/08 code family omits one code that DESIGN itself emits.** `docs/DESIGN.md:1604` specifies `OBSERVATION_BUDGET_EXCEEDED` as the exact TraceView overflow result, while the runtime/08 family at line 2058 does not list it. Stage08's local stable-code list at line 805 does include it. Add it to DESIGN §13.7 so implementers do not have to choose between the public behavior and the supposedly stable family.

### Clean checks

- The lifecycle state machine itself is coherent: R1 `GAP | FAILED | PRESTART_EXHAUSTED` preserves the planned R2 task, records attempt 1, performs zero R2 preflight/start/retry, and terminates it as `SKIPPED_UPSTREAM_TERMINAL`; only that outcome has a non-null typed `RoundSlotOutcomeReference` to the same-Flow R1 outcome. Nullability, runtime fields, canonical response fields, retry ceiling, fold states and crash recovery agree between DESIGN and Stage08.
- Stage08 M1 now names the Stage06 `repository-interpretation-registry` as its seventh direct byte preimage and excludes only Stage01–05 plus all other Stage06 bytes; the external validator retains full Stage01–06 reopening responsibility. This is coherent rather than a hidden exception.
- `ArtifactView` is byte-for-byte type-aligned for the reviewed fields: both DESIGN and Stage08 close `mediaType` to JSON/JSONL/Markdown and `validationState` to `MANIFEST_VERIFIED | FULLY_VALIDATED`.
- The eight stage output tables still total 52, and Stage08 still produces exactly one repository-level `NineSectionPlan` and `document.md`, never per-Flow Markdown.
- Raw Trace/source/prompt/model-response artifacts remain `METADATA_ONLY`; only fresh-validated `TraceView` can expose path-free source hops, and `COMPLETE_UTF8` fails with `ARTIFACT_CONTENT_NOT_PUBLIC` for those raw artifacts.
- Receipts remain integrity/provenance binders; denominators, partitions and conservation equations remain assigned to named semantic coverage/accounting payloads. The P1 is a missing Stage06 semantic carrier, not a recommendation to weaken that separation.

## Changed files

- progress/final-docs-round3-review.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared changes enumerated before this review; reviewer owns no pre-existing path. |
| `find ... -name AGENTS.md` plus bounded `sed` reads | PASS | Root, prototype/backend and scoped instructions read; no production or design file modified. |
| Full bounded reads with `sed`/`wc -l` | PASS | DESIGN 2,187 lines, Stage06 425 lines and Stage08 851 lines read to EOF. |
| `shasum -a 256` on reviewed documents | PASS | DESIGN `2ec7ed9e...15a21d`; Stage06 `87a19220...c3cfb`; Stage08 `1856fa51...e0e7` after the ArtifactView repair. |
| Stage output-table recount | PASS | Stage01–08 counts: `4, 5, 8, 5, 6, 10, 6, 8`; total 52. |
| Targeted `rg`/record comparison | PASS | R2 dependency-skip fields and fold/recovery agree; M1 registry exception is explicit; ArtifactView parity is closed; one missing DESIGN code found. |
| Maven/network/source capture/Provider | NOT RUN | Explicitly prohibited for this read-only documentation review. |

## Decisions

- Apply the deep-module review vocabulary to test whether Stage 06/08 interfaces hide implementation details while preserving complete, fail-closed handoffs.
- Classify findings as P0/P1/P2 only when supported by exact current document text; otherwise report CLEAN.
- Treat private M2/M5 module lifecycle references as valid recovery inputs but not as a substitute for the explicitly promised ten-file Stage06 semantic handoff or repository coverage accounting.
- Do not report the transient ArtifactView mismatch: the current Stage08 record now explicitly matches DESIGN.

## Blockers

- None.

## Exact next action

- Design Authority should repair the P1 semantic accounting carrier and the P2 DESIGN code list, then re-run this bounded cross-document review before freezing the documentation baseline.

## Resume checks

- Re-run `git status --short` and confirm only this reviewer writes progress/final-docs-round3-review.md.
- Re-read any document whose SHA differs from the Verification row before relying on this verdict.
- Confirm a P1 repair does not add an eleventh Stage06 public file: revise one of the ten named semantic payload contracts and preserve the 52-output total.
