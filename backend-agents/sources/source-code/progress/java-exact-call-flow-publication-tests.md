# Progress: exact-call Flow publication handoff

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Migrate the existing `FlowCompilationModulePublisherTest` v2 descriptor/envelope expectations to the published Step05 Flow compilation v3 contract and verify the public publication seam; the fixture's single M1 policy registration is included as the narrowly authorized contract-alignment correction. No production, graph-shape, design, Capsule, Step06, or Git changes.
- Approved inputs: Published Step05 §8.1.2 Flow compilation contract, current v3 Flow compiler tests, and the existing public publisher test.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress note before changing the publication test.
- Applied the two published v3 schema expectations and added minimal non-vacuous upstream checks for two compiled Flows and four actual exact-call signals.
- Terra's smallest publisher/policy replacement was applied, but the test fixture still registered `business-flows-flow-compilation-v2`; the resulting publication attempt failed with `ARTIFACT_POLICY_NOT_FOUND` before the publisher could install the v3 artifact.
- Updated only that fixture registration to `business-flows-flow-compilation-v3`.
- The targeted publisher/compiler rerun is green after the fixture correction; no graph/source shape or production changes were needed.

## Current state

- The existing publisher test expects `business-flows-flow-compilation-v3` in both the descriptor and serialized envelope, and the fixture now registers the same v3 policy.
- The test still verifies one payload/file/type, 13 predecessors, serialized full signal equality, coverage IDs, and independently calculated compilation identity; it also requires two compiled Flows and four actual `EXPLICIT_CALL` signals from the standard v3 fixture.

## Changed files

- `progress/java-exact-call-flow-publication-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (one M1 policy schema literal only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationModulePublisherTest test` | RED (production still publishes v2) | 2 tests, 2 failures, 0 errors, 0 skips; both the descriptor and serialized envelope returned `business-flows-flow-compilation-v2` while the migrated test requires v3. The two compiled Flows and four exact-call upstream assertions passed before those schema assertions. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationModulePublisherTest test` (after Terra's writer/policy replacement, before fixture correction) | RED (fixture registration mismatch) | Publication failed with `ARTIFACT_POLICY_NOT_FOUND` because `ProgramGraphsPublicFixture` registered only `business-flows-flow-compilation-v2`; no test assertion or fixture graph/source premise was reached. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowCompilationModulePublisherTest,EntryRootedFlowCompilerTest test` | PASS | 8 tests, 0 failures, 0 errors, 0 skipped; publisher 2/0/0/0 and compiler 6/0/0/0. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java spotless:apply` | PASS | Spotless selected exactly 1 fixture file; 0 changed, 1 already clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java spotless:check` | PASS | Spotless selected exactly 1 fixture file; 0 needed changes. |
| Pinned one-file Spotless apply | PASS | Selected exactly 1 owned Java file; 0 changed, 1 already clean. |
| Pinned one-file Spotless check | PASS | Selected exactly 1 owned Java file; 0 needed changes and the build succeeded. |

## Decisions

- Change only the two test expectations from `business-flows-flow-compilation-v2` to `business-flows-flow-compilation-v3`, and align the fixture's one M1 policy literal with that already-published v3 contract.
- Preserve the complete serialized Flow/signal, predecessor, coverage, and identity assertions; add no new schema or fixture shape.

## Blockers

- The prior publisher RED and subsequent `ARTIFACT_POLICY_NOT_FOUND` fixture RED are retained as historical evidence. The fixture literal is corrected and the two named classes now pass.

## Exact next action

- Release Maven to root/Terra. The M2 shared-source-span preparation remains read-only and separately owned by `progress/java-exact-call-shared-span-tests.md`.

## Resume checks

- This bounded handoff is complete. Keep the fixture change limited to its single M1 policy literal; do not edit production, graph/source fixture shapes, design, Capsules, Step06, or Git.
