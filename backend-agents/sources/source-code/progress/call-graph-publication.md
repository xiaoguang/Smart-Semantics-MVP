# Progress: call graph publication

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED and Terra/xhigh GREEN under the published Program Graphs M2 contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Persist one verified `CallGraphDraft` as the M2 receipt-last canonical module artifact; no control-flow, data-flow, evidence graph, or analysis-step publication.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `29d72aa`, the M2 M1-to-M2 reopening contract, and the two implementation plans.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Confirmed that M2 has an in-memory `CallGraphDraft`, but no allowed canonical module artifact contract or receipt-last publisher.
- Added the public canonical-store test. Its first run reached the expected compile RED because `CallGraphDraftReference` and `CallGraphModulePublisher` were absent.
- Added the M2 typed reference, receipt-last publisher and Foundation module contract. The publisher records the exact M1 payload reference together with the six source/discovery references and graph profile, never a raw M1 draft or a filesystem path.
- Corrected the test-only policy registry order before the GREEN run; policy identities are canonical-order sensitive.
- The direct publisher selector is GREEN: one M1 module is persisted and fresh-reopened, then the M2 call graph is installed and fresh-reopened with its eight exact upstream references.
- Added the M2 execution RED. It failed only because `CallGraphExecution` was absent; the test requires the execution to obtain its inputs from the persisted-input reader, fresh-reopen M1 through the sealed reader, build, and persist M2.
- Implemented the M2 execution seam. Its direct test now proves exactly one preceding input reopen and one M1 receipt/payload reopen before M2 publication.
- Added a Mapper-method candidate RED. When Stage 2 already identifies a Mapper interface/XML namespace but omits the exact called method candidate, the prior builder silently omitted the Java→XML relationship and emitted no Gap.
- The builder now distinguishes an ordinary non-Mapper target (no matching Mapper catalog, no Mapper binding claim) from a known Mapper with no candidate for the called method (`MAPPER_JAVA_METHOD_UNRESOLVED` Gap). The direct M2 builder selector is GREEN.
- Added deterministic replay and graph-profile mismatch checks. The same sealed M1/reopened aggregate yields an equal `CallGraphDraft`; a different call-graph profile is rejected with `GRAPH_REFERENCE_BROKEN` before call resolution.

## Current state

- The independent M2 publisher and M2 execution slices are GREEN. M2 still needs the remaining receipt mutation/determinism coverage and Mapper binding accounting before it can be accepted.

## Changed files

- `progress/call-graph-publication.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphDraftReference.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphExecution.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | RED | Test compilation fails only because `CallGraphDraftReference` and `CallGraphModulePublisher` are absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | PASS | 1 test, 0 failures/errors/skips; M2 receipt records the source/discovery/profile and fresh M1 payload lineage. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | PASS | 2 tests, 0 failures/errors/skips; execution fresh-reopens M1 before it builds and installs M2. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | 6 tests, 1 failure: a known Mapper with no called-method candidate silently produced no Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 6 tests, 0 failures/errors/skips; known incomplete Mapper catalogs now produce the scoped unresolved-method Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 8 tests, 0 failures/errors/skips; deterministic replay and profile-mismatch rejection pass. |

## Decisions

- The publisher is a single M2 module artifact, not a semantic analysis-step publication and not a compatibility format.
- The receipt's M1 predecessor is `ReopenedCodeStructureGraph.payloadRef()`: it is the already-store-verified canonical M1 payload identity, not a caller-supplied path or draft object.

## Blockers

- None.

## Exact next action

- Integrate the published M2→M3 reopening contract, then add the remaining invalid M1/Mapper-binding mutation tests and complete the M2 exit review.

## Resume checks

- Read this file and `progress/program-graph-input-identity.md`, run `git status --short`, then run only the selector recorded above.
