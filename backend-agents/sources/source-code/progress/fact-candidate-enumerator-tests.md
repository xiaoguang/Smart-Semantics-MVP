# Progress: fact-candidate-enumerator-tests

- Status: COMPLETE
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Establish the first public RED seam for deterministic Fact candidate enumeration.
- Approved inputs: Stage 04 design, persisted ApplicationDiscovery and ProgramGraphs contracts, existing graph fixtures.
- Current branch/worktree: codex/source-analysis-proven-code-facts

## Completed

- Added one public-seam RED test under `analysis/fact/candidates` and three canonical JSON fixture resources.
- The test loads reopened ApplicationDiscovery and ProgramGraphs JSON, applies the bounded Fact
  registry, and asserts deterministic output, generic Java boundary atoms, and no SQL/external
  effect candidate.

## Current state

The test is now in the contract-mandated `analysis/fact/candidates` package; fixtures remain
unchanged in meaning, but Terra's first GREEN selector run showed that their bytes were not
canonical. The three fixture files now use the project's byte-ordered compact form with no final
line feed.

## Changed files

- progress/fact-candidate-enumerator-tests.md
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java
- src/test/resources/analysis/fact/candidates/application-discovery-reopened.json
- src/test/resources/analysis/fact/candidates/program-graphs-reopened.json
- src/test/resources/analysis/fact/candidates/fact-registry.json

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateEnumeratorTest test` | PRODUCTION RED | All three fixture resources passed `CanonicalJsonCodec.parseCanonical`; execution then failed in `FactCandidateEnumerator.enumerate` at line 52 with `FACT_PROFILE_INVALID: discovery entries and graph index entries must close`. This is a production implementation failure (`HashSet.equals(List)` closure check), not a fixture/canonical-byte failure. |

## Decisions

- The first RED must fail only because the proposed public Fact seam is absent.
- The test will assert that an invocation leaving Java is a generic boundary candidate and does not imply external SQL/effect execution.

## Blockers

## Exact next action

Implement the public Fact seam in a subsequent Terra GREEN task; do not alter this RED test to
hide the missing production type.

## Resume checks

- Read this file first, then verify only the owned test/progress paths are changed.
- Do not add production Fact types in this task.
