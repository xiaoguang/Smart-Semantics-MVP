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

## Current state

- The independent M2 module-publisher slice is GREEN. M2 still needs its execution seam (fresh M1 reopen → builder → M2 publisher) and the remaining mutation/determinism coverage before its module can be accepted.

## Changed files

- `progress/call-graph-publication.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphDraftReference.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphModulePublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | RED | Test compilation fails only because `CallGraphDraftReference` and `CallGraphModulePublisher` are absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphModulePublisherTest test` | PASS | 1 test, 0 failures/errors/skips; M2 receipt records the source/discovery/profile and fresh M1 payload lineage. |

## Decisions

- The publisher is a single M2 module artifact, not a semantic analysis-step publication and not a compatibility format.
- The receipt's M1 predecessor is `ReopenedCodeStructureGraph.payloadRef()`: it is the already-store-verified canonical M1 payload identity, not a caller-supplied path or draft object.

## Blockers

- None.

## Exact next action

- Add the M2 execution test: it must fresh-reopen M1 through `PersistedCodeStructureGraphReader`, build calls, then publish M2 once; no caller may inject a raw M1 draft.

## Resume checks

- Read this file and `progress/program-graph-input-identity.md`, run `git status --short`, then run only the selector recorded above.
