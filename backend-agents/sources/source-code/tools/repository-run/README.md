# `source-analysis` local repository run

`source-analysis` is the only supported process entry point. It reads one absolute
`repository-run-config-v2` YAML or JSON file and delegates work to the same
`RepositoryAnalysisAgent` and persisted run coordinator used by the Java API.

The removed `RepositoryRunMain` and `generate` route are not compatibility entry
points. Existing material, Activity, process, and historical nine-section checkpoints
remain readable; this launcher does not regenerate them merely because their original
producer has retired.

## Configuration

Copy [the tracked template](jdt-luna-repository-run.template.json) to an ignored
workspace and replace every absolute-path placeholder. Never put a credential in the
file. Model authentication is named by environment variable, for example
`SOURCE_ANALYSIS_PRO_HOME` for an existing ChatGPT login context.

`sourceAnalysis.javaEngine` selects exactly one engine:

- `jdt`: starts the configured JDT LS/tool JVM and provides repository navigation.
- `javaparser`: uses the retained JavaParser adapter and does not start JDT. It keeps
  its existing, more limited resolution capability; selecting it does not promise JDT
  parity.

`sourceAnalysis.modelJobs` is the only model configuration. Global and per-provider
concurrency, routes, model identity, timeout, and authentication references are all in
this block. A second `--provider-config` argument is rejected.

Material identity excludes model concurrency and output-directory settings, so changing
those settings does not require another source scan. Changing the source commit or
material-building rules requires a new material checkpoint.

## Local Java 17 toolchain

The application uses Java 17. JDT's tool JVM is configured separately under
`sourceAnalysis.jdt.javaHome`.

```bash
export SOURCE_ANALYSIS_JAVA17_HOME=/absolute/path/to/java-17-home
sed "s|/absolute/path/to/java-17-home|$SOURCE_ANALYSIS_JAVA17_HOME|" \
  .mvn/toolchains.example.xml > .mvn/toolchains.local.xml

mvn -q -t .mvn/toolchains.local.xml \
  -DincludeScope=runtime \
  -Dmdep.outputFile=target/repository-run-classpath.txt \
  dependency:build-classpath
```

`.mvn/toolchains.local.xml` is ignored and must not be committed. The tracked example
contains no developer-machine path.

For the examples below:

```bash
CONFIG=/absolute/path/to/ignored/repository-run.yaml
CLASSPATH="target/classes:$(cat target/repository-run-classpath.txt)"
SOURCE_ANALYSIS=(java -Xmx8g -cp "$CLASSPATH" \
  org.sourceanalysis.app.adapter.cli.SourceAnalysisCli --config "$CONFIG")
```

The configuration path must be absolute. Relative configuration paths are rejected so
that a saved run never changes meaning with the caller's working directory.

## Commands

### Capture and queue

The configured source block can be captured independently:

```bash
"${SOURCE_ANALYSIS[@]}" capture-local-git
```

It prints a `sourceRegistrationId`. Queue a path-free run request with that immutable
registration:

```bash
"${SOURCE_ANALYSIS[@]}" start \
  --source-registration 'source-registration:<sha256>'
```

It prints a `runId` in `QUEUED` state. Supplying this ID to a later `execute-step`
requires an exact queued request match; stopped runs are never reactivated.

### Build materials without a model

```bash
"${SOURCE_ANALYSIS[@]}" plan-materials
```

This captures the configured immutable commit, executes the selected Java engine and
the retained Step01-05 analysis, then publishes the business-material checkpoint. It
does not construct a model provider.

Export a verified older material-state file without rescanning:

```bash
"${SOURCE_ANALYSIS[@]}" export-materials-state \
  --output-state /absolute/path/to/new/materials-state-v3.json
```

The destination must not already contain different bytes. Export verifies the original
configuration and checkpoint and never runs JDT, JavaParser, the material builder, or a
model.

### Explain Activities

Execute every saved material package:

```bash
"${SOURCE_ANALYSIS[@]}" execute-step \
  --target flow-interpretation \
  --run 'analysis-run:<queued-run-sha256>'
```

For one exact diagnostic sample, add `--material-id <material-id>`. A sample stores its
reviewed result but does not pretend to be a complete repository Activity checkpoint.

An optional explicit reuse source may be supplied:

```bash
  --reuse-from-model-batch 'analysis-run:<stopped-batch-sha256>'
```

Only a complete, validated DRAFT+REVIEW result with matching material, prompt, schema,
producer, model, and account identity is reused. Draft-only, failed, damaged, or
mismatched results execute as a new complete job; there is no half-round continuation,
automatic retry, or provider fallback.

### Discover and publish business processes

Start from a stopped Activity batch; source navigation and Activity explanation are not
rerun:

```bash
"${SOURCE_ANALYSIS[@]}" execute-step \
  --target repository-knowledge \
  --activity-model-batch 'analysis-run:<reviewed-activity-batch-sha256>' \
  --run 'analysis-run:<queued-process-run-sha256>'
```

The current Step07 publisher installs:

- `repository-business-process-catalog.json`
- `process-coverage.json`
- `business-processes.md`
- `source-refs.jsonl`
- `sources.md`

This command does not invoke the retired singleton process route or Step08. `render()` is
therefore intentionally not ready for a process-only run.

### Planned: reuse a catalog for cross-object reading (not implemented)

The [supplementary design](../../docs/supplements/cross-object-process-reconstruction/README.md)
adds `--catalog-from-model-batch <id>` and optional `--focus-question <text>` to the
same process command. Do not use these flags against the current binary yet.
The catalog is input material, distinct from `--reuse-from-model-batch`, which reuses
matching completed tasks. All existing Activities are read without regeneration;
frozen text is read without JDT. New calls perform global material selection, one
reading check per candidate, process DRAFT/REVIEW and consolidation.

Before a provider starts, the selected Activity/catalog/source references and question
are bound once to the queued run's private execution configuration. A conflicting
selection cannot silently reuse that run. This design does not authorize real calls.

### Observe saved results

Observation never initializes a model provider:

```bash
"${SOURCE_ANALYSIS[@]}" inspect --run 'analysis-run:<sha256>'

"${SOURCE_ANALYSIS[@]}" artifact \
  --run 'analysis-run:<sha256>' \
  --key BUSINESS_PROCESSES_MARKDOWN \
  --max-bytes 1000000
```

`artifact` accepts the closed names in `BusinessOutputArtifactKey`, including business
materials, Activities, current process outputs, and historical report outputs.

For a historical run that already owns a valid Step08 checkpoint:

```bash
"${SOURCE_ANALYSIS[@]}" render --run 'analysis-run:<sha256>'
```

Rendering reopens the stored reviewed report JSON and source references and deterministically
checks its Markdown. It performs zero model calls. New Step08 generation is not part of the
current production route.

## Failure and preservation rules

- Every model job is DRAFT then full REVIEW on one fixed provider binding.
- Completed jobs are saved immediately. A fatal failure stops new dispatch but preserves
  already saved results.
- A failed model batch never invalidates its source material checkpoint.
- Inspecting, exporting, artifact reading, and historical rendering do not scan source or
  contact a provider.
- No command silently falls back from JDT to JavaParser, from ChatGPT login to an API key,
  or from one provider to another.
- Runtime outputs, credentials, journals, local toolchains, and machine-specific config
  files stay outside version control.
