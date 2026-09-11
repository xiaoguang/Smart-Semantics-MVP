# Progress: business-flow provenance spec review

- Status: COMPLETE
- Agent role: Luna/xhigh bounded design/spec reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Review only the uncommitted Step 05 and Step 06 design changes against the approved closeout contract ruling.
- Approved inputs: Design-worktree docs/analysis-steps/05-business-flows.md and 06-flow-interpretation.md; implementation-worktree progress/business-flows-closeout-contract-ruling.md; applicable AGENTS and existing Step 06 consumers.
- Current branch/worktree: implementation branch/worktree shared at /private/tmp/linguan-source-analysis-process-design; design diff reviewed read-only from /private/tmp/linguan-flow-provenance-docs.EomKSG.

## Completed

- Read the implementation/design worktree AGENTS files, the source progress template, the spec-review guidance, the approved closeout ruling, and the complete uncommitted Step 05/06 diff.
- Checked the requested provenance origins, scope/denominator preservation, M3 replay, versions, Step 06 readers, local R0/R1/R2 material, process-packet stripping, budgets, authority boundaries, and the 57-output invariant.
- Confirmed the composite Step 04 candidate key format (`entryId|boundaryNodeId|candidateFactKey`) remains an opaque single string in `affectedSemanticIds[]`; no reviewed consumer splits it or imposes a conflicting separator rule.

## Current state

- The design diff is otherwise aligned with the ruling: three finite Gap origins, typed ArtifactReferences, required-nullable ledger provenance, no fabricated budget ledger/evidence, fresh M3 re-projection and byte comparison, unchanged five semantic files/57 reader-visible outputs, and no new model authority.
- One required migration rule is underspecified for existing local R0/R1/R2 Provider inputs; one nearby signal-identity paragraph became ambiguous after the new provenance subsection insertion.

## Changed files

- progress/business-flow-provenance-spec-review.md (owned; intent-to-add)

- No design-worktree files changed.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` in implementation worktree | PASS | Preserved unrelated shared WIP; only this progress file was newly owned. |
| `git add -N -- progress/business-flow-provenance-spec-review.md` | PASS | Intent-to-add completed before review notes were edited. |
| `git status --short` in design worktree | PASS | Only `docs/analysis-steps/05-business-flows.md` and `docs/analysis-steps/06-flow-interpretation.md` are modified. |
| `git diff --check -- docs/analysis-steps/05-business-flows.md docs/analysis-steps/06-flow-interpretation.md` | PASS | No whitespace errors. |
| Maven/tests | NOT RUN | Spec review only; explicitly no Maven lease. |

## Decisions

### [P1] Strip new program-only origins from local R0/R1/R2 inputs

- `docs/analysis-steps/05-business-flows.md` makes `originFactArtifactRef`, `originKind`, and `originGapLedgerRef` program-only provenance and requires them to be removed before Provider material. The Step 06 amendment at `docs/analysis-steps/06-flow-interpretation.md:1199` only specifies stripping them while building the future cross-Flow `ProcessModelPacketV1`.
- Existing local-R0/R1/R2 contract text at `docs/analysis-steps/06-flow-interpretation.md:199-201` still says each request contains the complete Capsule view. The current consumers also deep-copy the complete Capsule into `capsuleView` (`RegistryProposalTaskCompiler.java:293`, `FiniteKeyFlowTaskCompiler.java:307`). Once the new Step 05 fields exist, the normative design does not say whether local Provider bytes/request hashes include or exclude them.
- Add one explicit local-material rule covering R0, R1, and R2: remove exactly these program-only origin fields before canonical input bytes and request hashes; preserve typed `evidenceRefs[]`; apply the same path-free/program-only boundary validator used for each local request. This is a bounded migration clarification, not a new feature or schema redesign.

### [P2] Make `processJoinSignalId` hash scope explicit

- In `docs/analysis-steps/05-business-flows.md:430-479`, the new Fact/Gap provenance subsection is inserted between the `ProcessJoinSignalV1` field record and the existing `processJoinSignalId` formula. The formula’s “all above fields” wording can now be read as including Gap provenance fields, despite those fields not belonging to `ProcessJoinSignalV1`.
- Move the hash/specimen back under the signal subsection or state explicitly that the preimage is only `ProcessJoinSignalV1` fields (excluding only its self ID). This preserves the existing signal identity contract without redesign.

### Reviewed with no finding

- Step 04 composite candidate-denominator keys are preserved verbatim as opaque `affectedSemanticIds[]` values; current Step 06/07 contracts use string-set equality/union and do not parse the `|` separators.
- The finite origin precedence, source-vs-program Gap authority, no-self-reference budget Gap, full M3 replay/byte comparison, and Step 06 packet stripping of program-only origins are coherent for P1/P2.
- M2/public/normalized Gap version changes are coherent with the ruling (`v7`/`v5`/`v2`), while the current v4 local reader audit is an explicitly pre-cutover implementation fact that still needs the bounded migration above. No design contradiction or authority expansion was assigned to that underimplementation.
- No change alters Provider retry/budget semantics, the 57 reader-visible output count, real-source truth boundary, or the existing `DOMAIN_SPECIFIC` limitation.

## Blockers

- None for this read-only review. Findings are actionable documentation clarifications; no code or Maven verification is authorized in this slice.

## Exact next action

- Root/design owner should amend the two noted passages, then route any implementation migration through the existing bounded Step 06 reader tests. Do not edit the design worktree in this task.

## Resume checks

- Keep the design worktree read-only and do not run Maven.
- Preserve all unrelated implementation-worktree WIP and the intent-to-add state of this review progress.
