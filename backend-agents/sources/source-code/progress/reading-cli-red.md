# Progress: Reading CLI and execution binding RED

- Status: READY_FOR_COORDINATOR_RED
- Agent role: Luna/xhigh RED owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-16
- Scope: configured repository-knowledge CLI options and private v3 execution binding
- Owning design: docs/supplements/cross-object-process-reconstruction/module-design.md §7

## Confirmed seams

- `SourceAnalysisCli.executeConfigured` must accept process-only `--catalog-from-model-batch`
  and optional `--focus-question` for `repository-knowledge`.
- `--reuse-from-model-batch` remains a separate complete-batch reuse selector. It may coexist with
  `--catalog-from-model-batch` because the two values bind different inputs; duplicate flags remain
  invalid.
- `flow-interpretation` rejects both process-only options. A missing configuration after valid
  process option parsing must report `CONFIGURATION`, not `ARGUMENTS_INVALID`.
- The private writer seam is the existing `SourceAnalysisExecution.writeProcessModelJobExecutionConfiguration`
  with one additional `ProcessDiscoveryRequest readingRequest` parameter. It writes the existing
  model-batch/source/M10/activity/output owners plus the new v3 reading bindings atomically and
  idempotently; no public Agent seam or context framework is added.

## Changed files

- This progress record.
- `src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisConfiguredEntryPointTest.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/CrossObjectProcessExecutionConfigurationTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessReadingPipelineTest.java`

The CLI tests cover catalog-only plus exact focus text, catalog-plus-reuse co-forwarding,
focus omission, duplicate flags, and process-only flags rejected by flow interpretation. The
private binding test covers one same-run idempotent v3 record, exact non-null/null focus values,
full inventory publication identity, catalog lineage and byte identity, unchanged M10/activity/
output owners, and conflict on changed focus or inventory reference.

The first legacy-test migration slice now routes the scripted discovery fixture through the new
material-selection and per-candidate reading-check decisions. Process DRAFT/REVIEW assertions
read `readingPacket.reviewedActivities` and `readingPacket.sourceExcerpts`; the fixture no longer
emits the retired `requestedSourceRefs` response field. The pipeline fixture explicitly selects
SOURCE_REF M1/M2 plus the XML request so a context source reference is present only because it
was read into the packet allowlist. Other named legacy test groups remain pending.

## Verification

- No Maven/build/test/model/JDT/network/commit was run. Coordinator owns the named Maven run.
- `git diff --check` passes for the progress record and both RED files.

Coordinator-owned targeted check (recorded, not run by this agent):
`JAVA_HOME=J17 mvn -q -t .mvn/toolchains.local.xml -Dtest=SourceAnalysisConfiguredEntryPointTest surefire:test`
completed with 5 tests, 2 failures, and 0 errors. The two newly legal process-option cases still
returned the old `ARGUMENTS_INVALID`; the other three cases passed. This is the intended CLI RED.

## Expected v3 binding shape

The test freezes `model-job-execution-config-v3` at the existing configuration path. Existing
`modelBatchId`, `sourceRunId`, `materialsCheckpoint`, `activityCheckpoint`, `reuseFromModelBatchId`,
`modelJobsSha256`, `modelJobs`, and process scope owners remain intact. New fields mirror the
internal request names: `sourceInventoryReference` contains the complete verified-inventory
publication reference; `savedCatalogInput` contains a lineage object with the raw pair's
`runId`, `phase`, `jobKey`, `inputFingerprint`, and byte identity; `focusQuestion` is exact text or
explicit JSON null. The existing normalized model-jobs block remains under its current contract.

## Blockers

- Current production parser/writer does not yet support the new flags or v3 fields; this is the
  intended RED. Root will run the named Maven compile/test and hand the API to the GREEN owner.
