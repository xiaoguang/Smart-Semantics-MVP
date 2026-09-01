# Progress: Stage 04 runtime, archive, Trace and recovery design

- Status: COMPLETE
- Agent role: Stage 04 M8 detailed-design owner
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30T11:12:09-02:30
- Last updated: 2026-08-30T11:32:36-02:30
- Scope: Design only the target M8 orchestration, immutable Candidate archive, Trace, validation and recovery stage.
- Approved inputs: Scoped AGENTS files; current DESIGN.md M8 contract; Stage 01–03 designs and implementation; current Java Interface, CLI, archive, runtime-receipt and tests; scripted providers only.
- Current branch/worktree: codex/github-code-design-walkthrough in /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2

## Completed

- Read all applicable AGENTS files, the progress template and current root implementation progress.
- Located the M8 target contract and the existing public/archive/runtime/Trace seams to inspect.
- Read the Stage 01–03 detailed designs and the complete M8 target section in `DESIGN.md`.
- Inspected the current POC Java Interface, six CLI commands, eight-file archive, validation/Trace implementation, runtime-admission seam, Stage 03 runtime records and their direct tests.
- Created `docs/stages/04-runtime-archive-trace-recovery.md` as the implementation-ready M8 design, including exact public seams, source registration and configuration, identity chain, Reader Candidate state machine, lifecycle-aware Provider protocol, immutable artifact layout, deterministic revalidation, source-reopening Trace, CLI/HTTP adapters, stable failures, TDD slices and acceptance gates.
- Carried one synthetic inventory flow from `Stage03Result` through started-event persistence, Candidate installation, validation, recovery and formula Trace.
- Linked Stage 04 from the README reading order and M8 capability row, and from the DESIGN M8 section and ARCH-10/11 maturity rows.
- Kept Stage 03 acceptance unchanged while making M8 status consistently `DESIGNED / NOT IMPLEMENTED`.

## Current state

- The Stage 04 design and navigation/status synchronization are complete; M8 remains explicitly `DESIGNED / NOT IMPLEMENTED`.
- Existing POC archive, runtime admission and CLI are documented as independent seams, not as wired M1–M8 capabilities.
- The most important runtime correction is explicit: the Provider bridge must synchronously persist and acknowledge `thread.started` before accepting response content; a started round can never be replayed.

## Changed files

- `progress/stage04-runtime-archive-trace-design.md`
- `docs/stages/04-runtime-archive-trace-recovery.md`
- `DESIGN.md`
- `README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing parallel/unrelated changes identified and preserved. |
| `rg -n '^#{1,6} \|^```' docs/stages/04-runtime-archive-trace-recovery.md` | PASS | 17 ordered main sections; 24 fence markers form 12 closed blocks. |
| explicit `test -e` over all relative Markdown links | PASS | DESIGN, Stage 03 and all seven linked Java sources exist. |
| `git diff --check -- docs/stages/04-runtime-archive-trace-recovery.md progress/stage04-runtime-archive-trace-design.md` | PASS | No whitespace errors. |
| `test -f docs/stages/04-runtime-archive-trace-recovery.md` | PASS | New README/DESIGN Stage 04 target exists. |
| `rg -n 'Stage 04|04-runtime-archive-trace-recovery|M8 archive/Trace/recovery|ARCH-10 Candidate|ARCH-11 公共' DESIGN.md README.md` | PASS | Reading order, M8 target section and maturity rows all link the detailed design; Stage 03 accepted rows remain unchanged. |
| `git diff --check -- DESIGN.md README.md progress/stage04-runtime-archive-trace-design.md docs/stages/04-runtime-archive-trace-recovery.md` | PASS | No whitespace errors after navigation sync. |

## Decisions

- Describe one target M8 path rather than a POC branch.
- Keep public orchestration deep: Java, CLI and future loopback HTTP translate into the same core interface and cannot bypass validation or archive rules.
- Automated tests require no live Provider; scripted/recorded adapters must exercise the same started-event protocol.
- A machine-fatal Stage 03 run creates a terminal round record, not a selectable Candidate. Explicit Reader Candidate Round 2 applies to an immutable Round 1 Candidate with archived named review findings; it cannot disguise a transport/schema failure as a parent Candidate.

## Blockers

- None.

## Exact next action

- Parent agent reviews/integrates the completed Stage 04 design and navigation; implementation begins with the Luna/xhigh identity/state-machine RED slice in Stage 04 section 16.

## Resume checks

- Read this file, the current authority `docs/DESIGN.md`, and `progress/target-architecture-implementation.md`; interpret this record's historical “M8” references in their original context.
- Run `git status --short` and preserve all files outside this task's two owned paths.
