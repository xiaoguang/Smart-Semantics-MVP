# Progress: M7 formal process Gap design review

- Status: COMPLETE
- Agent role: Independent design reviewer
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Complete the parent-authorized minimal docs-only correction of M7 formal Gap/request material and M8 group-local ordering. Only this progress file and docs/analysis-steps/06-flow-interpretation.md may change.
- Approved inputs: Scoped AGENTS.md, Step 06 detailed design, M7 carrier rulings/progress, M7/M8 production and directly relevant tests.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Confirmed a dirty shared worktree and preserved all pre-existing changes.
- Read scoped instructions and the M7 no-model and model-safe limitation carrier rulings.
- Read Step 06 §6.7, §7.1/§7.2, current M7 compiler/publication records, current M8 runner/aggregate consumer, and the direct M7 carrier/M8 multi-shard test assertions.
- Used the codebase-design review method: compare producer guarantees, consumer requirements, cardinality, and observable persisted values at the public seam.
- Completed the bounded review with `CONDITIONAL`: the wrapper approach is sound, but the formal carrier design is not yet closed. Three actionable P1 themes are recorded below; the first has three precise carrier inconsistencies. No P0 was found in this scope.
- Applied the parent-authorized docs-only correction to Step 06 §3/§4, §6.7.2.2, §7.1 and §7.2: upstream wrapper fields and exact affected scope, Gap-before-shard identities, fixed P1 material/P2 plan timing, group-local ordinal validation and deterministic order.
- Corrected the earlier P1 GAP example to reference the same-shard wrapper, and named exactly two sequential RED targets plus their consumers.
- Read back every changed contract section and passed the two-file `git diff --check`.

## Current state

- Review and the authorized bounded design corrections are complete. The three design ambiguities are now resolved in the authoritative document; current production/tests still require the planned RED/GREEN work. M7 and Step 06 are not declared implemented or accepted.
- Existing NO_MODEL wrappers correctly preserve the original Capsule Gap value and use distinct process-owned IDs. Existing MODEL_SAFE limitation bindings correctly keep hashes and lineage outside the model packet. The current internal carrier still cannot be published as the formal Step 06 value under the current target document.

## Changed files

- progress/m7-process-gap-design-review.md
- docs/analysis-steps/06-flow-interpretation.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | READ | Shared worktree contains pre-existing production, tests, and design changes; no changes claimed here. |
| Scoped design/source/test inspection | COMPLETE | Findings below are based on exact current producer/consumer code and documented fields, not timestamp or Agent status. |
| Maven / Provider / network | NOT RUN | Explicitly excluded by the review brief. Existing progress records cite bounded GREEN; no new runtime claim is made here. |
| git diff --check -- progress/m7-process-gap-design-review.md | PASS | Only the owned progress file changed in this review. |
| Read-back plus git diff --check -- docs/analysis-steps/06-flow-interpretation.md progress/m7-process-gap-design-review.md | PASS | Parent-authorized follow-up changes only these two Markdown files; no Maven, Provider, network, code, tests, or Schema change. |

## Decisions

- No Maven, live Provider, source scanner, network, or production/schema/test/design edits in this task.

### Original review verdict and retained design

`CONDITIONAL`, not `ACCEPT`. Preserve the following design: an upstream Capsule Gap is immutable; each M7 owner shard receives its own process wrapper; the wrapper embeds the original `gapView` and source-Flow subset; NO_MODEL has no packet/call; MODEL_SAFE limitations use local keys and do not turn an eligible shard into NO_MODEL. Preserve disjoint `P = O ⊎ L`, zero M7 calls, fifteen Step 06 files, and the 57-output total.

The line references below describe the pre-correction review snapshot. The parent-authorized follow-up has installed the listed finite corrections in §6.7.2.2 and synchronized §7.1/§7.2; they now serve as the implementation/test work list, not remaining design choices.

The following are finite adjacent-module corrections. They do not require a new analysis step, new recovery subsystem, extra model round, or stronger evidence collection.

### P1-1a — Formal Gap shape does not admit the implemented wrapper kinds

- Evidence: `docs/analysis-steps/06-flow-interpretation.md:1089` requires both upstream wrapper kinds to satisfy §7.1. At lines 1654–1679 and 1716–1726, the closed `gapCode` matrix omits `PROCESS_UPSTREAM_MODEL_INELIGIBLE` and `PROCESS_UPSTREAM_LIMITATION`, and the formal record omits `upstreamGap`. Current `ProcessInterpretationGapV1.java:7` has only the smaller development carrier. It also uses `MAX_FLOWS` etc., while formal `limitKind` is `FLOW_COUNT/RELATION_COUNT/SIGNAL_COUNT/REGISTRY_ITEM_COUNT/INPUT_BYTES`.
- Impact: a correct existing wrapper cannot satisfy formal publication; adding empty fields without rules would leave Terra/Luna inventing closure or relabeling a Capsule Gap as a budget failure.
- Minimal design correction: add the two existing wrapper codes and exact `upstreamGap` nullable field to the single authoritative formal record and discriminator matrix. Preserve the original seven-field `gapView` unchanged. Specify each pre-model wrapper's hypothesis/claim/model-task arrays as empty, failure and limit fields as null; derive only available Fact/Proof/Evidence/source fields from the already frozen M6 records and freeze the exact nonempty `searchedScopeRefs` selection. Missing source/Proof is not fabricated: permitted empty source closure ends at the already documented `SEARCHED_SCOPE`. Fix the five budget enum values at the producer, with no alias reader.

### P1-1b — Gap and shard identity rules conflict and can create a cycle

- Evidence: formal Gap identity at document lines 1706–1714 excludes exactly `gapId,taskShardId` with domain `flow-interpretation-process-gap-id-v1`; formal shard identity at 1797 includes its Gap IDs and reader bindings. The safe-limitation ruling at `progress/m7-safe-limitation-gap-design.md:103` instead includes `taskShardId` in the Gap hash. `BusinessProcessTaskCompiler.java:487` implements that older rule. NO_MODEL wrappers use another domain at line 467; budget Gaps use an unframed reduced projection at line 816; shard IDs at line 804 use a reduced four-field projection and a different prefix.
- Impact: merely upgrading the shard to its formal identity creates `shard → limitation Gap ID → shard`. Current IDs also do not cover the formal values M9 is required to verify.
- Minimal design correction: supersede the old progress-only identity rules in the authoritative detailed design. Use the single existing formal Gap domain and full projection excluding only Gap/shard IDs for all M7 Gap kinds; include the new `upstreamGap` complete value. Compute wrappers, then formal shard including packet/bindings, then fill `taskShardId`. All owner relation and context fields already distinguish different owner shards. Do not retain old formulas as compatibility paths.

### P1-2 — Complete P2 request cannot be produced by M7 before P1 executes

- Evidence: document line 1100 assigns a complete `ProcessModelRequestV1` audit wrapper to M7, but lines 1516–1526 and 1734 require P2's actual P1 task/round IDs and complete reviewed hypotheses. The identity sequence at line 1808 correctly creates P2 only after P1 terminates. Current M7 output has shard/packet/bindings only; M8 currently constructs its small request in `BusinessProcessInterpretationRunner.java:291` and has no prompt/schema material in that request.
- Impact: a literal implementation of the carrier milestone would need to invent P1-dependent values or create a second P2 request later. No existing M7 carrier can supply those values before execution.
- Minimal design correction: state that M7 owns immutable packet, bindings, and frozen request controls/material, including exact prompt/schema/runtime references and readable instructions. M8 materializes the complete P1 wrapper before its call and complete P2 wrapper after terminal P1; P1 GAP/FAILED gives an empty reviewed set and a persisted NOT_RUN P2 task. The design must identify where those prompt/schema values are already frozen or add an explicit adjacent input reference, never default them. Only `readerApplicationRequest` enters the Provider. Do not require two complete P1/P2 requests in the M7 pre-execution payload.

### P1-1c — MODEL_SAFE affected-Flow semantics differ between ruling and implementation/test

- Evidence: `progress/m7-safe-limitation-gap-design.md:86` defines `affectedFlowSliceIds` as the exact context subset whose Capsule carries the Gap. `BusinessProcessTaskCompiler.java:351` passes all shard context Flows; `BusinessProcessTaskCompilation.java:83` and `BusinessProcessTaskCompilerTest.java:460` demand the whole context set instead. Nested `upstreamGap.sourceFlowSliceIds` already records the true subset.
- Impact: an uncertainty confined to one Flow is described as affecting every Flow in a safe packet, and the current test would reject the written design.
- Minimal design correction: keep the narrow written meaning for MODEL_SAFE `affectedFlowSliceIds = upstreamGap.sourceFlowSliceIds`, while NO_MODEL keeps the entire affected shard context because it blocks the whole task. The shard itself already preserves the wider context; update only the direct safe-limitation oracle/producer/constructor. Do not broaden the original Gap's source ownership.

### P1-3 — M8 assumes globally unique shard ordinals, M7 correctly restarts within each group

- Evidence: the formal shard field at document line 1402 is zero-based and contiguous within its group. `BusinessProcessTaskCompiler.java:108` starts a fresh shard list for each group and passes `shards.size()` as ordinal. The M8 all-safe ruling says every ordinal is globally unique (`progress/m8-all-safe-shards-design.md:53`), and `BusinessProcessInterpretationExecutionPublisher.java:171`/179 enforces a global `Set<Integer>`.
- Impact: a legitimate repository with two disconnected groups, each starting at ordinal 0, fails before any Provider call. Existing single-group multi-shard GREEN does not cover this full-repository case.
- Minimal design correction: preserve formal group-local numbering. M8 validates unique `(processEvidenceGroupId, shardOrdinal)`, contiguous ordinals within each group, and globally unique shard IDs. Execute in deterministic `(groupId UTF-8, ordinal, shardId UTF-8)` order. Do not mutate M7 ordinals to appease the current consumer.

### Small consumer consistency detail

The formal NO_MODEL reason is `NO_MODEL_SHARD` at document line 1779, while M8 currently writes `PROCESS_MODEL_INELIGIBLE` at `BusinessProcessInterpretationExecutionPublisher.java:289`. Include this constant correction in the later formal M8 carrier work; it does not warrant a separate design cycle.

### Smallest next Luna RED and Terra GREEN

First freeze P1-1's exact formal wrapper projection in the authoritative detailed design. Then add exactly one public M7 publication test, reusing the existing three-Flow mixed-eligibility input and real M6 publication:

`BusinessProcessTaskModulePublisherTest#persistsFormalShardOwnedUpstreamGapsWithoutModelWork`

The test compiles through the real M7 seam, publishes, fresh-reopens `process-task-shards.json`, and independently asserts:

- each NO_MODEL shard resolves to exactly its complete formal wrapper, not the raw upstream ID;
- the same original Capsule Gap used by two owner shards yields two distinct process wrappers;
- original nested `gapView` and exact source-Flow subset survive unchanged;
- the safe limitation's affected-Flow set is its actual source subset, while a NO_MODEL wrapper retains the whole blocked context;
- all formal fields, required nulls, empty pre-model arrays, real evidence/search references, and the single domain-framed Gap/shard identities match an independent projection;
- every process Gap belongs to `O ⊎ L` exactly once and no Provider is invoked.

Expected current RED is the missing formal fields/identity, after valid M6/M7 setup. Do not add unrelated Provider or packet tests to this first method. Terra changes only the M7 record/compiler/publisher necessary for this formal persisted value.

After that GREEN, one separate consumer RED is warranted: `BusinessProcessInterpretationExecutionPublisherTest#acceptsGroupLocalZeroOrdinalsAcrossTwoIndependentGroups`. Use two actual disconnected compiler-generated groups, not an ordinal rewrite of a single group. Give each group a valid NO_MODEL shard with ordinal 0 so zero Provider calls suffices to prove that M8 accepts both groups and preserves both complete Gap values. This directly isolates P1-3 without expanding the model grammar.

P1-2 requires an explicit docs ownership correction now; the existing planned M8 formal request-carrier work should test it later. This review does not add a third test task or redesign M9.

## Blockers

- No design blocker remains in this bounded correction. Formal M7 carrier implementation still requires the matching direct tests and production changes; existing GREEN cannot substitute for them. No external permission, dependency, or runtime blocker was identified.

## Exact next action

- Parent delegates the first formal-wrapper publication RED, then Terra GREEN, followed by the separate two-group ordinal consumer RED/GREEN. The already planned M8 formal-carrier work consumes the frozen request material; do not expand this docs task.

## Resume checks

- Re-read this progress and inspect current M7/M8 files because other agents share this worktree.
- Preserve unrelated changes and edit only the two authorized Markdown files.
