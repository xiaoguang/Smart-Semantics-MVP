# Progress: M5 R2 basis gate implementation

- Status: COMPLETE
- Agent role: Terra/xhigh minimal production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Make `InterpretationRunner` reject foreign R1 basis references and R2 basis expansion before any M5 execution result can be returned or published.
- Approved inputs: Frozen M5 §6.7.2.1, the two direct scripted-provider RED tests, the persisted M4 task and same-Flow registry publications.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read scoped repository rules, the precise M5 design gate, the two direct RED tests, and the runner/task/registry serialization seams.
- Confirmed the RED cause: `validateBasis` currently checks only key and Flow membership; it discards atom and Gap lists.
- Implemented the narrow registry/task closure: M5 fresh-reopens the M3 item's exact atom/Gap basis, verifies each M4 task's embedded allowed item equals that frozen value, and rejects foreign response references before an execution result exists.
- Implemented R2 per-key subset validation after the response basis has passed the same key's frozen membership gate.

## Current state

- Luna corrected the expansion fixture independently: it now creates one R0 registry item with two exact frozen atom basis values, lets R1 select only the first, and makes R2 return both. The strict production gate returns `MODEL_REVIEW_EXPANDED` only for that genuine expansion.
- Both public basis-gate tests and the directly affected runner test are GREEN.

## Changed files

- `progress/m5-r2-basis-gate-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunner.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=M5R2BasisGateTest test` | PASS | 2 tests, 0 failures/errors/skips; the corrected same-key A-to-A+B and foreign atom/Gap cases both reject before Candidate creation. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/interpretation/model/InterpretationRunner.java` | PASS | Scoped production formatting applied. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=InterpretationRunnerTest,M5R2BasisGateTest test` | PASS | 3 tests, 0 failures/errors/skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep the model response grammar, Provider invocation order/count, M5 carrier identity, and all artifacts unchanged.
- Use only M4 task JSON and M3 registry JSON reopened through existing module store; do not parse source or fall back to strings from source code.
- Do not treat all same-Flow Capsule atoms as a selected registry key's allowable basis: a registry key owns its exact frozen atom/Gap allowlists.

## Blockers

- None.

## Exact next action

- Hand this bounded GREEN result to the coordinator. Do not modify M5 carrier identity, receipts, publisher, model grammar, or Provider lifecycle in this work unit.

## Resume checks

- Re-read M5 §6.7.2.1 and confirm any future M5 carrier work preserves this pre-Candidate gate.
- Preserve all unrelated shared-worktree changes.
