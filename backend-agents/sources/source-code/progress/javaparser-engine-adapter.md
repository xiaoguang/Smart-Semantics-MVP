# Progress: JavaParser engine adapter

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: GPT-5
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Tasks 9 and 10 of the JDT-first Java engine implementation plan
- Approved inputs: current main at `a572f0f`; JavaParser capability baseline at `cec1997`
- Current branch/worktree: `codex/javaparser-engine-adapter` in
  `/private/tmp/linguan-source-analysis-process-design`

## Completed

- JDT stage released and pushed to main at `a572f0f`.
- Isolated JavaParser implementation branch created from that exact commit.
- Replayed the pre-cutover JavaParser baseline at detached `cec1997` with the direct selector for
  discovery, all five graph builders, proven facts, business flows, materials, workflow, and the
  four-entry semantic chain: 96 tests passed with 0 failures/errors/skips.
- Added the retained `JavaParserCodeEngine`, snapshot-owned project session, declaration catalog,
  and entry-context adapter. The adapter performs only the baseline syntax-first resolution and
  does not start JDT, add Symbol Solver wiring, or repair wildcard/inheritance/overload limits.
- Enabled the exact `javaparser` factory value and kept the selected engine on the same discovery,
  persisted Java-code-index, Flow/Capsule, business-material, Activity/Process/Report chain.
- Preserved the seven strict JavaParser graph payloads and four strict Fact/Proof payloads while
  publishing `java-code-index.jsonl` as the eighth Step03 semantic payload. The persisted Flow
  context now attaches the navigation source without rerunning the Builder parser.
- Added the public workflow check that reopens JavaParser output, exposes Controller, Service,
  Mapper, parameters, conditions, exceptions, and returns in one model packet, then generates a
  nine-section scripted report through the unchanged business consumers.
- Added dual-engine acceptance for exact YAML selection, engine isolation and engine-bound
  persisted index identity. Updated the authoritative current-state documentation to describe both
  engines as integrated without weakening their different capability boundaries.

## Final state

- Task 9 / approved phase 2.1 is complete.
- Task 10 / approved phase 2.2 is complete locally. Both configured engines use the same persisted
  navigation reader and business consumers; neither engine starts or falls back to the other.
- The branch is ready for its release commit, remote push and main integration.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/code/javaparser/**`
- selected-engine graph/index publication, Fact/Flow persisted readers, Flow context attachment,
  runtime factory/workflow, and exact-set artifact contracts
- `src/test/java/org/sourceanalysis/app/analysis/code/javaparser/JavaParserCodeEngineBaselineTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/JavaCodeEngineFactoryTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/TechnicalAnalysisWorkflowTest.java`
- `progress/javaparser-engine-adapter.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| JDT release combined selector | PASS | 79 tests, 0 failures/errors/skips |
| detached `cec1997` JavaParser baseline selector | PASS | 96 tests, 0 failures/errors/skips |
| Task 9 final baseline selector | PASS | 99 tests, 0 failures/errors/skips |
| selected JavaParser persisted workflow to scripted report | PASS | 1 test, 0 failures/errors/skips |
| Task 10 dual-engine selector | PASS | 12 tests, 0 failures/errors/skips |
| `SourceAnalysisArchitectureTest` | PASS | 3 tests, approved `analysis.code` package root registered |
| `mvn -o -t .mvn/toolchains.xml spotless:check` | PASS | 555 Java files clean |
| active documentation relative-link check | PASS | 31 files, 0 missing targets |

An exploratory wider selector ran 51 tests: 43 passed and 8 exposed pre-existing expectation
drift outside Task 9 (old flow/capsule version assertions, strict-path code-context assumptions,
and two business-language summary assertions). These are not used as evidence for Task 9 and are
not being hidden. The approved Task 10 selector is green; historical `docs/history/00-mvp.md` links
to physically removed POC paths are likewise excluded from the active documentation contract.

## Decisions

- Restore only behavior observable at `cec1997`; do not add Symbol Solver wiring, wildcard/import,
  inheritance, overload, or JDT-parity work.
- Both engines must feed one persisted EntryCodeContext and business-material chain; there is no
  fallback or mixed-engine run.

## Blockers

- None.

## Exact next action

Create the release commit, push the implementation branch, integrate it into `main`, and confirm
local and remote `main` identify the same commit.

## Resume checks

- If release was interrupted, confirm branch is `codex/javaparser-engine-adapter` and HEAD descends
  from `a572f0f`.
- Verify the recorded selectors remain green, commit the complete diff, push the branch, integrate
  it into `main`, and confirm local and remote `main` identify the same commit.
