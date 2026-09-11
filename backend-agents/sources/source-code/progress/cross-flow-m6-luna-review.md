# Progress: cross-flow M6 Luna review

- Status: COMPLETE
- Agent role: Read-only Step06 M6 review
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Review M6 production seam against the frozen Step06 contract and public CrossFlowCandidateCompilerTest; modify no production, test, design, or build files.
- Approved inputs: AGENTS.md; docs/analysis-steps/06-flow-interpretation.md §§5.1–5.3 and §7.1; current M6 process package; current public M6 test.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Read the scoped implementation rules, Step06 M6 wire/seam contract, M6 test, M6 production records/compiler, and existing M6 Terra progress.

## Current state

- Review complete. The four-test selector is a useful exact-call/zero-flow smoke slice, but it is not sufficient for M6 acceptance.

## Findings

### P0 blockers

1. **The Flow/Capsule signal equality check is tautological and the evidence closure is not revalidated.** In `CrossFlowCandidateCompiler.java:248-303`, `readFlows` parses capsule signals but `capsuleSignalJson` at lines 306-309 returns the Flow JSON argument unchanged. Therefore the comparison at lines 281-285 compares the Flow signal list with itself; a capsule can carry different signals and still be accepted. The compiler also reads neither the capsule Fact/atom/Proof/Evidence records nor the Step 04 publication content. `facts` is only used in lineage checks at lines 80-84 and 173-187. A fabricated or stale `EXPLICIT_CALL` signal can consequently become a `PROVEN_HANDOFF` at lines 372-469. This violates the model/evidence trust boundary and must fail closed before M7.

2. **The successful M6 result cannot carry the evidence required by the next module.** `ProcessPersistedFlowViewV1` contains only IDs (`src/main/java/org/sourceanalysis/app/analysis/interpretation/process/ProcessPersistedFlowViewV1.java:8-18`), and `ProcessPersistedMaterialV1` has only three lists and no `ProcessMaterialLimitsV1` (`ProcessPersistedMaterialV1.java:8-16`). `group` installs those ID-only views at `CrossFlowCandidateCompiler.java:537-587`. The frozen contract requires full Fact/Gap/Outcome/Signal/span/obligation values, path-bearing locators/excerpts, and limits so M7 can build a path-free packet. The returned `closed=true` result therefore cannot support the required P1/P2 evidence input and is not a valid completed M6 publication.

### P1 blockers

1. **The registry wire version is incompatible with the authoritative Step06 contract.** The compiler requires `flow-interpretation-repository-interpretation-registry-v2` at `CrossFlowCandidateCompiler.java:57-61`, while the frozen public registry is v3 (`docs/analysis-steps/06-flow-interpretation.md:815`). Correct public input is rejected before candidate compilation.

2. **Registry lineage and field identity are incomplete.** `readRegistry` accepts `businessFlows` but never uses it (`CrossFlowCandidateCompiler.java:311-338`), so it does not verify that each registry item belongs to a known Flow/Capsule or that its basis is inside that Capsule. `RegistryItem` stores the payload `provisionalKey` in a field named `registryItemId`, then `provisionalKey()` returns that same value (`CrossFlowCandidateCompiler.java:873-884`); the actual registry item ID is lost. The generated `ProcessRegistryItemViewV1` consequently cannot preserve the required registry ID/key distinction. `ProcessRegistryItemViewV1` also omits the contract's `evidenceCapsuleId` and normalized purpose fields.

3. **M6 does not reopen the analysis request/profile/budget.** `analysisRunRequestRef` is copied into the result and compilation identity (`CrossFlowCandidateCompiler.java:104-115, 590-609`) but never reopened or checked. The required cue profile and resource limits are therefore neither validated nor used. This also makes the required finite Registry cue lane impossible.

4. **Semantic-cue and shared-anchor lanes are absent.** `candidateRelations` only considers `EXPLICIT_CALL` + `INVOKES` + `CALL_TARGET` signals (`CrossFlowCandidateCompiler.java:372-400`). `group` initializes `cues` to an empty list and never reconstructs relation cue records (`lines 511-533`). Thus the documented `SHARED_ANCHOR` and `SEMANTIC_CUE/PENDING_ONLY` behavior is not implemented; only the narrow direct-call slice exists.

5. **Counter collection is absent.** Every relation is constructed with empty `counterBases`, `counterProcessJoinSignalIds`, and `blockingCounterProcessJoinSignalIds` (`CrossFlowCandidateCompiler.java:410-469`), while the top-level result always reports an empty `counterScopeIssues` list (`lines 93-115`). Blocking signals cannot be scoped, preserved, or converted to the required typed M7 Gap.

6. **Exact entry-target matching is not a unique target proof.** `entryTargets` finds one root edge per Flow but does not require the target node to occur in the entry traversal (`CrossFlowCandidateCompiler.java:341-369`), nor does it reject multiple Flow entries with the same exact target. The relation loop then links one caller signal to every matching callee Flow (`lines 383-397`), which can produce multiple `PROVEN_HANDOFF` candidates for an ambiguous target instead of a deterministic invalid/Gap outcome.

7. **Group and relation closure/identity checks are incomplete.** The group coverage guard at `CrossFlowCandidateCompiler.java:503-506` only checks a condition when `relations.isEmpty()`; it does not verify every relation is owned exactly once, endpoints are valid, or relation/group arrays are disjoint and complete. Relation identity at lines 441-448 omits positive-pair details, counters, fact/proof/evidence/gap IDs, and locators; group identity at lines 557-562 omits registry/cue/material/eligibility/limits. Equivalent IDs can therefore survive meaningful input changes, and malformed duplicate/order cases are not fail-closed.

8. **Publication-shape and malformed-record validation is too weak.** The records under `process/` mostly check non-null/nonblank values but do not enforce closed enum sets, sorted/unique arrays, left/right flow closure, required nonempty counter sides, exact basis rules, or group-to-relation membership. The compiler also maps malformed `ArtifactId`/payload parsing exceptions through the broad catch at lines 117-121 to `FLOW_INTERPRETATION_INPUT_INVALID`, rather than consistently distinguishing publication lineage from `PROCESS_MODEL_REFERENCE_INVALID`.

### P2 follow-ups

- Add explicit mutation tests for duplicate exact entry targets, stale Flow/Capsule signal content, registry-basis mismatch, counter scope, semantic cue profile normalization, group identity changes, and noncanonical arrays.
- Replace the unused `businessFlows` parameter in `readRegistry` only as part of the lineage/closure implementation; do not remove the required closure check.
- Add a direct test that M6 performs zero Provider calls and that all returned IDs are byte-stable under input iteration reordering; the current zero-flow test checks only the count and closed flag.

## Acceptance conclusion

The current four-test GREEN is insufficient. It proves only: one fixture's exact call produces a directed `PROVEN_HANDOFF`, one generic fixture produces no relation, zero Flow returns an empty closed result, and the invalid-target mutation is deferred rather than tested. It does not prove M6's required evidence trust, registry/cue handling, counter handling, process-material completeness, group closure, identity stability, or authoritative registry compatibility. M6 should remain `PARTIAL`/not accepted until at least the two P0 blockers and the P1 contract gaps are addressed.

## Changed files

- progress/cross-flow-m6-luna-review.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared-worktree WIP preserved; no unrelated changes touched. |
| Read-only source/spec comparison | PASS | Reviewed M6 compiler/records against Step06 §§5.1–5.3 and §7.1; no production/test/docs changes. |

## Decisions

- This is a read-only review; no production/test/design/POM edits are authorized in this task.

## Blockers

- M6 is not accepted: two P0 trust/material blockers and eight P1 contract blockers remain.

## Exact next action

- Parent should preserve this review, add RED tests for the P0/P1 findings, then implement only after the corresponding public seams are frozen.

## Resume checks

- Re-read this file, inspect only the M6 process package/test and Step06 contract, and preserve all other shared-worktree changes. Status is COMPLETE for this review, not acceptance of M6.
