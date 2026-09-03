# Progress: M2 call-target ambiguity tests

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Establish a truthful public-seam RED for an ambiguous frozen-Java external call target. No production, design, POM, or unrelated test changes.
- Approved inputs: scoped AGENTS.md; the program-graphs detailed design; the program-graphs implementation backlog; current CallGraphBuilder and graph fixtures.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory

## Completed

- Read the M2/M4 contracts and the current `CallGraphBuilder` and public fixture seam.
- Audited the legal ambiguous-source candidates requested by the task. A Java overload such as
  `AuditClient.recordStatus(String)` plus `recordStatus(Integer)` called with `null` is parsed,
  but the current public builder cannot represent it as target ambiguity: `expressionType` does
  not recognize `NullLiteralExpr`, so the call deterministically becomes
  `CALL_ARGUMENT_TYPE_UNRESOLVED` before target lookup.
- An `Object`-typed argument against those overloads is likewise parsed but produces a synthesized
  `AuditClient#recordStatus(java.lang.Object)` lookup miss and therefore `CALL_TARGET_UNRESOLVED`.
- Duplicate methods with the same receiver FQN/name/parameter signature are not a legal Java
  overload shape; using duplicate source declarations would not be a truthful frozen-Java
  ambiguity fixture and would also collide in the current structure index.
- Existing public ambiguity paths were verified to be different contracts:
  `ENTRY_HANDLER_AMBIGUOUS` (entry overload), `MAPPER_JAVA_METHOD_AMBIGUOUS` (duplicate mapper
  catalog binding), and `CALL_TARGET_UNRESOLVED` (target lookup miss). None can be relabeled as
  `CALL_TARGET_AMBIGUOUS`.
- No test was added because a truthful RED cannot be established without first adding a new M2
  public candidate/disposition contract, which is production/design work outside this test slice.

## Current state

## Changed files

- This progress file only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n "CALL_TARGET_AMBIGUOUS|CALL_TARGET_UNRESOLVED|CALL_ARGUMENT_TYPE_UNRESOLVED|ENTRY_HANDLER_AMBIGUOUS|MAPPER_JAVA_METHOD_AMBIGUOUS" src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java` | PASS | Only the existing unresolved/entry/mapper reasons are implemented/tested; no `CALL_TARGET_AMBIGUOUS` path exists. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

## Blockers

- The requested M2 formal disposition `CALL_TARGET_AMBIGUOUS` is not exposed by the current
  public seam. Adding it requires Sol/ultra design authority followed by a production M2 RED/GREEN
  slice; it must not be faked in M4 or represented using a different Gap reason.

## Exact next action

Sol/ultra should decide and publish the M2 candidate-set contract first. After that contract exists,
Luna can add the M2 RED and then the M4 test that verifies it is carried through without selecting
an external target.

## Resume checks
