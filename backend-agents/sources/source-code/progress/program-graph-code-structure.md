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
- Published the bounded `PersistedProgramGraphInputReader` design clarification as `21f3037` on `origin/main`. It closes the existing M1/M2 input contract without adding a graph module, artifact, or public API.
- Added the first input-reader RED. It fails only because `PersistedProgramGraphInputReader` is absent; the test also locks out caller-owned `Path` constructor inputs.
- Moved the reusable verified-source text boundary to `analysis.inventory` as `VerifiedSourceTextReader` / `PersistedVerifiedSourceTextReader`. Its direct two-test selector remains green; the Program Graph reader is still the only expected RED.
- Added a persisted-artifact behavior test for the reader. It now fails at test compilation only because the closed `ReopenedProgramGraphInputs` type and `reopen(VerifiedSourceInventoryReference, ApplicationDiscoveryReference)` behavior are absent; no parser or fixture failure is hidden by that RED.
- The next M1 slice is the reader implementation: fresh-reopen verified source text plus the four published application-discovery semantic artifacts, verify identity/controls/predecessor/artifact-set closure, then construct path-free graph inputs.
- Implemented that reader. It validates the exact two predecessor references, controls, successful application-discovery receipt and four-file artifact set; it then reads the semantic profile, capability coverage, entry JSONL and Mapper catalog JSONL into closed graph inputs. A missing exact verified-source predecessor is explicitly rejected.
- The reader and moved inventory text handle pass 5 direct tests; the directly affected application-discovery selectors pass 27 tests. Spotless and `git diff --check` also pass.
- Added the next M1 RED: the module-publisher selector now fails at test compilation only because the internal `ProgramGraphInputReader` and `CodeStructureGraphExecution` handoff types do not yet exist. The test requires one fresh input reopen before the real builder and real receipt-last module publisher run.
- Implemented the M1 execution seam. `CodeStructureGraphExecution` can only receive a fresh `ProgramGraphInputReader` result, then invokes the real builder and receipt-last module publisher; its direct tests pass together with the persisted-input reader tests.

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
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest test` | RED | 1 test, 1 expected failure, 0 errors/skips: the persisted input-reader class is absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest,PersistedVerifiedSourceTextReaderTest test` | RED | 3 tests: 2 source-reader tests pass; 1 expected Program Graph reader absence failure, no errors/skips. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest test` | RED | Test compilation fails only because `ReopenedProgramGraphInputs` and the reader `reopen(...)` method do not yet exist. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=PersistedProgramGraphInputReaderTest,PersistedVerifiedSourceTextReaderTest test` | PASS | 5 tests, 0 failures/errors/skips: fresh input re-open and missing-predecessor rejection pass. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ApplicationDiscoveryExecutionTest,ApplicationProfileDetectorTest,HttpEntryDiscoveryModulePublisherTest,MapperCapabilityCatalogerTest,SpringHttpEntryDiscovererTest,PersistedVerifiedSourceTextReaderTest test` | PASS | 27 tests, 0 failures/errors/skips after moving the shared verified-source reader to source inventory. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Formatter applied and reported no errors (it notes tracked paths deliberately moved during the package relocation). |
| `git diff --check` | PASS | No whitespace errors after formatter. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphModulePublisherTest test` | RED | Test compilation fails only on absent `ProgramGraphInputReader` and `CodeStructureGraphExecution`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphModulePublisherTest,PersistedProgramGraphInputReaderTest test` | PASS | 5 tests, 0 failures/errors/skips: one fresh input reopen drives the real builder and real receipt-last module publisher. |

## Decisions

- M1 will create only code-structure records and builder code; it will not add call, control-flow, data-flow, evidence, Fact, Flow, or model behavior.
- Each planned graph shard will be backed by a disjoint canonical denominator and will consume only its declared upstream artifacts.
- Static MyBatis extraction is deliberately limited to syntax that can be parsed without executing a mapper; unsupported source becomes a typed M1 Gap.
- A local-only WIP checkpoint preserves the pre-v2 M1 work while the final Stage 3 delivery remains uncommitted and unpublished.

## Blockers

- None.

## Exact next action

- Run the complete M1 selector after formatting, take a local WIP checkpoint, then start the M2 CallGraphBuilder RED: one exact Controller→Service call edge, no string/name fallback.

## Resume checks

- Read this file, run `git status --short`, confirm the branch is `codex/source-analysis-program-graphs`, then rerun the direct M1 test selector.
