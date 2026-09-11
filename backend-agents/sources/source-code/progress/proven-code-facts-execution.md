# Progress: proven code facts execution

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add the one production execution seam that composes the existing persisted Fact candidate, Proof, and publication modules. It must consume only typed Step 01–03 references and verified source text; it must not add business interpretation or modify graph algorithms.
- Approved inputs: Active `docs/analysis-steps/04-proven-code-facts.md`; existing canonical stores and public persisted graph fixture.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the full M1–M3 Fact sequence exists only as manual composition in the fixed-repository acceptance test; the public modules themselves are already persisted and independently validated.
- Added a public-seam RED using the existing two-entry persisted graph fixture.
- Implemented `ProvenCodeFactsExecutor`. It owns only M1→M2→M3 order, reopens the existing persisted inputs, and uses the established frozen-Java Fact/Proof registries; it does not change any graph, Fact, Proof, or business-semantics rule.
- Verified its Step 04 output reopens with exactly `fact-accounting.json`, `gap-ledger.json`, `proof-pack.json`, and `proven-facts.json`.

## Current state

- The Step 04 M1–M3 production ordering seam is closed. The global queued run still needs a safe input resolver and subsequent Step 05 composition; those are separate runtime work.

## Changed files

- `progress/proven-code-facts-execution.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/ProvenCodeFactsExecutionTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/ProvenCodeFactsExecutor.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ProvenCodeFactsExecutionTest test` | RED | 1 test, 1 expected assertion failure: no `ProvenCodeFactsExecutor`; 0 errors/skips. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ProvenCodeFactsExecutionTest,FactCandidateEnumeratorTest,AtomicProofBuilderTest test` | PASS | 8 tests, 0 failures/errors/skips. |
| Scoped Spotless + `git diff --check` | PASS | The two new Java files are formatted; repository-wide Spotless still reports 59 pre-existing dirty files outside this work unit. |

## Decisions

- The executor will use the existing frozen-Java Fact and Proof registries. It will not accept arbitrary caller-built graph drafts, source paths, Fact templates, or business labels.

## Blockers

- None.

## Exact next action

- Add the same thin execution seam for existing Step 05 Flow/Capsule modules, then use both executors in the future global runtime.

## Resume checks

- Reopen the fixture's Step 01–03 publications and verify the executor returns the four-file Step 04 publication before extending the global runtime.
