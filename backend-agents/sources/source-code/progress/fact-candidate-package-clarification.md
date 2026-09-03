# Progress: Fact candidate package clarification

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Resolve one local Step 04 package-location ambiguity before production implementation. This is documentation only.
- Approved inputs: Existing `04-proven-code-facts` design, target package registry, and the first candidate-enumerator RED review.
- Current branch/worktree: `codex/source-analysis-fact-candidates-design` at `/private/tmp/linguan-source-analysis-fact-candidates-design`.

## Completed

- Identified that a filesystem reference to `analysis/fact/candidates/` did not explicitly state whether the public M1 types live in `analysis.fact` or the child package.

## Current state

- The detailed design now makes one interpretation explicit: M1 public candidate types belong to `org.sourceanalysis.app.analysis.fact.candidates`; later Fact modules own similarly named child packages.

## Changed files

- `docs/analysis-steps/04-proven-code-facts.md`
- This progress file.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | clean design worktree before this progress record |

## Decisions

- The root `analysis.fact` remains the semantic step boundary; its named M1/M2/M3 submodules use explicit child packages so filenames, Java packages, and ownership agree.

## Blockers

- None.

## Exact next action

- Continue the already-started M1 RED/GREEN work only after this docs-only clarification is published to `origin/main`.

## Resume checks

- Confirm only the detailed design and this progress file changed before committing.
