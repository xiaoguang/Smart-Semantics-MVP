# Progress: dry model packet discipline

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Publish the cross-Flow model-reading, microcycle-testing, and progressive-packet rules before further Step 06 production changes. Preserve the eight-step workflow, the nine-section contract, Step 06's fifteen outputs, and the global 57-output accounting.
- Approved inputs: User instructions on semantic priority, small clean model inputs, progressive model evaluation, Luna/high live profile, existing authoritative design and Step 06 contract.
- Current branch/worktree: `codex/source-analysis-dry-model-contract`; `/private/tmp/linguan-source-analysis-dry-model-contract`.

## Completed

- Read the scoped Agent instructions, current target design, Step 06 design, and the M6 implementation review.
- Published the `FlowReaderPacketV1` and packet-local `ProcessModelPacketV2` design: the program retains full audit material and binds every model key back to it.
- Added one-behavior RED/GREEN microcycles and one-Flow-before-two-Flow progressive execution rules to the Agent instructions, overall design, and Step 06 design.

## Current state

- Docs-only amendment is complete and ready for the required design-publication commit/PR. No Java, tests, schemas, runtime artifacts, source capture, or live Provider changed.

## Changed files

- `AGENTS.md` — durable model-input and microcycle rules.
- `docs/DESIGN.md` — architecture-level reader-packet boundary and test execution discipline.
- `docs/analysis-steps/06-flow-interpretation.md` — exact reader-packet, binding, progressive execution, and M6–M8 test/implementation contract.
- `progress/dry-model-packet-discipline.md` — this recovery record.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read scoped instructions and target Step 06 design | PASS | Existing design retains eight steps, fifteen Step 06 outputs, and 57 total outputs. |
| Targeted terminology scan | PASS | No stale `ProcessModelPacketV1`, `ProcessModelRequestV1`, `BusinessProcessTaskShardV1`, `capsuleView`, or full model-view wire remains in the edited design surfaces. |
| UTF-8/final-newline check | PASS | All four edited Markdown files are valid UTF-8 and newline-terminated. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Models receive only packet-local business-reading keys, concise activity/condition/outcome material, finite vocabulary, necessary short excerpts, and repository-relative file/line context. Program-side artifacts retain all identities, hashes, Proof/Evidence closure, controls, receipts, and archives.
- One behavior is tested and made green at a time. One Flow packet is tried before a process group, and previously verified packets are not replayed merely because a later packet fails.

## Blockers

- None. M6 hardening remains a separate production work item after this docs-only publication is merged.

## Exact next action

- Commit and merge this docs-only amendment before the next Step 06 production RED. Then start the M6 internal-registry/reopen microcycle on a fresh RED.

## Resume checks

- Verify this clean worktree is still on `codex/source-analysis-dry-model-contract` with only the three intended documentation files and this progress file changed.
- Confirm `origin/main` contains the docs-only commit before any production modification.
