# Progress: source-analysis-local-git-capture

- Status: COMPLETE
- Agent role: Terra/xhigh implementation following the approved Source Code Analysis Agent design
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02T02:47:21Z
- Last updated: 2026-09-02T03:42:00Z
- Scope: Implement the first bounded vertical slice of `LocalGitCommitCaptureAdapter`: exact local commit admission, safe raw-tree enumeration, immutable snapshot installation, and source-registration output using only synthetic local Git fixtures. No customer capture and no analysis-step M1--M3 implementation in this work unit.
- Approved inputs: Approved master plan; scoped `AGENTS.md`; `docs/DESIGN.md`; `docs/analysis-steps/01-verified-source-inventory.md`; synthetic test repositories only.
- Current branch/worktree: `codex/source-analysis-local-git-capture` at `/private/tmp/linguan-source-analysis-local-git-capture`

## Completed

- Confirmed a clean worktree and read the scoped source-agent rules and the source-inventory design contract.
- Added the first public-seam RED test for exact committed-tree capture, text/media/executable inventory, and worktree independence.
- Implemented the independent local capture seam and added regression coverage for source-registration/receipt identity and symlink rejection.
- Updated design maturity text to distinguish the capture slice from the still-unimplemented verified-source-inventory analysis step.
- Added a fail-closed local-config include regression and fixed PMD so the project quality gate runs with the project JDK 17 Toolchain rather than the shell JDK.

## Current state

- The capture tests and local quality gate are GREEN. This work unit deliberately stops before private source-registry lookup and M1--M3 so it does not claim a completed analysis step.

## Changed files

- `progress/source-analysis-local-git-capture.md`
- `src/test/java/org/sourceanalysis/app/capture/localgit/LocalGitCommitCaptureAdapterTest.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `docs/plans/target-standards-and-toolchain-plan.md`
- `pom.xml`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Clean before this progress file was created. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | EXPECTED RED | Test compilation fails only because `LocalGitCommitCaptureAdapter`, `LocalGitCaptureRequest`, and `SourceRegistrationReference` are absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | PASS | 4 tests: committed-tree inventory, source identity, worktree independence, exact commit rejection, symlink rejection, and external-config rejection. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | All Java sources satisfy the project formatter. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipUTs=true verify` | PASS | Enforcer, JDK 17 compile, SpotBugs, and PMD all pass with tests intentionally skipped; the direct capture selector is recorded separately. |
| `mvn -t .mvn/toolchains.xml -o dependency:analyze-only` | PASS with existing incremental-scope warnings | Warns about declared future libraries and transitive Jackson/JUnit artifacts; no dependency was added by this slice. |

## Decisions

- Keep this delivery narrowly focused on the independently useful capture seam. It will not claim that the verified-source-inventory analysis step is implemented.
- Use constrained `git` plumbing without a shell and synthetic local repositories only; do not run the approved jshERP capture during this slice.
- Require the Git executable through composition rather than hard-coding a host path. PMD is independently pinned to the project JDK 17 Toolchain so local shell JDK versions cannot corrupt quality checks.

## Blockers

- None.

## Exact next action

- Start the M1 `FrozenRequestAdmission` RED from a fresh main-based worktree. It must consume only a source-registration reference and content-addressed request artifacts; do not add a compatibility path to this capture seam.

## Resume checks

- Re-read this file, verify the published capture commit is an ancestor of `origin/main`, and run `LocalGitCommitCaptureAdapterTest` before changing the capture contract.
