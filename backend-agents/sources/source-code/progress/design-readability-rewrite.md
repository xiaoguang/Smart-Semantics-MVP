# Progress: design readability rewrite

- Status: COMPLETE
- Agent role: GitHub Code Agent design-document editor
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-29 17:46:50 NDT
- Last updated: 2026-08-29 18:11:35 NDT
- Scope: Rewrite the GitHub Code Agent target architecture, POC implementation record, reader navigation, shared terminology, and scoped documentation-readability rules. No code, tests, schemas, manifests, candidates, `.workspace`, V6/Pinned/formal assets, or other Source Agents.
- Approved inputs: Parent-approved ten-point documentation plan; existing frozen jshERP/DepotHead facts may be read only and must not be regenerated or changed.
- Current branch/worktree: `codex/data-standardization-review-ia` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Read all four governing `AGENTS.md` files in full.
- Ran the required initial `git status --short`; pre-existing changes are outside this task and will be preserved.
- Read the current `DESIGN.md`, `docs/stages/00-mvp.md`, `README.md`, shared `CONTEXT.md`, and this directory's progress template in full.
- Confirmed the shared `NineSectionProfile` contract and exact heading order.
- Read the bounded Java Interface/core/archive/runtime-admission surfaces and the frozen DepotHead manifest/source spans needed to verify current facts without executing code.
- Rewrote `DESIGN.md` around one branch-free target data flow, twelve uniform ARCH module contracts, the POC-to-target term map, the real rejected DepotHead walkthrough, stable cross-module contracts, and one end-only maturity matrix.
- Verified immediately after replacement that `DESIGN.md` exists and contains both its orientation opening and final maturity matrix.
- Reframed `docs/stages/00-mvp.md` as the POC implementation/audit record: hypotheses, temporary target stand-ins, two isolation test paths, one Markdown plus seven JSON sidecars, memory-only intermediates, exact DepotHead rejection, and nine design changes returned to the target architecture.
- Preserved the detailed Manifest, Fact, R1/R2, identity, archive, Interface, CLI, safety, audit, gap, and test facts in the later technical-reference sections.
- Updated `README.md` with three explicit reader routes, a plain-language target/POC distinction, and retained accurate current capability/CLI/archive/test references.
- Rewrote shared `CONTEXT.md` with Chinese-first definitions for the target terms and explicit no-confusion pairs for integrity/proof, Trace/Proof, identity/correctness, the two round types, and configured/observed provider identity.
- Added the narrowly scoped documentation-readability rules to `sources/github-code/AGENTS.md`.
- Completed the first verification group: all twelve ARCH cards contain exactly one of each required field; the sole Mermaid flow has no POC branch; POC terms are confined to the POC map/case/status sections; the exact nine headings and status-matrix columns pass.
- Checked every Markdown link in the six changed documents; every local target exists.
- Removed Markdown hard-break trailing spaces from `CONTEXT.md` and rechecked all changed files for trailing whitespace.
- Re-read the complete rewritten `DESIGN.md` and the full stage record in bounded chunks; corrected the ARCH-10/11 next-module guarantees and remaining narrative MVP labels.
- Completed the final 17-item requirements audit, Markdown fence check, 62-link resolution check, repository diff/whitespace checks, and final scope/status review.

## Current state

All approved documentation work is complete and verified. The target architecture, POC record, reader routes, shared terminology, and durable readability rules agree; the DepotHead result remains rejected with accuracy exit NOT MET and no accepted Candidate.

## Changed files

- `sources/github-code/progress/design-readability-rewrite.md`
- `sources/github-code/DESIGN.md`
- `sources/github-code/docs/stages/00-mvp.md`
- `sources/github-code/README.md`
- `source-to-standard-markdown/CONTEXT.md`
- `sources/github-code/AGENTS.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git -C /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2 status --short` | PASS | Preserved pre-existing two modified design/handoff files and the untracked `source-to-standard-markdown/` tree. |
| Full reads of governing instructions and requested docs | PASS | Scope, frozen-input, source ownership, progress, and accuracy constraints confirmed. |
| Read `contracts/README.md` | PASS | Exact nine-section names/order and shared identity seam confirmed. |
| Read scoped implementation and frozen DepotHead spans | PASS | Current Interface/archive/receipt behavior and the 2-pass/3-fail semantic audit facts confirmed; no execution performed. |
| `test -f .../sources/github-code/DESIGN.md` plus bounded top/end reads | PASS | Rewritten authoritative design restored and complete after replacement. |
| Heading/key-contract scans and bounded reads of `docs/stages/00-mvp.md` | PASS | POC framing, exact exit result, 1+7 archive boundary, memory-only intermediates, two test paths, and design-feedback summary are present; technical facts remain. |
| Bounded reads and term scans of `README.md`, `CONTEXT.md`, and scoped `AGENTS.md` | PASS | Three reader routes, Chinese-first target vocabulary, five no-confusion pairs, and durable readability rules are present. |
| Twelve-card field validator | PASS | ARCH-01..ARCH-12 each contain exactly one ARCH identity, reader question, input, deterministic work, model role, output, failure behavior, and next-module guarantee. |
| Target-flow and POC-term placement validator | PASS | One Mermaid target flow; no POC branch; POC terms occur only in sections 3, 4, and 6. |
| Nine-section contract comparison | PASS | Exact headings and order match `NineSectionProfile`; status matrix has the requested five columns and is the last section. |
| Local Markdown link checker | PASS | Every local link in all changed documents resolves to an existing path. |
| `rg -n '[[:blank:]]+$' <changed files>` | PASS | No trailing whitespace remains. |
| Final requirements/document-shape validator | PASS | 17/17 requirements passed, including one target Mermaid, twelve eight-field ARCH cards, DepotHead per-module walk, target/POC separation, exact nine H2, reader routes, glossary pairs, and balanced fences. |
| Local Markdown link checker | PASS | Checked 62 local links; 0 missing. |
| `git -C /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2 diff --check` | PASS | Exit 0; no diagnostics. |
| `git diff --no-index --check /dev/null <changed-file>` for all six files | PASS | Each returned the expected added-file difference status with no whitespace diagnostics. |
| Final `git status --short` | PASS | The same two pre-existing modified files remain outside scope; `source-to-standard-markdown/` remains untracked. No commit was created. |
| Model/network/build/customer execution audit | PASS | No model/provider, network, Maven, customer code, test, build, or generated-candidate command was invoked. |

## Decisions

- Treat the approved plan as the already-approved bounded design; do not reopen architecture choices.
- Keep the main architecture free of MVP/baseline/recorded-provider branches; retain those facts only in the compact POC map, maturity matrix, and stage implementation record.
- Keep ARCH identities stable while making each module answer the same eight reader questions. Use the target pipeline terms as the primary architecture vocabulary.
- Treat DepotHead's semantic audit failure as the intended target-module walkthrough outcome; do not project its historical Markdown into target evidence.
- Use evidence-type labels in the stage ARCH table instead of a second target-maturity matrix.
- Keep shared terminology source-agnostic; code/command/file names remain in the GitHub Code Agent's technical sections.

## Blockers

- None.

## Exact next action

None. Task complete.

## Resume checks

- Re-run `git status --short` and confirm only scoped documentation/progress edits are attributable to this task.
- Re-read this progress file and verify the last recorded changed paths before continuing.
- Do not run Maven, a model/provider, network access, or customer code.
