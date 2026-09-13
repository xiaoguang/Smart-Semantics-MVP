# Progress: business-report-alignment-audit

- Status: COMPLETE
- Agent role: bounded read-only design/code audit (Renderer source externalization)
- Model: GPT-5
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Baseline commit `080a86db04c4917b27c5a48c88ca136d11bd0f2b`; Step 08 source map, deterministic Renderer, checkpoint reopen/rerender.
- Approved inputs: User-approved independent `source-refs.jsonl`, short body refs, and Chapter 1 source appendix.
- Current branch/worktree: formal checkout at `db28f8d`; pre-existing dirty `.mvn/toolchains.xml`, `AGENTS.md`, `README.md`, docs, and `pom.xml` preserved.

## Completed

- Confirmed the target contract: four report files include `source-refs.jsonl`; the model receives short refs and snippets while file paths/lines remain program-side; Chapter 1 owns the folded source area (`docs/analysis-steps/08-nine-section-document.md:13-19,21-39,41-55,80-93`).
- Confirmed deterministic rendering: `BusinessReportMarkdownRenderer.render` preserves report text, emits fixed H1/H2/paragraph/list structure, turns refs into same-document anchors, and inserts one folded source area immediately after section 1 (`src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportMarkdownRenderer.java:13-37,40-49,52-73`). Source rows contain ref, relative file, exact line range, and HTML-escaped snippet (`:52-80`).
- Confirmed independent persistence: `BusinessReportCheckpointPublisher` writes sorted source records to `source-refs.jsonl` with canonical JSONL bytes and a content-derived artifact id (`src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointPublisher.java:127-155`). Report JSON, Markdown, validation, and source map are installed as four payloads (`:65-94`).
- Confirmed source-map reopen/rerender seam: `BusinessReportCheckpointReader` reopens and validates the four payload descriptors, reconstructs allowed refs from `source-refs.jsonl`, and validates report refs against that set (`src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointReader.java:67-95,110-140,152-207,210-248`). `BusinessReportCheckpointRenderer` re-renders from report JSON plus the source map and rejects byte mismatch with saved Markdown without a Provider (`src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportCheckpointRenderer.java:15-35`).

## Current state

- Renderer externalization is structurally aligned with the approved design: body carries only short refs; source material is persisted independently and displayed in Chapter 1 without adding a tenth H2.
- Relevant quality boundary: publisher/Java checks that returned refs exist in the allowlist (`BusinessReportPublisher.java:387-403`) but does not determine whether a valid ref is semantically relevant to the sentence. The design explicitly assigns that judgment to the complete model REVIEW and keeps Java to structure/ref/budget checks (`docs/analysis-steps/08-nine-section-document.md:32-39`). This is a semantic-review quality condition, not a renderer serialization failure.
- The renderer receives the full request source list (`BusinessReportPublisher.java:84-108`), so the folded appendix can include valid but uncited source rows. The contract calls for the program-side map of referenced short refs; if the product wants an appendix containing only cited refs, the selection rule remains a design decision. No production change was made.
- Broader Step 6–8/runtime audit (partial/checkpoint timing/reuse/process inference) is DEFERRED per the latest scope instruction.

## Changed files

- `progress/business-report-alignment-audit.md` only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS (read-only pre-edit check) | Pre-existing dirty files listed; no production edits made. |
| `git show 080a86db...:<path> \| nl -ba` | PASS | Inspected baseline Renderer, publisher, checkpoint publisher/reader/renderer, SourceReference, and Step 08 design. |
| build/tests/model/network | NOT RUN | Explicitly out of scope. |

## Decisions

- Treat source existence/scope and deterministic byte rendering as verified implementation facts.
- Treat citation relevance and whether to suppress uncited appendix rows as semantic/design follow-up, not a factual runtime claim or an implementation change in this audit.

## Blockers

- None for the bounded Renderer review. The deferred broader audit was intentionally not continued.

## Exact next action

- Parent may use the verified line references above while updating the source-externalization design; no further action in this slice.

## Resume checks

- If broader audit is reopened, re-read the scoped AGENTS and inspect baseline via `git show 080a86db...`; do not infer acceptance from a DRAFT report or alter the dirty checkout.
