# `source-analysis` local repository run

`source-analysis` is the only supported process entry point. It reads one absolute
`repository-run-config-v2` YAML or JSON file and delegates work to the same
`RepositoryAnalysisAgent` and persisted run coordinator used by the Java API.

The removed `RepositoryRunMain` and `generate` route are not compatibility entry
points. Existing material, Activity, process, and historical nine-section checkpoints
remain readable; this launcher does not regenerate them merely because their original
producer has retired.

## Configuration

The new Step 01–05 reading-material route is implemented and locally verified. Its policy file is
[reading-materials-artifact-policy-set-v1.json](reading-materials-artifact-policy-set-v1.json):
it contains the existing source/discovery/navigation contracts and the new persistence
and reading-material contracts, with no Fact/Flow/Capsule or model-output contracts.
Do not replace the older policy file in saved configurations; those bytes remain part
of historical runs. The new route requires JDT, optional
`sourceAnalysis.persistence.plugins`, and `technical.readingMaterials` with
`maxPacketUtf8Bytes` and `maxEntriesPerPacket`. Its configuration and internal
03→04→05 chain, shared XML view, configured command arguments and state codec are
covered by direct tests and the 469-test clean quality build. Fixed-repository CLI
acceptance finished with 325 packets and 326 coverage records, including one explicit
navigation failure; retirement cleanup is complete. See the
[delivery verification](../../docs/supplements/jdt-persistence-reading-materials-delivery.md)
for measured results and limitations. This route does not run Activity or business models.

For this technical-only route, copy
[reading-materials.template.json](reading-materials.template.json) into an ignored
workspace. Replace its paths, repository identity and fixed commit, and supply any
already-approved local dependency JARs in `technical.approvedClasspath`. The template
contains no model configuration or credentials. Set persistence `plugins` to `[]`
or omit `persistence` to keep Java-only material. The packet byte limit admits whole
source units; excluded units are recorded, not silently truncated. The example limits
are configurable, not a promise that every repository fits them.

The [earlier model-run template](jdt-luna-repository-run.template.json) describes saved
legacy-material/model configurations, not the new Step05 material contract. Copy it to an ignored
workspace and replace every absolute-path placeholder. Never put a credential in the
file. Model authentication is named by environment variable, for example
`SOURCE_ANALYSIS_PRO_HOME` for an existing ChatGPT login context.

New reading-material execution requires `sourceAnalysis.javaEngine: jdt`: it starts
the configured JDT LS/tool JVM and provides repository navigation. JavaParser is
being retired from production in this delivery, not retained as an alternative for
this route. Historical configuration decoding is separate from permission to start
a new analysis; an old `javaparser` value does not trigger a fallback to JDT.

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

Application compilation and tests remain on that Java 17 toolchain. The configured
google-java-format 1.36.1 requires a JDK 21+ Maven host for Spotless and the complete
quality build; this is a build-tool requirement, not an application Java upgrade:

```bash
SOURCE_ANALYSIS_QUALITY_JAVA_HOME=/absolute/path/to/jdk-21-or-newer
JAVA_HOME="$SOURCE_ANALYSIS_QUALITY_JAVA_HOME" \
  mvn -t .mvn/toolchains.local.xml -Pquality clean spotless:check verify
```

Select the tool JDK explicitly; do not rely on whichever Java happens to be in PATH.

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

With the reading-material configuration, this captures the configured immutable
commit, discovers entries, collects JDT navigation, applies the configured persistence
plugin, and publishes Step05 `code-reading-materials.jsonl`. It writes state v4 and
a `READING_MATERIALS_ONLY` run output, then stops. It neither builds old M10 material
nor constructs a model provider.

To use an already registered frozen capture instead of capturing the source again:

```bash
"${SOURCE_ANALYSIS[@]}" plan-materials \
  --source-registration 'source-registration:<sha256>'
```

The registration must be in the configured capture workspace and match the configured
repository identity and commit. This reuses the capture, not an old navigation result:
the new run still executes JDT and Steps01–05. Use a new state destination and retain
the old run.

Export a verified older material-state file without rescanning:

```bash
"${SOURCE_ANALYSIS[@]}" export-materials-state \
  --output-state /absolute/path/to/new/materials-state-v3.json
```

The destination must not already contain different bytes. Export verifies the original
configuration and checkpoint and never runs JDT, the material builder, or a model.
This command is the historical M10/state-v3 export; it does not convert new Step05
materials into Activity input. Connecting the new material contract to Activity is
outside the current implementation scope.

### Explain Activities

With a historical M10/model configuration, execute every saved material package:

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

### Reuse a catalog for cross-object reading

The [supplementary design](../../docs/supplements/cross-object-process-reconstruction/README.md)
defines `--catalog-from-model-batch <id>` and optional `--focus-question <text>` on the
same process command:

```bash
"${SOURCE_ANALYSIS[@]}" execute-step \
  --target repository-knowledge \
  --activity-model-batch 'analysis-run:<reviewed-activity-batch-sha256>' \
  --catalog-from-model-batch 'analysis-run:<original-catalog-batch-sha256>' \
  --focus-question 'Which objects connect the stages, and under which conditions?'
```

The catalog is input material, distinct from `--reuse-from-model-batch`, which reuses
matching completed tasks. All existing Activities are read without regeneration;
frozen text is read without JDT. New calls perform global material selection, one
reading check per candidate, process DRAFT/REVIEW and consolidation.

Before a provider starts, the selected Activity/catalog/source references and question
are bound once to the queued run's private execution configuration v3. A conflicting
selection cannot silently reuse that run. Real calls still require user authorization.

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

For the new technical-only run, query `CODE_READING_MATERIALS` for its canonical JSONL,
or export a complete Markdown projection using the same typed material reader:

```bash
"${SOURCE_ANALYSIS[@]}" artifact \
  --run 'analysis-run:<sha256>' \
  --key CODE_READING_MATERIALS \
  --max-bytes 100000000 \
  --format markdown \
  --output /absolute/path/to/new/reading-materials.md
```

The output path must be absolute and new. `--max-bytes` applies to the requested
representation's UTF-8 bytes; it is configurable. Export hydrates already saved
references, does not rescan or call a model, and does not install another canonical
artifact. `inspect` exposes `completedReadingMaterials` separately from business
outputs. No Step08 report exists for a technical-only run.

For a historical run that already owns a valid Step08 checkpoint:

```bash
"${SOURCE_ANALYSIS[@]}" render --run 'analysis-run:<sha256>'
```

Rendering reopens the stored reviewed report JSON and source references and deterministically
checks its Markdown. It performs zero model calls. New Step08 generation is not part of the
current production route.

## Failure and preservation rules

- Technical material preparation initializes no model provider. Existing business
  jobs retain their own versioned stage contract and fixed provider binding; this
  upstream change does not rerun or rewrite those jobs.
- Completed jobs are saved immediately. A fatal failure stops new dispatch but preserves
  already saved results.
- A failed model batch never invalidates its source material checkpoint.
- Inspecting, exporting, artifact reading, and historical rendering do not scan source or
  contact a provider.
- No command silently falls back from JDT to JavaParser, from ChatGPT login to an API key,
  or from one provider to another.
- Runtime outputs, credentials, journals, local toolchains, and machine-specific config
  files stay outside version control.
