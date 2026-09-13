# Progress: repository run journal RED

- Status: COMPLETE
- Agent role: Bounded run-local Provider journal RED test
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Specify one direct public-wrapper test for exact-request journaling around the configured structured Provider.
- Approved inputs: `AGENTS.md`, `progress/jsherp-jdt-luna-repository-run.md`, and root's confirmed `RunJournalStructuredProvider` seam.
- Current branch/worktree: `codex/jsherp-jdt-luna-repository-run` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the current scoped instructions and whole-repository run progress.
- Coordinated the exact public seam with root: `org.sourceanalysis.app.adapter.cli.RunJournalStructuredProvider` implements `StructuredModelProvider`; constructor `(Path journalDirectory, ModelRuntimeIdentityV1 expectedRuntimeIdentity, StructuredModelProvider delegate)`.
- Confirmed the journal contract: persist the exact request before delegation, persist completed canonical response and actual runtime identity afterward, reuse only an exact completed request plus expected runtime identity, and reject incomplete or mismatched records without a delegate call.
- Confirmed the direct test is present and self-contained: one temporary journal directory per
  lifecycle case, in-memory `StructuredModelRequest`/`StructuredModelResponse` fixtures, and
  delegate call counters for every replay decision.

## Current state

- The production wrapper has now been confirmed by the implementation owner against this contract.
  The test uses in-memory request/response fixtures and temporary journal directories, with no
  model or subprocess calls.
- The exact test selector for the implementation handoff is
  `mvn -t .mvn/toolchains.xml -o -Dtest=RunJournalStructuredProviderTest test`; the implementation
  owner reports one test with zero failures and zero errors.

## Changed files

- `progress/repository-run-journal-red.md`
- `src/test/java/org/sourceanalysis/app/adapter/cli/RunJournalStructuredProviderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=RunJournalStructuredProviderTest test` | PASS (implementation-owner handoff) | 1 test, 0 failures, 0 errors; no model, subprocess, or customer-source activity. |
| Direct test contract review | COMPLETE | Constructor and method expected: `(Path, ModelRuntimeIdentityV1, StructuredModelProvider)` and `generate(StructuredModelRequest)`. |

## Decisions

- Keep one direct wrapper test method covering first execution, fresh-wrapper exact reuse, changed request/identity non-reuse, and started-without-completion rejection.
- Use reflection for the new wrapper type so this RED remains a test-only compile path if implementation is absent or changes independently.
- Do not introduce recovery state machines, retries, provider substitutes, live calls, or output changes.

## Blockers

- None for the journal contract. This session did not rerun Maven; the implementation owner supplied
  the exact targeted result above.

## Exact next action

- Preserve the single reflection-based journal contract test and its targeted GREEN result; no
  recovery subsystem or broader journal tests are authorized.

## Resume checks

- Read this file, preserve all existing launcher/bootstrap changes, and wait for root before running any Maven selector.
