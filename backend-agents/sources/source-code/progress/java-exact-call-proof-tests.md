# Progress: exact-call M2 Proof RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: One public-seam M2 `JAVA_EXACT_CALL` proof test in `AtomicProofBuilderTest`, with the existing positive boundary assertions narrowed to `JAVA_BOUNDARY_INVOCATION` only.
- Approved inputs: Real `ProgramGraphsPublicFixture.createWithSharedJavaCall`, five freshly persisted/reopened graph payloads, freshly published/read M1 v3 candidate artifacts, and the approved Step04 §8.0.2 contract; no production/schema/policy/design changes, customer source, Provider, or network.
- Current branch/worktree: `codex/source-analysis-process-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read repository, backend, and source-scoped `AGENTS.md` instructions completely.
- Read both implementation plans completely.
- Read Step04 §8.0.2 and the M1 v3 wire/proof requirements completely.
- Read the required repository and superpowers TDD guidance.
- Created this owned progress handoff before Java edits.

## Current state

- The M1 exact-call candidate publication/typed reader is GREEN on v3. The new proof test fresh-reopens all five graph inputs, independently derives exactly three entry/edge/METHOD rows, publishes and typed-reopens the M1 candidate union, and reaches the proof seam with all premises asserted.
- The intended M2 RED is precise and production-only: current `AtomicProofBuilder` enters its legacy static-target path for an exact candidate and throws `PROOF_DECISION_INVALID: atom value` before producing exact Facts. This is not a fixture, publication, schema, or descriptor failure.

## Changed files

- `progress/java-exact-call-proof-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared worktree changes preserved; only this task's progress and proof-test files are owned here. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=AtomicProofBuilderTest#provesExactCallsFromFreshPublishedCandidatesWithClosedAtomsAndRuleEvidence test` | RED (expected) | 1 test, 0 failures, 1 error, 0 skips; three graph-derived rows and fresh M1 v3 candidate read passed, then `PROOF_DECISION_INVALID: atom value` at `AtomicProofBuilder.java:429`. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java spotless:apply` | PASS | Spotless selected exactly 1 file and changed it to clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java spotless:check` | PASS | Spotless selected exactly 1 file; no formatting changes needed. |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/fact/proofs/AtomicProofBuilderTest.java progress/java-exact-call-proof-tests.md` | PASS | No whitespace errors. |

## Decisions

- Derive the expected three exact rows independently from fresh-reopened entry ownership, CALL_SITE nodes, EXACT `CALL_TARGET` edges, and METHOD endpoints; do not hardcode IDs or recompute expectations from proof production logic.
- Use the real M1 v3 publication/read seam before invoking M2 proof, and assert M2 subjects `[callSiteNodeId,targetMethodNodeId]` in UTF-8 byte order.
- Assert the four closed atom names/values and each atom's required edge/rule closure: call-site plus edge for `INVOCATION_CALL_ID`; edge plus target METHOD/source-element-parser for each `STATIC_TARGET_*` atom.
- Preserve the existing boundary behavior by filtering legacy totals to `JAVA_BOUNDARY_INVOCATION`; exact Facts must add no `ExternalEffectGap`, while the existing boundary Gap remains.

## Blockers

- Terra must add exact-call M2 proof support before this selector can turn GREEN; no further test-side work is authorized in this slice.

## Exact next action

- Release Maven and hand the exact M2 RED to root; do not run an aggregate or edit production/schema/policy/design in this slice.

## Resume checks

- Re-read this progress file before any continuation. Preserve the three graph-derived rows, M1 v3 publication/read premise, exact atom/rule/evidence oracles, and the filtered legacy boundary assertions.
