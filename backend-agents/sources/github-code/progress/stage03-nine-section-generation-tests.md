# Progress: Stage 03 nine-section generation tests

- Status: COMPLETE
- Agent role: Stage 03 RED test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add only public Stage03 M5–M7 RED tests, scripted-provider fixtures/helpers under `src/test/java/com/linguan/codemd/stage03/`; do not modify production code or design documents.
- Approved inputs: `docs/stages/03-nine-section-generation.md`, public Stage01/Stage02 seams and accepted synthetic/fixed jshERP fixtures.
- Current branch/worktree: shared workspace; preserve unrelated and parent-agent changes.

## Completed

- Read repository, prototype, backend, and GitHub-code scoped AGENTS instructions.
- Read the Stage 03 design and the public Stage 02 request/result/provider boundary described by the design.
- Created this progress file before modifying test sources.

## Current state

The test-only scripted `StructuredModelProvider`, frozen request/response helpers, and public-seam suite are complete. The suite contains 13 synthetic generator tests plus one fixed-checkout boundary test. After the Stage03 public seam became available, the fixed boundary selector entered the seam successfully.

## Changed files

- `progress/stage03-nine-section-generation-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage03/Stage03Fixtures.java`
- `src/test/java/com/linguan/codemd/stage03/Stage03GeneratorTest.java`
- `src/test/java/com/linguan/codemd/stage03/Stage03JshErpBoundaryTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest test` | RED (expected) | Main sources compile (108 files); testCompile fails because the Stage03 public package/seam is not implemented yet: `Stage03Request`, `Stage03Generator`, `Stage03Result`, `FlowModelTask`, `StructuredModelProvider`, registry records, runtime records, and resource budget are missing. No test method ran. |
| `git diff --check --no-index /dev/null <each new test/progress file>` | PASS | No whitespace errors. |
| `git status --short --untracked-files=all -- progress/stage03-nine-section-generation-tests.md src/test/java/com/linguan/codemd/stage03` | PASS | Only the owned progress and three Stage03 test files are listed. |
| `mvn -Dtest=Stage03JshErpBoundaryTest test` | PASS | Fixed jshERP boundary entered the public Stage03 seam: 1 test, 0 failures/errors/skips; 169 main sources compiled and Provider call count remained zero. |

## Decisions

- Tests will cross only `Stage03Generator.generate` and the public `StructuredModelProvider` contract; no reflection, package-private M5/M6/M7 modules, model calls, network, or customer build.
- The synthetic positive contract may assert the fixture-specific shape of one Flow, four Outcomes, seven admitted meanings, and nine Chinese sections; general tests will assert invariants rather than sample counts.
- Provider scripts will record every `FlowModelTask` and return immutable per-round JSON/runtime records, allowing tests to verify exactly two calls, same session per Flow, task identity, and response-order determinism.
- The synthetic vertical contract covers positive replay, seven admitted meanings from nine controlled proposals, all five expectation gaps, four outcomes/20 atoms/one proven formula, strict R1/R2 mutation stop rules, runtime identity drift, total technical fallback for an empty term registry, reader-body cleanliness, root/order determinism, and no-call fixed jshERP zero-Capsule disposition.
- The fixed jshERP test is guarded by revision/clean-checkout/file assumptions and asserts that Stage 03 retains an honest nine-section boundary when Stage 02 has zero Flows/Capsules.
- Zero-Flow/zero-Capsule registry construction now normalizes absent atom lists to empty basis lists; the synthetic positive registry still derives basis from admitted atoms.

## Blockers

- The initial Stage03 public records/generator absence produced the intended compile RED; those symbols are production implementation scope and were not worked around in tests. The remaining synthetic selector is for the production agent's GREEN cycle.

## Exact next action

Production implementation can now run the synthetic selector above; this test-only task is complete. Any constructor/record-shape change must follow the documented public contract and remain within the Stage03 test scope.

## Resume checks

- Re-read this file, run `git status --short`, and verify every subsequent changed path is under `src/test/java/com/linguan/codemd/stage03/` or this progress file.
