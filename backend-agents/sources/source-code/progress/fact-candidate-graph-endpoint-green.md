# Progress: fact candidate graph endpoint green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make the persisted Fact-candidate input reader fail closed when a non-Evidence program edge points to a nonexistent program node.
- Approved inputs: Scoped `AGENTS.md`; proven-code-facts M1 contract; `FactCandidateGhostEndpointTest` expected RED.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` / `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read scoped instructions, both implementation plans, M1 contract, and the targeted test.
- Confirmed the test mutates only `CALL_TARGET.toNodeId` in a freshly republished complete ProgramGraphs set and expects `PROOF_PACK_REFERENCE_BROKEN` before enumeration.
- Independently reproduced the expected RED: the reader accepted the graph whose call-target endpoint did not exist.
- Added cross-graph endpoint closure for every non-Evidence program edge, including optional guard references.
- Confirmed the targeted GREEN.
- Confirmed the directly related Fact-candidate regressions and scoped formatting check.

## Current state

- The reader now rejects a forged program-edge endpoint before candidate enumeration.
- The next verification is the directly related identity/exact-path/missing-path regression selector.

## Changed files

- `progress/fact-candidate-graph-endpoint-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGhostEndpointTest test` | RED | 1 test; expected assertion failure because `reopen` returned normally. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGhostEndpointTest test` | PASS | 1 test; 0 failures, errors, or skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest,FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest test` | PASS | 4 tests; 0 failures, errors, or skips. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java' spotless:check` | PASS | Scoped Java formatting check succeeded. |
| `git diff --check` | PASS | No whitespace errors in tracked diff. |

## Decisions

- Treat a missing endpoint as a malformed public ProgramGraphs input: fail closed with `PROOF_PACK_REFERENCE_BROKEN`, not a normal candidate disposition.
- Include optional guard endpoints when present; exclude Evidence graph edges from this program-node closure check.

## Blockers

- None.

## Exact next action

- Parent agent can include this bounded reader change in the M1 review gate; do not commit this subtask independently.

## Resume checks

- Read this file, inspect `git status --short`, and run `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateGhostEndpointTest test` before editing production code.
