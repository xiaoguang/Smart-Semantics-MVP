# Progress: Fact candidate missing-path RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: One public-seam RED test for persisted ProgramGraphs relation loss in Fact M1.
- Approved inputs: M1 public FactCandidateInputs contract, existing ProgramGraphsPublicFixture, canonical stores.
- Current branch/worktree: codex/source-analysis-proven-code-facts

## Completed

- Read the scoped instructions, target architecture, Fact M1 contract, plans, existing typed M1 seam, and persisted graph fixture.
- Added a single public-seam test and a private test-only republisher. The republisher copies real source/discovery/graph publications into a new run and clears one persisted `ARGUMENT_TO_BOUNDARY` edge's evidence IDs, then updates the canonical data graph and graph-index identities.

## Current state

- The RED test is ready to run. Fact receives only fresh typed source/discovery/program-graph references and the verified-source reader; it never receives a path, draft, or raw graph JSON.
- First targeted compile reached only a Java 21 convenience-method mistake in the new test (`List.getFirst()`); no production or assertion ran. This was corrected to the Java 17 `get(0)` form.
- A first mutation-helper draft used private store reflection. It was discarded before test execution; the current helper uses only public canonical store APIs and copies the real verified semantic payloads.
- The final helper uses a fresh run and public `RunStoreBootstrap`, module store, and analysis-step store. It changes only the persisted data-flow edge's `evidenceNodeIds`, then recomputes the changed graph and graph-index standalone identities before installation.

## Changed files

- progress/fact-candidate-missing-path-red.md
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateMissingPathTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateMissingPathTest test` | RED-COMPILE | New test used `List.getFirst()`, unavailable under Java 17; corrected before test execution. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateMissingPathTest test` | UNEXPECTED GREEN | 1 test, 0 failures/errors/skips; current M1 emits one approve `NOT_APPLICABLE` and one cancel candidate. |
| `git diff --check` | PASS | no whitespace errors |

## Decisions

- Do not weaken or delete this test because the intended RED is already GREEN. The production implementation currently satisfies this narrow persisted-mutation behavior.

## Blockers

## Exact next action

- Parent agent should review the unexpected GREEN and decide whether the next RED must target a different unimplemented M1 closure (for example owner/endpoint mismatch or missing control evidence), rather than changing this assertion.

## Resume checks
