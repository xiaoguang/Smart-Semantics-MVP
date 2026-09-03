# Progress: Code structure graph Gap carrier GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Upgrade the M1 code-structure graph draft, builder, wire, reader, publisher, and required artifact registration from v2 to v3 so every local M1 Graph Gap has the shared typed carrier.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` M1 and 8.0.1 shared Gap contract; the published design correction at `ae83851`; `CodeStructureGraphGapCarrierTest` RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the root and scoped Agent rules, the M1 detailed design, the shared `GraphGapDraft` contract, the public RED test, and the current M1 production seam.
- Confirmed the RED is specific: the current M1 draft is schema v2 and has only `GraphGapDisposition`, so it cannot carry a typed local Gap.
- Upgraded `CodeStructureGraphDraft` to v3 with sorted, immutable `gapDrafts`, typed Gap/coverage bidirectional closure, owner-entry validation, and a framed canonical graph identity that includes every Gap field and locator.
- Changed the M1 builder so each current local Java/XML/configuration Gap creates a `GraphGapDraft` from a verified document span, reuses its ID in coverage, and binds sorted Gap records into the graph ID. YAML and dynamic-SQL failures use their known line/statement spans; whole-document parser failures use the validated complete-file span.
- Added strict `gapDrafts` write/read, source locator bounds checks, module receipt `gapRefs = local typed gaps ∪ scope gaps`, and v3-only artifact-contract registration. Updated affected M1 fixture construction for the record shape and canonical graph identity.

## Current state

- The scoped M1 v3 carrier slice is green. Existing M2/M3/M4/M5/M6 worktree changes were preserved; no design, M2/M3 semantic, M4/M5/M6, or Luna RED-test logic was changed.

## Changed files

- This progress file.
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/PersistedCodeStructureGraphReader.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (M1 v3 contract registration only)
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphModulePublisherTest.java` (required v3 constructor/identity fixture update)
- `src/test/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphBuilderTest.java` (required v3 record field at an existing M1-draft reconstruction call site)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphGapCarrierTest test` | RED | 1 test, 1 failure, 0 errors/skips: current `CodeStructureGraphDraft` reports v2 instead of the required v3 carrier contract. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | M1 production and target test sources formatted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphGapCarrierTest test` | PASS | First GREEN: 1 test, 0 failures/errors/skips; 238 main and 46 test sources compiled. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphGapCarrierTest test` | PASS | Repeat GREEN: 1 test, 0 failures/errors/skips. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Each local M1 parse/configuration Gap reuses the same `gapId` in coverage and the shared carrier. The carrier owns reason, whole-discovery affected entries, candidate, and an exact verified source locator.
- Whole-file Java/XML parser failures use a validated full-file span. Local YAML and dynamic-SQL failures retain their known line or statement span; no locator is recovered through a later string search.
- `CodeStructureGraphDraft.requireIdentity` is invoked by publication and fresh reopen. The public RED observes the builder; persisted v3 malformed/old schemas will be rejected by the exact reader field/schema checks.

## Blockers

- No implementation blocker. The broader Step 03 M6 projection work remains outside this slice.

## Exact next action

- Return this completed vertical slice to the Stage 03 integrator; next work is M2/M6 integration outside this ownership scope.

## Resume checks

- Re-open this progress file, check `git status --short`, and rerun only `CodeStructureGraphGapCarrierTest` if a later M1/M6 integration edit changes the v3 wire.
