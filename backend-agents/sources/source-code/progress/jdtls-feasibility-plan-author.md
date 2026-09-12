# Progress: JDT LS source-navigation feasibility plan author

- Status: COMPLETE
- Agent role: Feasibility-plan author; documentation only
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-12T10:20:51Z
- Last updated: 2026-09-12T10:35:35Z
- Scope: Write one concise, executable plan for a later isolated JDT LS feasibility trial; do not install or run tools, index source, build customer code, call a product model, or change production behavior.
- Approved inputs: Repository/backend/source-code instructions; current `docs/DESIGN.md`; frozen jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; primary registration and secondary financial samples named by the user; coordinator-verified JDT LS settings and local runtime facts.
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` at `/private/tmp/linguan-source-analysis-process-design`; base `db28f8d05291a807fcf5ee478073994664216337`

## Completed

- Read the three applicable `AGENTS.md` files, `docs/DESIGN.md`, and this progress template in full.
- Confirmed the requested branch and base commit and preserved the coordinator's existing progress change.
- Received coordinator-verified frozen sample locations, JDT LS safety settings, and local JDK/JDT availability facts.
- Authored and self-reviewed the 249-line executable feasibility plan, then applied the coordinator's bounded executable-consistency corrections.

## Current state

- The approved plan-only feasibility document is complete. It defines the fixed input, isolated Java/LSP4J harness, no-build initialization, two entry walks, optional tiny structure diagnostic, packet/oracle contract, one-fix limit, Agent sequence, timing, and `PASS|LIMITED|FAIL` handoff.
- No JDT LS trial has run and no feasibility outcome has been measured; the plan remains `PLAN_ONLY`.

## Changed files

- `progress/jdtls-feasibility-plan-author.md`
- `docs/plans/jdtls-source-navigation-feasibility-plan.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS before author edit | Only coordinator-owned `progress/jdtls-feasibility-plan-coordination.md` was already added |
| `git branch --show-current` | PASS | `codex/jdtls-source-navigation-feasibility` |
| `git rev-parse HEAD` | PASS | `db28f8d05291a807fcf5ee478073994664216337` |
| Required instruction/design reads | PASS | 988 lines read in full across the three instruction files and `docs/DESIGN.md` |
| Exact fixed-source spot reads via `GIT_NO_LAZY_FETCH=1 git show 8c30ce…:<path>` | PASS | Confirmed registration, financial Service/Mapper/XML ranges and actual snippets without checkout or scan |
| Plan scope/placeholder scan | PASS | 249 lines; no trailing whitespace or unresolved placeholder markers |
| `git diff --check` | PASS | No whitespace errors in tracked diffs |
| `git status --short` | PASS with expected shared changes | Only the coordinator's README/progress plus this Agent's plan/progress are changed |

## Decisions

- Treat the user-approved request as a bounded feasibility spike plan, not production implementation or a multi-tool bakeoff.
- Address the frozen commit through immutable Git objects or a derived throwaway projection; never use the local clone's current HEAD as input.
- Keep all future harnesses and outputs under ignored `.workspace/` plus an isolated `research/` harness path; do not add target Java packages.
- Use one minimal Java/LSP4J harness with syntax-only JavaParser; preserve repository-local candidate bodies and never infer a Spring runtime implementation.
- Treat environment/authorization failure separately from JDT navigation failure, and stop all alternative-tool evaluation after a successful JDT trial.
- Run registration and financial phases in one orchestrator-owned stdio JDT session; gate the second phase on the saved/checked primary packet and keep LSP/source range conversions explicit.

## Blockers

- None for this completed plan. JDT LS release/JDK compatibility and server installation remain explicit later-trial preflight items, not assumed facts.

## Exact next action

- Coordinator reviews the completed plan and includes it in the single docs-only local commit. The later trial's first exact action is Task 1: pin official JDT LS/LSP4J/JDK inputs and request installation/execution authorization.

## Resume checks

- Re-read this file and the plan, run `git status --short`, and confirm branch/base before any continuation.
- Do not edit this completed progress file, run the JDT LS trial without new authorization, or infer success from the completed plan.
