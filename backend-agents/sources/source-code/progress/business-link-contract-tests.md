# Progress: Business-link contract tests

- Status: IN_PROGRESS
- Agent role: Business-link scope, typed/Corpus, and Runtime contract tests
- Model: GPT-6
- Started: 2026-10-05
- Last updated: 2026-10-05
- Scope: Add direct scope-v1/v2, typed-v4, Corpus v2, and bounded Runtime behavior tests; no production changes.
- Owning plan: `docs/plans/business-link-first-implementation-plan.md`
- Approved inputs: Fixed local fixtures only; no customer tools or model calls.
- Current branch/worktree: `technical-entry-evidence`

## Completed

- Read repository and scoped `AGENTS.md`, the business-link implementation plan, design §9.4 and §14, and `progress/TEMPLATE.md`.
- Added a direct `OntologyScopeReader.read(JsonNode, corpus)` contract class. Its fixtures use a real `OntologyEvidenceCorpus` from an `EntryEvidenceReader.Directory` and assert the returned task kinds.
- Covered v2 object-only SKELETON, ENRICHMENT ACTION with only a declared external source, missing dependency, SKELETON with ACTION, SKELETON with an external source, and strict scope-v1 behavior. Rejection tests assert the current reader's structured codes; accepted tests assert decoded purpose and preserved object source.
- Added a separate typed-v4 validator contract class. It validates all three object display roles, accepts a K1 disposition pointing to an actual L1 in the same typed response through strict review, rejects undisplayed K999 and dangling L999, preserves an empty model disposition without synthesizing a link, and confirms K remains navigation rather than evidence.
- Added a separate Corpus v2 contract class. Its control-clue test persists two deliberately non-domain-named IF controls through the existing R4 publisher/reader fixture, distinguishes the legacy Corpus from the opt-in business-link Corpus, and checks that returned K clues point back to the saved method. Its reverse-index test reuses the saved R4 PAGE_CONTEXT fixture and verifies exact entry/source membership, unmatched and no-request contexts, separate page request conditions/arguments, and NOT_PASSED callback parameters.
- The coordinator's first scope RED snapshot reported 8 tests, with the two positive v2 cases rejected by the prior reader because `purpose`/`objectSources` were unsupported.
- Added a focused formal Runtime DISCOVERY contract to `OntologyFormalRuntimeContractsTest`: SKELETON purpose is sent to prioritization, the actual schema allows only OBJECT, the saved selected task remains OBJECT, and an ACTION-containing priority response is rejected before typed dispatch.
- Corrected the new formal-flow upstream receipt expectations to match the physical O1/O2 inputs; O2 receives O1 publications and O3 receives O1 plus O2 publications.
- Updated the ACTION-priority Discovery test to read the structured `ONTOLOGY_DECISION_INVALID` from the saved stdout/inspect operation report rather than stderr.
- Updated the portable ontology configuration test for policy-set-v3 and its exact v1/v2 upstream registry templates.
- Added a pre-Provider rejection assertion for an active selection-v1 under policy-set-v2 while preserving the historical policy-set-v1 active-selection coverage.
- Added `newBusinessLinkModelO1UsesCurrentPhaseReadingBoundsNotSavedCorpusBounds` to capture an actual new-Corpus MODEL O1 reading request after O0 was saved with wider limits. It asserts current input/schema bounds and byte-for-byte unchanged O0 corpus publication; the coordinator has not run this selector.
- Added `v3ProducerRejectsHistoricalV1CorpusBeforeModelProviderDispatch`, reusing the exact-owner policy setup to create a projection-v1 O0, then explicitly configuring its historical owner registry under active policy-v3. A v1 MODEL scope must report `ONTOLOGY_CORPUS_VERSION_INVALID` before Provider initialization or dispatch. This new selector has not yet been run.

## Current state

The Provider-only reading-schema projection has been implemented and the new reading-bound tests passed in the coordinator's targeted 13-test batch. The coordinator reported the current-phase Runtime RED: actual O1 input used the saved O0 action bound 4 instead of current 1. The contract also checks navigation/schema bounds and immutable O0 publication. Separately, a new rejection selector now covers active policy-v3 over a historical projection-v1 O0 with its exact owner registry configured; code inspection found no general version fence, but this test has not yet been run. Historical v2-policy consumption of old v1 artifacts remains a separate supported path and is intentionally not modified.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/ontology/OntologyBusinessLinkScopeV2ContractsTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/ontology/OntologyBusinessLinkTypedV4ContractsTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/material/publish/OntologyBusinessLinkCorpusV2ContractsTest.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/OntologyFormalRuntimeContractsTest.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/OntologyConfigurationTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/ontology/OntologyProviderSchemaTest.java`
- `progress/business-link-contract-tests.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Coordinator's initial RED | `OntologyBusinessLinkScopeV2ContractsTest`; 8 tests | Two v2-positive tests errored because the prior reader refused `purpose`/`objectSources`. |
| Coordinator build `93134` | Reported by coordinator | Historical Discovery and two new formal Runtime flows passed; schema assertion was corrected to `$defs.selected`. |
| Coordinator 97-test batch | Reported by coordinator | Three failures described under Current state; test-side corrections are written. |
| Targeted Discovery RED | Reported by coordinator | JSON problem had fallback code/stage (`ONTOLOGY_RUNTIME_FAILURE` / `SURVEY`); strengthened contract requires the original model-output failure at `PRIORITIZE`. |
| Coordinator targeted reading batch | Reported by coordinator | 13 direct tests passed, including actual v4 schema/action/navigation and byte-limit request checks plus the historical v3 input-absence check. |
| Current-phase Runtime bounds RED | Reported by coordinator | `OntologyFormalRuntimeContractsTest#newBusinessLinkModelO1UsesCurrentPhaseReadingBoundsNotSavedCorpusBounds`; 1 failure, expected current action bound 1 but actual saved O0 bound 4. |
| Active-v3 / historical projection-v1 admission contract | Not run | `OntologyFormalRuntimeContractsTest#v3ProducerRejectsHistoricalV1CorpusBeforeModelProviderDispatch`; verify exact version error and zero Provider initialization/calls. |
| Builds/tests by this agent | Not run | Verification remains serialized by the root agent. |

## Decisions

- Keep scope tests in a new direct contract class and use only the existing scope reader entry point.
- Keep typed-v4 assertions on the direct validator boundary, pass the real selected clue refs explicitly, and assert observable diagnostics/documents instead of schema constants.
- Keep Corpus tests independent of existing test classes under modification; reuse the valid R4 PAGE_CONTEXT fixture and persist controls through the current publication path.
- Assert against the actual saved/request structures, including JSON Schema `$defs`/`$ref` locations; do not inline or weaken the production schema for test convenience.

## Blockers

- The active-v3/old-corpus admission selector awaits the coordinator's serialized RED and explicit version-gate decision.

## Exact next action

Coordinator should run only `OntologyFormalRuntimeContractsTest#v3ProducerRejectsHistoricalV1CorpusBeforeModelProviderDispatch`, confirm the fixture reaches the intended missing-version-guard behavior, then add the provider-free rejection without changing historical v1/v2 policy behavior. Do not run builds from this agent.

## Resume checks

- Run `git status --short` and preserve all pre-existing changes.
- Recheck the locked typed-v4 signatures and preserve selected-K inputs in tests.
- Preserve historical v1/v2 saved requests, prompt snapshots, and O0 identity; do not add a compatibility reexecution route for an old corpus under the new v3 policy.
- Do not run broader tests or builds unless the coordinator explicitly requests them.

## Plan closeout destinations

- Durable decisions: `docs/modules/ontology-recognition/business-link-first-design.md` §9.4 and §14.
- Remaining issues: coordinator's latest RED/GREEN record and the scope-v1 Prompt-identity compatibility point above.
- Verification and output references: coordinator's RED/GREEN record and final implementation progress.
