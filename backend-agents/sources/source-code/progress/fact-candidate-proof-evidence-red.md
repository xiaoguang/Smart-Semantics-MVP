# Progress: fact-candidate-proof-evidence-red

- Status: COMPLETE
- Agent role: Luna/xhigh M1 RED-test author for the corrected M1→M2 evidence handoff
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test proving that an applicable Java-boundary candidate preserves the complete subject-evidence closure and typed Evidence node payload needed by M2.
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/04-proven-code-facts.md`, both files under `docs/plans/`, `progress/TEMPLATE.md`, and the real persisted ProgramGraphs fixture/Fact M1 seams.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read the required repository instructions, Step 04 M1/M2 contract, both implementation plans, and the progress template before editing.
- Added one focused public-seam test using the real persisted `ProgramGraphsPublicFixture`, `PersistedFactCandidateInputReader`, and `FactCandidateEnumerator`. The test derives the expected call-site/boundary/target/argument-edge/local-origin/control/guard subject set from the candidate and checks complete public Evidence payloads through `FactCandidateInputs.evidenceGraph().nodesById()`.

## Current state

The test is expected to be RED because the current M1 candidate closure omits call-site and Java-local-origin subjects, while `FactCandidateInputs.EvidenceNode` exposes only a boolean source marker and no rule-application payload accessor.

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateProofEvidenceHandoffTest.java`
- `progress/fact-candidate-proof-evidence-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateProofEvidenceHandoffTest test` | RED | Test compilation succeeded; 1 test ran with 1 assertion failure and 0 errors. The actual candidate subject IDs omitted the required `CALL_SITE` (`candidate.invocationCallId()`) and Java-local-origin node, while retaining boundary/argument-edge/control/call-target subjects. Toolchain selected JDK 17 and Maven ran offline. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateProofEvidenceHandoffTest.java progress/fact-candidate-proof-evidence-red.md` | PASS | No whitespace errors. |

## Decisions

- The test asserts the published M1 subject closure from candidate accessors and includes the call-target edge required by the Step 04 atom-to-evidence rule; it does not assert any XML/SQL or external effect.
- Evidence payload checks deliberately use only public reflection (`Class.getMethod` and invocation) for the newly required `sourceExcerpt()` and `ruleApplication()` accessors. The source branch requires a complete `SourceExcerptV1`; the rule branch requires public `ruleId`, `ruleVersion`, and `inputProgramElementIds` values.

## Blockers

- Current production M1 handoff is incomplete. Terra must extend `FactCandidateInputs.EvidenceNode` to retain typed `SourceExcerptV1`/rule-application payloads from the persisted v3 Evidence graph, expose public accessors for both union branches, and update the reader/closure projection without dropping metadata. Terra must also add call-site and every Java-local-origin node as subject-evidence bindings in `FactCandidateEnumerator`, preserving the exact denominator and candidate identity.

## Exact next action

Terra/xhigh implements only the M1 candidate-input/closure correction, then reruns this selector; after it is GREEN, Luna can author the separate M2 `AtomicProofBuilder` RED.

## Resume checks
