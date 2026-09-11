# Progress: Business Flow bounded public-closure implementation

- Status: COMPLETE
- Agent role: Terra/xhigh bounded M3 public-closure GREEN implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Completed published bounded public-closure vertical: strict real discovery repository-entry coverage validation through Flow M1 and the required three-term M3 public-coverage conjunction.
- Approved inputs: Published Step 05 §§8.1 and 8.3, the completed bounded handoff diagnosis, real discovery/Flow writer and reader code, and the frozen Luna bounded public-closure RED.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Created this tracked preparation checkpoint before production edits.
- Recorded the task boundary: no production change, Maven command, fixture/test change, graph work, Provider/source action, schema/version/interface change, or owner-replay modification is authorized during preparation.
- Read both implementation plans, the published Step 05 M3 contract, and the completed bounded closure diagnosis. The design is already published: a bounded source is valid with `repositoryEntryCoverage.closed=false`; a missing or malformed value is fatal, never an implicit `true`.
- Confirmed the real discovery writer already publishes the full `application-discovery-capability-report-v2` shape. Its `application-profile.json` holds `inventoryScopeKind` and `repositoryCompletionEligible`; its `repositoryEntryCoverage` holds the sorted `entryIds`, entry/count fields, `noEntryDiscovered`, and required boolean `closed`. The writer defines `closed` exactly as `COMPLETE_CAPTURE && repositoryCompletionEligible`, rejects unknown scope/invalid eligibility, and rejects eligible bounded scope.
- Confirmed the prior Flow reader received `capability-report.json` with the correct payload policy but discarded it in `parseDiscovery`; `DiscoveryMaterial` retained only snapshot, profile ID, and entries. This was the minimal M1 seam to retain a validated discovery closure bit without changing M1 local coverage semantics.
- Confirmed M1 publisher intentionally writes `coverage.closed=true` for its local entry/Flow denominator, while the prior M3 wrote public `flow-coverage.json.closed=true` as a literal. The completed M3 seam fresh-reads discovery and M1 coverage, validates their existing fields, calculates existing public entry/Flow accounting, and emits the published three-term conjunction without changing file schemas, public identities, or module versions.
- Implemented the bounded reader seam in `PersistedFlowCompilationInputReader`: it now requires the real capability-report header, validates the profile scope/eligibility relation and capability profile identity, checks the sorted entry denominator against `repositoryEntryCoverage.entryIds`/`entryCount`/`noEntryDiscovered`, and requires the coverage boolean to equal the published scope/eligibility formula. M1 output wire and identity remain unchanged.
- Implemented the M3 seam in `FlowPublicationSpecifier`: it fresh-validates the same real discovery coverage, validates all persisted M1 local coverage fields against the material it publishes, requires public entry classification closure, and feeds the exact discovery-local-public conjunction to `flow-coverage.json.closed`.

## Current state

- This bounded public-closure slice is complete. The coordinated combined selector passed the bounded public test and all five provenance tests; its overall nonzero result is solely the two separately prepared graph-local RED failures.
- Full Step 05 remains unaccepted. The next local-graph coverage correction and deferred compiler-origin Gap normalization are separate verticals.
- The compiler-only raw `ENTRY` to public `FLOW` Gap-normalization concern is explicitly deferred to its own vertical and must not be bundled here.

## Changed files

- `progress/business-flow-bounded-publication-implementation.md` (owned implementation and verification record)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java` (strict real discovery capability validation; no M1 wire change)
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java` (strict M3 discovery/M1/public closure conjunction)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped `git status --short` | Baseline captured | Shared worktree contained extensive pre-existing changes owned by other tasks; they were preserved. |
| Frozen Luna public RED | RED; numeric exit 1 | `BoundedBusinessFlowPublicationTest`: 1 test, 1 failure, 0 errors, 0 skips; actual bounded chain reaches public coverage and emits literal `true` instead of required `false`. |
| Absolute two-file Spotless apply | GREEN; numeric exit 0 | Both owned production files were selected and formatted clean. |
| Absolute two-file Spotless check | GREEN; numeric exit 0 | Both selected production files required no changes. |
| Parent-coordinated combined direct selector | Overall expected RED; numeric exit 1 | 8 tests, 2 failures, 0 errors, 0 skips. The bounded `BoundedBusinessFlowPublicationTest` passed (1 test, 0 failures/errors/skips) and `BusinessFlowProvenanceTest` passed (5 tests, 0 failures/errors/skips). The only two failures are the separately frozen ControlFlow/DataFlow local-Gap REDs. |

## Decisions

- The completed Flow reader requires the discovery `repositoryEntryCoverage.closed` boolean and rejects missing, null, non-boolean, scope-inconsistent, eligibility-inconsistent, or entry-denominator-inconsistent values. It never defaults a missing value to `true`.
- M1 local coverage remains its existing local entry/Flow accounting result. M3 public coverage must be exactly `discoveryClosed && m1LocalClosed && publicAccountingClosed`; no new schema, identity, interface, or compatibility path is permitted.
- This vertical must preserve existing strict accounting and use existing upstream artifacts; it must not fabricate a repository scope Gap or relax closure validation.
- The implemented existing-field checks are: valid profile scope (`COMPLETE_CAPTURE` or `BOUNDED_PATH_SET`), required boolean eligibility with `true` allowed only for complete scope, required boolean discovery closure equal to the published scope/eligibility formula, and exact coverage `entryIds`/`entryCount`/`noEntryDiscovered` agreement with the reopened sorted HTTP entry denominator.
- M3 must retain the current valid local accounting checks and add no alternate wire: it should validate the persisted M1 `coverage.closed` boolean and existing local denominator against the material it publishes, then use that local result plus the discovery result and recomputed public accounting for public `closed`.

## Blockers

- None for this completed bounded public-closure slice. Full Step 05 remains unaccepted pending separately scoped work.

## Exact next action

- No further action in this completed bounded public-closure slice. Do not touch compiler-origin Gap normalization without its separate scope assignment.

## Resume checks

- Maven lease is released. Full Step 05 remains unaccepted, and deferred compiler-origin Gap normalization remains out of scope.
