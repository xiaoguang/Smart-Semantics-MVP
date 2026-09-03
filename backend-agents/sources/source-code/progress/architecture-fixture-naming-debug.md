# Progress: Architecture fixture naming debug

- Status: COMPLETE
- Agent role: Debug
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Diagnose and correct the ProgramGraphs architecture-test failure without weakening the forbidden-name gate.
- Approved inputs: `SourceAnalysisArchitectureTest`, scoped naming rules, current ProgramGraphs fixture set.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Reproduced `SourceAnalysisArchitectureTest`: 3 tests, 1 failure, reported forbidden `target`.
- Traced its data path: only project name, POM, Java package/declaration paths, and fixture directory paths enter `forbiddenTokens`.
- Verified project root and POM contain no forbidden `target` token.  Found four fixture directory names beginning `call-graph-target-`; the scanner correctly treats their path segment as an architectural violation.
- Renamed the four resources to `call-graph-resolution-*` and updated the direct CallGraph/AmbiguousHandoff fixture references.

## Current state

- Root cause is non-semantic fixture naming introduced with M2 call-resolution cases, not Maven build output or production source.  Rename them to `call-graph-resolution-*` and update only direct resource references; retain the strict scanner and its legacy-path negative tests.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/AmbiguousCallHandoffTest.java`
- `src/test/resources/analysis/graph/call-graph-resolution-ambiguous/`
- `src/test/resources/analysis/graph/call-graph-resolution-ambiguous-reversed/`
- `src/test/resources/analysis/graph/call-graph-resolution-null-reference-compatible/`
- `src/test/resources/analysis/graph/call-graph-resolution-null-primitive-incompatible/`
- `progress/architecture-fixture-naming-debug.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SourceAnalysisArchitectureTest test` | RED | 3 tests, one `target` forbidden-path failure |
| `find src/main/java src/test/java src/test/resources -print | rg 'target'` | Root cause | only `call-graph-target-*` fixture directories introduced the forbidden path token |
| `mvn -t .mvn/toolchains.xml -o -Dtest=SourceAnalysisArchitectureTest,CallGraphBuilderTest,AmbiguousCallHandoffTest test` | PASS | 22 tests; 0 failures, errors, or skips |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | formatted the touched DataFlow test only |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Do not weaken `SourceAnalysisArchitectureTest`: it is the authoritative wire-reset naming gate.

## Blockers

- None.

## Exact next action

- Include the strict naming-gate result in the ProgramGraphs delivery verification.

## Resume checks

- Re-read this file; use `find ... | rg target` and rerun the architecture selector.
