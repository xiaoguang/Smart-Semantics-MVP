# Progress: Fact candidate graph-profile reference RED

- Status: COMPLETE
- Agent role: Luna/xhigh bounded M1 test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one persisted M1 graphProfileRef lineage test; no production, design, POM, or existing-test changes.
- Approved inputs: M1 persisted-input contract, ProgramGraphsPublicFixture public mutation seam, existing M1 review finding on graphProfileRef/index lineage.
- Current branch/worktree: codex/source-analysis-proven-code-facts / /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Read scoped Agent instructions, M1 design contract, and complete code-review reception guidance.
- Confirmed the public fixture can republish all five graph payloads after a typed canonical JSON mutation.

## Current state

- The test and progress file are added. The expected RED was reproduced: the reader accepted a CALL graph and graph-index carrying a different syntactically valid graph profile reference.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateGraphProfileReferenceTest.java`
- `progress/fact-candidate-graph-profile-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGraphProfileReferenceTest test` | RED as expected | 1 test, 1 assertion failure: expected `PROOF_PACK_REFERENCE_BROKEN`, no throwable was raised |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Use a syntactically valid but different `graph-profile` ArtifactReference, mutate only CALL graph and graph-index profile references, and let the public fixture rebuild artifact IDs and graph descriptors.
- Expect `PROOF_PACK_REFERENCE_BROKEN` during fresh reopen; do not modify discovery or treat this as a scoped candidate NotApplicable.

## Blockers

- None.

## Exact next action

- Test and targeted selector are complete; production correction belongs to the Terra GREEN task.

## Resume checks

- Verify only the owned test and progress paths changed (the shared public fixture was consumed but not modified).
- Confirm no production/design/POM/old-test edits and no network/customer Maven/live Provider activity.
