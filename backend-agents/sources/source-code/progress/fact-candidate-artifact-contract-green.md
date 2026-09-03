# Progress: Fact candidate artifact contract green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Register only the existing M1 candidate-set artifact contract in the canonical module-publication engine.
- Approved inputs: Scoped `AGENTS.md`; `docs/analysis-steps/04-proven-code-facts.md` M1 contract; both implementation plans; existing FactCandidate module-artifact RED.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read the scoped rules, M1 contract, existing module-store contract table, and the public candidate-module RED.
- Registered the sole M1 candidate-set artifact mapping in the canonical module-publication engine.
- Confirmed the existing public publisher test first fails at the absent Foundation contract, then passes after the closed mapping is added.

## Current state

- The canonical store now accepts exactly one M1 candidates-module payload: `fact-candidate-set.json` with the fixed artifact type, schema, address, and `MODULE_ARTIFACT_JSON` envelope.

## Changed files

- `progress/fact-candidate-artifact-contract-green.md`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleArtifactTest test` | RED then PASS | Before mapping: 1 failure, `FACT_CANDIDATE_MODULE_PUBLICATION_FAILED` caused by `MODULE_INSTALL_REQUEST_INVALID`; after mapping: 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java spotless:check` | PASS | BUILD SUCCESS |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Keep the semantic candidate serialization and public publisher outside this slice.
- Register exactly one `MODULE_ARTIFACT_JSON` payload at `PROVEN_CODE_FACTS / 01-candidates / fact-candidate-set.json`.
- The public candidate-module RED is the direct behavior acceptance for this Foundation mapping; no duplicate store test was added.

## Blockers

- None.

## Exact next action

- Return this completed Foundation contract to the M1 publisher and parent integration work.

## Resume checks

- Read this file, run `git status --short`, and verify no other agent is running Maven before the direct selector.
