# `source-analysis` local repository run

Source preparation is an implemented independent operation with the scoped fail-closed downstream source-version gate verified.
Its directory/Git input readers, structured result contracts, four-file publication,
refresh/exclusion, CLI, and module-local Skill have direct coverage. `prepare-source` options, issue results, refresh/exclusion
rules, and one-step Skill contract are in the
[source-preparation CLI design](../../docs/modules/source-preparation/cli-and-skill.md).
Source preparation does not start downstream technical analysis, model login, or business execution. A prepared source is not permission to mix older JDT/Activity/process materials into a new run.

The implemented technical commands are documented in the
[technical CLI design](../../docs/modules/technical-analysis/cli-and-runtime.md):
`collect-code` (Step02/03), `analyze-persistence` (Step04), and `assemble-materials`
(Step05). Their READY fixture path saves and reopens R1/R2/R3; fixed customer-source Maven/JDT/Vue acceptance remains outstanding. The legacy `plan-materials`/`materials-only` producer is retired and rejected before configuration loading; historical saved outputs remain readable.

`technical-analysis-artifact-policy-set-v1.json` is the new complete technical-policy
example: it retains the full controlled JDT policy set and adds the three exact module-5/6
keys for Java compilation environment, Java readiness, and the frontend HTTP index. The
historical `jdt-artifact-policy-set-v1.json` remains byte-for-byte unchanged so saved
receipts retain their original registry identity.

The approved handoff requires official Maven `dependency:build-classpath` and
`help:effective-pom` outputs for the same module/build selection. The user runs
Maven, or explicitly authorizes an Agent to do so. Maven may load customer
extensions; its execution is not inherently free of customer code. The Agent
explains actual failures and passes file locations plus explicit module/JDK
choices. Java deterministically extracts concrete settings, binds R0 and the
actual JDK, and returns the environment or specific missing/unsupported inputs.
It does not evaluate POM inheritance/profiles/BOM, download dependencies, or
launch Maven. Agents must not assemble a semantic compilation-input JSON.

**Migration status:** `technical-analysis-config-v2` directly names the official
Maven-output files; [technical-analysis.example.yaml](technical-analysis.example.yaml)
shows the current contract. New `collect-code` requires that v2 input. Historical v1
records remain readable only through their historical observation/artifact paths; do
not manually fill a v1 JSON record to start new technical collection. The target YAML,
historical-read boundary, and remaining fixed-source acceptance are defined in the
[CLI design](../../docs/modules/technical-analysis/cli-and-runtime.md#2-技术配置与外部编译输入)
and [dependency handoff](../../docs/modules/technical-analysis/dependency-preparation.md).
These consumption checks do not prove that the customer project compiles or authorize
a Maven run.

`source-analysis` is the only supported process entry point. Source preparation reads
`source-preparation-config-v1`; existing analysis commands read the applicable
`repository-run-config-v2/v3`, while v4 source selection is being connected to real consumers. All paths are absolute. The launcher delegates work to the same
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

Current reading-material execution requires `sourceAnalysis.javaEngine: jdt`: it starts
the configured JDT LS/tool JVM and provides repository navigation. JavaParser is
retired from production, not retained as an alternative for
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

### Technical materials

Use a technical-analysis configuration and the three independent operations:

```bash
source-analysis --config /absolute/technical.yaml collect-code
source-analysis --config /absolute/technical.yaml analyze-persistence --code-run 'analysis-run:<R1>'
source-analysis --config /absolute/technical.yaml assemble-materials --persistence-run 'analysis-run:<R2>'
```

They respectively save R1 Step02/03, R2 Step04, and R3 Step05. They reopen exact saved upstreams; R2/R3 do not start JDT or Node. `plan-materials` and its `materials-only` implementation are not supported commands. Historical `READING_MATERIALS_ONLY` outputs remain available to their strict readers and queries.

Export a verified older material-state file without rescanning:

```bash
"${SOURCE_ANALYSIS[@]}" export-materials-state \
  --output-state /absolute/path/to/new/materials-state-v3.json
```

The destination must not already contain different bytes. Export verifies the original
configuration and checkpoint and never runs JDT, the material builder, or a model.
This command is the historical M10/state-v3 export; it does not convert new Step05
materials into Activity input. New Step05 materials have their own direct Activity
reader and do not require this export.

### Explain Activities

With either a historical M10 batch or the new Step05 reading-material state, execute
the saved packages. Step05 execution reopens the existing material checkpoint; it does
not run JDT or the material builder again:

```bash
"${SOURCE_ANALYSIS[@]}" execute-step \
  --target flow-interpretation \
  --run 'analysis-run:<queued-run-sha256>'
```

For a historical M10 diagnostic sample, add `--material-id <material-id>`. For a
Step05 packet, use `--packet-id <packet-id>`. A sample retains its reviewed results
without claiming whole-repository Activity completion.

An optional explicit reuse source may be supplied:

```bash
  --reuse-from-model-batch 'analysis-run:<stopped-batch-sha256>'
```

Step05 Activity execution uses its configured per-stage retry policy and saves
successful stages immediately. Explicit `--reuse-only --reuse-from-model-batch <id>`
rechecks saved scope and results without initializing a model provider. A packet with
unexplained required slices remains incomplete; partial success does not admit Step07.
See the Activity design for retryable and non-retryable failure classes. Historical
M10 reuse retains its original full-task matching contract.

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

For the new Step05 Activity source, the process request validates the entire saved
Activity scope before starting a provider. A stopped Activity run is not necessarily
complete: `inspect` and the M11 coverage must show every required packet scope complete.
The fixed 2026-09-17 corpus currently has 14 incomplete packets, so it must not be
presented as an eligible full Step07 input until those named gaps are resolved.

An internal two-phase acceptance driver can discover a catalog from the same full
Activity input, then reconstruct up to three selected candidate IDs without publishing
a fake full-repository result. Its output manifest preserves the candidates' original
catalog ordinals. See [the acceptance instructions](acceptance/README.md). After review,
the formal process command can explicitly reuse that stopped sample batch; the sample
selection changes scheduling only, not the model input or semantic fingerprint.

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
frozen text is read without JDT. Current calls perform global material selection, one
reading check per candidate, process DRAFT → WRITE → RULE_REVIEW and consolidation.

Before a provider starts, the selected Activity/catalog/source references and question
are bound once to the queued run's applicable private execution configuration. A conflicting
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

For a completed technical R3 run, query `CODE_READING_MATERIALS_V2` for its canonical JSONL,
or export its complete Markdown projection using the same typed R3 material reader:

```bash
"${SOURCE_ANALYSIS[@]}" artifact \
  --run 'analysis-run:<sha256>' \
  --key CODE_READING_MATERIALS_V2 \
  --max-bytes 100000000 \
  --format markdown \
  --output /absolute/path/to/new/reading-materials.md
```

The output path must be absolute and new. `--max-bytes` applies to the requested
representation's UTF-8 bytes; it is configurable. This export strictly reopens the
saved R3/R2/R1/R0 lineage, hydrates already saved references, does not rescan or call
a model, and does not install another canonical artifact. It does not change the
historical `CODE_READING_MATERIALS`/business artifact route. `inspect` exposes
`completedReadingMaterials` separately from business outputs. No Step08 report exists
for a technical-only run.

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
