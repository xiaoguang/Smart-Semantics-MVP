# Progress: phase2-codefact-core

- Status: COMPLETE
- Agent role: Phase 2 CodeFact analysis implementation owner
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add only the Phase 2 source-only analysis seam in `src/main/java/com/linguan/codemd/analysis/**` and this progress record. No tests, POM, design/docs, customer source, model, network, or solver work.
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; Phase 2 RED tests and fixtures; current Phase 1 discovery implementation and progress records; local synthetic Java fixture trees only.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; target Maven directory is shared.

## Completed

- Read scoped instructions, progress template, and the Phase 2 RED contract.
- Created this progress record before modifying Phase 2 production state.
- Reproduced the intentional RED baseline, where only the requested Phase 2 seam types were absent.
- Added the exact `SourceAnalyzer.analyze(AnalysisRequest) -> AnalysisResult` seam and immutable analysis records.
- Implemented source-only JavaParser analysis for declared type lookup, uniquely resolved field-receiver calls, `if` condition facts, deterministic ordering, SHA-256 fact IDs, and explicit unresolved call gaps.
- Corrected the condition source locator to the guard expression node after the direct contract exposed the full-statement range mismatch.
- Completed fresh focused verification, selected serial Phase 1/MVP regression, and the scoped whitespace/status audit.

## Current state

Phase 2 CodeFact analysis is complete. The production surface is limited to the requested analysis package and reuses the existing `discovery.SourceLocator` value type. No fixture is compiled or executed, and the analyzer has no solver, model, network, or customer-source dependency.

## Changed files

- progress/phase2-codefact-core.md
- src/main/java/com/linguan/codemd/analysis/AnalysisRequest.java
- src/main/java/com/linguan/codemd/analysis/AnalysisResult.java
- src/main/java/com/linguan/codemd/analysis/CodeFact.java
- src/main/java/com/linguan/codemd/analysis/ConditionFact.java
- src/main/java/com/linguan/codemd/analysis/Gap.java
- src/main/java/com/linguan/codemd/analysis/SourceAnalyzer.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated worktree changes are preserved. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeFactAnalyzerTest test` | RED (expected) | Test compilation fails only for absent `AnalysisRequest`, `AnalysisResult`, `SourceAnalyzer`, `CodeFact`, `ConditionFact`, and `Gap`. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeFactAnalyzerTest test` | FAIL (diagnosed) | 3 tests executed; only condition locator was lines 5–7 instead of required guard line 5. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeFactAnalyzerTest test` | PASS | 3 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest,CodeMdCliDiscoveryTest,ManifestEvidenceVerificationTest,NineSectionRenderingDeterminismTest test` | PASS | 9 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeFactAnalyzerTest test` | PASS | Fresh final focused run: 3 tests run; 0 failures, 0 errors. |
| `git status --short -- src/main/java/com/linguan/codemd/analysis progress/phase2-codefact-core.md` | PASS | Scoped changes are only this progress file and the new Phase 2 analysis package. |
| `rg -n '[[:blank:]]+$' src/main/java/com/linguan/codemd/analysis progress/phase2-codefact-core.md` | PASS | No trailing whitespace found. |

## Decisions

- Preserve the test seam exactly: `SourceAnalyzer.analyze(AnalysisRequest) -> AnalysisResult`.
- Build on JavaParser source syntax only; never compile fixtures, resolve symbols, or infer an ambiguous type.
- Reuse the existing public `discovery.SourceLocator` contract rather than create an incompatible Phase 2 locator type.
- Use only explicit source declarations, package/import syntax, and the safe simple-name fallback when no direct source binding exists; multiple candidates remain a gap.
- Canonical fact IDs are `fact:` plus SHA-256 of the canonical fact payload, so a changed guard literal changes the condition fact identity and no mutable state can retain the prior fact.

## Blockers

- None.

## Exact next action

Hand this completed implementation to the Phase 2 coordinator for integration with the Phase 2 RED tests.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Confirm modifications remain limited to this progress file and `src/main/java/com/linguan/codemd/analysis/**`.
