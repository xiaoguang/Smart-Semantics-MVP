# Progress: Bounded Fact input handoff implementation

- Status: COMPLETE
- Agent role: Terra/xhigh bounded existing-contract GREEN owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One strict Step 03-to-Step 04 bounded ProgramGraphs handoff slice in `PersistedFactCandidateInputReader`: admit only the published shared scope-Gap set and graph-index projection.
- Approved inputs: Frozen `BoundedFactInputHandoffTest` RED; completed `bounded-flow-closure-handoff-diagnosis.md`; published Step 03/04 contract; existing real reader and prior real-wire changes.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the applicable repository instructions, frozen RED evidence, and completed bounded-flow closure diagnosis before production edits.
- Confirmed the parent-provided RED: numeric exit 1; 1 test, 0 failures, 1 error, with all source/graph/index/receipt prerequisites passing before `PROOF_PACK_REFERENCE_BROKEN` at `validateCoverage:1182`.
- Created this owner-only progress file before production edits.
- Implemented the strict bounded reader validation: each M1–M4 public coverage now validates its canonical disjoint denominator and `closed == S.isEmpty()`; all four must share the same canonical `S`; index coverage must exactly project all five graph coverages; and index Gap IDs/status must equal local `G union S`.
- Confirmed through the parent that the concurrent `BusinessFlowProvenanceTest.java` edit is Java-frozen; the sole Maven lease remains with this slice.

## Current state

- The bounded Fact-input handoff slice is complete. Evidence coverage and graph-index structural closure remain strict; valid bounded M1–M4 scope coverage now carries canonical `S` and `G union S` without accepting arbitrary false values.

## Changed files

- `progress/bounded-fact-input-handoff-implementation.md` (owned; created before production edits)
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java` (owned; bounded graph coverage/index validation, preserving earlier real-wire fixes)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen `BoundedFactInputHandoffTest` evidence | RED; numeric exit 1 | 1 test, 0 failures, 1 error; all source/graph/index/receipt prerequisites pass, then valid bounded coverage reaches `PROOF_PACK_REFERENCE_BROKEN` at `validateCoverage:1182`. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java spotless:apply` | PASS; numeric exit 0 | Selected exactly 1 Java file; 1 changed to clean, 0 already clean, 0 skipped. |
| Same selected-file `spotless:check` | PASS; numeric exit 0 | Selected exactly 1 Java file; 0 need changes, 1 cache-skipped clean. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BoundedFactInputHandoffTest,DiscoveryToFactHandoffTest,FactCandidateEnumeratorTest test` | PASS; numeric exit 0 | 6 tests, 0 failures, 0 errors, 0 skipped: bounded handoff 1, discovery handoff 1, Fact candidate enumeration 4. |
| Scoped `git diff --check` | PASS; numeric exit 0 | No whitespace errors in this reader and owner progress file. |

## Decisions

- Validate only the existing shared canonical scope-Gap set `S` across code structure, call, control-flow, and data-flow graph coverage; never accept arbitrary `closed=false`.
- Preserve structural EvidenceGraph and graph-index closure requirements, all existing disjoint accounting/reference checks, and local graph Gap rows `G` as the only rows in `graph-gaps.jsonl`.
- Require `index.gapIds == sortedDistinct(G union S)` and validate the published index coverage projection. Do not add scope Gaps to a Fact ledger or widen to Flow/M3/version/design behavior.

## Blockers

- None for this bounded Fact-reader slice. The Maven lease is released after the recorded post-format GREEN bundle.

## Exact next action

- Release the Maven lease to the parent so the separately owned provenance selector can run. Do not expand into Flow M3 or full Step 05.

## Resume checks

- The shared worktree is intentionally dirty. Only this progress file and `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java` are authorized for this slice.
- The sole Maven lease is released. No test/fixture/design/schema/source changes, network, Provider, source capture, commit, push, or subagents have been used by this owner.
- Completing this Fact reader bounded-handoff slice does not accept Flow M3, full Step 05, or any new schema/Provider behavior.
