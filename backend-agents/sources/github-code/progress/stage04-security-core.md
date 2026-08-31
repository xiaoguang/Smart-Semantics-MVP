# Progress: Stage 04 security production GREEN

- Status: COMPLETE
- Agent role: Stage 04 security production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Fail-closed SourceRegistry ancestry and ambiguity handling, plus completed-candidate deterministic revalidation. Production code and this progress file only.
- Approved inputs: scoped `AGENTS.md`; Stage 04 source-registration/archive validation contracts; `progress/stage04-security-tests.md`; `Stage04SecurityTest`; directly affected Stage 04 production seams.
- Current branch/worktree: Shared dirty worktree; preserve unrelated work and every other agent's progress file.

## Completed

- Read the scoped guidance, TDD instructions, Stage 04 registration/recovery contract, all three direct security RED cases, and the current SourceRegistry/Agent implementation.
- Confirmed source registry currently checks only the nearest existing ancestor for symlinks, silently selects the first equivalent registration, and completed-slot lookup only scans sidecars without archive admission.

## Current state

- Direct security selector reproduced 3/3 expected failures: a symlinked registry-root ancestor is accepted, equivalent registrations are selected by sorted-first iteration, and completed-slot recovery returns a manifest-invalid Candidate.
- The first SourceRegistry vertical makes the intended custom ancestor and duplicate-registration cases pass. It exposed a platform-path caveat: this JVM's configured temporary directory is spelled through macOS's `/var -> /private/var` system alias, so checking from filesystem root also rejects every normal temporary fixture before it reaches an input-controlled path. The next small adjustment preserves an explicitly trusted process temporary-directory spelling while continuing to reject every symlink below it.
- The adjusted SourceRegistry slice is GREEN; the narrow test now has only the planned completed-candidate recovery failure.
- Completed recovery now opens the archive, requires every immutable archive check to pass, then requires the existing full deterministic validation receipt to be valid before returning an already-completed slot. It does not construct a lifecycle bridge or invoke the Provider.
- The requested direct regression set found one unrelated-but-affected compatibility expectation: the legacy four-argument Agent still advertises Round-2 as `NOT_IMPLEMENTED`; the new five-argument review-store seam should be the only one that performs it. Restore that stable public code without weakening its five-argument validation gate.
- All requested security and direct Stage 04 selectors are GREEN. The completed candidate path rejects the tampered archive before any Provider call; duplicate equivalent registrations are rejected before Stage 01 analysis.

## Changed files

- `progress/stage04-security-core.md` (this file)
- `src/main/java/com/linguan/codemd/stage04/FilesystemSourceRegistry.java`
- `src/main/java/com/linguan/codemd/stage04/DefaultCodeToMarkdownAgent.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04SecurityTest test` | RED | 3 tests, 3 assertion failures, 0 errors: all planned missing security gates reproduced. |
| `mvn -Dtest=Stage04SecurityTest test` | Partial GREEN | The symlink/ambiguity assertions now pass; the third test errors early with `SOURCE_REGISTRATION_INVALID` because `/var` is a system symlink in the configured JVM temp-root path. |
| `mvn -Dtest=Stage04SecurityTest test` | Partial GREEN | 3 tests, 1 remaining assertion failure: completed recovery returns the Markdown-tampered Candidate. |
| `mvn -Dtest=Stage04SecurityTest test` | GREEN | 3 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04Round2Test,Stage04PublicCoreTest,Stage04PersistedRecoveryTest test` | Partial GREEN | 21 tests, 1 failure: legacy four-argument `improveCandidate` returned `M8_REQUEST_INVALID` where its direct contract requires `NOT_IMPLEMENTED`; the other 20 tests passed. |
| `mvn -Dtest=Stage04ImprovementTest,Stage04ReviewStoreTest,Stage04Round2Test,Stage04PublicCoreTest,Stage04PersistedRecoveryTest test` | GREEN | 21 tests, 0 failures, 0 errors after restoring the legacy four-argument code. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- The public seams under test are `FilesystemSourceRegistry.resolve`/registered source use in `generateCandidate`, and completed-slot recovery through `generateCandidate`; no internal mock or Provider bypass will be added.
- Keep the existing v2 archive object/manifest validation path authoritative for completed recovery; do not reinterpret a sidecar scan as archive validation.
- The only platform compatibility allowance is the JVM-configured temporary-directory's own system alias ancestors (for this macOS runtime, `/var -> /private/var`). It does not allow symlinks in the configured registry/snapshot path or below the temporary root.
- The four-argument constructor remains intentionally Round-1-only and reports `NOT_IMPLEMENTED` for improve; five-argument construction with a `CandidateReviewStore` is the explicit Round-2 authorization seam.

## Blockers

- None.

## Exact next action

- Complete.

## Resume checks

- Verify this progress file is present and remains the only owned progress record; preserve all shared untracked Stage 01–04 files.
