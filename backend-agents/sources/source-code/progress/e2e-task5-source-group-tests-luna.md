# Progress: Task 5 source-group navigation tests

- Status: READY_FOR_ROOT_TEST
- Agent role: direct-test author
- Model: GPT-6 Luna / xhigh
- Started: 2026-09-23
- Scope: direct Task 5 tests in `src/test` plus this progress note; no production edits.
- Inputs read: `.workspace/end-to-end-business-delivery-20260923/task-5-brief.md`, `docs/modules/business-process-discovery/frozen-analysis-corpus.md`, `docs/modules/business-process-discovery/repository-business-cataloger.md`, and section 6 of `docs/end-to-end-business-delivery-design.md`.
- Parent-approved source-group JSON fields: `packetKey`, `sourceSnapshotId`, `navigationReceiptId`, `packetId`, `packetEntries` (`entryKey` plus canonical `entryId`), `activityEntryIds` (canonical IDs), nullable `sliceKey`, `groupPosition`, and `groupSize`.
- Current direct fixtures: (a) one strict Step05 `ProcessDiscoveryRequest` with three packets and eight Activities, asserting stable group identity across catalog, global selection, CHECK and final reading-packet inputs; (b) 418 synthetic Activities run through actual catalog sharding and selection, checking 1-based page identity/count, full card/disposition denominator, stable group objects across shards and absence of pagination claims on merge/selection inputs.
- Prompt contract tests: route expectations updated to catalog/shard/merge v3, SELECT v3 and CHECK v5. Added a routed-instruction assertion for stable `sourceGroup`/`packetKey` identity and non-temporal `groupPosition` guidance.
- Fixture correction: the frozen source-text reader now receives one valid `FrozenGroupSource.java` document; its snapshot comes from the existing Step05 header and matches the `VerifiedSourceTextSet` helper. The shared test-only text-document factory is package-visible for reuse.
- Legacy fixture correction: `FrozenAnalysisCorpusDualMaterialSourceTest.step05Materials()` now gives `packet:alpha` and `packet:beta` their real `entry:alpha` / `entry:beta` `EntrySeed`s, preserving the existing per-packet source-reference assertions while satisfying the strict Activity-entry membership contract.
- Verification: `git diff --check` passes. No Maven run by this worker; root owns the sole test slot.
- Exact next action: root should run `BusinessProcessDiscoveryTest#retainsAll418SourceGroupCardsAcrossCatalogShardsAndGlobalSelection`, `BusinessProcessPromptV2ContractTest`, and the previously green small source-group/FrozenCorpus selectors. No production edits or Maven run by this worker.
