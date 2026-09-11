# Progress: M7 frozen material reader seam

- Status: COMPLETE
- Agent role: Bounded design authority
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10 — bounded seam ruling complete; no implementation started
- Scope: M7 frozen prompt/schema/runtime material ownership, reader injection, and one next RED specification only.
- Approved inputs: scoped AGENTS; Step06 sections 6.7.1–6.7.2.2; existing process input reader, M6/M7 producers and fixtures; M9 input rules.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Checked dirty shared worktree; preserved all existing changes.
- Read scoped rules and the codebase-design skill; located current M7 missing reader injection.
- Confirmed M6 stores `analysisRunRequestRef` both in its payload and upstream references. The run request already points to profile, prompt, schema, and resource-budget inputs.
- Confirmed M7 compiler and publisher currently accept only `CanonicalModuleArtifactStore`; publisher independently rebuilds compilation, so both need the same existing frozen-input reader.
- Confirmed the current process fixture registers only run request, profile, and budget bytes; it has no prompt/schema bundle, response-schema, or runtime bytes.
- Confirmed a second fixture defect relevant to the proposed RED: profile/schema references use seed-derived digests, while `ProgramGraphsPublicFixture.controls()` uses seed digests and null prompt digest. Merely adding map entries cannot prove actual frozen material integrity.

## Current state

- Design ruling is complete. Parent may record the small constructor/fixture clarification in Step06 section 6.7.2.2 and then delegate the single RED below. Per the user's pause-at-closeout instruction, this agent has not begun that follow-up.

## Changed files

- progress/m7-frozen-material-seam-design.md only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | PASS | Shared worktree is dirty; no unrelated edits made. |
| Targeted source/document reads | PASS | M6 already owns a ProcessInputArtifactReader and persisted analysisRunRequestRef; M7 currently accepts only the module store. |
| Maven / Provider / network | NOT RUN | Outside this bounded design task. |

## Decisions

- No production, test, design-document, schema, or runtime artifact edits in this task.
- No Maven, Provider, network, or source scans.

### Minimal producer seam

Reuse `ProcessInputArtifactReader.reopen(ArtifactReference)` unchanged. Do not introduce another Store, registry, input request, public product method, or model call.

The exact constructor change is:

```java
BusinessProcessTaskCompiler(
    CanonicalModuleArtifactStore moduleArtifacts,
    ProcessInputArtifactReader inputArtifacts)

BusinessProcessTaskModulePublisher(
    CanonicalModuleArtifactStore moduleArtifacts,
    ProcessInputArtifactReader inputArtifacts)
```

`compileTasks(ModulePublicationReference m6Publication)` and `publish(m6Publication, compilation)` stay unchanged. The publisher creates its independent compiler with the injected reader and compares the full rebuilt compilation, including `requestMaterials`, before any installation. Remove the one-argument constructors as part of the eventual implementation; do not retain a default reader or fallback materials.

The application composition caller owns the existing frozen-input lookup adapter and passes the same registered artifact universe to M6 and M7. M6 remains owner of the persisted `analysisRunRequestRef`; M7 obtains it only from the freshly reopened M6 payload and verifies it is in M6's declared upstream references. No caller-supplied replacement run reference is added.

### Exact material lookup

After partitioning establishes at least one MODEL_SAFE shard, M7 follows this fixed chain:

1. M6 `analysisRunRequestRef` → exact canonical run request.
2. Run request `promptBundleRef` → the two existing `processTasks` members' `instructions` arrays.
3. Run request `schemaBundleRef` → each process task's `responseSchemaRef` → freshly reopened response-schema bytes.
4. Run request `profileBundleRef` → `/flowInterpretation/processModelRuntimeRef` → full `ModelRuntimeIdentityV1` bytes.

Each read must return the requested exact registered artifact, parse as canonical JSON, and have actual byte SHA equal to the reference SHA. The run-request profile/schema/prompt reference SHA values must match M6 receipt controls. Artifact IDs are validated by the registered lookup; no new ID formula is invented here. Missing, extra, malformed, unresolved, or mismatched process material fails with existing `PROCESS_MODEL_REFERENCE_INVALID` before M7 writes. Only the required process members are closed; unrelated existing bundle sections remain untouched.

The compiler creates one `ProcessTaskRequestMaterialV1` per safe shard from those values; only its `taskShardId` differs where all shards share the same frozen materials. Material values enter the M7 module identity and do not enter the shard identity. The P2 value remains an `AFTER_P1_TERMINAL` plan with no P1 result. When `S=0`, `requestMaterials=[]` and M7 does not open prompt/schema/runtime inputs. M7 performs zero Provider calls.

M9 continues to consume the completed M7/M8 publications. It does not acquire a reader or reopen external input bundles; its existing interface and fifteen-file boundary stay unchanged.

### Fixture prerequisite

Author small test-only prompt instructions, P1/P2 response schemas, and a four-field scripted runtime identity as explicit fixture inputs. Test-authored content is legitimate frozen input; production default content is not. Canonicalize them before constructing M6, calculate each actual SHA, and register eight immutable input entries: run request, profile, resource budget, prompt bundle, schema bundle, P1 schema, P2 schema, and runtime.

Build parent objects bottom-up: response schemas/runtime → prompt/schema/profile bundles → run request. The profile/schema/prompt control digests must be injected when the upstream graph fixture is created and propagate into all of its source/discovery/graph/fact/flow/registry publications. Use a small opt-in fixture construction overload accepting those three digests; preserve the existing policy and toolchain setup. Do not rewrite already installed publications or reuse the old seed-SHA/null-prompt fixture for this integrity assertion.

The existing process input map can remain the test Adapter if it stores immutable canonical bytes, checks exact reference lookup and byte SHA, and fails on missing entries. No fixture-specific filesystem Store is needed. Keep the returned `ProcessInputs` with the M6 fixture result or pass it explicitly through the helper so the same reader is available to both M7 compiler and publisher. Do not recreate it independently after M6.

### One next RED

Exact candidate method: `BusinessProcessTaskModulePublisherTest#persistsFrozenRequestMaterialsForEachModelSafeShardWithoutModelWork`.

Input: the existing two-entry shared-Java-call source, built with the real control digests above; real M3/BusinessFlows/M6 publications; one MODEL_SAFE two-Flow shard; frozen P1 and P2 instruction arrays containing distinct fixture markers; two distinct response-schema refs with registered canonical bytes; one exact runtime ref/value. Construct M7 compiler/publisher with the same registered reader. A recording reader is permitted; it records only requested refs and returns those exact immutable fixture bytes.

Expected after fresh reopen: exactly one `requestMaterials` item whose shard ID matches the safe shard; prompt/runtime refs and runtime value equal the independently authored inputs; `p1Base` has kind P1, ordinal 1, exact P1 schema ref and instructions; `p2Plan` has kind P2, ordinal 2, `AFTER_P1_TERMINAL`, exact P2 schema ref and instructions; no P1 result/task/round/reviewed-hypothesis fields; the reader observed both response-schema refs and runtime ref; Provider calls remain zero. Assert the module payload identity covers the full material via the existing independent canonical module oracle. Do not derive expected material from M7's output.

Current intended RED: the two-argument constructors and `requestMaterials` persistence are absent. Reflection may establish this missing public seam as an assertion failure while keeping test compilation valid. Once the constructors exist, the same test must advance to frozen-value and publication assertions. Fixture construction/hash failure is not an acceptable RED.

### Required documentation change

A small Step06 §6.7.2.2 clarification is necessary before implementation: record the two injected constructors, M6 run-ref lookup, publisher recomputation, matching control/actual SHA checks, and the opt-in fixture precondition. It changes only this adjacent-module implementation seam; it preserves process semantics, model-visible inputs, runtime keys, M9 interface, and the 15/57 output counts. No separate architecture redesign or new state is needed.

## Blockers

- None; this task is a bounded design decision.

## Exact next action

- Parent records this bounded clarification in Step06 §6.7.2.2, then later authorizes one fixture-ready Luna RED at the named method. This agent is finished and makes no further changes.

## Resume checks

- Read this progress and check git status before any owned update.
- Reopen cited producer/fixture lines; do not infer implementation from this note alone.
