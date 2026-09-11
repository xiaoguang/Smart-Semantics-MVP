# Progress: M6 cross-flow candidate compiler RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add only a minimal public-seam RED test for M6 CrossFlowCandidateCompiler.
- Approved inputs: Step 03/05 fresh-publication contracts and synthetic public fixtures only.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Read the scoped AGENTS.md, Step 06 M6 contract, and target standards/toolchain plan.
- Added `CrossFlowCandidateCompilerTest` with the two-flow synthetic public fixture. It fresh-reopens
  the seven Step 03 semantic payloads and five Step 05 semantic payloads, checks two Flow IDs and
  non-empty persisted process-join signals, then requires the future M6 compiler seam.
- The test specification records the required M6 behavior: exact entry-target/direct-call closure
  may form `PROVEN_HANDOFF`; generic Java/Mapper/XML/SQL anchors alone may not create a business
  order edge; counter material must remain blocking; all candidate/group/signal/Gap dispositions
  and zero-Flow coverage must be closed without Provider calls.

## Current state

- M6 production compiler and process artifact types are not present in the target package.
- The test establishes the missing-public-seam RED without adding production code. Fixture creation
  and fresh publication reopening complete before the RED, so the failure is not a fixture or
  test-compilation error.

## Changed files

- This progress file only so far.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -t /private/tmp/source-code-m6-toolchains.xml -Dtest=CrossFlowCandidateCompilerTest test` | RED | 1 test, 1 failure, 0 errors, 0 skipped; exact failure `CROSS_FLOW_CANDIDATE_COMPILER_NOT_IMPLEMENTED` caused by missing `org.sourceanalysis.app.analysis.interpretation.process.CrossFlowCandidateCompiler` |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/interpretation/process/CrossFlowCandidateCompilerTest.java progress/cross-flow-m6-red.md` | PASS | no whitespace errors |

## Decisions

- Use existing fresh-publication public inputs and a reflection seam, matching the repository's existing
  pre-implementation RED pattern; do not invent a production record or compatibility API in the test.
- The test fixture is intentionally bounded to two published Flows with persisted join signals. The
  semantic mutation matrix (exact handoff, generic-only, counter, full accounting, and zero Flow)
  remains the next M6 behavior assertions after Sol/ultra freezes the concrete result seam.

## Blockers

- None.

## Exact next action

- The minimal `CrossFlowCandidateCompilerTest` is added and its direct selector has the intended RED.

## Resume checks

- Confirm no production, design, POM, schema, or other progress file changed.
- Confirm the test failure remains the explicit M6-not-implemented seam and not test compilation or fixture construction.
