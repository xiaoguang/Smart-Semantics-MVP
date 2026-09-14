# JDT repository materials run

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

`activities-sample` and `generate` are currently narrow continuation modes for that same saved
`RUNNING` run. They revalidate the v2 state document's canonical configuration SHA-256 and exact
Step 05 publication, then rebuild the `BusinessMaterialSet` from that saved publication in memory;
they do not reopen a durable material checkpoint. They also do not capture, create a technical
executor, or run JDT. The successful sample leaves that run `RUNNING` and writes only an unpersisted
inspection result. Generation writes the durable activity, knowledge, and report checkpoints,
verifies the actual persisted Markdown location, then transitions the same run to `FINISHED`. A
started model failure transitions it to `FAILED`, after which neither continuation mode can reopen
it. The current command therefore cannot create a replacement model batch over the first saved
materials.

## Approved model-batch target (not implemented)

The approved target in [model job execution section 7](../../docs/modules/model-job-execution.md#7-固定材料与独立模型批次已批准待实施) changes
these operational semantics. State v3 will save the complete material checkpoint and original
business-flow publication, plus the saved material profile, material module version, and canonical
material-basis SHA-256. Each explicit sample or generation request will use a new `start`-allocated
`AnalysisRunId` as its `modelBatchId`, keep the material producer as `sourceRunId`, and perform zero
capture, JDT, graph, flow, or material-builder work. `analysis-run-output-v3` will validate the
material checkpoint against `sourceRunId` while activity, knowledge, and report outputs belong to
the new batch. It will not rewrite the original publication addresses.

An optional `--reuse-from-model-batch <analysis-run-id>` is also an approved target, but the current
launcher does not accept it. Do not add it to commands below. Reuse will require the same verified
materials checkpoint and an exact job fingerprint; only a complete, validated, atomically saved
DRAFT+REVIEW result may skip both Provider calls. A failed or unknown DRAFT, or a completed DRAFT
whose REVIEW failed or is unknown, causes a fresh whole pair in the explicit new batch and leaves
the old batch untouched. These batch identities and reuse records remain program-side and are not
added to model input. The target execution config also records the declared sample or generate
scope. After a sample saves its complete reviewed private job and sample inspection, that sample
batch transitions to `FINISHED` without fabricating the four-checkpoint full-run output; a failed
sample is `FAILED`. This makes a completed sample an eligible explicit reuse source while keeping
its lifecycle completion distinct from whole-repository report completion.

The tracked [template](jdt-luna-repository-run.template.json) contains no machine paths. Copy it
to an ignored run workspace, replace every absolute-path placeholder, and leave the final state
file absent. The maintained [JDT policy set](jdt-artifact-policy-set-v1.json) is loaded as explicit
resource bytes; the launcher derives the canonical registry identity and every configuration input
reference from canonical content rather than accepting supplied hashes. It registers the persisted
technical prefix plus the current Activity, Process, and nine-section report checkpoint outputs
needed by `generate`; the launcher never synthesizes an absent policy at runtime.

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

With a Java 17 Maven toolchain configured, build the direct runtime classpath after the launcher
test passes:

```bash
mvn -q -t .workspace/jsherp-jdt-luna-run.5Oqj9Y/toolchains.xml \
  -DincludeScope=runtime \
  -Dmdep.outputFile=target/repository-run-classpath.txt \
  dependency:build-classpath
```

The run coordinator can then start the approved materials-only run with Java 17:

```bash
/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home/bin/java -Xmx8g \
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
/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home/bin/java -Xmx8g \
  -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config /absolute/path/to/ignored/repository-run.json \
  --mode activities-sample \
  --material-id "exact-material-id"
```

The sample writes a canonical full `ActivityExplanationResult` to
`outputDirectory/<sha256(materialId)>-activity.json`; it creates no activity checkpoint. Once the
sample is accepted, generate the durable report from the same state and same run-local journal:

```bash
/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home/bin/java -Xmx8g \
  -cp "target/classes:$(cat target/repository-run-classpath.txt)" \
  org.sourceanalysis.app.adapter.cli.RepositoryRunMain \
  --config /absolute/path/to/ignored/repository-run.json \
  --mode generate
```

Generation prints the run ID, `FINISHED` lifecycle, and the verified on-disk `document.md` path.
The same exact DRAFT or REVIEW request used by the sample is reopened from the completed journal
record rather than sent to Codex a second time.
