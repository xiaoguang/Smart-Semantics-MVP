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
