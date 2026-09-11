# Progress: shared-source Capsule rejection regression

- Status: COMPLETE
- Agent role: Luna/xhigh bounded TDD test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Add one Step05 §8.6 public-seam negative test to `CapsuleProjectionModulePublisherTest` for shared-source Flow-rooted span ownership; preserve the existing positive oracle and all current assertions.
- Approved inputs: `EvidenceCapsuleProjectorTest#projectsSharedSourceEvidenceWithFlowRootedSpanIdentityAndClosure`, the real `ProgramGraphsPublicFixture.createWithShared...` graph/fact/M1/M2 path, and the public `CapsuleProjectionModulePublisher` seam; no production, fixture, helper outside this test, design, or Step06 changes.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`; preserve unrelated shared-worktree changes.

## Completed

- Read the TDD guidance and confirmed this slice must test the public publisher seam with real persisted upstream inputs.
- Confirmed the prior `flow-closeout-regression-tests.md` handoff is complete and remains untouched.
- Created and intent-to-added this owned progress file before editing the Java test.

## Current state

- The new single test is written, formatted, and verified through the public publisher seam.
- It starts from `createWithSharedJavaCall`, publishes real facts and M1, projects real M2 capsules, proves shared evidence plus distinct Flow-rooted span IDs, constructs independent cross-Flow rooted-span/support and bare-evidence-node-ID mutations, and invokes the public publisher directly for both rejections before publishing the untouched projection.
- The authorized Maven gate has completed for this slice. A post-format exact-method rerun was attempted, but test compilation was then blocked by a separate concurrently edited `FixedRepositoryBusinessFlowsIT.java`; that file is outside this slice and was not changed.

## Changed files

- `progress/shared-source-capsule-rejection-tests.md` (owned)
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisherTest.java` (owned; complete)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest#rejectsCrossFlowSpanOwnershipAndBareEvidenceNodeMutationsBeforeInstall test` | PASS, numeric exit 0 (session 6746) | Tests 1, Failures 0, Errors 0, Skipped 0. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest test` | PASS, numeric exit 0 (session 99049) | Tests 4, Failures 0, Errors 0, Skipped 0. |
| Exact-file `spotless:apply` for `CapsuleProjectionModulePublisherTest.java` | PASS, numeric exit 0 (session 42912) | One owned Java file formatted. |
| Exact-file `spotless:check` for `CapsuleProjectionModulePublisherTest.java` | PASS, numeric exit 0 (session 62751) | One file checked; 0 needing changes. |
| Post-format exact-method rerun | BLOCKED, numeric exit 1 (session 78789) | Test compilation stopped on 20 pre-existing/out-of-scope errors in concurrently edited `FixedRepositoryBusinessFlowsIT.java`; no owned-test failure was reached. |
| `git diff --check -- progress/shared-source-capsule-rejection-tests.md src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisherTest.java` | PASS, numeric exit 0 | No whitespace errors in owned files. |

## Decisions

- Build both mutations from a valid real `CapsuleProjection`, never by hand-forging a legal upstream graph, Fact, Proof, Flow, or Capsule. The wrong-owner mutation swaps the two real shared-source span IDs/support ownership references without adding a span or orphan; the legacy mutation substitutes the actual bare `evidence-node:*` ID while keeping capsule/obligation closure.
- Require both independent mutations to fail before installation with `PROCESS_JOIN_SIGNAL_FLOW_MISMATCH` or `EVIDENCE_PROJECTION_INVARIANT_BROKEN`; then publish and fresh-reopen the untouched projection.
- Avoid the existing reflection exception wrapper; use the public record/module publisher types directly.

## Blockers

- The final post-format rerun could not reach Surefire because the concurrently edited `FixedRepositoryBusinessFlowsIT.java` fails test compilation in 20 places. This is reported only; no out-of-scope file was modified. The exact method and full class both passed before formatting, and exact-file Spotless apply/check passed afterward.

## Exact next action

- Handoff complete. Do not run broader tests, modify production/design/fixtures/other tests, commit, or push from this slice.

## Resume checks

- Re-read this note before continuing. Keep scope to this progress file and `CapsuleProjectionModulePublisherTest.java`; do not run Maven before root release and do not modify production, fixtures, design, other tests, or Step06.
