# Progress: target Stage01 publication

- Status: COMPLETE
- Agent role: Terra/xhigh implementation owner for Stage01 M3
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement M3's fresh-reopen publication of the three Stage01 semantic files and the final Stage store handoff.
- Approved inputs: User-approved implementation plan; `docs/DESIGN.md` §13.3; `docs/stages/01-freeze-source.md` M3/§8.1.1; scoped `AGENTS.md`; completed Stage01 M1/M2 seams.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`

## Completed

- Added a path-free immutable input registry for exact analysis/frozen request bytes.
- Reopened M1/M2, checked controls/status/reference closure, published exactly the required three semantic files through M3, then installed the public Stage01 set through the Stage store.
- Verified M3 module payload count and the Stage store's separate receipt-last set.
- Committed the validated Foundation + Stage01 vertical as `d497d14` and pushed it to
  `origin/main` after fetching the confirmed `e8b64cb` ancestor.

## Current state

- The synthetic Stage01 vertical is complete. Full jshERP and broader security/limit acceptance remain later integration work; no test result claims them.

## Changed files

- `src/main/java/com/linguan/codemd/target/stage01/publish/**`
- `src/test/java/com/linguan/codemd/target/stage01/publish/Stage01PublicationSpecifierTest.java`
- `docs/stages/01-freeze-source.md`
- `docs/DESIGN.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01PublicationSpecifierTest test` | PASS | 1 test; M3 writes exactly three semantic artifacts and Stage store reopens its public set. |
| Stage01 direct selector set | PASS | 9 tests; Capture, M1, M2 and M3 direct tests all pass. |
| Target Spotless and `git diff --check` | PASS | Target Java formatted; no whitespace errors. |

## Decisions

- M3 receives run/frozen bytes through an identity-only registry. The registry is an internal composition boundary, not a new product API or a filesystem locator.

## Blockers

- None for this completed vertical.

## Exact next action

- Begin Stage02 from the pushed main baseline; keep Stage01's unrun full-jshERP and adversarial
  acceptance explicitly separate from this completed implementation vertical.

## Resume checks

- Confirm `origin/main` resolves to `d497d14` or a later fast-forward before starting Stage02.
