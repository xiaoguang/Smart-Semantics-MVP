# Progress: Verified source inventory execution

- Status: COMPLETE
- Agent role: Root implementation coordinator
- Model: Design and scope review: Sol/ultra; production: Terra/xhigh; tests: Luna/xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Extract the already-proven Step 01 M1→M3 composition from acceptance-only code into one production module. It must consume a registered frozen capture and persisted request bytes; it must not accept source paths, run Maven, call a Provider, or add a parallel inventory algorithm.
- Approved inputs: Active Step 01 design, existing M1/M2/M3 modules and local-Git capture registry.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the exact M1→M3 composition is presently repeated in the fixed-repository acceptance test rather than offered by a production execution module.
- Added `VerifiedSourceInventoryExecutionRequest` and `VerifiedSourceInventoryExecutor`.
- The executor performs the established M1 admission, M2 byte verification and M3 publication in
  the existing order. Its caller provides only a registered frozen capture, exact canonical request
  and frozen-request bytes, profiles and canonical stores; it accepts no source path.
- A real local Git fixture now invokes the production executor and fresh-reopens the exact three
  Step 01 semantic outputs.

## Current state

- Step 01 has a production execution seam. The global Agent still needs a configuration/registered
  input resolver before it can create this request from a queued run.

## Changed files

- `progress/verified-source-inventory-execution.md`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryExecutionRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryExecutor.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublicationSpecifierTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=VerifiedSourceInventoryPublicationSpecifierTest#executesTheExistingM1ThroughM3InventoryModulesFromOneRegisteredCapture test` | RED | The new test first failed because `VerifiedSourceInventoryExecutionRequest` did not exist. |
| Same selector | PASS | 1 test; real local Git capture produced and reopened exactly `source-input.json`, `source-inventory.jsonl`, and `verified-snapshot.json`. |
| Scoped `spotless:check` | PASS | Executor, request and direct test meet the configured formatter. |

## Decisions

- This is orchestration only. M1, M2, M3 algorithms and their persisted wire remain owned by existing modules.
- The execution request omits caller-supplied upstream ordering. The executor derives the eight
  M1 upstream references from the admitted request, so callers cannot accidentally construct a
  plausible but incomplete M1 publication.

## Blockers

- None.

## Exact next action

- Connect a queued run to a registered-input resolver, then invoke this executor. Do not copy the
  fixed-repository acceptance test into runtime code.

## Resume checks

- Read this file, inspect the executor/request, and run only the direct executor selector before
  changing the Step 01 composition.
