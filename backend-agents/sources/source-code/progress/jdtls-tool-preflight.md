# Progress: JDT LS tool preflight

- Status: COMPLETE
- Agent role: Task 1 design/preflight owner
- Model: Codex (inherited agent model)
- Started: 2026-09-12T10:52:05Z
- Last updated: 2026-09-12T11:04:18Z
- Scope: Select and document the official JDT LS milestone, verified download/checksum source, supported installed launch JDK, and matching LSP4J client version; do not download, install, launch, index, or modify production/POM files.
- Approved inputs: Full-plan implementation is approved, including later download/verification of the official prebuilt JDT LS server and fixed research-harness dependencies; Task 1 remains documentation and local preflight only.
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` / `/private/tmp/linguan-source-analysis-process-design` at `77a360d9b3a82569c349e7f8f8e5fd10b3ac2eac`

## Completed

- Read the Task 1 brief, scoped `AGENTS.md`, approved plan Task 1 constraints, and progress template.
- Confirmed the worktree was clean before edits.
- Resolved the newest official milestone as JDT LS `1.61.0`, archive `jdt-language-server-1.61.0-202609031315.tar.gz`, and published SHA-256 `338e7e73d61836651ba2453919a0d34fa763eb4e7c03342092309bffb8934c64`.
- Resolved the build-matched Eclipse LSP4J client version as `1.0.0`; the milestone ships `org.eclipse.lsp4j[_jsonrpc]_1.0.0.v20260209-1721` and its tagged target definition selects the LSP4J `1.0.0` update site.
- Verified installed Oracle JDK `26.0.1+8-34` at `/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home`; the tagged wrapper rejects only Java below 21 and adds its Java 24+ XML flags.
- Verified cached JavaParser `3.28.2`, Jackson `2.21.4` line, and matching Gson `2.14.0` artifacts against their local Maven SHA-1 sidecars and recorded SHA-256 values.
- Confirmed LSP4J `1.0.0` artifacts and the selected JDT LS installation directory are not present yet.
- Wrote the durable research decision and the ignored machine-readable tool manifest with approval and not-installed state distinguished explicitly.
- Validated the manifest as JSON, confirmed it is ignored by the scoped `.gitignore`, and passed whitespace/error checking.

## Current state

Task 1 is complete. JDT LS `1.61.0`, its exact archive and published checksum, JDK 26.0.1, LSP4J `1.0.0`, Gson `2.14.0`, JavaParser `3.28.2`, and Jackson `2.21.4` are pinned. The official wrapper requires Python 3.9; the manifest fixes `/usr/bin/python3` 3.9.6 and the exact JDK 26 environment. No server/dependency download, extraction, launch, indexing, customer build, or model call occurred.

## Changed files

- `backend-agents/sources/source-code/progress/jdtls-tool-preflight.md`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/README.md`
- `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/tool-manifest.json` (ignored local artifact)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Empty before edits |
| `git branch --show-current` | PASS | `codex/jdtls-source-navigation-feasibility` |
| `git rev-parse HEAD` | PASS | `77a360d9b3a82569c349e7f8f8e5fd10b3ac2eac` |
| `java -version` | PASS | Oracle Java `26.0.1+8-34` |
| `/usr/libexec/java_home -V` | PASS | JDK 26 home is `/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home` |
| cached JAR SHA-1 vs `.sha1` sidecars | PASS | JavaParser/Jackson/Gson values match |
| selected LSP4J cache paths | EXPECTED ABSENT | Both `org.eclipse.lsp4j` and `org.eclipse.lsp4j.jsonrpc` `1.0.0` are approved for later download |
| selected server install path | EXPECTED ABSENT | Task 1 did not download, extract, or launch JDT LS |
| `jq -e . .../tool-manifest.json` | PASS | Valid JSON |
| `git check-ignore -v .../tool-manifest.json` | PASS | Ignored by scoped `.gitignore:1` |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Restrict Task 1 to durable documentation and the ignored manifest; no server installation/launch, customer build, model call, production code, or POM changes.
- Use only JDT LS/Eclipse and dependency-project primary sources for current toolchain facts.
- Select JDT LS milestone `1.61.0` and the exact official archive/checksum named above.
- Select installed JDK 26 rather than JDK 17; Java 26 satisfies both the official minimum and the tagged wrapper gate, while 17 does not.
- Select LSP4J `1.0.0` and explicitly pin Gson `2.14.0` in the future independent harness because LSP4J publishes an open Gson range and the JDT LS `1.61.0` repository ships Gson `2.14.0`.
- Keep the plan's `bin/jdtls` server executable, but force `/usr/bin/python3` (3.9.6) and the exact JDK 26 `JAVA_HOME` in its launch environment.

## Blockers

None. Pending download/install/launch fields are intentionally `false`, not blockers for this preflight task.

## Exact next action

The next plan owner may download the exact approved server archive and missing LSP4J `1.0.0` artifacts, verify them, update the manifest's pending identities/statuses, extract only to `tools/selected/`, and continue with the approved offline trial. No additional authorization request is needed.

## Resume checks

- Re-read this file and `git status --short`.
- Verify any cited release/checksum URLs remain official and exact.
- Confirm no Task 2+ workspace projection, server extraction, or runtime process has been created.
