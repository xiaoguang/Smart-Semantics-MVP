# Progress: exact-call shared-span implementation

- Status: COMPLETE
- Agent role: Terra/xhigh M2 shared-span implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Approved Step05 M2 positive exact-call shared-source span projection only. Modify `EvidenceCapsuleProjector.java` to read M1/Step04 v3 material and project Flow-rooted source spans. Do not modify publisher/policy/public Step05, tests/fixtures, Capture/runtime, Step06, or domain classification.
- Approved inputs: Step05 §§8.1.2 and 8.4; Luna's frozen `EvidenceCapsuleProjectorTest#projectsSharedSourceEvidenceWithFlowRootedSpanIdentityAndClosure` RED; existing M2 projection contract.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record from `progress/TEMPLATE.md` before implementation inspection.
- Replaced the projector's M1 and consumed Step04 descriptor/header gates with their sole v3 contracts.
- Selected each source-backed model span by the published `(flowSliceId, evidenceNodeId)` framed identity, preserving independent Flow-local support, obligation, and Capsule membership.
- Ran exact one-file Spotless apply/check and the post-format bounded projector class after the test author migrated two stale v2-only assertions.

## Current state

- The projector accepts only M1 `business-flows-flow-compilation-v3` and consumed Step04 v3 payloads; it provides no v2 compatibility path.
- Each source-backed span uses the exact SHA-256 framed preimage `business-flows-model-evidence-span-id-v2`, Flow ID, and Evidence ID. The resulting `model-evidence-span:` ID selects a separate `SpanBuilder` per Flow/Evidence tuple, keeping support, obligation, and unique Capsule membership Flow-local.
- The post-format bounded projector class is GREEN: 6 tests, 0 failures, 0 errors/skips. The two prior v2-only test assertions were migrated by Luna; this slice did not modify tests.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java`
- `progress/java-exact-call-shared-span-implementation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Luna/root `EvidenceCapsuleProjectorTest#projectsSharedSourceEvidenceWithFlowRootedSpanIdentityAndClosure` | RED (confirmed) | 1 test, 1 failure, 0 errors/skips. The old M1 descriptor gate rejects before shared-span projection after all named persisted premises pass. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest#projectsSharedSourceEvidenceWithFlowRootedSpanIdentityAndClosure test` | PASS | 1 test, 0 failures, 0 errors/skips. |
| Pre-migration `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` | Expected test-only block | 6 tests, 2 failures, 0 errors/skips. Only lines 362 and 526 asserted `business-flows-flow-compilation-v2`; actual was approved v3. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java spotless:apply` | PASS | Exactly 1 file selected; 0 changed, 1 already clean, 0 skipped by cache. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java spotless:check` | PASS | Exactly 1 file selected; 0 needing changes; cache reported 1 skipped after apply. |
| Post-format `mvn -o -t .mvn/toolchains.xml -Dtest=EvidenceCapsuleProjectorTest test` | PASS | 6 tests, 0 failures, 0 errors/skips. Raw Surefire TXT confirmed. |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/flow/capsule/EvidenceCapsuleProjector.java progress/java-exact-call-shared-span-implementation.md` | PASS | No whitespace errors. |

## Decisions

- Replace only M1 and Step04 reader descriptors with their published v3 contracts, without a compatibility path.
- Keep source span selection, support, and obligation ownership Flow-local. The span identity is exactly the approved framed SHA-256 preimage over version domain, Flow ID, and Evidence ID.

## Blockers

- None for this bounded M2 projector slice. Maven is released for the coordinated publisher/public/reader wire handoff.

## Exact next action

- Do not extend this completed slice. The next coordinated handoff owns publisher/public/reader wiring; it is not runtime or Step06 work.

## Resume checks

- Do not modify M2 publisher/policy, public publication, tests/fixtures, Capture/runtime, domain classification, or Step06. Run only the named test, its class after a positive result, and exact one-file formatter/check.
