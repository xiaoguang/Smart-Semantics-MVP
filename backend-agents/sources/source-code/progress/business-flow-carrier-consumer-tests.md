# Progress: business-flow carrier consumer tests

- Status: COMPLETE
- Agent role: Luna/xhigh bounded test migration owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Migrate six existing consumer/public-seam test files and one fixed-repository JSON policy oracle to the already-published Business Flows v7/v5/v2 carrier contract; update R0/R1/R2 expected model-view projections to remove only program-only provenance metadata before task hashing.
- Approved inputs: Step 05 §8.1.3, published Step 06 §6.1 paragraph 203, existing public producer/consumer tests, and the fixed acceptance policy oracle. No production constants, fixtures, source inputs, profiles, budgets, or new test classes are in scope.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the scoped AGENTS instructions, both implementation plans, Step 05 §8.1.3, and Step 06 §6.1 paragraph 203.
- Mapped the exact stale v6/v4/v1 expectations and the four full-capsule R0/R1/R2 byte comparisons before editing.
- Created this progress record before the carrier-consumer edits.

## Current state

- The six Java test paths and one JSON oracle now match the published M2 v7/public Capsule v5/public Gap v2 carrier contract. The public publisher test derives source FACT versus compiler FLOW scope from reopened artifacts.
- R0/R1/R2 expected capsule views are deep copies with only `factViews[*].originFactArtifactRef`, `gapViews[*].originKind`, and `gapViews[*].originGapLedgerRef` removed; typed `evidenceRefs` and every other field remain byte-identical. Existing task input SHA assertions remain active and were extended to the selected R0/R1/R2 paths.
- The carrier cutover and the two corrected consumer oracles are GREEN after the authorized production-side carrier work: the BusinessFlows publication assertions and the R0/R1/R2 task-view/SHA assertions pass. The final frozen verification also passes the related Registry/Finite consumers.
- Follow-up verification exposed two stale test premises and no production issue: zero-flow public JSON contains source FACT gaps alongside compiler FLOW gaps, and the guarded two-Flow fixture has only source-ledger gaps. The premises were corrected to assert exact per-entry compiler-gap ownership and exact source-ledger Gap-ID equality; the final combined run has only the independent lineage RED.
- A byte-level follow-up found the owned fixed-repository JSON oracle had the same semantic object and fingerprints but one accidental final LF, which made the config-only check report 1 test, 0 failures, 1 error, 0 skipped. Only that final LF was removed; no policy/config value changed.
- Root's final frozen verification session 16470 passes the repaired config-only IT with 1 test, 0 failures, 0 errors, and 0 skipped.

## Changed files

- `progress/business-flow-carrier-consumer-tests.md` (owned)
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsPublicationSpecifierTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowCoverageTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompilerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java`
- `src/test/resources/analysis/flow/fixed-repository/fixed-repository-acceptance-config.json`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only contract and stale-wire audit | PASS | Mapped the six test consumers, fixed oracle, published v7/v5/v2 schemas, and unchanged production R0/R1/R2 reader gate. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<six absolute Java paths> spotless:apply` | PASS | Spotless selected exactly six files; one Java file changed. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<six absolute Java paths> spotless:check` | PASS | Six selected files clean. |
| `mvn -o -t .mvn/toolchains.xml '-Dtest=RegistryProposalTaskCompilerTest#compilesFullSignalClosedR0TasksFromFreshReopenedPublicCapsules,FiniteKeyFlowTaskCompilerTest#retainsTheFullOwningPublicCapsuleInEveryR1AndR2Task' test` | RED (numeric exit 1) | Tests run 2, failures 1, errors 1, skipped 0. Both selectors reached `RegistryProposalTaskCompiler.requireDescriptor` and failed on unchanged stale public carrier descriptor expectations before task-view assertions. |
| `mvn -o -t .mvn/toolchains.xml '-Dtest=BusinessFlowsPublicationSpecifierTest,FiniteKeyFlowTaskCompilerTest' test` (first follow-up) | RED (numeric exit 1) | Tests run 8, failures 2, errors 0, skipped 0. All 4 BusinessFlows tests passed; the new lineage test installed all three decoys but production accepted all three (`null` failures), while zero-flow exposed the stale all-gap scope premise. |
| `mvn -o -t .mvn/toolchains.xml '-Dtest=BusinessFlowsPublicationSpecifierTest,FiniteKeyFlowTaskCompilerTest' test` (final follow-up) | RED (numeric exit 1) | Tests run 8, failures 1, errors 0, skipped 0. BusinessFlows 4/4 and existing FiniteKey 3/3 passed; the separate lineage test was the sole RED. |
| Root byte audit and owned LF correction | PASS | Semantic fingerprints unchanged; the observed final LF was removed without changing policy/config values. |
| Node byte/semantic assertion after correction | PASS | Final byte is `}` with no LF; minified JSON semantic SHA remains `351516314acb17734debe4824f0c2314fff7a06666a46e1f31b45c0a0a34f486`. |
| Root frozen session `16470` | PASS | 90 UTs: 0 failures, 0 errors, 0 skipped; config-only IT: 1/0/0/0; overall exit 0. |

## Decisions

- Change only exact published schema expectations: M2 `business-flows-capsule-projection-v6` → v7, public Capsule v4 → v5, and normalized public Gap v1 → v2.
- Derive source-vs-program Gap assertions from the reopened Step 04 ledger and existing public artifacts; do not hardcode a blanket `FLOW` scope or create a provenance helper framework.
- Keep the model-view projection mechanical and local to existing assertions: remove exactly the three program-only fields, preserve typed evidence references, then compare canonical bytes and existing task-input SHA behavior.
- Recompute only the fixed oracle's policy registry Base64 and its matching artifact-policy registry ID/SHA after the three policy schema substitutions; preserve all other controls, profiles, budgets, and source values.
- Preserve the honest production boundary: this slice records the stale reader gate and does not migrate production constants or projection behavior.

## Blockers

- The carrier assertions, semantic oracle values, and owned JSON final-byte correction are complete and verified. No carrier blocker remains. Full Step 05 is not complete; source/domain acceptance remains blocked.

## Exact next action

- Release this completed bounded carrier-consumer slice; do not rerun or broaden it here.

## Resume checks

- Do not modify production constants, fixtures, source, profiles, budgets, or any test outside the six named Java paths.
- Do not run the fixed config selector or the real fixed-repository acceptance IT in this slice.
- Preserve the distinction between this stale reader version RED and the later intended provenance-stripping RED; no production or additional consumer work is authorized in this activity.
