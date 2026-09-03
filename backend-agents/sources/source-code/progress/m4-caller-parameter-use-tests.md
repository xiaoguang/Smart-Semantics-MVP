# M4 caller parameter use RED test

Status: IN_PROGRESS

Scope: add one public-seam RED test for the M4 simple-name read contract. The
guarded direct-setter fixture must reuse its existing M4 `ARGUMENT` node and
emit `external M1 PARAMETER → M4 ARGUMENT` as `DEF_USE`, with the exact M3
guard/polarity context for the activated call.

Contract basis: `docs/analysis-steps/03-program-graphs.md` §§8.3 and 8.4
(status argument closure, AST-role `ARGUMENT_SIMPLE_NAME_READ`, and the
`DEF_USE` endpoint matrix).

Changed files planned:

- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `progress/m4-caller-parameter-use-tests.md`

Verification planned:

`mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test`, then
`git diff --check`.

## Follow-up

The controller-argument characterization now derives the expected count of
`data-flow-argument-work-item-v1` entries from admitted argument edges,
requires at least one `data-flow-java-read-work-item-v1` for the simple-name
parameter reads, and rejects any other work-item prefix. It still requires
processed IDs to equal enqueued IDs. Maven verification is intentionally
pending the reader fix to avoid concurrent execution.

## Result

The existing guarded direct-setter public-seam test was tightened to require
the single `ARGUMENT` node for `java-expression-canonical-v1|NAME|status` and
an `EXACT` `DEF_USE` from the external `DepotHeadService#batchSetStatus`
parameter. It also retains the no-second-`USE` assertion and requires the
`DEF_USE` guard node and polarity to match the M3 TRUE setter callsite.

The required selector remains an assertion RED: 9 tests ran with 1 failure
and 0 errors. The failure is precisely the missing parameter-to-existing-
argument `DEF_USE` edge (the filtered edge list is empty at
`DataFlowGraphBuilderTest.java:309`); all other tests passed.

`git diff --check` passed with no output.
