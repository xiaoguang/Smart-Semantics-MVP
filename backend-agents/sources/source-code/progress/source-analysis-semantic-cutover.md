# Progress: source-analysis-semantic-cutover

- Status: COMPLETE
- Agent role: Terra/xhigh implementation lead
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Delivery 2 — move the source agent to its semantic product name; reset the Maven/package/wire vocabulary; remove pre-reset implementation; retain only a new fail-closed source-analysis skeleton and tests.
- Approved inputs: User-approved Source Code Analysis Agent naming refactor and complete implementation plan; published design PR #1 (`3914eb5`).
- Current branch/worktree: `codex/source-analysis-semantic-cutover` at `/private/tmp/linguan-source-analysis-semantic-cutover`

## Completed

- Verified the published design commit is present on the branch.
- Read the scoped implementation rules and both approved implementation plans.
- Established the architecture RED with JDK 17: the pre-reset directory, Maven
  identity, missing semantic roots, and legacy wire vocabulary caused the
  intended failure.
- Moved the Agent to `sources/source-code/`, replaced Maven identity, removed
  pre-reset production/test/fixture trees, and created the semantic package
  skeleton.
- Installed the project-local JDK 17 toolchain configuration and verified the
  architecture GREEN.
- Added a closed current-wire guard and verified the pre-reset/unknown-wire
  rejection GREEN.
- Added the path-scoped GitHub Actions workflow; it uses pinned actions and
  runs the local Maven toolchain/format/test/quality gates serially.
- Hardened the new-wire rejection boundary: a descriptor that carries a current
  header but a pre-reset structural discriminator fails closed with the same
  stable error. The guard only checks descriptor metadata, never business
  content.
- Hardened the architecture test to reject legacy Java directory layouts in
  both production and test source trees, even when their package declarations
  are new. It still ignores ordinary Java string literals.
- Completed independent review. The one reported P1 (legacy test directory
  layout not being checked) was reproduced as RED, fixed, and independently
  confirmed closed.

## Current state

- The only active source tree is `sources/source-code/` with
  `org.sourceanalysis.app` package roots. No compatibility reader, old
  production/test package, old fixture, old CLI, or old Maven identity remains.
- Local verification and independent review are complete. The delivery is
  ready to stage, create its squash PR, and merge using the user-approved
  local-gate policy.

## Changed files

- `backend-agents/sources/source-code/progress/source-analysis-semantic-cutover.md`
- See the task-owned progress files for the architecture RED, wire-rejection
  RED/GREEN, CI, and documentation closeout paths.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git fetch origin main` | PASS | `origin/main` includes published design merge `3914eb5`. |
| `mvn -t .mvn/toolchains.xml -Dtest=SourceAnalysisArchitectureTest test` | RED then PASS | Expected legacy failure, then 1 test passed after semantic reset. |
| `mvn -t .mvn/toolchains.xml -Dtest=PreResetWireRejectionTest,SourceAnalysisArchitectureTest test` | PASS | Final selector: 16 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml spotless:apply` | PASS | Semantic source/test files formatted. |
| `git diff --check` | PASS | No whitespace errors after code slices. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 20 Java files clean. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS | Final run: Enforcer, JDK 17 Toolchain, 16 tests, SpotBugs, and PMD all pass. |

## Decisions

- Use a fresh worktree based on `origin/main`; preserve all existing user worktrees and historical progress records.
- Do not migrate pre-reset wire data or retain aliases/readers; reject it through a stable fail-closed seam.
- The current-wire guard recognizes only the current header; it does not parse,
  translate, or enumerate legacy formats.
- The project-owned toolchain uses the approved Homebrew JDK 17 stable symlink;
  CI substitutes its ephemeral runner JDK only inside its checkout.
- The first offline quality run exposed an uncached PMD report skin. A single
  non-offline run downloaded that declared Maven-plugin transitive component;
  the identical offline quality command then passed.

## Blockers

- None. The remote repository has no enforced branch-protection CI capability;
  this delivery uses required local verification before PR merge, per user
  direction.

## Exact next action

- From the verified worktree, stage the approved semantic cutover, create the
  delivery PR, and squash-merge it into `main` without waiting for remote CI.

## Resume checks

- Read this file, inspect `git status --short`, verify the merged `main` has
  the `sources/source-code/` root, then create the next delivery worktree from
  that exact main tip.
