# Progress: Stage 04 immutable archive contract correction

- Status: COMPLETE
- Agent role: Design contract correction
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30T13:03:02-02:30
- Last updated: 2026-08-30T13:23:11-02:30
- Scope: Audit the current Stage 04 archive against zero-Provider deterministic revalidation and precise Trace, then correct only the Stage 04 detailed design.
- Approved inputs: Scoped AGENTS rules; Stage 04 design; current Stage 03/04 records, implementations, tests, and D2 progress; no live model, network, source refresh, customer execution, or generated candidate run.
- Current branch/worktree: codex/github-code-design-walkthrough; shared dirty worktree preserved.

## Completed

- Read repository, prototype, backend-Agent, and GitHub-Code-Agent scoped instructions.
- Read the codebase-design and domain-modeling skill instructions relevant to the archive seam and terminology.
- Confirmed the working tree contains substantial pre-existing Stage 01–04 and documentation changes that must be preserved.
- Audited the current exact 17-artifact Candidate projection, store, fresh validation, factual Trace, D2 typed-Trace tests/progress, Stage 03 request/result/task/response records, and lifecycle bridge.
- Proved the existing archive is not a sufficient preimage for Stage 03 admission replay: it omits the six registry contents, task input/output schemas, canonical R1/R2 responses, complete rootless Stage 02/03 controls, and lifecycle-event binding.
- Proved the same omissions prevent precise `ADMITTED_TERM` and `TECHNICAL_FALLBACK` Trace even if a resolver can follow IDs already present in the admitted result.
- Corrected the Stage 04 detailed design to define archive-v2 as exactly 19 non-manifest artifacts, including full registries and per-round task/response records.
- Defined the strengthened rootless input, runtime/lifecycle receipt, content-identity, zero-Provider replay, typed Trace, failure-code, acceptance, and append-only v1-to-v2 migration contracts.
- Recorded the required cross-stage recommendation for a Stage 03-owned deterministic replay Interface without editing `DESIGN.md`, Stage 03 documentation, code, or tests.
- Re-read the completed D2 progress: its direct Stage 04 selector is green at 19/19, but its own bounded contract stops `ADMITTED_TERM` at the admitted meaning/key and treats `EMPTY_SECTION` as a built-in fallback. The design therefore records D2 as useful typed-hop coverage, not as evidence that archive-v1 contains the missing replay preimages.
- Verified the final document structure, all local links, the DESIGN heading anchor, exact 19-row artifact table, balanced fences, scoped ownership, and whitespace.

## Current state

- The sufficiency audit, Stage 04 design correction, and documentation-only verification are complete.
- No production code, tests, schemas, generated artifacts, or cross-stage design files have been changed.

## Changed files

- `progress/stage04-archive-contract-correction.md`
- `docs/stages/04-runtime-archive-trace-recovery.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared dirty worktree identified before edits; unrelated changes preserved. |
| Read-only current-contract audit | PASS | All data needed for zero-Provider Stage 03 admission replay was traced to its producing record or confirmed absent from the 17 archived artifacts. |
| Read `progress/stage04-typed-trace-core.md` and current D2 implementation | PASS | D2 reports 19/19 direct tests green; verified that this proves bounded archived-ID hops, not registry/task/response replay. |
| `awk '/^```/{n++} END{print n}' docs/stages/04-runtime-archive-trace-recovery.md` | PASS | 30 fence markers; balanced. |
| Stage 10.2 artifact-row `awk` check | PASS | Exactly 19 numbered non-manifest artifacts. |
| `rg '^#{1,3} ' docs/stages/04-runtime-archive-trace-recovery.md` | PASS | Heading hierarchy is complete and ordered through section 17. |
| Local-link and DESIGN-anchor checks | PASS | Every linked source/design/stage file exists; target DESIGN heading is present. |
| `git diff --check` | PASS | No tracked whitespace errors. |
| `git diff --no-index --check /dev/null <owned-file>` for both owned untracked files | PASS | Exit 1 only because each file is an addition; neither command emitted whitespace diagnostics. |
| `git status --short -- <two owned files>` | PASS | Only the Stage 04 design and this progress file are owned by this task; both are untracked in the shared worktree. |
| Tests | NOT RUN | Scope is documentation-only; the task explicitly forbids code/test execution. |

## Decisions

- Treat the immutable candidate archive as the replay interface: Stage 04 validation must be able to prove admission from archived bytes without a Provider or unstored in-memory state.
- Keep Stage 04-specific wire-contract corrections in the Stage 04 detailed design; record, but do not implement, any cross-stage recommendation.
- Do not overload admitted result files with generation inputs. Keep full candidate-level registries in `registry-bundle.json`, and one canonical task/response record per Flow round in `model-rounds.jsonl`.
- Reuse `source-input.json` for the exact rootless Stage 01/02/03 control chain and `generation-receipts.jsonl` for runtime/lifecycle receipts; this avoids a third new artifact while keeping responsibilities distinct.

## Blockers

- None.

## Exact next action

- None for this bounded design correction. A new TDD migration should first synchronize the Stage 03 replay seam in cross-stage design, then add Luna RED tests for archive-v2 exact 19 and implement the v1-to-v2 migration without mutating installed v1 Candidates.

## Resume checks

- Re-read this file.
- Run `git status --short` from the prototype repository.
- Confirm only this progress file and `docs/stages/04-runtime-archive-trace-recovery.md` are owned by this task.
