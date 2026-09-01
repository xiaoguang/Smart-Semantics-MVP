# Progress: target Stage01 local Git capture

- Status: COMPLETE
- Agent role: Terra/xhigh implementation owner executing the approved Stage01 capture vertical slice
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Establish the `LocalGitCommitCaptureAdapter` public behavior with a deterministic local synthetic Git repository, then implement only the records and adapter needed for that verified slice.
- Approved inputs: The user-approved complete implementation plan; `docs/DESIGN.md`; `docs/stages/01-freeze-source.md`; scoped `AGENTS.md`; test-created local Git fixtures only.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`

## Completed

- Read the target architecture, complete Stage01 design, Foundation continuity record, scoped rules, and toolchain contract.
- Confirmed Foundation stores are GREEN for the Stage01 M3 semantic artifact fixture, but no Stage01 production package exists yet.
- Established the capture public-seam RED: its only failures were absent capture request, adapter, result, entry and disposition types.
- Implemented and verified the first private Git-object vertical slice. It reads only an exact commit's tree/blob objects through a scrubbed, shell-free Git plumbing process; three committed regular blobs were captured unchanged after the fixture worktree source changed. Two text files and one binary media file are separately counted and the executable mode remains `100755`.
- Upgraded the slice to the required durable private capture boundary. Capture now atomically installs immutable blob copies with canonical `snapshot-manifest.jsonl`, `capture-receipt.json`, and `source-registration.json`; a fresh reopen verifies each document binding and every blob SHA/size before returning the rootless result.

## Current state

- Starting the capture-adapter slice before M1 request admission. The test will prove an exact committed tree is captured from Git objects even after the fixture worktree changes, and that text/media files retain distinct deterministic dispositions.
- No customer repository, live Provider, external network, customer Maven, or old production package will be used.
- A single public-seam test is now written. It requires the as-yet-absent capture request, adapter, result, manifest-entry and disposition types; the next selector should therefore fail at test compilation for that precise missing seam.
- The initial GREEN exposes only rootless in-memory capture records. It intentionally does not yet meet the required durable `snapshot-manifest.jsonl` / receipt / registration installation contract; the next RED adds that store boundary rather than treating this slice as complete.
- The capture test now requires a private `FileSystemLocalGitCaptureStore`, the two-argument adapter construction, and a fresh reopen equality check. The next selector should fail only because that durable store seam is absent.
- The durable store RED was exactly two missing-type errors for `FileSystemLocalGitCaptureStore`; the implementation is now GREEN. The one-argument, non-persistent adapter constructor was removed so a successful capture cannot skip durable installation.
- Reopened this bounded capture work unit for the next test-first security gate: local Git config include rejection.
- Established the config-include RED: the adapter captured instead of rejecting a repository whose local config contained a standard `[include]` section. It now rejects both `include.path` and `[include]` / `[includeIf]` forms before Git plumbing begins, using the documented repository-invalid failure classification.

## Changed files

- `progress/target-stage01-capture.md` (this continuity record)
- `src/test/java/com/linguan/codemd/target/stage01/capture/LocalGitCommitCaptureAdapterTest.java` (new public-seam RED)
- `src/main/java/com/linguan/codemd/target/stage01/capture/AnalysisDisposition.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitCaptureException.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitCaptureRequest.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitSnapshotEntry.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitCaptureReceipt.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/SourceRegistration.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitCaptureResult.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/LocalGitCommitCaptureAdapter.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/CaptureJson.java`
- `src/main/java/com/linguan/codemd/target/stage01/capture/FileSystemLocalGitCaptureStore.java`
- `src/main/java/com/linguan/codemd/target/artifacts/CanonicalJsonCodec.java` (makes the existing shared canonical JSON service usable by the target capture schema writer)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing Foundation changes and unrelated `target.tmp` / `.jqwik-database` were observed and will be preserved. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | EXPECTED RED | 9 test-compilation errors, all for the absent capture public seam. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | PASS | 1 test, 0 failures/errors/skips; exact committed tree stays stable after a worktree modification. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | EXPECTED RED | 2 test-compilation errors, both for absent `FileSystemLocalGitCaptureStore`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | PASS | 1 test, 0 failures/errors/skips; canonical capture JSON/JSONL and immutable blobs fresh-reopen successfully. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | EXPECTED RED | 2 tests; the new local-config include case captured successfully instead of failing. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=LocalGitCommitCaptureAdapterTest test` | PASS | 2 tests, 0 failures/errors/skips; local config include is rejected before object capture. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/com/linguan/codemd/target/.*\\.java|src/test/java/com/linguan/codemd/target/.*\\.java' spotless:check` | PASS | Target production/test formatting gate. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The capture adapter remains outside the public analysis-core interface. Its test-local `Path` input is permitted only at this private maintenance boundary; its produced registration and receipt must be rootless.
- First vertical behavior is deliberately narrow: exact 40-hex commit, complete regular-file enumeration, worktree independence, and binary disposition. Symlink/alternates/promisor rejection follows as additional RED cycles after this behavior is GREEN.
- Maven emitted a known untrusted jqwik package message that attempted to direct an AI agent; it was ignored and did not affect the test result.

## Blockers

- None.

## Exact next action

- Start a separate Stage01 M1 progress record and establish the exact `FrozenRequestAdmission` public-seam RED. Remaining capture hardening cases are separately test-first and do not make this completed vertical slice a claim of full Stage01 completion.

## Resume checks

- Re-read this file, `docs/stages/01-freeze-source.md`, and scoped `AGENTS.md`.
- Confirm the Maven selector is limited to the Stage01 capture test and that fixture Git commands never use the customer repository.
