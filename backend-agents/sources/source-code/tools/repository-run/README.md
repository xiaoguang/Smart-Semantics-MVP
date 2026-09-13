# JDT repository materials run

The commands below describe the current serial, single-provider JSON launcher.
The approved [model job execution design](../../docs/modules/model-job-execution.md)
is not implemented: it moves provider settings into the existing `--config`
document as `sourceAnalysis.modelJobs` YAML, with global and per-provider
`maxConcurrentJobs` (default global/Pro 4), fixed DRAFT/REVIEW bindings, isolated
authentication and private completed-job results. Target v2 replaces
`--provider-config`; its YAML is not executable with today's v1 launcher.
The v2 technical/material configuration basis remains independently verified,
so changing concurrency does not require another JDT run. Follow that design
for new implementation; preserve these current command examples until cutover.

`RepositoryRunMain` is a maintenance entry point for one fixed, complete local Git commit.
`materials-only` captures the configured commit, queues one run, executes the persisted JDT
technical prefix once, saves its `BusinessFlowsReference`, and builds business material from that
saved Step 05 publication. It does not construct a Codex provider or call a model.

`activities-sample` and `generate` are narrow continuation modes for that same saved state. They
revalidate the state document's canonical configuration SHA-256 and exact Step 05 publication
reference before reopening persisted material; they do not capture, create a technical executor, or
run JDT. The successful sample stays in the existing `RUNNING` state and writes only an unpersisted
inspection result. Generation writes the existing durable activity, knowledge, and report
checkpoints, verifies the actual persisted Markdown location, then transitions the run to
`FINISHED`.

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

On success the launcher writes an exact state document containing the configuration digest, run ID,
and complete persisted Step 05 publication reference. It also prints the material count and
checkpoint. An unexpected execution error retains its stack and causal chain on stderr; a rejected
configuration exits before creating a capture, run, or tool process.

## Provider continuation

Create this separate, ignored Provider configuration with exactly these five fields. Its paths are
absolute, `journalDirectory` and `outputDirectory` are existing non-symlink directories, and it
contains no credential or API-key field. The continuation launcher fixes the model to Luna/high and
requires the resulting `codex_subscription` / `read-only` runtime identity.

This is the current capability, not proof of strict subscription authentication:
the current subprocess inherits environment and login-status success alone does
not establish its auth mode. The approved design requires explicit ChatGPT auth,
API environment isolation and local-state preflight. It cannot promise to prevent
use of already-paid account credits; account-side verification is required before
an authorized subscription-only run. Explicit API routes are future configured
services, never automatic fallback after a failed call.

```json
{
  "schemaVersion": "repository-run-provider-v1",
  "executable": "/absolute/path/to/codex",
  "timeoutSeconds": 600,
  "journalDirectory": "/absolute/path/to/ignored/run-journal",
  "outputDirectory": "/absolute/path/to/ignored/inspection"
}
```

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
  --provider-config /absolute/path/to/ignored/provider-run.json \
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
  --mode generate \
  --provider-config /absolute/path/to/ignored/provider-run.json
```

Generation prints the run ID, `FINISHED` lifecycle, and the verified on-disk `document.md` path.
The same exact DRAFT or REVIEW request used by the sample is reopened from the completed journal
record rather than sent to Codex a second time.
