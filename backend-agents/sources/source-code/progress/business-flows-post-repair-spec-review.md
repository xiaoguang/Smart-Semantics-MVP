# Progress: business-flows-post-repair-spec-review

- Status: COMPLETE
- Agent role: Bounded Step 05 post-repair SPEC reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Verify the three previously recorded P1 repairs—bounded discovery-to-public closure, Fact/source/compiler/budget Gap provenance, and complete M3 owner replay—and confirm the v5/v2 reader cutover plus three-field path-free byte/SHA projection. Do not add capability or reopen the two known non-accepted external choices.
- Approved inputs: Fixed base `dea5c1bd96987270ecdc0f8060b612599b8f51d9`; `progress/business-flows-closeout-spec-review.md`; published Step04/05/06 contracts; current implementation and test diffs; relevant completed progress/raw reports.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`

## Completed

- Read the code-review skill and scoped source-code instructions.
- Created this progress record before the bounded review; intent-to-add follows before further review edits.
- Compared the fixed-base Step 05/06 amendments with the current implementation, completed P1 progress records, and the available raw reports. No new P0/P1 or evidence-backed bounded P2 was found.
- Confirmed the three prior P1 repairs are closed for this review: discovery closure is propagated into public closure; Fact/source/compiler/budget Gap provenance is preserved and normalized by the published priority; and M3 performs fresh owner-semantic reprojection with complete-body byte comparison before install.
- Confirmed the carrier cutover is coherent: public Capsule v5 and normalized Gap v2 are required, R0/R1/R2 task projection removes only the three program-only origin metadata fields, retains typed `evidenceRefs`, and computes the task input SHA from the projected canonical bytes. This wording does not assert that local Capsule material is free of source paths; path-free projection is the later model-packet contract.

## Current state

- Static post-repair review is complete. The bounded result is `CLOSED` for the three prior P1 repairs and the requested v5/v2 reader/projection cutover. This does not constitute full Step 05 acceptance; the known non-accepted source-capture/domain-classification choices and broader aggregate quality gates remain outside this review.

## Review result

1. **Bounded discovery-to-public closure — CLOSED.** The design now makes `ApplicationDiscovery.repositoryEntryCoverage.closed` a required boolean and defines public `repositoryFlowCoverage.closed` as its conjunction with M1 local closure and public entry/flow accounting. The implementation reopens and validates the real discovery coverage shape and computes the same conjunction; the bounded public test evidence records discovery `false`, M1 local `true`, and public `false` while retaining nonempty local Flow/Capsule closure.
2. **Fact/source/compiler/budget provenance — CLOSED.** The design's §8.1.3 precedence is reflected in the producer and publisher: Fact views carry the exact proven-facts descriptor; Step 04 ledger rows remain FACT/source authority with exact ledger and typed EvidenceGraph references; raw compiler gaps normalize to FLOW/FLOW_COMPILATION without a fabricated ledger; and budget gaps normalize to FLOW/CAPSULE_PROJECTION with null ledger and empty evidence. The completed provenance reports cover Fact, source Gap, budget Gap, and compiler-only normalization paths.
3. **Complete M3 owner replay — CLOSED.** The design requires fresh M1/M2/predecessor reopen, projector reconstruction, the M2 owner's pure canonical body encoder, and a complete byte comparison before Step 05 installation. The implementation follows that sequence; the positive carrier and semantic replay negative reports show valid publication and rejection of a self-consistent rehashed M2 body whose owning Fact atom was changed, with no Step 05 receipt on rejection.
4. **v5/v2 reader and origin-metadata carrier — CLOSED for the requested seam.** Current R0/R1/R2 consumers require `business-flows-evidence-capsule-v5` and `business-flows-flow-gap-v2`. Their deep-copy projection validates and removes exactly `originFactArtifactRef`, `originKind`, and `originGapLedgerRef`; typed `evidenceRefs` and all other Capsule fields remain. Existing carrier tests assert the projected canonical view and its `inputJsonSha256`. The Step 06 wording now explicitly covers the existing local R0/R1/R2 readers, not only future readers, and explicitly scopes signal identity away from Capsule/Gap transport fields. This is an origin-metadata projection, not a claim that local Capsule bytes contain no source paths.

No actionable spec contradiction was found in the reviewed scope. The M4 current-Flow lineage work and the pending aggregate import repair are implementation/test coordination matters, not additional design findings for this bounded review.

## Changed files

- `progress/business-flows-post-repair-spec-review.md` (owned review record only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git rev-parse dea5c1bd96987270ecdc0f8060b612599b8f51d9` | PASS | Fixed review base resolves to `dea5c1bd96987270ecdc0f8060b612599b8f51d9`. |
| Scoped reads of `docs/analysis-steps/05-business-flows.md`, `docs/analysis-steps/06-flow-interpretation.md`, implementation, tests, completed progress, and raw reports | PASS | Required closure, provenance, replay, and v5/v2 projection contracts matched current code/evidence. |
| Prior bounded raw reports | PASS | Bounded closure/provenance/compiler/graph bundle and M3 carrier/replay evidence were reviewed; no full-suite claim made. |
| `git diff --check -- progress/business-flows-post-repair-spec-review.md` | PASS | No whitespace errors in the owned progress record. |

## Decisions

- Do not review or redesign the previously recorded non-accepted source-path and domain-classification choices.
- Do not run Maven or modify production, tests, design, fixtures, Provider, source, or network state.
- Do not treat the bounded `CLOSED` result as full Step 05 acceptance; retain the distinction in any handoff.

## Blockers

- None for this bounded review. Full Step 05 remains outside the conclusion because the known external source/domain choices and broader aggregate quality gates were not part of this review.

## Exact next action

- Release this completed bounded review to `/root`; no further Java, production, design, or Maven action is authorized in this activity.

## Resume checks

- Preserve the fixed base and existing worktree changes.
- If resumed, re-check only this progress and the fixed-base diff; do not widen the review or claim full Step 05 acceptance.
