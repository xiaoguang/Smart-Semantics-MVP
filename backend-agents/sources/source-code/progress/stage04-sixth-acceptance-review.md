# Progress: Stage04 sixth final independent acceptance review

- Status: COMPLETE
- Agent role: sixth-round independent acceptance reviewer
- Model: GPT-5.6
- Started: 2026-08-31 00:23:37 NDT
- Last updated: 2026-08-31 00:34:06 NDT
- Scope: current-state contract review of the three fifth-review P1 remediations, plus targeted regression sampling of nested tuple closure, persisted typed trace, lifecycle/re-entry, Candidate identity, and Round2; report P0/P1/P2 and accept only when P0=0 and P1=0
- Approved inputs: scoped AGENTS files, fifth acceptance review, sixth audit core/tests/docs progress, current README/DESIGN/Stage04 detailed design, relevant Stage04 production/tests, and root-supplied clean verification evidence
- Current branch/worktree: `codex/github-code-design-walkthrough` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/github-code`

## Completed

- Re-read all four applicable AGENTS files in full and captured the shared worktree baseline before edits.
- Created this reviewer-owned progress file; no production, test, design, README, or prior progress file is in reviewer write scope.
- Read the fifth acceptance review and the complete sixth-round core, test, and documentation closeout records. They claim narrowly scoped RED/GREEN closure for both behavior P1s and synchronized 111/69/79 final-review-pending documentation; those claims remain evidence to verify independently against current files.
- Read `README.md`, `DESIGN.md`, and the Stage04 owning detailed design in full. Their current-state status/evidence, the three fifth-review closures, and the retained bounded-v0 exclusions agree; obsolete 67-test/seven-open-P1/acceptance-in-progress text is absent from current-state claims.
- Independently closed the fifth review's `EMPTY_SECTION` P1: the assembler persists exact section/profile/template refs, Stage03 replay compares regenerated Trace bytes, and the resolver consumes the persisted record and closes it to the archived ReaderItem/effective profile without invoking the legacy naming check for the empty-section branch. The sixth test covers positive refs plus coherent deletion/substitution rewrites of all three fields.
- Independently closed the fifth review's addressed-destination P1: `FilesystemCandidateStore.verifyArchive` calls the shared bounded directory reader before retaining/comparing names or bytes, and `installedExactlyMatches` rethrows `CANDIDATE_SIZE_LIMIT_EXCEEDED` rather than collapsing it into identity collision. The sixth test drives the public install seam with a real addressed destination and a limit-plus-one directory.
- Sampled prior closures against current production/tests: nested model/task/receipt tuple and both identities; addressed Candidate ID/directory/sidecar/content/lineage identity; finite Round-2 overlay, unaffected-Flow round/receipt reuse and unique slot; shared Round-1/Round-2 active-slot `FAILED_AFTER_STARTED` terminalization and fresh zero-Provider re-entry. No regression found.
- Completed the final stale-state scan, finding-anchor check, diff check, and reviewer mutation audit. This reviewer changed only this progress file and did not run Maven because the root supplied fresh exclusive clean aggregate evidence and no unresolved behavior question required the optional narrow selector.

## Current state

- Final disposition: **ACCEPT Stage04 bounded v0 — P0=0, P1=0, P2=1**. The acceptance gate is met.
- The three fifth-review P1s are closed in current state: persisted `EMPTY_SECTION` exact refs and fail-closed replay/resolution; addressed destination pre-collection bound with stable resource code; and synchronized durable current-state status/closure evidence with the real bounded-v0 exclusions retained.
- One non-blocking documentation precision issue remains: Stage04 detailed design says every Candidate-validation directory path uses the shared `readBoundedDirectory` helper, while `CandidateArchive.open` implements an equivalent bounded loop inline. The implementation still rejects the 257th entry before retaining it and exposes the correct resource-limit validation outcome, so this is P2 only and does not reopen the addressed-destination P1.
- Root-supplied verification baseline: exclusive clean Stage04 111/111, Stage03 69/69, Stage01/02 79/79; documentation stale scan zero, 63 links pass, and documentation diff-check pass.
- Shared worktree is intentionally dirty with multi-agent implementation/documentation work; reviewer will preserve all pre-existing changes and maintain only this file.

## Findings

- P0: none.
- P1: none.
- P2 — documentation implementation-detail precision: `docs/stages/04-runtime-archive-trace-recovery.md:304` says Candidate validation uses the shared `readBoundedDirectory` seam, but `CandidateValidationTrace.java:1955-2007` (`CandidateArchive.open`) uses its own pre-retention `Files.list` loop; the actual shared helper is at `CandidateValidationTrace.java:2250-2283`. Both paths enforce the 256-entry bound before retaining entry 257, so the runtime contract and stable resource failure remain closed; only the seam-unification statement is too broad.

## Changed files

- `progress/stage04-sixth-acceptance-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Captured dirty shared-worktree baseline before reviewer edit. |
| full reads of root/prototype/backend-agents/source `AGENTS.md` | PASS | Confirmed scoped ownership, progress cadence, current-state documentation, and narrow-verification constraints. |
| full reads of fifth review and sixth core/tests/docs progress | PASS | Mapped each prior P1 to its claimed implementation seam and test/document evidence without treating progress claims as acceptance proof. |
| full reads of `README.md`, `DESIGN.md`, and Stage04 owning design | PASS | Current status is final-review-pending with 111/69/79 evidence; historical findings are distinguished from open state and true live/remote/distributed/deployment limits remain explicit. |
| static `EMPTY_SECTION` persisted/replay/resolver trace | PASS | `CandidateAssembler.java:496-503`; `CandidateValidationTrace.java:501-513, 818-845, 976-1047`; `Stage04SixthAuditTest.java:44-89`. |
| static addressed-destination bound/error trace | PASS | `FilesystemCandidateStore.java:91-99, 322-357`; shared helper `CandidateValidationTrace.java:2250-2283`; `Stage04SixthAuditTest.java:28-42`. |
| static nested tuple/identity/Round2/lifecycle regression sample | PASS | Triple tuple equality and both IDs remain recomputed; addressed and lineage identities remain bound; unaffected Flow reuse/parent receipt ownership/unique slot remain closed; both outer catches use active bridge state and persisted terminal replay. |
| final exact-anchor scan | PASS | Exact persisted refs remain at `CandidateAssembler.java:500-502`; resolver uses `emptySectionLineage` at `CandidateValidationTrace.java:979,1026`; addressed verification uses the bounded seam at `FilesystemCandidateStore.java:95,322,338`. |
| current-doc stale scan | PASS | No `67/67`, `P1 未关闭`, `ACCEPTANCE REVIEW IN PROGRESS`, or seven-open-P1 wording in README/DESIGN/Stage04 owning design. |
| `git diff --check` | PASS | No whitespace errors in current shared-worktree diff before final progress write. |
| root-supplied aggregate tests | PASS (external evidence) | Exclusive clean Stage04 111/111, Stage03 69/69, Stage01/02 79/79; this reviewer did not rerun Maven. |
| reviewer mutation audit | PASS | Reviewer-owned mutation is limited to `progress/stage04-sixth-acceptance-review.md`; all listed production/test/docs changes pre-existed or belong to the coordinated sixth-round work. |

## Decisions

- This is a current-state contract acceptance review, not a fixed-point diff review.
- Do not rerun aggregate suites already supplied by root; at most one necessary narrow selector is permitted.
- Acceptance gate is exactly P0=0 and P1=0 for bounded v0.
- The single P2 is recorded for exactness but is not acceptance-blocking because the executable admission bound and public failure semantics are intact.

## Blockers

- None.

## Exact next action

- None. Sixth-round independent acceptance review is complete; root may close Stage04 bounded v0 with the non-blocking P2 documented.

## Resume checks

- Re-read this file before resuming.
- Re-run `git status --short` and distinguish concurrent shared-worktree changes from this reviewer's sole owned file.
- Continue from the exact next action; never modify prior reviewer progress or implementation/documentation files.
