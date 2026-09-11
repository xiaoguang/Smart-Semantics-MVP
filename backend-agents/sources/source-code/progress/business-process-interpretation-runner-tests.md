# Progress: business process interpretation runner tests

- Status: COMPLETE
- Agent role: Root delivery coordinator, Luna/xhigh TDD role
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Establish one public-seam RED for M8 P1/P2 over one fresh-reopened M7 `MODEL_SAFE` dry packet. The test must prove packet-local references only, exactly one scripted P1 followed by P2, and no source/provider retry or archive metadata exposure. It must not implement M9 publication or invoke a live Provider.
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, `AGENTS.md`, fresh M6/M7 synthetic fixture publications, scripted Provider only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout`; `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the applicable implementation plans, scoped instructions, M6/M7 current code, and the M8/P1/P2 target contract.
- Added one reflection-based public-seam test for the first M8 packet.
- Ran its direct selector. It compiled 103 test sources and reached the intended RED: the absent
  `BusinessProcessInterpretationRunner` caused exactly one assertion failure,
  `PROCESS_INTERPRETATION_RUNNER_NOT_IMPLEMENTED`; no test errors or Provider calls occurred.
- Added the design-required M6 profile runtime reference to the existing fresh-reopen fixture and
  made M6 reject a missing, malformed, or extra profile member before it produces candidates.
- Implemented the bounded M8 runner and its small transport records. The same direct selector is
  GREEN: 1 test, 0 failures/errors/skips. The test proves one M7 `MODEL_SAFE` two-Flow packet
  becomes exactly P1 then P2 through the scripted provider, with no live model call.
- The runtime mismatch and P2 protected-reference regressions now have their own progress
  records; this original success-path record remains complete and is not a substitute for the
  pending terminal/persistence M8 design and implementation.

## Current state

- M7 can fresh-reopen a value-bearing M6 group and persist a `MODEL_SAFE` dry packet with program-only key bindings, but no M8 runner exists.
- The first RED will freeze only the smallest two-Flow packet behavior. It will not claim full process publication or process knowledge.

## Changed files

- `progress/business-process-interpretation-runner-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationRunnerTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/process/CrossFlowCandidateCompilerTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationRunner.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationResult.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessInterpretationException.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/ProcessModelProvider.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/ProcessModelProviderRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/ProcessModelProviderResponse.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/CrossFlowCandidateCompiler.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessInterpretationRunnerTest#runsOneFreshReopenedModelSafeShardAsP1ThenP2WithoutLeakingProgramOnlyMaterial test` | Expected RED | 1 test; 1 assertion failure: `PROCESS_INTERPRETATION_RUNNER_NOT_IMPLEMENTED`; 0 errors/skips. |
| Same direct selector after implementation | PASS | 1 test; 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CrossFlowCandidateCompilerTest test` | PASS | 11 tests; 0 failures/errors/skips after the runtime-profile contract was added. |
| Scoped `spotless:apply` over the first M8 production/test files | PASS | Applied the project formatter only to the eight owned Java files. |

## Decisions

- Use a scripted Provider; product Luna/high remains a later, separately authorized acceptance call.
- Keep model input to the existing dry packet plus bounded packet-local instructions and P1 semantic projection. Program-only bindings remain outside the Provider request.

## Blockers

- None for the bounded first P1/P2 seam.

## Exact next action

- Start a separate one-behavior RED for runtime mismatch before Provider result acceptance. Keep
  M9 publication and all terminal P1/P2 branches out of that test.

## Resume checks

- Re-read this file, `git status --short`, the direct M8 selector report, the M7 packet fixture,
  and the exact runtime-reference design rule before adding the next M8 behavior.
