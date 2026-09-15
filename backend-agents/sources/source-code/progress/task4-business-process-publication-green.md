# Task 4 — business-process publication GREEN

## Scope

Implement the deterministic Step07 publisher output in the formal `source-code`
checkout. This task does not change discovery, JDT collection, Activity
interpretation, prompts, or product-model execution.

## Delivered

- Publish and reopen the exact five v2 files: catalog, coverage,
  `business-processes.md`, `source-refs.jsonl`, and `sources.md`.
- Preserve stage narratives and rule `activityUseIds` in the catalog JSON;
  coverage includes the deterministic Activity name.
- Render narrative-first process Markdown with portable `sources.md#sNN`
  links and a deterministic source-index Markdown view.
- Require the complete v2 file set when reopening and verify deterministic
  re-rendering of both Markdown files without a Provider or source scan.
- Register policy, fixture, and public artifact-query support for the new
  `sources.md` artifact while preserving historical v1 output handling.

## Verification

```text
mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessPublicationTest,PublicBusinessArtifactQueryContractTest test
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

`git diff --check` also completed with no whitespace errors.

## Review remediation

- Replaced the main-document source-reference wall with one local `查看依据`
  link for each process, stage, and rule.  Each link opens a process-local
  source index that gives the file and line range, then links to the preserved
  snippet in `sources.md`.
- Reader reopening now requires the catalog's complete source-reference set to
  match `source-refs.jsonl` exactly once.  Illegal, duplicate, missing, or
  surplus references fail before Markdown comparison.
- The canonical store now recognizes only the exact legacy four-file v1
  contract or the exact current five-file v2 contract; a mixed set is not a
  valid publication.
- Rule grammar now uses labels rather than trying to wrap free-form Chinese
  conditions, and source paths/references are rendered safely.

```text
mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=<task4-files>
mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessPublicationTest test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
mvn -t .mvn/toolchains.xml -o spotless:check -DspotlessFiles=<task4-files>
git diff --check
```
