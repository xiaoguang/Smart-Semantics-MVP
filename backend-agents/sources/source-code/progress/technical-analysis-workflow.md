# Progress: technical analysis workflow

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add the internal, persisted Step 01-to-Step 05 coordinator that starts from a verified-source publication and creates discovery, graphs, Facts and Flows without caller-created intermediate objects.
- Approved inputs: Existing Step 02–05 production executors, typed profiles, canonical stores and the active runtime design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed each Step 02–05 executor exists and only consumes typed persisted predecessor references.
- Added `TechnicalAnalysisWorkflow`, which composes discovery, five graphs, Fact/Proof and
  Flow/Capsule from exactly one persisted `VerifiedSourceInventoryReference`.
- Added a direct local-Git Spring MVC/MyBatis test that freezes a real small repository, persists
  and fresh-reopens the analysis request, runs Step 01, then invokes the coordinator through the
  public typed seam and fresh-reopens the five Step 05 files.

## Current state

- The coordinator is complete. It accepts no caller path, raw source, graph, Fact, Flow or Capsule;
  it delegates all source work to the persisted predecessor/existing executors.

## Changed files

- `src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflow.java`
- `src/main/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowResult.java`
- `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `progress/technical-analysis-workflow.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=TechnicalAnalysisWorkflowTest test` | PASS | One local-Git Spring MVC/MyBatis repository ran from persisted request through Step 05; the five Flow/Capsule files fresh-reopened. |

## Decisions

- This coordinator intentionally begins after Step 01. A separate runtime input resolver will turn a queued request and registered capture into the Step 01 execution request; it must not duplicate M1–M3 inventory logic here.
- The direct coordinator test uses the current Step 02–05 policy fixture plus the five Step 01
  contracts. The fixed-repository acceptance oracle has an older data-flow public artifact prefix,
  so it is not used as the runtime test policy source; that isolated acceptance-config drift remains
  for the final full-repository acceptance work.

## Blockers

- None.

## Exact next action

- Implement the runtime input resolver so a queued persisted request plus registered capture/config
  can invoke Step 01, then pass the result into this coordinator without test-side assembly.

## Resume checks

- Verify the resulting Step 05 Flow publication can be handed unchanged to the business-material
  stage once the runtime resolver is present.
