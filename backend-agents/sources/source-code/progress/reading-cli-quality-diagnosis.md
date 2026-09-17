# Progress: reading-cli-quality-diagnosis

- Status: COMPLETE
- Agent role: Bounded read-only CLI quality diagnosis
- Model: Inherited Codex sub-agent
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Only PMD CyclomaticComplexity in SourceAnalysisExecution.Arguments.parse(String[]); no production changes or builds
- Owning plan: docs/supplements/cross-object-process-reconstruction/README.md
- Approved inputs: Existing source, tests, config/pmd-rules.xml and existing local CI reports
- Current branch/worktree: codex/cross-object-process-reading, formal source-code checkout

## Completed

- Read repository, Linguan, backend and source-code AGENTS.md guidance.
- Checked Git status and branch; preserve all existing worktree changes.
- Confirmed the existing target/pmd.xml has exactly one unsuppressed violation: Arguments.parse(String[]) cyclomatic complexity 105 at SourceAnalysisExecution.java:1611.
- Confirmed config/pmd-rules.xml:22 keeps methodReportLevel=100; local PMD 7.17.0 reports values greater than or equal to this threshold.
- Read the complete Arguments.parse and configured CLI option translation; compared the current source diff and relevant configured entry-point tests.
- Inspected the installed PMD 7.17.0 CycloVisitor bytecode read-only. Its ASTCatchClause and ASTThrowStatement visitors each increment complexity.

## Current state

- One named issue only: the new catalog/focus options and their checks pushed the existing large parse method over its unchanged complexity gate. This is a local structural quality failure, not a test/model/semantic failure.
- Minimum proposal: one private static AnalysisRunId conversion helper, reused at the four --activity-model-batch, --reuse-from-model-batch, --catalog-from-model-batch and --run cases. Move their identical try/catch, including the original failure("ARGUMENTS_INVALID", invalid), into the helper.
- Local PMD counts each extracted catch and its throw separately; four extracted pairs remove 8 from parse, so its expected metric is 105-8=97. This is a source/metric-implementation derivation, not a rerun result.
- The earlier catch-only estimate of 101, and the two-helper estimate of 99, are superseded by the actual installed PMD counting behavior. A second max-bytes helper would predict 93 but is not required by this finding.

## Changed files

- progress/reading-cli-quality-diagnosis.md only

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short; git branch --show-current | Read-only checked | Dirty worktree, expected codex/cross-object-process-reading branch |
| cat config/pmd-rules.xml; cat target/pmd.xml | Existing report checked | Threshold 100, one unsuppressed parse violation at 105 |
| git diff and nl/sed of SourceAnalysisExecution/SourceAnalysisCli and configured entry-point tests | Read-only checked | Four identical RunId conversions; unchanged switch/validation order can be preserved |
| javap -c -p installed pmd-java-7.17.0.jar CycloVisitor/CyclomaticComplexityRule | Read-only checked | catch and throw each increment; report threshold is >=100 |
| Tests/builds/PMD rerun | Not run by scope | Parent owns verification after implementation authorization |

## Decisions

- No tests, builds, model calls, interface changes, suppressions or threshold changes in this diagnosis.
- Preserve all switch labels, option parsing order, blank-value checks, record fields, and mode validation in place. The helper catches only IllegalArgumentException, retains the original cause, does not trim/normalize values, and uses AnalysisRunId.parse exactly once per selected case.
- Leave max-bytes, ArtifactId, BusinessOutputArtifactKey and argumentPath untouched; do not restructure the whole mode validator.
- Relevant verification for an authorized fix: extend/run SourceAnalysisConfiguredEntryPointTest through executeConfigured for valid and malformed values on all four affected switches, assert valid input reaches missing-config diagnostics while malformed input yields ARGUMENTS_INVALID, then run only the affected test and the existing PMD quality gate without rerunning suites.

## Blockers

- None for read-only investigation.

## Exact next action

- Parent to authorize/assign the single private RunId-helper extraction to production owner, retain behavior tests and durable current-fact documentation, then serially verify targeted tests and PMD. Do not claim 97 as measured until that check completes.

## Resume checks

- Recheck own progress, branch/status and cited source/report lines.

## Plan closeout destinations

- Durable decisions: Owning cross-object reconstruction design, if parent accepts implementation scope.
- Remaining issues: Owning plan delivery/backlog record.
- Verification and output references: docs/supplements/cross-object-process-reconstruction/delivery.md

Keep this handoff while the plan is active. At whole-plan closeout, consolidate
the information above into its durable destinations and remove the temporary
task file; do not archive a second copy of the progress record.
