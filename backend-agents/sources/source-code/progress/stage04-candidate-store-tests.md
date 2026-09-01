# Progress: Stage04 immutable Candidate store RED tests

- Status: COMPLETE
- Agent role: Stage04 TDD test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Bounded filesystem Candidate store contract: exact artifact archive, manifest closure, idempotency/collision, and fail-closed path/encoding/size safety.
- Approved inputs: scoped AGENTS.md; `docs/stages/04-runtime-archive-trace-recovery.md` §§6, 10, 16; current Stage04 ledger and lifecycle seam.
- Current branch/worktree: shared worktree; preserve unrelated parent changes.

## Completed

- Read the Stage04 identity, persistence, validation, and TDD matrix sections.
- Confirmed this slice will not use Stage03 generation, Provider, HTTP, archive replay, or customer source files.
- Created this progress file before adding the Candidate store test.

## Current state

- Preparing one bounded `Stage04CandidateStoreTest` against a proposed
  `FilesystemCandidateStore(Path, CandidateStoreLimits)` and typed
  `CandidateBundle`/Stage04 `CandidateReference` seam. The fixture will contain
  all non-manifest §10 artifacts; the store must generate `archive-manifest.json`.
- Added the single test class covering exact regular/no-link artifact closure,
  generated manifest size/SHA entries, same-byte idempotency, changed-byte
  collision preservation, invalid artifact names/sets, workspace/candidate
  symlinks, invalid UTF-8/canonical JSON, and candidate byte limits.
- Correcting byte-array assertions and making the collision mutation preserve the
  reference/document identity while changing one canonical sidecar byte.
- The collision mutation now changes only `proof-pack.json` from `{}` to the
  still-canonical `{"x":1}`; the original proof-pack bytes are asserted intact
  after the collision.

## Changed files

- `progress/stage04-candidate-store-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04CandidateStoreTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04CandidateStoreTest test` | GREEN | Main compilation succeeded for 175 sources; test compilation succeeded for 51 sources; `Stage04CandidateStoreTest` ran 6 tests with 0 failures, 0 errors, 0 skipped. |

## Decisions

- Use a JUnit temporary workspace and synthetic UTF-8/canonical JSON/JSONL bytes;
  no source tree or external process is opened.
- Assert artifact bytes and manifest size/SHA closure independently of the store
  implementation; candidate directory naming is checked from the returned
  candidate identity digest.
- Treat traversal, absolute/unlisted/missing entries, symlink roots, invalid
  encoding/canonical JSON, and byte-budget overflow as stable fail-closed store
  errors with no installed candidate.

## Blockers

- The Stage04 store seam is present in the shared worktree and the corrected
  fixture is green. No production file was edited by this test slice.

## Exact next action

- None for this test-authoring slice; the narrow selector is green with no
  remaining failures.

## Resume checks

- COMPLETE. Only `src/test/java/com/linguan/codemd/stage04/Stage04CandidateStoreTest.java`
  and this progress file were edited by this slice; no production or design file
  was changed.
