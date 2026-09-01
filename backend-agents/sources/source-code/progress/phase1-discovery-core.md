# Progress: phase1-discovery-core

- Status: COMPLETE
- Agent role: Phase 1 Java/Spring MVC/MyBatis discovery production implementer
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Implement only the deterministic offline Phase 1 discovery API under `src/main/java/**`, plus this progress record.
- Approved inputs: Existing synthetic `RepositoryDiscovererTest`, JavaParser Core 3.28.2, JDK XML parser, and the local source strings created by tests. No network, frozen customer-source read, model invocation, or customer Maven execution.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; the target Maven directory is shared and untracked.

## Completed

- Read the applicable repository and source-agent instructions, Phase 1 progress/test handoffs, POM, and current Java production/test sources.
- Confirmed the only accepted public package contract is `com.linguan.codemd.discovery`, as imported directly by `RepositoryDiscovererTest`.
- Ran the focused Java 17 selector in Maven offline mode. It reached test compilation and failed only because the intended production discovery API types are absent.
- Added the public discovery records and `RepositoryDiscoverer` under `src/main/java/com/linguan/codemd/discovery`.
- Reached GREEN for the focused discovery contract: both static and dynamic synthetic fixtures pass.
- Investigated the one post-implementation test failure before fixing it. JavaParser ranges were absent because token storage had been disabled; retaining parser tokens restores the exact method source ranges required by the contract.
- Completed the required serial regression pass: all 14 existing MVP tests and both discovery tests passed offline under Java 17.
- Self-audited deterministic ordering, direct-field call restrictions, exact namespace/statement binding, dynamic-SQL exclusion, and source locator handling. Mapper identity depends on exact interface FQN/XML namespace agreement, not an optional `@Mapper` annotation.

## Current state

Phase 1 core discovery is complete. The implementation parses Java source with
JavaParser and mapper XML with a hardened JDK parser; it makes no type solver,
runtime, classpath, network, or MyBatis calls.

## Changed files

- progress/phase1-discovery-core.md
- src/main/java/com/linguan/codemd/discovery/DiscoveryRequest.java
- src/main/java/com/linguan/codemd/discovery/SourceLocator.java
- src/main/java/com/linguan/codemd/discovery/HttpRoute.java
- src/main/java/com/linguan/codemd/discovery/DirectCallEdge.java
- src/main/java/com/linguan/codemd/discovery/MapperBinding.java
- src/main/java/com/linguan/codemd/discovery/SqlUpdateFact.java
- src/main/java/com/linguan/codemd/discovery/DiscoveryGap.java
- src/main/java/com/linguan/codemd/discovery/DiscoveryResult.java
- src/main/java/com/linguan/codemd/discovery/RepositoryDiscoverer.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated root/worktree changes observed and preserved before this task. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest test` | RED | Test compilation failed only on the eight expected missing Phase 1 production symbols. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest test` | GREEN | 2 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CandidateArchivePersistenceTest test` | PASS | 3 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliPersistenceTest test` | PASS | 1 test run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliValidateTest test` | PASS | 2 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=InterpretationAdmissionGateTest test` | PASS | 3 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=ManifestEvidenceVerificationTest test` | PASS | 2 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=NineSectionRenderingDeterminismTest test` | PASS | 2 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=TraceLocatorTest test` | PASS | 1 test run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest test` | PASS | Final run: 2 tests run; 0 failures, 0 errors. |
| `rg -n '[[:blank:]]+$' src/main/java/com/linguan/codemd/discovery progress/phase1-discovery-core.md` | PASS | No trailing whitespace found. |

## Decisions

- Parse source text only: no symbol solver, classpath resolution, source execution, network access, or MyBatis runtime.
- Preserve exact JavaParser method ranges in locators; deterministic value records and explicit sorted collections provide repeat equivalence.
- Admit a mapper binding only when the mapper’s fully-qualified interface name exactly equals exactly one XML namespace and the statement ID is unique; report ambiguity or dynamic SQL rather than infer facts.
- Keep JavaParser token storage enabled: it is required for exact `Range` locators and does not require source resolution.
- Treat an interface with a unique exact XML namespace/statement match as a mapper even if registration uses `@MapperScan` instead of `@Mapper`; the exact source binding remains the admission condition.

## Blockers

- None.

## Exact next action

Hand the completed core implementation to the Phase 1 coordinator for integration with the remaining discovery work.

## Resume checks

- Re-read this progress file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Re-run only the directly affected discovery selector if this implementation changes.
