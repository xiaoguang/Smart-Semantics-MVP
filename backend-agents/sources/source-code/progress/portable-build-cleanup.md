# Progress: portable-build-cleanup

- Status: COMPLETE
- Agent role: Maven toolchain and run-documentation implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: portable Maven toolchain configuration, direct CI workflow use, and repository-run instructions only; no Java runtime, model invocation, JDT execution, or customer-source scanning.
- Owning plan: 代码清理与统一 source-analysis 入口实施计划
- Approved inputs: approved implementation-task brief; current `pom.xml`, `.mvn/toolchains.xml`, `.gitignore`, `tools/repository-run/README.md`, and `.github/workflows/source-analysis.yml`.
- Current branch/worktree: `codex/source-analysis-cli-cleanup`; pre-existing untracked `docs/research/` is preserved.

## Completed

- Read the repository, backend-agent, and source-code instructions.
- Confirmed this task requires no model calls and no JDT/customer-source execution.
- Read the TDD workflow and test-quality guidance; planned verification is Maven's actual local-toolchain resolution rather than a text-grep test.
- Established RED with `mvn -t .mvn/toolchains.local.xml -DskipUTs -DskipITs validate`: Maven rejected the absent required local toolchain file.
- Moved the existing host-specific Java 17 path into ignored `.mvn/toolchains.local.xml`, added tracked `.mvn/toolchains.example.xml`, and ignored the local file.
- Updated CI to generate its ignored local toolchain from `JAVA_HOME` after `actions/setup-java`; no workflow step mutates a tracked toolchain file.
- Updated repository-run commands to use `SOURCE_ANALYSIS_MAVEN_TOOLCHAINS` and `SOURCE_ANALYSIS_JAVA17_HOME`, while documenting that `sourceAnalysis.jdt.javaHome` remains an independent tool JVM setting.
- Established GREEN with `mvn -t .mvn/toolchains.local.xml -DskipUTs -DskipITs validate`; Maven selected the local Java 17 toolchain.
- Compiled the module offline with unit and integration tests skipped; Maven Compiler selected the same local Java 17 toolchain.
- Rendered the tracked template exactly as CI does into a temporary toolchain file and verified Maven selected Java 17.
- Parsed the workflow YAML, checked the final scoped content for retired path/Perl references, and confirmed the local file is ignored.

## Current state

The portable toolchain/configuration transition is complete. The existing POM already selects Java 17 in the Maven Toolchains, Compiler, and PMD configurations, so it requires no behavior-changing edit. The remaining plan work belongs to the parent task; this subtask must not commit from the shared worktree.

## Changed files

- `progress/portable-build-cleanup.md`
- `.mvn/toolchains.example.xml`
- `.mvn/toolchains.local.xml` (ignored, local only)
- `.gitignore`
- `tools/repository-run/README.md`
- `../../../.github/workflows/source-analysis.yml`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | On `codex/source-analysis-cli-cleanup`; only pre-existing `?? docs/research/`. |
| `mvn -t .mvn/toolchains.local.xml -DskipUTs -DskipITs validate` | RED | Failed as intended because `.mvn/toolchains.local.xml` was absent. |
| `mvn -t .mvn/toolchains.local.xml -DskipUTs -DskipITs validate` | GREEN | Maven selected `JDK[/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home]`. |
| `mvn -o -t .mvn/toolchains.local.xml -DskipUTs -DskipITs compile` | PASS | Compiler selected the same Java 17 toolchain; no tests, JDT, model, or source scan ran. |
| CI-equivalent template render + `mvn -o -t <temporary-file> ... validate` | PASS | The generated toolchain selected Java 17. |
| `ruby -e 'require "yaml"; YAML.load_file(...)' .github/workflows/source-analysis.yml` | PASS | `YAML_OK`. |
| Scoped retired-path/Perl search and `git check-ignore` | PASS | No retired reference in changed tracked files; `.mvn/toolchains.local.xml` is ignored. |
| Final repository-root retired-path/Perl search | PASS | `RETIRED_REFERENCES_ABSENT`. |

## Decisions

- Use an ignored local toolchain file with a tracked template so shared configuration contains no host path.
- Keep the Java 17 Maven host and the separately configured JDT tool JVM distinct.
- Preserve the existing POM because it already makes the Maven Toolchains plugin, compiler, and PMD choose Java 17; this migration changes configuration location, not the Java contract.
- CI obtains Java 17 from the pinned setup action, checks `${JAVA_HOME}/bin/java`, and renders a new ignored local file instead of modifying a tracked file.

## Blockers

- None for this scoped subtask.

## Exact next action

Hand off the changed-path list and verification evidence to the parent task; do not commit from the shared worktree.

## Resume checks

- Re-read this file and `git status --short --branch`.
- Confirm no unexpected edits overlap the scoped configuration files.

## Plan closeout destinations

- Durable decisions: tracked portable toolchain template, local-file ignore rule, CI-generated local configuration, and repository-run README.
- Remaining issues: none for this subtask; parent owns integration and commit.
- Verification and output references: RED/GREEN toolchain validation, offline compilation, CI-equivalent template render, YAML parse, and `git diff --check`.
