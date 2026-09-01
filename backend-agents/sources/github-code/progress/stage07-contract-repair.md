# Progress: Stage 07 contract repair

- Status: COMPLETE
- Agent role: Sol/ultra design authority sub-agent
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Repair only the Stage 07 detailed design contract and this progress file; no code, tests, POM, README, network, Provider, source capture, Maven, commit, or push.
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, `docs/stages/05-compile-business-flows.md`, `docs/stages/06-interpret-one-flow-at-a-time.md`, `docs/stages/07-admit-and-merge-business-knowledge.md`, and Stage 08 consumer wording.
- Current branch/worktree: shared worktree at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`; unrelated pre-existing changes preserved.

## Completed

- Read the repository instructions and the Stage 05–08 cross-stage contracts.
- Identified that the Stage 07 M1 schema omits model-ineligible Stage 05 Flows because it requires a Stage 06 disposition for every decision.
- Identified obsolete `flowDecisionId`/`ADMITTED_WITH_GAPS` wording and a purported wire specimen that does not establish a replay-valid identity.
- Identified that `knowledge-accounting.json` does not yet define the required embedded `RepositoryCoverageLedgerDraftV2` contract.
- Replaced the Stage 07 detailed contract with an all-Flow, five-variant `FlowAdmissionDecisionV1` and a separate five-variant `InterpretationProposalDecisionV1`.
- Defined required-nullable matrices, identity preimages, fallback/Gap unions, ownership, exact ID-set equations, sorting, failure codes, and targeted test guidance.
- Defined acyclic `Stage07CoveragePreparationV1`, embedded `RepositoryCoverageLedgerDraftV2`, its nested reference, identity, and Stage 08 handoff without adding a reader-visible file.
- Removed the obsolete nonconforming module specimen; all remaining JSON examples are explicitly illustrative and parse as JSON.

## Current state

- Stage 07 design repair is complete. No production code, tests, build files, README, or other stage documents were changed by this task; no Maven, network, Provider, source capture, commit, or push was run.

## Changed files

- `progress/stage07-contract-repair.md`
- `docs/stages/07-admit-and-merge-business-knowledge.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing shared-worktree edits recorded before this task; no cleanup attempted. |
| `rg`/`sed` reads of Stage 05–08 and DESIGN contracts | PASS | Confirmed the missing all-Flow admission closure and coverage-draft details. |
| `git diff --check -- docs/stages/07-admit-and-merge-business-knowledge.md progress/stage07-contract-repair.md` | PASS | No whitespace errors. |
| fence/JSON/link shell checks | PASS | 16 balanced `~~~` markers; all 3 illustrative JSON documents parse with `jq`; relative design link resolves. |
| stale-contract `rg` scan | PASS | No v3 admission schema, old `flowDecisionId`, admitted-with-gaps value used as a decision variant, self-referential Stage07 coverage-root field, or false structural-wire claim remains. |
| required-contract `rg` scan | PASS | All five Flow variants and the draft/preparation/decision/lineage closure fields are present. |
| final combined document check | PASS | `fences=16`, `jsonDocuments=3`, `readerVisibleFiles=6`, `flowVariants=5`, `draftAcyclic=true`, `placeholders=0`; only the Stage 07 design and this progress file are task-owned. |

## Decisions

- Every Stage 05 `flowSliceId` will map to exactly one content-addressed `FlowAdmissionDecisionV1`, including model-ineligible Flows with null Stage 06 references.
- The flow-level decision will be a closed five-variant tagged union; proposal-level KEEP/NARROW/DROP/NEEDS decisions remain a separate closed union.
- `RepositoryCoverageLedgerDraftV2` will be embedded in the existing `knowledge-accounting.json`; Stage 07 remains five semantic files plus one receipt.
- Non-replay examples will be labelled illustrative and will not be described as schema-valid wire specimens.
- The draft stores Stage01–06 publication roots plus a Stage07 M1/M2 semantic preparation root; it never embeds Stage07 publication/root/receipt or its own enclosing artifact reference.
- Stage 08 identifies the embedded value with the enclosing `knowledge-accounting.json` reference plus `repositoryCoverageLedgerDraftId`; only Stage08 M1 creates the final ledger.

## Blockers

- None. The root design owner acknowledged and is synchronizing DESIGN/Stage08 to the exact Stage 07 records, including distinct proposal-decision versus flow-decision IDs and the nested draft reference.

## Exact next action

- Root agent integrates this completed Stage 07 design with its DESIGN/Stage08 synchronization; implementation must begin from the new v4/v3 schemas and targeted RED tests only after the design publication gate.

## Resume checks

- Re-read `git status --short` and confirm only the Stage 07 design plus this progress file are task-owned.
- Re-run searches for obsolete `stage07-admission-decision-set-v3`, `flowDecisionId`, and claims that illustrative JSON is wire-exact; `ADMITTED_WITH_GAPS` may appear only in the explicit rejection sentence, never as a variant.
