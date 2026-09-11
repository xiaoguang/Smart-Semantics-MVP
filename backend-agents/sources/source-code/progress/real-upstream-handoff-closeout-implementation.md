# Progress: Real upstream handoff closeout implementation

- Status: COMPLETE
- Agent role: Terra/xhigh bounded existing-contract GREEN owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Restore two frozen real-upstream handoffs only: scope-derived CALL graph closure and Flow-reader acceptance of exact real ApplicationDiscovery JSONL line headers.
- Approved inputs: Frozen direct REDs; completed `fact-reader-graph-gap-handoff-diagnosis.md`; current published Step 02–05 contracts; real ApplicationDiscovery/ProgramGraphs producers; existing Flow reader; and the frozen completed fixture migration.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve all unrelated shared-worktree changes.

## Completed

- Read the source-scoped instructions, both implementation plans, the progress template, the completed graph-gap diagnosis, and the prior blocked discovery-to-Fact checkpoint.
- Confirmed the first frozen real handoff RED attributes CALL `coverage.closed=false` to its writer rather than Fact-reader permissiveness: published Step 03 requires `closed == scopeGapIds.isEmpty()`.
- Confirmed the second frozen RED attributes Flow-reader rejection to fabricated plural JSONL descriptor headers; the real ApplicationDiscovery writer emits singular entry/catalog record schemas with no per-line `artifactType`.
- Created this owner-only progress file before any production edit.
- Restored CALL closure to the copied scope-gap denominator only; its real discovery-to-Fact handoff selector is GREEN without changing local Gap, candidate, edge, owner, or identity construction.
- Restored Flow-reader acceptance of the real singular entry JSONL record schema without adding a legacy/dual reader or loosening descriptor-level payload validation.

## Current state

- Hypothesis A: replace only CALL's closure predicate with the existing copied scope-gap-set emptiness rule, preserving all graph candidates, local Gaps, exact edges, owners, and IDs.
- Hypothesis B: make `PersistedFlowCompilationInputReader` validate the exact real entry JSONL line-header schema, without compatibility aliases or a dual-reader path.
- This bounded real-upstream handoff slice is complete. CALL closure is derived exclusively from copied `scopeGapIds`, and the Flow reader accepts only the real singular entry JSONL record schema while still rejecting per-line descriptor metadata.
- The post-format direct bundle is GREEN (12 tests). Selected absolute Spotless checked the three owned production files; no other source, test, fixture, schema, design, provenance, Provider, or flow-algorithm change was made in this slice.

## Changed files

- `progress/real-upstream-handoff-closeout-implementation.md` (owned; created before production edits)
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java` (authorized; CALL closure predicate only)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java` (authorized; real entry JSONL line-header validation only)
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java` (prior owned bounded handoff change preserved and selected for the required final formatter/check; no new semantic change in this slice)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen `DiscoveryToFactHandoffTest#handsRealDiscoveryAndGraphPublicationsToTheFactInputReader` evidence | RED; numeric exit 1 | 1 test, 1 failure, 0 errors, 0 skipped; Fact reader rejects CALL `coverage.closed=false`, whose producer uses the wrong local-Gap predicate. |
| Frozen `EntryRootedFlowCompilerTest` evidence | RED; numeric exit 1 | 7 assertion failures at `PersistedFlowCompilationInputReader.requireJsonLineHeader` via `parseDiscovery`; real JSONL line schema is singular and has no `artifactType`. |
| `DiscoveryToFactHandoffTest#handsRealDiscoveryAndGraphPublicationsToTheFactInputReader` after CALL correction | PASS; numeric exit 0 | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`; no CONTROL_FLOW or DATA_FLOW follow-on failure occurred. |
| `EntryRootedFlowCompilerTest` after Flow-reader line-header correction | PASS; numeric exit 0 | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`; no further real-shape mismatch surfaced. |
| `DiscoveryToFactHandoffTest,FactCandidateEnumeratorTest,EntryRootedFlowCompilerTest` before formatting | PASS; numeric exit 0 | `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`. |
| Absolute selected `spotless:apply` | PASS; numeric exit 0 | 3 owned production files selected; 1 reformatted, 2 already clean. |
| Absolute selected `spotless:check` | PASS; numeric exit 0 | 3 owned production files selected; 0 pending changes. |
| `DiscoveryToFactHandoffTest,FactCandidateEnumeratorTest,EntryRootedFlowCompilerTest` post-format | PASS; numeric exit 0 | `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`. |
| Scoped `git diff --check` | PASS; numeric exit 0 | No whitespace errors in the three selected production files or this progress file. |
| Production selectors and Spotless | PENDING | Run serially under the sole Maven lease after the corresponding minimal change. |

## Decisions

- Do not change Fact-reader closure validation, discovery/graph producers other than the named CALL predicate, test fixtures, schemas, provenance, flow algorithms, or Step 06 behavior.
- A local graph Gap is a complete disposition and must not determine repository-scope closure; missing or malformed scope/accounting data remains fail-closed.
- The real singular JSONL line header replaces the fabricated descriptor-level expectation; do not add a dual or legacy reader path. Existing field validation remains otherwise unchanged.
- The documented identical CONTROL_FLOW/DATA_FLOW predicate follow-on did not occur after the CALL correction, so those builders were not changed.

## Blockers

- None for this bounded slice. Full Step 05 remains independently unaccepted and retains its separate closure, provenance, replay, source, and domain gates.

## Exact next action

- Release the Maven lease to root/Luna for the separately scoped provenance RED; do not extend this completed handoff slice.

## Resume checks

- The shared worktree remains intentionally dirty. This slice touched only its progress file, CALL closure predicate, and Flow entry-line header; the prior owned Fact reader remains preserved.
- Maven lease released. No network, Provider, customer Maven/capture, test/fixture/design/schema edits, commit, push, or subagents were used.
- Completion applies only to this bounded real-upstream handoff slice; it does not accept full Step 05.
