# Progress: Stage 01 M3 proof tests

- Status: COMPLETE
- Agent role: Luna/xhigh TDD RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add Stage 01 M3 behavioral RED tests and test-only helpers for the synthetic reservation fixture; current regression slice adds semantic closure, decoy isolation, renamed-symbol, gap-trigger, and request-profile contracts.
- Approved inputs: Repository-local AGENTS.md, DESIGN.md M3 F01-F08/A01-A20 contract, Stage 01 sections 7/9/11/12, existing M1/M2 public APIs and synthetic fixture.
- Current branch/worktree: shared workspace; preserve unrelated changes and other agents' files.

## Completed

- Read repository and local GitHub Code Agent instructions, M1/M2 APIs, existing M1/M2 tests/helpers, synthetic six-file fixture and manifest, and required Stage 01/M3 design sections.
- Added the synthetic positive, canonical, route-prefix rejection, XML version-predicate rejection, guard-literal mutation, five expectation-gap, and proof-source tamper behavior tests.
- Added only test-side mutation and reflective M3 assertion helpers; no production, POM, design, or existing fixture source was changed.
- Positive ProofPack checks independently recompute each proof-node span digest from the copied M1 bytes and match its declared source-file digest.
- Corrected the route-prefix mutation helper to remove the fixture's column-1 `@RequestMapping` line exactly.
- Existing M3 extraction/mutation selector now passes 8/8 after the route-helper correction.
- Added independent semantic-closure regression slice with local builders for proof reachability, SQL roots, A09/A16 binding chains, decoy isolation, renamed symbols, expectation triggers, and request profile/JSON rejection.

## Current state

- M3 production is now partially implemented and the selector reaches behavioral tests; the route mutation helper now matches the frozen fixture bytes exactly.
- The new regression selector compiles and runs; all eight tests are intentionally RED against current known M3 gaps.

## Changed files

- `progress/stage01-proof-tests.md`
- `src/test/java/com/linguan/codemd/stage01/M3TestSupport.java`
- `src/test/java/com/linguan/codemd/stage01/ProvenFactExtractionTest.java`
- `src/test/java/com/linguan/codemd/stage01/ProofMutationTest.java`
- `src/test/java/com/linguan/codemd/stage01/ProofSemanticClosureRegressionTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=ProvenFactExtractionTest,ProofMutationTest test` | PASS | 8 tests run; ProofMutationTest 2/2 and ProvenFactExtractionTest 6/6 pass after the route-helper correction. |
| `mvn -Dtest=ProofSemanticClosureRegressionTest test` | EXPECTED RED | 8 tests run, 8 failures: arbitrary roots/unreachable closures, SQL opening-tag roots, missing A09/A16 dependency chain, decoy node borrowing, renamed symbols erased candidates, retry/warehouse gaps remain, and missing Stage01Request API. |
| `git diff --check` | PASS | no whitespace errors. |
| `mvn -Dtest=ProvenFactExtractionTest,ProofMutationTest test` | PASS | 8 tests run; ProofMutationTest 2/2 and ProvenFactExtractionTest 6/6 pass after the route-helper correction. |

## Decisions

- Tests use `new Stage01Analyzer().analyze(FrozenRepositoryRequest)` only, with test-side byte mutations and no production helper calls.
- Existing M1/M2 fixture files remain unchanged; mutation helpers copy then edit temporary snapshots and update declarations independently.

## Blockers

## Exact next action

- Parent/Terra should implement the semantic closure and request-profile contracts until this exact selector is GREEN; no existing test/helper or production file was changed by this slice.

## Resume checks

- Re-read this file and nearest AGENTS.md; verify scoped status before resuming; after Terra adds M3, run the exact selector and do not run the full suite under this task.
