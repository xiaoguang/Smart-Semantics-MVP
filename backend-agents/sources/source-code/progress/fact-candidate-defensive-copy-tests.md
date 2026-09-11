# Progress: FactCandidate defensive-copy RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded test-only owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One immutable-argument-list behavior test for canonical non-boundary `FactCandidate` records, covering real `JAVA_GUARD_CONDITION` and `JAVA_EXACT_CALL` candidates.
- Approved inputs: existing guarded/exact-call fixture enumeration, existing FactCandidate constructors/accessors, and the quality diagnosis identifying the conditional shallow-copy defect.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the source-scoped instructions, both implementation plans, TDD guidance, and the quality diagnosis.
- Confirmed this is test-only: no production, fixture, design, or unrelated-test edits are authorized.
- Created this progress file before the new Java test and intent-to-add staged it with `git add -N` before the Java edit.
- Inspected the canonical nested `FactCandidate` record and confirmed both non-boundary variants accept empty argument lists through the full constructor.
- Added the one behavior test using real guarded and exact-call persisted enumerations.

## Current state

- The test obtains real guarded and exact-call candidates from existing enumeration, reconstructs canonical records with caller-owned mutable empty argument lists, mutates those lists, and asserts candidate accessors remain empty and unmodifiable.
- The exact selector reached the assertions for both candidate kinds and produced the expected RED: both accessors reflected caller mutation as `[null]`.

## Changed files

- `progress/fact-candidate-defensive-copy-tests.md` (owned; intent-to-add staged)
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateDefensiveCopyTest.java` (owned; single behavior test)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Progress creation + `git add -N -- progress/fact-candidate-defensive-copy-tests.md` | PASS | Intent-to-add completed before the Java edit; `git diff --check` passed. |
| Exact test selector (session 61470) | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; both guard and exact-call assertions failed with `Expecting empty but was: [null]`. |
| Absolute one-file Spotless apply (session 66610) | PASS; numeric exit 0 | Applied with `-DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateDefensiveCopyTest.java`; exactly one file cleaned. |
| Absolute one-file Spotless check (session 52473) | PASS; numeric exit 0 | One selected file clean; no changes needed. |
| Post-format exact selector (session 92389) | RED; numeric exit 1 | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`; same two assertion failures, proving the RED is not formatting/setup noise. |
| Scoped `git diff --check` | PASS; numeric exit 0 | No whitespace errors in the owned progress/test scope. |

## Decisions

- Use real existing fixture enumeration for both candidate kinds; do not hand-build graph facts, mutate persistence, or add fixture behavior.
- Preserve every original candidate field and replace only the two argument-list slots with caller-owned mutable `ArrayList` instances.
- Assert both lists are empty after caller mutation and that each accessor rejects direct mutation, proving defensive copy and unmodifiable exposure.
- Do not implement production remediation in this slice; stop after the genuine RED and release Maven.

## Blockers

- None for this bounded test slice. The production remediation remains intentionally outside scope for the next owner.

## Exact next action

- Release the Maven lease and hand off the two-file test-only RED to root/Terra; do not modify production in this slice.

## Resume checks

- Maven lease was held serially for the exact selector, one-file Spotless apply/check, and post-format exact selector; it is now released.
- Keep Java scope to the new test file only; no production/docs/fixtures/unrelated tests.
- Root may implement the unconditional `List.copyOf` production remediation separately; this test is the regression oracle for both non-boundary variants.
