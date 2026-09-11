# Progress: CLI observation adapter

- Status: IN_PROGRESS
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Add a Picocli adapter for public finished-run inspection, rendering and business artifact
  observation. It must not own or duplicate source analysis, model use, or filesystem traversal.
- Approved inputs: Existing `RepositoryAnalysisAgent` start/inspect/render/artifact behavior and
  the existing Picocli dependency.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed Picocli is a fixed project dependency and no CLI production class exists yet.

## Current state

- Adding a behavior RED for `inspect`, `render` and `artifact` command-to-core mapping with a
  fake public Agent. The test must prove the adapter sends no source/model configuration itself.

## Changed files

- `progress/cli-observation-adapter.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Not run | Pending | CLI public adapter contract does not yet exist. |

## Decisions

- This slice intentionally does not add `analyze` because production bootstrap/configuration of
  the full technical prefix is not finished. An observation adapter may only call public core
  operations that already have durable behavior.

## Blockers

- None.

## Exact next action

- Add and run the command-to-core mapping RED.

## Resume checks

- CLI output must not contain a local source path, prompt, model response or secret.
