# Progress: direct-call source context

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Improve the direct-entry BusinessMaterialBuilder path so a model packet can contain a
  uniquely resolved, direct same-repository Java call target in addition to the HTTP handler. This
  is generic source-context selection, not a business-rule parser and not a replacement for the
  persisted graph path.
- Approved inputs: frozen Step01 source, Step02 entries, existing generic JavaParser dependency,
  direct-entry material route and business-first design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Inspected the real fixed jshERP material output. A typical `batchSetStatus` entry contains the
  controller handler and its `personService.batchSetStatus(status, ids)` invocation, but not the
  target service method. That makes reliable business interpretation needlessly shallow.
- Confirmed the existing generic CallGraphBuilder already treats a static field receiver plus a
  unique target as a safe Java relation. This work will use the same conservative shape only to
  select adjacent source text for the model; it will not emit a graph edge or declare a business
  relationship.
- Added the focused RED and made it GREEN on the guarded two-entry Java fixture: an entry handler
  and its uniquely resolved direct target appear as two bounded source references and model-safe
  snippets.
- Completed the real fixed jshERP planning run after the selector change. Of 337 material packets,
  281 contain one or more uniquely resolved direct target snippets; all packets stay within the
  four-reference profile limit and no Provider was called.

## Current state

- Complete. Ambiguous, unresolved and non-field calls retain handler-only material; direct target
  context is optional and never changes entry coverage or technical conclusions.

## Changed files

- `progress/direct-call-source-context.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilderFallbackTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| fixed jshERP direct-material acceptance | PASS | 337 handler-only fallback materials, zero Provider calls. |
| focused direct-context selector | RED then PASS | Initial output had only the handler; the direct target appears after the bounded selector implementation. |
| material builder regression plus fixed planner | PASS | 9 tests; all 337 entries are covered; 281 packets receive direct context; zero Provider calls. |

## Decisions

- Do not require Symbol Solver, complete overload resolution or service naming conventions. A
  direct context target is selected only when owner type, method name and arity yield exactly one
  repository method.
- Do not give the model paths, hashes, graph IDs or proof IDs. The program-side source references
  retain locators, while the model sees only short refs and snippets.

## Blockers

- None for the bounded Java selection. A later real Luna call remains blocked on a concrete Codex
  CLI compatibility hypothesis, so this work uses no product Provider.

## Exact next action

- Add the focused material-builder RED using the guarded OrderController fixture, then implement
  the one-hop direct target selection and run only the material-builder selectors.

## Resume checks

- Verify every selected target is a unique direct field-receiver method in the same frozen source
  set, reference count obeys the existing profile budget, and unresolved/ambiguous calls preserve
  handler-only output.
