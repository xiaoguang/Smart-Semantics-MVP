# Progress: target-stage02-application-profile

- Status: IN_PROGRESS
- Agent role: Stage 02 M1 implementation
- Model: Terra / xhigh (implementation), guided by the approved Stage 02 design
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement only the deterministic Stage 02 application-profile module and its direct public-seam tests.
- Approved inputs: `docs/DESIGN.md`, `docs/stages/02-discover-application-and-entries.md`, persisted Stage 01 artifacts, and the approved GitHub Code Agent implementation plan.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the Stage 02 M1 contract and verified the Stage 01 persisted publication seam available to consume.
- Added the direct Stage02 M1 public-seam fixture: frozen POM, application config and Java source are first published through a real Stage01 artifact store.
- Observed the intended RED: the `ApplicationProfileDetector` API and related closed records do not exist.
- Implemented the M1 persisted vertical slice and observed the positive Java/Spring MVC/MyBatis profile test GREEN.
- Added a binary-denominator regression: Stage02 must retain Stage01 media in coverage while never decoding it.
- M1 is complete: its direct selector now covers the target positive profile, release conflict, root determinism, and binary inventory behavior.

## Current state

- Stage02 M1 is complete and documented. M2 HTTP entry discovery is the next bounded module; it will consume M1's persisted module draft rather than re-read POM/config.

## Changed files

- `progress/target-stage02-application-profile.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only pre-existing untracked `.jqwik-database` and `target.tmp`; neither will be touched. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage02ApplicationProfileDetectorTest test` | RED | Target Stage02 public detector/types are absent. Test-only Java 17/accessor errors were corrected before production work; the remaining error set is exclusively the missing Stage02 seam. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage02ApplicationProfileDetectorTest test` | PASS | Stage02 M1 selector: 4 tests, 0 failures/errors/skips. |

## Decisions

- M1 will reopen Stage 01 artifacts and source bytes through identity-based handles only; it will not take a filesystem path or execute Maven.
- The first implementation slice is Java release plus Spring MVC/MyBatis signal detection and deterministic conflict handling; publication is reserved for Stage 02 M4.

## Blockers

- None.

## Exact next action

- Preserve this M1 progress record and begin a separate `target-stage02-http-entry.md` progress file before Stage02 M2 test work.

## Resume checks

- Re-read this file, `git status --short`, Stage 02 M1 contract, and run only `Stage02ApplicationProfileDetectorTest` after changes.
