# Progress: data-flow provenance closure

- Status: IN_PROGRESS
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Diagnose and correct the generic data-flow provenance closure failure exposed by the
  explicitly authorized 8 GiB fixed-repository replay. Own only the direct regression fixture,
  data-flow builder correction, this progress record, and the existing fixed-repository preflight
  record.
- Approved inputs: Current Step03 design, public data-flow graph contracts, and fixed jshERP
  commit `8c30ce7861570458920175e200bb2a6442713580`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- The 8 GiB replay completed code structure, call, and control flow construction for all 719
  tracked files, then failed DataFlowGraphDraft closure with exactly one unused provenance record.
- A bounded diagnostic identified it as `java-boundary-argument-v1` at
  `SysDictTypeService.java:80`, a no-argument mapper call.
- Static source review confirms the generic boundary builder creates and imports an argument
  provenance record even when the invocation has zero arguments; no final node or edge can
  reference that record.
- Added a zero-argument mapper boundary fixture. Its direct data-flow test failed at the exact
  provenance closure gate with the same unused `java-boundary-argument-v1` rule.
- The boundary builder now creates argument provenance only when an invocation has at least one
  argument. It retains invocation provenance for every boundary.
- The direct regression and full `DataFlowGraphBuilderTest` selector are green (19 tests).
- The next complete replay passed data-flow draft construction and stopped only when publishing
  its module receipt: one Graph Gap may cover multiple candidate elements, but the publisher
  incorrectly required the receipt's `gapRefs` list to repeat its gap ID once per candidate.
- Extended the existing literal-argument Gap test to publish that graph. It reproduced the same
  publisher error, then passed after the publisher canonicalized its sorted completion list to
  one reference per Gap ID. The full direct data-flow selector remains green (19 tests).

## Current state

- The two generic data-flow corrections are locally complete. The fresh 8 GiB replay then built all
  five graph drafts and reached M6, where the canonical module store rejected the public data-flow
  artifact identity.
- The active fixed-repository policy deliberately assigns `PROGRAM_GRAPHS_DATA_FLOW_GRAPH` the
  prefix `data-flow-graph`; `ProgramGraphSetPublicationSpecifier` still hard-codes
  `program-graphs-data-flow-graph`. The local M6 fixture masked this by configuring the same
  obsolete hard-coded prefix. The policy registry is the authoritative source, so the next bounded
  correction is a policy-driven public-payload identity test followed by resolving all M6 public
  payload prefixes through the trusted module store.

## Changed files

- `progress/data-flow-provenance-closure.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilder.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/DataFlowGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilderTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicationSpecifierTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Fixed repository Step01–05, 8 GiB, retry 14 | Reproduced | 719 files; structure/call/control complete; only unused `java-boundary-argument-v1` at `SysDictTypeService.java:80`. |
| Zero-argument boundary regression before implementation | RED | The direct builder failed provenance closure with an unused `java-boundary-argument-v1` record. |
| Zero-argument boundary regression after implementation | PASS | 1 test, 0 failures/errors/skips. |
| `DataFlowGraphBuilderTest` | PASS | 19 tests, 0 failures/errors/skips. |
| Multi-candidate Gap publication before implementation | RED | The module publisher rejected duplicate candidate dispositions for one logical Gap. |
| Multi-candidate Gap publication after implementation | PASS | 1 test, 0 failures/errors/skips. |
| Fixed-repository publisher-prefix audit | PASS | M4/M5 policy prefixes already match `data-flow-graph` and `evidence-graph`; no speculative prefix change is needed. |
| Fixed repository Step01–05, 8 GiB, retry 17 | Reproduced | All five graph drafts completed; M6 rejected a data-flow local Gap whose ad-hoc ID does not satisfy the shared identity contract. |
| Local Gap identity regressions before implementation | RED | 2 direct fixtures both fail at `GraphGapDraft.requireIdentity(DATA_FLOW, gap)` with `graph gap identity is invalid`. |
| Local Gap identity regressions after implementation | PASS | 2 tests, 0 failures/errors/skips; both direct constructors now use the shared DATA_FLOW local-Gap identity. |
| `DataFlowGraphBuilderTest` | PASS | 19 tests, 0 failures/errors/skips after the local-Gap identity correction. |
| Fixed repository Step01–05, 8 GiB, retry 18 | BLOCKED_AT_STEP03_M6 | 719 files; Step01 and Step02 complete; all five graph drafts built; public data-flow artifact identity conflicts with the active policy prefix. No OOM and no facts/flows executed. |
| Policy-driven M6 public payload regression before implementation | RED | The direct public-graph publication test failed only at canonical module installation when its data-flow policy used `data-flow-graph` and the publisher used a different hard-coded prefix. |
| Policy-driven M6 public payload regression after implementation | PASS | 1 test, 0 failures/errors/skips. All public graph, evidence, Gap and index payload identities now resolve prefixes through the trusted module-store policy. |
| `ProgramGraphsPublicationSpecifierTest`, `ProgramGraphGapProjectionTest`, `ProgramGraphPublicWireTest` | PASS | 3 tests, 0 failures/errors/skips after the public-payload policy correction. |
| Fixed repository Step01–05, 8 GiB, retry 19 | INTERRUPTED_FOR_PERFORMANCE | The invocation produced no new error or stage report after substantially exceeding the previous 428-second run; it was stopped after roughly 16 minutes to avoid indefinite wait. The ignored workspace and Maven output remain preserved. |

## Decisions

- A zero-argument Java boundary has invocation evidence but no argument evidence. This is a
  generic graph-model correction, not a jshERP-specific exception.
- All local program-graph Gaps must use `GraphGapDraft.forLocalOccurrence`; a publisher is the
  correct final guard, but builders must create valid carriers before publication.
- Artifact policy is authoritative for every public M6 payload identity; a publisher must not
  reintroduce static prefixes after earlier modules have resolved their prefixes from the store.

## Blockers

- None.

## Exact next action

- Treat full-repository M6 publication as performance-blocked on this host until a bounded
  execution approach is selected. Continue only with direct contracts; do not repeat the same
  unbounded 8 GiB replay without a changed performance hypothesis.

## Resume checks

- Re-run the direct regression before and after the implementation change.
- Preserve the fixed-repository report for retry 14 regardless of later progress.
