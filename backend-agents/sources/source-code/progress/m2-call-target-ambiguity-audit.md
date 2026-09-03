# Progress: M2 call-target ambiguity implementation audit

- Status: COMPLETE
- Agent role: Sol/ultra design audit
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Synchronize the ProgramGraphs implementation audit and backlog with the verified absence of a current M2 `CALL_TARGET_AMBIGUOUS` output path. Do not redesign the approved M2/M4 contract or change production, tests, POM, commit, or push state.
- Working directory: `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Inputs read

- `AGENTS.md`
- `docs/analysis-steps/03-program-graphs.md` M2 and M4 boundary contracts
- `docs/supplements/program-graphs-implementation-backlog.md`
- `progress/m4-ambiguous-boundary-gap-tests.md`

## Verified fact

The target design requires M4 not to guess when M2 reports `CALL_TARGET_AMBIGUOUS`. The current implementation does not yet expose that formal M2 output path: overloaded entry resolution uses `ENTRY_HANDLER_AMBIGUOUS`, duplicate Mapper binding uses `MAPPER_JAVA_METHOD_AMBIGUOUS`, and an unresolved Java invocation target uses `CALL_TARGET_UNRESOLVED`. Therefore a truthful M2 call-target ambiguity fixture cannot currently be constructed through the public seam.

## Completed documentation-only synchronization

- Updated the ProgramGraphs current implementation audit to name the three implemented Gap paths and state explicitly that the formal M2 `CALL_TARGET_AMBIGUOUS` path is not implemented.
- Updated P5 to sequence the ambiguity RED after a real M2 public-seam output exists; fake predecessors and substitute Gap reasons remain forbidden.
- Kept the approved M2/M4 target contract, wire, failure semantics, and graph cardinality unchanged.

## Changed files

- This progress file (owned by this agent).
- `docs/analysis-steps/03-program-graphs.md` (current implementation audit only).
- `docs/supplements/program-graphs-implementation-backlog.md` (P5 current gap and Luna RED ordering only).

## Verification

| Check | Result |
| --- | --- |
| Resolve every local Markdown link in the three changed files from its containing directory | PASS (`LOCAL_MARKDOWN_LINKS_OK`) |
| Grep the target design, backlog, and two audit records for the four relevant Gap reasons | PASS; target contract and current implementation wording are distinct |
| Inspect the exact documentation diff | PASS; only the implementation-audit row and P5 implementation guidance changed |
| `git diff --check` | PASS |

No production code, test, fixture, POM, schema, commit, push, source/model/network call, or test execution was performed.

## Design-gate note

This audit does not reopen or change the approved M2/M4 design. If implementation of the missing M2 output path proceeds, the repository publication rule still requires the coherent documentation state to be committed and fast-forward pushed before a new Luna RED or Terra GREEN begins; this agent was explicitly not authorized to commit or push.
