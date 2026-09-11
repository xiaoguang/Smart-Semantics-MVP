# Progress: Codex subprocess health check

- Status: COMPLETE
- Agent role: Primary diagnostic agent
- Model: gpt-5.6-luna / high only if the non-source health check reaches the service
- Started: 2026-09-11 16:05 UTC
- Last updated: 2026-09-11 16:18 UTC
- Scope: Diagnose the Java-to-Codex process boundary after one terminally failed customer-material request. The only permitted live action is one tiny, source-free structured-output health check; it contains no repository code, packet, path, user data or business instruction. It is not a retry of the DepotHead material.
- Approved inputs: Existing user authorization for Luna/high product interpretation; local CLI help and login status; minimal JSON Schema below.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Confirmed the desktop CLI version, login state and supported `exec` flags agree with the Java adapter.
- Ran the one source-free health-check command inside the standard sandbox. It failed before any model request because the desktop Codex subprocess needs to open `/Users/yexiaoguang/.codex/state_5.sqlite` for writing, but the sandbox exposed that database read-only. The command also used a work-directory-relative Schema path in this manual diagnostic; the production adapter already passes an absolute Schema path, so that second issue does not explain the Java failure.
- Repeated only the source-free health check with narrowly approved state-directory access and absolute Schema/output paths. Luna/high returned the required `{ "status": "ok" }` JSON. The command initialized an ephemeral session successfully, so the actual root cause is confirmed as the standard sandbox's state-database write restriction.

## Current state

- Root cause is resolved for future permitted executions: the Codex subprocess must run with access to its existing state database. The failed DepotHead execution remains terminal and will not be replayed automatically.

## Changed files

- progress/codex-subprocess-healthcheck.md
- .workspace/codex-subprocess-healthcheck/response-schema.json

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `codex --version`, `login status`, `exec --help` | PASS | CLI 0.153.4, ChatGPT login, adapter flags recognized. |
| source-free ephemeral health check in default sandbox | BLOCKED_BEFORE_MODEL | Codex reports `state_5.sqlite` is readonly; no source packet or model output was sent/received. |
| escalated source-free ephemeral health check | PASS | Luna/high returned schema-valid `{ "status": "ok" }`; state initialization and structured output both work. |

## Decisions

- Limit this diagnostic to one structured `{ "status": "ok" }` response. A failure is recorded once and does not trigger a loop or a second model request.

## Blockers

- None for future permitted executions. The prior DepotHead request stays failed and is not retried.

## Exact next action

- Select a distinct automatic material package for the next declared product sample; preserve the DepotHead failure receipt and do not reuse it as a successful result.

## Resume checks

- Read this file, verify the DepotHead request remains failed without retry, and retain the state-directory escalation when executing later explicitly declared product samples.
