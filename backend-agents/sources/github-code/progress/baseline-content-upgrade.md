# Progress: baseline-content-upgrade

- Status: IN_PROGRESS
- Agent role: deterministic business-content upgrade
- Model: gpt-5.6-sol
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: replace the overly generic deterministic baseline prose with factual, evidence-backed nine-section content from the frozen Flow Manifest; do not invoke a model or alter frozen source.
- Approved inputs: existing verified `DepotHead` Flow Manifest and synthetic MVP fixture.
- Current branch/worktree: shared untracked source-agent directory under `/Users/yexiaoguang/Documents/ErpMock`.

## Completed

- Confirmed the current real-source baseline has correct evidence/trace mechanics but generic reader prose that is insufficient as a business document.
- Located the cause: `FrozenManifest.parseFacts` retains only fact identifiers, dropping verified kinds and attributes before deterministic rendering.

## Current state

The renderer needs an immutable, validated fact view that preserves only the already-verified `kind`, scalar/list attributes, and evidence references. It will use a deterministic template per supported fact kind and explicitly retain unknown meaning as a pending confirmation.

## Changed files

- progress/baseline-content-upgrade.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Existing frozen baseline `validate` and `trace` | PASS | Nine H2 sections and five exact evidence spans, but business prose is too generic |

## Decisions

- Do not use the rejected R2 or make another model call.
- Do not let arbitrary Manifest prose enter Markdown; derive wording only from validated, typed facts and a fixed Chinese template library.

## Blockers

- None.

## Exact next action

Add a focused baseline-content test for HTTP request, state guards, status persistence and SQL mapping; then retain these fact fields through the core and render them deterministically.

## Resume checks

- Read AGENTS.md and this progress file.
- Run `git status --short` from the repository root.
- Re-run the focused baseline content test after implementation.
