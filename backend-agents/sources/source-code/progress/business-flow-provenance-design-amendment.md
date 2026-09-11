# Progress: business-flow provenance design amendment

- Status: COMPLETE
- Agent role: Sol/ultra design authority for the bounded Step 05 provenance amendment
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Amend only the Step 05 provenance, version, closure-consumption, and M3 replay contracts plus the necessary existing-reader projection statement in Step 06.
- Approved files: `docs/analysis-steps/05-business-flows.md`, necessary references in `docs/analysis-steps/06-flow-interpretation.md`, and this progress file.
- Branch/base: `codex/source-analysis-flow-provenance-design` at `dea5c1bd96987270ecdc0f8060b612599b8f51d9`
- Worktree: `/private/tmp/linguan-flow-provenance-docs.EomKSG`

## Completed

- Confirmed the clean docs-only worktree, requested branch, and base.
- Re-read the completed closeout contract ruling that owns this amendment.
- Created this task-owned progress record before editing target design.
- Amended Step 05 with the exact finite provenance contract, normalization precedence, version/identity cutover, discovery-closure consumption rule, model-versus-transport budget rule, and existing M3 full-body replay responsibility.
- Amended only the necessary Step 06 reader/projection paragraph so every origin field remains program-only and is stripped before Provider material while typed evidence references retain their existing model-packet role.
- Added symbolic source-Fact-Gap and budget-Gap examples that are explicitly not actual output, fixture, digest, or replay material.
- Applied the bounded spec-review clarification that local R0/R1/R2 also strips the three program-only origin fields before canonical task bytes/request hashing, migrates its reader to public Capsule v5/Gap v2, and preserves typed evidence references and all existing packet/budget rules.
- Scoped the `processJoinSignalId` preimage explicitly to `ProcessJoinSignalV1` fields excluding only its self ID; Fact/Gap origins and other Capsule transport cannot enter that identity.

## Current state

- The bounded docs-only amendment is ready for root review and publication through the existing design-publication gate.
- It preserves exactly 57 formal outputs, five Step 05 semantic payloads plus one receipt, eight steps, nine chapters, the public `RepositoryAnalysisAgent` seam, and the frozen Java target boundary.
- The original implementation worktree is read-only for this task.
- No Java, test, POM, schema, Provider, source, network, commit, or push work was performed.

## Changed files

- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `progress/business-flow-provenance-design-amendment.md`

## Verification

| Check | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Clean requested design branch. |
| `git rev-parse HEAD` | PASS | `dea5c1bd96987270ecdc0f8060b612599b8f51d9` |
| Changed-link check | PASS | `added_markdown_links=0`; the amendment adds no unresolved Markdown link target. |
| `git diff --check -- <three allowed files>` | PASS | No whitespace/error output. |
| Scope check | PASS | Only the two allowed analysis-step documents and this task-owned progress file are changed. |
| Maven/tests | NOT RUN | Docs-only task; explicitly prohibited. |

## Decisions

- `FlowFactViewV1.originFactArtifactRef` is the exact full `proven-facts.json` descriptor reference. Typed Gap evidence references are exact persisted `ArtifactReference` values; graph node IDs are not artifacts.
- `FlowGapOriginKindV1` is the closed three-value set `PROVEN_CODE_FACTS_GAP_LEDGER | FLOW_COMPILATION | CAPSULE_PROJECTION`; `originGapLedgerRef` is required-nullable and non-null only for the Step 04 origin. Compiler/budget Gaps gain no source Proof, and no carrier refers to itself.
- M3 normalization gives an exact Step 04 ledger row precedence over M1's entry-only copy, then admits an M1-only compiler Gap, then the single existing M2 budget Gap. Conflicting origins are fatal.
- The cutover is M2 v7 / public Capsule v5 / normalized public Gap v2, with M2 producer moduleVersion v6 and M3 producer moduleVersion v2. M1 v3, Step 04 v3, Flow slices v3, coverage v1, and entry disposition v1 remain unchanged; old carrier versions fail closed without compatibility readers.
- Program-only origin metadata changes persisted bytes/descriptor identities, not Fact/Gap/Flow/Capsule semantic IDs, eligibility, claim authority, or model-visible source material. Step 05 span budgets exclude it; Step 06 packet byte limits apply after origin stripping.
- Both local R0/R1/R2 and future process-model projections remove exactly `originFactArtifactRef`, `originKind`, and `originGapLedgerRef` before Provider-bound canonical bytes and hashes; neither path removes typed `evidenceRefs` or gains new model authority.
- `processJoinSignalId` remains the identity of one `ProcessJoinSignalV1` only. The provenance cutover cannot alter its preimage through nearby subsection wording.
- M3 fresh-reopens and reprojects through the existing projector, then compares the complete canonical M2 body produced by the M2-owned pure encoder. This is a deeper existing module interface, not a new subsystem, and performs no source parsing.
- ApplicationDiscovery `repositoryEntryCoverage.closed` remains an existing required v2 boolean. M3 consumes the complete real writer shape, rejects absence, and combines it with M1 local closure; no Step 02 edit or missing-to-true rule was added.

## Blockers

- None for the docs-only amendment.
- Existing domain-classification and promisor source-copy blockers remain out of scope and unchanged; this amendment neither solves nor weakens them.

## Exact next action

- Root reviews and publishes this coherent docs-only change through the design-publication gate. Only after that may Luna/Terra execute the recorded provenance/closure/replay RED/GREEN; Step 05 then pauses.

## Resume checks

- Reconfirm this worktree/branch and inspect only the three allowed paths.
- Preserve exactly 57 formal outputs, six Step 05 files, eight steps, nine chapters, the public Agent seam, and the Java target boundary.
- Do not investigate or implement beyond the completed ruling; do not add classifier, source-copy, Provider, or later-stage work.
