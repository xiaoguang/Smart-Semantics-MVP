# Progress: exact-call candidate publication RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: One public stored-artifact persistence test for the approved `JAVA_EXACT_CALL` v3 candidate wire, plus the coordinated M1-only fixture/policy cutover later released by root.
- Approved inputs: Frozen synthetic dual-root/shared-callsite graph fixture only; no customer source, Provider, network, schema/design changes, or production edits.
- Current branch/worktree: `codex/source-analysis-process-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Created this owned progress handoff before Java edits.
- Root released the Java/Maven gate after Terra’s candidate GREEN2/0/0/0 baseline; the planned publication test and M1-only v3 cutover are applied.

## Current state

- Completed the full `FactCandidateModuleArtifactTest`, `FactCandidateModuleReaderTest`, §8.0.2, and M1 candidate-wire read.
- The fixture/test M1 candidate policy and publication/reader expectations are v3. The historical RED was the old production publisher requesting v2 while only the v3 fixture policy was registered; it was not an absent v3 fixture registration. M2/public Step04 policies remained unchanged in this slice.
- The exact persisted premises passed before publication: 3 exact candidate rows, `OrderService#dispatch(java.lang.String)` METHOD target, and call-site/edge/METHOD evidence closures.
- The publication RED was precise: the old v2 production publisher reached the real store, but the fixture registered only v3, so publication failed with `FACT_CANDIDATE_MODULE_PUBLICATION_FAILED` caused by `ArtifactStoreException: ARTIFACT_POLICY_NOT_FOUND`. Terra later made the v3 publisher/reader GREEN.

## Changed files

- `progress/java-exact-call-publication-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleArtifactTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleReaderTest.java` (M1 schema expectation only)
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (M1 candidate policy only; shared fixture was already owned from the prior exact-call slice)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared implementation/test changes preserved; no owned Java edits. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateModuleArtifactTest#publishesExactCallUnionAndFreshTypedReopensTheSameV3Bytes test` | RED (historical) | 1 test, 1 failure, 0 errors, 0 skips; all exact/evidence premises passed, then the old v2 publisher failed at store install with `ArtifactStoreException: ARTIFACT_POLICY_NOT_FOUND` because the fixture registered v3 only. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute three owned Java files> spotless:apply` | PASS | Spotless selected exactly 3 files; 1 changed to clean and 2 were already clean. |
| `git diff --check` (owned Java files) | PASS | No whitespace errors. |

## Decisions

- Derive the expected three exact rows from fresh-reopened public graphs, then publish through the real candidate module publisher/store.
- Assert the v3 exact variant fields only: call-site ID, CALL_TARGET edge ID, target METHOD ID/canonical method, evidence-by-subject union, and four required atoms; do not require boundary-only fields on exact variants.
- Fresh typed reopen must equal the original candidate set, identity, and canonical bytes; exact rows must be the three entry/edge denominator rows.
- Preserve the common union discriminator `candidateFactKey`, `entryId`, and `kind`; exact variant JSON fields are exactly `callSiteNodeId`, `callTargetEdgeId`, `targetMethodNodeId`, `targetCanonicalMethod`, `evidenceNodeIdsBySubject`, and `requiredAtoms` in addition to those common fields. Assert absence of boundary/guard-only fields.
- Assert `evidenceNodeIdsBySubject` has the persisted call-site, CALL_TARGET edge, and METHOD subjects, and `requiredAtoms` has the exact four §8.0.2 atom keys/role/type values.

## Exact next action

- No further action; this bounded publication slice is complete and Maven is released. The current M2 proof slice is tracked separately in `java-exact-call-proof-tests.md`.

## Resume checks

- Re-read this progress file and preserve the single M1 v3 cutover; do not add M2/public Step04 policies or a v2 compatibility path.
