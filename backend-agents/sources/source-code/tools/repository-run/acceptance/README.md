# Step05 business-process sample driver

This internal acceptance tool exercises the same saved Step05 Activity assembly,
source checks, execution configuration and Step07 sample seams as the formal
`source-analysis` command. It does not run JDT, the material builder or Activity
explanation, and it does not publish full-repository process coverage. Use it only
with a complete, verified Activity batch. Its `focusQuestion` is always `null`.

From the `source-code` module, compile the standalone driver against the same
application/runtime classpath used by `source-analysis`:

```bash
DRIVER_CLASSES=/absolute/path/to/new/private/driver-classes
CLASSPATH="target/classes:$(cat target/repository-run-classpath.txt)"
mkdir -p "$DRIVER_CLASSES"
javac -cp "$CLASSPATH" -d "$DRIVER_CLASSES" \
  tools/repository-run/acceptance/Step05ProcessSampleDriver.java
```

First generate the unguided full-corpus catalog. The output directory must be an
absolute path that does not exist; its parent must already exist:

```bash
java -cp "$DRIVER_CLASSES:$CLASSPATH" \
  org.sourceanalysis.app.adapter.cli.Step05ProcessSampleDriver \
  /absolute/path/to/ignored/activity-process-config.yaml \
  'analysis-run:<complete-activity-batch-id>' \
  catalog - /absolute/path/to/new/catalog-output
```

Read `catalog-output/sample-manifest.json`. It records the new model batch ID,
system assessment, every candidate ID and original ordinal. Inspect those actual
candidate results before selecting one to three for the second command. Labels are
local output directory names, not business answers supplied to the model:

```bash
java -cp "$DRIVER_CLASSES:$CLASSPATH" \
  org.sourceanalysis.app.adapter.cli.Step05ProcessSampleDriver \
  /absolute/path/to/ignored/activity-process-config.yaml \
  'analysis-run:<same-complete-activity-batch-id>' \
  selected 'analysis-run:<catalog-sample-batch-id>' \
  /absolute/path/to/new/selected-output \
  'sample-one=process-candidate:<id-from-manifest>'
```

The second run explicitly reuses the first run's complete catalog and selection
where inputs, prompts, provider identity and configuration match. Each selected
candidate saves its normal reading decision and DRAFT/WRITE/RULE_REVIEW result in
the private model-job journal. The new output contains `sample-manifest.json`, a
short index and one `business-processes.md` plus `sources.md` per selected candidate.
One candidate may contain several process fragments. These files are previews,
not the formal five-file repository publication.

After the user has seen and accepted the samples, the full `source-analysis
execute-step --target repository-knowledge` run may pass the **selected-sample**
batch as `--reuse-from-model-batch`. It must use the same Activity batch and
model/reading configuration. The formal run reuses matching completed jobs and
processes remaining candidates before consolidation. The sample driver never
initiates that full expansion itself.
