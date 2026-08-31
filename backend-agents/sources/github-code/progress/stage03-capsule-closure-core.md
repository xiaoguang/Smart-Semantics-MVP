# Progress: Stage03 capsule closure core

- Status: COMPLETE
- Agent role: Stage03 Capsule-closure production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add fail-closed Stage01/02-to-Stage03 capsule closure validation and invoke it before any provider call.
- Approved inputs: Scoped AGENTS, TDD and systematic-debugging guidance, Stage03 capsule-closure
  contract, and public Stage01/02/03 records plus existing generator implementation.
- Current branch/worktree: Shared worktree; preserve all unrelated existing changes.

## Completed

- Created this owned progress file before production edits.
- Read the scoped repository instructions, current worktree status, and the required progress template.
- Read the dedicated public closure contract, its completed test-author progress, the M4/M5 design
  boundary, existing Generator pre-provider flow, and the relevant Stage01/Stage02 public records.
- Reproduced the dedicated RED: test compilation reaches 48 sources then fails only because the
  approved `Stage03CapsuleClosureValidator` type is absent (2 `cannot find symbol` errors at the
  two direct validator calls); no test method or provider ran.
- Traced the root cause: `CapsuleContext.validateClosure()` checks only local Flow/Capsule atom,
  outcome, and Gap sets. It does not independently reopen Stage01 ProofPack membership, frozen
  bytes, span identity, or projection-obligation closure.
- Added the package-private `Stage03CapsuleClosureValidator` and verified the TDD cycle: the
  initial missing-seam compile RED is replaced by a 2-test GREEN covering honest acceptance and
  seven independent single-field closure mutations.
- Corrected one over-strict provisional check after a failing honest input: Stage01 AST ProofNode
  locators retain their Stage01 parser coordinates, so their validation reopens byte range and
  span SHA; Stage02 `ModelEvidenceSpan` locators alone are recomputed with the projection
  line/column algorithm. The public closure test is GREEN after that single correction.
- Integrated a first Generator pass that validates every sorted Flow/Capsule pair before entering
  the interpretation loop, so a later invalid Capsule cannot permit an earlier Provider call.
- Full direct Stage03 selector is GREEN: 59 tests with zero failures or errors.

## Current state

- The dedicated closure selector, complete Stage03 selector, and scoped whitespace verification
  all pass.

## Changed files

- progress/stage03-capsule-closure-core.md
- src/main/java/com/linguan/codemd/stage03/Stage03CapsuleClosureValidator.java
- src/main/java/com/linguan/codemd/stage03/Stage03Generator.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03CapsuleClosureTest test` | RED | Test compilation fails only with 2 missing `Stage03CapsuleClosureValidator` symbols; no test method/provider ran. |
| `mvn -Dtest=Stage03CapsuleClosureTest test` | provisional RED | 2 tests / 1 failure: honest input exposed an over-strict Stage01 ProofNode line/column recomputation. |
| `mvn -Dtest=Stage03CapsuleClosureTest test` | GREEN | 2 tests, 0 failures/errors after byte-range-only ProofNode correction. |
| `mvn -Dtest=Stage03CapsuleClosureTest test` | GREEN | 2 tests, 0 failures/errors after Generator integration. |
| `mvn -Dtest='Stage03*Test' test` | GREEN | 59 tests, 0 failures/errors. |
| `git diff --check -- src/main/java/com/linguan/codemd/stage03/Stage03CapsuleClosureValidator.java src/main/java/com/linguan/codemd/stage03/Stage03Generator.java progress/stage03-capsule-closure-core.md` | GREEN | No whitespace errors. |

## Decisions

- Limit production changes to the new package-level validator and the Stage03Generator call site.
- Normalize all closure violations to the stable `CAPSULE_CLOSURE_BROKEN` Stage03 error code.
- Use Stage01 facts/proofs/verified snapshot as the source of truth; Flow/Capsule records remain
  projections and may not introduce local-only IDs, atom fields, spans, or obligation subjects.
- The preflight runs in a separate complete loop over sorted FlowSlice IDs before `interpret`,
  rather than once per individual Provider turn, preserving the no-provider-before-closure gate
  when multiple flows are present.
- Stage01 ProofNode byte spans are source closure evidence, while Stage02 ModelEvidenceSpan
  locators are the projection contract whose derived line/column values can be revalidated.

## Blockers

- None.

## Exact next action

- None; the requested capsule-closure vertical slice is complete.

## Resume checks

- Confirm all generated evidence is validated before a provider invocation and no unrelated paths changed.
