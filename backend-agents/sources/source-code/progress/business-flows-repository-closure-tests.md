# Progress: bounded repository-flow closure RED

- Status: BLOCKED
- Agent role: Luna/xhigh bounded test-only owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add one public-seam RED proving a bounded `ApplicationDiscovery` upstream keeps final `flow-coverage.json.closed` false even when the bounded local entries compile to complete Flow/Capsule pairs.
- Approved inputs: the existing `BusinessFlowsPublicationSpecifierTest.java`, an independent bounded fixture factory in `ProgramGraphsPublicFixture.java` only if required, the published Step05 §8.6 contract, and real canonical stores/publishers.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created and intent-to-added this progress file before Java/fixture edits.
- Read the Step05 repository-closure contract and the existing ApplicationDiscovery capability/profile wire shape.
- Confirmed the test uses canonical synthetic source/discovery publications, then the real ProgramGraphs/Facts/Flow/Capsule/M3 pipeline; it does not claim a real repository capture or full first-five-step integration.
- Drafted an independent bounded-discovery fixture variant and one public-seam test, then removed that invalid draft after the real Fact reader rejected the required capability field; no net Java/fixture change from this slice remains.

## Current state

- A canonical bounded draft carrying `BOUNDED_PATH_SET`, `repositoryCompletionEligible=false`, and `repositoryEntryCoverage.closed=false` was rejected by the real Fact input reader before M1 with `PROOF_PACK_REFERENCE_BROKEN`.
- The intended M3 RED was not reached. No bypass or fabricated M1/M2 material is allowed; this slice stops at the precise upstream contract mismatch.

## Changed files

- `progress/business-flows-repository-closure-tests.md` (owned; intent-to-added)
- No net Java/fixture file is retained for this slice; both listed Java paths already contain unrelated/prior shared-worktree changes.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress creation + `git add -N -- progress/business-flows-repository-closure-tests.md` | PASS | Intent-to-added before Java/fixture edits. |
| Read-only contract/factory inspection | PASS | Existing capability report fields inspected; no imagined capture/discovery fields used except the bounded factory's explicit contract field. |
| Exact selector, session 64109 | COMPILE ERROR, numeric exit 1 | Missing local `payload` helper; fixed in owned test. |
| Exact selector, session 4600 | COMPILE ERROR, numeric exit 1 | `VerifiedCanonicalPayload` needed `.canonicalUtf8()`; fixed in owned test. |
| Exact selector, session 68609 | BLOCKED, numeric exit 1 | Tests 1, Failures 0, Errors 1, Skipped 0; `FactCandidateReferenceException: PROOF_PACK_REFERENCE_BROKEN` at `PersistedFactCandidateInputReader.parseDiscovery` because its exact capability field set rejects `repositoryEntryCoverage.closed`; M1/M2/M3 not reached. |
| Exact owned-Java Spotless apply/check | NOT RUN | Stopped because no legal RED was established. |
| Maven post-format exact selector | NOT RUN | Stopped at the upstream schema blocker. |

## Decisions

- Keep source predecessor canonical and graph-compatible while making only the independent discovery publication bounded; however, the current Fact reader's strict capability schema prevents the required `closed` field from entering the real pipeline.
- Do not modify the existing two-entry, guarded, shared-source, or seven-entry factories.
- Do not alter production `FlowPublicationSpecifier`; this slice records the precise RED for the next owner.
- Do not infer order, database effects, provider/model behavior, or Step06 semantics from the bounded entries.

## Blockers

- Production Fact input schema and the Step05 repository-coverage contract disagree: adding the required discovery `repositoryEntryCoverage.closed` field causes `PersistedFactCandidateInputReader` to reject the canonical publication. Fixing that cross-step contract is outside this test-only scope.

## Exact next action

- Stop and report the exact blocker; release Maven. Do not run Spotless or alter production/design to force the RED.

## Resume checks

- Maven is sole-leased and must not be run concurrently.
- Keep edits limited to the three paths above; no production/design/full-suite changes.
- On completion, record the concrete RED/GREEN outcome and release the Maven lease to root.
