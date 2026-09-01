# Progress: source-analysis-naming-design

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Docs-only naming design for the GitHub source-analysis Agent: durable implementation plan, target path/group/package/module vocabulary, eight analysis-step documents, current maturity audit, Wire Reset, and documentation gates.
- Approved inputs: Delegated delivery-1 brief; root/backend/source AGENTS; `docs/plans/target-standards-and-toolchain-plan.md`; existing authoritative design, stage documents, README, and historical progress files.
- Current branch/worktree: `codex/source-analysis-naming-design` in `/private/tmp/linguan-source-analysis-naming-design`

## Completed

- Read the root, backend, and GitHub source-scoped AGENTS instructions.
- Read the approved target standards and toolchain plan and the progress template.
- Confirmed the worktree was clean before edits.
- Fixed the approved target registry: `sources/source-code`, `org.sourceanalysis:source-code-analysis-agent`, `org.sourceanalysis.app`, eight semantic analysis packages, and the explicit cross-cutting roots.
- Renamed the eight authoritative documents into `docs/analysis-steps/` with their approved ordered semantic slugs.
- Moved the POC record to `docs/history/00-mvp.md` so it is no longer active navigation.
- Created `docs/plans/source-analysis-naming-and-delivery-plan.md` with the full Wire Reset and delivery sequence.
- Updated backend/source instructions, README, DESIGN, the target toolchain plan, and the recovery supplement to use the approved semantic analysis-step vocabulary and target identity.
- Defined semantic receipt/archive paths, preserved the 42 + 8 + 1 + 1 = 52 official-output contract, and kept the design-publication gate, fixed jshERP scope, and no-same-run-auto-recovery rule.
- Kept pre-reset implementation facts in explicit current-maturity audit sections and history-only records; target contracts reject old paths, packages, numbered step names, schemas, receipts, and aliases without compatibility readers.
- Completed active-document link, navigation, naming-registry/cardinality, exact-file-set, and whitespace checks without Maven, model, source, customer-code, or network calls.

## Review round 1 resolutions

- Removed the two active links to deleted `docs/stages/00-mvp.md`; backend navigation now enters the authoritative DESIGN and eight-analysis-step index, while the downstream review design names only DESIGN as the upstream architecture authority.
- Restored the canonical Adapter vocabulary in the nine-section document: `analyze-step`, `POST /analysis-step-executions`, and semantic `--analysis-step-key` location discriminators.
- Corrected the strict-fixture constructor example to the exact four-field `AnalysisStepModuleAddress(runId, analysisStepKey, moduleNumber, moduleKey)` contract.
- Restored `Stage02Compiler` as an explicitly pre-reset current-maturity fact instead of relabeling the historical class with target discovery vocabulary.
- Repaired the coverage equations to use the defined `flowSliceIds` fields and the exact set of `flowSliceId` values from canonical `flow-interpretation-dispositions.jsonl` records.

## Current state

The docs-only naming design is coherent and ready for integration. Target contracts use the approved semantic names; old implementation names remain only where the current-maturity audit or Wire Reset rejection contract must identify pre-reset inputs. Implementation remains pre-reset and may not begin until this coherent design commit is integrated and the design-publication gate is satisfied.

## Changed files

- `backend-agents/README.md`
- `backend-agents/AGENTS.md`
- `backend-agents/sources/github-code/AGENTS.md`
- `backend-agents/sources/github-code/README.md`
- `backend-agents/sources/github-code/docs/DESIGN.md`
- `backend-agents/sources/github-code/docs/analysis-steps/01-verified-source-inventory.md` through `08-nine-section-document.md` (renamed from `docs/stages/`)
- `backend-agents/sources/github-code/docs/history/00-mvp.md` (renamed from `docs/stages/00-mvp.md`; historical content retained, links retargeted)
- `backend-agents/sources/github-code/docs/history/implementation-estimate-calibration-2026-09-01.md` (moved from active plans; historical content retained, link retargeted)
- `backend-agents/sources/github-code/docs/plans/source-analysis-naming-and-delivery-plan.md`
- `backend-agents/sources/github-code/docs/plans/target-standards-and-toolchain-plan.md`
- `backend-agents/sources/github-code/docs/supplements/runtime-recovery-todo.md`
- `backend-agents/sources/github-code/progress/source-analysis-naming-design.md`
- `docs/design/data-standardization-review-experience.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean before edits. |
| Inline naming-registry/cardinality scan | PASS | Exact eight filenames, semantic keys/packages/receipts, and `42 + 8 + 1 + 1 = 52` contract present. |
| Inline semantic-range scan | PASS | No numbered semantic-key ranges or numeric `finalAnalysisStepKey` values remain. |
| Inline Markdown relative-link scan | PASS | All relative links resolve across 15 active files. |
| Inline stale active-navigation scan | PASS | No active links to `docs/stages/`, the POC record, or the historical estimate. |
| Inline exact analysis-step file scan | PASS | Exactly the eight approved files exist under `docs/analysis-steps/`. |
| `git diff --check` | PASS | No whitespace errors. |
| Review round 1 Markdown relative-link scan | PASS | All relative links resolve across 17 active source/downstream documents. |
| Review round 1 stale active-navigation scan | PASS | No active link targets deleted `docs/stages/`, the POC, or the historical estimate. |
| Review round 1 regression scan | PASS | Invalid Adapter spellings, false maturity alias, malformed coverage phrase, and five-argument address example are absent. |
| Review round 1 exact file/cardinality scan | PASS | Eight approved analysis-step files and the fixed 52-output contract remain present. |
| Review round 1 `git diff --check` | PASS | No whitespace errors in the review fix. |
| Maven/model/source/customer/network calls | NOT RUN | Prohibited for this docs-only delivery. |

## Decisions

- This work unit is documentation-only and invokes no LLM/product-content generation, source capture, network access, Maven, customer build, or runtime call.
- Preserve historical progress file contents; only this task-owned progress file may be created and updated.
- Numerical prefixes order documentation/runtime directories only; packages, Java types, fields, schema names, artifact IDs, and commands use semantic names.
- The implementation plan uses `source-code-analysis-agent` and the exact approved semantic/cross-cutting package registry; it creates no generic helper root.

## Blockers

None.

## Exact next action

Integrate and publish this single docs/progress commit through the parent workflow; only after the design-publication gate is satisfied may the separately authorized Wire Reset implementation begin.

## Resume checks

- Confirm branch is `codex/source-analysis-naming-design` and worktree is `/private/tmp/linguan-source-analysis-naming-design`.
- Re-read this progress file and inspect `git status --short`.
- Do not modify Java, POM, tests, schemas, code, or configuration.
- Do not treat current pre-reset implementation names or history-only records as target aliases.
