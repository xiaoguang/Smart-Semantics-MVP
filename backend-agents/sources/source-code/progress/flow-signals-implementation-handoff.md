# Progress: Flow signals implementation handoff

- Status: COMPLETE
- Agent role: Sol/ultra design authority for the bounded Step 05 process-signal implementation handoff
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08T18:55:44Z
- Last updated: 2026-09-08T20:03:52Z
- Scope: Ground the Step 05 `Fact`/atom-to-`processJoinSignals` contract in current persisted artifacts; define one exact public-seam RED and small continuation slices; clarify only necessary Step 05/adjacent consumer contracts; write the implementation handoff. No Java, tests, schemas, POM, Maven, customer scan/build, Provider call, Git publication, or architecture restart.
- Approved inputs: `/private/tmp/source-analysis-flow-signals-design-brief.md`; repository/scoped `AGENTS.md`; current Step 05 and relevant Step 06 design; both active implementation plans; named current Java and test sources; existing public stored-artifact fixtures; root-reported targeted baseline (`EntryRootedFlowCompilerTest`, `EvidenceCapsuleProjectorTest`, `BusinessFlowsPublicationSpecifierTest`: 8/8 PASS); `.superpowers/flow-signals-contract-review.md`. No generative product-content invocation is planned.
- Current branch/worktree: `codex/source-analysis-flow-signals-contract` at `e11cab54c1670ce5d073ed5ff1e090ae069ff061` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the bounded brief, all applicable instructions, and the progress template.
- Confirmed the exact branch/HEAD and preserved the pre-existing root-owned modification to `progress/continued-implementation-coordination.md`.
- Read Step 05 completely, the Step 06 signal/pair/counter/group/packet/identity consumers, both implementation plans, and all named current M1/M2/M3/store/local-reader/test/fixture sources.
- Audited the current Fact registry and persisted wire: two Fact kinds, nine atom names, full Proof closure, typed data-flow boundary, Evidence source locators, and the public gap-ledger fields actually available to M1.
- Froze the finite four-family extraction rule, explicit unsupported-family absence policy, acyclic identity order, two M1 signal budgets, first reflective public-seam RED, constructor-safe migration order, M2/M3 work, eleven-file schema cutover, and local R0/R1/R2 reader migration.
- Added the bounded Step 05 clarification and synchronized superseded toolchain output-count/execution references to the published 57-output architecture.
- Wrote `.superpowers/flow-signals-implementation-handoff.md` with exact owner files, selectors, fixture facts, expected signal values, continuation slices, and stop rules.
- Verified and closed the contract review P1: narrowed the first reflective public-seam RED from unsupported approve 4/cancel 3 to proven approve 3/cancel 3, retained guard Fact/TRUE-FALSE Outcome assertions, and preserved counter positive as a separate proof-gated compiler slice without changing ProgramGraphs.

## Current state

- The bounded P1 correction is complete: the exact first vertical now expects three signals per Flow and explicitly verifies counter absence for the early-return fixture's null typed boundary guard context.
- Counter-positive and domain/classification acceptance remain required later slices inside the approved plan. No Java/test/schema/POM file and no root-owned or reviewer progress file was edited by this task.

## Reopened-review evidence

- `ControlFlowGraphBuilder` writes the guarded FALSE edge from the guard to the continuation block, then an unguarded `NEXT` edge from that block to the call site.
- `DataFlowGraphBuilder.guardContext(...)` accepts only a guard-bearing edge whose destination is the call-site node; for this shape it returns `GuardContext.none()` and persists null guard ID/polarity in the boundary context.
- `ProgramGraphsPublicFixture` places `approvalClient.record(status)` after `if (status == null) return;`, so the independent guard Fact and TRUE/FALSE Outcomes do not satisfy §8.1.1's typed-boundary counter gate.

## Cumulative owned artifacts

- `backend-agents/sources/source-code/progress/flow-signals-implementation-handoff.md` (owned by this task)
- `backend-agents/sources/source-code/docs/analysis-steps/05-business-flows.md` (bounded extraction/identity/budget clarification)
- `backend-agents/sources/source-code/docs/plans/target-standards-and-toolchain-plan.md` (prior active 14-payload/57-output execution synchronization; unchanged in the reopened P1 correction)
- `.superpowers/flow-signals-implementation-handoff.md` (ignored local implementation handoff requested by the brief)

## Prior closeout verification (retained)

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Correct branch; only pre-existing root-owned coordination progress was modified before this file was created. |
| `git rev-parse HEAD` | PASS | `71a67ed9aa78e9a27c858040944356aca5cdae32` |
| Parent baseline: `EntryRootedFlowCompilerTest,EvidenceCapsuleProjectorTest,BusinessFlowsPublicationSpecifierTest` | PASS | 8 tests, 0 failures/errors/skips, 21.767 s; run by root before this docs-only edit. |
| `git diff --check` | PASS | No whitespace errors after the final documentation corrections. |
| stale-rule `rg` across the two edited docs, progress, and handoff | PASS | None of the enumerated obsolete count, filename, module-count, role/approval, or domain-authorization tokens remains. |
| old Step 05 schema-owner `rg --stats -l` | PASS | 21 matches in exactly the eleven implementation/test owners listed in the handoff. |
| Step 05 fence-balance `awk` | PASS | 20 `~~~` fence lines; even and balanced. |
| required-contract `rg` | PASS | Exact RED name, two signal budgets, external-effect error, acyclic identity, corrected repository filename, and `9+3+4` module count are present. |
| `git check-ignore -v .superpowers/flow-signals-implementation-handoff.md` | PASS | The requested local handoff is intentionally ignored by the repository `.gitignore`; root must consume it from this worktree. |

No Maven command was run by this task, as required. The parent baseline above is evidence for the unchanged pre-edit bounded behavior, not a claim about the not-yet-implemented signal slice.

## Reopened P1 correction verification

| Check | Result | Evidence |
| --- | --- | --- |
| Review/source inspection | PASS | `ControlFlowGraphBuilder` lines 299–313/476–486 put the guard on `guard → continuation block` and the call-site incoming `NEXT` edge has null guard fields; `DataFlowGraphBuilder` lines 413–443/1844–1852 therefore persists null boundary guard ID/polarity. |
| Fixture inspection | PASS | `ProgramGraphsPublicFixture` lines 602–606 place the approval call after the early-return guard. |
| First-RED contract search | PASS | Step 05 and the handoff say approve 3/cancel 3, preserve guard Fact/TRUE-FALSE Outcomes, and assert no counter for either Flow. |
| Counter-acceptance search | PASS | The subsequent exact selector is `EntryRootedFlowCompilerTest#emitsCounterConditionOnlyForProofClosedTypedBoundaryGuardContext`; it requires a naturally persisted non-null typed boundary context and forbids JSON mutation, inferred dominance, or a ProgramGraphs contract change in this cutover. |
| Rejected-expectation search | PASS | No approve-four/guarded-approve-counter expectation remains in the owning Step 05 or handoff. |
| `git diff --check` and Step 05 fence balance | PASS | No whitespace errors; 20 `~~~` fence lines. |
| Scope/status | PASS | Current reopened diff contains only Step 05, this owned progress, and root-owned coordination state; local handoff is ignored. Reviewer progress is untracked and untouched. |

No Maven, Java/test/schema/POM, ProgramGraphs, Provider, customer, network, commit, or push action was performed for the correction.

## Decisions

- Treat the published cross-Flow architecture as fixed; this task only closes implementable extraction and migration detail.
- Use the public stored-artifact seam for the first RED and preserve the six-file Step 05 / 57-output accounting.
- The finite current contract supports at most `JAVA_TYPE_ANCHOR`, `EXPLICIT_CALL`, guarded call-scoped `COUNTER_CONDITION`, and `EXTERNAL_EFFECT_GAP`; all are `GENERIC_TECHNICAL`. The current first fixture proves only type/call/external-Gap and must emit no counter because its typed boundary context is null.
- Match an external-effect Gap through its persisted singleton `affectedCandidateDenominatorKeys` to `CodeFact.candidateDenominatorKey`; the public gap line does not retain the in-memory target fields.
- Establish the first RED with reflection before changing mandatory record constructors, then migrate all known constructors in one compiler GREEN without compatibility overloads or old-wire aliases.
- Keep counter-positive acceptance as a separate exact compiler selector that starts only from a naturally persisted typed boundary guard context; do not change upstream graphs to save the rejected first-fixture expectation.
- Treat Proof-closed counter and domain/classification positives as already-authorized acceptance prerequisites after the initial 3/3 cutover, not optional future features or reasons to mislabel generic technical signals.
- No LLM or other generative model will produce product content, evidence, copy, documents, images, or drift-prone artifacts in this plan.

## Blockers

- None.
- Remaining acceptance prerequisites, not blockers to this handoff or its first RED: the separately proof-gated counter fixture/rule and bounded Sol/ultra domain/classification contract decision must precede full Step 05/effective Step 06 acceptance.

## Exact next action

- Root publishes the corrected docs-first change, then Luna adds only `EntryRootedFlowCompilerTest#emitsExactProofClosedSignalsForTwoPersistedFlowsWithoutCrossFlowBorrowing` using reflection against unchanged constructors: assert approve 3/cancel 3, retained approve guard Fact/TRUE-FALSE Outcomes, null approve boundary guard fields, and no counter in either Flow. Terra makes that RED green before the separate proof-gated counter selector. Counter-positive and bounded domain/classification decisions must close before whole-Step 05/full-process acceptance.

## Resume checks

- At implementation start, re-read this progress record and `.superpowers/flow-signals-implementation-handoff.md`, then run `git status --short --branch`.
- Confirm the corrected authoritative docs-first change is published and reconcile any newer Step 05 contract before writing RED.
- Preserve `progress/continued-implementation-coordination.md`; it is owned by root.
