# Progress: mixed-eligibility stored-artifact flow coverage test

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: One public `BusinessFlowCoverageTest` for the current Flow v2/Capsule v3 publication seam.
- Approved inputs: Frozen public fixtures and scripted in-memory artifacts only; no source/customer scan, network, or live Provider.
- Current branch/worktree: `codex/source-analysis-process-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read repository, backend-agent, and source-code guidance plus required TDD instructions.
- Confirmed the worktree has unrelated in-progress edits; they are preserved.
- Created this progress handoff before changing test files.

## Current state

- The one stored-artifact mixed-eligibility test is complete and GREEN.
- The test is Spotless-clean and its exact selector passed after formatting.
- Gate was released by root after the adjacent Terra work completed; this slice then applied and verified the test.

## Changed files

- `progress/flow-coverage-tests.md` (retained completed handoff)
- `src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowCoverageTest.java` (retained completed test)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing source/design/test edits observed and preserved. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowCoverageTest test` | PASS | 1 test, 0 failures/errors/skips. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/publish/BusinessFlowCoverageTest.java spotless:apply` | PASS | Exact one-file selection; 0 changes on final apply. |
| Same absolute `-DspotlessFiles` with `spotless:check` | PASS | `Spotless.Java` selected 1 file; 0 changes needed. |
| `git diff --check` | PASS | No whitespace errors. |
| Same `BusinessFlowCoverageTest` selector after formatting | PASS | 1 test, 0 failures/errors/skips. |

## Decisions

- Use only `ProgramGraphsPublicFixture` and the public `RegistryProposalTaskCompilerTest.public publishBusinessFlows(...)` overload.
- Derive the deterministic mixed budget from the smaller positive model-evidence-span count of an independent generous fixture; never fabricate outputs or republish at an immutable address.
- Assert exact closed coverage/mapping/Gap union, one eligible R0 task, and persisted complete facts/outcomes/signals/spans for both capsules.
- The independent generous fixture produced two distinct counts: 8 spans for `flow:f3c2c3c632309b16e239474137dcf50d4326172ced73ab323eb1b2b922baef12` and 9 spans for `flow:7b6e00d79a05009a9e6553a175f54f5ed16c58643e59fede09be97b9c17c4089`; the bounded profile uses `maxSpansPerCapsule=8`, yielding one eligible and one ineligible Flow.

## Blockers

- None. Root released the gate; no production, fixture, schema, design, or POM files were changed.

## Exact next action

- Release the Maven gate to the root agent with the exact test/format results and leave the two owned files in place.

## Resume checks

- Re-read this file, run `git status --short`, and confirm the Java/Maven gate has been explicitly released.
