# Progress: test-tree-path-red

- Status: COMPLETE
- Agent role: TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01 (after mandated RED run)
- Scope: Add one minimal architecture regression test proving that Java source file relative directory components are part of the production/test package-layout contract.
- Approved inputs: Repository/worktree/source-code AGENTS.md; source-analysis-naming-and-delivery-plan.md; target-standards-and-toolchain-plan.md; user-scoped RED-only request.
- Current branch/worktree: codex/source-analysis-semantic-cutover; /private/tmp/linguan-source-analysis-semantic-cutover

## Completed

- Read the root, backend, and source-scoped AGENTS instructions.
- Read both source-code implementation plans in full.
- Read the TDD and verification skill guidance.
- Checked the worktree status and preserved all pre-existing changes.

## Current state

- `SourceAnalysisArchitectureTest` detects forbidden Java package declarations and fixture path tokens, but not Java source file relative directory components.
- Added a temporary `src/test/java/com/linguan/codemd/OldTest.java` whose declaration is the approved `org.sourceanalysis.app` package; the expected forbidden path finding must be RED until the scanner includes Java tree paths.
- Existing Java string-literal positive assertion must remain unchanged and passing.

## Changed files

- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/SourceAnalysisArchitectureTest.java` (planned RED-only test change)
- `backend-agents/sources/source-code/progress/test-tree-path-red.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=SourceAnalysisArchitectureTest test` | RED | Exit 1; Tests run: 3, Failures: 1, Errors: 0, Skipped: 0. Only `scannerRejectsForbiddenJavaTreePathWithApprovedPackageDeclaration` failed; actual findings were `[]`, missing `com.linguan.codemd`. Existing package/literal and architecture assertions passed. |

## Decisions

- Use the existing real filesystem helper and a single temporary project fixture; no mocks, production changes, POM changes, or new fixture files.
- Keep the existing legal Java string-literal assertion intact so forbidden vocabulary in source literals remains ignored.

## Blockers

- None.

## Exact next action

Report the exact RED output and the two allowed changed paths to the parent agent; leave production implementation unchanged for the GREEN owner.

## Resume checks

- Confirm only the architecture test and this progress file are modified by this task.
- Confirm the new assertion fails because the current scanner misses the path, while the existing literal assertion remains valid.
