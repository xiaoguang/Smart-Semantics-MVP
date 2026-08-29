# Progress: phase2-codefact-tests

- Status: COMPLETE
- Agent role: Phase 2 CodeFact/ConditionFact TDD test author
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add only source-only Phase 2 tests/fixtures under `src/test/**` and this progress record. Preserve the intentional RED handoff for the CodeFact analysis seam.
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; Phase 1 and MVP progress records; current source/test contracts; local synthetic Java fixtures only. No symbol solver, network, customer source, customer build, POM/main/docs/model changes.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; target Maven directory is shared.

## Completed

- Read scoped repository rules, progress template, Phase 1/MVP progress records, DESIGN contracts for symbols/CFG/CodeFact/conditions/mutation, POM, and current Java source/test surfaces.
- Confirmed Phase 1 currently exposes source-only direct-call discovery but does not expose CodeFacts, ConditionFacts, source locators on call facts, or locator-level unresolved gaps.
- Created this progress record before modifying test/fixture state.
- Added a synthetic exact-call chain, ambiguous receiver fixture, guard-literal mutation fixture, and three focused tests against the proposed Phase 2 seam.
- Ran the focused selector and confirmed an intentional compile-time RED caused only by the absent Phase 2 production seam.

## Current state

Phase 2 tests are written first against a small proposed source-only analysis seam. They require exact direct-call CodeFacts and guard ConditionFacts with source locators, and fail closed with an explicit `UNRESOLVED` Gap when a call receiver field maps to zero or multiple declared types. The guard-literal mutation changes the condition fact identity/payload and leaves no old condition fact admitted. Production Phase 2 types remain intentionally absent for the RED handoff.

## Changed files

- progress/phase2-codefact-tests.md
- src/test/java/com/linguan/codemd/analysis/CodeFactAnalyzerTest.java
- src/test/java/com/linguan/codemd/analysis/CodeFactFixtures.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated root changes preserved; no scoped Phase 2 test/fixture files existed before this task. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeFactAnalyzerTest test` | RED (expected) | Main sources compile; test compilation fails only on the absent Phase 2 seam: `AnalysisResult`, `CodeFact`, `Gap`, `ConditionFact`, `AnalysisRequest`, and `SourceAnalyzer`. |

## Decisions

- Keep fixtures as temporary source trees created by tests; do not compile or execute them and do not add checked-in generated artifacts.
- Proposed test seam is package `com.linguan.codemd.analysis`: `SourceAnalyzer.analyze(AnalysisRequest) -> AnalysisResult`, with `CodeFact`, `ConditionFact`, `Gap`, `SourceLocator`, and `FactKind`/`Resolution` value types. The seam is intentionally source-only and implementation may reuse Phase 1 parsing without a symbol solver.
- Treat exact binding as a first-class assertion: only a uniquely declared field receiver produces an `EXACT` call fact; unresolved/ambiguous field type produces no call fact and one `UNRESOLVED` gap carrying the call-site locator.
- Make the mutation assertion compare canonical fact payloads rather than unstable object identity or output ordering.

## Blockers

- Production Phase 2 analysis types are intentionally absent until the implementation handoff; this is the expected RED condition, not a test-author blocker.

## Exact next action

Hand the tests to the Phase 2 implementation owner. Implement the exact seam named by the compile-time errors, then rerun only `CodeFactAnalyzerTest` under Java 17.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Confirm only this progress file and `src/test/**` are changed by this task.
