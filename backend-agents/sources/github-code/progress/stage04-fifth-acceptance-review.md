# Progress: Stage04 Fifth Acceptance Review

- Status: COMPLETE
- Agent role: independent acceptance reviewer
- Model: gpt-5.6-sol
- Started: 2026-08-30 23:21:39 -0230
- Last updated: 2026-08-30 23:28:16 -0230
- Scope: current-state contract review of Stage04, independently rechecking the four prior P1 areas plus Candidate identity, Round 2, and recent-change regression
- Approved inputs: repository-local design, production code, tests, prior review/audit progress, and at most one directly relevant narrow Maven selector; no network, model provider, customer Maven, source, generator, deployment, commit, or push
- Current branch/worktree: `codex/github-code-design-walkthrough` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Read repository, prototype, backend-agent, and source-scoped `AGENTS.md` instructions.
- Captured the pre-review dirty worktree; all pre-existing changes are preserved and out of this reviewer's edit scope.
- Read the prior fourth acceptance review, fifth-audit implementation/test records, the complete Stage04 detailed design, and the relevant M8 sections/current-state matrix in `DESIGN.md`.
- Rechecked nested archived model/task/receipt tuple closure: current validation binds duplicated flow, capsule, and round fields in both model and receipt to the nested typed task and recomputes model/receipt IDs; no residual P0/P1 found in this group.
- Rechecked persisted typed Trace compilation/replay. Flow technical fallback now persists subtype and exact flow/policy/template/task/anchor/resolution-order refs, and deterministic replay compares exact `trace.jsonl` bytes. One P1 remains for empty-section Trace records (below).
- Rechecked the newly introduced bounded read helpers and each previously listed caller. Source reopen, registry enumeration, recovery Candidate lookup, series enumeration, event replay, and validation-time Candidate enumeration are capped before retention. One P1 remains in the Candidate store idempotent/destination verification path (below).
- Rechecked Candidate identity and addressed reads: content identity binds document/plan/stage/source/registry/model-content/typed-Trace roots; lineage identity binds series/round/parent/findings/addendum; addressed reads require the requested directory ID to equal the persisted sidecar ID. No Candidate-identity regression found.
- Rechecked Round 2: finding resolution precedes slot reservation, overlays contain only parent refs, unaffected Flow rounds/receipts remain byte-identical and parent-slot-owned, and a distinct second Round-2 request cannot be represented. No new Round-2 P0/P1 found.
- Rechecked Round-1 and Round-2 post-start failure handling and fresh-process re-entry. Both outer catch paths use the bridge's current slot, append `FAILED_AFTER_STARTED` from `STARTED_CONSUMED`, and terminal re-entry returns `PROVIDER_FAILURE_AFTER_START` before Provider reachability. No residual lifecycle P0/P1 found.
- Audited durable current-state documentation after the fifth-audit production/test changes. A separate P1 remains because both owning design documents still publish obsolete Stage04 counts/status/open-findings as current facts (below).

## Current state

- Final disposition: **REJECT Stage04 acceptance.** P0: 0. P1: 3. Acceptance requires P0=0 and P1=0.
- P1 — persisted `EMPTY_SECTION` typed Trace is not self-describing per the accepted contract. The contract requires section/profile/effective-template refs (`docs/stages/04-runtime-archive-trace-recovery.md:596-604`), but `CandidateAssembler.fallbackTraceRefs` writes only `fallbackSubtype`, `sectionKey`, and `templateKey` (`CandidateAssembler.java:496-502`). Replay reproduces the same incomplete record (`CandidateValidationTrace.java:501-513`), while runtime resolution infers the built-in template from the reader item/name and current code (`CandidateValidationTrace.java:1019-1030`, `1254-1273`). The fifth-audit test asserts exact refs only for `FLOW_TECHNICAL_DISPLAY` (`Stage04FifthAuditArchiveTraceTest.java:98-114`), so it cannot close the empty-section half of the required subtype contract.
- P1 — installed-Candidate destination verification still performs unbounded pre-collection enumeration. `FilesystemCandidateStore.verifyArchive` calls `Files.list(directory)` and `paths.toList()` before comparing the expected 20-entry archive (`FilesystemCandidateStore.java:334-344`). This path is reached for an existing addressed destination before idempotent return/collision (`FilesystemCandidateStore.java:91-99`) and again after atomic install (`:101-108`). A hostile/high-cardinality existing Candidate directory is therefore materialized without the shared cap, and its public failure is collapsed to `CANDIDATE_IDENTITY_COLLISION` rather than the stable resource-limit code. This violates the Candidate-as-untrusted and pre-allocation budget contract (`docs/stages/04-runtime-archive-trace-recovery.md:651-668`). Existing bounds tests cover `CandidateArchive.open`, not this store path (`Stage04ReopenAndDirectoryBoundsTest.java:91-118`).
- P1 — the durable current-state contract was not updated with the fifth-audit implementation. The Stage04 document still says direct tests are `67/67`, acceptance is in progress, and seven earlier P1s remain (`docs/stages/04-runtime-archive-trace-recovery.md:3-17`, `718-799`); `DESIGN.md` repeats the same obsolete current-state claim and maturity entries (`DESIGN.md:17`, `1404-1421`). This conflicts with the source-scoped rule that the stage document owns current implemented results/tests/gaps/exit conditions and with the repository-wide requirement that every code/test change update durable documentation in the same work unit. The supplied current evidence is Stage04 `109/109`, and the fifth-audit code/test records claim the old P1s closed; leaving the authoritative contract contradictory prevents a current-state acceptance even if code defects are repaired.

## Changed files

- `progress/stage04-fifth-acceptance-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing modified/untracked Stage01-04 and documentation work recorded; reviewer file did not yet exist. |
| Static model/task/receipt closure trace | PASS | `CandidateValidationTrace.java:616-692` closes model/receipt duplicates to nested task and recomputes both identities. |
| Static typed-Trace contract trace | FAIL (P1) | Flow fallback is self-describing, but empty-section records omit profile/effective-template refs required by Stage04 §12. |
| Static pre-allocation directory trace | FAIL (P1) | Listed read paths use the shared bounded helper except `FilesystemCandidateStore.verifyArchive`, which still materializes an unbounded directory stream. |
| Static Candidate identity/Round-2 regression trace | PASS | Addressed identity, content/lineage roots, parent/finding/overlay closure, unaffected-flow reuse, and unique Round-2 slot remain closed. |
| Static Round-1/Round-2 terminal re-entry trace | PASS | Active bridge slot is terminalized after a durable start; fresh terminal re-entry cannot reach Provider. |
| Durable documentation current-state audit | FAIL (P1) | Stage04 and overall design still report the obsolete 67-test/seven-P1 pre-fifth-audit state. |
| Root-supplied completed test evidence | PASS (evidence only) | Stage04 `109/109`; Stage03 `69/69`; Stage01/02 `79/79`. |
| Maven in this review | NOT RUN | Static evidence proves three blocking P1s; the supplied fresh suites already cover the implemented test surface, and the one permitted selector would not cover the two untested production gaps plus documentation drift. |
| Final `git status --short` and finding-anchor reread | PASS | All three findings remained present; shared dirty worktree preserved. A separate `stage04-sixth-audit-tests.md` appeared during finalization and is not owned by this reviewer. |
| Reviewer mutation audit | PASS | This reviewer modified only `progress/stage04-fifth-acceptance-review.md`; no production, test, design, source, generation, deployment, commit, or push action was performed. |

## Decisions

- This is a current-state contract review, not a fixed-point diff review.
- Findings will be reported as P0/P1/P2 with exact file and line evidence; acceptance requires P0=0 and P1=0.
- Existing reported suites (Stage04 109/109, Stage03 69/69, Stage01/02 79/79) are treated as supplied evidence, not as commands run by this reviewer.
- The documentation P1 may be closed in the same Sol/ultra durable-documentation work unit after the two production P1s and their RED/GREEN evidence are final; it remains blocking in this current-state snapshot.

## Blockers

- None. The rejection is an evidence-backed acceptance result, not a review-execution blocker.

## Exact next action

- None. Review complete; correction and a fresh acceptance pass remain with the parent work unit.

## Resume checks

- Re-read this file, run `git status --short`, and verify no file other than this progress file was modified by this reviewer.
