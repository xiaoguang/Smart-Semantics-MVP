# Progress: Program graph public-wire review

- Status: COMPLETE
- Agent role: Luna/xhigh code review
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Read-only review of the uncommitted M6 public data-flow projection and its focused RED/GREEN test.
- Approved inputs: Scoped `AGENTS.md`, `docs/analysis-steps/03-program-graphs.md` M4/M6 contracts, M6 design/delivery progress, and the three-file diff.
- Current branch/worktree: `codex/source-analysis-program-graph-public-wire` / `/private/tmp/linguan-source-analysis-program-graph-public-wire/backend-agents/sources/source-code`

## Completed

- Reviewed the M4 draft wire, typed `DataFlowNode` variants, M6 public serializer, canonical identity path, existing public-wire test, and the M6 version-gate context.
- Confirmed the production projection uses the already typed `JavaBoundaryInvocationV1` and `UnknownBoundaryReturnV1`; it does not parse `canonicalValue`, reopen source, or invent SQL/Mapper/external-effect semantics.
- Confirmed variant fields are emitted only while serializing the DATA_FLOW public graph. Other graph kinds continue to use the common public node shape, and draft provenance remains replaced by public `evidenceNodeIds`.

## Findings

### P0

- None found in this narrow diff.

### P1

1. **The delivered RED does not establish the complete M6 variant contract.**
   `ProgramGraphPublicWireTest.assertPublicBoundaryVariant` only checks that one
   `JAVA_BOUNDARY_INVOCATION` has an object and a textual
   `invocationCallId` (`src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java:301-314`).
   It does not compare `callTargetEdgeId`, all three static target fields,
   ordered argument ordinals/IDs/origins, control context, exact locator, or
   rule; it also does not exercise an `UNKNOWN_BOUNDARY_RETURN` node. It does
   not assert required-nullable fields on ordinary DATA_FLOW nodes or the
   absence of both variant fields on the other public graphs. The M6 design
   explicitly requires every field of both variants and the DATA_FLOW-only
   placement. A future implementation could drop most of the variant and
   still pass this selector.

   Required follow-up: add one narrow RED/GREEN test using a real persisted
   fixture with both a boundary invocation and a consumed non-void boundary
   return. Assert exact field-for-field projection, null closure by node kind,
   no variant fields on CODE_STRUCTURE/CALL/CONTROL_FLOW/EVIDENCE, and no
   `evidenceDraftRefs` inside the public payload. Keep this as the next M6
   slice; do not broaden the production contract.

2. **Variant identity mutation is not covered by the new test.**
   The serializer now includes the typed variant in the public document before
   `payloadJson` calculates the standalone artifact identity, and the M4
   persisted reader independently rebuilds the draft. That is directionally
   correct, but no new assertion proves that changing a variant field changes
   the canonical public bytes/identity or that a malformed variant cannot be
   admitted through a re-opened publication. This is a test-evidence gap, not
   a demonstrated production defect. The follow-up variant test should mutate
   one nested field (or its source draft) and verify the expected identity
   change/rejection through the existing real stores; it must not add a public
   compatibility reader.

### P2

- `DataFlowGraphWire.boundaryInvocationValue` and
  `unknownBoundaryReturnValue` are package-private static helpers. Their
  enclosing class is package-private and the search found only the draft
  serializer and M6 publisher as callers, so their exposure is minimal and
  acceptable for this slice. No production change is required; keep them
  package-local and do not make them part of the public application API.
- The focused test has no multi-boundary assertion. This is useful for a later
  projection/order mutation slice, but is not required to judge this
  one-node production change once the full field/variant test above exists.

## Spec assessment

- **Faithful M4 projection:** PASS for the inspected path. M6 copies typed
  fields through `DataFlowGraphWire`; it does not reconstruct them from a
  display string.
- **DATA_FLOW-only variants:** PASS by the explicit `identity.kind()` guard in
  `ProgramGraphSetPublicationSpecifier` and by typed `DataFlowNode` closure.
  Test coverage is incomplete as described in P1-1.
- **No draft provenance:** PASS in the changed path. Public nodes keep
  `evidenceNodeIds`; the nested variants contain no draft references.
- **No cross-boundary or SQL inference:** PASS. The diff adds no technology
  kind, SQL field, Mapper execution result, or external-effect edge.
- **Canonical/public identity:** Production path is consistent: the variant
  is present before canonical standalone payload identity is calculated. The
  mutation proof remains missing from the test evidence (P1-2).

## Verification

| Check | Result | Key output |
| --- | --- | --- |
| Source/design/diff inspection | PASS | M4 typed variants are carried into the DATA_FLOW public serializer without draft provenance or external effects. |
| Targeted Maven test | NOT RUN | Review instruction prohibited a heavy Maven run; coordinator/Terra progress records the focused selector as 1 test passing after GREEN. |
| Production/test mutation execution | NOT RUN | Not performed in a read-only review. |
| `git diff --check` | NOT RUN | No file changes existed before this review; coordinator/Terra progress records it passing on the implementation slice. |

## Decisions

- This review does not modify production code, tests, POM, or design. The two P1 items are bounded test follow-ups, not a reason to change the M4/M6 architecture.
- M6 must continue to consume typed, fresh-reopened M4/M5 material. It must not expose the helper methods publicly or add a second public graph schema.

## Blockers

- None for this review. The M6 slice should not be called fully closed until the P1-1 field/unknown-return test is completed.

## Exact next action

- Coordinator: request a new Luna RED for full field-by-field boundary and unknown-return projection, then run Terra GREEN and the direct M6 regression selectors before integrating the bounded slice.

## Resume checks

- Re-read this file and verify the coordinator has not marked the M6 public-wire contract closed while P1-1 remains open.
- Before a follow-up code task, inspect the current diff and use the real canonical stores/fixture path; do not hand-author a detached public graph.
