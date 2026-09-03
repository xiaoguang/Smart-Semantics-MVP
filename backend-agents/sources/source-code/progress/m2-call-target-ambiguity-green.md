# Progress: M2 call-target ambiguity GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement the published bounded M2.1 candidate-set resolution for one direct frozen-Java call site, including the `CALL_TARGET_AMBIGUOUS` local Gap outcome.
- Approved inputs: Scoped `AGENTS.md`; published M2.1 contract in the program-graphs design; Luna’s established `CallGraphBuilderTest` RED; existing M2 public seam and frozen fixtures.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the scoped instructions, delivery/toolchain plans, M2.1 design/backlog, Luna RED record, current M2 builder and public test seam.
- Confirmed that the implementation work has not yet started and that the established RED is expected to distinguish `CALL_TARGET_AMBIGUOUS` from the current `CALL_ARGUMENT_TYPE_UNRESOLVED` path.
- Reproduced the expected public RED before production edits.
- Implemented bounded direct-declaration candidate selection, deterministic visibility filtering, reference/array `null` compatibility, exact endpoint checks, and the single ambiguity Gap path.
- Re-ran the public call-graph selector successfully after the smallest implementation change.
- Formatted the owned source file and ran the directly affected data-flow regression selector.

## Current state

The task is limited to deterministic candidate enumeration for visible direct declarations on one resolved receiver type. It must not infer an external target, depend on source declaration order, or change later program-graph modules.

## Changed files

- `progress/m2-call-target-ambiguity-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | EXPECTED RED | 12 tests; 1 failure, 0 errors. The `null` overload fixture expected `CALL_TARGET_AMBIGUOUS` and received `CALL_ARGUMENT_TYPE_UNRESOLVED`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 12 tests, 0 failures/errors/skips after M2.1 candidate resolution. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java spotless:apply` | PASS | Formatted only the owned builder. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test` | PASS | 17 tests, 0 failures/errors/skips. |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java progress/m2-call-target-ambiguity-green.md` | PASS | No whitespace errors in owned paths. |

## Decisions

- `null` remains a supported actual only for reference/array formal compatibility; primitive formals are excluded.
- Multiple compatible direct declarations produce one entry-owned local `CALL_TARGET_AMBIGUOUS` Gap and no exact call/return pair.

## Blockers

None.

## Exact next action

Parent may review this bounded M2.1 GREEN together with the existing Luna RED. Follow-up work must stay in the published M2 candidate-set matrix or return to Sol/ultra for a design decision.

## Resume checks

- Reopen this progress file and check `git status --short` before editing.
- Confirm the only new failure is the Luna ambiguity assertion, not compilation or fixture setup.
- Preserve existing exact, unresolved, and Mapper-binding paths.
- The bounded implementation does not add widening, boxing, varargs, generics, inheritance, or multi-entry union handling.
