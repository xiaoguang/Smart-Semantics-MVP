# Progress: business material direct-entry fallback

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Make the existing BusinessMaterialBuilder implement the active Step06 contract for a
  safely located Step02 entry when no Step05 BusinessFlows publication exists. Preserve the
  Flow/Capsule-preferred route, source references, budget gates, entry coverage, and clean model
  packets. Do not add business dictionaries, alter program graphs, call a provider, or change the
  public RepositoryAnalysisAgent.
- Approved inputs: Active `docs/DESIGN.md` and Step06 detailed design explicitly require safe
  no-Flow entry-source fallback; user approved ongoing implementation and local module-level
  contract corrections.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the active design requires a request to include verified source and application
  discovery inputs, with Flow/Capsule preferred but not mandatory.
- Confirmed the current two-field request only accepts `BusinessFlowsReference`; its existing
  fallback begins too late, because a Step05 publication must already exist.

## Current state

- The request now accepts either published Flow input or a matching verified-source plus
  application-discovery pair. The Builder reopens and validates the direct pair before emitting
  `FLOW_NOT_AVAILABLE` entry-source material. Existing Flow-preferred callers remain unchanged.

## Changed files

- `progress/business-material-direct-entry-fallback.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BuildBusinessMaterialsRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderFallbackTest.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Step06 design-to-code comparison | GAP CONFIRMED | Design permits Step02 direct fallback; current request requires Step05. |
| direct-entry fallback before implementation | RED | The missing source/discovery request constructor prevented a no-Step05 caller from compiling. |
| direct-entry fallback after implementation | PASS | 1 test, 0 failures/errors/skips; two located entries became `ENTRY_SOURCE_FALLBACK` materials without a Flow publication. |
| material module regression | PASS | 6 tests, 0 failures/errors/skips; Flow/Capsule-preferred, zero-entry, budget and existing Flow-Gap paths remain green. |
| `spotless:check && git diff --check` | PASS | 0 Java format violations and no whitespace errors. |

## Decisions

- This is a local design-conformance correction, not a new fallback reader or a second analysis
  route: the same BusinessMaterialBuilder owns both Flow-preferred and direct-entry modes.

## Blockers

- None.

## Exact next action

- No further action in this work unit.

## Resume checks

- Direct-entry material must use frozen source, have a globally valid short SourceRef, retain a
  concrete Flow-not-available limitation, and send no Provider request.
