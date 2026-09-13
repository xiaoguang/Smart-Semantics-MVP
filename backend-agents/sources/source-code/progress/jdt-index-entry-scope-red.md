# Progress: jdt-index-entry-scope-red

- Status: IN_PROGRESS
- Agent role: Bounded public-seam regression tests for JDT Java-code-index entry scoping
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Tests only; add two public publish/fresh-reopen regressions for shared physical calls and source-syntax conflicts.
- Approved inputs: Existing source-code test fixtures and the user-authorized frozen JDT/Luna design context; no new model/source runs.
- Current branch/worktree: Shared source-code worktree at /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read scoped AGENTS.md and TDD/test-quality guidance.
- Confirmed the existing JavaCodeIndexPublicationSpecifierTest uses the public ProgramGraphsPublicFixture pipeline.

## Current state

- Existing publisher globally deduplicates CALL records by physical callKey, while entry memberships retain callKeys. New tests establish entry-local context preservation and reject true same-physical-source syntax conflicts. Initial fixture RED was corrected after discovering the included target needed a declared formal parameter for its association.

## Changed files

- progress/jdt-index-entry-scope-red.md (owned progress)
- src/test/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexPublicationSpecifierTest.java
- src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java (mechanical v2 policy alignment for the in-flight production schema)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing worktree changes preserved; no owned test changes yet. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexPublicationSpecifierTest test` | RED (fixture) | Initial runtime reached the seam but failed before publication because included target association lacked a target parameter; corrected in test fixture. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexPublicationSpecifierTest test` | RED (precise) | 3 tests run; 1 error only in `preservesEntryLocalCallProjectionsWhenPhysicalCallIsShared`, at `JavaCodeIndexPublicationSpecifier.putSame` line 272/437 with `JAVA_CODE_INDEX_INVALID`; conflict test passed. |
| `git diff --check` | PASS | No whitespace errors in owned test/progress edits. |

## Decisions

- Extend JavaCodeIndexPublicationSpecifierTest and its fakeSession public fixture rather than using reflection or direct private method calls.
- Assert reader-visible EntryCodeContext projections, not serialized wire internals.

## Blockers

- None currently.

## Exact next action

- Handoff precise RED and owned test changes to the parent agent; keep the selector available for post-GREEN verification after production implementation.

## Resume checks

- Re-read this file, run `git status --short`, and inspect the selector output before any additional edit.
