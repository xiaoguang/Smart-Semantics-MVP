# Progress: exact-call registry regression migration

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Correct the three public registry-determinism tests' custom registry schema literal to the published v3 Fact registry; preserve their boundary-only custom-template counts, template ordering, and required-atom ordering assertions.
- Approved inputs: current Step04 v3 Fact registry contract and `FactCandidateRegistryDeterminismTest`; no production, fixture, schema, design, Provider/customer/network, or unrelated test changes.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress note before the bounded Java correction.
- Root's 24-Fact aggregate established 35 total tests with 32 passed and exactly 3 errors from this class's stale custom registry literal; no pre-change selector rerun is needed.
- Replaced the stale custom-key templates with canonical boundary and exact templates from the published standard v3 registry, preserving all three test intents.

## Current state

- The template-order test now compares canonical boundary+exact registries and requires the real standard-fixture total of six candidates (two boundary, four exact), with no not-applicable rows.
- The deletion test is named for its actual semantics: removing the canonical exact template leaves the two complete boundary rows unchanged, with no not-applicable rows.
- The required-atom declaration-order test retains the canonical boundary key and only reverses its declared atom list.
- Root's post-correction verification ran the exact registry class successfully: 3 tests, 0 failures, 0 errors, 0 skips.
- Root's direct 24-Fact aggregate then completed 35 tests, 0 failures, 0 errors, 0 skips; all 24 Fact classes were independently read from raw output.

## Changed files

- `progress/java-exact-call-registry-regression-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateRegistryDeterminismTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Root 24-Fact aggregate (historical pre-change evidence) | RED (stale registry literal) | 35 total tests, 32 passed, 3 errors; all three errors were the schema-invalid custom registry construction. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateRegistryDeterminismTest#equivalentTemplateCollectionOrderHasTheSameCandidateIdentityAndDenominator+deletingOneTemplateRemovesOnlyItsEntryBoundaryCombinations+requiredAtomsKeepRegistryDeclarationOrderAsCandidateSemantics test` | RED (second contract issue) | 3 tests, 0 failures, 2 errors, 0 skips; two custom-key tests error with `FACT_PROFILE_INVALID: boundary candidate key is invalid` at `FactCandidateSet.java:313`, while the required-atom-order method passes. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateRegistryDeterminismTest test` (root post-correction verification) | PASS | 3 tests, 0 failures, 0 errors, 0 skips. |
| Root direct 24-Fact aggregate (post-correction verification) | PASS | 35 tests, 0 failures, 0 errors, 0 skips; all 24 Fact classes were independently read from raw output. |
| Pinned one-file Spotless apply | PASS | Selected exactly 1 owned Java file; 1 changed to clean. |
| Pinned one-file Spotless check | PASS | Selected exactly 1 owned Java file; 0 needed changes and the build succeeded. |
| `git diff --check -- progress/java-exact-call-registry-regression-tests.md src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateRegistryDeterminismTest.java` | PASS | No whitespace errors. |

## Decisions

- Use only canonical `JAVA_BOUNDARY_INVOCATION` and `JAVA_EXACT_CALL` templates selected from `FactRegistry.standardJavaFacts()`; the v3 registry rejects synthetic boundary aliases.
- Preserve template-order identity/denominator equality, require the standard six-candidate shape (two boundary and four exact), and retain the required-atom declaration/reversal assertions.
- Deleting the exact template is asserted to leave the complete boundary rows unchanged and to leave both complete/reduced not-applicable lists empty.

## Blockers

- No blocker remains for this registry slice; the canonical-template correction is verified green.

## Exact next action

- Keep this registry slice closed; no further Java or aggregate work is authorized here.

## Resume checks

- Keep the one-file scope and canonical v3 registry semantics. Do not modify `FactRegistry` production, `PersistedFlowCompilationInputReader.java`, or any Step05 Java in this slice.
