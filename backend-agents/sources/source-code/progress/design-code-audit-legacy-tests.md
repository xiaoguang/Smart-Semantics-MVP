# Progress: Step06/07/08 and legacy/runtime audit

- Status: COMPLETE (audit handoff)
- Agent role: audit legacy production code, resources, CLI/provider/runtime and directly affected tests
- Model: GPT-5
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: full read-through of Step06/07/08, runtime/CLI/provider residue, resource reflection, test fixtures and actual runtime entry points; no production/test edits
- Approved inputs: formal source-code checkout; baseline `main` `e8c40ea2f250da55d6b8797c32c380461061c3c9`; frozen repository source/design/tests
- Current branch/worktree: nested `linguan-prototype-v2` `main`; preserve unrelated untracked `progress/design-code-submission-audit.md`

## Completed

- Read repository, `linguan-prototype-v2`, `backend-agents`, and source-code scoped AGENTS instructions.
- Confirmed the formal source-code checkout and baseline commit.

## Current state

- The eight Step07 knowledge v1 prompt files were checked by exact filename across the
  committed source, tests, and docs. Six have no static reference; two are historical
  inputs to one valid fingerprint-invalidation test. The old process-group v1 pair is a
  separate live `generate` dependency and is excluded from this set.

## Confirmed audit findings

### F04 — Step07 knowledge v1 prompts

`BusinessProcessPromptCatalog.java:11-26` maps every current Step07 task kind to the
v2 resources only. The real Step07 runtime reaches that catalog from
`DefaultBusinessProcessDiscovery.java:506-523` (model calls) and `:552-577`
(fingerprints). `BusinessProcessPromptV2ContractTest.java:18-45` independently
enumerates and reads the ten v2 task-kind mappings, so these are the actual runtime
resources.

Exact filename search over `src/main`, `src/test`, and `docs` found:

| v1 resource | Static/dynamic consumer evidence | Disposition conclusion |
| --- | --- | --- |
| `business-catalog-draft-v1.txt` | `BusinessProcessSemanticFingerprintV2Test.java:142-157`; `promptResource` selects it and `readV1Prompt` loads it from the classpath | Not safe to delete in place. Move unchanged to `src/test/resources/org/sourceanalysis/app/analysis/knowledge/`; this preserves the test's historical v1 fingerprint fixture without shipping it as production resource. |
| `business-catalog-review-v1.txt` | Same test and lines as above (`:142-157`) | Same move-only disposition. |
| `business-catalog-merge-draft-v1.txt` | No reference found outside the resource itself | Safe deletion from `src/main/resources` as part of the six-file set below. |
| `business-catalog-merge-review-v1.txt` | No reference found outside the resource itself | Safe deletion as above. |
| `business-process-draft-v1.txt` | No reference found outside the resource itself | Safe deletion as above. |
| `business-process-review-v1.txt` | No reference found outside the resource itself | Safe deletion as above. |
| `business-process-consolidation-draft-v1.txt` | No reference found outside the resource itself | Safe deletion as above. |
| `business-process-consolidation-review-v1.txt` | No reference found outside the resource itself | Safe deletion as above. |

The complete six-file deletion candidate is exactly the last six rows above; no
Java/test file is part of that deletion. The direct test affected by the two moved
files is `BusinessProcessSemanticFingerprintV2Test` (the historical v1 pair must
remain available at its test classpath path). `BusinessProcessPromptV2ContractTest`
reads only v2 files (`:18-45`, `:84-95`) and is unaffected. `ProcessPromptCatalog.java:7-23`
loads `process-group-draft-v1.txt` and `process-group-review-v1.txt` for the old
ProcessExplainer route; those two files must remain and are not among the eight under
review. The same boundary is recorded in `docs/supplements/design-code-cleanup-audit.md:80-93`.

No production resource loader uses directory scanning, reflection, or a version
fallback for these names. The only production classpath prompt loaders in scope are
`BusinessProcessPromptCatalog.java:38-47` and the explicitly separate
`ProcessPromptCatalog.java:27-35`; test-only resource/reflection checks are visible
at `BusinessProcessSemanticFingerprintV2Test.java:150-160` and
`BusinessProcessPromptV2ContractTest.java:84-95`.

### F06 — Old process interpretation route is live, not deletable residue

The direct maintenance `generate` mode is wired at
`RepositoryRunMain.java:113-118,129-153,372-429`: it constructs
`PersistedBusinessRunExecutor` at `:403-404`, whose `:131-136` composition creates
`BusinessAnalysisWorkflow`; its `:150-155` factory creates `ProcessExplainer`.
`BusinessAnalysisWorkflow.java:63-82` then calls `processExplainer.explain`, producing
`RepositoryBusinessKnowledge` and passing it to `BusinessReportPublisher`.
The public coordinator's production constructor also wires
`PersistedBusinessRunExecutor::execute` (`RepositoryAnalysisRunCoordinator.java:37-47`)
and runs that flow in `:140-160`. Therefore `ProcessExplainer`,
`RepositoryBusinessKnowledge`, `ProcessKnowledgeCheckpointPublisher`,
`ProcessExplanationProfile`, `ProcessPromptCatalog`, and the old report request/data
types cannot be deleted as a cleanup-only change.

The old checkpoint publisher is a real production call at
`ProcessExplainer.java:161-184`; it installs REPOSITORY_KNOWLEDGE module 1 under the
legacy key `process-explainer` (`ProcessKnowledgeCheckpointPublisher.java:91-107`).
`ProcessKnowledgeCheckpointReader.java:116-150` has no production caller (only the
reflection-based `ProcessKnowledgeCheckpointTest.java:106-129`), so it is not on the
current runtime path, but it remains coupled to the old checkpoint contract and direct
test. Its eventual deletion requires an explicit old-route retirement, not this audit.

The complete direct-test impact of retiring that route is broader than one test:
`ProcessExplainerTest`, `ProcessGroupingTest`, `ProcessMaterialRecallTest`,
`ProcessPromptContractTest`, `ParallelProcessExplainerTest`, `RepositorySummaryTest`,
`ProcessKnowledgeCheckpointTest`, `Task5PartialPropagationRedTest`, the old LiveLuna
process/report ITs, `BusinessReportCheckpointTest`, `BusinessReportPublisherTest`,
`FourEntryBusinessSemanticChainTest`, `PersistedBusinessRunExecutorTest`,
`RepositoryAnalysisRunCoordinatorTest`, `LocalRepositoryAnalysisAgentExecutionTest`,
and `ModelBatchReuseWorkflowTest` all statically construct or type-check this route.
No such tests are run under this frozen audit.

This is a design migration discussion, not a dead-code finding: Step07's new route is
`PersistedBusinessProcessRunExecutor.java:16-17,49-68`, while Step08 documentation
still records the old report input and pending catalog wiring (`docs/analysis-steps/08-nine-section-document.md:3,93-97`; `docs/DESIGN.md:210-233`).
The public extension seams and current JavaParser/JDT capabilities are not classified
as removable.

### F07 — Retired finite-key/R0 implementation already absent

`AnalysisStepModuleAddress.java:13-37` registers only active modules, with
FLOW_INTERPRETATION 10/11 and REPOSITORY_KNOWLEDGE 1 as
`business-process-publisher`. The only compatibility exception is the deliberate
legacy checkpoint reader key at `:53-60`. The retired implementation packages
(`analysis.interpretation.model`, `proposal`, `registry`, and `process`) are absent
from both main and test trees. `LegacySemanticRouteRetirementTest.java:22-66` asserts
old numeric module addresses/classes are absent while retaining the active 10/11
modules and `ModelRuntimeIdentityV1`. This is already-clean state; no files are
proposed for deletion, and JavaParser is retained per the scoped design instructions.

## Changed files

- `progress/design-code-audit-legacy-tests.md` (this audit handoff only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated worktree changes preserved; only this progress file is ours. |
| `git rev-parse --verify e8c40ea2f250da55d6b8797c32c380461061c3c9` | PASS | Baseline resolves in nested checkout. |
| Exact `rg -n -l -F <each v1 filename> .` search over source/tests/docs | PASS | Only `BusinessProcessSemanticFingerprintV2Test.java:144-145` reads two catalog v1 fixtures; the other six have no textual consumers. |
| `rg -n 'getResourceAsStream|Class\\.forName|readAllBytes' src/main/java src/test/java` plus prompt-catalog call-site review | PASS | No hidden production directory scan/version fallback; production loaders are the explicit v2 and old process-group catalogs. |

## Decisions

- Do not run Maven, customer code, model, capture, or live provider commands.
- Only this progress file may be created/maintained in the assigned source directory.

## Blockers

- None for the scoped resource conclusion. Retiring the old `generate` route remains an
  explicit design decision because it has live production callers and a broad direct-test
  surface; this audit does not authorize that migration.

## Exact next action

- Parent handoff: delete only the six no-consumer `src/main/resources` v1 prompts when
  implementing the agreed mechanical cleanup; move the two catalog v1 fingerprint fixtures
  unchanged to same-package `src/test/resources`; retain both `process-group-*-v1.txt`.

## Resume checks

- Re-read this file, run nested `git status --short`, and continue from the current-state section.
