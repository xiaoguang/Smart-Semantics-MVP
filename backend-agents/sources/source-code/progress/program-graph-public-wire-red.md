# Progress: program graph public-wire boundary projection

- Status: COMPLETE
- Agent role: Luna/xhigh TDD
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Bound the M6 public-wire RED to the existing real-store fixture and
  `JAVA_BOUNDARY_INVOCATION` projection. No production, design, or POM changes.
- Approved inputs: Existing public graph test fixture and the persisted M1–M5
  canonical module/analysis-step seams; no live provider, network, or customer Maven.
- Current branch/worktree: `codex/source-analysis-program-graph-public-wire` /
  `/private/tmp/linguan-source-analysis-program-graph-public-wire/backend-agents/sources/source-code`

## Completed

- Read scoped instructions, the target design/plans, M4/M6 graph contracts, and the
  existing public-wire/publication tests.

## Current state

- The initially explored standalone dual-variant test was withdrawn before delivery
  when the coordinator narrowed the seam to one boundary-invocation assertion in the
  existing `ProgramGraphPublicWireTest`.
- The coordinator owns the existing-test edit and its real-store runtime RED; no test
  or production source change is attributed to this task.

## Changed files

- `progress/program-graph-public-wire-red.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphBoundaryPublicWireTest test` | NOT RUN | The standalone test was withdrawn before delivery; no further Maven run was requested. |

## Decisions

- Keep the test to one public M6 execution and one runtime assertion; do not assert
  external effects, SQL, or XML behavior.

## Blockers

- Scope was transferred to the coordinator's minimal existing-fixture RED. The
  coordinator should record the selector and exact missing-variant assertion there.

## Exact next action

No further action in this task. The coordinator's progress file is the source of
truth for the delivered minimal RED.

## Resume checks

- Confirm this file contains only the handoff record; do not claim the withdrawn
  standalone selector as evidence for the coordinator's RED.
