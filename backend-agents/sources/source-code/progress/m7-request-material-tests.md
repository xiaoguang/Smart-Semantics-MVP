# Progress: M7 request-material public seam RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add one public-seam RED test for M7 MODEL_SAFE requestMaterials persistence.
- Approved inputs: Existing frozen ProgramGraphsPublicFixture, M6 publisher, M7 compiler/publisher, and existing process-task test helpers only.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Audited the real M7 public fixture path used by `BusinessProcessTaskModulePublisherTest`.
- Confirmed the frozen `CrossFlowCandidateCompilerTest.processInputs(...)` reader registers only the run request, profile bundle, and resource budget. It does not register the prompt bundle or schema bundle bytes named by the run request, and it does not register the runtime artifact bytes named by `flowInterpretation.processModelRuntimeRef`.
- Confirmed `BusinessProcessTaskModulePublisher` currently accepts only the M6 publication and `BusinessProcessTaskCompilation`; it has no frozen-input reader from which a test could independently obtain prompt instructions, response-schema references, or the full runtime identity.
- Therefore an honest request-material RED cannot assert the required `p1Base.instructions`, `p1Base.responseSchemaRef`, `p2Plan.instructions`, `p2Plan.responseSchemaRef`, or complete `expectedRuntime` from frozen fixture inputs without changing fixtures or inventing constants. Per the task boundary, no test method was added and no production/design/schema/fixture file was changed.

## Current state

The target behavior is specified in `docs/analysis-steps/06-flow-interpretation.md` §6.7.2.2: one request material per MODEL_SAFE shard, none for NO_MODEL, with frozen prompt/schema/runtime values and P1/P2 plan records. The required upstream fixture material is absent, so adding a test that fabricates these values would violate the public-seam and frozen-input requirement.

## Changed files

No test file was changed because the required frozen prompt/schema/runtime inputs are not present in the approved fixture. No production, design, schema, or fixture changes were made.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n "values.put\\(|promptBundleRef|schemaBundleRef|processModelRuntimeRef" src/test/java/.../CrossFlowCandidateCompilerTest.java` | PASS | Reader values contain only run request, profile, and budget; prompt/schema/runtime contents are not registered. |

## Decisions

- Expected values must come from fixture inputs or reopened upstream artifacts, never from the current publisher output, provider response, or hard-coded defaults.
- If the existing fixture cannot expose the frozen prompt/schema values needed by the public seam, record that exact upstream limitation here and do not invent values.
- Exact limitation: run request carries only references for `prompt-bundle:m6` and `schema-bundle:m6`; the fixture reader has no bytes for either. Profile carries only a runtime reference; the runtime artifact bytes/full `ModelRuntimeIdentityV1` are not registered. M7's current constructor also has no input-reader seam.

## Blockers

## Exact next action

Parent agent must add the M7 producer/input-reader seam (or explicitly scope a fixture-only input registration change) before requesting the required request-material RED. Do not infer or hard-code instructions, schema refs, or runtime identity.

## Resume checks

- Read this file before continuing.
- Run only the direct M7 selector.
- Update this file in place after the test and after verification.
