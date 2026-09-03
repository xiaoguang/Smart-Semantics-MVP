# Progress: control-flow-v3-store-green

- Status: COMPLETE
- Agent role: Terra production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03T01:05:01Z
- Last updated: 2026-09-03T01:07:00Z
- Scope: Accept only the published v3 control-flow graph draft schema in the canonical module-artifact store.
- Approved inputs: Published `program-graphs` gap-projection design contract and the existing `ProgramGraphGapProjectionTest` RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped implementation contract and inspected the exact `AtomicCanonicalPublicationEngine` acceptance branch.
- Confirmed the root cause: the M3 control-flow producer identifies drafts as `program-graphs-control-flow-draft-v3`, while the store branch only accepts v2.
- Changed only the M3 control-flow draft contract branch to accept v3.
- Formatted the module. Spotless also normalized the already-present untracked `ProgramGraphGapProjectionTest` as part of the required project formatting pass; no test behavior was edited.
- Verified the direct selector now installs the v3 M3 draft and reaches the expected later M6 Gap-projection failure.

## Current state

- The bounded production change is complete. The remaining failure belongs to M6 Gap projection, outside this slice.

## Changed files

- `progress/control-flow-v3-store-green.md`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing Stage03 worktree changes preserved. |
| `rg ... AtomicCanonicalPublicationEngine.java` | PASS | M3 branch accepts only `program-graphs-control-flow-draft-v2`. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Build succeeded; production source and existing test were formatted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphGapProjectionTest test` | EXPECTED RED | Reached `ProgramGraphSetPublicationSpecifier.requireNoUnprojectableGaps` (`GRAPH_REFERENCE_BROKEN`); no `MODULE_INSTALL_REQUEST_INVALID` M3 store failure remains. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- This is a store acceptance correction only. It must reject v2 and accept v3; no builder, reader, publisher, test, or M6 behavior changes are in scope.
- `GRAPH_REFERENCE_BROKEN` now originates at M6's unprojectable-gap guard, exactly the next planned implementation boundary.

## Blockers

- M6 has not yet projected valid local M1–M4 graph-gap drafts into `graph-gaps.jsonl`; that is intentionally outside this M3 store slice.

## Exact next action

- Hand the verified M6 RED to the next bounded Gap-projection slice.

## Resume checks

- Re-read this file, confirm the schema literal remains v3-only, and use the direct selector to confirm any subsequent M6 change closes the projected-gap path.
