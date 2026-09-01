# Progress: Stage 06 eligibility contract synchronization

- Status: COMPLETE
- Agent role: Sol/ultra Design Authority, Stage 06 bounded contract repair
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Make Stage 06 consume exactly the Stage 05 model-eligible Flow subset while preserving complete Stage 05 denominator validation and Stage 07 ownership of model-ineligible fallback.
- Approved inputs: scoped `AGENTS.md`, `docs/DESIGN.md` sections 3.5/3.7/13.4, Stage 05, Stage 06, Stage 07 target designs
- Current branch/worktree: `codex/github-code-target-implementation` / `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the scoped repository rules and deep-module design guidance.
- Confirmed the worktree contains unrelated in-progress design and implementation drafts; this task will modify only its assigned stage document and this progress file.
- Read the complete Stage 05 and Stage 07 eligibility/admission contracts plus the governing overall accounting, completion, and algorithm invariants.
- Reframed Stage 06 around four non-interchangeable quantities: all Stage 05 Flows `N`, model-eligible Flows `E`, model-ineligible Flows `I=N-E`, and R0-ready Flows `R`.
- Made the full Stage 05 denominator validation precede the eligible-only model projection, while forbidding every Stage 06 per-Flow object for the ineligible subset.
- Updated the six module briefs, ten reader-visible artifacts, wire-table cardinalities, task payload guarantees, tests, and acceptance rules to use exact `E`/`R` semantics.
- Preserved one frozen registry and all ten Stage 06 files when `E=0`, including the non-empty repository case where every Flow is model-ineligible.

## Current state

- Stage 06 eligibility synchronization is complete and textually verified. No implementation, test, build, schema file, source, provider, or network action was performed.

## Changed files

- `docs/stages/06-interpret-one-flow-at-a-time.md`
- `progress/stage06-eligibility-sync.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared work identified; no owned edit overwritten. |
| `git diff --check -- docs/stages/06-interpret-one-flow-at-a-time.md progress/stage06-eligibility-sync.md` | PASS | No whitespace errors. |
| module heading count | PASS | Exactly six named business modules, M1 through M6. |
| reader-visible Stage 06 output count | PASS | Exactly ten files, including the store-last stage receipt. |
| tilde fence balance | PASS | 14 fence markers; balanced. |
| stale cardinality search | PASS | No `3N`, `2N`, `正常N`, `N R0`, or all-Flow three-round wording remains in Stage 06. |
| positive contract search | PASS | `N=E+I`, `E+2R`, exact ineligible exclusion, same full Capsule view, technical fallback, and ten-file persistence all present. |

## Decisions

- Stage 06 will remain a six-module, ten-reader-visible-artifact stage.
- Stage 06 revalidates all `N` Stage 05 Flow/Capsule records, but its task, round, candidate, and disposition domains are limited to the `E` eligible subset.
- `R` is the exact `READY_FOR_FREEZE` subset. R1/R2 task count is `2R`; normal logical slots/calls are `E+2R`, and only equal `3E` when `R=E`.
- Stage 07, not Stage 06, owns `MODEL_INELIGIBLE_TECHNICAL_FALLBACK` for each ineligible Flow.
- No production code, POM, test, schema file, provider, capture, Maven, network, or deployment action is in scope.

## Blockers

- None.

The root Design Authority should synchronize these already-existing cross-document statements before publication:

- `docs/DESIGN.md` §3.5 currently names the R0-ready subset `READY_FOR_ADMISSION`; the Stage 06/07 wire enum is `READY_FOR_FREEZE`.
- `docs/DESIGN.md` §3.1/§3.3, §13.9 and current-maturity wording still contain all-ready `3N` shorthand; they should use `E+2R`, with `3E` only when `R=E`.
- scoped `AGENTS.md` still says normal N-Flow cardinality is `3N` and that Stage 06 covers every Stage 05 Flow; it must distinguish the complete `N` denominator, eligible `E` subset, R0-ready `R` subset, and Stage07 ownership of model-ineligible fallback.

## Exact next action

- Root Design Authority integrates the listed DESIGN/AGENTS synchronization, then includes this stage document in the independent architecture review and docs-only publication gate.

## Resume checks

- Re-read this progress file.
- Run `git status --short` and preserve all non-owned changes.
- Re-open the current Stage 05 and Stage 07 eligibility records before resuming edits.
