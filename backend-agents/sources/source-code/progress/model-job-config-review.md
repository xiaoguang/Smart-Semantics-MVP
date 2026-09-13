# Progress: model-job-config-review

- Status: COMPLETE
- Agent role: Step 1 Standards and Spec reviewer
- Model: gpt-6 / ultra
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Read-only review of unified model-job configuration, state identity, preflight and runnable documentation; no production or test edits.
- Approved inputs: `AGENTS.md`, `docs/modules/model-job-execution.md`, Step 1 test/implementation progress, current diff and direct consumers.
- Current branch/worktree: `codex/model-job-parallel-execution` in the formal `source-code` checkout.

## Completed

- Confirmed the current diff and the Step 1 reported 27-test GREEN evidence.
- Read the full scoped instructions, model-job owner design, config tests/implementation progress, launcher diff, current template and runner guide.
- Compared normalized configuration, hash coverage, private state, provider selection and YAML parser behavior against the approved contract.
- Reproduced Jackson's silent acceptance of a second YAML document and malformed trailing document in read-only JShell with the project's runtime classpath.
- Re-reviewed the Step 1 fixes: normalized execution JSON/SHA now includes executable/journal/output, YAML now requires EOF, private execution configuration is saved after state/routing validation and before Provider construction, and mixed/API routes are explicitly rejected by the interim bridge.
- Confirmed materials-only never calls authentication environment resolution or constructs a model Provider.
- Final hardening review confirms `Files.createLink(destination, temporary)` installs completed bytes without replacement; same-byte collisions return successfully, differing bytes fail closed, and unsupported filesystems do not fall back to overwriting moves.
- Confirmed owner-design lines 93/95 and overall-design line 179 now match the implemented v2 configuration state.

## Current state

- Production and documentation findings are closed. No remaining P0/P1 in this bounded configuration review. One P2 remains in the concurrency regression's timing-dependent setup.

## Findings

### Standards P2: the new concurrency regression can fail from scheduler timing

- `RepositoryRunModelJobsConfigurationTest.java:488-507,539-548`: the test polls for a short-lived temporary file and assumes it can create the competitor before the main thread finishes a 16 MiB write and links it. `watcherStarted` only announces thread entry, not that the observation or competing install completed. A fast write or delayed watcher can therefore fail a correct no-replace implementation.
- Minimum remedy: launch two byte-value groups against one absent destination from a shared barrier. Assert that final bytes equal exactly one submitted value, all matching-value writers succeed, and every differing-value writer reports conflict. This checks the required observable invariant without transient-file timing or production test hooks.

## Changed files

- `progress/model-job-config-review.md` only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Other agents' edits preserved. |
| Read-only JShell with current Jackson YAML runtime | CONFIRMED | A second document and malformed trailing document are silently ignored by the configured `readTree` call. |
| Maven | NOT RUN | The coordinator owns the build slot; no new suite run for review. |

## Decisions

- Review current uncommitted Step 1 changes against HEAD; parallel activity test work is outside this review.
- Do not run Maven; the coordinator owns the build slot.

## Blockers

- No production blocker. Stabilize the timing-dependent direct regression before claiming reliable CI coverage.

## Exact next action

- Coordinator replaces the regression's transient-file polling with deterministic outcome assertions. No production/test changes were made by this reviewer.

## Resume checks

1. Recheck current branch and status.
2. Do not modify production, test or another agent's progress files.
