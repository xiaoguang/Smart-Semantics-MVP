# Progress: Whole repository material planning

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: No model Provider; zero-provider planning and offline verification only
- Started: 2026-09-11 12:56 UTC
- Last updated: 2026-09-11 15:34 UTC
- Scope: Use only the approved fixed jshERP commit and existing Step01–05 production chain to obtain an observable zero-provider whole-repository material plan, then report material/coverage and request-count facts. Do not invoke Luna, Codex, customer Maven or a network source.
- Approved inputs: User-approved business-first implementation plan; fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; existing local full Git object database.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Confirmed the approved local jshERP object database resolves the fixed full commit and contains 719 tracked tree entries.
- Confirmed the existing module workspace contains prior offline Step01–05 and material-planning audit directories, while no current public run output has been claimed as a complete repository business report.
- Extended the explicit fixed-repository acceptance seam so that, after it has durably published Steps 01–05, it invokes the production `PersistedBusinessRunExecutor.buildMaterials` operation. Its injected Provider throws on invocation, making a nonzero Provider call an immediate test failure.
- Rebuilt the frozen acceptance policy registry so it names the current Step05 `flow-compilation-v4`, `capsule-projection-v8`, `evidence-capsule-v6`, and `flow-slices-v4` schema contracts. Its content-addressed registry ID and digest were recalculated; the preflight test now passes.
- Ran the newly extended fixed-repository selector once. It preserved a fresh capture, source inventory and application-discovery publication, then stopped at program graphs with `OutOfMemoryError` before any Provider or material planning could occur. The new report records the exact boundary and all successful upstream outputs.
- Reran in a separate fresh workspace with both Maven and Surefire child heaps explicitly set to 8 GiB. The same Step03 failure occurred, proving the first failure was not merely a missing child-JVM heap setting.
- Located the structural cause: the CodeStructure declaration inventory copied every discovered HTTP entry ID onto every declaration node. On this repository that duplicated 339 owners across each Java/XML declaration, even though those declarations are neutral inventory and actual ownership is established only by reachable call/control/data traversal.
- Added a direct regression assertion, then changed the CodeStructure builder to persist neutral declaration and parameter nodes with an empty owner list. The direct builder selector now passes; dependent graph tests and the full fixed-repository material plan remain to run.
- Ran the five direct graph selectors after the neutral-owner correction: all 65 tests passed. The first corrected full-repository run persisted Steps01–04 but correctly stopped in Step05: its persisted-input reader still rejected the intentionally ownerless `CODE_STRUCTURE` gaps that the Step03 contract permits for dynamic SQL with no proven entry owner.
- Corrected that reader boundary: only ownerless `CODE_STRUCTURE` gaps are accepted as repository-level context; every Call, ControlFlow and DataFlow gap still requires an existing affected entry. These neutral gaps are not attributed to every Flow.
- The next run reached EntryContext assembly and rejected repeated call shapes because its old stable key omitted the source occurrence. Added a focused failing value-contract test: two same-text calls with distinct frozen evidence locations must both survive. The pending production correction makes occurrence evidence part of that key rather than discarding either call.
- The subsequent run passed flow compilation and exposed the first Capsule reader defect: it parsed `argumentExpressions` as a sorted unique reference set. Real Java calls may repeat a positional argument, so the projector now preserves argument order and repeats while retaining strict sorted/unique checks for reference sets and limitations.
- That run then reached final Capsule construction. It showed a legacy constraint requiring every readable Flow to have an admitted Fact, even though 31 real compiled Flows include source/terminal context before any strict Fact matches. Added a focused RED test and removed only that requirement: nonempty outcome, selected source span and obligation remain mandatory, and the no-Fact condition is carried as a limitation instead of blocking business reading.
- The next full run completed the full production Step05 publication (all five public semantic files and receipt were durably written). Its final test-only inspection used an AssertJ API that rejects an empty comparison set; changed the assertion to run only when ineligible flows exist. This is an acceptance-test correction, not a production behavior change.
- Reran the full fixed-repository selector in a new ignored workspace after all targeted corrections. It completed Steps01–05 and the production materials-only builder in 267 seconds with zero Provider calls. The immutable run records 719 tracked files (686 analyzable text, 33 media), 339 discovered entries, 31 compiled Flow/Capsule pairs, 308 Flow gaps, and 339 material/coverage pairs: 31 `FLOW_PREFERRED` plus 308 `ENTRY_SOURCE_FALLBACK`.
- Opened the persisted `business-materials.jsonl` rather than relying on test objects. It contains 678 JSONL records (one material and one coverage result per discovered entry) and preserves a readable controller/service snippet, call observations, conditions, returns and limitations. It does not claim business interpretation or a nine-section report.

## Current state

- The materials-only whole-repository target is complete. The next work is a quality inspection of several persisted packets and an existing activity-to-report scripted-path check; neither task may invoke a product Provider.

## Changed files

- progress/whole-repository-material-planning.md
- src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java
- src/test/resources/analysis/flow/fixed-repository/fixed-repository-acceptance-config.json
- src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilder.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java
- src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java
- src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java
- src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilderTest.java
- src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationTest.java
- src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java
- docs/analysis-steps/03-program-graphs.md
- docs/analysis-steps/05-business-flows.md
- docs/analysis-steps/06-flow-interpretation.md
- docs/DESIGN.md
- README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git -C .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580 rev-parse 8c30ce…^{commit}` | PASS | Fixed commit resolves locally; repository is not shallow and its tracked tree has 719 entries. |
| Fixed-repository acceptance extension | READY | It persists material planning only after its current complete Step01–05 chain and rejects any Provider call. |
| First full fixed-repository selector | BLOCKED at Step03 | Steps01–02 persist successfully; `OutOfMemoryError` occurs in program graphs before the materials-only module. |
| 8 GiB fixed-repository selector | BLOCKED at Step03 | Same failure; dump locates allocation during canonical reopening of an oversized CodeStructure publication. |
| `FixedRepositoryBusinessFlowsIT#loadsAndValidatesFrozenAcceptanceOracleBeforeAnySourceRead` | PASS | Rebuilt content-addressed policy registry matches current Step05 schema versions. |
| `CodeStructureGraphBuilderTest#producesDistinctJavaAndMyBatisStructureFromFrozenSourceBytes` | PASS | Neutral structure declarations no longer receive every discovered entry as a false owner. |
| Five direct graph selectors | PASS | 65 tests; neutral declaration ownership preserves Call, ControlFlow and DataFlow graph behavior. |
| Corrected full-repository selector | BLOCKED at Step05 | Steps01–04 persisted; ownerless dynamic-SQL structure gaps were valid upstream data but rejected by the downstream reader. |
| Recorrected full-repository selector | BLOCKED at Step05 | EntryContext rejected two separate call-site occurrences with the same display shape; no call is being discarded. |
| Third corrected full-repository selector | BLOCKED at Step05 M2 | Capsule reader incorrectly treated repeated positional call arguments as invalid set members. |
| Fourth corrected full-repository selector | BLOCKED at Step05 M2 | Legacy Capsule contract rejected readable source context solely because no strict Fact was admitted. |
| Fifth corrected full-repository selector | PRODUCTION Step05 PASS | All Step05 artifacts persisted; only a test assertion rejected the valid empty ineligible set. |
| Sixth corrected full-repository selector | PASS | 1 test, 0 failures/errors/skips; 267 seconds; Steps01–05 plus materials-only planning completed with zero Provider calls and 339 covered entries. |

## Decisions

- The materials-only target is the required zero-Provider checkpoint before any later small-package or full-repository budgeting.
- The current measured output replaces the historical 337-material diagnostic as the branch's whole-repository material evidence. It is still not semantic acceptance.

## Blockers

- None for the materials-only run. Product-model work remains deliberately outside this completed checkpoint and requires its own input-quality review and authorization.

## Exact next action

- Inspect representative `FLOW_PREFERRED` and `ENTRY_SOURCE_FALLBACK` packets from the persisted run, then run the direct scripted activity-to-report chain selector. Do not start a product-model request.

## Resume checks

- Read this file, inspect the persisted material JSONL and its coverage companion records, then verify the activity-to-report scripted path before considering any Provider action.
