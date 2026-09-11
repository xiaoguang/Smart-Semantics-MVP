# Progress: process explainer contract

- Status: COMPLETE
- Agent role: Root coordinator; Step 07 M1 process reconstruction and checkpoint
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Define the minimal public ProcessExplainer typed seam needed to turn reviewed local activities into model-reviewed business processes. The scope excludes old process-carrier modules, runtime/HTTP wiring, real model calls, and final Markdown.
- Approved inputs: Scoped `AGENTS.md`; `docs/DESIGN.md`; Step 07 detailed design sections 1–3.11; current Step 06 ActivityExplanationResult.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the active design requires a single deep ProcessExplainer, not an extension of the legacy `analysis.interpretation.process` protocol.
- Added the typed ProcessExplainer seam, complete typed process result and separate Chinese DRAFT/REVIEW Prompt resources.
- Added scripted three-activity verification: Java groups by shared material only; the Provider proposes and reviews the business stage sequence; the result preserves the confirmation that the organisation may not require that sequence.
- Added bounded overlapping recall groups: an oversized connected component carries its boundary activity into the next model package, while a hard group limit records only activities that never entered any package as unmatched.
- Added a durable M1 checkpoint through the canonical store: `business-processes.jsonl`, `process-coverage.json`, and `repository-business-knowledge.json` fresh-reopen from the Step 06 activity checkpoint.
- Added the one permitted repository-level consolidation pass. With a positive bounded summary limit, it executes exactly one clean repository DRAFT plus one REVIEW after group reviews; it retains complete activities/processes and saves only a navigation summary, goals, relations, topics, refs and explicit non-consolidated process IDs.

## Current state

- The scripted Provider core is usable with a persistent Step 06 input. Process grouping and its one permitted repository-level DRAFT/REVIEW consolidation are complete. Real Luna/high, per-group partial persistence, and final runtime/CLI publication remain outside this work unit.

## Changed files

- `progress/process-explainer-contract.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/*`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/*`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/*`
- `src/main/java/org/sourceanalysis/app/artifact/AnalysisStepModuleAddress.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Step 06 direct activity selectors | PASS | 4 tests; 0 failures/errors/skips. |
| ProcessExplainer scripted selectors | PASS | 3 tests; one complete process, Chinese Prompt contract, and bounded overlapping group coverage. |
| Process checkpoint selector | PASS | 1 test; 3 canonical files fresh reopen. |
| `RepositorySummaryTest` | PASS | 1 test; repository DRAFT/REVIEW sees clean activities/processes and REVIEW receives the actual DRAFT. |

## Decisions

- ProcessExplainer consumes reviewed activities and their coverage directly. It needs short ref IDs for model validation, but it does not require Java to reread source or resolve ref locations.
- The result keeps the entire reviewed activity set as well as process hypotheses, so later report generation never has to reconstruct business details from process names.
- The persistent M1 checkpoint has one module address (`repository-knowledge/process-explainer`), not the retired admission/merge/publish path.

## Blockers

- None.

## Exact next action

- Start the narrow runtime/CLI orchestrator that supplies persisted Step 05/06 inputs; do not create a second semantic interface.

## Resume checks

- Re-read this file, verify Step 06 activity selectors, and do not import or extend legacy process-carrier packages.
