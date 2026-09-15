# Progress: business lifecycle process-detail implementation

- Status: COMPLETE
- Agent role: Terra/xhigh GREEN production implementer
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Implement only the approved Step07 v2 production contracts covered by the committed lifecycle-detail RED tests: stage narrative, rule activity-use scope, and coverage activity name. Tests, prompts/resources, publisher versions/files, renderer wording, consolidation semantics, runtime/CLI, and unrelated code remain out of scope.
- Approved inputs: `docs/plans/business-process-discovery-and-reconstruction-change-design.md` sections 6, 8, and 9; commits `dae4122` and `1934344`; `progress/business-lifecycle-process-detail-tests.md`.
- Current branch/worktree: `codex/business-lifecycle-readable-implementation` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Approved contract

- `RepositoryBusinessProcessCatalog.ProcessStage` stores a required nonblank `narrative`, which survives parse, serialization, copying, and response-schema validation.
- `BusinessRule` stores required nonempty global `activityUseIds`; wire responses supply required nonempty `activityUseLocalIds`, which parse only through the current process's local-use map. Rule statement and source refs must be valid for the union of exactly those selected uses' Activities.
- `ProcessCoverage.ActivityDisposition` stores the original reviewed `Activity.name`, projected deterministically by discovery and retained through JSON.

## Plan

1. Locate all record constructors and JSON parse/serialize/copy paths for stages, rules, and activity dispositions. COMPLETE.
2. Make the minimal production-only contract changes and compile-consumer updates required by record arity. COMPLETE.
3. Run the focused RED selector and targeted Spotless; update this handoff to COMPLETE with exact evidence. COMPLETE.
4. Commit only the production changes and this progress document. COMPLETE.

## Current state

- `ProcessStage` now requires and preserves `narrative`; the DRAFT/REVIEW response schema requires a nonblank string, parsing stores it, and consolidation's record JSON plus the merge copy retain it.
- `BusinessRule` now requires stored global `activityUseIds`. DRAFT/REVIEW wire schemas require a nonempty `activityUseLocalIds` array of nonblank IDs; parsing resolves only current-process uses, rejects unknown/duplicate/empty local IDs, and validates rule refs against the selected uses' Activities rather than the whole candidate.
- `ActivityDisposition` now stores the original reviewed Activity name. Catalog parsing obtains it from the matching deterministic activity index card and record JSON retains it.
- Existing candidate-coverage validation now runs before detailed parsing when a response contains only known but incomplete candidate Activities, retaining the pre-existing coverage error precedence when that malformed response also leaves a rule with a removed local use.
- Existing unrelated worktree content under `docs/research/` is untracked and will be preserved.
- No model, network, live source, customer build, prompt-resource, publisher, renderer, runtime, or CLI work was performed. The versioned publisher remains deliberately unchanged as assigned; its v1 catalog/coverage artifact serializers are outside this GREEN slice.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java,src/main/java/org/sourceanalysis/app/analysis/knowledge/ProcessCoverage.java,src/main/java/org/sourceanalysis/app/analysis/knowledge/RepositoryBusinessProcessCatalog.java` | PASS | Targeted Spotless completed with exit 0. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessDiscoveryTest test` | PASS | 21 tests run; 0 failures, 0 errors, 0 skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Blockers

- None. Follow-on publisher/version work remains intentionally outside this task's approved scope.
