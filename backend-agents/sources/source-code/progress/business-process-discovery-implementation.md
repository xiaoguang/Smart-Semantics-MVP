# Progress: business-process discovery implementation

- Status: COMPLETE
- Started: 2026-09-14
- Branch: `codex/business-process-discovery`
- Baseline: `5d7f78f5dad45ed527b16ba06dc649284a39f273`
- Goal: replace the legacy singleton process path with repository catalog discovery, detailed candidate reconstruction, consolidation, and deterministic `business-processes.md` publication.

## Fixed reusable input

- Activity batch named in the plan: `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2afcb955b74572f3e802`
- Formal-store Activity owner actually reopened: `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e`
- Activity receipt: `module-receipt:982380d33683c32cc36e4d578117e9766413d73974fe2afcb955b74572f3e802`
- Material run: `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`
- Material receipt: `module-receipt:7228b9f5048bc3dc9dfba94712e3e839fd211d8a1c2c9baebccea6825a89e67b`

## Completed

- Saved the approved documentation baseline as commit `5d7f78f` and pushed it to `main`.
- Created this implementation branch from that exact baseline.
- Implemented frozen Activity/material corpus, index cards, sharded catalog DRAFT/REVIEW and merge.
- Implemented overlapping candidates, full-Activity assembly, requested source excerpts and detailed process DRAFT/REVIEW.
- Implemented repository consolidation, exact Activity/candidate/process coverage, aliases and direct Activity knowledge.
- Implemented four-artifact canonical Step07 publication, deterministic Markdown and fresh reopen.
- Implemented process-only run output, maintenance launcher mode and public CLI target mapping.
- Separated the exact historical input policy registry from the current Step07 output registry, so the fixed Activity/M10 checkpoints reopen without weakening or changing new publications.
- Verified the formal checkpoint contains 326 Activity JSONL rows, 326 coverage rows and zero unexplained entries; the receipt IDs match the plan.
- Direct verification: 44 tests passed across discovery, publication, run-output, launcher, CLI and model-job configuration.
- Full local verification completed with `mvn -t .mvn/toolchains.xml -Pquality spotless:check verify`: 520 tests, zero failures, zero errors and two designed skips. Spotless, Enforcer, SpotBugs and PMD all pass. The command took 7 minutes 58 seconds in the approved local environment required by the three loopback HTTP tests.

## Final delivery

- Added RED/GREEN coverage for separate historical-input/current-output stores and controls. New model batches retain the source inputs but declare the current output policy.
- Added RED/GREEN coverage for deterministic model JSON Schema enums; every enum is deduplicated and UTF-8 sorted before its schema hash enters the reusable job fingerprint.
- Migrated the already completed `2cd64d62...` reviewed pairs once through a local offline replay; this made no model calls and preserved the exact saved DRAFT/REVIEW content.
- Final formal batch `analysis-run:8be6f8273cc76837256d51a922593b525a6cf2eafa502e2484512d6755a034e6` finished by reusing 22/22 complete reviewed jobs and making zero new model calls.
- Published 46 business processes, including 21 multi-Activity processes; all 46 have multiple stages. Activity coverage is CLOSED: 102 PROCESS_MEMBER, 157 SUPPORT_ONLY, 17 STANDALONE and 50 UNCLASSIFIED. Semantic delivery is therefore PARTIAL.
- The Markdown contains 680 unique short references, all present in the 1,625-row `source-refs.jsonl`.
- Formal artifacts are stored under run `analysis-run:8be6f8273cc76837256d51a922593b525a6cf2eafa502e2484512d6755a034e6`, including `business-processes.md`, `repository-business-process-catalog.json`, `process-coverage.json`, `source-refs.jsonl` and the canonical module receipt.

## Constraints

- Do not rerun JDT, Steps01-05, BusinessMaterialBuilder, or ActivityExplainer.
- Do not invoke or publish Step08/nine-section output.
- Do not hard-code domain vocabulary or add automatic model retry/fallback.
- Preserve the unrelated root `docs/research/` files.
