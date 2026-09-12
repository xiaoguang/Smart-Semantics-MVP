# Progress: JDT LS packet checks

- Status: COMPLETE
- Agent role: Task 2 TDD RED/checks owner
- Model: Codex (inherited agent model)
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Build the isolated JDT LS source-navigation feasibility research harness scaffold, frozen independent oracle JSON inputs, and direct failing tests for materialization and packet checking. Do not implement NavigationProbe or PacketOracleCheck production classes.
- Approved inputs: Task 2 brief; Task 1 pinned JDT LS/LSP4J/JavaParser/Jackson/Gson versions; fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580` and source facts in the brief.
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the Task 2 brief, scoped source-agent instructions, Task 1 research README, and progress template.
- Verified the approved commit object, relevant paths, Git blob IDs, and source bytes using `GIT_NO_LAZY_FETCH=1` Git object reads; no checkout or customer build was used.
- Added the isolated JUnit/Maven scaffold, checker-only registration/financial oracle inputs, and reflection-based public-seam RED tests.
- Added the concrete probe input-only seam and a randomized temporary-source behavioral canary against sibling `checks/` reads and hard-coded target paths.
- Added a static guard that reads the future `NavigationProbe.java` source and rejects `checks/`, `oracle`, frozen registration/financial target class names, and all frozen target line constants.
- Added complete positive packet fixtures for both registration and financial oracles; the signature-only negative is generated from the same metadata and differs only in method-body extent.
- Strengthened materialization assertions to require the exact Git archive, a per-source projection manifest (`originalPath`, `projectedPath`, `gitBlob`, `sha256`) for every selected Java path, matching projected bytes, and recursive build/Eclipse/AP exclusion.
- Replaced the single body negative with three parameterized negatives, each truncating exactly one of `validateCaptcha`, `registerUser`, or `checkLoginName` while leaving the other two complete.
- Corrected financial oracle line semantics to service call line 187, catch line 190, XML predicate line 153, and validated ranges/markers against pinned Git blobs.
- Ran the direct harness test command after the round-2 amendments; Maven compiled the test and the fresh run had 13 tests with 12 intentional assertion failures and zero errors.
- Validated both oracle files with `jq -e .` and checked whitespace with `git diff --check`.

## Current state

- The research harness remains RED because `NavigationProbe` and `PacketOracleCheck` production types are intentionally absent. The independent Git/oracle integrity test and generated packet Jackson parsing pass, proving the RED is not caused by malformed JSON, packet fixtures, or incorrect frozen source facts.

## Changed files

- `backend-agents/sources/source-code/progress/jdtls-packet-checks.md`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/checks/registration-oracle.json`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/checks/financial-oracle.json`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/NavigationProbeTest.java`
- `.superpowers/sdd/jdtls-source-navigation-feasibility-plan/task-2-report.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `GIT_NO_LAZY_FETCH=1 git -C .workspace/jshERP-... cat-file -e 8c30ce...^{commit}` | PASS | Approved commit object exists; working-tree HEAD was not used |
| `GIT_NO_LAZY_FETCH=1 git ... ls-tree/cat-file` plus SHA-256 checks | PASS | Six exact source blobs/bytes match recorded oracle identities |
| `mvn -f research/jdtls-source-navigation-feasibility/pom.xml -o -Dtest=NavigationProbeTest test` | RED (expected) | Fresh run: Tests run: 13; Failures: 12; Errors: 0; failures are missing `NavigationProbe`/`PacketOracleCheck` seams |
| `jq -e . checks/registration-oracle.json checks/financial-oracle.json` | PASS | Both independent oracle files are valid JSON |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Keep the initial POM dependency set limited to cached JUnit and Maven compiler/Surefire plugins so the mandated offline RED command reaches the tests; Terra may add the pinned LSP4J/JavaParser/Jackson/Gson runtime dependencies after the authorized dependency installation.
- Define the RED seam as static `NavigationProbe.materialize(Path snapshotRepository, String snapshotCommit, Path trialRoot)`, static `NavigationProbe.probe(String snapshotCommit, Path projectionRoot, String entryFile, int entryStartLine, int entryEndLine, int maxMethods, int maxDepth, Duration timeout)`, and static `PacketOracleCheck.check(Path packet, Path oracle)`. The navigator-facing API and source are checked for absence of oracle/checker method or parameter names/types, target names, target paths, and frozen target line constants.
- Keep oracle files checker-only: they record independent commit/blob/SHA-256, exact source ranges, required markers, and review points, but tests never pass their paths to `NavigationProbe`.
- Keep the concrete probe seam constrained to `(snapshotCommit, projectionRoot, entryFile, entryStartLine, entryEndLine, maxMethods, maxDepth, timeout)`; its behavioral canary fixture has no target source and must not expose a sibling `checks/` canary.
- Require `input/jshERP-8c30ce7861570458920175e200bb2a6442713580.tar` and `input/projection-manifest.json` as materialization outputs; validate one manifest `sources` entry per selected Git Java path and matching blob/hash/projected bytes; inspect every projection path recursively for prohibited build/Eclipse/AP inputs.

## Blockers

- `NavigationProbe.java` and `PacketOracleCheck.java` are intentionally absent pending the Terra harness implementation; this is the expected TDD RED, not an environment blocker.
- No current blockers beyond the intentional missing production seams.

## Exact next action

Terra should implement only the production seams specified by the failing tests, rerun the direct harness test command, and preserve the oracle/checker separation.

## Resume checks

- Re-read this file and run `git status --short` in `/private/tmp/linguan-source-analysis-process-design`.
- Confirm no NavigationProbe or PacketOracleCheck production source has been added.
- Run only the direct research-harness test command named in the Task 2 brief.
