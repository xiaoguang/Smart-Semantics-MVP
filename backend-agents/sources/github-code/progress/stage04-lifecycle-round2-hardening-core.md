# Progress: Stage 04 lifecycle and Round-2 hardening

- Status: COMPLETE
- Agent role: Stage 04 production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Close public prestart retry, installed-Candidate recovery, unaffected-Flow Round-2 reuse, and typed fatal-addendum validation. Production code and this progress file only.
- Approved inputs: scoped `AGENTS.md`; Stage 04 design sections 4.1–4.2 and lifecycle rules; final implementation review; direct hardening RED test and existing lifecycle, recovery, review, Round-2 and public seams.
- Current branch/worktree: Shared dirty worktree; preserve unrelated and parallel changes.

## Completed

- Created this owned record before production edits.
- Read the current review, Stage 04 lifecycle/Round-2 design, direct RED test, and existing public production seams.

## Current state

- Reproduced all four RED assertions: retry/recovery conflicts, no unaffected-Flow reuse, and arbitrary addendum acceptance.
- The first vertical slice is GREEN: a same-request slot now accepts only a complete persisted no-start sequence for retry, and a `STARTED_CONSUMED` slot discovers the installed Candidate, revalidates it without Provider execution, folds `RECOVERED_COMPLETION`, or terminalizes conservatively.
- Fatal findings now require an explicit `CorrectiveAddendumStore` resolution. Its immutable content is only the exact parent/receipt/finding/correction tuple; a five-argument Agent has no store and therefore fails closed rather than treating a caller string as evidence.
- The Round-2 path now passes exact parent canonical rounds and their real lifecycle transcript only for unaffected Flows. Stage 03 replays those tasks/semantic responses and calls the provider only for overlay flows; archive assembly retains the copied model-round and receipt records.
- The complete hardening selector and specified lifecycle/Round-2/public regression set are GREEN. A one-Flow Round-2 intentionally has no reusable pair and retains the normal finite provider path.

## Changed files

- `progress/stage04-lifecycle-round2-hardening-core.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/DefaultCodeToMarkdownAgent.java`
- `src/main/java/com/linguan/codemd/stage04/LifecycleProviderBridge.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java`
- `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendumStore.java`
- `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendum.java`
- `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendumDirective.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java` (narrow canonical-round reuse overload)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest test` | RED | 4 tests, 4 failures, 0 errors. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest#freshAgentMayRetryTheSameRequestAfterACompleteNoStartPreflight+freshAgentRecoversAnInstalledCandidateWhenCompletionEventIsMissingWithoutProviderCalls test` | GREEN | 2 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest#fatalFindingRejectsAnArbitraryAddendumBeforeRoundTwoProviderExecution test` | GREEN | 1 test, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest#roundTwoCallsProviderOnlyForFlowWithFindingAndReusesUnaffectedFlowRoundsByteForByte test` | GREEN | 1 test, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04LifecycleRound2HardeningTest test` | GREEN | 4 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04LifecycleProviderTest,Stage04PersistedRecoveryTest,Stage04Round2Test,Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04PublicCoreTest test` | GREEN | 25 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Fatal findings must fail closed without an explicit immutable addendum resolution seam; arbitrary string identifiers are not evidence.

## Blockers

- None.

## Exact next action

- Complete.

## Resume checks

- Re-read this file, retain only Stage 04 production scope, and rerun the direct selector before claiming any behavior is green.
