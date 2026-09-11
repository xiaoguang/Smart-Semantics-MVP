# Progress: M5 local interpretation carrier design review

- Status: COMPLETE
- Agent role: independent design authority review
- Model: gpt-6-astra / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Review the completed M5 carrier design and, under the explicit follow-up assignment, apply only the reviewed M5 identity and same-R1 subset amendment to the authoritative Step 06 detailed design.
- Approved inputs: Scoped AGENTS, docs/DESIGN.md, Step 06 target design, M2/M5/M9 progress, current M4/M5 records, publishers, runners, and direct tests.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Located the exact shared worktree and recorded its pre-existing changes through read-only Git status.
- Read the completed M5 carrier design and the Step 06 M9 publication-readiness and receipt contracts.
- Confirmed that the requested task is a carrier design review; completion must not be reported as M5 implementation or Step 06 acceptance.
- Applied the explicitly assigned correction to authoritative Step 06 §6.7.2.1: exact four-ID formulas, complete self-ID-free task-disposition projection, same-R1 basis subset validation, and the separate M2 required-nullable producer prerequisite.

## Current state

- Verdict: CONDITIONALLY ACCEPTED as a bounded M5 carrier design. The exact amendment is now installed in authoritative Step 06 §6.7.2.1; its independent RED oracle and minimal GREEN remain separately assigned. This does not approve the current M5 implementation, the local model boundary, or M9/Step 06 readiness.
- The completed design contains substantive work: it identifies the real semantic task-set mismatch, declares all fifteen GenerationReceiptV3 fields, separates configured adapter/auth from observed runtime, and replaces IDs-only Candidate ownership with complete same-Flow proposal values without a duplicate top-level owner.
- No P0 was found. The M5 identity-contract gap is closed at the design level by §6.7.2.1. The two identified P1 readiness defects remain implementation prerequisites; they must not be hidden by a carrier-only GREEN.

## Changed files

- progress/m5-local-interpretation-carrier-design-review.md
- docs/analysis-steps/06-flow-interpretation.md (only new §6.7.2.1 in this work unit; existing shared-worktree edits preserved)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only Git status and scoped contract reads | PASS | Existing shared-worktree changes preserved; exact review targets found. |
| M4/M5 and completed M2 source/test inspection | PASS | Followed profile, task, runner, receipt, proposal, Candidate and publisher fields; inspected actual M2 nullable-field oracle. |
| `git diff --check` | PASS | No whitespace errors in the shared worktree after the complete review record was written. |
| Read-back of Step 06 §6.7.2.1 | PASS | All four exact identity rows, disposition field set, same-R1 subset rule and M2 prerequisite present on disk. |

## Decisions

- The explicit follow-up authorizes only `docs/analysis-steps/06-flow-interpretation.md` and this owned progress file. No production, test, schema, provider, Maven, Git mutation or M2 design change is authorized.
- No Maven, Provider, network, or customer-source execution is required.

### Accepted design decisions and bounds

- `InterpretationRunner` and the Provider-free M5 publisher must fresh-reopen the exact M4 publication and preserve `payload.flowTaskSetId`. The artifact root remains a publication locator. This corrects the actual assignment in `InterpretationExecutionSetModulePublisher.java:128`.
- R1/R2 runtime configuration remains outside `inputJson`; the shared `ModelRuntimeIdentityV1` has only upstream provider/model/reasoning/sandbox. Full expected/observed value equality and round-derived receipt kind are correct. Started transport/runtime failures remain fatal with no retry or manufactured successful receipt/publication.
- Candidate is the only canonical owner of complete `InterpretationProposal` values. The eight proposal fields in the completed design are sufficient to retain the existing selected key, same-Flow registry binding, basis and actual R2 decision for M9 projection. DROP and NEEDS_EVIDENCE values remain represented; carrier work must not upgrade or discard them.
- The frozen public runner/publisher/reopen seam, distinct R1/R2 sentinels, independent canonical oracle, and unchanged call counter are appropriate for the positive carrier behavior. A carrier GREEN proves only that behavior; it cannot certify existing R2 evidence validation or the separately acknowledged local DRY packet gap.

### P1 M5 amendment: make changed identities executable without invented disposition IDs

`progress/m5-local-interpretation-carrier-design.md:42` requires task-disposition IDs, but `ModelTaskDisposition.java:6` and `docs/DESIGN.md:1119` define a self-ID-free value. The task/proposal/Candidate instructions at design lines 49 and 61 specify content inclusion but omit exact prefix/domain/framing. Freeze this smallest amendment before using an independent golden:

For each row below, compute `prefix + lowercaseHex(SHA-256(frame(UTF8(domain)) || frame(canonicalJson(projection))))`. `frame(bytes)` is an unsigned/nonnegative 64-bit big-endian byte length followed by those bytes, matching the existing canonical v3 receipt framing. Canonical JSON uses the repository codec; no Java record/ImmutableBytes implementation metadata is serialized.

| Identity | Prefix | Domain | Exact projection |
| --- | --- | --- | --- |
| `FlowModelTask.taskSpecId` | `flow-model-task:` | `flow-interpretation-flow-model-task-id-v1` | Exactly `taskKind`, `round`, `flowSliceId`, `evidenceCapsuleId`, `isolatedSessionKey`, `allowedKeys`, `inputJson`, `inputJsonSha256`, `outputSchemaSha256`, `promptBundleSha256`, `configuredAdapterId`, `configuredAuthMode`, `expectedRuntimeRef`, `expectedRuntime`; `inputJson` is the canonical application JSON value, not a byte-array wrapper. |
| `InterpretationProposal.interpretationProposalId` | `interpretation-proposal:` | `flow-interpretation-interpretation-proposal-id-v1` | Exactly `registryProposalId`, `flowSliceId`, `provisionalKey`, `selectedKey`, `basisAtomIds`, `basisGapIds`, `r2Decision`. |
| `FlowInterpretationCandidate.candidateId` | `flow-interpretation-candidate:` | `flow-interpretation-flow-interpretation-candidate-id-v1` | Exactly `flowSliceId`, `evidenceCapsuleId`, `r1RoundId`, `r2RoundId`, `interpretationProposals`; proposals are complete values including their already-computed IDs. |
| `InterpretationExecutionSet.executionSetId` | `interpretation-execution-set:` | `flow-interpretation-execution-set-id-v1` | Exactly `flowTaskSetId`, `modelRoundIds`, `generationReceiptIds`, `candidateIds`, `modelTaskDispositions`, `flowInterpretationDispositionIds`. IDs are derived from the corresponding execution collections. `modelTaskDispositions` contains complete self-ID-free values, not only task IDs. |

- `allowedKeys`, proposal basis arrays, and every ID array in the table are unique and sorted by UTF-8 byte order. Duplicate IDs fail closed. Embedded proposals are sorted by `interpretationProposalId`; `modelTaskDispositions` are sorted by `taskSpecId`.
- Every task disposition includes exactly its current fields: `taskSpecId`, `flowSliceId`, `round`, `state`, `modelRoundId`, `generationReceiptId`, `upstreamTaskSpecId`, `gapIds`, `reasonCode`; required-nullable values participate as JSON null. Do not invent `modelTaskDispositionId` or change V1.
- The M4 task-set ID continues to use the newly recomputed task IDs. For `R=0`, the existing semantic task-set identity has no per-task configuration to bind; the blanket statement that any configuration change changes this empty semantic ID must be qualified. Complete M4 artifact bytes still bind `runtimePolicy` and controls.
- The v3 receipt formula remains exactly the target formula; all fifteen fields, including `taskShardId:null`, are present before excluding only `generationReceiptId`. The publisher independently verifies complete field sets, identities and exact M4/round joins before installation. Missing fields, old formulas or mixed old/new carriers fail closed; no alias, dual writer or repair during M9.
- The existing carrier RED must build its expected canonical projection independently. It must assert required field presence as well as nullable value, and must not derive the expected field set from the actual encoded receipt.

### P1 prerequisite defect: completed M2 omits required-nullable shard scope

- Target evidence: Step 06 `docs/analysis-steps/06-flow-interpretation.md:1290` defines `?` as required nullable, and lines 1675/1806 require scope fields and non-excluded nulls in the ID projection.
- Implementation evidence: `RegistryProposalGenerationReceipt.java:162` builds both receipt wire and ID preimage but has no `taskShardId` member. `RegistryProposalRunnerTest.java:244` asserts Java null from `receipt.get("taskShardId")`, which requires a missing member instead of the required JSON null. Its ID oracle removes only the ID from the actual incomplete object, so the test cannot expose that omission.
- Exact repair: the R0 encoder/preimage must add `putNull("taskShardId")`; the test must assert `receipt.has("taskShardId")` and `receipt.get("taskShardId").isNull()`, assert the exact fifteen-field set, and compute the expected v3 ID from an independently built complete expected receipt. Recompute affected receipt/downstream IDs; never insert the field at M9 while retaining the old ID. This is an M2 repair, not authority to modify M2 inside the M5 carrier slice.

### P1 existing local validation gap: R2 basis expansion is not already checked

- M5 design line 60 assumes an already validated R1 selection/R2 review and only names same-Flow finite membership. Actual `InterpretationRunner.java:318` ignores both `atoms` and `gaps`; `parseR2` at lines 278–294 compares selected keys but never checks each R2 basis set against that key's actual R1 basis. `docs/DESIGN.md:712` expressly permits only basis subsets and forbids new basis in R2.
- Concrete counterexample: the same registry key permits atoms A and B; R1 selects only A; R2 keeps the key but returns A and B. Both values are same-Flow/task-admitted, so the carrier's stated membership checks would accept the forbidden R2 expansion. Current validation also accepts a syntactically valid foreign atom ID.
- Smallest bounded prerequisite: validate R1 basis membership against the exact persisted task/registry input, retain the per-key R1 selection in program memory, require every R2 atom/Gap set to be a subset of that same selection, and reject a violation as `MODEL_REFERENCE_INVALID` / `MODEL_REVIEW_EXPANDED` before returning an execution or publishing M5. No new model grammar, evidence mechanism, Provider call or public file is needed. Freeze a direct scripted RED for the same-key A-to-A+B case and foreign basis before its separate minimal GREEN; do not pretend the positive carrier test covers it.

## Blockers

- No blocker to completing this read-only review. The identity amendment and two implementation findings above remain prerequisites for claiming target-compliant publication readiness.

## Exact next action

- Hand the completed authoritative design correction to the coordinator. No further action remains in this docs-only assignment; independent RED/implementation and any design publication are separate work.

## Resume checks

- Re-read this progress file and inspect only the assigned source scope.
- Preserve all unrelated shared-worktree changes and completed progress records.
