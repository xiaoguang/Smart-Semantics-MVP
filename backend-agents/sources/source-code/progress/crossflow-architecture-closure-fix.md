# Progress: cross-flow architecture closure fix

- Status: COMPLETE
- Agent role: sole Sol/ultra design authority for final documentation closure
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-08 11:06:25 NDT
- Last updated: 2026-09-08 12:23:20 NDT
- Scope: documentation-only repair of every P1/P2 finding in the final architecture review; no Java, tests, schemas, fixtures, runtime artifacts, source scans, model calls, push, or PR
- Approved inputs: final review report, approved cross-flow requirements, current source-code Agent instructions, target design/detail documents, implementation plan, graph backlog, README, and read-only current implementation inspection
- Current branch/worktree: `codex/source-analysis-process-reconstruction-design` in `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the final review, approved cross-flow requirements, and scoped Agent instructions.
- Confirmed the worktree was clean before this progress file was created.
- Read the overall design, Steps 03–08 contracts, delivery plan, graph backlog, README, and exact Step 03/04 public artifact catalogs.
- Audited the current Java tree read-only: ProgramGraphs M1–M6, ProvenCodeFacts v2 M1–M3, BusinessFlows M1–M3, and local FlowInterpretation M1–M5 exist; RepositoryKnowledge, NineSectionDocument, public runtime, and adapters remain skeletons.
- Closed all seven P1 and both P2 findings in the active contracts while preserving eight steps, nine sections, the evidence/model trust boundary, the seven-method public Interface, and the 57-output count.
- Mirrored the exact Step 06/07/08 record catalogs and identity tables between their detailed designs and `docs/DESIGN.md`; the graph-only backlog needed no cross-flow contract edit.

## Current state

- The closure design, instructions, implementation plan, and current-maturity index are synchronized, verified, and committed as a documentation-only change.

## Changed files

- `AGENTS.md`
- `README.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/plans/source-analysis-naming-and-delivery-plan.md`
- `progress/crossflow-architecture-closure-fix.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | clean branch, ahead of `origin/main` by five commits |
| read-only Java/source catalog inspection | PASS | exact current modules and v2 ProvenCodeFacts publisher/schema constants observed; no tests or customer sources executed |
| targeted output/accounting checks | PASS | eight semantic rows total 47; Step 06 has 15 files; total remains 57 |
| targeted ownership/model-boundary checks | PASS | `A`/`S`, no-model dispositions, deterministic partition, path-free packet, request/hash equality, and 12 per-hypothesis P2 subset sets are explicit |
| targeted lineage checks | PASS | exact Step 03/04/05 filenames, counter transformation, plural claim mapping, disposition hop, NOT_RUN branch, and legal SOURCE_EXCERPT variant all present; stale names absent |
| detailed/overall mirror checks | PASS | Step 06 and Step 07 wire catalogs plus identity tables, and Step 08 Trace catalog, match `docs/DESIGN.md` after comment normalization |
| `git diff --check` | PASS | no whitespace errors |
| documentation commit | PASS | nine documentation/progress paths committed with subject `docs: close cross-flow architecture contracts` |

## Decisions

- Preserve the eight steps, fixed nine sections, evidence/model trust boundary, public seven-method Interface, and exact 57-output accounting.
- Treat the final review as the exact acceptance contract and make only bounded local/inter-module documentation corrections.
- Define `A` as every deterministic process ownership shard and `S` as only the model-safe subset; every shard gets one persisted disposition, while only `S` receives P1/P2 tasks.
- Persist path-bearing process material only for deterministic validation and derive a separate exact path-free `ProcessModelPacketV1`; only canonical `ProcessModelRequestV1` bytes cross the Provider seam.
- Make P2 monotone over every hypothesis evidence/reference set; only deterministic review gaps may be newly attached to the review record and cannot support a claim.
- Use one `BusinessProcessTaskShardV1`, one process request schema, one request digest equality, and one acyclic semantic materialization order in all mirrors.
- Derive blocking counters from existing Step 05 counter kinds by a closed rule, and preserve plural claim keys as plural canonical arrays in Step 07.

## Blockers

- None.

## Exact next action

- None for this task. The final fix report is written at the review workflow's requested path; later work is implementation against the closed contracts.

## Resume checks

- Re-read this file and the final review report.
- Run `git status --short --branch` and confirm only this task's documentation changes are present.
- Do not run Maven, tests, customer-source scans, model calls, network operations, push, or PR actions.
