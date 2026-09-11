# Progress: exact-call shared source-span capsule test

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Implement one bounded Step05 §8.4 public-seam M2 shared-source-span test for `EvidenceCapsuleProjector`; only the owned test and this progress note are in scope. No production, fixture, schema, design, or Step06 changes.
- Approved inputs: Step05 §8.4, existing public capsule projector records/seams, and `ProgramGraphsPublicFixture.createWithSharedJavaCall`.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress note before Java/Maven preparation.

## Current state

- Read-only feasibility and public-seam mapping are complete; the intended test must use persisted shared-call graphs, fresh v3 M1 publication, and actual closed Facts/Proof/Evidence rather than fabricated IDs or copied production-derived span values. The M1 publisher/compiler gate is green after the separately authorized fixture policy literal correction.
- The implementation gate is now released. The new positive test has been applied to `EvidenceCapsuleProjectorTest`; it proves two persisted Flow slices, disjoint entry-owned Fact IDs, closed Proof evidence, a nonempty shared source-excerpt intersection, and the independently framed per-Flow span/closure oracle before invoking `project()`. The bounded legacy compatibility migration now asserts the published four-signal count and exact kind multiset without changing any basis/support/obligation/identity oracle.

## Changed files

- `progress/java-exact-call-shared-span-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjectorTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only `docs/analysis-steps/05-business-flows.md` §8.4/§8.6 plus `EvidenceCapsuleProjectorTest`, `CapsuleProjection`, and `ProgramGraphsPublicFixture` public seams | PASS | Public capsule fields, persisted Fact/Proof/Evidence payload locations, shared-call fixture, and exact framed span identity are available; no missing public material identified. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest#projectsSharedSourceEvidenceWithFlowRootedSpanIdentityAndClosure test` | RED (intended upstream M2 handoff failure) | 1 test, 1 failure, 0 errors, 0 skipped; all pre-project two-Flow/disjoint-Fact/closed-Proof/shared-source-excerpt premises passed, then the existing projector failed at `requireCompilation` with `UPSTREAM_ARTIFACT_REPLAY_MISMATCH`. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` (after two schema literals v2→v3) | RED (preserved stale legacy oracle) | 6 tests, 1 failure, 0 errors, 0 skipped; the shared-span test passes, while `projectsFullProcessJoinSignalsWithSameFlowBasisAndIdentityBinding` still expects 3 signals and observes 4. No behavior assertion was changed. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` (after four-signal compatibility migration) | PASS | 6 tests, 0 failures, 0 errors, 0 skipped; the shared-span test and all preserved basis/source/support/obligation/identity assertions pass. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjectorTest.java spotless:apply` | PASS | Spotless selected exactly 1 owned test file; 0 changed, 1 already clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjectorTest.java spotless:check` | PASS | Spotless selected exactly 1 owned test file; 0 needed changes. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjectorTest.java spotless:apply` | PASS | Spotless selected exactly 1 owned test file; 1 changed to clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjectorTest.java spotless:check` | PASS | Spotless selected exactly 1 owned test file; 0 needed changes. |

## Decisions

- Keep the implementation to one positive public-seam test in `EvidenceCapsuleProjectorTest` after the M1 publication gate is green.
- Derive the shared source Evidence/excerpt independently from reopened persisted Proof/Evidence and calculate each span ID from the published v2 framing contract using Flow ID and Evidence ID; do not copy production helper outputs.
- Test map: create `ProgramGraphsPublicFixture.createWithSharedJavaCall`, publish fresh Proven Code Facts through the existing test helper, compile and publish the two real entry-rooted Flows, then invoke the existing public `EvidenceCapsuleProjector.project(...)` seam through the test's established profile adapter.
- Premises: assert two compiled Flow slices, disjoint actual `factIds`, closed actual Facts/Proofs/signals from the persisted v3 inputs, and one Capsule per Flow before checking shared source material. Do not hardcode entry, Fact, Evidence, span, or signal IDs.
- Independent shared-material derivation: reopen the fresh Fact publication's `proven-facts.json` and `proof-pack.json`, collect each Flow's required evidence IDs from its actual Fact atom Proofs, outcome required Proofs, and process-join signal evidence IDs, then intersect the two Flow sets. Reopen `evidence-graph.json` and require a nonempty intersection with equal `SourceExcerptV1` values; this proves the source is shared without copying projector selection output.
- Span oracle: for each selected shared evidence ID and each Flow, expect exactly `model-evidence-span:` plus lowercase SHA-256 of framed `business-flows-model-evidence-span-id-v2`, that Flow's actual `flowSliceId`, and the actual Evidence ID. Require the two IDs to differ, each ID to occur in exactly one Capsule, and each source excerpt/support set to remain Flow-local.
- Closure oracle: each Capsule's fact/atom, outcome, signal, span, and obligation IDs must be exact same-Flow sets; every span support ID and every obligation satisfying span must be contained by that Capsule, with no cross-Flow union. Existing fixture source text and persisted graph shape remain untouched.

## Blockers

- The M1 v3 publication handoff is green (8 targeted tests); the shared-span positive seam and full projector class are green. No production or fixture change is included.

## Exact next action

- Release Maven to root/Terra. The bounded compatibility correction is complete; do not extend this slice.

## Resume checks

- Keep ownership limited to this test and progress note; do not edit fixtures, production, schema, design, Step06, or Git. The bounded selector and exact one-file formatting checks are complete.
