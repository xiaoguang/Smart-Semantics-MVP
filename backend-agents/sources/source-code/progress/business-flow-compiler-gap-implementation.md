# Progress: Business Flow compiler Gap normalization implementation

- Status: COMPLETE
- Agent role: Terra/xhigh compiler-Gap GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Apply the published raw compiler Gap scope normalization at the M3 public boundary only. Raw M1 `ENTRY` and `FLOW` scope values must normalize to public `FLOW` while preserving exact affected IDs, typed evidence, source-ledger priority, and all existing origin handling.
- Approved inputs: Published Step 05 §8.1.3, the frozen Luna compiler-only normalization RED, and current `FlowPublicationSpecifier` behavior.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this tracked progress checkpoint before production edits.
- Recorded the frozen combined RED: numeric exit 1; 9 tests, 1 failure, 0 errors, 0 skips. The two graph-local coverage methods, bounded public-closure method, and five existing provenance methods passed. The sole failure is the new compiler-only actual zero-Flow assertion at `FlowPublicationSpecifier.normalizeCompilerGap` line 596, where raw M1 `ENTRY` is rejected.
- Confirmed the published contract: raw M1 compiler `ENTRY` and `FLOW` are both valid sources for a public normalized `FLOW` Gap. Source-ledger Gap priority remains unchanged and still normalizes from ledger authority rather than this raw branch.
- Expanded only `FlowPublicationSpecifier.normalizeCompilerGap`'s raw scope allowlist to accept `ENTRY` and `FLOW`; its existing normalized public scope remains `FLOW`. A raw scope outside that documented pair still fails closed.
- Formatted the one owned production file and ran the assigned combined selector after all Java was frozen.

## Current state

- This compiler-only normalization slice is complete. The assigned post-format combined selector passes all nine direct tests.
- Full Step 05 remains unaccepted. This completion does not expand into replay, provider stripping, bounded closure, or any other deferred vertical.

## Changed files

- `progress/business-flow-compiler-gap-implementation.md` (owned implementation and verification record)
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java` (raw compiler scope allowlist only; public normalized scope unchanged)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen parent-coordinated combined selector | RED; numeric exit 1 | 9 tests, 1 failure, 0 errors, 0 skips. Only the compiler-only raw `ENTRY` normalization assertion fails; graph, bounded, and prior provenance subsets pass. |
| Absolute one-file Spotless apply | GREEN; numeric exit 0 | The selected `FlowPublicationSpecifier.java` file was already clean. |
| Absolute one-file Spotless check | GREEN; numeric exit 0 | The selected production file required no changes. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BoundedBusinessFlowPublicationTest,BusinessFlowProvenanceTest,ControlFlowGraphBuilderTest#sharesOneProfileStopGapAcrossEntriesThatReachTheSameUnsupportedGuard,DataFlowGraphBuilderTest#recordsALocalGapForAnUnsupportedExactMapperBoundaryActual test` | GREEN; numeric exit 0 | 9 tests, 0 failures, 0 errors, 0 skips: 6 provenance, 1 bounded public-closure, and 1 each ControlFlow/DataFlow local-Gap coverage method. |
| Scoped `git diff --check` | GREEN; numeric exit 0 | No whitespace errors in the production file or owned progress records. |

## Decisions

- `normalizeCompilerGap` accepts raw `ENTRY` or `FLOW` only and continues emitting the existing public `FLOW` value.
- Preserve exact `affectedSemanticIds`, evidence-node validation and descriptor projection, source-ledger priority, `originKind`, nullable ledger references, M1 wire, schemas, IDs, and all noncompiler branches.
- Do not change compiler output, source ledger processing, budget Gap behavior, test/fixture code, schema/version, provider behavior, or replay/closure handling.

## Blockers

- None for this bounded compiler-only normalization slice. Full Step 05 remains unaccepted pending separately scoped work.

## Exact next action

- Release the Maven lease. Do not change compiler output, public schema, source-ledger handling, or adjacent Step 05 work without a separate frozen RED and scope assignment.

## Resume checks

- Maven lease is released. Full Step 05 remains unaccepted; no source/network/Provider/commit/push/subagent action was taken by this owner.
