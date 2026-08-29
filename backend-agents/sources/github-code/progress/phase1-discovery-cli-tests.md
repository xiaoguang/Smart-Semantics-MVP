# Progress: phase1-discovery-cli-tests

- Status: COMPLETE
- Agent role: Phase 1 discovery CLI TDD test author
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add only synthetic RED tests for fresh `code-md inspect` and `code-md discover` CLI invocations, plus this progress record.
- Approved inputs: Scoped `AGENTS.md`, `progress/TEMPLATE.md`, Phase 1 discovery progress records, current CLI/discovery Java sources and tests, and local synthetic fixtures only. No network, customer source, model call, or customer Maven build.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; target Maven directory is shared and untracked.

## Completed

- Read the repository, prototype, source-to-standard-markdown, and github-code scoped instructions.
- Read the progress template, Phase 1 discovery handoffs, POM, current CLI/discovery/MVP Java sources, and current Java tests including `RepositoryDiscovererTest`.
- Confirmed the test boundary: fresh `CodeMdCli`, synthetic repository fixture, JSON-object stdout, stable relative identities, and explicit gaps for unsupported/dynamic input.
- Created this progress record before modifying tests.
- Added `CodeMdCliDiscoveryTest` covering inspect summary counts, discover static route/call/binding/update facts, and dynamic/unparseable gaps.
- Ran the focused new selector after Java 17 test compilation succeeded; all three tests reached the missing-command behavior and failed with exit code 2 instead of the required 0.

## Current state

The CLI currently exposes only `generate`, `trace`, and `validate`. The requested
`inspect` and `discover` command seams are absent. The new tests are intentionally
RED at command execution, not blocked at compilation.

## Changed files

- progress/phase1-discovery-cli-tests.md
- src/test/java/com/linguan/codemd/cli/CodeMdCliDiscoveryTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated worktree changes preserved before edits. |
| `git diff --check -- src/test progress/phase1-discovery-cli-tests.md` | PASS | No whitespace errors in changed test/progress paths. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=CodeMdCliDiscoveryTest test` | RED (expected) | 3 tests run; 3 failures, 0 errors. All fail because fresh CLI returns 2 for absent `inspect`/`discover`; compilation succeeded. |

## Decisions

- Use one focused test class with a copied-small static fixture shaped like `RepositoryDiscovererTest`: controller, service, mapper interface, mapper XML, and one dynamic/unsupported source variant.
- Assert only reader-facing JSON facts and stable relative source paths; never assert temporary absolute paths or temp directory names as identity.
- Keep JavaParser and all repository analysis behind the CLI production seam; tests invoke only a fresh `CodeMdCli` through picocli.

## Blockers

- The intended RED seam is missing from `CodeMdCli`: register `InspectCommand` and `DiscoverCommand` in the root command, each accepting `--repository-root`, returning 0, and writing exactly one JSON object to stdout.
- `inspect` needs a deterministic summary contract: `profile=WALKING_SLICE_V0`, `fileCount=4`, `javaFileCount=3`, `xmlFileCount=1`, and `routeCount=1` for the fixture, without temporary absolute identity.
- `discover` needs JSON serialization for `httpRoutes`, `directCallEdges`, `mapperBindings`, `sqlUpdateFacts`, and `gaps`, with stable relative paths and no ephemeral absolute identity.
- Dynamic SQL must emit `DYNAMIC_SQL_UNRESOLVED` and no `sqlUpdateFacts`; an unparseable Java file must emit a parse-coded gap rather than silently disappearing or becoming a fact.

## Exact next action

Hand the RED tests and the listed production seams to the coordinator; do not
implement production code in this task.

## Resume checks

- Re-read this progress file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Confirm only this progress file and `src/test/**` are changed by this task.
