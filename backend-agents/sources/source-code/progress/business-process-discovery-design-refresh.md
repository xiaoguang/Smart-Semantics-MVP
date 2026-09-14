# Progress: business-process-discovery-design-refresh

- Status: COMPLETE
- Agent role: primary design editor
- Model: GPT-5
- Started: 2026-09-14
- Last updated: 2026-09-14
- Scope: Refresh all active Source Code Analysis Agent design documents for the approved business-process discovery and reconstruction design; add a bounded implementation-delta supplement; do not modify production code, tests, schemas, or runtime artifacts.
- Approved inputs: user-approved design discussion; current main at `6c764c9`; saved 326 reviewed Activities; saved JDT/source corpus and current 340 singleton-process result as implementation evidence.
- Current branch/worktree: `main` in `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Read repository, backend, and source-code scoped instructions.
- Confirmed formal checkout is on `main` at `6c764c9`, matching `origin/main`.
- Audited the active design, current Java ownership and saved jshERP artifacts before changing the target documents.
- Verified the actual baseline: 326 reviewed Activities; 275 with nonempty formulas/metrics; 340 singleton Process records covering only 325 distinct Activities; one omitted Activity while `unmatchedActivityIds=[]`.
- Reframed Step07 around two deep Interfaces: `BusinessProcessDiscovery` and `BusinessProcessPublisher`.
- Added detailed designs for the frozen corpus, full-repository catalog, candidate material assembly, detailed process reconstruction, repository consolidation and deterministic process publication.
- Defined the no-domain-dictionary, two-pass model route: compact whole-repository cards for discovery, then complete selected Activities and requested saved source snippets for detailed reconstruction.
- Added `ActivityUse`, detailed stage/rule/certainty contracts, `CatalogKnowledgeItem`, direct Activity knowledge retention, coverage closure and fatal/PARTIAL behavior.
- Made `business-processes.md` the primary process-quality artifact; Step08 now consumes the complete consolidated catalog rather than rediscovering processes from raw Activities.
- Updated all active step, engine, prompt, execution, persistence, navigation and walkthrough documents, and marked incompatible historical plans as superseded navigation.
- Added the approved implementation-delta supplement for the next planning turn.
- Completed an independent ultra-level architecture review and corrected its findings: standalone knowledge retention, fatal publication boundary, real single-Activity/multiple-ActivityUse sales identity, and two exact Activity statement indexes.

## Current state

- The approved target is now consistently represented across active design documents.
- Current and target states remain explicit: JDT/source collection and 326 Activity DRAFT/REVIEW results are reusable and implemented; current singleton Process grouping is a measured RED baseline; the new Step07 process-discovery Modules and Step08 catalog-only input are not yet implemented.
- No production Java, tests, schemas or runtime artifacts were modified in this work item.

## Changed files

- `CONTEXT.md`, `README.md`, `AGENTS.md`, `docs/DESIGN.md`.
- All eight active `docs/analysis-steps/*.md` designs.
- `docs/modules/business-process-discovery/*.md` detailed Module designs.
- `docs/modules/model-job-execution.md` and active Java engine designs.
- `docs/references/semantic-interpretation-prompts.md` and active publication/persistence references.
- Both active walkthroughs and applicable supplements/tool guidance.
- `docs/plans/business-process-discovery-and-reconstruction-change-design.md` as the bounded implementation-delta supplement.
- Historical plans only received clear superseded/completed navigation banners where needed; their old contracts were not revived.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only pre-existing untracked root `docs/research/` before this progress file |
| `git log -5 --oneline --decorate` | PASS | `main` and `origin/main` both at `6c764c9` |
| Saved artifact counts (`wc`, `jq`) | PASS | 326 Activities; 275 with formulas; 340 Processes; 340 singleton Activity/Stage; 325 distinct members |
| Activity/Process set difference and knowledge read | PASS | exact omitted Activity found; persisted `unmatchedActivityIds=[]` confirmed |
| active Markdown relative-link check | PASS | no missing relative links outside historical documents |
| Markdown fence parity | PASS | no active Markdown file has an unclosed fenced block |
| `git diff --check` | PASS | no whitespace errors |
| independent ultra design review | PASS | no remaining P0/P1; final two incorrect sample statement indexes corrected |

## Decisions

- Existing JDT/source artifacts are the authoritative technical corpus; no rescan is part of this design refresh.
- Reviewed Activities are the semantic index and candidate-discovery input, not the sole source for detailed process rules.
- Business-process discovery and reconstruction become the primary semantic outcome; the nine-section report remains a downstream presentation.
- Java performs identity, retrieval, validation, coverage, scheduling and deterministic publication; LLMs perform domain discovery, candidate membership, ActivityUse, process order/branches and business-language interpretation.
- SUPPORT/STANDALONE/UNCLASSIFIED Activities retain their existing object/formula/question knowledge through a deterministic Discovery-owned projection; no fake Process and no Publisher-side corpus read.
- PARTIAL requires closed, valid semantic dispositions. Provider transport/runtime/schema failure is fatal and blocks formal catalog publication and Step08 for that batch.
- The real generic jshERP Activity `activity:c7f1d276…` is represented once in candidate membership and can yield several process-specific ActivityUse records; the design does not invent separate Activity identities for its variants.

## Blockers

- None.

## Exact next action

- In a later user-requested planning turn, derive an implementation plan only from `docs/DESIGN.md`, the detailed `docs/modules/business-process-discovery/` designs and `docs/plans/business-process-discovery-and-reconstruction-change-design.md`. Do not resume a superseded plan or rerun JDT/326 Activity jobs.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm no Java, test, schema, or runtime-artifact file was changed by this docs-only work item.
- Use the current/target tables and exact 326/340 baseline as the first implementation-plan checkpoint.
