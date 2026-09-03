# Progress: M2 multi-entry owner-union review

- Status: COMPLETE
- Agent role: Luna/xhigh read-only code review
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Read-only review of the M2.1 multi-entry physical call-site owner-union change
- Approved inputs: scoped AGENTS.md; `docs/analysis-steps/03-program-graphs.md` M2.1; `docs/supplements/program-graphs-implementation-backlog.md` P3; `CallGraphBuilder.java`; `CallGraphBuilderTest.java`; multi-entry fixture; M2 progress files
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the repository and source-scoped rules and the M2.1/P3 contract.
- Reviewed the owner-union implementation, the two-entry fixture, and the existing M4 ambiguous-call handoff test without running Maven.

## Current state

- No P0 found in the bounded owner-union change.
- P1: at `CallGraphBuilder.java:196-217`, the call-node identity uses the caller signature, receiver/name/argument descriptors, file ID, and rule, but not the exact call-expression span. Two distinct same-shaped calls in one caller therefore receive the same node ID; `mergeCallNode` then sees different provenance at `:418-431` and fails with `GraphReferenceException` instead of representing two physical call sites. This violates M2.1's `fileId + exact call span + caller signature` site key and can stop a repository analysis on a common pattern.
- P1: `CallGraphDraft` graph identity material at `CallGraphBuilder.java:578-616` includes exact element IDs and Gap material, but not the serialized call-node owner sets. Adding a second entry owner changes the graph payload while leaving `graphId` unchanged. If graph IDs are used for downstream reuse/identity (as the draft/publication contract requires), two different ownership results can be treated as the same graph. This needs either an owner-inclusive identity calculation or an explicitly documented exclusion before the M2 step can be accepted.
- P2: the multi-entry test in `CallGraphBuilderTest.java:116-151` covers exact-node owner union and deterministic edge/candidate cardinality, but has no shared multi-entry local-Gap fixture asserting owner union, rebuilt Gap identity, and one coverage disposition. The implementation path at `CallGraphBuilder.java:434-481` is present and appears correct, but is not proven by the public test.
- The exact/return edge identity is not split by entry, and the reviewed owner-union change does not alter the M2.1 null/overload/ambiguity branch or the existing M4 handoff behavior.

## Changed files

- `progress/m2-owner-union-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only source/design inspection | PASS | Reviewed only the requested M2.1 implementation, test, fixture, and progress scope; no Maven run |
| `git diff --check -- progress/m2-owner-union-review.md` | PASS | No whitespace errors |

## Decisions

- Findings are classified against the M2.1 contract, not only the current four-file fixture.
- The owner merge itself is considered deterministic for the fixture: node and Gap aggregation are keyed by a stable candidate/node key, and public lists are sorted by their record constructors/finish path.
- The missing Gap test is reported separately from the identity defect because the implementation contains a Gap-owner merge path but the requested public seam does not prove it.

## Blockers

- None for this read-only review.

## Exact next action

- Parent should address the two P1 identity findings before accepting the M2.1
  owner-union slice, and may add the P2 shared-Gap test in a separate Luna task.

## Resume checks

- Re-read this file and inspect only the M2.1 files if resumed.
- Do not edit production code, tests, fixtures, design, POM, or another agent's progress file.
- Do not run Maven or any live model/provider/source operation.
