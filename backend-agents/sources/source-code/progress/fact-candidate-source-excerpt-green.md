# Progress: fact-candidate source excerpt integrity GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh bounded M1 production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make Fact M1 fresh-reopen validation construct the existing source locator and excerpt value objects for persisted evidence SOURCE_EXCERPT nodes, rejecting malformed locators or byte/hash mismatches.
- Approved inputs: `AGENTS.md`, both implementation plans, `docs/analysis-steps/04-proven-code-facts.md`, `FactCandidateSourceExcerptIntegrityTest`, `SourceLocatorV1`, `SourceExcerptV1`, and `PersistedFactCandidateInputReader`.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read the scoped contract, the focused RED test, and the existing evidence value objects.
- Confirmed the RED is caused by the reader performing syntax-only checks instead of constructing `SourceLocatorV1` and `SourceExcerptV1`.

## Current state

- `PersistedFactCandidateInputReader` now constructs the existing `SourceLocatorV1` and `SourceExcerptV1` from every persisted `SOURCE_EXCERPT`. Their existing invariants reject repository-external paths, invalid ranges/line-columns, and UTF-8 byte/hash disagreement; `reopen` normalizes those failures to `PROOF_PACK_REFERENCE_BROKEN`.
- The focused selector and all six current M1 candidate selectors pass. No source file was reopened and no source-span semantic claim was added.

## Changed files

- `progress/fact-candidate-source-excerpt-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Preserved all pre-existing uncommitted M1 work. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateSourceExcerptIntegrityTest test` | RED (from test author) | Both persisted excerpt mutations reached the reader without `PROOF_PACK_REFERENCE_BROKEN`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateSourceExcerptIntegrityTest test` | PASS | 1 test, 0 failures/errors/skips; both persisted mutations now fail closed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateEvidenceSupportKindTest,FactCandidateIdentityTest,FactCandidateSourceExcerptIntegrityTest test` | PASS | 6 tests, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java,src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSourceExcerptIntegrityTest.java' spotless:check` | PASS | Scoped formatting check. |
| `git diff --check` and untracked progress whitespace check | PASS | No whitespace errors. |

## Decisions

- This slice validates the persisted locator and excerpt bytes/hash only. It deliberately does not reopen source files or judge whether a span semantically supports a Fact; that is M2 Proof work.
- Direct construction of the existing evidence value objects is preferred over reproducing their path/range/hash rules in the Fact reader.

## Blockers

## Exact next action

- Parent agent may include this bounded GREEN slice in the M1 review gate; this task is complete and must not modify further files.

## Resume checks

- Confirm the focused and six-selector outputs above before integrating this task with other M1 changes.
