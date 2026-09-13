# Progress: navigation reuse and readable report implementation

- Status: IN_PROGRESS
- Agent role: primary implementation coordinator
- Model: GPT-5
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: implement the approved JDT query reuse, reference-based Step05/Capsule persistence, local CI separation, and report source externalization
- Approved inputs: docs/plans/navigation-reuse-and-readable-report-design.md and user-approved implementation plan
- Current branch/worktree: codex/navigation-reuse-implementation in the formal repository checkout

## Completed

- Preserved the older Java 25 experiment in local commit `6374dc7`; it is not part of this branch.
- Synchronized the formal checkout to fetched `origin/main`.
- Merged the docs-only design through PR 22 as main commit `4bc15d2`.
- Created this implementation branch from exact main commit `4bc15d2`.
- Task 1 RED established the independent `skipUTs`/`skipITs` contract, exact
  Failsafe real-JDT classes, and separation of real helper coverage from fake
  Surefire process tests.
- Task 1 GREEN added the explicit `real-jdt-it` profile and one root `verify`
  lifecycle. Real tests now require all four portable prerequisites and fail
  closed when any is missing; remote workflow runs unit plus quality only
  because it does not provision the fixed source/JDT distribution/dependency
  inputs required by the real integration tests.
- Task 2 RED observed four cache misses: identical definition, legal-empty,
  failed, and prepare/outgoing requests were physically repeated.
- Task 2 GREEN now caches JDT navigation by operation and exact position/item
  for one session, validates results before success caching, journals physical
  exchanges separately from key-only hits, and retains the private journal and
  immutable statistics after session close.
- Task 3 persists Step05 and Capsule contexts by typed references, reopens the
  existing navigation index/compilation to hydrate the same immutable view, and
  keeps business model packets self-contained.
- Task 4 removes embedded source blocks from Markdown while preserving nine
  chapters, short citations, and the independent source-reference sidecar.
- Task 5 completed the full local unit/real-JDT/quality lifecycle and the fixed
  four-entry comparison. The successful comparison observed 4,466 physical
  navigation RPCs and 1,766 cache hits across 6,232 logical requests.

## Current state

- Tasks 1 through 4 are implemented and verified.
- Task 5 implementation and measurement are complete; independent review and
  Git delivery remain.

## Changed files

- `progress/navigation-reuse-implementation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git fetch --prune origin` | PASS | `origin/main` was `080a86d` before PR 22 |
| `git diff --exit-code origin/main -- src pom.xml .mvn .github tools` | PASS | formal code/config matched the fetched baseline before the docs merge |
| `gh pr view 22 --json files,state,mergeCommit` | PASS | MERGED; 22 documentation files only; merge commit `4bc15d2` |
| `mvn -o -t .mvn/toolchains.xml -Dtest=MavenLocalCiClassificationTest,JdtSyntaxHelperClientTest test` | PASS | Initial Task 1 GREEN: 7 tests, no failures/errors/skips |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtProjectSessionTest test` before cache implementation | RED | 19 tests; four expected cache failures, no errors after the fake-server correction |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtProjectSessionTest test` after cache implementation | PASS | 20 tests, no failures/errors/skips |
| `mvn -o -t .mvn/toolchains.xml -Dtest=JdtProjectSessionTest,MavenLocalCiClassificationTest,JdtSyntaxHelperClientTest test` after review corrections | PASS | 32 tests, no failures/errors/skips |
| `mvn -o -f tools/jdt-syntax-helper/pom.xml verify` | PASS | helper 9 unit tests plus 1 executable integration test |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Pquality,real-jdt-it ... verify` | PASS | 454 Surefire tests (0 failures/errors, 2 skipped), 2 real JDT Failsafe tests, SpotBugs 0 bugs/errors, PMD PASS; total 8:08 |
| fixed four-entry `RepositoryRunMain --mode materials-only` | PASS on fresh comparison session | 4 materials; method/call counts identical to baseline; 4,466 physical RPCs, 1,766 cache hits |
| normalized model-packet comparison | PASS | only session-owned temporary JDT project roots differed before normalization; packets and observations otherwise identical |
| persisted size comparison | PASS | flow slices 3,792,840→169,652 bytes; capsules 3,630,318→4,515 bytes |
| preserved report JSON rerender | PASS | 9 chapters; 249,102→12,033 bytes; 61 cited refs all present in the 457-record sidecar; no embedded source blocks |

## Decisions

- Ruling: the implementation starts from `4bc15d2`; Java 25 remains a recoverable local experiment because the accepted application baseline is Java 17.
- Ruling: CI classification is implemented first so later validation does not repeat the same tests.
- Ruling: this delivery does not include a global design/code audit or any product-model call.
- Ruling: cache ownership remains inside `JdtLanguageServerClient`; entry
  traversal and `EntryCodeContext` are not cached.

## Blockers

- No implementation blocker. Real-JDT verification remains a local-only check
  because the remote workflow does not provision its frozen source/tool inputs.

## Exact next action

- Complete the independent Standards/Spec review, apply any required findings,
  rerun final verification if code changes, then commit, push, merge, and verify
  `origin/main`.

## Resume checks

- Confirm branch `codex/navigation-reuse-implementation` and base `4bc15d2`.
- Read this file and the approved design before editing.
- Run only the selector directly covering the current task; never run heavy Maven commands concurrently.
