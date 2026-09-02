# Progress: program graph input identity

- Status: IN_PROGRESS
- Agent role: Terra/xhigh implementation repair against the published Program Graphs M1/M2 identity contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Enforce the published M1-to-M2 persisted structure reopening and exact input-basis identity contract.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `e1aa952`, the M1 persisted input boundary, and the M2 `CallGraphInputs` contract.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Verified that the published M2 contract requires the M1 structure draft and its reopened source/discovery inputs to share snapshot, controls, and upstream artifact references.
- Verified that the current M1 draft exposes only snapshot, application profile, and entry IDs. Therefore M2 cannot prove the required controls/reference equality from public values.
- Rebases this work onto the published `e1aa952`/`1ad3f5f` M1-to-M2 reopening contract. The CallGraph public fixture now installs a real M1 module publication in the canonical module store and asks the missing reader to reopen it before builder use.
- Ran the resulting public selector. The expected RED is limited to the absent `ReopenedCodeStructureGraph` and `PersistedCodeStructureGraphReader` types; the test does not bypass the store with a raw M1 draft.
- Implemented the sealed M1 reopening aggregate, exact canonical module reader, strict M1 payload parser, and immutable `ProgramGraphInputBasis`. The CallGraph builder now refuses a different graph profile before parsing any source.
- Re-ran the public real-store fixture: all four CallGraph behavior cases are GREEN after M1 is installed and fresh-reopened through the canonical store.
- Added the real-store controls mutation: the reader rejects an otherwise valid persisted M1 publication when the fresh reopened source has a different toolchain digest. The rejection occurs before the CallGraph builder runs and uses the stable `GRAPH_REFERENCE_BROKEN` error.

## Current state

- The M1-to-M2 input boundary is now closed for the normal persisted path and a controls-mismatch mutation. M2 remains incomplete: it still needs its own module publication, additional Mapper ambiguity coverage, and its exit review before control-flow work may begin.

## Changed files

- `progress/program-graph-input-identity.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips before lineage-hardening test is added. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | RED | Test compilation fails only on absent `ReopenedCodeStructureGraph` and `PersistedCodeStructureGraphReader`; the fixture uses a real canonical M1 module publication. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips: real M1 module publication is fresh-reopened and proven before every static call-graph assertion. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 5 tests, 0 failures/errors/skips: includes the real-store changed-controls rejection. |

## Decisions

- M2 may consume only the sealed value returned by `PersistedCodeStructureGraphReader`; it never accepts a raw M1 draft. Its basis is recomputed from fresh reopened inputs and must equal the receipt and payload lineage.

## Blockers

- None.

## Exact next action

- Add an exact Mapper ambiguity/missing-binding CallGraph negative case, then publish/reopen the M2 call-graph module using this same input boundary.

## Resume checks

- Read this file, run `git status --short`, confirm the published M1-to-M2 reader contract at `e1aa952`, then run the direct targeted selector recorded above.
