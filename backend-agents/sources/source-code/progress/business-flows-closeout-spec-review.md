# Progress: business-flows-closeout-spec-review

- Status: COMPLETE
- Agent role: Independent Step 05 SPEC-only reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Review the uncommitted Step05 closeout WIP against the exact Step04/05/06 contracts; write only this review progress file.
- Approved inputs: Fixed base `dea5c1bd96987270ecdc0f8060b612599b8f51d9`; supplied source diff; `docs/DESIGN.md`; analysis-step docs 04, 05, and 06; existing progress evidence. No Java/test/design/config edits, Maven, customer source, network/provider, commit, or push.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the applicable root, backend, and source-code `AGENTS.md` files, both implementation plans, the code-review skill, and the relevant Step04/05/06/design materials.
- Confirmed the fixed base resolves to the current `HEAD`; the review target is the uncommitted source WIP diff.

## Current state

- Bounded spec-only pass complete. The current WIP has three concrete contract gaps; no scope-creep finding and no new acceptance blocker was inferred.

## Findings

1. **P1 — M3 can publish false repository-closed coverage.** M1/M2-local closure is not repository closure: Step05 requires `discoveredEntries = compiled + gapped + excluded`, `allEntryShardIds = exactDisjointUnion(...) = discoveredEntries`, and additionally says public `repositoryFlowCoverage.closed` is true only when ApplicationDiscovery `repositoryEntryCoverage` is closed (05-business-flows.md:215, 480-499). A legal `BOUNDED_PATH_SET` predecessor may have a complete local entry denominator and let M1/M2 compile/project every local entry, while source scope remains `repositoryCompletionEligible=false`; 02-application-discovery.md:224 requires its `repositoryEntryCoverage.closed=false`. `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java:250-275` drops both discovery coverage and source-scope eligibility, and `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java:95-103` reopens discovery only for lineage, derives public coverage from local compiler dispositions (193-240), then hard-codes `closed=true` (392-431). Thus the defect is the M3 repo-closure gate, not M1's legitimate local denominator closure.

2. **P1 — Capsule views lose required provenance and rewrite Gap ownership.** `FlowGapView` does have a `scope` field; the defect is that the source Gap's typed evidence/origin is not retained. Step05 §8.1/§8.3 requires Fact `originFactArtifactRef` and Gap `scope`, typed `evidenceRefs[]`, `originGapLedgerRef`, and exact affected IDs (05-business-flows.md:314-323, 376-380, 501-509). Current Fact ledger records have no `scope`; their exact kinds are `EXTERNAL_EFFECT` and `FACT_REJECTION`, with `affectedEntryIds`, candidate keys, and `evidenceNodeIds` (04-proven-code-facts.md:246, 338-347; `src/main/java/org/sourceanalysis/app/analysis/fact/publish/FactLedgerPublicationSpecifier.java:679-730`). M1 rewrites every ledger Gap to `scope=FLOW` (`src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java:90-103`), and M2 repeats that projection (`EvidenceCapsuleProjector.java:157-167, 263-269`); `CapsuleProjection.java:119-162`/publisher `:281-306` omit the required Fact origin and Gap ledger/evidence-ref fields. This is a legal publisher-path loss of FACT/ATOM/OUTCOME ownership and upstream lineage.

3. **P1 — M3/Step06 lack semantic replay revalidation at the module boundary.** This is not ordinary byte tampering: M2's normal publisher fresh-reprojects and compares the full projection (`src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java:76-82`), and the store verifies content identity. The minimal bypass is an allowed low-level `CanonicalModuleArtifactStore.install(ModuleInstallRequest)` containing a self-consistent v6 envelope/body whose capsule Fact/Gap/Outcome views were changed and whose artifact/receipt hashes were recomputed; `AtomicCanonicalPublicationEngine.java:248-291` validates envelope identity, not the semantic body. M3 accepts that module reference after `requireModule` (`FlowPublicationSpecifier.java:104-125`) and compares only Flow/Capsule IDs and processJoinSignals (`223-295`), merging Gap IDs with `putIfAbsent` (260-268). Step05 §8.0/§8.3 instead requires M3 to rederive exact Flow↔Capsule Fact/Gap/Outcome sets (05-business-flows.md:177, 189, 191, 509); Step06's shape checks are not a substitute (`RegistryProposalTaskCompiler.java:197-283`).

## Changed files

- `progress/business-flows-closeout-spec-review.md` (this review only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing WIP changes preserved; no review-owned implementation edits. |
| Instruction/spec reads | PASS | Applicable AGENTS, plans, code-review skill, and Step04/05/06/design docs read. |

## Decisions

- Treat existing fixed-repository capture rejection, missing domain classifier input, and downstream NOT_RUN states as known acceptance blockers, not new regressions.
- Do not run Maven or customer/live-source/provider commands.

## Blockers

- None for the bounded static review.

## Exact next action

- Release this completed review to the parent; root owns fixes and decisions.

## Resume checks

- Re-run `git status --short`; only this progress file may be newly owned by this review.
