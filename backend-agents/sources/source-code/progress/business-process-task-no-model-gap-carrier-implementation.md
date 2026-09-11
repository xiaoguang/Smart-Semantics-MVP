# Progress: M7 no-model upstream Gap carrier implementation

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Implement only the Sol-frozen M7 wrapper projection for upstream model-ineligibility Capsule Gaps and the minimal fresh-reopen conservation checks.
- Approved inputs: `AGENTS.md`, both implementation plans, Step 06 design, `progress/m7-no-model-gap-carrier-design.md`, and the confirmed Luna RED in `progress/business-process-task-no-model-gap-carrier-tests.md`.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped implementation rules, both plans, the frozen Sol contract, the Luna RED, and the current M6/M7 records, compiler, publisher, and tests.
- Confirmed that existing M7 `NO_MODEL` shards carry raw upstream Capsule Gap IDs while `processGaps` contains only budget Gaps; the confirmed RED is therefore caused by the missing M7-owned wrapper values.
- Implemented `PROCESS_UPSTREAM_MODEL_INELIGIBLE` with a closed budget-versus-upstream field matrix, nullable boxed limit fields, and a defensive complete M6 `gapView` projection.
- Implemented per-shard upstream-Gap resolution, byte-equivalence rejection for a duplicated upstream ID with conflicting value, content-addressed wrapper IDs, sorted wrapper installation, and compilation-level shard-to-Gap conservation.
- Re-ran the corrected Luna direct compiler selector: 1 test, 0 failures/errors/skips.
- Re-ran the existing M7 module publisher regression: 2 tests, 0 failures/errors/skips.
- Re-ran scoped Spotless for the four owned production sources and `git diff --check`.

## Current state

- Production produces the expected two distinct M7 wrapper IDs for the mixed three-Flow fixture, and the serialized M7 `processGaps` contains exactly those values with the original nested Gap view.
- The Luna-owned selector now iterates the shard Gap array correctly and verifies that each no-model shard references an M7-owned wrapper rather than a raw Capsule Gap ID.

## Changed files

- `progress/business-process-task-no-model-gap-carrier-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/ProcessInterpretationGapV1.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/UpstreamFlowGapProjectionV1.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompilation.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/process/BusinessProcessTaskCompiler.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Confirmed Luna direct RED selector | PASS (precondition) | Existing test records one assertion failure at missing M7 process-Gap carrier, not a fixture or compile failure. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskCompilerTest#separatesMixedEligibleAndIneligibleRelationUnitsWithoutLosingAnyOwner test` | PASS | 1 test, 0 failures/errors/skips after the Luna-owned direct-array extraction correction. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=BusinessProcessTaskModulePublisherTest test` | PASS | 2 tests, 0 failures/errors/skips. |
| Scoped `spotless:apply` then `spotless:check` | PASS | The four owned production sources are formatted. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The upstream `gapView` will be copied from the fresh-reopened M6 value-bearing capsule material. It will not be reconstructed from an ID or relabeled as a budget Gap.
- Only the wrapper has M7 ownership. Its ID excludes `gapId` and `taskShardId` to avoid the owner cycle; the raw source Gap ID remains identity-significant inside the embedded view.
- No publisher mapper was added: the compiler returns a self-validating compilation and the existing publisher recompiles it before receipt-last installation.

## Blockers

- None within this bounded M7 production slice.

## Exact next action

- M8 may resume from the fresh M7 publication/fixture: no-model shard Gap references now close through M7-owned wrappers while model-safe shard and Provider contracts remain unchanged.

## Resume checks

- Preserve all other dirty shared-worktree files.
- Do not weaken M8's no-model full-denominator requirement or send any Provider request.
