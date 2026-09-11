# Progress: Business flow Fact origin implementation

- Status: COMPLETE
- Agent role: Terra/xhigh bounded existing-contract GREEN owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: One persisted Fact-origin vertical slice only: project the exact reopened `proven-facts.json` descriptor into each Flow Fact view and cut M2 to the published v7 projection identity/schema/module version.
- Approved inputs: Frozen `BusinessFlowProvenanceTest` RED; published Step 05 §8.1.3 Fact-origin and v7 cutover contract; current M2 projector/publisher/model code; exact reopened Step 04 payloads; and existing artifact policy registry.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the frozen public test's Fact/atom oracle and the published M2 Fact-origin/cutover requirements.
- Confirmed the public M2 publisher installs successfully, then the test fails only because `FlowFactView` serialization omits required `originFactArtifactRef`; the oracle obtains the expected value from the exact reopened `proven-facts.json` descriptor.
- Created this owner-only progress file before production edits.
- Added the required immutable Fact-origin reference and populated it from the fresh-reopened `proven-facts.json` descriptor held by the projector's exact Step 04 payload map.
- Added the Fact-origin field to the M2 canonical body, changed M2 to the required v7 schema/v6 module version, recomputed `capsuleProjectionId` from the canonical projection body without self ID using the published v2 domain, and replaced the strict module-artifact policy registration with v7 only.
- Ran the four-file absolute selected Spotless apply/check after the frozen fixture-policy migration, then passed the only permitted post-format selector.

## Current state

- This Fact-origin slice is complete. `FlowFactView` requires a non-null `originFactArtifactRef`, and every projected Fact receives exactly the descriptor reference for the reopened `proven-facts.json` payload that supplied the Fact rows.
- M2 emits only v7/v6 bytes and computes `capsuleProjectionId` from the complete current canonical body without the self field using `business-flows-capsule-projection-id-v2`. M3 replay, full Gap-origin transport, and Step 06 Provider-packet stripping remain explicitly outside this slice.
- The Maven lease is released. Do not extend this result to a full v7 carrier or Step 05 acceptance.

## Changed files

- `progress/business-flow-fact-origin-implementation.md` (owned; created before production edits)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjection.java` (authorized; required immutable Fact-origin reference)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java` (authorized; derive Fact origin only from reopened `proven-facts.json` descriptor)
- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java` (authorized; Fact-origin serialization and M2 v7/v6/body-hash cutover)
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (authorized; strict M2 v7 artifact-policy registration only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Frozen `BusinessFlowProvenanceTest` evidence | RED; numeric exit 1 | 1 test, 1 failure, 0 errors, 0 skipped; public M2 publication succeeds, then a Fact view lacks `originFactArtifactRef`. |
| Scoped `git diff --check` after production patch | PASS; numeric exit 0 | No whitespace error in the owned progress and authorized production paths. |
| Four-file absolute selected Spotless apply | PASS; numeric exit 0 | 4 files selected; 1 changed to clean, 3 already clean, 0 skipped. |
| Four-file absolute selected Spotless check | PASS; numeric exit 0 | 4 files selected; 0 need changes, cache skipped all 4 as clean. |
| Post-format `BusinessFlowProvenanceTest` | PASS; numeric exit 0 | 1 test, 0 failures, 0 errors, 0 skipped. |

## Decisions

- Populate Fact origin only from the reopened `proven-facts.json` descriptor; never infer it from a Fact ID, atom, proof, signal, or another artifact.
- Use the published v7/v6/new-domain cutover exactly; do not accept or emit v6 compatibility bytes, aliases, defaults, or migration readers.
- Preserve Fact/atom values and Fact/Flow/Capsule/signal identities. Leave M3 replay, Gap provenance, and local Provider stripping for their separately frozen REDs.

## Blockers

- None for this bounded Fact-origin implementation. Gap provenance, M3 replay, and local Provider stripping require their own future frozen REDs and remain out of scope.

## Exact next action

- Root may release the Maven lease to the next separately scoped frozen-RED owner. Do not infer full v7 transport or Step 05 acceptance from this Fact-only result.

## Resume checks

- The shared worktree is intentionally dirty. Only this progress file, the owned capsule package production files, and the necessary exact v7 artifact-policy registration are authorized for this slice.
- Maven lease is released. No network, Provider, source capture, test/fixture/design/schema edits, commit, push, or subagents were used by this owner.
- Completing this persisted Fact-origin slice does not accept the full v7 carrier, full Step 05, M3 replay, Gap provenance, or Step 06 behavior.
