# Progress: business-flow wire handoff tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Apply the already-published M1/M2/public wire schema handoff in exactly four existing test classes plus the three matching fixture policy literals; no new behavior, fixture/source/graph/design/Step06/Provider changes.
- Approved inputs: Published Step04/Step05 wire mappings and the existing public publisher/consumer tests.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress note before the selector baseline.

## Current state

- Mapping is bounded to `CapsuleProjectionModulePublisherTest`, `BusinessFlowsPublicationSpecifierTest`, `RegistryProposalTaskCompilerTest`, `FiniteKeyFlowTaskCompilerTest`, and the three matching policy literals in `ProgramGraphsPublicFixture`.
- Published mappings: M1 Flow compilation v2→v3, M2 capsule projection v5→v6, public Flow slices v2→v3, and public Evidence capsule v3→v4. Standard fixture signal-count assertions migrate only from 3 to the approved four-kind multiset.
- The exact four-class baseline selector completed with the expected stale-wire RED; the five failures were stale signal-count assertions and the seven errors were `FLOW_ACCOUNTING_INVARIANT_BROKEN` from old published-wire policy/schema versions.
- The mapped selector compiled and reached the unchanged old M2 writer: 14 tests produced 3 assertion failures and 11 errors, all `EVIDENCE_PROJECTION_INVARIANT_BROKEN` at the capsule projection publisher after the fixture policy moved to v6. No non-wire assertion or fixture-construction failure was observed.
- The formatted-file rerun reproduced the same 14-test RED (3 failures, 11 errors, 0 skipped), confirming the expected old-writer matching-policy gate without changing production.

## Changed files

- `progress/business-flow-wire-handoff-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowsPublicationSpecifierTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompilerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompilerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (only the three matching wire policy literals)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest,BusinessFlowsPublicationSpecifierTest,RegistryProposalTaskCompilerTest,FiniteKeyFlowTaskCompilerTest test` | RED (exit 1) | 14 tests: 5 failures, 7 errors, 0 skipped. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest,BusinessFlowsPublicationSpecifierTest,RegistryProposalTaskCompilerTest,FiniteKeyFlowTaskCompilerTest test` (after mappings) | RED (exit 1) | 14 tests: 3 failures, 11 errors, 0 skipped; all failures/errors are the unchanged `EVIDENCE_PROJECTION_INVARIANT_BROKEN` M2 writer gate. |
| Pinned `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<five absolute owned Java paths> spotless:apply` | PASS (exit 0) | 5 selected files cleaned. |
| Pinned `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<five absolute owned Java paths> spotless:check` | PASS (exit 0) | 5 selected files clean. |
| Exact four-class selector after formatting | RED (exit 1) | 14 tests: 3 failures, 11 errors, 0 skipped; same `EVIDENCE_PROJECTION_INVARIANT_BROKEN` old-writer gate. |

## Decisions

- Preserve every non-wire closure, hash, round/task, privacy, zero-Flow, budget, gap, disposition, and identity assertion.
- Record the pre-migration four-class RED before applying only the published mappings and matching fixture policies.

## Blockers

- None; Java/Maven gate is released for this bounded handoff.

## Exact next action

- Release Maven. The bounded wire-handoff test migration is complete; production must absorb the already-published M2 writer handoff separately.

## Resume checks

- Do not edit production, source/graph fixtures, other policies, design, Step06, or Git; report any non-wire/non-published-count failure instead of changing it.
