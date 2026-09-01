# Progress: Stage 01 jshERP acceptance tests

- Status: COMPLETE
- Agent role: TDD acceptance-test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add a fixed-commit, bounded eight-file jshERP Stage 01 acceptance test and test-only helpers if necessary.
- Approved inputs: The local read-only `.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580` checkout and the Stage 01 design/contracts.
- Current branch/worktree: shared working tree; preserve unrelated changes.

## Completed

- Read repository, backend-agent, and GitHub-code source instructions.
- Read Stage 01 design sections 10–12 and the progress template.
- Confirmed the repository has unrelated pre-existing changes; no edits to them.
- Completed the required read-only fixed checkout preflight: HEAD is
  `8c30ce7861570458920175e200bb2a6442713580` and porcelain status is empty.
- Verified the eight declared files and independently observed their current
  byte sizes and SHA-256 values.
- Read the public Stage01Analyzer seam, M1–M3 records, existing contract tests,
  proof helpers, and the jshERP controller/service/mapper/XML source facts.
- Added the real fixed-commit bounded acceptance test. It builds an independent
  size/SHA receipt, uses the java8 profile and bounded scope, and never invokes
  the customer repository's build/runtime.
- Acceptance assertions cover M1 8/8 + exact revision, stable M2/M3 IDs and
  counts across two runs, capability coverage accounting, Proof closure and
  current-byte hashes, legacy rejected-Fact non-copying, dynamic SQL refusal,
  and standard DOCTYPE zero external resolution.

## Current state

- Added `src/test/java/com/linguan/codemd/stage01/JshErpStage01AcceptanceTest.java`.
- Acceptance is implemented and passing against the fixed local checkout.

## Changed files

- `progress/stage01-jsherp-acceptance-tests.md`
- `src/test/java/com/linguan/codemd/stage01/JshErpStage01AcceptanceTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing changes present; preserved. |
| `git -C .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580 rev-parse HEAD` | PASS | Fixed commit is `8c30ce7861570458920175e200bb2a6442713580`. |
| `git -C .workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580 status --porcelain=v1` | PASS | Empty status. |
| `mvn -q -Dtest=JshErpStage01AcceptanceTest test` | PASS | 1 test run, 0 failures/errors/skips; fixed checkout acceptance passed. |
| Post-test fixed checkout HEAD/status preflight | PASS | HEAD remains exact fixed commit; status remains empty. |

## Decisions

- Keep the acceptance test test-only and call the public Stage 01 analyzer seam; do not read or execute customer build/runtime code.
- Use an assumption/blocking result for a missing or dirty fixed checkout, per Stage 01 section 10.
- Do not call `git` or `ProcessBuilder` from JUnit; the required HEAD/status
  preflight is an external shell check and the test only checks the immutable
  path set is present.

## Blockers

- None.

## Exact next action

No further implementation action. Parent agent may review the two owned files
and include them in the overall Stage 01 handoff.

## Resume checks

- Re-run `git status --short`.
- Confirm only this progress file and the acceptance test/helper paths are owned by this task.
- Re-run the exact selector if the parent changes Stage 01 production behavior.
