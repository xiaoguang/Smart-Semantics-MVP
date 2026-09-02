# Progress: program graph code structure

- Status: IN_PROGRESS
- Agent role: Terra/xhigh production implementation under the approved Sol/ultra design
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: ProgramGraphs M1 only — code-structure draft and its direct public-seam tests
- Approved inputs: `docs/DESIGN.md`, `docs/analysis-steps/03-program-graphs.md` at `5457819`, both implementation plans, user-approved shard rules and provenance-schema v2 confirmation
- Current branch/worktree: `codex/source-analysis-program-graphs` at `/private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code`

## Completed

- Read the scoped implementation rules, the authoritative program-graphs design, and both required implementation plans.
- Confirmed no current production implementation exists in `analysis.graph`.
- Added the first `CodeStructureGraphBuilderTest` fixture and watched it fail because all M1 public types were absent.
- Implemented the minimal M1 draft records and builder: JavaParser declaration parsing, secure MyBatis XML parsing, static update table/column extraction, stable IDs, and closed candidate accounting.
- The first structure test now passes.
- Added a malformed-Java RED: JavaParser returned a partial AST, which the initial implementation incorrectly accepted as exact structure.
- Added a custom-entity Mapper XML RED and a forged-source-file-ID RED; both now fail closed as an explicit Gap or invalid input respectively.
- Ran formatting and the quality gate. Initial SpotBugs reported 12 representation/catch issues; the smallest corrections now leave SpotBugs and PMD at zero findings.

## Current state

- The in-memory code-structure draft and canonical module publisher/reopen slices are GREEN.
- The Foundation store now seals the approved ProgramGraphs/code-structure artifact type and its one allowed payload filename; this was an implementation omission, not a contract change.
- The v1 M1 code checkpoint was rebased onto the published provenance-schema v2 design. It is intentionally superseded: M1 must now emit dereferenceable locator/digest/rule provenance drafts and configuration resource structure before it can be called complete.
- The first v2 RED is established: the direct builder selector stops in test compilation because `CodeStructureGraphDraft.provenanceDrafts()` and `ProvenanceDraftV1` do not yet exist.
- The v2 production seam now compiles and the publisher/reopen test is green. The YAML golden was corrected after independently recounting the newline after `mybatis:`; the combined direct selectors then passed all 6 tests.
- Added a declaration/token precision RED. It proves the current Java and XML paths still attach whole-file evidence spans rather than the exact Controller method, SQL table, and SQL column spans required by the v2 contract. The direct builder selector now has 6 tests with exactly one expected assertion failure and no errors.
- Added a nested-configuration RED required by the M1 registry. The builder currently collapses `app.persistence.mapper-location` to `app.mapper-location`; the 7-test selector has exactly that one failure and no errors.
- Implemented the bounded static YAML mapping walker: it tracks indentation-scoped parents, flattens nested mapping keys, preserves key/value source spans, and emits typed Gaps for unsafe mapping or indentation shapes. The nested-key selector is GREEN.
- Published the related current-maturity correction as docs commit `5a98f4a` on `origin/main`. It distinguishes the M1 builder/module slice from the still-unimplemented persisted runtime assembly and M2–M6.
- The next M1 slice is an executor that fresh-reopens verified-source and application-discovery inputs before it builds/publishes the draft; it must not make the manual structured test inputs a production handoff.

## Changed files

- `progress/program-graph-code-structure.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/*.java` (M1 code-structure records and builder)
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphBuilderTest.java`
- `src/test/resources/analysis/graph/code-structure/**`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphModulePublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | RED | After correcting a test-only policy-reference constructor, the selector failed only because the M1 graph public seam was absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | PASS | First slice: 1 test, 0 failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | RED | 2 tests, 1 expected failure: a malformed Java resource produced partial declaration nodes instead of one Gap. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | PASS | 4 tests, 0 failures/errors/skips after Java-parser, XML-entity, and source-ID gates. |
| `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | PASS | Existing POM still ran 106 tests despite `-DskipTests`; all passed, with SpotBugs 0 and PMD 0. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphModulePublisherTest test` | RED — test helper correction required | The intended missing publisher/reference seam is present; helper lacked an overload for byte-array identity input. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphModulePublisherTest test` | RED — Foundation contract incomplete | Compilation passed; fresh module install reached `requireExpectedPayloadSet` and failed only with `MODULE_INSTALL_REQUEST_INVALID`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphModulePublisherTest test` | PASS | 1 test, 0 failures/errors/skips. The fixture installed and fresh-reopened the one M1 module payload. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | RED | Test compilation fails only on the missing v2 provenance-draft public types/accessor. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest,CodeStructureGraphModulePublisherTest test` | RED — test expectation | 6 tests compiled and ran; publisher passed and the only builder mismatch was expected YAML key bytes `10..26` versus correctly counted `11..27`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest,CodeStructureGraphModulePublisherTest test` | PASS | 6 tests, 0 failures/errors/skips after the independent YAML-span correction. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | RED | 6 tests, 1 expected failure, 0 errors/skips: `batchSetStatus` still carries the full-file source locator instead of JavaParser's declaration range. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | RED | 7 tests, 1 expected failure, 0 errors/skips: nested YAML key is flattened as `app.mapper-location` rather than `app.persistence.mapper-location`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest test` | PASS | 7 tests, 0 failures/errors/skips after the bounded nested-mapping implementation. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphBuilderTest,CodeStructureGraphModulePublisherTest test` | PASS | 8 tests, 0 failures/errors/skips; canonical module payload fresh reopen remains green. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | All M1 sources and tests formatted. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- M1 will create only code-structure records and builder code; it will not add call, control-flow, data-flow, evidence, Fact, Flow, or model behavior.
- Each planned graph shard will be backed by a disjoint canonical denominator and will consume only its declared upstream artifacts.
- Static MyBatis extraction is deliberately limited to syntax that can be parsed without executing a mapper; unsupported source becomes a typed M1 Gap.
- A local-only WIP checkpoint preserves the pre-v2 M1 work while the final Stage 3 delivery remains uncommitted and unpublished.

## Blockers

- None.

## Exact next action

- Create a local M1 checkpoint, rebase it on published design commit `5a98f4a`, then write the smallest public RED for persisted M1 input assembly before starting M2.

## Resume checks

- Read this file, run `git status --short`, confirm the branch is `codex/source-analysis-program-graphs`, then rerun the direct M1 test selector.
