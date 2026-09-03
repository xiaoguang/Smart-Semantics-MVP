# Progress: call graph shared Gap carrier GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Implement the published M2 call-graph v3 shared `GraphGapDraft` contract only.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` at published design commit `ae83851`; the Luna RED `CallGraphGapCarrierTest`.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the root and source-scoped rules, both implementation plans, the M2 design contract, the Luna RED, and the existing M2/M3 wire implementations.
- Replaced M2's bare local Gap dispositions with source-located shared `GraphGapDraft` values, kept coverage bidirectionally closed, and bound the v3 graph identity to all ordered Gap fields.
- Upgraded the M2 JSON writer, reader, and module completion gaps to v3. The old persisted v2 schema is rejected by the M2 artifact contract.
- Updated current M2/M3 test construction sites to explicitly supply `gapDrafts`; no compatibility constructor or persisted legacy wire was added.
- Replaced the graph identity's Java collection rendering with framed canonical JSON that includes every ordered local Gap field and source locator.
- Confirmed there is no v2 source-compatibility constructor or M2 v2 schema acceptance in the owned call-graph implementation.

## Current state

- M2 v3 is GREEN for the direct shared-Gap carrier seam and is ready for parent integration with the remaining graph modules.

## Changed files

- `progress/call-graph-gap-carrier-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedCallGraphReader.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisherTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Required formatting completed twice; only owned Java files changed in the final pass. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphGapCarrierTest test` | PASS | Ran twice after the final correction: 1 test, 0 failures/errors/skips each time. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- A Mapper binding Gap uses the already parsed originating service call range as its source locator; no source-text search or synthetic locator is permitted.
- The current M2 record shape is explicit everywhere. Persisted v2 payloads remain fail-closed; no source or wire compatibility path exists.

## Blockers

- None.

## Exact next action

- Parent integration may continue with the M1/M6 shared-Gap work. This slice must not be committed independently.

## Resume checks

- Re-read this file, inspect `git status --short`, and preserve the M2 v3 wire while integrating adjacent graph modules.
