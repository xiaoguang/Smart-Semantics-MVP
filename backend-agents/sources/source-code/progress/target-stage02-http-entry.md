# Progress: target-stage02-http-entry

- Status: IN_PROGRESS
- Agent role: Stage 02 M2 implementation
- Model: Terra / xhigh (implementation), guided by the approved Stage 02 design
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement only deterministic Spring MVC HTTP entry discovery and its direct public-seam tests.
- Approved inputs: `docs/DESIGN.md`, `docs/stages/02-discover-application-and-entries.md`, persisted Stage01/M1 artifacts, and the approved GitHub Code Agent implementation plan.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the M2 discovery contract and confirmed M1 now publishes a persisted application-profile draft.
- Added a two-controller public-seam fixture and M2 test. The frozen DepotHead route requires both class-level `/depotHead` and method-level `/batchSetStatus` evidence; a second `GET /health` route proves the inventory is repository-wide rather than a single walkthrough.
- Observed the intended RED: `SpringHttpEntryDiscoverer`, its persisted result, route profile and typed entry record are absent. The RED compiles all upstream Stage01/M1 sources and has no fixture failure.
- Implemented the first M2 vertical slice with JavaParser and verified the two-entry frozen repository fixture GREEN.

## Current state

- The initial M2 selector is verified GREEN: 1 test, 0 failures/errors/skips. It proves only two static routes in a small complete-capture fixture.
- The implementation still fails the target M2 contract: a dynamic annotation currently aborts the whole module instead of being a site-level Gap; it has no explicit site/disposition/shard accounting, and its route excerpt lookup is vulnerable to repeated annotation text.
- The dynamic-route RED first reached the intended missing sites() seam. Its first GREEN attempt then failed before M2 because the newly constructed fixture emitted literal backslashes before Java string quotes. The failing parser input was identified from the test source itself; this is a test-fixture encoding defect, not a discovery decision.
- Implemented the typed M2 site/disposition/shard output. The dynamic-route regression is GREEN: two static entries remain admitted, while the dynamic method has one AMBIGUOUS site with DYNAMIC_ROUTE_EXPRESSION, no affected entry, and exact source evidence.
- A repeated-annotation regression is GREEN: two identical textual PostMapping annotations receive different byte locators, proving M2 uses JavaParser ranges rather than the prior unsafe text-search approach.

## Changed files

- `progress/target-stage02-http-entry.md`
- `src/main/java/com/linguan/codemd/target/stage02/applicationprofile/{HttpEntryDiscoveryProfile,HttpEntryPoint,HttpEntryDiscovery,SpringHttpEntryDiscoverer}.java`
- `src/test/java/com/linguan/codemd/target/stage02/applicationprofile/Stage02SpringHttpEntryDiscovererTest.java`
- Stage02 frozen controller resources and shared test fixture updates

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage02ApplicationProfileDetectorTest test` | PASS | Upstream M1 selector: 4 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage02SpringHttpEntryDiscovererTest test` | RED | Only the four missing M2 public-seam types are unresolved. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage02SpringHttpEntryDiscovererTest test` | PASS | M2 initial selector: 1 test, 0 failures/errors/skips; verified two routes, separate DepotHead route evidence and persisted M2 draft. |

## Decisions

- M2 will reopen the M1 module artifact and Stage01 publication; it will not consume a live `ApplicationProfile` object, reparse POM/config, or read a filesystem path.
- Routes must retain separate class-level and method-level source excerpts; dynamic annotation values become typed gaps instead of guessed routes.

## Blockers

- None.

## Exact next action

- Begin M3 Mapper capability cataloger from the published M1 profile and Stage01 artifacts; M2 remains partial until Stage02 M4 combines all four public files.

## Resume checks

- Re-read this file, `git status --short`, M2 in the Stage02 design, and run only `Stage02SpringHttpEntryDiscovererTest` after changes.
