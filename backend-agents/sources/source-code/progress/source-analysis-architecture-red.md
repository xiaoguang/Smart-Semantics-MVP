# Progress: Source analysis architecture RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD — semantic wire reset architecture seam
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01 (after direct Maven selector)
- Scope: Add only the initial public-seam architecture RED test for Task 2.
- Approved inputs: Published naming design, Task 2 wire-reset contract, target package and Maven identity registry.
- Current branch/worktree: codex/source-analysis-semantic-cutover / /private/tmp/linguan-source-analysis-semantic-cutover/backend-agents/sources/github-code

## Completed

- Read the repository, prototype, source-agent, TDD, target design, naming plan, and toolchain plan instructions.
- Confirmed the pre-reset project still uses `com.linguan.codemd` and the old Maven identity.

## Current state

- The initial architecture RED is established against the pre-reset checkout.
- The test reports seven grouped assertion failures; no test error occurred.

## Changed files

- `src/test/java/org/sourceanalysis/app/SourceAnalysisArchitectureTest.java`
- `progress/source-analysis-architecture-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=SourceAnalysisArchitectureTest test` | EXPECTED RED | 1 test, 1 failure, 0 errors/skips; old module directory, Maven identity, package roots, and forbidden wire vocabulary are present. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The RED will inspect the checked-out project descriptor and source tree, and will require the semantic `org.sourceanalysis.app` production root while rejecting the pre-reset Maven/package/directory vocabulary.
- No production code, POM, directory move, formatter, or compatibility reader is part of this slice.
- The project-local toolchain file exists and points to the approved JDK 17 installation; the shell default remains JDK 26, so the selector will use `-t .mvn/toolchains.xml` explicitly.

## Blockers

- None.

## Exact next action

- Parent Terra agent may consume this RED for the semantic directory/POM/package wire reset. Do not add a second architecture RED or alter this test while the reset is in progress.

## Resume checks

- Confirm only this progress file and the architecture test are changed.
- Confirm the direct selector does not run unrelated tests.
- Preserve the RED for the Terra semantic cutover implementation; this agent does not commit or push.
