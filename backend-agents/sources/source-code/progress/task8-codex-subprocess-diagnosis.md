# Progress: Task 8 Codex subprocess diagnosis

- Status: COMPLETE
- Agent role: Primary implementation coordinator
- Model: GPT-5
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Diagnose the terminal Task 7 Java-to-Codex DRAFT failure without replaying its candidate.
  Add only an evidence-backed, non-secret diagnostic or execution fix; then, if the boundary is
  fixed, use a new candidate for the separately authorized four-entry Luna/high DRAFT and REVIEW.
- Approved inputs: User's 2026-09-12 authorization following the Task 7 terminal record; scoped
  `AGENTS.md`; frozen four-entry Task 7 material only for a new candidate after diagnosis.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed Task 7 login preflight succeeded but its started DRAFT failed before response with
  `CODEX_SUBSCRIPTION_EXECUTION_FAILED:UNKNOWN`; no retry or REVIEW occurred.
- Read prior bounded diagnostics and host-permission evidence. A direct no-customer Luna/high
  structured-output diagnostic completed from an empty temporary working directory under host
  permission, while Java/Maven-child provider executions fail before DRAFT output.
- Created a new candidate directory after the first host Maven startup stopped before a Provider
  invocation because its working directory was not preserved. The absolute-path invocation then
  completed one DRAFT and one REVIEW in 111.604 seconds.
- The reviewed result contains four activities, one each for E1 through E4; all four global entry
  coverages are `ANALYZED` and every activity uses only its matching S487/S722/S731/S898 reference.

## Current state

- Root cause was the local execution boundary: Codex needs permission to update its own local
  session-state runtime, which the normal Java/Maven sandbox denies. It does not need to write the
  customer repository, and the provider remains read-only for model-generated commands. The old
  adapter's private cleanup explains why Task 7 reported only `UNKNOWN`; it is not the cause.
  The host-session candidate demonstrates this diagnosis: it generated a complete four-entry
  Activity result without changing the material, prompt, model or Provider.

## Changed files

- `progress/task8-codex-subprocess-diagnosis.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/plans/coherent-code-context-implementation-plan.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Task 7 live selector | TERMINAL FAILURE | DRAFT had no response; provider category was `UNKNOWN`. |
| `codex login status` | PASS | Existing ChatGPT login is active. |
| `codex exec --help` | PASS | CLI 0.153.4 exposes the expected noninteractive flags. |
| Prior host-permission structured-output diagnostic | PASS | Same Luna/high profile returned schema-valid `READY` from an empty temporary directory. |
| First host Maven invocation | STOPPED_BEFORE_PROVIDER | Escalated environment ignored the worktree; Maven did not locate toolchains and no Provider started. |
| New host-session candidate | PASS | One DRAFT plus one REVIEW, 111.604 seconds, 4 reviewed activities and 4 `ANALYZED` entry coverages. |
| Human result inspection | PASS | E1–E4 have concrete business activities and matching refs; service internals, roles and session policy remain questions; no lifecycle claim. |

## Decisions

- The previous candidate is immutable. Diagnostics must not replay its input or use an API key.
- The minimal evidence-backed change is execution permission, not a provider or prompt rewrite.
  The new candidate will run Maven on the host only so Codex can manage its own session state;
  customer source remains read-only and the Codex sandbox stays read-only.
- This validates only local Activity interpretation. It does not validate a BusinessProcess, a
  report, a repository-wide run or an external side effect.

## Blockers

- None for this bounded diagnosis and activity candidate.

## Exact next action

- Preserve the ignored candidate output and the committed result record. Any process, report or
  repository-wide model work requires its own authorization and candidate scope.

## Resume checks

- Re-read this file, confirm the result directory contains exactly DRAFT input/response, REVIEW
  input/response and final Activity result, and do not treat it as a whole-repository result.
