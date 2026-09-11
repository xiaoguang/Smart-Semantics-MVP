# Progress: semantic priority design

- Status: BLOCKED
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09T14:06:13Z
- Last updated: 2026-09-09T14:19:19Z
- Scope: Docs-only alignment of Steps 05–08 around business-process completeness and tiered evidence precision.
- Approved inputs: User priority that process/semantic completeness outranks byte/line precision; existing eight-step and nine-section architecture.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Read the complete scoped AGENTS contract and the applicable design/domain vocabulary skills.
- Confirmed the worktree contains unrelated implementation and documentation changes that must be preserved.
- Drafted the minimal priority ladder in `docs/DESIGN.md` and `docs/analysis-steps/06-flow-interpretation.md`.
- Drafted the corresponding `EVIDENCE_SUPPORTED_INFERENCE` admission relaxation in `docs/analysis-steps/07-repository-knowledge.md` while preserving exact evidence for `SOURCE_CONFIRMED` and external effects.

## Current state

- The docs-only amendment is present in the shared worktree but remains uncommitted and not statically verified because this task was interrupted.

## Changed files

- `progress/semantic-priority-design.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing dirty worktree inventoried; no unrelated file will be overwritten. |
| Static consistency and `git diff --check` | NOT RUN | Task interrupted before final verification. |

## Decisions

- Preserve the eight-step workflow, fixed nine chapters, 57-output boundary, source-location traceability, and the prohibition against presenting inference as source fact.
- Treat exact byte/hash/line closure as strongest available evidence, not as a universal prerequisite for semantic reconstruction.

## Blockers

- Current design task was explicitly interrupted before verification and completion.

## Exact next action

- Parent design authority should inspect the three uncommitted doc edits, keep or revise the minimal amendment, then run static terminology scans and `git diff --check` before publication.

## Resume checks

- Read this progress file first.
- Re-run `git status --short` and preserve all paths not listed under Changed files here.
- Confirm no Java, test, schema, POM, runtime artifact, network, Maven, or model action has occurred in this task.
