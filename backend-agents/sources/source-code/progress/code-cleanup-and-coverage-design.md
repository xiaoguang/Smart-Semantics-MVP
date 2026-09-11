# Progress: code-cleanup-and-coverage-design

- Status: COMPLETE
- Agent role: sole design-document author for code cleanup and scalable activity coverage
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: inspect the current implementation and write one PROPOSED cleanup/coverage design; no implementation, deletion, test/configuration changes, execution, commit, or push
- Approved inputs: current worktree at HEAD 6a191017c976d9304513caa9828b1b903221c08d; existing dirty worktree; named design documents; current Java/tests; supplied E1-E4 live artifacts; read-only inventory of the protected pre-reset main worktree
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read the applicable AGENTS.md in full.
- Read docs/DESIGN.md, Steps 05-08, and docs/plans/coherent-code-context-implementation-plan.md in full.
- Confirmed the starting commit, branch, and pre-existing dirty worktree before writing.
- Read the current ActivityExplainer lifecycle, output schema, prompts, coverage/checkpoint publishers, ProcessExplainer and BusinessReportPublisher handoffs.
- Verified the saved four-entry live DRAFT input/response and the old-package/test dependency inventory without running Maven or a Provider.
- Applied the parent review's single bounded factual calibration: corrected the shared-contract count, zero-entry Provider boundary, semantic-status wording, verification wording, and material-grouped downstream projection.

## Current state

- The single PROPOSED design is complete at docs/plans/code-cleanup-and-scalable-activity-coverage-design.md.
- No Java, test, configuration, authoritative design, README, customer source, or stored product artifact was changed.

## Changed files

- progress/code-cleanup-and-coverage-design.md
- docs/plans/code-cleanup-and-scalable-activity-coverage-design.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse HEAD` | PASS | `6a191017c976d9304513caa9828b1b903221c08d` |
| `git branch --show-current` | PASS | `codex/source-analysis-business-flows-closeout` |
| `git status --short` | PASS | Existing dirty worktree observed and left untouched; design task starts with 124 pre-existing changes as supplied by the coordinator |
| Read-only implementation and artifact inspection | PASS | Confirmed DRAFT coverage validation occurs before REVIEW, schema maxItems follows the activity profile, the live four-entry response covers only E1/E2, and current downstream model inputs project only NOT_ANALYZED counts |
| `wc -l docs/plans/code-cleanup-and-scalable-activity-coverage-design.md` | PASS | 424 lines after the one parent-review calibration, within the requested bounded design size |
| Placeholder/trailing-whitespace scan | PASS | No TBD/TODO placeholders, forbidden fixed output totals, or trailing whitespace found |
| Task-owned `git status --short` | PASS | Exactly the progress file and proposed design are new; overall worktree count is the supplied 124 plus these two files |
| Parent design review calibration | PASS | Five requested factual corrections applied without expanding scope or changing the proposed core contract |

## Decisions

- Treat this as a PROPOSED design, not an authorized implementation plan execution.
- Preserve the eight steps, four deep business Modules, exact source/Proof rules, fixed nine chapters, and current fatal/no-replay boundaries.
- Mark any change to current design rules, including coverage-before-REVIEW, for explicit user approval.
- Keep DRAFT and REVIEW as internal tasks of one product candidate; do not reinterpret the prior failed response as replayable.
- Keep current active FLOW_INTERPRETATION module addresses 10/11 and ModelRuntimeIdentityV1 while retiring old 1-9 registrations and old-only payload branches in dependency order.
- Recommend a REVIEW-only required `unexplainedEntries` key array plus program-owned `MODEL_NOT_EXPLAINED`; preserve the original model-visible material context and local key for specific downstream Chapter 9 projection without parsing the context string.
- Treat immediate per-package Activity/Process checkpointing as a separately documented existing gap because current fixed module addresses cannot be repeatedly installed with different aggregate content.
- Group unexplained-entry model projection by material so one material context is sent once with all local missing keys; keep per-entry program-side coverage records.

## Blockers

- No authoring blocker. User confirmation is required before changing coverage-before-REVIEW and adding the proposed unexplained-entry/downstream schema contract.

## Exact next action

- Parent agent reviews and presents the PROPOSED design and its five explicit user-confirmation points; no implementation begins from this task.

## Resume checks

- Re-read this progress file, verify HEAD/branch, and confirm that only this progress file plus the intended design file are task-owned.
- Re-run `git status --short` and protect every pre-existing change.
- Reopen the design rather than reconstructing it from progress; confirm user approval before converting it into TDD implementation work.
