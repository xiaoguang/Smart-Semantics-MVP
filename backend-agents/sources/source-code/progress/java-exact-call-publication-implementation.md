# Progress: java-exact-call-publication-implementation

- Status: COMPLETE
- Agent role: Terra/xhigh M1 exact-call publication owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Replace only M1 candidate-set publication and fresh typed reopening with the approved v3 exact-call closed union. Authorized production paths are `FactCandidateSetModulePublisher`, `PersistedFactCandidateSetReader`, and the strictly required M1 v3 artifact-policy registration in `AtomicCanonicalPublicationEngine`.
- Approved inputs: Root-inspected Luna public persistence RED; published Step 04 §8.0.2; already-green v3 candidate-family records/enumerator; frozen fixture and test writers.
- Current branch/worktree: `codex/source-analysis-process-materials` at main `12005e8eab122650db7783934903e8acc972f6e9` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the applicable instructions, recorded the pre-existing shared dirty worktree, and read the new public M1 persistence test without modifying it.
- Confirmed root's raw RED: `FactCandidateModuleArtifactTest#publishesExactCallUnionAndFreshTypedReopensTheSameV3Bytes` ran 1 test with 1 expected old-v2 policy failure and 0 errors after three persisted exact-call rows and their Evidence premises passed.

## Current state

- The bounded M1 publication replacement is complete. `FactCandidateSetModulePublisher` and `PersistedFactCandidateSetReader` accept only `proven-code-facts-fact-candidate-set-v3`/module `v3`; the publisher emits the exact nine-field JSON branch without boundary/guard fields, and the reader fresh-reopens it into the same v3 closed union. `AtomicCanonicalPublicationEngine` recognizes only the matching v3 M1 artifact policy. Its unrelated dirty Flow/material edits remain preserved.
- The new persistence selector and post-format direct aggregate are green. Raw Surefire XML totals are 5 tests, 0 failures, 0 errors, and 0 skipped across the artifact, reader, and enumerator classes. Exact three-file Spotless and scoped diff checks are clean.
- This task is complete, but the coordinated v3 cutover remains incomplete and unpublished: M2, M3, Step 05, and the separately owned exact-call denominator negative-policy work are outside this bounded M1 publication scope.

## Changed files

- `backend-agents/sources/source-code/progress/java-exact-call-publication-implementation.md` (this owned M1 publication progress record)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSetModulePublisher.java` (M1 v3 exact-call serialization)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateSetReader.java` (M1 v3 exact-call fresh reopen)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (sole M1 v3 artifact-policy registration)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` before work | PASS | Recorded and preserved the shared dirty worktree, including candidate-family GREEN changes and frozen M1 tests. |
| root-inspected M1 persistence RED | RED CONFIRMED | `FactCandidateModuleArtifactTest#publishesExactCallUnionAndFreshTypedReopensTheSameV3Bytes`: 1 test, 1 failure, 0 errors; old v2 publisher policy was absent from the v3-only fixture. |
| targeted public M1 test read | PASS | Requires exact nine-field JSON union, three exact denominators, full typed reopen equality, and identical canonical bytes. |
| `-Dtest=FactCandidateModuleArtifactTest#publishesExactCallUnionAndFreshTypedReopensTheSameV3Bytes test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped; compiled 314 main and 91 test sources. |
| `-Dtest=FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest,FactCandidateEnumeratorTest test` before format | PASS | 5 tests, 0 failures, 0 errors, 0 skipped. |
| exact three-file `spotless:apply` | PASS | 3 files selected: 0 changed, 3 already clean, 0 cache-skipped. |
| same exact three-file `spotless:check` | PASS | 3 files selected: 0 need changes, 0 already clean, 3 cache-skipped. |
| same three-class aggregate after format | PASS | Raw Surefire XML: Artifact 2, Reader 1, Enumerator 2; total 5 tests, 0 failures, 0 errors, 0 skipped. |
| scoped production `git diff --check` | PASS | No whitespace errors in the three authorized production files. |
| owned progress trailing-whitespace check | PASS | No trailing whitespace. |

## Decisions

- Use only v3 M1 schema/module/policy registration. Do not retain a v2 writer, reader, compatibility adapter, mixed fixture, or fallback.
- Serialize and parse `JAVA_EXACT_CALL` with exactly its common, exact-target, Evidence, and required-atom fields; boundary and guard JSON shapes stay unchanged.
- This bounded M1 publication task is complete while the larger v3 cutover remains explicitly incomplete and unpublished.

## Blockers

- None for the bounded M1 publication task. The remaining whole-cutover slices are separately owned and released.

## Exact next action

- Maven is released. Await the root's next frozen RED and bounded brief; do not implement M2, M3, Step 05, or exact-call denominator policy in this task.

## Resume checks

- Re-read this record, check Git status, preserve all other owners' changes, and run only the assigned targeted Maven selectors.
