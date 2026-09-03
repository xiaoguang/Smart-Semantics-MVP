# Progress: fact candidate boundary ownership RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded M1 RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Verify that a persisted Java boundary node with no owning discovery entry fails closed instead of silently disappearing from the Fact-candidate denominator.
- Approved inputs: `AGENTS.md`, the two implementation plans, `docs/analysis-steps/04-proven-code-facts.md`, the M1 review, and `ProgramGraphsPublicFixture` public mutation seam.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read the scoped rules, TDD guidance, M1 contract/review, and the existing persisted public graph fixture.
- Added one public-seam test that copies the complete fixture into a fresh run, clears only the `approve` boundary's `owningEntryIds`, recomputes the data-flow and graph-index identities, and reopens through the production Fact reader.

## Current state

- The test is intentionally expected to be RED until M1 rejects an ownerless boundary with `PROOF_PACK_REFERENCE_BROKEN`.
- It does not invoke the enumerator after the malformed input is reopened; accepting the malformed publication is itself the silent-denominator defect.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateBoundaryOwnershipTest.java`
- `progress/fact-candidate-boundary-ownership-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing uncommitted M1 work preserved before edits. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateBoundaryOwnershipTest test` | RED | 1 test, 1 assertion failure, 0 errors/skips; `reopen` returned normally instead of raising `PROOF_PACK_REFERENCE_BROKEN`. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateBoundaryOwnershipTest.java' spotless:check` | PASS | Owned test is formatted. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Treat empty boundary ownership as malformed persisted graph input, because no Fact candidate or scoped disposition can be keyed to an entry and silently filtering it loses the required denominator.
- Keep the mutation in the test-only public graph fixture seam; the Fact reader receives only fresh typed publication references and never raw graph JSON or a filesystem path.
- Do not infer any behavior for SQL, databases, messaging, search, or other external systems.

## Blockers

## Exact next action

- Parent Terra agent should implement the smallest fail-closed ownership check and rerun this selector. No production change was made in this task.

## Resume checks

- Confirm only the two files listed under Changed files are owned by this task; rerun the focused selector before any further edit.
