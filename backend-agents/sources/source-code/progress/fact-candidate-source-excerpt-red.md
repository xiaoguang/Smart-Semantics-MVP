# Progress: fact-candidate source excerpt integrity RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded M1 RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add only persisted SOURCE_EXCERPT byte-hash and locator-invariant RED coverage for Fact M1.
- Approved inputs: `AGENTS.md`, the two implementation plans, `docs/analysis-steps/04-proven-code-facts.md`, the M1 review, and `ProgramGraphsPublicFixture.republishMutatedGraphs`.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Created this task progress file before modifying the test tree.

## Current state

- The focused test is added. It mutates only one persisted source excerpt at a time, rebuilds the Evidence graph and graph index with the public fixture helper, and invokes a fresh Fact input reopen before enumeration. Both malformed mutations currently pass through the reader, so the intended RED is established.

## Changed files

- `progress/fact-candidate-source-excerpt-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSourceExcerptIntegrityTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing uncommitted M1 work preserved before this task. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateSourceExcerptIntegrityTest test` | RED | 1 test, 1 failure, 0 errors/skips; both changed-bytes and invalid-locator assertions report a null throwable. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSourceExcerptIntegrityTest.java' spotless:check` | PASS | Focused formatting check. |
| `git diff --check` | PASS | No tracked whitespace errors; untracked test was checked with `git diff --no-index --check`. |

## Decisions

- Require `FactCandidateReferenceException` with `PROOF_PACK_REFERENCE_BROKEN` for both a changed `rawUtf8` whose declared hash is unchanged and an invalid repository-relative locator/range.
- Keep the test entirely on persisted public graph inputs; no Fact JSON, graph drafts, external behavior, production code, design, POM, or existing test changes.

## Blockers

## Exact next action

- Parent agent may dispatch the corresponding Terra GREEN fix; this RED task is complete and must not modify production code.

## Resume checks

- Confirm the test changes only the new source-excerpt RED file and that the production tree remains untouched.
