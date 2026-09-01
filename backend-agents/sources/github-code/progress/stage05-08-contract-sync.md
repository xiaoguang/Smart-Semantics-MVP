# Progress: Stage05-08 contract sync

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority — bounded cross-stage documentation repair
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01T01:46:54-02:30
- Last updated: 2026-09-01T01:57:23-02:30
- Scope: Align Stage05 repository Flow coverage and Stage08 coverage-ledger/trace contracts with the accepted Stage07 all-Flow eligibility contract.
- Approved inputs: scoped AGENTS.md, docs/DESIGN.md sections 3.7/11/13.3, and Stage05/07/08 target designs.
- Current branch/worktree: codex/github-code-target-implementation / /private/tmp/linguan-github-code-target-implementation

## Completed

- Read repository and source-scoped instructions plus the codebase-design skill.
- Confirmed this slice is documentation-only and owns only Stage05, Stage08, and this progress file.
- Added Stage05's exact all-Flow model eligibility partition and per-ineligible-Flow Gap mapping to the public coverage contract, equations, module handoff, examples, and Luna/Terra guidance.
- Replaced Stage08's ambiguous draft ArtifactReference with `RepositoryCoverageLedgerDraftReferenceV1` and separated Stage01–06 refs, the Stage07 draft preparation root, and the completed Stage07 publication ref without an identity cycle.
- Expanded `RepositoryCoverageLedgerV3` to preserve every Stage07 proposal/decision/meaning/lineage/fallback/knowledge/ownership ID set before adding reader ownership.
- Preserved M1-M4, exactly eight reader-visible Stage08 outputs, one repository plan/document, and zero Stage06 calls for model-ineligible Flows.

## Current state

- Bounded documentation repair is complete. Root Design Authority has the exact final field order and remaining DESIGN synchronization list.

## Changed files

- `docs/stages/05-compile-business-flows.md`
- `docs/stages/08-build-nine-section-document-and-archive.md`
- `progress/stage05-08-contract-sync.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared worktree changes identified; this slice will not stage or alter unrelated paths. |
| `git diff --check -- docs/stages/05-compile-business-flows.md docs/stages/08-build-nine-section-document-and-archive.md progress/stage05-08-contract-sync.md` | PASS | No whitespace errors. |
| `rg -c '^~~~' docs/stages/05-compile-business-flows.md docs/stages/08-build-nine-section-document-and-archive.md` | PASS | Fence counts are even: Stage05=16, Stage08=18. |
| Stage08 output/module scan | PASS | Exactly eight reader-visible output rows; M1-M4 remain the only Stage08 modules. |
| Stage07 draft → Stage08 final field-set comparison | PASS | All 43 Stage07 fields from `sourceFileIds` through `reasonedSemanticExclusionIds` are present in the final ledger; missing set is empty. |
| Stale-term scan | PASS | No `stage01To07CoverageRoots`, `draftLedgerRef`, or `interpretationDecisionIds` remains in the two owned stage designs. |

## Decisions

- Preserve exactly eight Stage08 reader-visible outputs and M1-M4.
- Treat model-ineligible Stage05 Flows as first-class repository coverage, never as implicit Stage06 calls.
- The Stage07 nested draft is addressed only by `{knowledgeAccountingRef, repositoryCoverageLedgerDraftId, schemaVersion}`.
- Stage08 preparation binds Stage01–06 publication refs, the nested draft's Stage07 preparation root, and the completed Stage07 publication ref as three distinct facts.
- The final ledger copies Stage07 ID sets field-by-field; `interpretationProposalDecisionIds` cannot stand in for Flow admission, meaning, lineage, fallback, relation, metric, conflict, or ownership sets.

## Blockers

- None.

## Exact next action

- Root Design Authority synchronizes DESIGN §3.7/§11/§13.2/§13.3 and Stage05's global coverage summary, then runs the independent architecture review.

## Resume checks

- Re-read this file and `git status --short`.
- Verify Stage07's latest five-variant `FlowAdmissionDecisionV1` and `RepositoryCoverageLedgerDraftV2` fields remain identical to the Stage08 copy set.
