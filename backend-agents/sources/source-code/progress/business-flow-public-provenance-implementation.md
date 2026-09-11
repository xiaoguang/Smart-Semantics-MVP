# Progress: Business Flow public provenance implementation

- Status: COMPLETE
- Agent role: Terra/xhigh M3 public provenance carrier GREEN owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Bounded M3 carrier vertical: consume persisted M2 v7 provenance and transport it into the public Capsule v5 and Gap v2 forms, with M3 module version v2.
- Approved inputs: Published Step 05 §8.1.3; current M2/M3 serializers and canonical identity implementation; frozen public positive RED `BusinessFlowProvenanceTest#publishesFreshReopenedM3FactAndSourceGapProvenanceWithPublishedCarrierVersions` (pre-change: 1 test, 0 failures, 1 error at the M2 v7 descriptor rejection).
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Checked the intentionally dirty shared worktree before this preparation-only checkpoint.
- Read published Step 05 §8.1.3 and confirmed the one-way cutover: M2 `business-flows-capsule-projection-v7`/producer v6 feeds public Capsule v5 and normalized public Gap v2; M3 producer moves from v1 to v2; Fact/Gap/Flow/Capsule/signal identities otherwise stay unchanged.
- Confirmed current M2 is already at the required v7/v6 state. `CapsuleProjection.FlowFactView` carries `originFactArtifactRef`; `FlowGapView` carries typed `evidenceRefs`, `originKind`, and required-nullable `originGapLedgerRef`; `EvidenceCapsuleProjector` derives the exact Step 04 `proven-facts.json`, `gap-ledger.json`, and EvidenceGraph descriptor references.
- Identified the M3 normalization seam in `FlowPublicationSpecifier.material(...)`: it currently seeds `gapsById` from raw M1 compiler gaps and uses M2 `gapViews` only through `putIfAbsent`, giving the M1 entry copy precedence. `completePublicCapsules(...)` is the public Capsule transport seam: it currently deep-copies M2 capsule fields and adds complete span/obligation material.
- Identified the only expected production paths for the frozen-RED vertical: `FlowPublicationSpecifier.java` for strict M2 v7 consumption, M3 v2, public Fact-origin validation/transport, and source-ledger-over-M1 Gap normalization; `AtomicCanonicalPublicationEngine.java` for exact v5/v2 public artifact-policy registration. The current M2 pure `projectionBody(...)` encoder is the owner canonical-body seam, but replay/body comparison is explicitly deferred and must not be changed in this vertical.
- Completed the authorized M3 carrier implementation. It strictly consumes M2 `capsule-projection-v7` with producer v6, emits M3 v2/public Capsule v5/public Gap v2, validates each public Fact origin against the reopened `proven-facts.json` descriptor and Fact rows, and normalizes source-ledger Gaps from reopened ledger/evidence artifacts ahead of an agreeing M1 entry copy. Typed evidence references are the actual reopened EvidenceGraph descriptor, never evidence node IDs.

## Current state

- COMPLETE for the bounded M3 carrier. The frozen public class completed 4 tests with 0 failures, 0 errors, and 0 skips before the private-name-only correction; its behavioral evidence remains unchanged. The correction replaced the prohibited numbered private runtime type with semantic `FlowProvenanceSources` only.
- This vertical carried real persisted M2 v7 Fact/source-ledger provenance into public Capsule v5 and Gap v2 under M3 module version v2, preserving Fact IDs, atom values, Flow/Capsule/signal IDs and using actual descriptors rather than node IDs.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java` (owned strict M3 carrier and provenance normalization)
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (owned exact public v5/v2 policy registration)
- `progress/business-flow-public-provenance-implementation.md` (owned progress)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Scoped implementation/schema inspection | COMPLETE | Located M3 Gap normalization/public-Capsule transport seams and current M2/M3/policy versions; no RED or executable verification is authorized yet. |
| Frozen public RED | RED; numeric exit 1 | `BusinessFlowProvenanceTest#publishesFreshReopenedM3FactAndSourceGapProvenanceWithPublishedCarrierVersions`: 1 test, 0 failures, 1 error at the old M2 v6 descriptor gate (`requireModule:563`). |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java spotless:apply` | PASS; numeric exit 0 | Exactly 2 selected production files: 1 changed clean, 1 already clean, 0 cache-skipped. |
| Same exact absolute two-file `spotless:check` | PASS; numeric exit 0 | Exactly 2 selected production files: 0 need changes, 0 already clean, 2 cache-skipped clean. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest test` | PASS; numeric exit 0 | Raw Surefire XML: 4 tests, 0 failures, 0 errors, 0 skipped. It includes the frozen M3 Fact/source-Gap carrier method plus the prior Fact, source-Gap, and budget-Gap methods. |
| Scoped `git diff --check` | PASS; numeric exit 0 | No whitespace errors in either owned production path or this owner progress note. |
| Absolute one-file `FlowPublicationSpecifier.java` Spotless apply after private-name correction | PASS; numeric exit 0 | Exactly 1 selected Java file, 1 changed clean, 0 already clean, 0 cache-skipped. |
| Same exact absolute one-file Spotless check | PASS; numeric exit 0 | Exactly 1 selected Java file, 0 need changes, 0 already clean, 1 cache-skipped clean. No test rerun is authorized for this mechanical rename; the next Luna-owned related selector will compile it. |

## Constraints

- Do not implement M2 replay, bounded closure, Provider stripping, schema redesign, or any new source behavior in this vertical.
- Do not use compatibility aliases/defaults or replace actual artifact descriptors with evidence node IDs.
- Preserve all current upstream real-wire and provenance fixes; do not modify tests, fixtures, plans, schemas, POMs, or unrelated production code.

## Blockers

- None for this bounded M3 carrier. The private-name correction is formatted and frozen; replay, bounded closure, Provider stripping, and all other Step 05 gates remain deliberately outside this slice.

## Exact next action

- Release the formatting lease. Do not rerun the public class solely for the mechanical private-name correction; its prior 4/0/0/0 evidence remains the carrier GREEN evidence, and the next Luna-owned related selector will compile it.

## Resume checks

- The formatting lease is released. No test/design/schema/source/network/Provider/commit/push/subagent changes were made for the private-name correction.
- Full Step 05, M2 replay, bounded closure, and Provider stripping remain outside this completed carrier slice and are not accepted here.
