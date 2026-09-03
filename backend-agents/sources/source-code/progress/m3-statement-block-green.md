# Progress: M3 statement blocks

- Status: IN_PROGRESS
- Agent role: Terra/xhigh implementation of the published M3 AST-block prerequisite
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Turn the approved two-statement BASIC_BLOCK RED into a source-backed M3 implementation,
  while preserving M2 call/return projection for Java methods with and without bodies.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md` at `2c05e52`, the completed Luna
  RED in `progress/m3-ast-block-closure-tests.md`, and sealed M1/M2 public seams.
- Current branch/worktree: `codex/source-analysis-program-graphs` at
  `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Rebased the M3 work branch onto the published M4 intra-method data-flow contract.
- Replaced the method-wide BASIC_BLOCK provenance with one M3 BASIC_BLOCK per top-level Java
  statement, carrying that statement's exact verified source range.
- Added deterministic linear `NEXT` edges between top-level statement blocks for the installed
  no-loop, no-direct-throw slice; a call now uses the block that contains its source span.
- Preserved M2 Java-method-to-MyBatis-XML CALL/RETURN projection for a method declaration with no
  body, without inventing a Java BASIC_BLOCK for it.
- Closed the next predecessor-closure RED: when a supported guard has a following top-level
  statement, its nonterminal branch now targets that statement block, which then has a `NEXT` edge
  to its contained call site. A call nested inside the guard itself retains the direct branch shape.

## Current state

- The focused M3 selector is green with 11 tests, including the new two-statement block test.
- The two public M3 statement-block tests are green. More complex branch joins, loops, nested
  statement ownership, and multi-entry ownership still need their own bounded REDs before M4 may
  claim general reaching definitions.

## Changed files

- `progress/m3-statement-block-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ControlFlowGraphBuilder.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Expected RED | 11 tests: two-statement Service produced one method-wide BASIC_BLOCK. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Compile failure | `NodeList<Statement>` and an empty `List` were mixed in the new block collector. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | Regression | 11 tests: Mapper interface's M2 XML binding was skipped by the no-body return. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 11 tests, 0 failures/errors/skips: exact statement blocks and M2 XML binding both preserved. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ControlFlowGraphBuilderTest test` | PASS | 12 tests, 0 failures/errors/skips: guard nonterminal branch enters the post-if BASIC_BLOCK before its call site. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Updated Java sources formatted. |

## Decisions

- A Java declaration with no body is not a statement block. It may still project an existing,
  independently proven Mapper Java-to-XML CALL/RETURN pair.
- This change creates no new wire field, artifact, public method, or M1/M2/M3 schema.

## Blockers

- None.

## Exact next action

- Add the next public RED for AST-block predecessor closure before implementing local reaching
  definitions in M4.

## Resume checks

- Read this file, run `git status --short`, then run only the M3 selector above before adding the
  next RED.
