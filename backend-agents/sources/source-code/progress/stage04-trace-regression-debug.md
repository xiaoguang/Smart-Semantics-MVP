# Progress: Stage 04 trace regression debug

- Status: COMPLETE
- Agent role: read-only debugging agent
- Model: gpt-5.6-sol / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Diagnose exactly two post-Trace hardening regressions without modifying production code or tests: `Stage04LedgerAddendumHardeningTest.missingWorkspaceSeriesLedger` unexpectedly validates true, and `Stage04TypedTraceTest` `GAP_QUESTION` fails in `CandidateTraceResolver.stage01GapProvenance` at `uniqueDirectByField`. Identify the exact root causes and minimal production corrections consistent with strict ledger validation and exact-but-optional question provenance.
- Approved inputs: Current local working tree under this GitHub Code Agent only; the two named tests and their directly related production sources. No network, live model, source refresh, capture, generation, freeze, package, deployment, or customer build. Generative product-content inventory: none.
- Current branch/worktree: `codex/github-code-design-walkthrough` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/github-code`

## Completed

- Read the repository, prototype, backend-agent, and GitHub Code Agent `AGENTS.md` files.
- Read the systematic-debugging and root-cause-tracing workflows.
- Captured the pre-investigation working-tree status and noted extensive pre-existing Stage 04 work.
- Traced `missingWorkspaceSeriesLedgerMakesOtherwiseCanonicalCandidateInvalid` to a stale test setup: `Stage04LedgerAddendumHardeningTest.java:53` calls `Stage04CandidateFixture.create(...).install()`, while `Stage04CandidateFixture.java:108-123` now persists the exact durable series/slot events before installing the Candidate. The fixture therefore no longer represents a missing-ledger workspace. Production validation is still strict at `CandidateValidationTrace.java:186-206`: every archived receipt must resolve to `LifecycleEventEvidence.VERIFIED`, otherwise `LIFECYCLE_EVENT_CLOSURE` fails. Minimal correction: change only that test setup to call `create(...)`, then install `fixture.bundle()` directly through `FilesystemCandidateStore` at `fixture.archiveWorkspace()` without constructing `CandidateSeriesLedger`; retain the existing invalid/failed-check assertions.
- Traced the `GAP_QUESTION` failure to an over-strong registry assumption in `CandidateTraceResolver.stage01GapProvenance`: Stage 01 emits five exact `questionTemplateKey` values at `ProvenFactCompiler.java:771-789`, but the frozen fixture question registry contains only exact key `ASK_MISSING_ROW_POLICY` at `Stage03Fixtures.java:293-297`. Requiring `uniqueDirectByField` for every Stage 01 gap rejects legitimate gaps whose question key is exact provenance but has no optional registry enrichment. Minimal production correction at `CandidateValidationTrace.java:1059-1101`: select with `optionalDirectByField`; always emit the exact Stage 01 `GAP_TO_QUESTION_TEMPLATE` hop; only when an exact registry entry exists validate `allowedGapReasonCodes` and `readerTemplateKey` and emit the registry/template hops. The current working file already contains this concurrent correction at lines 1067 and 1081-1091.
- Reported both exact findings and line guidance to `/root`; root confirmed the diagnosis matches the applied fixes.

## Current state

- Read-only diagnosis is complete. No production or test files were edited by this agent.
- Maven was not run: both `ps` and `pgrep` process inspection are unavailable in this sandbox, so the required “no active Maven process” precondition could not be established while Terra was concurrently fixing.
- A requested Luna/xhigh child dispatch for the isolated ledger test correction was attempted with no inherited history, but collaboration rejected it with `agent thread limit reached`; root was notified and confirmed the actual fix separately.

## Changed files

- `progress/stage04-trace-regression-debug.md` (owned progress state only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Recorded pre-existing modified/untracked Stage 04 work; no implementation ownership assumed. |
| `rg`/`nl` bounded source inspection of the two tests, fixture, validator, resolver, Stage01 gap compiler, and Stage03 registry fixture | PASS | Located the two first incorrect assumptions and exact correction seams. |
| `ps ...` / `pgrep ...` Maven-process checks | UNAVAILABLE | Sandbox denied process-list access; no Maven command was started. |
| `mvn ...` | NOT RUN | Required inactive-Maven precondition could not be verified during concurrent Terra work. |

## Decisions

- Preserve strict durable-ledger validation; repair the stale missing-ledger test setup rather than weakening production closure.
- Preserve Stage 01's exact question key as provenance while making frozen-registry enrichment optional and exact-match-only.
- Do not modify production or tests in this debugging slice.

## Blockers

- None for diagnosis. Test execution was intentionally omitted because Maven-process activity could not be inspected.

## Exact next action

- COMPLETE. Root/Terra should retain the two minimal corrections above and run only `Stage04LedgerAddendumHardeningTest` and `Stage04TypedTraceTest` after confirming no Maven process is active.

## Resume checks

- COMPLETE. Confirm this agent changed only `progress/stage04-trace-regression-debug.md`.
- Confirm no production or test files were edited by this debugging agent.
