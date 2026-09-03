# Progress: fact candidate evidence support green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Make Fact M1 fresh reopening reject Evidence edges whose declared support kind does not match the actual program subject type.
- Approved inputs: Scoped AGENTS.md, published Proven Code Facts M1 contract, existing `FactCandidateEvidenceSupportKindTest` RED, and existing persisted public-graph fixture.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `8dac8bc` in `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read scoped rules, both implementation plans, the M1 contract, RED test, and Fact candidate input/reader seams.
- Confirmed the supplied selector is an expected RED: relabeling a real CALL-edge support as node support is accepted instead of failing closed.
- Preserved the Evidence support kind in `EvidenceEdge`, validated every fresh-reopened Evidence subject against the declared graph's actual node/edge universe, and made candidate closure select the required support kind explicitly.
- Confirmed the supplied selector is GREEN after the minimal change.

## Current state

- The RED changes a real Evidence edge that supports a CALL graph edge from `SUPPORTS_PROGRAM_EDGE` to `SUPPORTS_PROGRAM_NODE`; current reader must fail closed before candidate enumeration.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateInputs.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java`
- `progress/fact-candidate-evidence-support-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateEvidenceSupportKindTest test` | Expected RED | 1 test, 1 assertion failure: reader accepted a `SUPPORTS_PROGRAM_NODE` edge whose subject is a CALL graph edge. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateEvidenceSupportKindTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest,FactCandidateMissingPathTest,FactCandidateGhostEndpointTest,FactCandidateIdentityTest,FactCandidateEvidenceSupportKindTest test` | PASS | 5 tests, 0 failures, 0 errors, 0 skipped. |
| Scoped `spotless:check` and `git diff --check` | PASS | All three owned Java files comply; no whitespace errors. |

## Decisions

- Keep the wire/schema unchanged. Preserve Evidence edge kind in the typed input, validate its subject against the correct public graph universe, and require subject-specific closure to use the matching support kind.

## Blockers

- None.

## Exact next action

- Parent agent may run the combined M1 selector and scoped formatting/diff checks before integrating this slice.

## Resume checks

- Read this file, check `git status --short`, rerun the one selector, and confirm no other agent is running Maven before broader direct selectors.
