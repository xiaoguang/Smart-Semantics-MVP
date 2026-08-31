# Progress: Stage04 fifth-audit P1 tests

- Status: IN_PROGRESS
- Agent role: Stage04 fifth-audit test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add at most two new Stage04 test classes and at most eight `@Test` methods covering exactly the four P1 contracts in `progress/stage04-final-acceptance-review.md`: coherent model/receipt duplicate-flow identity rewrites; self-describing persisted typed Trace records; pre-allocation source/directory bounds; and post-start Round-1/Round-2 assembler/store failure with durable terminal lifecycle and provider-free stable re-entry.
- Approved inputs: scoped `AGENTS.md`; `progress/stage04-final-acceptance-review.md`; Stage04 design and current production/test seams.
- Current branch/worktree: Shared dirty worktree; preserve all unrelated parent/agent changes, especially existing Stage04 audit files.

## Completed

- Read scoped repository guidance and the final Stage04 acceptance review before test edits.
- Created this progress file before modifying tests.

## Current state

- Need inspect existing public/deep seams and fixture helpers before writing the two bounded test classes. No production/design edits are authorized.

## Changed files

- `progress/stage04-fifth-audit-tests.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |

## Decisions

- Use two classes grouped as archive/trace integrity and lifecycle/bounds. Keep test counts at or below eight and use finite small limit-plus-one fixtures.
- Run only the new selectors after test compilation is clean; expected result is clean assertion RED with no fixture construction errors.

## Blockers

None known.

## Exact next action

Inspect current Stage04 archive, Trace, source-registry, ledger, and public-agent seams, then add the minimal test-only contracts and run only their selectors.

## Resume checks

- Confirm no production/design file changed.
- Confirm new test classes contain no more than eight `@Test` methods total.
