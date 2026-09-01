# Progress: target Stage01 verified source index

- Status: COMPLETE
- Agent role: Terra/xhigh implementation owner for Stage01 M2
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement the Stage01 M2 `VerifiedSourceIndexer` vertical: fresh-reopen M1, validate each declared source byte sequence through a path-free registered snapshot handle, distinguish text/media correctly, and install a canonical verified-source-index module artifact for M3.
- Approved inputs: User-approved full implementation plan; `docs/DESIGN.md` §§3.2–3.5, 13.3; `docs/stages/01-freeze-source.md` M2/§8.1.1; scoped `AGENTS.md`; completed M1 and capture module seams.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`

## Completed

- Read the M2 upstream, artifact, accounting, no-Path and failure contracts.
- Confirmed M2 must consume the M1 `ModulePublicationReference`, never the `AdmittedSourceRequest` object retained by M1.
- Added the first public M2 vertical test: it installs an M1 envelope, passes only an opaque
  registered snapshot boundary, and requires M2 to install/reopen an index covering one text
  and one media file.
- The first vertical is GREEN: M2 freshly reopens M1, verifies byte length/SHA and text/media
  disposition, installs its envelope, then reopens and parses the same index.
- Added the next RED test for a real captured snapshot registry. It requires Capture storage to
  expose only `snapshotId + read(fileId)` to M2, never a source path.
- Added the next behavioral RED: M2 must compare opaque no-follow metadata before and after a
  read, then reject an identity race rather than trusting the bytes from only one instant.
- The Capture-store registry and the read-race check are GREEN. The handle now offers a
  path-free metadata observation before and after its byte read; M2 rejects a changed size,
  hash or regular-file status with a stable fatal code.

## Current state

- The M2 package has a canonical index payload, one complete verification shard, rootless Capture
  adapter, source-integrity result, and pre/post-read identity checks.
- M3 is GREEN. It reopens M1/M2 and exact input artifacts, writes exactly three semantic payloads
  through its M3 module, and lets the Stage store write the fourth, final receipt.

## Changed files

- `progress/target-stage01-source-index.md` (this continuity record)
- `src/test/java/com/linguan/codemd/target/stage01/requestadmission/Stage01VerifiedSourceIndexerTest.java`
- `src/test/java/com/linguan/codemd/target/stage01/sourceindex/LocalCaptureRegisteredSnapshotRegistryTest.java`
- `src/test/java/com/linguan/codemd/target/stage01/publish/Stage01PublicationSpecifierTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01VerifiedSourceIndexerTest test` | RED | Test compilation reports only the five missing M2 public types; no production implementation exists. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01VerifiedSourceIndexerTest test` | PASS | 1 test; M1-to-M2 fresh-reopen source-index vertical is green. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01VerifiedSourceIndexerTest,LocalCaptureRegisteredSnapshotRegistryTest test` | PASS | 3 tests; real Capture handle remains rootless and M2 rejects a pre/post-read identity drift. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01PublicationSpecifierTest test` | PASS | 1 test; M3 has exactly three semantic payloads and Stage Store reopens the four-file Stage01 set. |

## Decisions

- M2 will expose a narrow read-only snapshot boundary keyed by `sourceRegistrationId` and typed file identity; filesystem paths remain capture-maintenance internals.
- The first TDD slice uses an in-memory fake boundary. The production private-capture adapter and fault/race hardening follow after this vertical is green.

## Blockers

- None.

## Exact next action

- Stage01 was committed as `d497d14` and pushed to `origin/main`. A later integration work item
  must add the full jshERP and broader Stage01 adversarial acceptance described in the durable
  Stage01 design; this completion record does not claim those unrun checks.

## Resume checks

- Re-read this record, Stage01 M2 contract and current M1 progress.
- Confirm any test fixture uses an installed M1 module reference and does not use a source filesystem `Path` in the M2 public seam.
