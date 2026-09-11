# Progress: discovery-to-fact handoff implementation

- Status: BLOCKED
- Agent role: Terra/xhigh bounded production implementation owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One existing-contract GREEN that makes `PersistedFactCandidateInputReader` strictly consume the real ApplicationDiscovery M4 publication shape and provenance.
- Approved inputs: frozen real `DiscoveryToFactHandoffTest` RED, completed closeout contract ruling, merged PR15 Step 02/04 contracts, real ApplicationDiscovery and ProgramGraphs producers, and current Fact input reader.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the frozen handoff-test progress, current strict reader, the closeout contract ruling, and the published Step 02/04 handoff contract.
- Confirmed the real producer completes; the Fact reader rejects it because it requires nonexistent profile-body `sourceInventoryRef`, `verifiedSnapshotRef`, and `controls` fields and accepts only a reduced capability-report shape.
- Created this owner-only progress file before the production edit.
- Reproduced the frozen real producer-to-consumer RED at the reader’s profile field gate.

## Current state

- The bounded hypothesis is to validate source lineage and controls from reopened Step receipts and real capability source-publication references, and validate the full exact M4 profile/capability/site/shard/count shape without a compatibility branch or default.
- The strict real-shape reader path is in place, including required `closed` equality with `COMPLETE_CAPTURE && repositoryCompletionEligible` so bounded captures propagate `false` rather than being rejected.
- The shared fixture alignment reached a compile-ready checkpoint. The reader now accepts the real discovery publication far enough to reach the existing ProgramGraphs coverage validation, which rejects the graph publication at `validateCoverage`; that downstream graph-coverage contract is outside this discovery-reader-only slice.

## Changed files

- `progress/discovery-to-fact-handoff-implementation.md` (owned; intent-to-add staged before production edit)
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java` (authorized production file; strict real ApplicationDiscovery consumer validation only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress creation + `git add -N -- progress/discovery-to-fact-handoff-implementation.md` | PASS; numeric exit 0 | Intent-to-add completed before the production edit. |
| Exact `DiscoveryToFactHandoffTest#handsRealDiscoveryAndGraphPublicationsToTheFactInputReader` selector | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; real M1–M4 and ProgramGraphs publications succeeded, then `parseDiscovery` rejected the obsolete profile field set at line 191. |
| First post-edit exact selector | LOCAL COMPILE FAILURE; numeric exit 1 | `PersistedFactCandidateInputReader` referenced missing private `requiredBoolean` and `nonnegativeInt` helpers; no test executed. |
| Second post-edit exact selector | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; real M4 JSONL omitted the reader's fabricated per-line `artifactType` field. |
| Third post-edit exact selector | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; real M4 JSONL uses documented singular line schemas, distinct from plural payload schemas. |
| Fourth post-edit exact selector | BLOCKED BEFORE TEST EXECUTION; numeric exit 1 | Test compilation fails in out-of-scope `ProgramGraphsPublicFixture`: missing `discoveryProfile(SourceMaterial)` (line 234), `discoveryEntries(SourceMaterial)` (line 248), and `discoveryMappers(SourceMaterial)` (line 256); 0 tests ran. |
| Exact selector after fixture compile checkpoint | RED OUTSIDE THIS SLICE; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; discovery parsing completed, then existing graph coverage validation threw `PROOF_PACK_REFERENCE_BROKEN` from `validateCoverage` line 1159 via `parseProgramGraph` line 634. |
| Absolute production-file Spotless apply/check | PENDING | Run only after the one-file GREEN. |
| Post-format exact selector | PENDING | Run after formatting. |

## Decisions

- Consume only the real M4 producer shape; do not accept legacy fabricated fields, unknown fields, compatibility aliases, or inferred closure.
- Preserve source, control, coverage, and identity fail-closed behavior and change no producer, schema/version, test, fixture, or graph semantics.
- Require `repositoryEntryCoverage.closed` to be a present boolean equal to the reopened source's `COMPLETE_CAPTURE && repositoryCompletionEligible` state; never default it or impose a global-true gate.

## Blockers

- The exact real handoff test now reaches a downstream ProgramGraphs coverage rejection (`validateCoverage`, `PROOF_PACK_REFERENCE_BROKEN`). Its graph coverage/mapper compatibility resolution is outside the one-reader-file authority; do not broaden this slice.

## Exact next action

- Release the Maven lease for the graph fixture/coverage owner. Do not run Spotless or post-format verification unless root assigns a subsequent scoped GREEN after the downstream graph issue is resolved.

## Resume checks

- The shared worktree is intentionally dirty; only this progress file and `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java` are authorized for this task.
- Hold the sole Maven-heavy-command lease for all Maven work in this slice; no network, real Provider, customer Maven/capture, commit, push, or subagents.
- This bounded reader slice is not GREEN or complete. Full Step 05 remains unaccepted and has independent outstanding gates.
