# Progress: runtime local capture command

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add the sole path-accepting CLI operation for local Git capture. It freezes a configured repository identity at a user-supplied full commit and returns only a source-registration ID. Analysis remains path-free.
- Approved inputs: Active business-first design; existing `LocalGitCommitCaptureAdapter`, `LocalSourceCapture`, and CLI/application seams.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed the current CLI has no capture operation although the registered-source analysis start is path-free.
- Confirmed `LocalGitCommitCaptureAdapter` already supplies the complete no-network, immutable-commit capture behavior; this unit only needs a thin configured template and CLI mapping.
- Added `LocalGitCaptureRequestTemplate`, which keeps repository identity, capture policy and resource budget at bootstrap while allowing only the absolute repository path and exact commit to vary.
- Added `capture-local-git` to the same CLI and a capture-aware `SourceAnalysisApplication` constructor; the command prints only `sourceRegistrationId`.
- Updated README, overall design and Step01 implementation state.

## Current state

- The capture CLI may accept a local repository path and complete commit only. It must not accept provider, workspace, policy or analysis controls from the command line.

## Changed files

- `src/main/java/org/sourceanalysis/app/capture/localgit/LocalGitCaptureRequestTemplate.java`
- `src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCli.java`
- `src/main/java/org/sourceanalysis/app/runtime/SourceAnalysisApplication.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisCliContractTest.java`
- `README.md`, `docs/DESIGN.md`, `docs/analysis-steps/01-verified-source-inventory.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Runtime/capture seam inspection | PASS | Existing local capture is isolated from the analysis core; CLI has no mapping yet. |
| `mvn -Dtest=SourceAnalysisCliContractTest#capturesOnlyTheConfiguredLocalGitIdentityAndReturnsItsRegistrationId test` | RED | Test compiled, then failed because `LocalGitCaptureRequestTemplate` and the capture CLI constructor/mapping were absent. |
| same selector | PASS | One test passed after template/mapping implementation; the capture request contains only configured identity/policy/budget plus the supplied absolute path and full commit. |

## Decisions

- A bootstrap-owned `LocalGitCaptureRequestTemplate` will hold declared repository identity and capture policy/resource-budget references. The CLI supplies only the local path and full commit.

## Blockers

- None.

## Exact next action

- Start the public runtime end-to-end scripted-run unit; do not broaden capture configuration or accept analysis paths in the CLI.

## Resume checks

- Re-read this progress file, `SourceAnalysisCli`, `SourceAnalysisApplication`, `LocalSourceCapture`, `LocalGitCaptureRequest`, and the direct CLI selector.
