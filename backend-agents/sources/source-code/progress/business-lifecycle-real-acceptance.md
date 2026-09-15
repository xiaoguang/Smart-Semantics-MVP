# Progress: Business lifecycle real acceptance

- Status: COMPLETE
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
- Catalog batch `analysis-run:7b658e2525a4cc554b6bd08498f9a39dbca9b572107afafcd192c2dcd8e971a9` completed a 23-candidate DRAFT and 24-candidate REVIEW with all 326 array slots, but REVIEW repeated 46 Activity IDs and therefore omitted 46 others. This is a long-array transcription defect, not a new business decision. A direct RED/GREEN now keeps the complete DRAFT denominator when the REVIEW echo is structurally duplicate/incomplete, then derives membership from the reviewed candidates. A complete unique REVIEW ledger containing an orphan PROCESS_MEMBER remains fatal.
- Catalog was reopened successfully and produced 24 candidates with all 326 Activities disposed. Two real sample candidates completed Luna/high DRAFT and REVIEW and were saved under model batch `analysis-run:4125a702ec8489a65792e93d5d77cd1630b7933c3e34f928b93c9dba85ac3224`.
- The sample exposed an over-strict evidence-ownership check: valid sources from another Activity inside the same candidate were rejected when a rule listed a narrower activityUse. The rule now accepts any existing candidate-scoped statement/source reference while activityUseIds remains the business-applicability field. Unknown and out-of-candidate refs remain fatal; no new evidence subsystem or model call was added.
- Full candidate batch `analysis-run:e25692b14a8815ff191b9cd60d2d94a1ef5248de1d874511b1ab051e8ff43f02` preserved 12 reviewed candidates before a Codex capacity failure. The finite diagnostic classifier matched the word `model` before `capacity`, so the error was incorrectly reported as MODEL_CONFIGURATION; a direct RED/GREEN now classifies the combined phrase as CAPACITY.
- The first explicit continuation exposed that Provider request journals were shared across model batches: the old STARTED request blocked the new batch with RUN_JOURNAL_STARTED_REPLAY_FORBIDDEN, contradicting the documented explicit-new-batch contract. Provider journals are now scoped by modelBatchId; the same direct test proves two batches create independent request directories while completed job reuse remains unchanged.
- Continuation batch `analysis-run:23519d8392ab2db82e3ac781e26b5d0bf96f9347be48af1fa27fdf9e1dcea298` completed and saved all 24 candidate DRAFT/REVIEW pairs. Its repository-consolidation DRAFT then failed immediately: the request carried 1,656,824 bytes of input because every detailed statement/source reference was repeated across the full-process JSON. A direct RED/GREEN now sends a deterministic business-complete consolidation view while retaining full original processes program-side; stage narratives, predicates, rules, results and unresolved connections remain, but repeated nested evidence fields do not consume the model context.
- Compact-consolidation batch `analysis-run:41d2a7a80c951726395d4c35603a141bf5b81c67175ed1c05daf644fefef185e` reused all 24 reviewed candidates. Both consolidation calls returned, but the REVIEW supplied 83 of 85 unique process decisions. A direct RED/GREEN now retains every omitted original process as KEEP while applying all explicit reviewed decisions. Unknown/duplicate IDs and illegal merges remain fatal; no business text is invented.
- Final zero-call reuse batch `analysis-run:c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035` finished and published all five Step07 artifacts. It reused the 24 candidate results and the compact consolidation pair; no JDT, Builder, Activity, candidate, or consolidation model call was made in the publication batch.
- The final catalog publishes 85 processes across 24 business areas. All 85 contain at least two stages; 22 contain multiple Activities. Coverage is CLOSED: 138 PROCESS_MEMBER, 155 SUPPORT_ONLY, 19 STANDALONE, and 14 UNCLASSIFIED Activities. Two candidates are INSUFFICIENT_MATERIAL, so semantic delivery is correctly PARTIAL rather than overstated.
- All process source refs resolve in `source-refs.jsonl`. A recovery archive containing the complete material run, 326 reviewed Activities, both real sample results, 24 final candidate results, consolidation result, and final publication was saved as `.workspace/retained/jsherp-business-lifecycle-v2-c589a5e6.tar.gz`; its identity is recorded in `progress/business-lifecycle-v2-retention.md`.

## Final state

- Failed catalog batches retained read-only: `analysis-run:128575e8423a95090000c4b9941910f1b8af3203c749770ec40b88b40f0e9860`, `analysis-run:f9861990c7813ed4bf613e7881fec1e272cdaca39f3d0c9e40d725592c4ada19`, and the zero-call parser check `analysis-run:2bc623110bacb9dfd6844f2580508d3202d8bdc2177d8b25e36149c196ca7907`.
- The five Step07 artifacts are available under `analysis-run:c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035`. JDT, BusinessMaterialBuilder, ActivityExplainer and Step08 remained at zero calls throughout this Step07 acceptance.

## Verification

| Check | Result |
| --- | --- |
| Merge schema RED | PASS as a failing test: min/max item count absent |
| Merge schema GREEN | PASS, exact count required for DRAFT and REVIEW |
| Exact duplicate area membership RED/GREEN | PASS; duplicate normalized, unknown ID still rejected |
| Merge lifecycle/membership Prompt RED/GREEN | PASS; no fixture domain terms introduced |
| Merge REVIEW duplicate/missing disposition recovery RED/GREEN | PASS; DRAFT denominator retained, reviewed candidates still authoritative |
| Compact consolidation business view RED/GREEN | PASS; detailed business semantics retained and repeated nested evidence fields omitted |
| Consolidation REVIEW omission RED/GREEN | PASS; omitted original process is retained as KEEP |
| Direct affected regression | 58 tests, 0 failures/errors |
| Real publication | PASS; FINISHED, 85 processes, CLOSED coverage, honest PARTIAL semantics |
| Source lookup | PASS; zero process source refs missing from `source-refs.jsonl` |
| `BusinessProcessDiscoveryTest,BusinessProcessPromptV2ContractTest` | 32 tests, 0 failures/errors |
| Full local CI | PASS; `mvn -t .mvn/toolchains.xml -Pquality spotless:check verify`, 558 tests, 0 failures, 0 errors, 2 skipped; Spotless, Enforcer, SpotBugs and PMD passed |
