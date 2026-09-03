# M4 graph identity RED audit

Status: BLOCKED — no honest RED available

## Scope and contract check

The requested slice was to prove that `DataFlowGraphDraft.graphId` binds the
complete `DataFlowWorklistAccounting` and `GraphGapDraft` contents, preserving
the rest of the graph shape/coverage while mutating those values. The M4
contract in `docs/analysis-steps/03-program-graphs.md` §8.4 requires graphId
to bind complete worklist accounting and sorted gap drafts.

The current public seam already satisfies this rule: `DataFlowGraphBuilder`
passes both `worklistIdentity(worklist)` and `gapIdentity(gapDrafts)` into its
graphId preimage. `worklistIdentity` includes enqueued IDs, processed IDs, and
`overLimit`; `gapIdentity` includes gap ID, reason, affected entries,
candidate IDs, and the complete source locator. `DataFlowGraphDraft`'s public
canonical constructor accepts an explicit graphId and does not recompute one,
so a constructor-only mutation cannot honestly assert automatic identity
recomputation without changing that public contract. Persisted-reader mutation
would fail through independent rebuilt-draft equality, which would test reader
tamper rejection rather than graphId binding.

## Baseline verification

The required selector was run before any test/prod modification:

```text
$ mvn -t .mvn/toolchains.xml -o -Dtest=DataFlowGraphBuilderTest test
[INFO] Running org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.825 s -- in org.sourceanalysis.app.analysis.graph.DataFlowGraphBuilderTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
Process exited with code 0
```

No test was added because the expected RED cannot be established against the
current implementation without inventing a new graphId recomputation or
validation contract. No production, design, POM, fixture, or existing
progress file was modified.

## Next smallest M4 behavior

The next bounded public-seam RED should cover the missing simple-name
caller-side reaching-definition transfer: the service M1 `PARAMETER` read in
`depotHead.setStatus(status)` must produce `DEF_USE` to the already existing
M4 `ARGUMENT` node, with the M3 activation guard context. This is the
`DEF_USE` row in §8.4 (external M1 `PARAMETER` → M4 `USE` or span-identical
M4 `ARGUMENT`) and the M4 next-slice tests in §8.6 (AST-role read/write
denominator, `USE/ARGUMENT` reuse, and true reaching-definition behavior).
It is distinct from the already-green target-body formal→USE→FIELD and caller
ARGUMENT→FIELD setter facts.

`git diff --check` completed with exit code 0 and no output.
