# Task 8 direct-test progress — Luna

## Scope

Tests only in the formal source-code module. Cover Step05-owned Activity/M11 process assembly through saved stores without upstream work, and selected preview/full continuation against the full catalog. No Maven, production edits, live model calls, JDT, or commits.

## Current state

- Added a real configured Agent/store assertion to `SourceAnalysisReuseOnlyReplayTest`: after offline reuse, reopen the saved M11 through `assembleStep05ProcessDiscoveryRequest`, verify source-run/M11/Step05 ownership and frozen text, and assert the adopted process journal has no model result.
- Added a Step05-specific sample-to-formal-continuation test to `BusinessProcessAcceptanceSampleTest`: canonical Step05 packets with explicit COMPLETE M11 records, a non-contiguous selection (full-catalog ordinals 0 and 2), all final split fragments, no unselected/consolidation calls during preview, and cache reuse during formal continuation.
- Kept the existing M10 tests and their assertions unchanged. The scripted catalog fixture now derives member IDs from input cards so the same provider covers both source modes.
- `git diff --check` passes. No Maven/build was run; sources are frozen for the root's targeted Maven check.

## Acceptance-driver check

- Root requested a separate standalone-driver contract test. The source path is `tools/repository-run/acceptance/Step05ProcessSampleDriver.java`, with package `org.sourceanalysis.app.adapter.cli` and CLI shape `<absolute-config> <activity-batch> <catalog|selected> <reuse-batch|-> <absolute-output-directory> [label=candidate-id ...]`.
- The driver source exists and root reports the original malformed-argument test GREEN. The test class compiles the exact source into `@TempDir`; added all six permutations of three `label=candidate-id` pairs to detect order loss in parsed selection/manifest rows, plus an existing-output preflight check using a nonexistent configuration and no provider setup.
- No tool or production source was edited by this test task; no Maven/build was run here. `git diff --check` is the only local verification.
- Exact selectors: `Step05ProcessSampleDriverTest#rejectsMalformedCatalogAndSelectedArgumentsBeforeConfigurationOrProvider`, `Step05ProcessSampleDriverTest#selectedCandidateLabelsRetainTheirInputOrderForTheManifest`, `Step05ProcessSampleDriverTest#rejectsAnExistingOutputDirectoryBeforeLoadingConfigurationOrProvider`, and `Step05ProcessSampleDriverTest#rejectsAnOutputWhoseParentDoesNotExistBeforeLoadingConfigurationOrProvider`.

## Exact selectors

`SourceAnalysisReuseOnlyReplayTest#literalReuseOnlyReopensFailedBatchAndPublishesCurrentCompletionWithoutModelAuth`

`BusinessProcessAcceptanceSampleTest#step05SelectedSampleKeepsOriginalCatalogOrdinalsAndFormalRunReusesSelectedTriples`
