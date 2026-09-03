# Progress: ProgramGraphs execution entry RED tests

- Status: COMPLETE
- Agent role: Luna/xhigh test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam RED test for the persisted program-graphs M1–M6 execution chain.
- Approved inputs: `docs/analysis-steps/03-program-graphs.md`, `docs/supplements/program-graphs-implementation-backlog.md`, `docs/DESIGN.md`, existing graph builders/readers/publishers and frozen test fixtures.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Added one public-seam end-to-end test using real filesystem canonical module/analysis-step stores.
- The fixture installs formal VerifiedSourceInventory and ApplicationDiscovery publication references, then the requested seam is expected to execute M1→M6 and return one `ProgramGraphsReference`.
- The assertion requires the exact seven semantic graph payloads and successful fresh reopen of the returned analysis-step publication.

## Current state

The production `ProgramGraphsExecution` type now compiles. The fixture was corrected in the smallest possible way: both child roots are created before opening the test stores, and standalone payload bodies declare their `schemaVersion` and `artifactType`. The rerun now reaches a production input-boundary error, not a fixture setup error. `PersistedProgramGraphInputReader` rejects the persisted discovery publication because its `applicationProfileId` differs from the final `application-profile.json` artifact id.

The execution-seam owner has corrected that production reader contract. This task is reopened for one additional test-only correction in the older reader fixture: the profile's domain `applicationProfileId` must remain distinct from the content-addressed JSON artifact id, while the payload descriptor must match the JSON body's `artifactId`.

The first rerun after that correction exposed a helper mistake: the descriptor helper attempted to parse JSONL bytes as one canonical JSON value. The helper is now narrowed to JSON media types only; JSONL keeps its existing descriptor identity path.

The old reader fixture now keeps the domain `applicationProfileId` distinct from the content-addressed profile artifact id, derives the profile artifact id with the canonical standalone framing, and derives the JSON descriptor id from the JSON body's `artifactId`. The capability payload continues to reference the domain id.

## Changed files

- `progress/program-graphs-execution-entry-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecutionTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/PersistedProgramGraphInputReaderTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsExecutionTest test` | RED (production identity boundary) | Main/test compile succeeds; Surefire runs 1 test with 0 assertion failures and 1 error: `ProgramGraphInputException: PROGRAM_GRAPH_INPUT_INVALID` at `PersistedProgramGraphInputReader.java:114`, where `applicationProfileId != application-profile.json.artifactId`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest,ProgramGraphsExecutionTest test` | PENDING | Rerun after correcting the older reader fixture's profile artifact identity. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest,ProgramGraphsExecutionTest test` | RED (test helper) | `ProgramGraphsExecutionTest` passed 1/1; old reader fixture errored twice because the shared descriptor helper parsed newline-delimited JSON as canonical single-value JSON. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest,ProgramGraphsExecutionTest test` | PASS | Main/test compile succeeds; Surefire runs 4 tests, 0 failures, 0 errors, 0 skipped. `ProgramGraphsExecutionTest` 1/1 and `PersistedProgramGraphInputReaderTest` 3/3. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless formatted the new test (also cleaned a concurrently modified `NestedGuardControlFlowSafetyTest`). |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The test will introduce only the smallest graph-package public seam needed to execute M1 through M6 from formal persisted predecessor references.
- It will not add runtime state, CLI/HTTP behavior, same-run recovery, real source capture, or model calls.
- The proposed seam shape is `ProgramGraphsExecution(sourceReader, moduleStore, analysisStepStore).execute(verifiedSource, applicationDiscovery, graphProfileRef, controls)`; this is intentionally the only missing symbol in the RED.

## Blockers

## Exact next action

No further changes in this test slice. The production owner can retain the corrected persisted-identity contract and continue the broader graph execution work.

## Resume checks

- Recheck that only this test and this progress file are owned by this task (Spotless also touched the already-active concurrent test; do not claim that file as owned).
- Do not modify production, design, POM, or another agent's progress file.
- Do not stage, commit, or push.
