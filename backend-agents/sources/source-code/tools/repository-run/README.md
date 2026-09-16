# JDT repository materials run

> **Current implementation guide:** the commands below describe the existing material/Activity/report launcher. `generate` remains the historical complete-report path. The new `business-processes` mode reopens fixed M10 and reviewed Activity checkpoints, performs the approved cross-Activity discovery, and publishes Step07 without running JDT, rebuilding materials, re-explaining Activities or invoking Step08.

The launcher now accepts one `repository-run-config-v2` YAML or JSON document.
In model modes, `sourceAnalysis.modelJobs` replaces the old second
`--provider-config` file. It validates the global and per-Provider job limits,
fixed routes, model/effort, non-secret authentication environment references and
the v2 state identity before it opens a saved materials run. The technical/material
base identity excludes only `sourceAnalysis.modelJobs`, so a concurrency change
does not require another JDT run. `materials-only` may omit `modelJobs` and never
resolves an authentication environment or constructs a Provider.

The launcher executes the approved
[model job execution design](../../docs/modules/model-job-execution.md).
Activity and process-group work use bounded global and per-Provider concurrency,
stable routes, whole DRAFT-to-REVIEW jobs, and private reviewed-result records.
Codex Subscription and explicit OpenAI Responses API providers can share one
run without fallback; repository summary and whole-report generation each remain
a single job after their upstream barrier closes.

`RepositoryRunMain` is a maintenance entry point for one fixed, complete local Git commit.
`materials-only` captures the configured commit, queues one run, executes the persisted JDT
technical prefix once, saves its `BusinessFlowsReference`, and builds business material from that
saved Step 05 publication. It does not construct a Codex provider or call a model.

`materials-only` now writes a `repository-run-state-v3` document containing the complete material
checkpoint, the original Step 05 publication, the actual material profile and the canonical
material-basis SHA-256. `activities-sample` and `generate` require this v3 state and directly reopen
the saved M10 material artifact. They do not capture, run JDT, rebuild graphs or call the material
builder.

Each explicit model command creates a new `AnalysisRunId`. That value is the `modelBatchId` and owns
the Activity, Knowledge and Report outputs; `sourceRunId` and the material checkpoint keep their
original address. A sample writes its complete reviewed result under an output subdirectory named
for the new batch and finishes that batch without fabricating a repository report. A full generation
records `analysis-run-output-v3`, whose mixed-ownership checks require the material to belong to the
source run and the remaining checkpoints to belong to the model batch.

Use `--reuse-from-model-batch <analysis-run-id>` to select one stopped prior batch explicitly. Only
an atomically saved, validated DRAFT+REVIEW pair whose full input fingerprint and runtime binding
match can skip both calls. Draft-only, failed, unknown, mismatched or damaged results are never
continued as half a job and never trigger an automatic retry. Activity jobs, process groups, the
repository summary and the report each make this decision from their own actual input. The old
batch and its logs remain unchanged.

## Exporting an existing v2 material run

Existing material runs are not silently dual-read. Export a verified old state once to a new,
previously absent v3 file:

```bash
java -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config /absolute/path/to/ignored/repository-run.json \
  --mode export-materials-state \
  --output-state /absolute/path/to/ignored/materials-state-v3.json
```

The exporter verifies the old configuration digest, Step 05 reference and completed M10 artifact,
then writes the new v3 state without running JDT, Builder or a Provider. It never overwrites the old
state. Point the configuration's `stateFile` at the new v3 file before running model modes.

The tracked [template](jdt-luna-repository-run.template.json) contains no machine paths. Copy it
to an ignored run workspace, replace every absolute-path placeholder, and leave the final state
file absent. The maintained [JDT policy set](jdt-artifact-policy-set-v1.json) is loaded as explicit
resource bytes; the launcher derives the canonical registry identity and every configuration input
reference from canonical content rather than accepting supplied hashes. It registers the persisted
technical prefix plus the current Activity, Process, and nine-section report checkpoint outputs
needed by `generate`; the launcher never synthesizes an absent policy at runtime.

When a stopped historical material or Activity checkpoint was written with an older exact policy
registry, set the optional root `inputPolicyRegistry` to that tracked registry file. The launcher
uses it only to reopen and verify those immutable inputs; new Step07 output is always installed with
`policyRegistry`. Omitting the field makes both roles use `policyRegistry`. The input registry path
does not participate in the technical-material basis, and a missing or mismatched historical policy
fails explicitly instead of causing JDT, Builder, or Activity execution.

The whole-repository defaults are intentionally above the approved jshERP source size, rather than
fixture-scale limits. `technical.approvedClasspath` is the explicit list of locally approved JARs
needed by JDT to bind framework annotations. The launcher requires regular, non-symlink files,
hashes their bytes before queuing the run, and puts the ordered `{path,sha256}` entries in the
effective toolchain reference. They remain bounded by the configuration and must be reviewed before
using a different source or commit.

`technical.selectedEntryIds` is an optional JSON string array. Omit it or use `[]` to collect every
discovered entry. For a bounded sample, provide each exact discovery entry ID once; the launcher
sorts the IDs, rejects blanks and duplicates, and rejects IDs not present in the persisted discovery
before it calls JDT collection. The source catalog and discovery denominator remain complete;
unselected entries are published as `NOT_SELECTED_FOR_SAMPLE`. A nonempty selection is injected
into the effective graph profile and an effective profile bundle used by both the queued run request
and verified-source inventory, so its run identity cannot be confused with an all-entry run. Do not
repeat the selection under `inputs.graphProfile`.

## Local Java 17 Maven toolchain

The Maven host and compiled application use Java 17. Set the local Java 17 home once, then generate
the ignored local toolchain from the tracked template. This host toolchain is separate from
`sourceAnalysis.jdt.javaHome`: the repository-run configuration remains the sole owner of the JDT
tool JVM and must name a JDK compatible with its JDT installation.

```bash
export SOURCE_ANALYSIS_JAVA17_HOME="/absolute/path/to/java-17-home"
export SOURCE_ANALYSIS_MAVEN_TOOLCHAINS="$PWD/.mvn/toolchains.local.xml"
test -x "$SOURCE_ANALYSIS_JAVA17_HOME/bin/java"
sed "s|/absolute/path/to/java-17-home|$SOURCE_ANALYSIS_JAVA17_HOME|" \
  .mvn/toolchains.example.xml > "$SOURCE_ANALYSIS_MAVEN_TOOLCHAINS"
```

Do not commit `.mvn/toolchains.local.xml`. It contains only the local Java 17 installation path;
the tracked example must remain free of machine-specific paths. With that local toolchain configured, build the direct
runtime classpath after the launcher test passes:

```bash
mvn -q -t "$SOURCE_ANALYSIS_MAVEN_TOOLCHAINS" \
  -DincludeScope=runtime \
  -Dmdep.outputFile=target/repository-run-classpath.txt \
  dependency:build-classpath
```

The run coordinator can then start the approved materials-only run with Java 17:

```bash
"$SOURCE_ANALYSIS_JAVA17_HOME/bin/java" -Xmx8g \
  -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config "$(pwd)/.workspace/jsherp-jdt-luna-run.5Oqj9Y/repository-run.json" \
  --mode materials-only
```

On success the launcher writes an exact state document containing the base configuration digest, run ID,
and complete persisted Step 05 publication reference. It also prints the material count and
checkpoint. An unexpected execution error retains its stack and causal chain on stderr; a rejected
configuration exits before creating a capture, run, or tool process.

## Provider continuation

Add a `sourceAnalysis.modelJobs` block to that same ignored run configuration.
Codex uses `auth.mode: chatgpt` and a named existing login-context environment
variable; API keys are environment-variable names, never YAML values. The
OpenAI API provider uses the official Responses Java SDK with automatic retries
disabled. Codex Subscription forces ChatGPT authentication in the selected
`CODEX_HOME` context and removes inherited API-key settings. Routes are fixed
before dispatch; no Provider failure retries or falls back to another service.

The journal records every complete request field and the expected runtime identity before a Codex
call. Only an exact completed canonical response is reusable. A `STARTED`, malformed, or
identity-mismatched record fails closed and never starts another Provider call.

After inspecting the material IDs from the saved state, run one exact activity sample. Replace the
placeholders with absolute paths and the selected material ID:

```bash
"$SOURCE_ANALYSIS_JAVA17_HOME/bin/java" -Xmx8g \
  -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config /absolute/path/to/ignored/repository-run.json \
  --mode activities-sample \
  --material-id "exact-material-id"
```

The sample writes a canonical full `ActivityExplanationResult` to
`outputDirectory/<sha256(modelBatchId)>/<sha256(materialId)>-activity.json`; it creates no aggregate
activity checkpoint. Once the sample is accepted, it can be selected as an explicit reuse source
for a later batch:

```bash
"$SOURCE_ANALYSIS_JAVA17_HOME/bin/java" -Xmx8g \
  -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config /absolute/path/to/ignored/repository-run.json \
  --mode generate \
  --reuse-from-model-batch "analysis-run:<completed-sample-batch-id>"
```

Generation prints `sourceRunId`, the new `modelBatchId`, `FINISHED` lifecycle and the verified
on-disk `document.md` path. Matching complete reviewed jobs are copied as validated reuse records;
the journal is diagnostic and is not by itself considered a reusable business result.

## Business-process discovery from an Activity checkpoint

Add `business.processDiscovery` using the fields in the tracked template. These downstream limits
do not participate in the saved technical-material identity. Start a new process-only model batch
from an exact, stopped Activity batch:

```bash
"$SOURCE_ANALYSIS_JAVA17_HOME/bin/java" -Xmx8g \
  -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config /absolute/path/to/ignored/repository-run.json \
  --mode business-processes \
  --activity-model-batch "analysis-run:<reviewed-activity-batch-id>"
```

The command creates a new `AnalysisRunId`, reopens the fixed Activity and material publications,
runs catalog discovery, detailed candidates and repository consolidation, then installs exactly
`repository-business-process-catalog.json`, `process-coverage.json`, `business-processes.md`,
`source-refs.jsonl` and `sources.md`. It prints the final process count, semantic delivery status and
Markdown path.
Use `--reuse-from-model-batch` only with a stopped process-only batch whose material and Activity
checkpoints match; reusable DRAFT+REVIEW pairs make zero new model calls. `render()` remains not
ready because this mode intentionally has no Step08 report checkpoint.
