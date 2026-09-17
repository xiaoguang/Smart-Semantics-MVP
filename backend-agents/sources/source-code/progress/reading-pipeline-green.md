# Progress: Reading pipeline GREEN

- Status: READY_FOR_COORDINATOR_VERIFICATION
- Agent role: Step07 cross-object reading pipeline implementation owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: `DefaultBusinessProcessDiscovery`, prompt catalog/resources, request carrier, one-shot reading-decision persistence, and current reading-module design status
- Owning plan: `docs/supplements/cross-object-process-reconstruction/README.md`
- Approved inputs: `module-design.md`, `prompts.zh-CN.md`, `acceptance.md`, `BusinessProcessReadingPipelineTest`, and the established frozen corpus/request carrier
- Current branch/worktree: `codex/cross-object-process-reading`; formal source-code checkout

## Completed

- Foundation GREEN is complete: `FrozenProcessSourceCorpusTest` passed 2/2 in the coordinator's Java 17 run.
- Pipeline RED was established: `BusinessProcessReadingPipelineTest` failed 2/2 because the implementation reran the old catalog path.
- The request carrier now carries optional paired verified inventory/reader, saved catalog bytes, and focus question without changing legacy constructors.
- The coordinator independently verified `ProcessReadingDecisionStoreTest`: 4/4 pass.
- Saved catalog input now reopens the canonical completed v2 pair and bypasses old catalog work. A new repository still discovers its initial catalog before it enters the same selection path.
- Selection/check use the approved detailed Chinese v1 instructions and typed schemas. They receive only lightweight global Activity navigation; full Activities and source excerpts appear only in the candidate packet.
- Selection context is carried through the one reading check into the packet. Context remains non-member for ActivityUse/coverage but may contribute actual statement/source evidence.
- DRAFT/REVIEW v3 consume one non-duplicated `readingPacket`; the response contract no longer has `requestedSourceRefs`, and REVIEW additionally receives the actual DRAFT. After all packets have completed their checks, new packet-local source refs are deterministically mapped by actual file/range/text before parsing, ID construction, and consolidation; persisted raw responses remain unchanged alongside their local-to-final map.
- Formal execution saves/reuses `process-reading-decision-v1` records and uses the existing bounded job pool for checks and process pairs under stable full-catalog ordinals. Samples prepare only their selected candidates.

## Current state

- The unified flow is selection → actual requested reads → one candidate check → one optional supplemental-read pass → packet → DRAFT/REVIEW → consolidation.
- An Activity's complete reviewed text is always included when selected; its M10 refs are only navigation until an explicit `SOURCE_REF` request succeeds. File reads without a frozen reader become packet limitations, never the former DRAFT-then-read route.

## Changed files

- `progress/reading-pipeline-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPromptCatalog.java`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/process-material-selection-v1.txt`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/process-reading-check-v1.txt`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/business-process-draft-v3.txt`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/business-process-review-v3.txt`
- `src/main/java/org/sourceanalysis/app/runtime/modeljob/PrivateModelJobResultStore.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Java 17 `FrozenProcessSourceCorpusTest` (coordinator) | PASS | 2/2 foundation behaviors pass. |
| Java 17 `BusinessProcessReadingPipelineTest` (coordinator) | EXPECTED RED before this slice | 2/2 behaviors failed at the old catalog rerun. Coordinator owns the GREEN rerun. |
| Java 17 `ProcessReadingDecisionStoreTest` (coordinator) | PASS | 4/4 API/store behaviors pass. |
| `git diff --check` | PASS | No whitespace errors after the core slice. |

## Decisions

- The old catalog bytes are input only: reopen and validate the completed catalog pair, do not rerun catalog model work.
- Every candidate takes exactly one reading-check decision after initial reads, with at most one actual supplementary read pass.
- DRAFT and REVIEW consume the same self-contained reading packet; source requests are removed from their response contract.
- Candidate ordinal remains the full-catalog ordinal for provider binding and sample reconstruction.
- No source excerpt is admitted by Activity ownership alone; context enables evidence but never membership.

## Blockers

- The PipelineProvider fixture must consume `readingPacket.reviewedActivities` and request M1/M2 explicitly, since the production packet correctly stopped auto-copying every Activity-linked M10 excerpt. Luna/root owns that fixture-only alignment; existing observable assertions remain unchanged.

## Exact next action

- Ask the coordinator to run the named Java 17 `BusinessProcessReadingPipelineTest` after the fixture alignment; do not start Maven from this task.

## Resume checks

- Preserve all unrelated worktree changes and never edit the RED fixtures in this slice.
- Ask the coordinator to run the single serialized Maven command after the implementation is ready.

## Plan closeout destinations

- Durable decisions: `docs/supplements/cross-object-process-reconstruction/module-design.md`
- Remaining issues: `docs/supplements/cross-object-process-reconstruction/acceptance.md`
- Verification and output references: `docs/supplements/cross-object-process-reconstruction/delivery.md`
