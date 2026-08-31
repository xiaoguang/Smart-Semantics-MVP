# Progress: Stage 04 validation, Trace, and resource hardening

- Status: COMPLETE
- Agent role: Stage 04 production hardening implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Close Candidate address binding, coherent archive replay/identity tamper detection, typed term/proof Trace closure, and pre-allocation Candidate artifact limits. Production code and this progress file only.
- Approved inputs: scoped `AGENTS.md`; final implementation review; direct hardening RED tests; existing Stage 04 canonical archive, validation and Trace modules.
- Current branch/worktree: Shared dirty worktree; preserve unrelated work and every other agent's progress file.

## Completed

- Created this owned record before production edits.

## Current state

- Reproduced all five direct RED cases: A-sidecar substitution redirects a requested Candidate, coherent semantic/archive rewrites validate, term and Proof mutations do not close Trace, and Candidate artifacts are read before the size limit is checked.
- Candidate address binding is now GREEN: `referenceFor` requires the requested ID to equal the canonical sidecar ID before constructing a public reference.
- Candidate archive reads now stat every artifact before allocation and return a separate stable `CANDIDATE_SIZE_LIMIT_EXCEEDED` validation check; the sparse oversized-file case is GREEN.
- Shared canonical assembler projections now make validation byte-compare replayed Stage01 facts/proofs, Stage02 flow/capsule projections, and Stage03 registry/interpretation/model/plan/trace/document artifacts. Source drift is kept distinct from an archived projection mismatch so the latter is a stable `TRACE_CLOSURE_BROKEN` result.
- Trace now explicitly verifies every required Proof node and edge against the re-proven M2 closure and reopens each required source span. Admitted-term Trace binds its meaning to the frozen registry entry, anchor/display fields, minimum atom basis, and eligible atom kinds before following factual basis.
- The direct hardening selector is GREEN (5 tests, 0 failures, 0 errors).
- The requested Stage 04 regression selectors are GREEN (34 tests, 0 failures, 0 errors), and `git diff --check` is clean.

## Changed files

- `progress/stage04-validation-hardening-core.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ValidationHardeningTest test` | RED | 5 tests, 5 failures, 0 errors; each planned hardening gap reproduced. |
| `mvn -Dtest=Stage04ValidationHardeningTest test` | Partial GREEN | 5 tests, 4 failures, 0 errors after Candidate A/B address binding. |
| `mvn -Dtest=Stage04ValidationHardeningTest test` | Partial GREEN | 5 tests, 3 failures, 0 errors after the stat-before-read size gate. |
| `mvn -Dtest=Stage04ValidationHardeningTest test` | Partial GREEN | 5 tests, 1 failure, 0 errors. All closures reject correctly; an archived Proof mismatch currently surfaces as `SNAPSHOT_REOPEN_MISMATCH` rather than `TRACE_CLOSURE_BROKEN`. |
| `mvn -Dtest=Stage04ValidationHardeningTest test` | GREEN | 5 tests, 0 failures, 0 errors. |
| `mvn -Dtest=Stage04TypedTraceTest test` | GREEN | 2 tests, 0 failures, 0 errors after correcting term atom-kind lookup to the canonical atom `role`. |
| `mvn -Dtest=Stage04ArchiveV2Test,Stage04ValidationTraceTest,Stage04TypedTraceTest,Stage04SecurityTest,Stage04PublicCoreTest test` | GREEN | 34 tests, 0 failures, 0 errors. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Reuse existing canonical archive/replay/Trace abstractions rather than adding test-specific repair paths.

## Blockers

- None.

## Exact next action

- Complete.

## Resume checks

- Verify this progress record is present and preserve all shared untracked implementation files.
