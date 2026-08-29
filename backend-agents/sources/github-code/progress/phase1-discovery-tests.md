# Progress: phase1-discovery-tests

- Status: COMPLETE
- Agent role: Phase 1 automatic MVC/MyBatis discovery TDD test author
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add only synthetic Phase 1 discovery fixtures/tests under `src/test/**` and this progress record. Preserve the intentional RED handoff for the production discovery seam.
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; `progress/phase1-discovery.md`; all MVP progress records; `pom.xml`; current Java main/test sources; local synthetic source strings only. No network, customer source, model call, or customer build.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; target Maven directory is shared and untracked.

## Completed

- Read the root and scoped repository rules, progress template, Phase 1/MVP progress records, POM, and all current Java production/test sources.
- Confirmed the Phase 1 contract: deterministic Spring MVC route discovery, direct service edge, unique MyBatis namespace/id binding, static UPDATE facts, and explicit gaps for dynamic SQL or ambiguous binding.
- Created this progress file before modifying tests.
- Added a four-source-file static fixture and a four-source-file dynamic-SQL fixture in `RepositoryDiscovererTest`.
- Added RED contract assertions for route/method locators, both direct-call edges in the chain, unique mapper bindings, static UPDATE facts, stable ordering, repeat equivalence, and `DYNAMIC_SQL_UNRESOLVED`.
- Ran the focused Java 17 Maven selector; dependency resolution stopped before test compilation because the approved `javaparser-core:3.28.2` artifact is absent and the Maven local repository cannot create its tracking directory.

## Current state

The strict discovery contract tests are written test-first against a proposed `RepositoryDiscoverer.discover(DiscoveryRequest)` seam and immutable `DiscoveryResult` value objects. Production discovery types are intentionally absent; once the approved JavaParser artifact is available, the focused selector should remain RED for that reason.

## Changed files

- progress/phase1-discovery-tests.md
- src/test/java/com/linguan/codemd/discovery/RepositoryDiscovererTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated root changes and shared target changes preserved. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=RepositoryDiscovererTest test` | BLOCKED | Maven could not create `/Users/yexiaoguang/.m2/repository/com/github/javaparser` (`Operation not permitted`) while resolving the approved JavaParser artifact. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest test` | BLOCKED | Offline Maven confirmed `javaparser-core:3.28.2` has not been downloaded; tests did not compile or execute. |

## Decisions

- Use a temporary synthetic repository with exactly four Java/XML source files: one annotated controller, one service, one mapper interface, and one mapper XML.
- Keep expected values hand-derived and assert stable ordering, route/method locators, direct call edge, unique mapper binding, and static UPDATE table/field/value facts.
- Add a second four-file fixture containing both `<if>` and `${}` dynamic SQL; require a `DYNAMIC_SQL_UNRESOLVED` gap and no dynamic SQL fact admitted. Ambiguous binding remains an equivalent production gap code, but is not fabricated by this fixture.
- Keep all fixture files as test inputs. Tests do not call JavaParser directly; the production seam owns source parsing while the test remains independent of parser implementation details.

## Blockers

- Production discovery seam is intentionally absent until the implementation handoff.
- The focused selector is blocked before compilation because the approved `javaparser-core:3.28.2` artifact is not present in the local Maven cache and `.m2` is not writable. No workaround or alternate dependency was used.

## Exact next action

Hand the tests to the coordinator with the exact seam and the pre-compilation JavaParser environment blocker; after the authorized artifact becomes available, rerun only `RepositoryDiscovererTest` with Java 17 to observe the intended missing-production-seam RED.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Confirm no production/POM/docs/agent files changed before handing the RED tests to the coordinator.
