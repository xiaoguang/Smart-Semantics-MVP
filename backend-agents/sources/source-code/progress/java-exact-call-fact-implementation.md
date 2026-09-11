# Progress: java-exact-call-fact-implementation

- Status: IN_PROGRESS
- Agent role: Terra/xhigh exact-call candidate-family implementation owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Implement only the approved Step 04 `JAVA_EXACT_CALL` M1 candidate family in `FactRegistry`, `FactCandidateSet`, and `FactCandidateEnumerator`. This slice excludes M2, M1 publication/fresh-reader persistence, M3, Step 05, tests, fixtures, design-contract changes, Git, Provider, network, and customer builds.
- Approved inputs: Root's verified corrected RED; published Step 04 §8.0.2; current M1 candidate seams; frozen test/fixture writers.
- Current branch/worktree: `codex/source-analysis-process-materials` at main `12005e8eab122650db7783934903e8acc972f6e9` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the applicable repository, backend, and source-scoped instructions and recorded the existing shared dirty worktree before this owned record was created.
- Confirmed the first GREEN is limited to the candidate family and waited for the root's RED/release.
- Read Step 04 §8.0.2 in full and the current M1 registry, candidate union, enumerator, fresh predecessor reader, M1 publisher, and M1 fresh-reopen reader.

## Current state

- The candidate-family GREEN is complete in only `FactRegistry`, `FactCandidateSet`, and `FactCandidateEnumerator`: the sole M1 registry is v3; the closed union adds `JAVA_EXACT_CALL` with `callSiteNodeId`, `callTargetEdgeId`, `targetMethodNodeId`, and `targetCanonicalMethod`; exact subjects sort the two node IDs; and exact denominators are `entryId|callTargetEdgeId|JAVA_EXACT_CALL`. Boundary and guard v3 variants preserve their existing fields and behavior.
- Exact enumeration is persisted-only: it expands CALL_SITE owners within the frozen entry denominator, requires one exact static-field-receiver CALL_TARGET edge, resolves a CODE_STRUCTURE METHOD canonical target, and writes the four required atoms plus call-site/edge/METHOD Evidence bindings. A present non-METHOD target is `NOT_APPLICABLE/JAVA_EXACT_CALL_TARGET_NOT_METHOD`; it does not reparse source or use DataFlow boundary fields.
- The exact selector and the full two-test enumerator class are green after the three-file scoped format; raw post-format Surefire totals are 2/0/0/0. This is not full v3 completion: M1 publication/fresh-reader persistence, M2, M3, and Step 05 remain separately unreleased and unpublished.
- Fresh input can still admit malformed nonblank METHOD canonical values or absent Evidence closures, for which this happy-path enumerator silently omits a combination. The next negative publication/Proof slice must account for or fail closed on these cases; no denominator policy or new error code is added here.

## Changed files

- `backend-agents/sources/source-code/progress/java-exact-call-fact-implementation.md` (this owned candidate-family progress record)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactRegistry.java` (v3 exact-call template registration)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java` (v3 exact-call closed-union record and identity)
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java` (persisted exact-call owner/edge/METHOD candidate enumeration)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` before work | PASS | Recorded the pre-existing shared Flow/material source, test, design, and progress changes; they remain preserved. |
| `sed -n '257,290p' docs/analysis-steps/04-proven-code-facts.md` | PASS | Read approved §8.0.2 contract: exact CALL_TARGET/METHOD join, four atoms, typed non-METHOD disposition, v3 replacement rule, and no source/effect inference. |
| targeted current M1 class reads | PASS | Confirmed the predecessor reader already validates global edge endpoints; candidate record/publisher/fresh reader currently admit only boundary and guard v2 shapes. |
| root-inspected corrected public RED | RED CONFIRMED | `FactCandidateEnumeratorTest#enumeratesExactCallForEachPersistedCallTargetAndOwningEntry`: 1 test, 1 failure, 0 errors; expected 3 owner-expanded candidates and actual 0. |
| `-Dtest=FactCandidateEnumeratorTest#enumeratesExactCallForEachPersistedCallTargetAndOwningEntry test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped; compiled 314 main and 91 test sources. |
| `-Dtest=FactCandidateEnumeratorTest test` before format | PASS | 2 tests, 0 failures, 0 errors, 0 skipped. |
| exact three-file `spotless:apply` | PASS | 3 files selected: 1 changed (`FactCandidateSet`), 2 already clean, 0 cache-skipped. |
| same exact three-file `spotless:check` | PASS | 3 files selected: 0 need changes, 0 already clean, 3 cache-skipped. |
| `-Dtest=FactCandidateEnumeratorTest test` after format | PASS | Raw Surefire XML/TXT: 2 tests, 0 failures, 0 errors, 0 skipped. |
| scoped production `git diff --check` | PASS | No whitespace errors in the three authorized production files. |
| owned progress trailing-whitespace check | PASS | No trailing whitespace. |

## Decisions

- Do not add a dual reader, adapter, fallback, schema bridge, or source reparse. The current coordinated v3 cutover will be mapped as a single current wire.
- Preserve the existing boundary and guard record variants and their behavior; the potential GREEN is only the `JAVA_EXACT_CALL` candidate family after a frozen intended RED and root authorization.
- Retain the existing `PROOF_PACK_REFERENCE_BROKEN` predecessor-reader behavior for an absent edge endpoint. Only a present exact target that is not a CODE_STRUCTURE `METHOD` becomes `NOT_APPLICABLE/JAVA_EXACT_CALL_TARGET_NOT_METHOD`.
- Do not treat this happy-path candidate GREEN as full exact-call denominator closure: malformed METHOD canonical values and absent evidence closures can reach fresh M1 input and currently omit their combinations. The later released negative publication/Proof slice owns the fail-closed/accounting resolution.

## Blockers

- None for this bounded candidate family. The required next work is separately owned/released persisted M1 and M2/M3 negative coverage, including exact-call denominator closure for malformed canonical values or absent Evidence closure.

## Exact next action

- Maven is released. Await the root's next frozen RED and bounded brief; do not implement M1 publication/fresh-reader persistence, M2, M3, Step 05, or a denominator-closure policy in this slice.

## Resume checks

- Re-read this record, check Git status, preserve all shared changes, and wait for the root's confirmed RED/release before any production edit or Maven command.
