# Progress: Stage04 validation hardening RED tests

- Status: COMPLETE
- Agent role: Stage04 validation/Trace hardening test author
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one bounded Stage04 test class for final-review P1 identity, replay/identity, typed-Trace, and byte-limit gaps; no production, design, lifecycle, Round-2, addendum, or existing-test changes.
- Approved inputs: root/backend/source-scoped `AGENTS.md`; `progress/final-implementation-review.md`; `docs/stages/04-runtime-archive-trace-recovery.md`; current Stage04 archive, validation, Trace, fixture, and test seams.
- Current branch/worktree: Shared worktree; preserve unrelated parent and agent changes.

## Completed

- Read the root and nearest scoped instructions, Stage04 design and final implementation review, related progress files, and current Stage04 fixtures/tests.
- Created this unique progress file before test edits.
- Added one new `Stage04ValidationHardeningTest` class with exactly five `@Test` methods covering Candidate A→B sidecar substitution across reference/read/validation/Trace, coherent semantic-sidecar plus identity-root/manifest tampering, admitted-term registry-contract Trace closure, factual Proof dependency-edge Trace closure, and sparse/limit+1 Candidate artifact input.
- Kept all mutations inside per-test temporary archives and used only canonical JSON/manifest rewrites; no production, design, lifecycle, Round-2, addendum, or existing-test file was changed.

## Current state

- Added a single new `Stage04ValidationHardeningTest` with five test methods. Tests use the real `Stage04CandidateFixture`, canonical archive bytes, and deterministic source/Provider seams; no live model, network, customer build, or production mutation is allowed.
- The class is a deliberate RED contract against the final-review P1 gaps: current implementation redirects A→B references/reads, accepts a coherent semantic-sidecar/identity rewrite, omits term registry and Proof-edge closure, and lacks the stable oversized-artifact code.

## Changed files

- `progress/stage04-validation-hardening-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04ValidationHardeningTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04ValidationHardeningTest test` | RED (intentional) | Baseline selector run compiled the test and Surefire ran 5 tests with 5 failures, 0 errors, 0 skips. Failures are the intended missing P1 behaviors: A→B reference substitution, coherent replay/identity rejection, term registry closure, Proof dependency closure, and `CANDIDATE_SIZE_LIMIT_EXCEEDED`. |
| `mvn -Dtest=Stage04ValidationHardeningTest#admittedTermTraceRejectsRegistryContractMutationAfterRootAndManifestRewrite test` | NOT A TEST RESULT | A later retry overlapped a concurrent production compile and Surefire reported `NoClassDefFoundError: CandidateArtifactReader` with 0 tests; the earlier complete selector run above is the authoritative RED evidence. |
| `git diff --check -- progress/stage04-validation-hardening-tests.md src/test/java/com/linguan/codemd/stage04/Stage04ValidationHardeningTest.java` | PASS | No whitespace diagnostics. |
| `git status --short -- progress/stage04-validation-hardening-tests.md src/test/java/com/linguan/codemd/stage04/Stage04ValidationHardeningTest.java` | PASS | Only the owned progress file and new test class are present in the scoped status view. |

## Decisions

- Cover Candidate A→B sidecar substitution as a fail-closed identity seam, coherent semantic-sidecar/root/manifest rewrite as replay/identity rejection, term registry and Proof dependency Trace closure mutations, and one sparse/limit+1 Candidate artifact budget case.
- Run only the new test selector and `git diff --check`; preserve the accurate RED result for production hardening.

## Blockers

## Exact next action

- Production hardening can now implement the missing closures against this selector; rerun only `mvn -Dtest=Stage04ValidationHardeningTest test` after implementation changes.

## Resume checks

- Verify only this progress file and the new test class are owned by this slice; do not touch lifecycle/Round2/addendum tests or production/design files.
