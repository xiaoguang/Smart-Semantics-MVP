# Progress: Stage 04 fifth audit archive trace tests

- Status: COMPLETE
- Agent role: Stage 04 fifth-audit test implementer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add exactly one Stage04FifthAuditArchiveTraceTest class with at most four adversarial tests for coherent archived model/receipt rewrites and self-describing persisted typed trace records.
- Approved inputs: Stage 04 implementation, existing test fixtures/helpers, `docs/stages/04-runtime-archive-trace-recovery.md`, and `progress/stage04-final-acceptance-review.md`. No production, design, existing-test, source, generation, network, or live-model changes.
- Current branch/worktree: codex/github-code-design-walkthrough / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/github-code`

## Completed

- Read the complete applicable `AGENTS.md` and the required Stage 04 final acceptance review.
- Recorded the pre-existing shared worktree state before edits.
- Created this progress record before modifying tests.

## Current state

- Added the single requested test class with exactly one `@Test` covering isolated flowSliceId, evidenceCapsuleId, and round rewrites.
- The test recomputes model/receipt roots, candidate content/address identity, and archive manifest before validation.
- The first mutation reaches the final validation assertion and fails because the current validator accepts the coherent duplicate rewrite; later field cases remain in the same test and will execute after the production correction.
- Parent requested one additional `@Test` for persisted typed-Trace self-description; mutation coverage was explicitly deferred for this bounded RED slice.
- Added exactly one additional `@Test` for a representative persisted `FLOW_TECHNICAL_DISPLAY` record, requiring `fallbackSubtype`, `policyId`, `templateKey`, `taskSpecId`, `resolutionOrder`, and `anchorKey`.
- Narrowed the representative test to the existing fallback fixture via reflection; it reads only `trace.jsonl`, selects a `TECHNICAL_FALLBACK` record with the `reader:technical:` key, and does not invoke resolver/TraceView or mutate artifacts.

## Changed files

- `progress/stage04-fifth-audit-archive-trace-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04FifthAuditArchiveTraceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing Stage 04 implementation/test and documentation changes recorded; no owned test class existed yet. |
| `mvn -q -Dtest=Stage04FifthAuditArchiveTraceTest test` | EXPECTED RED | 2 tests run, 2 assertion failures, 0 errors, 0 skipped. First test: `flowSliceId duplicate rewrite must fail nested task closure` (`expected false, was true`). Second test: persisted `fallbackSubtype` is absent (`expected FLOW_TECHNICAL_DISPLAY, was empty`). |
| `git diff --check` | PASS | No whitespace diagnostics for tracked changes. |

## Decisions

- Keep all requested coverage in one new class and use existing fixture/mutation helpers where possible.
- Preserve the repository test scope by running only this class.

## Blockers

- None.

## Exact next action

- Parent production owner consumes this clean two-failure RED selector and persists the requested task-bound duplicate checks and flow-fallback trace refs.

## Resume checks

- Re-read this progress file, run `git status --short`, and confirm only this progress file and the new test class are owned changes.
