# Progress: Business Flow carrier-consumer implementation

- Status: PAUSED
- Agent role: Terra/xhigh Step 06 public-carrier consumer GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Read-only preparation for the published Step 06 public-carrier cutover. The future GREEN is limited to exact public Capsule v5/Gap v2 validation plus the mechanical removal of program-only provenance fields before local R0/R1/R2 task bytes and hashes.
- Approved inputs: Published Step 06 §6.1 paragraph 203, current `RegistryProposalTaskCompiler` and `FiniteKeyFlowTaskCompiler` validation/projection code, and future frozen Luna public R0/R1R2 REDs.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this tracked preparation checkpoint before any future production edit.
- Recorded the hard boundary: no production edit, Maven command, test/fixture migration, provider behavior, schema/interface/task-identity change, fallback, or new Step 06 capability is authorized during preparation.
- Read Step 06 paragraph 203 and the current public publisher and consumer implementations. The producer already emits only Capsule v5 and Gap v2, with `originFactArtifactRef` on each Fact and `originKind` plus required-nullable `originGapLedgerRef` and typed `evidenceRefs` on each Gap.
- Located the only future task-byte seams: `RegistryProposalTaskCompiler.view()` currently returns an unmodified deep copy for R0, and `FiniteKeyFlowTaskCompiler.input()` currently installs an unmodified deep copy for R1/R2. The current descriptor gates remain Capsule v4 in both consumers and Gap v1 in the Registry consumer.
- Confirmed the minimal future validation boundary: validate the newly required Fact reference as an exact ArtifactReference and validate Gap provenance (textual origin kind, required-nullable exact ArtifactReference ledger reference, ordered unique typed artifact references) before removing precisely those program-only fields from a deep-copied task view. Existing closure/content validation remains intact.
- Implemented the bounded consumer cutover: Registry now requires exact Capsule v5/Gap v2 and validates the new source fields while reopening; its R0 view deletes only the three program-only provenance fields. Finite now requires exact Capsule v5 and validates then deletes the same fields from each R1/R2 view before canonical input bytes and hashes are constructed.

## Current state

- Luna has frozen the public R0/R1R2 carriers and released Maven. The established RED is 2 tests / 1 failure / 1 error / 0 skipped, exit 1: both cases stop at the stale v4/v1 descriptor gates before the model-view byte and SHA assertions can run.
- Production GREEN is now authorized only for the exact Capsule v5/Gap v2 gates and validated task-view projection. Existing dirty changes in the two production files predate this slice, including the separately owned registry/current-Flow lineage P1; they will be preserved without alteration.
- The minimal production change, selected two-file formatting, and assigned combined selector are complete. The carrier-relevant public task assertions reached and passed, but the aggregate selector is not GREEN because two pre-existing, out-of-slice assertions failed; no further production change is authorized.
- A pre-existing registry/current-Flow lineage P1 is separately owned by the parent and must not be mixed into the carrier cutover.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompiler.java` (exact v5/v2 gates, source-shape validation, R0 task view)
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java` (exact v5 gate and R1/R2 task view)
- `progress/business-flow-carrier-consumer-implementation.md` (owned evidence and pause state)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped `git status --short` | Read-only baseline captured | Shared worktree is extensively dirty with other owners’ changes; they will be preserved. |
| Frozen public R0/R1R2 selector (Luna) | RED; numeric exit 1 | 2 tests, 1 failure, 1 error, 0 skipped: both stopped at the stale Capsule v4/Gap v1 descriptor gates before model-view assertions. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute Registry,Finite paths> spotless:apply` | PASS; numeric exit 0 | Exactly 2 production files selected; 0 changed, 2 already clean, 0 skipped. |
| Same absolute two-file `spotless:check` | PASS; numeric exit 0 | Exactly 2 production files clean; 0 needed changes, 2 cache-skipped. |
| Assigned seven-class combined selector after formatting | PAUSED; numeric exit 1 | 23 tests, 2 failures, 0 errors, 0 skipped. Carrier results: Registry 4/0/0/0; Finite 3/1/0/0 (its two non-zero-flow task cases pass); Capsule publisher 4/0/0/0; provenance 6/0/0/0; bounded publication 1/0/0/0; coverage 1/0/0/0. Out-of-slice failures: Finite zero-flow test still expects two raw public `ENTRY` gaps at line 343 although published normalization emits `FLOW`; BusinessFlows spec line 122 expects a public Gap outside the source ledger, but the actual two public Fact gaps both exactly map to current source-ledger rows. |
| Scoped `git diff --check` | PASS; numeric exit 0 | No whitespace errors in the two production files or this progress record. |

## Decisions

- The future reader cutover accepts only public `business-flows-evidence-capsule-v5` and normalized `business-flows-flow-gap-v2`; it must not dual-read or reinterpret v4/v1.
- Before canonical R0/R1/R2 task input bytes and request hashes, delete only each Fact `originFactArtifactRef` and each Gap `originKind`/`originGapLedgerRef`. Retain typed `evidenceRefs` plus all existing Fact, Gap, Outcome, span, obligation, basis, path, closure, and budget content.
- Restrict future production edits to the existing `RegistryProposalTaskCompiler` constants and `view()` projection plus `FiniteKeyFlowTaskCompiler` Capsule constant and `input()` deep-copy projection. Do not introduce a framework, new public interface, provider change, fallback, or task identity rule.

## Blockers

- Root disposition is required for the two aggregate-only, pre-existing expectation failures. Do not alter the zero-flow raw-scope oracle, source-ledger/public-Gap oracle, or any production behavior in this carrier-consumer slice.

## Exact next action

- Release the Maven lease and await root's bounded disposition of the two out-of-slice aggregate failures.

## Resume checks

- Maven lease is released after the one assigned selector. Full Step 05 and Step 06 remain unaccepted; this carrier-consumer slice cannot be marked complete until the aggregate-only failures are dispositioned.
