# Progress: Business Flows post-repair Standards review

- Status: COMPLETE
- Agent role: Bounded READ-ONLY Standards-axis follow-up reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Standards review of closeout repairs since dea5c1bd96987270ecdc0f8060b612599b8f51d9 in backend-agents/sources/source-code; source implementation and tests are read-only.
- Approved inputs: Explicit parent authorization; no Maven/build/formatter; no provider/network/customer commands; no sub-agents; only this progress file may be edited.
- Current branch/worktree: /private/tmp/linguan-source-analysis-process-design

## Completed

- Read repository, worktree, backend-agent, and source-code guidance.
- Read the code-review skill and its Fowler-smell baseline.
- Confirmed the worktree is intentionally dirty with the closeout repair set.
- Reviewed the post-review repair hunks covering provenance, owner replay, bounded closure, real upstream carriers, scope-gap closure predicates, model-input metadata stripping, Registry lineage, immutable-copy wrappers, and the new direct tests.
- Found no hard documented standards violation in the bounded repair set.
- Recorded three non-blocking P2 smell heuristics: duplicated provenance/model-safe capsule projection helpers across R0 and R1/R2 compilers; the new `view` helper name does not reveal metadata-stripping/validation; and repeated JSON assertion/fixture helpers across the new handoff/provenance tests.

## Current state

- Follow-up review was limited to concrete documented standards and labelled smell heuristics in closeout repairs after the prior Standards review.
- Prior review had only two non-blocking P2 findings; no Spec-axis judgement is in scope.

## Changed files

- Only this progress file is owned by this review.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git -C /private/tmp/linguan-source-analysis-process-design status --short` | PASS | Confirmed dirty closeout-repair worktree; source changes preserved. |
| `git diff --check dea5c1bd96987270ecdc0f8060b612599b8f51d9 -- backend-agents/sources/source-code/src/main backend-agents/sources/source-code/src/test` | PASS | No whitespace errors in the scoped source/test diff. |

## Decisions

- Do not run Maven, tests, builds, formatters, source scanners, provider/network/customer commands, or modify source/tests/design/schema/POM files.
- Report only actionable standards findings with exact lines; label smell heuristics separately from actual documented-rule violations.
- Do not treat the 52 immutable-copy wrappers, the nine mechanical quality cleanups, or the import-only `ArtifactReference` correction as new standards findings.

## Findings

- No hard documented standards violation.
- P2 Duplicated Code: `RegistryProposalTaskCompiler.java:326-390` and `FiniteKeyFlowTaskCompiler.java:331-380` now duplicate the model-safe capsule copy plus artifact-reference validation/ordering for the same three non-model provenance fields. A semantic Flow Interpretation capsule-view projector could own the shared shape while preserving the two caller-specific failure codes.
- P2 Mysterious Name: both new/changed `view(JsonNode capsule)` helpers at `RegistryProposalTaskCompiler.java:326` and `FiniteKeyFlowTaskCompiler.java:331` hide that they deep-copy, validate, and strip provenance metadata before canonical model bytes. A name such as `modelSafeCapsuleView` would make the trust-boundary behavior explicit.
- P2 Duplicated Code (tests): `BusinessFlowProvenanceTest.java:1008-1054`, `BoundedBusinessFlowPublicationTest.java:253-310`, and `BoundedFactInputHandoffTest.java:134-164` repeat JSON assertion, object-index, text-array, reference, and digest helpers. This is non-blocking because the tests remain scoped, but a stable test-only assertion fixture would reduce repair churn.

## Exact next action

- Release this bounded review to the parent with the findings above; do not claim full Step 05 acceptance.

## Resume checks

- Re-read this file, verify the worktree status, and resume the bounded source-only inspection.
