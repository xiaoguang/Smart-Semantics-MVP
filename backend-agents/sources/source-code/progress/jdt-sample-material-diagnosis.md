# Progress: JDT sample material diagnosis

- Status: COMPLETE
- Agent role: Read-only debugging sub-agent
- Model: GPT-5
- Started: 2026-09-13T08:14:21-02:30
- Last updated: 2026-09-13T08:19:23-02:30
- Scope: Diagnose the four-entry JDT run's BusinessMaterialBuilder failure and identify an existing public persisted Step 01–05 reuse path; no source, test, configuration, or durable design edits.
- Approved inputs: Run `analysis-run:152cf03fc5efd50c56a27d226643d0476c4fe6934de1a725bdd897dc73ac987e`; run root `.workspace/jsherp-jdt-luna-run.5Oqj9Y`; `four-entry-materials-run.log`; persisted Step 01–05 artifacts and current repository implementation/docs.
- Current branch/worktree: `codex/jsherp-jdt-luna-repository-run` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read repository, backend-agent, and source-code scoped instructions.
- Confirmed the worktree contains substantial pre-existing changes that must be preserved.
- Verified the failed run's published Step 05 contains 326 entry contexts: 4 valid `COLLECTED`
  contexts with code and 322 valid `NOT_COLLECTED / NOT_SELECTED_FOR_SAMPLE` contexts with both
  code and strict technical context null. The Capsule publication contains exactly the four
  collected entries.
- Traced the producer contract through `FlowCompilation`, `EntryContextAssembler`, the navigation
  module publisher, and Step 05 publication checks. `NOT_COLLECTED` requires a concrete reason and
  null `codeContext`; `strictTechnicalContext` is optional.
- Isolated the failure to `BusinessMaterialBuilder.entryContext`: it rejects both context forms
  being null without first interpreting the valid `NOT_COLLECTED` state.
- Confirmed there is no implemented public cross-run Step 01–05 reuse path. The maintenance
  continuation state is written only after a successful material build and is restricted to a
  `RUNNING` run; public `executeStep` currently reruns the complete technical executor for a newly
  queued run.

## Current state

- Diagnosis complete. The parent has the exact reader/coverage ruling and the absence of a current
  cross-run reuse seam.

## Changed files

- `progress/jdt-sample-material-diagnosis.md` only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing source, test, docs, plan, and progress changes observed; no files modified by this task before this progress file. |
| Bounded Step 05 `jq`/line-count inspection | PASS | 4 `COLLECTED` code contexts, 322 `NOT_COLLECTED / NOT_SELECTED_FOR_SAMPLE` null contexts, 4 Capsules, and 326 dispositions. |
| Static producer/consumer trace | PASS | Producer and publisher accept the persisted state; Builder line 1197 is the first contradictory check. |
| Static launcher/runtime reuse trace | PASS | No valid cross-run continuation exists in current Java; same-run continuation requires a state file and `RUNNING`, while public execution invokes the technical prefix. |

## Decisions

- Diagnosis is read-only. Do not run Maven, JDT, a Provider, or mutate an existing failed run.
- Do not propose reducing the discovered-entry denominator or redesigning recovery.
- The smallest correct reader fix is status-aware validation: `COLLECTED` requires matching code
  and null reason; `NOT_COLLECTED` requires a nonblank reason and null code while allowing optional
  strict context; unknown or contradictory states fail closed.
- A `NOT_COLLECTED` entry with neither context produces no material but remains in coverage as
  `NOT_MATERIALIZED` with its exact collection reason, not the generic `FLOW_NOT_COMPILED`.
- Since the real bounded scan took about seven minutes and no Provider request started, use a new
  four-entry materials-only run after the reader fix, then the existing saved-state sample/generate
  modes. Do not add cross-run recovery for this work unit.

## Blockers

- None for the reader fix. Reusing the terminal failed run without another scan is unsupported by
  the current public implementation and is deliberately not added for this run.

## Exact next action

- The implementation owner should apply the narrow Builder reader/coverage GREEN against the
  focused RED, run only its direct tests, and hand the fresh four-entry execution back to the run
  coordinator.

## Resume checks

- Re-read this file, confirm the named run artifacts still exist, and check `git status --short` before continuing.
