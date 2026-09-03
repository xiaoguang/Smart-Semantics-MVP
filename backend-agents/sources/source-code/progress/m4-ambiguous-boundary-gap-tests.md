# Progress: M4 ambiguous external boundary gap test

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam M4 test for an M2-ambiguous external Java call. No production, design, POM, or unrelated test changes.
- Approved inputs: scoped AGENTS.md; docs/analysis-steps/03-program-graphs.md; existing graph tests and frozen fixtures.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory

## Completed

- Read the M4 boundary contract and the public `CallGraphBuilder`/`DataFlowGraphBuilder` seams.
- Checked every existing M2 ambiguity path and fixture. The current M2 implementation emits
  `ENTRY_HANDLER_AMBIGUOUS` for an overloaded entry, `MAPPER_JAVA_METHOD_AMBIGUOUS` for duplicate
  Mapper catalog bindings, and `CALL_TARGET_UNRESOLVED` when a Java call target cannot be found.
- Verified that `CALL_TARGET_AMBIGUOUS` does not exist in current production or graph tests.
- Did not add a test: no legal frozen-Java fixture can exercise the requested contract without
  inventing a new M2 behavior or constructing a fake/invalid predecessor. This preserves the
  requirement not to fabricate ambiguity.

## Current state

The requested M4 test cannot establish a truthful RED against the current public seam. `CallGraphBuilder`
derives one target signature from the receiver's statically declared field type and the inferred
argument types. It either finds one method, records `CALL_TARGET_UNRESOLVED`, or stops earlier for
an unresolved argument/receiver. Java overload resolution is therefore not represented as an M2
ambiguous target. Multiple Mapper catalog candidates are a binding ambiguity after an exact Java
call target has already been produced, so using that fixture would test a different contract.

## Changed files

- This progress file (owned by this agent).

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check` | PASS | No whitespace errors in the shared worktree. |
| `rg -n "CALL_TARGET_AMBIGUOUS\|CALL_TARGET_UNRESOLVED\|ENTRY_HANDLER_AMBIGUOUS\|MAPPER_JAVA_METHOD_AMBIGUOUS" src/main/java/.../CallGraphBuilder.java src/test/java/.../CallGraphBuilderTest.java` | PASS (inspection) | Only `CALL_TARGET_UNRESOLVED`, `ENTRY_HANDLER_AMBIGUOUS`, and `MAPPER_JAVA_METHOD_AMBIGUOUS` are present; no `CALL_TARGET_AMBIGUOUS`. |

## Decisions

## Blockers

- The M2 public contract has no `CALL_TARGET_AMBIGUOUS` reason or legal fixture path. Adding one
  would be a production/design change outside this Luna test slice and requires Sol/ultra design
  authority. Existing tests remain unchanged.

## Exact next action

Sol/ultra should decide whether M2 should add a distinct external-target ambiguity contract. If it
does, add that design and M2 RED first; then create the M4 fail-closed test from the new public seam.

## Resume checks

Read this file and verify that this task changed only its progress record; no test or production path
was changed because the requested ambiguity cannot currently be produced honestly.
