# Progress: business material code outline

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh implementation, following approved Sol/ultra design
- Started: 2026-09-11 02:40 NDT
- Last updated: 2026-09-11 03:06 NDT
- Scope: Improve the existing `BusinessMaterialBuilder` so its automatic model packet expresses code-known inputs, guards, direct actions, terminal behavior, and limitations without adding a business dictionary or changing Steps 01–05.
- Approved inputs: Existing frozen fixture source, persisted Flow/Capsule publications, and the already approved business-first Step 06 design. No live provider call in this work unit.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed that the existing real Luna/high DepotHead sample used a hand-authored `ModelActivityPacket`; it validates the prompt and response shape, not automatic material quality.
- Confirmed that the current automatic fallback packet only reports route, handler, and generic direct-call context, which is insufficient for a useful business interpretation.
- Added a generic syntax projection over already selected source snippets: method inputs, `if` conditions, scoped direct calls, and `return`/`throw` terminal behavior. It neither adds a business dictionary nor expands source selection.
- Added Flow-preferred and direct-entry fallback public-seam assertions for the guarded Java fixture.
- Added bounded long-method selection: an already selected method keeps its short opening window and,
  only while source-ref capacity remains, contributes its first and last later receiver-qualified
  call as exact one-line source references. This preserves a later direct action without scanning a
  new file or extending the call graph.
- The opt-in fixed-repository recheck reaches the capture gate and stops before source reading with `LOCAL_GIT_PROMISOR_UNSUPPORTED`: the configured fixed checkout is a partial/promisor clone. No model or customer code ran.

## Current state

- The direct selector is green: three Flow-preferred Builder tests and four direct-entry fallback
  tests pass.
- Durable design/README state now distinguishes hand-curated live Luna samples from automatic Builder quality.
- A full real-repository automatic-packet quality comparison remains blocked by the existing incomplete frozen Git object set.

## Changed files

- `progress/business-material-code-outline.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderFallbackTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Repository/source status and targeted source inspection | PASS | Identified automatic-material quality gap and exact live-sample limitation. |
| `mvn -Dtest=BusinessMaterialBuilderTest#exposesACompactCodeOutlineForASelectedLocalActivityWithoutNamingTheBusiness test` | RED then PASS | RED showed only route and method names; GREEN exposes input, guard, call, and terminal observations. |
| `mvn -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest test` | PASS | 6 tests, 0 failures/errors/skips. |
| `mvn -Dtest=BusinessMaterialBuilderFallbackTest#keepsALateDirectCallFromALongSelectedServiceMethodInTheModelPacket test` | RED then PASS | RED omitted a direct call after the 24-line window; GREEN retains the exact later call and its neutral code observation. |
| `mvn -Dtest=BusinessMaterialBuilderTest,BusinessMaterialBuilderFallbackTest test` | PASS | 7 tests, 0 failures/errors/skips. |
| opt-in fixed-repository material planner | BLOCKED BEFORE SOURCE READ | `LOCAL_GIT_PROMISOR_UNSUPPORTED`; no Provider and no customer execution. |

## Decisions

- The quality gate is an inspectable automatic model packet before any additional broad Luna call.
- The packet retains concise source-derived outline facts; Luna remains responsible for business purpose, object naming, and process semantics.
- A prior full jshERP direct-entry material run predates this projection and is only a coverage-count result; it must be regenerated before semantic evaluation.
- Do not bypass the Capture refusal for a promisor/partial clone. Continue fixture-based work until a complete immutable checkout is available for the real quality gate.
- A long source method receives a short head window plus at most two later direct-call lines under
  the existing source-ref cap. This is a generic context-preservation rule, not a persistence or
  business-meaning heuristic.

## Blockers

- Full real-repository automatic-material quality validation remains blocked by the incomplete
  fixed Git object set. The implementation task itself has no blocker.

## Exact next action

- Before any wider Luna run, obtain or register a complete immutable checkout of the approved fixed
  commit, regenerate a small automatic material packet, inspect it, and only then decide whether to
  launch one live quality-check package.

## Resume checks

- Read this file, inspect the two Builder tests and generated short-ref behavior, verify the fixed
  source checkout is complete, then run the direct real-material planner with zero Provider calls.
