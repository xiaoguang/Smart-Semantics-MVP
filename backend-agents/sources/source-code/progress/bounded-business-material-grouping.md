# Progress: bounded business material grouping

- Status: COMPLETE
- Agent role: primary implementation and verification agent
- Model: gpt-5.6-sol / ultra design interpretation already captured in the approved Step06 architecture; gpt-5.6-luna / xhigh for a public-seam RED; gpt-5.6-terra / xhigh for the minimal GREEN
- Started: 2026-09-11T16:13:00Z
- Last updated: 2026-09-11T18:59:00Z
- Scope: Replace the current one-entry-per-BusinessMaterial ordinary path with bounded, deterministic related-entry material groups. Preserve per-entry coverage, source references, Flow/Facts and the model boundary. Do not implement Java business classification or change Activity/Process/Report schemas.
- Approved inputs: Current Step05 Flow/Capsule publications and EntryContexts; existing BusinessMaterialBuilder public fixture; approved business-first design.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Verified that `BusinessMaterial` already supports multiple entry IDs and ActivityExplainer already supports multiple activities per material.
- Located the inefficient ordinary path: `BusinessMaterialBuilder.materialSet` emits one material for each entry, causing the projected 339-entry repository to schedule 339 DRAFT+REVIEW pairs.

## Current state

- Updated the Step06 grouping contract and changed the synthetic replenishment public-seam expectation from seven one-entry packets to two bounded packets of three or four related entries. The test also requires every entry coverage record to point to a packet containing that entry.
- The public-seam RED failed as intended: expected two packets, actual seven. Implemented an initial ordinary-path grouping projection: same handler owner plus material mode, capped by entry count, source-reference count and actual model packet characters. The projection reuses existing complete packet contexts and SourceRefs; it does not reconstruct calls or decide business semantics.
- The first affected-selector run exposed a correctness defect, not merely old count expectations: the initial grouping chose representative snippets and could discard part of a member entry's Controller → Service → boundary-call context. Grouping must instead be all-or-nothing for each entry's selected packet references; if the union cannot fit, those entries remain separate.
- Corrected the redundant nested-span selection so a method snippet can subsume its own parameter and call-line excerpts without dropping distinct source regions. The replenishment grouping selector is GREEN again, and the connected Controller → Service → boundary packet selector remains GREEN.
- Added a focused grouped-material RED: a response must assign each activity to scope-local `E1…En` entry keys; Java then maps only those keys to persisted entry coverage. Implemented the minimal output-schema, clean-packet, reviewed-activity and coverage mapping change, plus the prompt instruction. The two-test ActivityExplainer selector is GREEN.
- The direct material/activity regression found one expected contract update: the ordinary fixture now correctly has one two-entry material record plus two coverage records, rather than two duplicate material records. Updated that test to assert both routes are present in the one clean packet.
- The rerun found one real group-only leakage: the combined packet copied program-side `fact-gap:` identifiers from persisted limitations into the model-visible limitation list. The existing model-cleanliness assertion caught it. The group packet must use the existing limitation sanitiser, just as a one-entry packet does.
- The existing fixed-repository acceptance harness already creates business materials with zero Provider calls after Step01–05. Its three-argument profile now uses the intended bounded four-entry default, so a new fresh-workspace run will measure the actual grouped repository material count without a new test-only path.
- The first planning invocation was rejected before source capture because the temporary workspace was outside this module's ignored `.workspace/` directory. This is an existing test safety boundary, not a source, model, or grouping failure. No source scan or Provider call started.
- Applied that same limitation sanitiser to grouped packets. The direct material/activity regression is now GREEN: 21 tests, zero failures and zero errors. It confirms complete selected code context, scope-local activity coverage, clean model input, fallback handling, budgets, prompt instructions and response validation.
- Added a public-seam RED proving that each grouped context must visibly label its member entry as `E1…En`, the same keys supplied in the model schema. The RED failed because the packet listed routes without a stable per-entry mapping. The minimal GREEN labels each preserved member context, and the grouping plus ActivityExplainer selector is GREEN: 3 tests, zero failures and zero errors.
- Reran the fixed-repository acceptance test in a fresh permitted `.workspace/` child. It passed in 254.5 seconds with zero Provider calls. The fixed commit still has 719 tracked files and 339 discovered HTTP entries. The persisted materials JSONL now has 446 records: 107 `BUSINESS_MATERIAL` records and 339 `ENTRY_COVERAGE` records. The material entry-count distribution is 21 one-entry, 10 two-entry, 6 three-entry and 70 four-entry packets, which closes exactly over all 339 entries. Twelve groups are `FLOW_PREFERRED`; 95 are `ENTRY_SOURCE_FALLBACK`. Each inspected multi-entry model context visibly carries `入口 E1…En` labels.
- The final direct material/activity regression is GREEN after project formatting: 21 tests, zero failures and zero errors. `spotless:check` passes, as does `git diff --check`.

## Changed files

- progress/bounded-business-material-grouping.md
- docs/analysis-steps/06-flow-interpretation.md
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialProfile.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java
- src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderReplenishmentTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| source inspection | PASS | Existing model and coverage types accept grouped materials without a new public contract. |
| grouped-material selector | RED | 1 failure, expected two packets but old builder emitted seven. |
| grouped-material selector after implementation | PASS | The grouped replenishment selector passed. |
| affected material/activity selectors | RED | Grouped source-reference selection can hide a member's context; several tests also intentionally assume a one-entry test fixture and need explicit `maxEntriesPerMaterial=1`. |
| nested-span grouping correction | PASS | Replenishment grouping and full connected-context material selectors both pass. |
| grouped activity entry-key selector | PASS | 2 tests: one-entry Draft/Review and multi-entry per-activity coverage mapping. |
| focused material/activity regression | PARTIAL | 8 selected classes passed; BusinessMaterialBuilderTest needs the grouped-record expectation update now applied. |
| focused material/activity regression rerun | RED | One model-boundary failure: grouped limitations leak a `fact-gap:` identity. |
| focused material/activity regression after sanitising grouped limitations | PASS | 21 tests, 0 failures, 0 errors; Provider calls are scripted only. |
| grouped entry-key context RED | PASS (expected RED) | 1 failure: model context had no `入口 E1：` label. |
| grouped entry-key context GREEN | PASS | BusinessMaterialBuilderReplenishmentTest + ActivityExplainerTest: 3 tests, 0 failures, 0 errors. |
| fixed-repository grouped planning preflight | BLOCKED (expected safety check) | 0.3 seconds; workspace outside the module `.workspace/` rejected before any source read. |
| fixed-repository grouped materials-only acceptance | PASS | 1 test, 0 failures/errors/skips; 254.5 seconds; 107 material groups + 339 entry coverage records; 0 Provider calls. |
| final material/activity direct regression | PASS | 21 tests, 0 failures, 0 errors after formatting. |
| `spotless:check` | PASS | All 593 Java files clean. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Grouping may use code structure and direct already-persisted call anchors only; it cannot name a business process, infer order or merge objects.
- Each group has a strict entry cap and actual serialized packet budget. An entry that cannot fit remains separately materialized or receives an explicit coverage reason.
- Per-entry coverage continues to reference the one group that supplied its reading context.
- A group never drops a member's selected source reference. The model receives a whole selected entry packet or the entry stays outside that group.
- A grouped activity must name the scope-local entry keys it covers. Java maps those keys to persisted entry IDs and records coverage per entry; it must not treat every activity in a packet as covering every member.

## Blockers

- None known.

## Exact next action

- Completed. A successor must use the persisted 107-group materials plan for a bounded grouped-material semantic-quality check before scheduling broader Provider work.

## Resume checks

- Read this file and `docs/analysis-steps/06-flow-interpretation.md`; inspect `BusinessMaterialBuilder.materialSet` before editing.
