# Progress: corrected SystemConfig annotation audit

- Status: COMPLETE
- Agent role: bounded read-only evidence auditor
- Model: gpt-5.6-luna / xhigh
- Date: 2026-09-13
- Scope: inspect the existing corrected whole-repository application-discovery
  artifacts, the captured `SystemConfigController.java`, and the current
  discoverer implementation. No new JDT, Maven, model, source capture, or
  production/design change was authorized.

## Exact inputs

- Corrected run:
  `.workspace/jsherp-jdt-luna-run.5Oqj9Y/stores/runs/analysis-run--75a8beaf1baefa893cbf6f7df66ae67f4a4516e2b2c83048850bdf1b49b77984/`
- Discovery step:
  `steps/02-application-discovery/`
- Captured source snapshot:
  `capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/`
- JDT log inspected only at the task-specific path supplied by the coordinator:
  `/var/folders/ss/74phyrrn46q1qxmrst23qr9m0000gn/T/source-analysis-jdt-3088008221723535960/language-server-data/.metadata/.log`

## Proven from existing artifacts

- `capability-report.json` records 339 HTTP sites, 13
  `ANNOTATION_IDENTITY_UNRESOLVED` sites, 326 admitted entries, 15 gap refs,
  and `repositoryEntryCoverage.closed: true`.
- Every one of the 13 unresolved sites is in
  `jshERP-boot/src/main/java/com/jsh/erp/controller/SystemConfigController.java`,
  at lines 62, 76, 85, 93, 101, 109, 117, 136, 160, 187, 228, 293, and 344.
- The persisted source excerpts identify the mapping annotations exactly:
  `GetMapping /info`, `GetMapping /list`, `PostMapping /add`,
  `PutMapping /update`, `DeleteMapping /delete`, `DeleteMapping /deleteBatch`,
  `GetMapping /checkIsNameExist`, `GetMapping /getCurrentInfo`,
  `GetMapping /fileSizeLimit`, `PostMapping /upload`, `GetMapping /static/**`,
  `GetMapping /static/mini/**`, and `PostMapping /exportExcelByParam`.
- The unresolved sites have empty `affectedEntryIds`, disposition
  `UNSUPPORTED`, and explicit `HTTP_ENTRY_DECLARATION` gap IDs. The active
  `entry-points.jsonl` and publish shard each contain 326 lines, so these 13
  sites are not silently admitted.
- The captured source is manifest-verified as UTF-8, 14,371 bytes, SHA-256
  `c36ed157ea04cffa9bdd5c43975be4573861d7ba27b2da3c6a3440432faa0d7b`.
  It imports `org.springframework.web.bind.annotation.*` and uses the same
  wildcard import form visible in admitted sibling controllers.
- Current `SpringHttpEntryDiscoverer` behavior is fail-closed: a mapping
  annotation is cataloged only with a non-null qualified identity, and a
  recognized mapping simple name with null identity is emitted as
  `ANNOTATION_IDENTITY_UNRESOLVED`. No package-name inference was found or
  introduced.
- The task-specific JDT log contains repeated validation summaries for
  `/SystemConfigController.java` (`67 problems reported`) but no raw
  per-problem definition/identity response. This is evidence of diagnostics,
  not a causal explanation for the 13 unresolved mapping identities.

## Not proven

- The corrected run's Step 03/JDT catalog was not yet materialized while its
  `run-state.json` still reported `lifecycleState: RUNNING`; therefore the
  upstream JDT response for these annotation identities is unavailable here.
- It is not proven that the wildcard import, a particular missing dependency,
  a source-level diagnostic, or any package/classpath condition caused the
  unresolved identities. The old no-classpath run is historical and cannot
  establish the corrected run's cause.

## Verification

| Check | Result |
| --- | --- |
| `jq` counts from corrected `capability-report.json` | PASS: 339 sites / 13 unresolved / 326 entries / 15 gaps / closed |
| unresolved source path, lines, and raw annotation excerpts | PASS: 13 all in `SystemConfigController.java` |
| admitted entry and publish shard line counts | PASS: 326 each |
| snapshot manifest and source blob | PASS: path, hash, encoding, size, imports, and mapping declarations match |
| current discoverer source | PASS: null annotation identity remains explicit, not inferred |
| Maven/JDT/model/source capture | NOT RUN (intentional) |

## Handoff

The coordinator may inspect the corrected JDT catalog and raw navigation
responses after the active run completes. Until then, the defensible report
claim is 326 admitted entries plus 13 explicit unresolved mapping gaps; no
stronger root-cause claim is supported by the preserved evidence.
