# Progress: Registry-flow lineage closeout tests

- Status: COMPLETE
- Agent role: Luna/xhigh bounded test-preparation owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One bounded public FiniteKeyFlowTaskCompiler regression that reuses one real Registry body and independently installs three validly rehashed decoys differing only in run identity, controls, or one current BusinessFlows upstream reference.
- Approved inputs: `progress/registry-flow-lineage-closeout-diagnosis.md`, published Step 05/06 lineage contracts, current `FiniteKeyFlowTaskCompilerTest`, `BusinessFlowProvenanceTest`, and public canonical module-store install seams. Production, fixture, design, schema, Provider, source, commit, and network changes remain out of scope.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve concurrent carrier-cutover and Terra changes.

## Completed

- Created this progress record before any Java or build action and staged it intent-to-add.
- Read the complete Sol/xhigh lineage diagnosis and the scoped source-code AGENTS rules.
- Read the existing FiniteKeyFlowTaskCompilerTest real Fact → Flow → Capsule → Registry → M3 chain and the BusinessFlowProvenanceTest public module-envelope rehash/install pattern.

## Current state

- The bounded M4 regression now has the diagnosed lineage checks in place. The final frozen verification rejects the independent wrong-run, wrong-controls, and wrong-current-BusinessFlows-upstream decoys while preserving the valid Registry path.
- The completed test builds one real current Registry publication, reopens its M3 envelope/body, and uses three independent temporary public module stores. Each decoy keeps the Registry semantic payload/body unchanged and changes exactly one authenticated envelope/receipt join before recomputing the canonical module-artifact identity:
  - wrong-run: change only the M3 `AnalysisStepModuleAddress.runId` and envelope producer address;
  - wrong-controls: keep address/upstreams and change one non-policy `ArtifactControls` digest, then envelope controls;
  - wrong-current-upstream: keep address/controls and replace one exact BusinessFlows semantic `ArtifactReference` in the seven-item upstream list and envelope upstreams.
- Each decoy first installs through `RunStoreBootstrap.openForTest`, `FileSystemCanonicalModuleArtifactStore`, and `ModuleInstallRequest`, proving the decoy is store-valid. The public `FiniteKeyFlowTaskCompiler` call now rejects each decoy; the test's three `assertAll` cases are GREEN.
- The single test reuses `FiniteKeyFlowTaskCompilerTest`'s real chain through the M3 publisher, retains the original fixture's `CanonicalAnalysisStepArtifactStore` for current BusinessFlows, and passes each isolated decoy `CanonicalModuleArtifactStore` to a fresh public M4 compiler. Each decoy root is an existing empty `@TempDir` directory; the store uses the fixture's public policy registry and the same explicit limits as the fixture. Root's final session confirms the valid path and all three decoy rejections.
- Rehashing must update the M3 payload envelope's producer/controls/upstream fields as applicable, remove and recompute its `artifactId` with the existing `canonical-module-artifact-id-v1` framing, and install through the ordinary `ModuleInstallRequest`; no receipt or filesystem mutation is permitted.
- The assertion shape is one `assertAll` of three lambdas, each first fresh-reopening its decoy reference and then invoking the public `compileFiniteKeyTasks` seam. The valid original Registry body, Flow IDs, capsule IDs, and basis items are held constant across all three cases; only the targeted lineage join differs. All three decoy installations and fresh reopens passed.

## Changed files

- `progress/registry-flow-lineage-closeout-tests.md` (owned; intent-to-add)
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompilerTest.java` (one lineage test plus test-local public-store/envelope helpers)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped `git status --short` | PASS | The authorized lineage test and owned progress are present; unrelated shared-worktree changes were preserved. |
| Read-only contract/fixture trace | PASS | One real M3 Registry chain, public store bootstrap/install, envelope rehash, and three decoy mutations are feasible from existing seams. |
| Exact two-file Spotless apply/check | PASS | Only `BusinessFlowsPublicationSpecifierTest.java` and `FiniteKeyFlowTaskCompilerTest.java` selected; both clean. |
| First `mvn -o -t .mvn/toolchains.xml '-Dtest=BusinessFlowsPublicationSpecifierTest,FiniteKeyFlowTaskCompilerTest' test` | RED (numeric exit 1) | Tests run 8, failures 2, errors 0, skipped 0. All 4 BusinessFlows tests passed; all three decoys installed but the new lineage test was RED, while the zero-flow premise was too broad over source FACT gaps. |
| Final `mvn -o -t .mvn/toolchains.xml '-Dtest=BusinessFlowsPublicationSpecifierTest,FiniteKeyFlowTaskCompilerTest' test` | RED (numeric exit 1) | Tests run 8, failures 1, errors 0, skipped 0. BusinessFlows 4/4 and existing FiniteKey 3/3 passed; only the lineage `assertAll` failed because unchanged M4 accepted all three decoys. |
| Root frozen session `16470` | PASS | `FiniteKeyFlowTaskCompilerTest`: 4 tests, 0 failures, 0 errors, 0 skipped, including all three decoy rejections; `RegistryProposalTaskCompilerTest`: 4/0/0/0; overall 90 UTs + 1 config IT, exit 0. |

## Decisions

- Keep this at the M4 Registry-to-current-BusinessFlows join. Do not authenticate opaque R0 M1/M2 upstream references, add input fields, or broaden the schema/API.
- Use the unchanged Registry semantic body and exact existing Flow/capsule/basis IDs; only envelope/receipt lineage fields vary per decoy.
- Keep the three cases independent and run them under one test with `assertAll`, so a wrong-run rejection cannot mask the controls or current-upstream checks.
- Derive all changed references from the reopened real publication and controls; do not invent a Flow ID, Registry body, receipt, or synthetic upstream artifact.
- Keep the current-BusinessFlows mutation to one exact semantic artifact reference in M3's seven-item sorted upstream list; do not attempt to authenticate the opaque R0 task/execution references that M4 cannot independently reconstruct.
- Keep the first-run zero-flow failure classified as a corrected test premise: source FACT gaps remain in the public set, while only disposition-owned raw ENTRY gaps normalize to FLOW/FLOW_COMPILATION.

## Blockers

- The single Java test and public-store helpers remain limited to `FiniteKeyFlowTaskCompilerTest.java`. The final frozen verification is GREEN: all three independently installed decoys are rejected and the valid Registry path remains accepted. Full Step 05 is not complete; source/domain acceptance remains blocked.

## Exact next action

- Release this completed bounded lineage slice; do not alter production or rerun it here.

## Resume checks

- Do not modify production M4, `BusinessFlowProvenanceTest`, fixtures, design, schema, Provider, source, or other tests.
- Do not change the Registry semantic body, Flow IDs, capsule/basis data, M1/M2 opaque upstreams, or public identity formulas.
- No further Java, test, Maven, Spotless, commit, push, or network action is authorized in this activity.
