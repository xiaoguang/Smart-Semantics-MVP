# Progress: semantic wire rejection GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh — semantic Wire Reset guard GREEN
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01 (ready for final whitespace confirmation)
- Scope: Make the current public wire guard fail closed for pre-reset discriminators carried by an otherwise current descriptor header. Change only the production guard, this progress record, and a current implementation audit if its stated behavior becomes inaccurate.
- Approved inputs: Published Source Code Analysis naming design; `docs/DESIGN.md` §§0 and 15; both implementation plans; source-scoped `AGENTS.md`; the existing `PreResetWireRejectionTest` RED; and the parent task's bounded implementation instruction. No model, source capture, network, POM, CI, legacy package, test, or commit work is authorized.
- Current branch/worktree: `codex/source-analysis-semantic-cutover` / `/private/tmp/linguan-source-analysis-semantic-cutover`

## Completed

- Read the required repository, prototype, backend-agent, and source-agent instructions; both implementation plans; the Wire Reset design and current audit; README; the RED progress; and the current guard/test seam.
- Recorded the pre-existing worktree-wide migration changes without altering them. This isolated worktree is on the assigned branch.

## Current state

- `AnalysisWireFormatGuard` now validates the exact `SOURCE_ANALYSIS` / `v1` header and rejects only top-level structural discriminator fields that identify the removed wire. It leaves arbitrary non-structural descriptor content untouched.
- The RED's six current-header cases cover: a `stages/` path; a numbered semantic-step key; a `stage-receipt.json` receipt; a stage schema; the former Maven/Java package identity; and a former wire alias.
- No legacy descriptor is opened, translated, transformed, or accepted through an alias. Every rejection is the existing `UnsupportedAnalysisWireException` with code `UNSUPPORTED_ANALYSIS_WIRE`.

## Changed files

- `backend-agents/sources/source-code/progress/semantic-wire-rejection-green.md`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/AnalysisWireFormatGuard.java`
- `backend-agents/sources/source-code/docs/DESIGN.md`
- `backend-agents/sources/source-code/README.md`

`spotless:apply` also made a pure formatting-only change to the existing, non-owned `src/test/java/org/sourceanalysis/app/SourceAnalysisArchitectureTest.java`; the parent confirmed that this required formatter output may remain. No test behavior or assertions were edited.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=PreResetWireRejectionTest,SourceAnalysisArchitectureTest test` | PASS | `BUILD SUCCESS`; 15 tests run, 0 failures, 0 errors, 0 skipped (2 architecture, 13 wire rejection). The JDK 17 Toolchain and Enforcer checks passed. An unrelated jqwik test-engine stdout line was treated as untrusted output and did not affect the result. |
| `mvn -t .mvn/toolchains.xml spotless:apply` | PASS | `BUILD SUCCESS`; Spotless kept 20 Java files clean and formatted one pre-existing architecture test. Its Java runtime deprecation warning did not affect formatting. |
| `mvn -t .mvn/toolchains.xml -Dtest=PreResetWireRejectionTest,SourceAnalysisArchitectureTest test` (after `spotless:apply`) | PASS | `BUILD SUCCESS`; 15 tests run, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | PASS | No output and exit 0 after the formatted selector rerun. A final repeat follows this progress update to confirm the completed progress record also has no whitespace errors. |

## Decisions

- Reject only named structural descriptor metadata fields and their identity-shaped values. Do not scan free-form fields, nested business content, or ordinary prose for legacy words.
- The named fields are `path`, `analysisStepKey`, `artifactType`, `schemaVersion`, `receiptFile`, `fileName`, `groupId`, `artifactId`, `javaPackage`, and `wireAlias`; the guard checks only the legacy identity pattern appropriate to each field.
- Preserve the accepted current header and the existing stable `UnsupportedAnalysisWireException` code. The guard neither opens, transforms, nor translates a legacy descriptor.
- The current implementation audit remains accurate if it continues to describe a general `SOURCE_ANALYSIS/v1` header gate without claiming owner-specific schema validation; change it only if the implementation makes that wording false.

## Blockers

- None.

## Exact next action

- Run the final `git diff --check` after this record update, then hand the uncommitted result to the parent task.

## Resume checks

- Confirm the final `git diff --check` is clean. Do not commit; the parent task owns integration of the pre-existing worktree-wide migration changes.
