# Progress: Business lifecycle real acceptance

- Status: IN_PROGRESS
- Agent role: Real Luna acceptance and release coordination
- Model: gpt-6-astra
- Started: 2026-09-15
- Scope: Reuse the fixed 326 reviewed Activities and M10 material checkpoint, run the v2 repository catalog, detailed process reconstruction, consolidation, and publish the five Step07 artifacts. Do not rerun JDT, BusinessMaterialBuilder, ActivityExplainer, or Step08.
- Branch: `codex/business-lifecycle-readable-implementation`

## Fixed inputs

- Activity run: `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e`
- Activity receipt: `module-receipt:982380d33683c32cc36e4d578117e9766413d73974fe2afcb955b74572f3e802`
- Material run: `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`
- M10 receipt: `module-receipt:7228b9f5048bc3dc9dfba94712e3e839fd211d8a1c2c9baebccea6825a89e67b`

## Completed

- Implementation steps 0–5 are complete and locally verified.
- Six catalog shards completed DRAFT and REVIEW and were saved: 12 Luna/high calls.
- Catalog merge DRAFT completed with 15 candidate processes but disposed only 152 of 326 Activities.
- Catalog merge REVIEW returned empty catalog arrays. The existing program denominator check rejected publication with `PROCESS_CATALOG_ACTIVITY_DENOMINATOR_OPEN`; no formal Step07 output was published and no upstream analysis was rerun.
- Added a direct RED/GREEN contract test and a merge-only response-schema constraint requiring exactly one disposition for every repository Activity. Updated the merge prompts so uncertainty becomes an explicit `UNCLASSIFIED` disposition rather than an empty catalog. Shard task contracts are unchanged and remain eligible for reuse.
- Reuse run `analysis-run:f9861990c7813ed4bf613e7881fec1e272cdaca39f3d0c9e40d725592c4ada19` proved that all twelve shard calls were reused and only merge DRAFT/REVIEW ran. DRAFT returned 9 candidates and 326 dispositions. REVIEW also returned 326 dispositions, but repeated two legal Activity IDs inside one business-area membership list. The parser's combined unknown-or-duplicate error rejected the catalog. Exact duplicate area membership is now deterministically removed while genuinely unknown IDs remain fatal; no semantic content, candidate use, disposition, or source reference is dropped.
- Zero-call parse run `analysis-run:2bc623110bacb9dfd6844f2580508d3202d8bdc2177d8b25e36149c196ca7907` then exposed a second contradiction in that same reviewed response: 32 Activities were labelled `PROCESS_MEMBER` but occurred in no candidate. The existing fatal check is retained because silently assigning them would invent candidate membership and silently downgrading them would lose intended process members. Merge prompts now require lifecycle/object-handoff candidates instead of broad CRUD maintenance groups and require every `PROCESS_MEMBER` to occur in at least one candidate; otherwise the model must choose an explicit nonmember disposition.

## Current state and next action

- Failed catalog batches retained read-only: `analysis-run:128575e8423a95090000c4b9941910f1b8af3203c749770ec40b88b40f0e9860`, `analysis-run:f9861990c7813ed4bf613e7881fec1e272cdaca39f3d0c9e40d725592c4ada19`, and the zero-call parser check `analysis-run:2bc623110bacb9dfd6844f2580508d3202d8bdc2177d8b25e36149c196ca7907`.
- Rebuild the prompt resources, then start a new catalog batch with explicit reuse from `analysis-run:f9861990c7813ed4bf613e7881fec1e272cdaca39f3d0c9e40d725592c4ada19`. Shard jobs remain reusable; merge DRAFT/REVIEW must rerun because their prompt identity changed. Verify all 326 dispositions, no orphan `PROCESS_MEMBER`, and lifecycle-oriented rather than broad CRUD candidates.
- After catalog closure, run two real candidate DRAFT/REVIEW pairs, inspect business conditions and narratives, then reuse them in the remaining candidate run, consolidation, and five-artifact publication.

## Verification

| Check | Result |
| --- | --- |
| Merge schema RED | PASS as a failing test: min/max item count absent |
| Merge schema GREEN | PASS, exact count required for DRAFT and REVIEW |
| Exact duplicate area membership RED/GREEN | PASS; duplicate normalized, unknown ID still rejected |
| Merge lifecycle/membership Prompt RED/GREEN | PASS; no fixture domain terms introduced |
| `BusinessProcessDiscoveryTest,BusinessProcessPromptV2ContractTest` | 31 tests, 0 failures/errors |
