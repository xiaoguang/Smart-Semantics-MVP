# Progress: fact-candidate-identity-red

- Status: COMPLETE
- Agent role: Luna/xhigh bounded RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Prove that FactCandidateSet identity includes every wire-visible candidate semantic while preserving identity under equivalent input ordering. Do not modify production code, design, POM, or existing tests.
- Approved inputs: AGENTS.md, docs/analysis-steps/04-proven-code-facts.md M1 contract, current candidates records, and current M1 progress/review.
- Current branch/worktree: codex/source-analysis-proven-code-facts at /private/tmp/linguan-source-analysis-proven-code-facts

## Completed

- Added one direct typed-seam test covering candidate-set identity closure.
- Confirmed equivalent root/candidate input ordering retains the same identity.
- Confirmed the current production identity returns the same ID after changing required atom, Java-local argument origin, or subject evidence closure; all three semantic assertions are RED.
- No production, design, POM, fixture, or existing-test files were modified.

## Current state

The bounded RED is established. Test design is limited to direct typed `FactCandidateSet.create` seam; it uses no raw JSON, Draft, filesystem path, or legacy POC.

## Changed files

- progress/fact-candidate-identity-red.md

## Verification

| Command | Result | Key output |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest test` | RED (expected) | 1 test; 3 grouped assertion failures: required atom, Java-local origin, and evidence closure mutations all retained candidateSetId; equivalent reorder assertion passed |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateIdentityTest.java' spotless:check` | PASS | owned test is formatted |
| `git diff --check` | PASS | no whitespace errors |
| --- | --- | --- |

## Decisions

- Compare candidate-set IDs for equivalent order permutations and for semantic mutations in required atoms, Java-local argument origins, and subject evidence closure.
- Use a single candidate and a complete denominator with five typed graph roots so failures isolate identity material rather than denominator behavior.

## Blockers

## Exact next action

- Parent Terra agent may implement the smallest candidate identity fix from this RED. This test remains the acceptance guard; do not widen M1 scope in the GREEN task.

## Resume checks

- Confirm only this progress file and the new identity test are changed; confirm the selector does not run unrelated tests.
