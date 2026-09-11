# Progress: discovery graph handoff diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh bounded read-only debugger
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Diagnose the first genuine real ApplicationDiscovery to real ProgramGraphs publication RED in `DiscoveryToFactHandoffTest.handsRealDiscoveryAndGraphPublicationsToTheFactInputReader`; classify production contract bug versus invalid test bootstrap and identify the minimum Terra fix.
- Approved inputs: Existing source-scoped design, production and test source, exact prior raw Surefire report, and at most the one exact Maven selector. No network, Provider, source capture, production/test/design edits, commit, or push.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, preserve all unrelated shared-worktree changes.

## Completed

- Read the applicable repository, backend, and source-code instructions completely.
- Read the mandatory Superpowers workflow, Codex adaptation, systematic-debugging workflow, and root-cause-tracing guidance.
- Read the existing handoff test progress, Java source, and raw Surefire report before task-local changes.
- Ran one effective exact selector with JVM exception logging; the Maven process completed with exit 1 and the Surefire raw counts remained 1 test / 0 failures / 1 error / 0 skipped.
- Located the swallowed exception: `ArtifactStoreException: MODULE_INSTALL_REQUEST_INVALID` is thrown by `AtomicCanonicalPublicationEngine.validateStandaloneJsonArtifact` at bytecode offset 176, which maps to source line 322 (the recomputed standalone artifact ID does not equal the payload ID).
- Traced that identity mismatch to the test-local artifact-policy bootstrap. The first rejected payload is `code-structure-graph.json`: the real publisher emits prefix `program-graphs-code-structure-graph`, while the test registry declares `code-structure-graph`. The same copied-prefix defect exists for public control-flow, data-flow, and evidence graph policies.

## Current state

- The current RED is an invalid test bootstrap, not a production graph contract failure. `ProgramGraphSetPublicationSpecifier` correctly creates the public graph ID with its registered production prefix; the test supplied a contradictory registry and the artifact store correctly failed closed.
- The erroneous test lines are `DiscoveryToFactHandoffTest.java:457`, `:471`, `:485`, and `:499`; their expected values are respectively `program-graphs-code-structure-graph`, `program-graphs-control-flow-graph`, `program-graphs-data-flow-graph`, and `program-graphs-evidence-graph`, matching `ProgramGraphSetPublicationSpecifier.java:1207-1210` and `:1097`.
- Static inspection also found a separate mapper-signature producer/consumer discrepancy, but JVM evidence proves it is not the swallowed publication exception in this RED. It is intentionally not expanded or proposed as part of this bounded closeout.

## Changed files

- `progress/discovery-graph-handoff-diagnosis.md` (owned diagnostic progress only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Existing raw Surefire report inspection | RED evidence read | Tests run 1, Failures 0, Errors 1, Skipped 0; failure at publication specifier line 157. |
| Exact selector without project-local toolchain flag | PRE-TEST FAILURE / exit 1 | 0 tests executed; Maven rejected the missing user-level JDK 17 toolchain. No source/test conclusion drawn. |
| `JAVA_TOOL_OPTIONS=<JVM exception log> mvn -t .mvn/toolchains.xml -o -Dtest=org.sourceanalysis.app.analysis.graph.DiscoveryToFactHandoffTest#handsRealDiscoveryAndGraphPublicationsToTheFactInputReader test` | RED / exit 1 | Tests run 1, Failures 0, Errors 1, Skipped 0. Fork log 58,479 lines; swallowed exception is `ArtifactStoreException: MODULE_INSTALL_REQUEST_INVALID` at standalone-ID equality line 322. |
| `javap -c -l -p AtomicCanonicalPublicationEngine` | PASS | Bytecode offset 176 maps exactly to the `expectedId != payload.artifactId` throw at source line 322. |
| Fresh relevant-source inspection + scoped `git diff --check` | PASS | Publisher/test prefix mismatch remains exactly as diagnosed; owned progress has no whitespace errors and is the task's only repository change. |

## Decisions

- Preserve the first real producer-to-consumer failure; do not edit or bypass production/test contracts during diagnosis.
- Classify this RED as invalid test bootstrap. Do not dispatch a Terra production change for it.
- The minimum correction belongs only to the Luna-owned handoff test: replace the four public graph policy prefixes with the production prefixes already used by the working public graph fixtures and execution test. Preserve types, schema versions, payload bytes, graph algorithms, and all production code.

## Blockers

- None. Diagnosis is complete; no test or production fix was authorized in this task.

## Exact next action

- Root should return the RED to its test owner for the four-prefix bootstrap correction, then rerun this exact method once. If a different downstream RED emerges, diagnose that new evidence separately rather than bundling a speculative production change.

## Resume checks

- Maven completed and the lease is released.
- No production, test, design, schema, fixture, source, network, Provider, commit, or push action is authorized.
