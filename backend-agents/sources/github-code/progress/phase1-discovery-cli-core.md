# Progress: phase1-discovery-cli-core

- Status: COMPLETE
- Agent role: Phase 1 discovery CLI production implementer
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Implement only fresh offline `code-md inspect` and `code-md discover` commands in `src/main/java/**`, plus this progress record. Do not modify tests, POM, docs, frozen sources, or other agent files.
- Approved inputs: Scoped `AGENTS.md`, `progress/TEMPLATE.md`, Phase 1 discovery handoffs/tests, existing CLI/discovery Java sources, and synthetic local test fixtures. No network, model, customer source, or customer Maven build.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; the target Maven directory is shared and untracked.

## Completed

- Read the repository and `github-code` scoped instructions, progress template, Phase 1 discovery progress records, POM, current CLI/discovery implementation, and the new fresh-CLI RED tests.
- Confirmed the target `CodeMdCli` currently registers only `generate`, `trace`, and `validate`; `inspect` and `discover` are absent.
- Confirmed the existing `RepositoryDiscoverer` returns stable discovery records and already owns source parsing.
- Resolved the profile contract with the coordinator: use `WALKING_SLICE_V0` as the approved current Phase 1/MVP capability profile. A later versioned capability manifest may rename it without changing this candidate contract.
- Re-ran the focused Java 17 offline CLI test selector: all three tests are RED exclusively because Picocli returns exit code 2 for the absent `inspect` and `discover` commands.
- Added `inspect` and `discover` Picocli subcommands, each with required `--repository-root`, one UTF-8 JSON-object stdout emission, and successful exit code for source-analysis gaps.
- Added stable inspection counts and the approved `WALKING_SLICE_V0` profile; the discover response serializes the existing deterministic `DiscoveryResult` record surface.
- Updated source discovery to retain no partial Java parse result and emit stable Java/XML/tree-read gaps instead of leaking parser/read exceptions or temporary absolute paths.
- Reached GREEN for the focused fresh-CLI contract: all three inspect/discover tests pass offline under Java 17.
- Passed the serial direct discovery/CLI regressions (five tests) and the selected Phase 1/MVP regressions (eleven tests), all offline under Java 17.
- Re-ran the complete selected Phase 1/MVP verification set after the final source edits: nineteen tests passed with zero failures or errors. Checked all three changed paths for trailing whitespace.

## Current state

`inspect` and `discover` are registered fresh-process CLI commands. They require
`--repository-root`, write one UTF-8 JSON object to stdout, and return zero for
supported source-analysis outcomes, including conservative analysis gaps.
`RepositoryDiscoverer` now excludes partial parse results and records malformed
Java/XML or unreadable-source conditions as stable, path-free gap codes.

## Changed files

- progress/phase1-discovery-cli-core.md
- src/main/java/com/linguan/codemd/cli/CodeMdCli.java
- src/main/java/com/linguan/codemd/discovery/RepositoryDiscoverer.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated worktree changes were observed and preserved before edits. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliDiscoveryTest test` | RED (from test handoff) | 3 tests compile and fail only because fresh CLI returns 2 for absent `inspect` and `discover`. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliDiscoveryTest test` | RED (confirmed) | 3 tests run; 3 failures, 0 errors; each expected exit code 0 but received 2. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliDiscoveryTest test` | GREEN | 3 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest test` | PASS | 5 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CandidateArchivePersistenceTest,InterpretationAdmissionGateTest,ManifestEvidenceVerificationTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | PASS | 11 tests run; 0 failures, 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliDiscoveryTest,RepositoryDiscovererTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest,CandidateArchivePersistenceTest,InterpretationAdmissionGateTest,ManifestEvidenceVerificationTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | PASS | 19 tests run; 0 failures, 0 errors. |
| `rg -n '[[:blank:]]+$' src/main/java/com/linguan/codemd/cli/CodeMdCli.java src/main/java/com/linguan/codemd/discovery/RepositoryDiscoverer.java progress/phase1-discovery-cli-core.md` | PASS | No trailing whitespace found. |

## Decisions

- Keep discovery analysis in `RepositoryDiscoverer`; CLI only validates its root option, invokes the discoverer, derives the inspect summary, and serializes deterministic JSON.
- Preserve existing JSON property names from public record components so source locators remain relative paths.
- Represent malformed Java, malformed XML, unreadable source files, or failed source walks as path-free, deterministic gap codes. Successful fresh command execution remains exit code 0 for source-analysis gaps.

## Blockers

- None.

## Exact next action

Hand the completed CLI implementation and verification evidence to the coordinator.

## Resume checks

- Re-read this progress file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Confirm changes are limited to this progress file and `src/main/java/**`.
