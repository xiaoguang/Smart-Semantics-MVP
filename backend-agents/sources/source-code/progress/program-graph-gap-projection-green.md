# Progress: M6 program graph Gap projection GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Make the M6 public graph-set publisher project validated local `GraphGapDraft` carriers into `graph-gaps.jsonl`.
- Approved inputs: `ProgramGraphGapProjectionTest` decisive RED; `docs/analysis-steps/03-program-graphs.md` M6 contract; frozen M1–M5 persisted graph drafts.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, the existing RED progress record, and the M6 public-seam test.
- Confirmed the expected RED is the old M6 rejection of a valid control-flow Gap carrier.

## Current state

- The production publisher installs the complete M6 set after accepting the real M3 carrier. The
  Luna owner corrected its JSONL setup; the requested repeat selector is green.

## Changed files

- `progress/program-graph-gap-projection-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | No files needed formatting. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphGapProjectionTest test` | RED (initial) | Valid M3 carrier rejected by legacy `requireNoUnprojectableGaps`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphGapProjectionTest test` | BLOCKED after GREEN | M6 installs; test fails at line 251 because it calls `parseCanonical` on `graph-gaps.jsonl`, a JSONL payload with trailing LF. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphGapProjectionTest test` | PASS | 1 test, 0 failures/errors/skips; required repeat after Luna's test-only JSONL correction. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Scope-level Gap references remain receipt/accounting references; only local graph carriers become public `GraphGap` entries with locators.
- No M1–M5 builders, tests, or target design will be edited.
- The public graph payload prefixes now match the installed artifact-policy registry: code structure and call retain their registered `program-graphs-*` prefix; control-flow and data-flow use their registered short prefixes.

## Blockers

- None.

## Exact next action

- Hand the bounded M6 GREEN result back to the parent Stage03 owner; do not widen this task.

## Resume checks

- Reopen the M6 implementation and confirm graph nodes/edges are only read, never added or reparsed.
- Keep mixed-M1–M4 Gap coverage owned by its dedicated test slice; this task covers the real M3
  local carrier only.
