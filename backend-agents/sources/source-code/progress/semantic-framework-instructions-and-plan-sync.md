# Progress: Semantic framework instructions and plan sync

- Status: COMPLETE
- Agent role: Sol/ultra design authority for Batch 1 instruction and plan synchronization
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Align current Source Code Analysis Agent instructions and navigation with the approved semantic-material, local/process DRAFT+REVIEW framework, add one concise ten-batch implementation plan, apply the separately authorized source-neutral `FlowInterpretationRound` correction in `backend-agents/AGENTS.md`, and record the approved production store-root canonicalization rule. Documentation only; no Java, tests, schemas, POM, historical progress, run artifacts, source capture, network, or Provider calls.
- Approved inputs: User-approved ten-batch delivery direction; `docs/DESIGN.md`; Steps 05–08 target designs; `README.md`; `progress/semantic-framework-delivery-coordination.md`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `dea5c1b`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the nearest scoped `AGENTS.md`, target `README.md`, `docs/DESIGN.md`, Steps 05–08, the coordination progress, and the progress template.
- Recorded the broad pre-existing dirty worktree before editing; overlapping target docs already contain the approved semantic design and must not be rewritten by this narrow work unit.
- Replaced scoped target rules for R0 registry/finite-key R1/R2 and fixed P1/P2 with bounded local/process DRAFT+REVIEW, DRY packets, program-only proof metadata, whole-record review/admission, and no-live-Provider automated tests.
- Added the user-approved Batch 1–10 sequence with per-batch exit criteria and the exact `42 + 8 + 1 + 1 = 52` reader-visible output inventory; linked it from current navigation.
- Corrected the deferred runtime-recovery note so it no longer freezes the retired semantic route as an active invariant.
- Applied the separately authorized shared-rule correction: `FlowInterpretationRound` is now source-neutral, its names and semantics come from the nearest source-scoped target design, and it still cannot create or consume another reader candidate.
- Recorded the approved production `RunStoreBootstrap.open(Path)` canonicalization boundary: reject a final-component symlink, resolve existing parent aliases once, retain only the canonical real directory, and keep `openForTest(Path)` test-only.

## Current state

- Durable source-scoped target instructions, design navigation, and plan navigation now agree on the six-module DRAFT/REVIEW route and 52-output target.
- Current Java/schema R0/finite-key files remain explicitly documented as `NOT IMPLEMENTED` migration input; this docs-only work did not alter them.
- Batch 1 remains `IN_PROGRESS`: this closeout does not claim the run-core Steps 01–05 prefix is integrated.

## Changed files

- `progress/semantic-framework-instructions-and-plan-sync.md`
- `AGENTS.md`
- `README.md`
- `docs/DESIGN.md`
- `docs/plans/semantic-framework-ten-batch-implementation-plan.md`
- `docs/plans/source-analysis-naming-and-delivery-plan.md`
- `docs/plans/target-standards-and-toolchain-plan.md`
- `docs/supplements/runtime-recovery-todo.md`
- `../../AGENTS.md`
- `docs/references/canonical-persistence-identity-contracts.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Required target/link existence plus ten-batch/count search | PASS | New plan exists; exactly 10 `## Batch` headings; explicit `42 + 8 + 1 + 1 = 52`; links found in scoped AGENTS, README, DESIGN, and both prior plan headers. |
| Scoped stale-target assertion search | PASS | No old R0/R1/R2/P1/P2 task arithmetic, 47-payload target, or 57-output target remains in `AGENTS.md`; remaining old names in target docs are explicitly labeled retired/current migration state. |
| `git diff --check` on tracked edited paths | PASS | No whitespace errors. |
| `git diff --no-index --check /dev/null` on the new plan and this progress file | PASS | Expected difference status with empty whitespace-error output for both untracked files. |
| Shared `FlowInterpretationRound` focused diff/search | PASS | The only parent-AGENTS diff is the authorized paragraph; it no longer prescribes R1/R2 and still states that internal interpretation does not create a replacement or consume another `ReaderCandidateRound`. |
| `git diff --check -- ../../AGENTS.md` | PASS | No whitespace errors. |
| Store-root contract focused search and no-index diff check | PASS | The contract contains final-component NOFOLLOW rejection, one default `toRealPath()` parent resolution, canonical-directory-only retention, lexical-alias prohibition, and distinct `openForTest(Path)`; no whitespace errors. |

## Decisions

- Preserve all existing source, evidence, security, provider-identity, human-authorization, two-reader-candidate, and fail-closed rules while replacing only the superseded semantic-production route.
- Treat current R0/finite-key Java as migration input, never as the approved target or a compatibility contract.
- Batch 1's full exit still includes the Steps 01–05 run-core prefix, but this closeout claims only instruction synchronization and store open/reopen work; the new plan explicitly keeps Batch 1 `IN_PROGRESS` until the prefix and all other criteria have fresh evidence.
- Production bootstrap may follow existing parent aliases exactly once to canonicalize the root, but the caller-selected final component itself is never a symlink and no downstream store sees the lexical alias.

## Blockers

- None for this source-scoped documentation work.
- No unresolved instruction disagreement remains: the authorized shared paragraph now delegates internal round names to each nearest source-scoped target design.

## Exact next action

1. Root coordinator may include the verified documentation set in the Batch 1 closeout; Batch 1 itself remains `IN_PROGRESS` until its non-documentation exit criteria are evidenced.

## Resume checks

- Re-run `git status --short` and inspect only this work unit's paths before resuming.
- Verify links/search consistency and `git diff --check`; do not run Maven or any Provider/source/network command.
