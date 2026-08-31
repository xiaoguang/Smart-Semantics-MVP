# Progress: Stage 04 residual P1 hardening

- Status: COMPLETE
- Agent role: Stage 04 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Close coherent receipt/model/root/manifest rewrite admission and source/ledger pre-allocation byte limits. Production code and this progress file only.
- Approved inputs: scoped `AGENTS.md`; final review; residual P1 RED tests; existing archive, candidate validation, source registry, and ledger production seams.
- Current branch/worktree: Shared dirty worktree; preserve unrelated and parallel changes.

## Completed

- Created this owned record before production edits.
- Read scoped implementation and TDD instructions and recorded the shared dirty worktree.
- Reproduced the direct selector: 4 tests, 2 failures. The receipt/root closure and source/ledger resource paths were independently failing.
- Closed the resource slice: Stage01 streams declared files with the applicable remaining byte budget before buffering; Stage04 has one no-follow, stat-before-read, hard-capped reader for Candidate archive/sidecar, registration, and persisted ledger records. The direct selector now has only the coherent receipt/root failure.
- Closed the identity slice: candidate content now commits full `model-rounds.jsonl` and `generation-receipts.jsonl` roots; archive validation recomputes candidate content/candidate lineage IDs and the exact model-round/generation-receipt formulas, including task runtime, observed runtime, started event ID/ordinal, and started receipt linkage.

## Current state

- Complete. Direct RED and requested regression selectors are GREEN; whitespace validation is clean.

## Changed files

- `src/main/java/com/linguan/codemd/stage01/SnapshotVerifier.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage04/FilesystemSourceRegistry.java`
- `src/main/java/com/linguan/codemd/stage04/LifecycleProviderBridge.java`
- `progress/stage04-residual-p1-core.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ResidualP1Test test` | RED | Initial: 4 tests, 2 failures (receipt closure and source/ledger resource limits). |
| `mvn -Dtest=Stage04ResidualP1Test test` | RED | After resource slice: 4 tests, 1 failure, 0 errors; only coherent receipt/root closure remains. |
| `mvn -Dtest=Stage04ResidualP1Test test` | PASS | 4 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04ValidationHardeningTest,Stage04ArchiveV2Test,Stage04LifecycleTranscriptTest,Stage04PersistedRecoveryTest,Stage04SecurityTest,VerifiedSnapshotContractTest test` | PASS | 44 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Reuse one stat/`NOFOLLOW`/hard-cap reader across Candidate, source-registration, and ledger JSON rather than adding fixture-specific allocation guards.
- Keep lifecycle evidence honest: the current archive has no durable event transcript for in-memory fixtures, so validation will verify exact receipt material (including event ID/ordinal) and runtime/model linkage via the generation formula rather than asserting nonexistent persisted events.

## Blockers

- None.

## Exact next action

- None; slice is complete.

## Resume checks

- Re-read this record, retain production-only scope, and rerun the direct selector before completion.
