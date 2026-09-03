# Progress: M2 null and primitive compatibility tests

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add the bounded M2 public-seam tests for null compatibility with reference overloads and primitive-only targets.
- Approved inputs: scoped AGENTS.md; published M2 candidate-set design; current CallGraphBuilderTest and M2 ambiguity implementation.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Added two bounded public-seam cases to `CallGraphBuilderTest`: a reference/primitive overload pair where `null` selects the reference overload, and a primitive-only overload where `null` produces a target-unresolved Gap.
- Added independent frozen Java/XML fixtures for both cases.

## Current state

The test cases and fixtures are complete. Two initial test-only issues were corrected: graph-wide edge cardinality was narrowed to the AuditClient target, and a non-Java `Stream.single()` call was replaced with AssertJ cardinality plus indexed access. The fixture and production path are GREEN.

## Changed files

- progress/m2-null-primitive-compatibility-tests.md
- src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java
- src/test/resources/analysis/graph/call-graph-target-null-reference-compatible/
- src/test/resources/analysis/graph/call-graph-target-null-primitive-incompatible/

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED (test assertion) | 14 tests; the two new cases reached the intended overload behavior, but assertions incorrectly expected the whole graph to contain only the target call. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless formatted the changed test and concurrently touched already-active graph files; those shared changes are not logically part of this test slice. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED (test assertion) | 14 tests; the null/primitive assertions passed, but the reference return assertion selected the controller call site instead of the AuditClient call site. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED (test compile) | The corrected return pairing used `Stream.single()`, unavailable on Java 17. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 14 tests, 0 failures, 0 errors, 0 skipped; both null/reference and null/primitive cases pass. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep scope limited to direct `null` compatibility: reference formals are compatible; primitive formals are not.
- Do not add tests for widening, boxing, varargs, inheritance, generics, or multiple call sites.

## Blockers

## Exact next action

No further action for this bounded test slice. Parent should review the test-only changes and fold them into the current M2 work unit.

## Resume checks

- Do not modify production, design, POM, or another agent's progress.
- Preserve the existing M2 overload ambiguity test and its canonical gap behavior.
- Final selector result is GREEN; no production or design change was made by this task.
