# Progress: Normalized guard condition design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Publish the minimal Step03/04 contract correction that exposes a guard's normalized Java condition separately from its technical canonical node value. No Java, test, schema, source capture, model, or deployment changes.
- Approved inputs: User-confirmed independent guard-condition Fact design; published f332e0c; existing Step03/04 design documents; guarded persisted-fixture RED from the isolated Step04 delivery worktree.
- Current branch/worktree: codex/source-analysis-normalized-guard-design at /private/tmp/linguan-source-analysis-normalized-guard-design/backend-agents/sources/source-code

## Completed

- Confirmed that current control-flow public nodes persist only `canonicalValue`, whose current guard value includes method-signature identity as well as condition text.
- Confirmed that Step04 must not parse or strip that technical key to create `CONTROL_CONDITION`.
- Updated the Step03 ControlFlowNode/public wire, Step04 prerequisite and overall descriptor example: `normalizedCondition` is required-nullable, non-null only for `GUARD`, and control-flow public schema is v2.

## Current state

The target contract already requires a GUARD's `normalizedCondition`; this correction makes the missing Step03 public field, its exact normalization rule, its control-graph v2 schema, and the Step04 input precondition explicit. The correction does not change the five-graph count, Step04/M3 public file count, or business semantics.

## Changed files

- progress/normalized-guard-condition-design.md
- docs/DESIGN.md
- docs/analysis-steps/03-program-graphs.md
- docs/analysis-steps/04-proven-code-facts.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| design/implementation comparison | PASS | Current graph wire has canonicalValue only; the target needs a dedicated normalizedCondition field. |
| cross-step wording review | PASS | Step03 produces the value; Step04 consumes it without parsing canonicalValue; the graph count and artifact count remain unchanged. |

## Decisions

- A JavaParser AST expression's deterministic `toString()` under the frozen parser/toolchain is the v0 `java-guard-condition-normalizer-v1` output.
- Technical `canonicalValue` remains an identity/display field and is never parsed by Step04 to recover a business-relevant condition.

## Blockers

- None.

## Exact next action

Implement the published Step03 control-wire correction from a fresh code branch, then rebase the isolated Step04 guard-fact branch and resume its persisted-wire RED.

## Resume checks

- Re-read this file, verify the worktree is based on f332e0c, and confirm the three design files agree on control-flow schema v2.
