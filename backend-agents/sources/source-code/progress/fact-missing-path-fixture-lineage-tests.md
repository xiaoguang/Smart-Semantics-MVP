# Progress: fact-missing-path-fixture-lineage-tests

- Status: COMPLETE
- Agent role: Luna/xhigh bounded test-fixture lineage correction owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Repair the existing `FactCandidateMissingPathTest` public mutation fixture so its copied ApplicationDiscovery capability report points to the newly copied Source publication, while preserving the missing-argument-evidence mutation and all existing assertions. Remove only the accidental final LF from the already-owned fixed-repository JSON oracle. No reader, production, design, fixture behavior, source, Provider, network, or Maven changes.
- Approved inputs: `progress/fact-missing-path-fixture-lineage-diagnosis.md`, the existing test/helper, the public ApplicationDiscovery writer/reader contract, and the owned carrier oracle.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`

## Completed

- Created this progress record before Java/JSON edits and staged it intent-to-add.
- Updated only the existing mutation helper: the copied Discovery `capability-report.json` now rebinds its four-field `verifiedSourceInventoryPublicationRef` to `sourceStep.reference()`, and the changed payload is rehashed with `standalonePayload`; the other three Discovery payloads and mutation assertions remain unchanged.
- Corrected the owned carrier progress wording so the projection is described as removing only program-only origin metadata, not as removing source paths from local Capsule material.
- Removed only the accidental final LF from the owned fixed-repository JSON oracle; its minified JSON semantic SHA is unchanged.

## Current state

- The Java and JSON corrections are complete and frozen. Root's final frozen verification session 16470 passed the repaired `FactCandidateMissingPathTest` with 1 test, 0 failures, 0 errors, and 0 skipped; the config-only IT also passed with 1/0/0/0. No further correction is pending.

## Changed files

- `progress/fact-missing-path-fixture-lineage-tests.md` (owned)
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateMissingPathTest.java`
- `src/test/resources/analysis/flow/fixed-repository/fixed-repository-acceptance-config.json` (final LF only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared worktree changes preserved before edits. |
| Static diagnosis and helper contract read | PASS | The failure is stale copied Discovery provenance, not a reader defect; only the capability payload requires rebinding. |
| Scoped `git diff --check` | PASS | No whitespace errors in the owned Java/progress edits. |
| Node byte/semantic assertion before and after LF correction | PASS | Final byte changed from LF to `}`; JSON is minified and semantic SHA remains `351516314acb17734debe4824f0c2314fff7a06666a46e1f31b45c0a0a34f486`. |
| Root frozen session `16470` | PASS | `FactCandidateMissingPathTest`: 1 test, 0 failures, 0 errors, 0 skipped; config-only IT: 1/0/0/0; overall exit 0. |

## Decisions

- Keep the mutation semantics, all three unchanged Discovery payloads, graph mutation, and assertions unchanged.
- Reuse the existing public `standalonePayload` helper for the capability report; do not hand-edit artifact IDs or relax `PersistedFactCandidateInputReader`.
- Remove only the final LF from the owned JSON oracle; do not change policy/config values.
- Do not run Maven in this activity; Java remains frozen and the authorized JSON byte correction is complete.

## Blockers

- No blocker remains for this bounded fixture-lineage correction. Full Step 05 remains explicitly outside this slice and source/domain acceptance remains blocked.

## Exact next action

- Release this completed bounded correction; no further edits or verification are authorized in this activity.

## Resume checks

- Confirm the capability report alone changes among Discovery payloads and its artifact ID/SHA are recomputed by `standalonePayload`.
- Confirm no reader/production/design/test assertion changes outside the owned Java helper and no JSON semantic changes.
- Do not run Maven or broaden the slice in this activity.
