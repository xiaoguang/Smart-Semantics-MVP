# Progress: target cross-stage persistence final review

- Status: COMPLETE
- Agent role: Independent Sol/ultra final architecture reviewer
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-31T22:51:28Z
- Last updated: 2026-08-31T23:05:16Z
- Scope: Read-only review of `docs/DESIGN.md`, target stage documents `docs/stages/01-*.md` through `08-*.md`, and the scoped `AGENTS.md` design-publication gate; only this progress file may be changed.
- Approved inputs: Current authoritative target-design documents and scoped repository instructions in `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`; no Maven, network, model, source, or runtime calls.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read all three applicable `AGENTS.md` files and the progress template.
- Captured the pre-review worktree status and preserved all pre-existing changes.
- Read the complete current `docs/DESIGN.md` and all eight target stage documents (`01-*` through `08-*`).
- Traced the approved request, identity/control, typed-address, publication-order, Stage08 DAG, lifecycle/result, example, and stage-output-count invariants.
- Ran docs-only whitespace, fence/JSON, exact-control, request-version, module-key, artifact-ID, and typed-reference checks.

## Current state

Review complete. **REJECT** the current documentation package for implementation/publication. There is no P0. The target prose is coherent for request v2, five-field controls, framed policy-selected identities, typed/path-free public store seams, Stages01–07 receipt-last publication, the acyclic Stage08 five→archive→receipt→manifest→M4 order, RAW_UTF8 exclusivity for the final Markdown, lifecycle/result separation, and stage output counts. P1 contradictions remain in the exact receipt/example/module-address contracts, so the published examples cannot be accepted by the stores and parsers they specify.

## Changed files

- `progress/target-cross-stage-persistence-final-review.md` (this review record only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Recorded pre-existing modified/untracked work; no reviewed artifact was edited by this reviewer. |
| `sed` over the three applicable `AGENTS.md` files | PASS | Root, backend-agent, and GitHub Code Agent instructions read before review. |
| Complete read of `docs/DESIGN.md` and `docs/stages/01-*` through `08-*` | PASS | Nine authoritative target documents read in full. |
| `git diff --check -- <scoped docs> progress/target-cross-stage-persistence-final-review.md` | PASS | No whitespace errors. |
| Ruby Markdown-fence/JSON parser over the scoped docs | PASS | `fenced_blocks=73 json_units=76 errors=0`. |
| Exact request-version scan | PASS | `analysis-run-request-v1=0`; `analysis-run-request-v2=16`. |
| Exact five-field `ArtifactControls` check over module examples | PASS | `29/29` examples have exactly the required five controls. |
| `producer.module` grammar check over module examples | FAIL (P1) | `29/29` use values that fail the required compiled lower-case module-key grammar. |
| Recursive `artifactId` grammar check over stage examples | FAIL (P1) | Three malformed `upstreamArtifacts[].artifactId` values at Stage08 line 256. |
| Stage08 M4 typed-reference shape check | FAIL (P1) | `stagePublicationRef` lacks `address`; `runManifestRef` uses generic `artifactId/sha256` instead of `address/runManifestId/runManifestSha256`. |
| Observable-output table count | PASS | Stages01–08 are `4/5/8/5/6/10/6/8 = 52` files: 42 semantic payloads, eight stage receipts, one Stage08 archive manifest, and one root run manifest. |
| Independent read-only example/count cross-check | PASS | Confirmed the count totals and independently reproduced the receipt, module-key, and stage-example schema contradictions; no P0. |
| Final fresh scoped rerun | PASS | `diff --check` clean; 73/73 fences and 76/76 JSON units parse; only request v2 occurs; output counts remain `4/5/8/5/6/10/6/8`; reproduced 29 invalid module keys, three invalid Stage08 artifact IDs, three exact-receipt mismatches, and two M4 typed-reference mismatches. |

## Decisions

- Treat historical/current-audit descriptions as maturity evidence, not as the target contract.
- Report only P0/P1 findings with exact path/line evidence; otherwise accept.
- Current decision direction: REJECT until the executable examples are aligned with the authoritative schemas/store validators.
- P1 — the DESIGN receipt presented as exact is not schema-valid: `docs/DESIGN.md:627`, `:639-643`, `:649`, and `:654-656` conflict with the exact records/counts at `:1237`, `:1246`, and the seven-artifact Stage03 contract.
- P1 — module identity is not executable: `docs/DESIGN.md:1107,1305-1317` require exact compiled lower-case module keys, but all 29 `ModuleArtifact` examples use class names (`docs/stages/01-freeze-source.md:262-265`; `docs/stages/02-discover-application-and-entries.md:201-203`; `docs/stages/03-build-five-program-graphs.md:254-258`; `docs/stages/04-prove-code-facts.md:187-188`; `docs/stages/05-compile-business-flows.md:200-201`; `docs/stages/06-interpret-one-flow-at-a-time.md:216-220`; `docs/stages/07-admit-and-merge-business-knowledge.md:198-199`; `docs/stages/08-build-nine-section-document-and-archive.md:256-261`). The fixed ownership rule at `docs/DESIGN.md:1568` also lacks a defined encoding to the differing package/fixture names in the per-module briefs (for example `docs/stages/01-freeze-source.md:214-215,228-229,242-243`).
- P1 — exact/schema-valid target examples disagree with their schemas: Stage01 `docs/stages/01-freeze-source.md:29-46,169-185,176,182,258-265`; Stage02 `docs/stages/02-discover-application-and-entries.md:23-33,59-69,191-193`; Stage04 `docs/stages/04-prove-code-facts.md:181,188,200-229`; Stage06 `docs/stages/06-interpret-one-flow-at-a-time.md:213,218-219,246-262`; Stage07 `docs/stages/07-admit-and-merge-business-knowledge.md:190,199`; Stage08 `docs/stages/08-build-nine-section-document-and-archive.md:24-39,253,256,259`.
- P1 — Stage08 specifically violates the safe/typed reference contract: line 256 has three `upstreamArtifacts[].artifactId` values outside `docs/DESIGN.md:1303` grammar; line 259 omits/changes the exact `StagePublicationReference` and `RunManifestReference` fields from `docs/DESIGN.md:1265,1271`.

## Blockers

- Publication/implementation is blocked by the P1 documentation contradictions above; the review itself is complete.

## Exact next action

Design Authority must correct and republish the exact receipt/example/module-address contracts, then request a fresh docs-only final review before implementation proceeds.

## Resume checks

- Re-read this file and `git status --short`.
- Confirm only this progress file is changed by the reviewer.
- Re-run the recorded docs-only checks after the Design Authority's corrections.
