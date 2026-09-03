# Progress: M2 call-target ambiguity algorithm design

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Specify the bounded, deterministic M2 Java call-candidate enumeration and resolution algorithm for `CALL_TARGET_AMBIGUOUS`, its shared Gap carrier, its M4 handoff, and the matching Luna/Terra guidance. Documentation only.
- Approved inputs: scoped `AGENTS.md`; both implementation plans; `docs/analysis-steps/03-program-graphs.md` M2/M4/M6; program-graphs backlog P3/P5; `progress/m2-call-target-ambiguity-tests.md`; current `CallGraphBuilder` and `CallGraphBuilderTest`.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the scoped rules, both implementation plans, the current program-graphs design/backlog, the prior Luna audit, and the current M2 production/test seams.
- Confirmed the current implementation jumps from inferred argument types to one synthesized signature, so a legal `null` overload ambiguity cannot yet reach a truthful `CALL_TARGET_AMBIGUOUS` disposition.
- Frozen the M2 candidate enumeration order, bounded type compatibility, `null` handling, visibility rules, cardinality decisions, duplicate/syntax/receiver failure classification, and exact Gap/accounting shape.
- Added one source-to-output example and unambiguous Luna RED / Terra GREEN guidance without changing any public Interface, graph, schema, or output count.
- Synchronized backlog P3/P5 so they distinguish the published target algorithm from the still-unimplemented current code.

## Current state

- The local design is complete and remains an unimplemented target contract. Current M2 maturity is still `PARTIAL`; only a later Luna/Terra work unit may change code or tests.

## Changed files

- `progress/m2-call-target-ambiguity-algorithm-design.md`
- `docs/analysis-steps/03-program-graphs.md`
- `docs/supplements/program-graphs-implementation-backlog.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `rg -n "CALL_TARGET_AMBIGUOUS|CALL_TARGET_UNRESOLVED|CALL_ARGUMENT_TYPE_UNRESOLVED" ...` | PASS | Current M2 has unresolved paths but no call-target ambiguity path; prior Luna progress records the same blocker. |
| `awk '/^~~~/ {count++} END {print count; exit(count % 2)}' docs/analysis-steps/03-program-graphs.md` | PASS | 34 tilde fences; balanced. |
| `test -f docs/analysis-steps/03-program-graphs.md` and backlog link target check | PASS | The relative P3 link resolves to the existing Stage 03 design. |
| snippet locator recomputation | PASS | The example invocation is exactly bytes `[205,235)`, line 10, columns `[12,42)`. |
| `rg -n '两个 exact target|双exact fatal' ...` | PASS | No stale ambiguity-as-fatal wording remains. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep one deep public seam: `CallGraphBuilder.buildCalls(...)`; candidate enumeration and bounded compatibility remain internal implementation details.
- Do not add a graph, artifact, public payload field, or detached candidate artifact.
- `CALL_TARGET_AMBIGUOUS` is an M2 CALL local-Gap reason. Duplicate canonical signatures or broken M1 endpoint closure remain fatal and cannot be mislabeled as overload ambiguity.
- The existing `GraphGapDraft` wire accepts this reason without a shape/version change; no reason-family/wire conflict was found. The implementation still must add it to the versioned CALL reason validation required by the design.

## Blockers

- None.

## Exact next action

Parent Agent publishes this docs-only contract to `origin/main`; only afterward Luna adds the exact M2 public-seam RED described in M2.1.

## Resume checks

- Confirm the parent commit contains only intended design/progress paths from the shared dirty worktree.
- Before implementation, verify current M2 still lacks `CALL_TARGET_AMBIGUOUS`; the first Luna test must fail for that behavior rather than a fixture/setup error.
