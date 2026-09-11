# Progress: Business Flow scoped quality cleanup

- Status: COMPLETE
- Agent role: Terra/xhigh read-only scoped-quality implementation preparer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Behavior-preserving cleanup of the diagnosed Step 05 changed-record quality findings only: analyzer-visible immutable list copies and the WIP-new/induced mechanical PMD warnings.
- Approved inputs: `progress/business-flows-closeout-quality-diagnosis.md`, current SpotBugs/PMD XML, and the three changed records named in that diagnosis.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve all shared-worktree changes.

## Completed

- Captured the dirty shared-worktree baseline before this preparation checkpoint.
- Read the complete Sol/xhigh quality diagnosis. No Java, POM, filter/suppression, test, design, or Maven action is authorized during preparation.
- Reconciled the preserved reports against current sources. The SpotBugs XML still contains 55 historical `EI_EXPOSE_REP` instances for the three records; its three formerly real `FactCandidateSet` instances are excluded because the prior defensive-copy slice already normalized the two mutable nested argument lists. The remaining 52 analyzer-only locations still exist as source assignments: 8 in `FactCandidateSet`, 24 in `CapsuleProjection`, and 20 in `FlowCompilation`.
- Confirmed that all 52 assignments still receive an immutable helper result (`List.copyOf(...)` or Java `Stream.toList()`); the minimum future change is a direct `List.copyOf(helper(...))` at each compact-constructor assignment, without changing validation, ordering, nested values, or public fields.
- Confirmed all nine WIP-new/induced mechanical PMD positions remain in source: `EntryRootedFlowCompiler` has two unused `label` parameters and two redundant `java.util.Map` qualifiers; `PersistedFlowCompilationInputReader` has one nested CALL/CALL_TARGET `if` and four redundant fully qualified `ArtifactId` references (one WIP-new exact-call line and three WIP-induced old lines).
- Applied the authorized behavior-preserving source edits: 52 direct analyzer-visible `List.copyOf(helper(...))` assignments across the three records, plus all nine PMD mechanical cleanups in the two compiler files. The combined six-file production set also includes the separately bounded M4 Registry-to-BusinessFlows lineage validation in `FiniteKeyFlowTaskCompiler`.
- Root verified quality session `68123`: compilation, JAR, and Enforcer passed; SpotBugs reported 114 warnings and 0 analysis errors. All 52 scoped wrapper findings are gone; only the pre-existing `FactCandidateSet.candidates()` helper-return false positive remains among the 113 baseline findings.
- Root verified PMD quality session `99147` under JDK 17 and the quality profile: 33 warnings remain, with the original nine WIP mechanics and the lineage unused finding gone. The remaining authorized cleanup is limited to one explicit top-level candidate copy, one redundant provenance parenthesis pair, four redundant imported type qualifiers, and one private Fact-reader helper proven unused by `rg`.
- Applied that final authorized cleanup without changing validation or data behavior: `FactCandidateSet` now makes its top-level ordered candidate result explicitly immutable; `CapsuleProjection` preserves its nullable ledger relation while removing only redundant parentheses; `PersistedFactCandidateInputReader` uses imported `ArtifactId` and `Sha256Digest` at its two reference constructors and removes the private `controls(JsonNode, ArtifactControls)` helper after `rg` found no invocation.
- Root verified session `16470`: all 44 selected direct selectors (90 unit tests) and the config integration test passed, with numeric exit 0. Root also verified session `50822`: the 412-Java-file Spotless check and package passed.
- Root verified fresh report-generation session `20574` exited 0 and reported SpotBugs 113 (the three changed records have 0 warnings) and PMD 27. Report generation is diagnostic evidence, not a quality-gate PASS; no quality-gate PASS is claimed by this record.
- The new transitive cleanup tail was isolated to `PersistedFactCandidateInputReader`: after removal of `controls(JsonNode, ArtifactControls)`, `rg` found `policyRegistryReference(JsonNode)` only at its private declaration and its type only at that declaration/import. Removed only that unreachable helper and now-unused import.
- Root verified final session `82282`: exact one-file Spotless apply/check passed; seven direct Fact tests passed (FactCandidateMissingPath 1, FactCandidateEnumerator 4, BoundedFactInputHandoff 1, DiscoveryToFactHandoff 1), all with 0 failures, 0 errors, and 0 skipped; the JDK 17 JAR build exited 0.
- Root verified PMD check session `3296` failed with numeric exit 1 and 26 warnings. A fresh XML count remains 26 and contains no `PersistedFactCandidateInputReader` finding. These are unmodified out-of-bounded-cleanup warnings; this result is not a global quality-gate PASS.

## Current state

- Bounded scoped-quality work is COMPLETE: the 44-selector aggregate, config integration test, 412-file Spotless check, package, final one-file Spotless, targeted Fact regressions, and JDK 17 JAR build are root-verified PASS. No behavior, helper validation/order/exception behavior, nested data, ID, schema, filter, suppression, POM, or baseline-quality scope was changed.
- Global quality is NOT PASS: PMD session `3296` failed with 26 unmodified warnings, and the 113 SpotBugs checkpoint remains out of scope. This bounded completion does not accept full Step 05 or Step 06.
- The independent registry-lineage P1 and the carrier selector’s two test-premise failures are expressly out of scope. The carrier record remains a partial/paused result; its aggregate 23-test run is not a pass.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java` (8 direct immutable copies)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java` (24 direct immutable copies)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilation.java` (20 direct immutable copies)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/EntryRootedFlowCompiler.java` (2 unused private labels removed; 2 redundant `Map` qualifiers removed)
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java` (nested `if` collapsed; 4 redundant `ArtifactId` qualifiers removed)
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java` (combined six-file M4 lineage validation, separately detailed in its owned record)
- `progress/business-flow-scoped-quality-cleanup.md` (owned quality evidence)
- `progress/registry-flow-lineage-closeout-implementation.md` (owned combined lineage evidence)
- Current final-pass scope: `FactCandidateSet.java`, `CapsuleProjection.java`, `PersistedFactCandidateInputReader.java`, and this record only.
- Current transitive tail scope: `PersistedFactCandidateInputReader.java` and this record only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared dirty-worktree baseline captured before this owned record was created. |
| Full Sol quality diagnosis read | PASS | Diagnosis reports 52 false-positive EI members across the three records and nine WIP-new/induced mechanical PMD warnings for bounded follow-up verification. |
| Preserved `target/spotbugsXml.xml` member extraction | PASS | 55 historical instances remain in the report: 3 prior defensive-copy instances plus the current 52-source-location cleanup scope (FactCandidateSet 8, CapsuleProjection 24, FlowCompilation 20). |
| Preserved `target/pmd.xml` and current-source cross-check | PASS | All nine WIP-new/induced mechanical locations remain: EntryRootedFlowCompiler 4 and PersistedFlowCompilationInputReader 5. The preserved XML timestamps are 03:08/03:11 and were not regenerated. |
| Exact six-file Spotless apply | PASS; numeric exit 0 | Exactly six production files selected; only `FiniteKeyFlowTaskCompiler` and `FlowCompilation` were changed to clean, with four already clean. |
| Exact six-file Spotless check | PASS; numeric exit 0 | All six selected production files clean; 0 needed changes and 6 cache-skipped. |
| Root quality session `68123` | REPORT ONLY, not a quality-gate PASS | Compilation, JAR, and Enforcer passed; SpotBugs produced 114 warnings and 0 analysis errors. The scoped 52 wrapper reports are eliminated; `FactCandidateSet.candidates()` remains the one old helper-return false positive. |
| Root PMD session `99147` | REPORT ONLY, not a quality-gate PASS | JDK 17 quality-profile report contains 33 warnings; the nine WIP mechanical warnings and lineage unused warning are eliminated. |
| Pre-removal `rg -n 'controls\\(' PersistedFactCandidateInputReader.java` | PASS | Found the private `controls(JsonNode, ArtifactControls)` declaration but no invocation; receiver-method matches are unrelated. |
| Root session `16470` | PASS; numeric exit 0 | Fixed 44-selector aggregate passed: 90 unit tests plus 1 config integration test. |
| Root session `50822` | PASS | 412-Java-file Spotless check and package passed. |
| Root session `20574` | REPORT ONLY, not a quality-gate PASS; numeric exit 0 | Fresh reports: SpotBugs 113, with the three changed records at 0 warnings; PMD 27. |
| `rg -n 'policyRegistryReference\\(|ArtifactPolicyRegistryReference' PersistedFactCandidateInputReader.java` | PASS | Before removal, found the private helper and type only at its declaration/import; no call site remained. |
| Root session `82282` one-file Spotless apply/check | PASS | Exact `PersistedFactCandidateInputReader.java` formatting passed. |
| Root session `82282` targeted Fact regressions | PASS; 7 tests, 0 failures, 0 errors, 0 skipped | FactCandidateMissingPath 1, FactCandidateEnumerator 4, BoundedFactInputHandoff 1, and DiscoveryToFactHandoff 1 passed. |
| Root session `82282` JDK 17 JAR build | PASS; numeric exit 0 | JAR built after the final helper-tail cleanup. |
| Root session `3296` PMD check | FAIL; numeric exit 1 | 26 warnings remain; fresh XML contains no `PersistedFactCandidateInputReader` finding. This is a global quality-gate failure outside the bounded cleanup scope. |

## Decisions

- Direct `List.copyOf(helper(...))` is used only where the analyzer needs to see already-existing immutability; it adds no filter, suppression, POM change, or behavioral rewrite.
- PMD cleanup remains limited to the diagnosed unused private helper, redundant qualifiers, redundant parentheses, private unused parameters, and nested-if collapse. Do not touch the 113 unchanged SpotBugs or 24 unchanged-file PMD findings.
- The previously fixed real `FactCandidate` defensive-copy defect is not part of this cleanup preparation.
- Proposed future direct selector, after a separately authorized edit and file-scoped formatting: `FactCandidateDefensiveCopyTest,FactCandidateEnumeratorTest,EvidenceCapsuleProjectorTest,CapsuleProjectionModulePublisherTest,FlowCompilationTest,EntryRootedFlowCompilerTest,FlowCompilationModulePublisherTest,DiscoveryToFactHandoffTest`. It directly covers the five planned production files; no whole-suite expansion is needed.

## Blockers

- None for the bounded cleanup. The global PMD and SpotBugs baselines remain explicitly out of scope.

## Exact next action

- No further action in this bounded cleanup; preserve the global quality and Step 05 acceptance boundaries.

## Resume checks

- Full Step 05 and Step 06 remain unaccepted. The bounded quality cleanup is complete, but global quality is not PASS.
