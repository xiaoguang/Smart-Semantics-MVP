# Progress: program graph input identity

- Status: IN_PROGRESS
- Agent role: Terra/xhigh implementation repair against the published Program Graphs M1/M2 identity contract
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add the existing required source/discovery control and artifact-reference lineage to the M1 structure draft so M2 can reject mixed reopened inputs.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `7beb3ff`, M1 persisted input boundary, and M2 `CallGraphInputs` contract.
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Verified that the published M2 contract requires the M1 structure draft and its reopened source/discovery inputs to share snapshot, controls, and upstream artifact references.
- Verified that the current M1 draft exposes only snapshot, application profile, and entry IDs. Therefore M2 cannot prove the required controls/reference equality from public values.

## Current state

- This is an implementation omission against an already-published design, not a design change. M2 work is paused after its current green slice until the public draft can carry and validate the required lineage.

## Changed files

- `progress/program-graph-input-identity.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips before lineage-hardening test is added. |

## Decisions

- Do not weaken M2 to trust the currently incomplete M1 draft. Extend M1’s typed output only with the source/discovery references and controls already required by the published contract, then make M2 compare them exactly.

## Blockers

- None.

## Exact next action

- Write one public-seam RED proving `CallGraphInputs` rejects an M1 structure draft whose source controls or upstream artifact references differ from the same reopened input basis.

## Resume checks

- Read this file, run `git status --short`, confirm the published M2 contract at `7beb3ff`, then run the direct targeted selector recorded above.
