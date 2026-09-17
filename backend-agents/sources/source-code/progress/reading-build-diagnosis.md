# Progress: Reading build diagnosis

- Status: COMPLETE
- Agent role: Bounded read-only Maven test compilation diagnosis
- Model: Current dispatched agent
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Formal source-code checkout compiler inputs, outputs, IDE configuration and local build records; no compile, source/model/customer/network execution or production/test/build edits
- Owning plan: docs/supplements/cross-object-process-reconstruction/README.md
- Approved inputs: Existing Maven test failure brief, argsfile, repository configuration and local generated build records
- Current branch/worktree: codex/cross-object-process-reading in formal source-code checkout

## Completed

- Read scoped AGENTS.md files and systematic-debugging skill fully.
- Inspected Git status and preserved all existing changes.
- POM selects Java 17, has no custom build/output directories or compiler argument overrides.
- Identified exact active VSCode JavaLS workspace and its generated Maven project metadata.
- Confirmed the IDE Java builder and Maven builder share both target/classes and target/test-classes with Maven.
- Inspected installed compiler plugin metadata and current Maven input/created-file inventories without compiling.

## Current state

The most plausible hypothesis is an unstable shared-output snapshot, not missing production sources: VSCode JavaLS process 18468 imports this exact formal checkout, registers the JDT Java builder and m2e builder, and maps main/test source output to Maven's exact directories. It was active at inspection (61.6% CPU), and its log has diagnostic-clear events within both the old failure and current clean windows. However, these events are not file-write attribution; the original race remains unconfirmed. The original javac argsfile and pre-clean Maven status inventory are no longer present, so they cannot be independently inspected after the root's clean.

The current main compiler inventory includes ArtifactId, RunStoreHandle and DefaultBusinessProcessDiscovery. The current testCompile inventory includes all 166 test source files: Surefire's -Dtest controls execution, not test compilation. No source/class absence is inferred from the old javac error.

## Changed files

- progress/reading-build-diagnosis.md only

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | Reviewed | Existing implementation/test/docs changes preserved |
| POM read-only inspection | Complete | Compiler 3.15.0, release 17, conventional Maven output paths |
| Exact process 18468 command inspection | Read successfully after narrow read-only escalation | RedHat JavaLS workspace belongs to formal checkout; live CPU activity |
| Generated IDE .classpath/.project inspection | Shared output confirmed | Main target/classes; test target/test-classes; JDT Java + m2e builders |
| IDE .metadata/.log inspection | Timing activity confirmed, write owner not confirmed | Diagnostic clears at 07:07 and 07:18/07:19 |
| Installed compiler 3.15.0 plugin.xml inspection | Complete | useIncrementalCompilation=false is per-source stale selection, not forced full compilation |
| Current Maven status inventories | Read successfully | Missing-error class names have source and created-class entries; test input count 166 |
| git diff --check -- progress/reading-build-diagnosis.md | PASS before final progress update | No whitespace errors |

## Decisions

- Root remains sole owner of all heavy verification and rebuilds.
- Collect evidence before recommending an invocation/isolation remedy; output race remains an unconfirmed hypothesis.
- Minimal next verification: pause Java auto-build for the exact VSCode workspace (local java.autobuild.enabled=false or close that workspace), allow its builder to quiesce, freeze source/test edits, then run the existing Java 17 toolchain clean with the same explicitly selected direct tests serially.
- Do not use -Dmaven.compiler.useIncrementalCompilation=false as a force rebuild or isolation remedy: true already performs all-source rebuilding on changes, while false can retain stale dependent references.
- Avoid partial main-only output redirection: compiler main output has a CLI override, but test output has no equivalent bound property and classpath/Surefire would also need coherent wiring. No shared POM change or new subsystem is needed for the first isolation experiment.

## Blockers

- No direct proof attributing historical target writes to a process. The old argsfile was removed by the ongoing clean; do not label the hypothesis proven or the build fixed until root verifies the isolated command.

## Exact next action

Root owns the serial rebuild and isolation experiment; diagnostic handoff has been sent. If the frozen clean passes while IDE remains active, that supports source-edit instability but does not by itself disprove intermittent IDE output competition. Do not run additional compilers in this agent.

## Resume checks

- Read this file and check Git status; do not start Maven or javac.
- IDE metadata evidence: /Users/yexiaoguang/Library/Application Support/Code/User/workspaceStorage/e4835b2cdca66be4a83f85d66adc2b05/redhat.java/jdt_ws/.metadata/.plugins/org.eclipse.core.resources/.projects/source-code-analysis-agent/{.classpath,.project}; the same jdt_ws/.metadata/.log owns timing-only events.

## Plan closeout destinations

- Durable decisions: docs/supplements/cross-object-process-reconstruction/module-design.md if applicable
- Remaining issues: owning plan acceptance/backlog
- Verification and output references: docs/supplements/cross-object-process-reconstruction/delivery.md

Keep this handoff while the owning plan is active.
