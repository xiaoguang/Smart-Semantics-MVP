# Progress: repository run bootstrap

- Status: COMPLETE
- Agent role: thin production launcher implementation
- Model: inherited production implementation session (exact model identifier is not exposed to this agent)
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: add the approved zero-model, configuration-driven JDT whole-repository materials launcher
  and narrow dependency-classpath handoff; after the coordinator releases Maven, add only the
  exact-request run-local Provider journal and then the separately approved thin saved-materials
  continuation modes. No parser or business-pipeline redesign and no model invocation by this
  agent.
- Approved inputs: local jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; ignored execution workspace `.workspace/jsherp-jdt-luna-run.5Oqj9Y/`; direct launcher RED in `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMainTest.java`.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design` on `codex/jsherp-jdt-luna-repository-run`.

## Completed

- Read the source-scoped instructions, run handoff, launcher RED, and the existing runtime/capture/store composition seams.
- Confirmed the launcher must call `PersistedTechnicalRunExecutor.execute` once and pass its saved `BusinessFlowsReference` directly to `PersistedBusinessRunExecutor.buildMaterials`; `RepositoryAnalysisRunCoordinator` would re-run the technical prefix.

## Current state

- The zero-model launcher, maintained JDT policy resource, tracked template, and ignored local
  configuration are ready for the run coordinator. The launcher queues through the public
  `RepositoryAnalysisAgent`, executes the technical prefix once, then builds material from its
  saved `BusinessFlowsReference`.

- Root's first authorized materials run captured all 719 files and discovered 339 HTTP sites, but
  JDT admitted only 2 because `PersistedTechnicalRunExecutor.openSession` supplied an empty
  approved classpath. The direct runtime regression is now green with the three approved Spring
  JARs in both discovery-only and full technical execution. This agent must not rerun the
  whole-repository JDT path or change the root-owned local configuration.

- A separate Luna RED now specifies the deferred run-local structured-provider journal. Its only
  approved behavior is exact request plus expected runtime-identity replay after a completed local
  response. It must persist `STARTED` before delegation and fail closed for incomplete, corrupt, or
  identity-mismatched records. Maven remains reserved for the root's active whole-repository JDT
  run until the coordinator releases it.

- The coordinator has now fixed the continuation contract but has not released implementation:
  `RepositoryRunMain --config ABS --mode activities-sample|generate --provider-config ABS`, with
  `activities-sample` additionally requiring `--material-id EXACT_ID`. Provider configuration is
  an absolute-path JSON document with exactly the approved executable, timeout, run-local journal,
  and output directory fields; Luna/high and the expected codex-subscription/read-only identity
  remain fixed. Both modes validate the original state configuration hash and reopen saved Step05
  output only. The sample writes canonical `ActivityExplanationResult`; generation writes only
  actual persisted document/run locations. This is read-only preparation while the root JDT run is
  active.

- The permitted direct journal RED has now been observed: the isolated offline selector failed
  with zero errors because `org.sourceanalysis.app.adapter.cli.RunJournalStructuredProvider` is
  absent. It did not execute JDT, a repository fixture, a Provider, or a quality aggregate.

- The smallest production GREEN candidate now exists at the agreed adapter seam. It derives a
  filename key from every structured request field plus the expected runtime identity, writes an
  exact canonical `STARTED` record before delegation, and replaces it only after a canonical
  response with the expected actual identity. Existing `STARTED`, malformed, or mismatched records
  fail before delegation. The direct selector is the next permitted check.

- The permitted direct journal GREEN now passes: the same isolated offline selector reports one
  test with zero failures and zero errors. Its fresh report records the required `-Xmx256m` test
  fork. Targeted formatter execution remains intentionally deferred because the root granted only
  this one Maven test selector while whole-repository JDT is active.

- Maven is now released to Luna for the continuation-mode RED. This agent is read-only until that
  strict `RepositoryRunMainTest` boundary is ready; it will not rerun the journal selector or
  compete with the root's JDT execution.

- Luna has completed the strict continuation configuration/parameter RED. The root has transferred
  the sole lightweight Maven lane back to this agent: run only the offline
  `RepositoryRunMainTest` selector with `MAVEN_OPTS=-Xmx512m` and `-DargLine=-Xmx256m`, observe its
  new failure, then implement the approved launcher-only continuation wiring. No other test,
  capture, source scan, or Provider call is authorized.

- The permitted continuation RED was observed: `RepositoryRunMainTest` ran two tests with one
  expected failure and zero errors. `activities-sample` currently emits `ARGUMENTS_INVALID` from
  the four-argument parser rather than reaching the required strict configuration validation. No
  live launcher mode was invoked.

- The continuation GREEN candidate is now confined to `RepositoryRunMain`: strict argument shapes
  and the exact five-field Provider JSON are parsed only after the original run configuration;
  fixed Luna/high Codex execution is wrapped in the journal; saved-state canonical config hash and
  Step05 reference are revalidated; and both modes reopen only persisted material. The sample
  filters one exact material plus equal entry coverage and writes an unpersisted canonical result;
  generation saves all final checkpoint references, verifies the persisted document file, then
  moves the existing run from RUNNING to FINISHED. No technical executor or capture is constructed
  on either path. The direct selector is the next permitted check.

- Root's read-only layout audit found the direct document observation must use the existing encoded
  run directory (`analysis-run--…`), rather than the wire run identifier with `:`. The launcher now
  derives that exact existing layout and retains the document check before the FINISHED transition;
  no public Store Path seam was added.

- The permitted continuation GREEN now passes: the same offline `RepositoryRunMainTest` reports two
  tests with zero failures and zero errors. Its boundary exercises both new argument shapes against
  invalid configuration without creating a run, invoking a Provider, or reaching a scan. The root
  has then authorized the one direct report-schema RED/Green slice in the same lightweight lane.

- The direct report-schema RED was observed with one failure and zero errors: 1,001 source refs
  produced 18,036 repeated enum values in the two report schemas. The GREEN candidate changes only
  `BusinessReportPublisher.contentsProperty`: transport refs remain bounded nonempty strings using
  the existing profile text limit, while Java's unchanged `allowedRefs.containsAll` validation and
  the nine fixed number/title enums remain authoritative. No generic schema layer was added.

- The permitted report-schema GREEN now passes: the direct large-source selector reports one test
  with zero failures and zero errors. It verifies that the Provider still receives all 1,001 input
  refs, each of the 18 paragraph/item ref positions has bounded nonempty-string transport rules
  without an enum, and the nine fixed chapter number/title enums remain. The existing out-of-scope
  citation rejection remains untouched.

- Root has authorized final lightweight handoff verification only: one offline unit aggregate of
  `RepositoryRunMainTest`, `RunJournalStructuredProviderTest`, and `BusinessReportPublisherTest`
  with the same 512m/256m limits; targeted Spotless apply/check over the three changed Java files;
  and `git diff --check`. It also authorized a README-only update for the already-approved five
  field Provider config and sample/generate commands. No ignored local configuration may change.

- Final handoff checks are complete. The authorized offline aggregate passed all seven selected
  tests (2 launcher, 1 journal, 4 report, including the existing invalid-ref rejection). Targeted
  Spotless apply/check passed for the three production Java files, and `git diff --check` passed.
  The tracked maintenance README now documents the exact five-field ignored Provider JSON and the
  absolute-path sample/generate commands. No ignored configuration, JDT process, repository scan,
  or live Provider was touched.

## Changed files

- `progress/repository-run-bootstrap.md`
- `src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java`
- `src/main/java/org/sourceanalysis/app/adapter/cli/RunJournalStructuredProvider.java`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisher.java`
- `tools/repository-run/jdt-artifact-policy-set-v1.json`
- `tools/repository-run/jdt-luna-repository-run.template.json`
- `tools/repository-run/README.md`
- Ignored local support: `.workspace/jsherp-jdt-luna-run.5Oqj9Y/repository-run.json` and
  `.workspace/jsherp-jdt-luna-run.5Oqj9Y/toolchains.xml`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Direct launcher RED | COMPLETE (parent-provided Luna lane) | `RepositoryRunMainTest` failed because the launcher class was absent |
| Direct launcher GREEN | PASS | `mvn -q -t .workspace/jsherp-jdt-luna-run.5Oqj9Y/toolchains.xml -Dtest=RepositoryRunMainTest test`: 1 test, 0 failures, 0 errors |
| Targeted formatting | PASS | `mvn -q -t .workspace/jsherp-jdt-luna-run.5Oqj9Y/toolchains.xml -DspotlessFiles=src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java spotless:apply` |
| JSON resources | PASS | strict `jq` parse; 22 ordered JDT policy entries; prepared capture/store/inspection directories and absent state file confirmed |
| Runtime classpath | PASS | `target/repository-run-classpath.txt` generated (2637 bytes) with the Maven dependency plugin |
| Approved JDT classpath regression | PASS | `mvn -q -t .mvn/toolchains.xml -o -Dtest=TechnicalAnalysisWorkflowTest#selectedJdtRuntimePassesApprovedClasspathToDiscoveryAndTechnicalExecution test`: 1 test, 0 failures, 0 errors |
| Launcher invalid-configuration regression after classpath change | PASS | `mvn -q -t .mvn/toolchains.xml -o -Dtest=RepositoryRunMainTest test`: 1 test, 0 failures, 0 errors |
| Direct run-local journal RED | EXPECTED FAIL | Offline `RunJournalStructuredProviderTest`: 1 failure, 0 errors; wrapper class missing |
| Direct run-local journal GREEN | PASS | Offline `RunJournalStructuredProviderTest`: 1 test, 0 failures, 0 errors with `MAVEN_OPTS=-Xmx512m`, `-DargLine=-Xmx256m` |
| Direct continuation RED | EXPECTED FAIL | Offline `RepositoryRunMainTest`: 2 tests, 1 failure, 0 errors; four-argument parser emitted `ARGUMENTS_INVALID` |
| Direct continuation GREEN | PASS | Offline `RepositoryRunMainTest`: 2 tests, 0 failures, 0 errors with `MAVEN_OPTS=-Xmx512m`, `-DargLine=-Xmx256m` |
| Direct report-schema RED | EXPECTED FAIL | Offline `BusinessReportPublisherTest#boundsSourceReferenceSchemaWithoutRepeatingLargeAllowlist`: 1 failure, 0 errors; 18,036 enum values vs fixed 18 |
| Direct report-schema GREEN | PASS | Offline `BusinessReportPublisherTest#boundsSourceReferenceSchemaWithoutRepeatingLargeAllowlist`: 1 test, 0 failures, 0 errors |
| Final bounded unit aggregate | PASS | Offline `RepositoryRunMainTest,RunJournalStructuredProviderTest,BusinessReportPublisherTest`: 7 tests, 0 failures, 0 errors |
| Targeted formatting | PASS | Offline Spotless apply/check for `RepositoryRunMain`, `RunJournalStructuredProvider`, and `BusinessReportPublisher` |
| Whitespace validation | PASS | `git diff --check` |

## Decisions

- The launcher is a non-public maintenance entry point at `org.sourceanalysis.app.adapter.cli.RepositoryRunMain`; its sole externally testable seam is `execute(String[], PrintWriter, PrintWriter)`.
- Config-owned source paths live only in the ignored run workspace. The tracked configuration is a placeholder template and maintained policy resource.
- The local Java 17 home is represented only in ignored `toolchains.xml` and the ignored concrete
  run configuration; no machine path was committed in the template.
- `PersistedTechnicalRunConfiguration.approvedClasspath` is immutable and defaults to an empty
  list through both prior constructors. The launcher requires an explicit technical classpath,
  verifies every regular non-symlink JAR, passes paths to JDT, and derives the queued toolchain
  reference from the declared toolchain plus each ordered `{path, sha256}` pair.

## Blockers

- None. Maven and the working files are released to root for its explicitly authorized real sample.

## Exact next action

- Root owns the next authorized real activity sample and any generation execution.

## Resume checks

- Read this progress file, the launcher RED, and `progress/jsherp-jdt-luna-repository-run.md`; run only the direct launcher test if the coordinator schedules Maven.
