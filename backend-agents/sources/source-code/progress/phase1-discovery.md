# Progress: phase1-discovery

- Status: IN_PROGRESS
- Agent role: Phase 1 automatic MVC/MyBatis discovery coordinator
- Model: gpt-5.6-sol (ultra)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Begin the approved Phase 1 automatic entry, direct-call and static MyBatis SQL discovery using only synthetic fixtures.
- Approved inputs: User-approved `javaparser-core:3.28.2`; existing MVP source and tests; no customer build, application execution, live model, or unapproved source snapshot.
- Current branch/worktree: /Users/yexiaoguang/Documents/ErpMock on codex/rag-frontend-phase-one; target Maven directory is untracked and intentionally implemented in place.

## Completed

- Recorded this Phase 1 boundary before changing the Maven dependency graph.
- Added the user-approved fixed JavaParser Core dependency declaration.
- Downloaded that exact dependency from Maven Central under the approved
  Phase 1 scope; no other analyzer dependency was introduced.
- Integrated the JavaParser/JDK-XML discovery core after RED-to-GREEN
  synthetic verification and serial MVP regression.
- Integrated fresh-process `inspect` and `discover` JSON CLI commands after
  their Phase 1 test-first implementation.

## Current state

The parser-only discovery core and its CLI are verified with small synthetic
Spring MVC → direct service → MyBatis XML fixtures. They are not yet connected
to a real locked source snapshot or to automatic Markdown Flow Manifest
compilation.

## Changed files

- pom.xml
- progress/phase1-discovery.md
- src/main/java/com/linguan/codemd/discovery/
- src/test/java/com/linguan/codemd/discovery/RepositoryDiscovererTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated root changes remain preserved |
| `mvn -Dtest=RepositoryDiscovererTest test` | PASS | 2 synthetic discovery tests after the expected missing-production RED stage |
| existing 14 MVP test selector | PASS | 14 tests, 0 failures, 0 errors after discovery integration |
| complete selected MVP + Phase 1 selector | PASS | 19 tests, 0 failures, 0 errors under Java 17 offline Maven |

## Decisions

- Phase 1 recognizes only declarative Spring MVC HTTP routes, direct Java method calls, and unique MyBatis namespace/method/static SQL bindings.
- Dynamic SQL, multiple binding candidates, reflection, JPA and unsupported constructs produce a Gap rather than an inferred fact.

## Blockers

- The locked real jshERP commit remains unavailable due the recorded DNS failure; Phase 1 uses synthetic fixtures until that source identity can be captured.

## Exact next action

Add a manual-flow Manifest compiler to connect supported discovered facts to the
existing nine-section candidate pipeline, but only after the locked real source
can be captured or the user authorizes a different frozen identity.

## Resume checks

- Read this file, AGENTS.md and mvp-integration.md.
- Run git status --short from the repository root.
- Confirm the JavaParser dependency version remains fixed at 3.28.2.
