# Progress: business flows closeout quality diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh root-cause debugger; no design authority
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-09 03:19 NDT
- Last updated: 2026-09-09 03:35 NDT
- Scope: read-only diagnosis of the current Step 05 SpotBugs and PMD quality-gate reports; classify all changed-file findings against the fixed base and identify the smallest class/member-specific remedy without implementing it.
- Approved inputs: fixed base `dea5c1bd96987270ecdc0f8060b612599b8f51d9`; source WIP diff SHA `0b12446cdc233c652945ea559566bbdf3f8d47114b0987cab2d423f5ff73f172`; local `target/spotbugsXml.xml`; local `target/pmd.xml`; current tracked/untracked worktree.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read applicable repository, backend-Agent, and source-code-Agent instructions.
- Read the requested systematic-debugging workflow and captured the pre-existing dirty worktree before analysis.
- Read the complete approved standards/toolchain plan and the relevant Step 05/06 contracts.
- Parsed the preserved SpotBugs and PMD XML rather than inferring from aggregate output.
- Traced every one of the 55 changed-record `EI_EXPOSE_REP` reports through compact constructors, collection helpers, generated accessors, and nested value types.
- Compared all 17 changed-file PMD locations to fixed base `dea5c1bd96987270ecdc0f8060b612599b8f51d9` and inspected the three non-EI SpotBugs locations without expanding into the other 113 reports.

## Current state

- The preserved reports contain exactly 168 SpotBugs findings (`154 EI_EXPOSE_REP`, `11 EI_EXPOSE_REP2`, and three non-EI) and 41 PMD findings. This diagnosis owns only the 55 `EI_EXPOSE_REP` findings in the three changed records, the 17 changed-file PMD findings, and effect/scope classification of the three non-EI findings.
- The 55 EI findings resolve to **three report instances representing one confirmed pre-existing shallow-copy defect** and **52 false positives**. Of the 55 reported members, 46 existed at the base and nine are WIP additions; none of the nine new members is mutable.

### Changed-record EI matrix

| Record/member group (all reported members accounted) | Count | Finding | Base/WIP | Evidence and smallest remedy |
| --- | ---: | --- | --- | --- |
| `FactCandidateSet.candidates`; `FactCandidate.orderedArgumentEdgeIds`, `FactCandidate.orderedArguments` | 3 | **CONFIRMED DEFECT** (one root cause) | Members and guard path existed at base; WIP exact-call variant adds a second affected path | The canonical `FactCandidate` constructor copies these two lists only inside the `JAVA_BOUNDARY_INVOCATION` branch. A caller (including a record deserializer) can supply mutable empty lists for `JAVA_GUARD_CONDITION` or `JAVA_EXACT_CALL`; they pass validation and are stored, and the top-level unmodifiable candidate list then exposes the mutable nested record. Unconditionally normalize both lists before kind dispatch (`List.copyOf`, retaining ID validation) and remove the branch-local duplicate; directly `List.copyOf(orderedCandidates(...))` at the top assignment gives SpotBugs the same visible proof. No API/schema change. |
| `FactCandidateSet.sourceGraphRoots`, `notApplicableDispositions`; `BoundaryArgumentBinding.javaLocalOriginNodeIds`; `CandidateDenominator.applicableKeys`, `notApplicableKeys`; `FactCandidate.branchEdgeIds`; both `SubjectEvidenceBinding` ID lists | 8 | **FALSE POSITIVE** | Existing | Each component is unconditionally replaced by `List.copyOf(...)` or a helper returning it; elements are immutable `String`, `ArtifactReference` (`ArtifactId` + `Sha256Digest`), or records whose own lists are copied. Tool-only remedy: make the copy visible at each listed compact-constructor assignment with direct `List.copyOf(helper(...))`; do not globally suppress EI. |
| `CapsuleProjection` top three lists; `EvidenceCapsule` nine reported lists; `FlowEntryView.routeEvidenceNodeIds`; both `FlowFactView` lists; both `FlowGapView` lists; three `FlowOutcomePathView` ID lists; three `ModelEvidenceSpan` ID lists; `ProjectionObligation.satisfyingSpanIds` | 24 | **FALSE POSITIVE** | 22 existing; new: `EvidenceCapsule.processJoinSignals`, `ModelEvidenceSpan.supportedProcessJoinSignalIds` | Every list is unconditionally produced by `Stream.toList()` (specified unmodifiable) or `List.copyOf`. Nested values close transitively: `FlowAtomView`/`BranchDecisionView`/`BudgetUsage` are scalar records; `ProcessJoinSignalV1` copies all lists; `SourceExcerptV1` contains immutable `SourceLocatorV1`, `Sha256Digest`, and defensive-copy `ImmutableBytes`. Same direct `List.copyOf(helper(...))` member-level tool remedy. |
| `FlowCompilation` top three lists; `EntryDisposition.gapIds`; both `FlowGap` lists; five reported `FlowSlice` lists; three `OutcomePath` ID lists; all six reported `ProcessJoinSignalV1` lists | 20 | **FALSE POSITIVE** | 13 existing; new: `FlowSlice.processJoinSignals` and the six signal lists | All paths unconditionally replace the inputs with helper-produced unmodifiable lists; nested `EntryDisposition`, `FlowGap`, `OutcomePath`/`BranchDecision`, `ProcessJoinSignalV1`, and `SourceLocatorV1` are transitively immutable. Same direct `List.copyOf(helper(...))` member-level tool remedy. |
| **Total** | **55** | **3 confirmed report instances / 52 false positives / 0 unresolved** | **46 existing / 9 new** | The analyzer pattern is corroborated inside these classes: direct `List.copyOf` fields such as `FactCandidate.evidenceBySubject`, `FlowSlice.sharedSteps`, and both outcome `decisions` lists are not reported, while semantically equivalent helper-return assignments are reported. |

The 52 false positives may alternatively use a SpotBugs exclude filter only if every exact class+accessor is enumerated. A package-wide, root-record-wide, or global `EI_EXPOSE_REP` exclusion would hide the confirmed `FactCandidate` defect and future regressions, so it is not a valid remedy.

### Changed-file PMD matrix

| Classification | Count | Exact findings | Smallest safe scope |
| --- | ---: | --- | --- |
| WIP-new source lines, mechanical | 4 | `EntryRootedFlowCompiler.orderedDistinct(label)`, `orderedUnion(label)`; `PersistedFlowCompilationInputReader` nested CALL/CALL_TARGET `if`; new exact-call `ArtifactId` qualifier | Remove the two private unused parameters and update callers; collapse the nested condition; remove the redundant qualifier. No public contract or behavior change. |
| Existing source lines newly made unnecessary by WIP imports | 5 | Two `java.util.Map` qualifiers in `EntryRootedFlowCompiler` after WIP added `Map`; three old `ArtifactId` qualifiers in `PersistedFlowCompilationInputReader` after WIP added that import | Remove qualifiers only. The lines existed at base, but the warnings are WIP-induced by the new imports. |
| Pre-existing, mechanical | 7 | `AtomicProofBuilder.attemptAtom(candidateKey)`; `PersistedProofDecisionSetReader` parentheses; `CapsuleProjection.required(label)`; `EvidenceCapsuleProjector.selectSourceSpans(profile)`; `FlowCompilation.required(label)`; `FlowCompilationModulePublisher` `Map` qualifier and `coverage(reopened)` | Remove private unused parameters and callers, remove parentheses/qualifier. `EvidenceCapsuleProjector` already applies every profile budget at lines 245–250; M1 publisher already reopens, verifies, and rebuilds the compilation before rendering coverage, so those two unused parameters carry no missing behavior. |
| Pre-existing correctness defect exposed by PMD | 1 | `FiniteKeyFlowTaskCompiler.reopenRegistry(..., businessFlowsPublication)` | Do not delete the parameter. The method currently accepts any M3 registry with the right module number/key/schema, but never checks its run, controls, or five BusinessFlows upstream descriptors before combining its items with capsules from the separately supplied current BusinessFlows publication. Use the existing private parameter (prefer the already reopened publication) to verify exact run/control/upstream lineage. Private implementation only; no public contract change. |
| **Total** | **17** | **4 new lines / 5 existing lines made newly redundant / 8 pre-existing findings** | No PMD suppression is needed. |

### Three non-EI SpotBugs findings (unchanged baseline scope)

| Finding | Classification/effect | Smallest remedy |
| --- | --- | --- |
| `DataFlowGraphWire.nullableLocator` `NP_NULL_PARAM_DEREF` | **Confirmed latent local defect, currently guarded.** Its sole caller first requires the exact `sourceLocator` field, so current valid/malformed replay reaches either JSON null or an object; nevertheless, the helper itself sends an absent field to `locator(null)`, producing an accidental NPE rather than the typed graph failure if reused without that guard. | Explicit `item == null -> throw broken()`, `item.isNull() -> null`, otherwise `locator(item)`. |
| `CodeStructureGraphBuilder.GraphAccumulator.edge(...)` UPM | **Confirmed dead private overload**, no runtime effect; PMD independently reports the same method. | Remove only the five-argument overload; the six-argument implementation remains. |
| `RepositoryInterpretationRegistryModulePublisher.verifyClosedInputs` `taskIds` UC | **Confirmed dead local work**, no semantic effect: list allocation/add/sort is never read; `taskFlowById` owns uniqueness and downstream lookup. | Remove declaration, `add`, and `sort`; keep the map checks. |

## Changed files

- `backend-agents/sources/source-code/progress/business-flows-closeout-quality-diagnosis.md` (this Agent's only owned file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing implementation/design/test/progress changes recorded; owned progress path was absent before creation. |
| `xmllint --xpath 'count(/BugCollection/BugInstance)' target/spotbugsXml.xml` | PASS | `168`; exact subtype counts `154/11/1/1/1`. |
| PMD XML count/extraction | PASS | `41` total; all 17 changed-file locations classified against the fixed base. |
| Fixed-base `git diff` and `git show` inspection | PASS | EI members `46 existing + 9 new`; PMD source-line split `4 new + 13 existing`, with five existing lines newly made redundant by WIP imports. |
| Constructor/helper/nested-type trace | PASS | One conditional-copy defect; every other listed collection and nested value is immutable by construction. |

## Decisions

- Treat tool findings as hypotheses until constructor, helper, accessor, nested-value, and fixed-base evidence confirms the actual exposure or identifies a narrowly justified false positive.
- Do not rerun Maven, edit production/tests/POM/config/design docs, access network/provider/customer sources, or solve the already-pending domain-classification and real-repository-input blockers.
- Do not treat all helper-return EI reports as equivalent: the conditional `FactCandidate` constructor is a real exposure, while the unconditional helper assignments are analyzer false positives.
- Keep the 113 other SpotBugs reports and 24 unchanged-file PMD reports as unmodified baseline scope; this diagnosis does not authorize a whole-repository cleanup.

## Blockers

- None. The two independent Step 05 acceptance blockers remain owned elsewhere and were not expanded here.

## Exact next action

- Return this diagnosis to the parent and release the task. Any implementation must be separately assigned to Luna/Terra and should prioritize the one real `FactCandidate` copy defect plus the WIP-caused PMD findings before deciding whether to encode exact member-level SpotBugs exclusions.

## Resume checks

- No continuation is planned. If resumed, first confirm the XML timestamps/content and source WIP have not changed, then re-run only this bounded classification before relying on the counts above.
