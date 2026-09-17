# Progress: Gate A MyBatis/JSqlParser feasibility RED tests

- Status: IN_PROGRESS
- Agent role: bounded Gate A RED-test writer
- Model: GPT-5
- Started: 2026-09-17
- Last updated: 2026-09-17
- Scope: `research/persistence-tool-feasibility/src/test` and this progress record only
- Owning plan: approved Gate A in `docs/supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md` §§5, 8, 9
- Approved inputs: neutral frozen XML fixtures embedded in the test; no customer build, JDT run, network source, database, provider, or model call
- Current branch/worktree: shared formal source-code checkout; no commit made

## Completed

- Added a reflection-backed RED contract for `org.sourceanalysis.research.persistence.PersistenceToolProbe`.
- Covered the minimum Gate A behaviours: full raw XML retention, mixed text/CDATA, dynamic `if`/`choose`/`foreach` conditions, static include dependencies, aggregate/LEFT JOIN/ON/WHERE SQL structure, duplicate namespace + `databaseId` candidates, dynamic `insertSelective` column/value conditions, unsupported `${...}` retention, unresolved/cyclic include diagnostics, mapper namespace/method filtering and parameter aliases, non-evaluation of OGNL, and external-entity rejection without secret expansion.
- Kept fixtures domain-neutral (`sample.alpha.RecordMapper`, `alpha_record`, `beta_record`, `record_id`, etc.); no fixed customer method/path or business conclusion is encoded.
- Used reflection only to keep the test suite compilable and fail as JUnit assertion RED while the probe class is absent. The expected implementer seam is:
  - `PersistenceToolProbe()`
  - `analyze(List<MapperResource>, List<MapperMethodDescriptor>)`
  - `MapperResource(String resourcePath, String xml)`
  - `MapperMethodDescriptor(String mapperFqn, String methodKey, String methodName, List<MapperParameter>)`
  - `MapperParameter(String declarationName, String explicitParamAlias)`
  - result accessors `resources()`, `statements()`, `bindings()`, `sqlAnalyses()`, and `diagnostics()`; statement/condition/resource projections expose the accessors named in the test.

## Current state

The root agent recorded the initial RED run as 7 tests, 7 expected assertion failures, 0 errors, completed in 2.911 seconds using the targeted Maven command. After the probe implementation was added, one first GREEN run covered 9 tests with one fixture-expectation failure: the CDATA text includes a space before `]]>`. That expected assertion is now corrected. The test file contains 14 focused tests after adding namespace/method filtering, include diagnostics, unsafe-XML diagnostics, an include-only dynamic-fragment regression, nonstandard external-entity/XInclude rejection, static SQL string-literal whitespace preservation, UNION bounded-status handling, and no-ON join preservation; the root agent should rerun only this named test after implementation changes.

Dynamic insert does not require an AST in this gate. Its raw conditional columns, raw conditional values, and condition expressions must remain available; SQL AST is optional/partial as specified by the owning design.

## Changed files

- `research/persistence-tool-feasibility/src/test/java/org/sourceanalysis/research/persistence/PersistenceToolProbeTest.java`
- `progress/persistence-tool-red.md`

The research POM and any future `src/main` implementation are outside this agent's ownership and were not edited here.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -B -ntp -f research/persistence-tool-feasibility/pom.xml -Dtest=PersistenceToolProbeTest test` | Ran by root before the final test additions | 7 expected assertion failures for the absent probe, 0 errors; root retained the RED evidence |
| `git diff --check -- backend-agents/sources/source-code/research/persistence-tool-feasibility/src/test backend-agents/sources/source-code/progress/persistence-tool-red.md` | PASS after final test additions | No whitespace errors; no Maven rerun by this agent after the additional tests |

## Decisions

- The probe accepts immutable resource path/text pairs and mapper method descriptors; it does not accept a filesystem root, invoke a customer class, instantiate a customer type, evaluate OGNL, or execute a database/application build.
- Original resource text is asserted byte-for-byte at the supplied `String` boundary and statement raw text is asserted to retain the dynamic XML. No test requires a custom XML tokenizer or lexical line slicing.
- SQL assertions require a status of `PARSED` or `PARTIAL` for the neutral aggregate/join example and permit `PARTIAL`/`UNSUPPORTED` for dynamic identifiers. They never require a guessed all-branches SQL string.
- Duplicate XML candidates are asserted as two records, with the primary candidate's null `databaseId` and the variant's exact `databaseId` `alt`; later candidates must not overwrite earlier ones.
- Dynamic `insertSelective` is checked through paired raw conditional columns/values and condition expressions, not an AST requirement.
- Unsafe XML must retain original text while producing an explicit diagnostic and no parsed external-read statement; the local secret must never appear in the result.
- An include-only statement must expose dynamic conditions and parameter placeholders originating in its included fragment, and its SQL status must not be `PARSED` when those conditions were omitted from the analysis copy.
- External parameter entities and XInclude are rejected as resources with diagnostics; fixture-local secret material must never enter the result. The test does not prescribe parser feature flags or require a custom XML grammar.
- Static SQL analysis copies must preserve repeated whitespace inside quoted literals (for example `SELECT 'a  b' ...`); whitespace normalization must not mutate SQL literal values.
- A `UNION` statement must retain its raw source and produce a bounded `PARTIAL`/`UNSUPPORTED` result rather than throwing from a `PlainSelect`-only visitor and aborting the whole probe batch.
- A CROSS JOIN (or equivalent join without an ON expression) must retain its right-side relation and null `on` projection without throwing an index/accessor exception.

## Blockers

- None for the bounded RED-test deliverable. The probe implementation and green rerun belong to the implementing/root agent.

## Exact next action

Root/implementer: add the thin probe under `research/persistence-tool-feasibility/src/main` using the API above, run only `mvn -B -ntp -f research/persistence-tool-feasibility/pom.xml -Dtest=PersistenceToolProbeTest test`, and record the post-implementation result. Preserve the initial RED evidence before comparing GREEN.

## Resume checks

- Confirm this test file remains the only test-scope change owned here.
- Confirm the implementation uses official MyBatis 3.5.19/JSqlParser 5.3 dependencies from the research POM, does not add a second parser, and does not execute MyBatis `BoundSql`/OGNL or customer code.
- Confirm the real frozen-source examples remain integration work for Gate A and are not hard-coded into these unit fixtures.

## Plan closeout destinations

- Durable decisions: Gate A design and final tool report, maintained by root/implementer.
- Remaining issues: final report's unsupported dynamic SQL/include/security limitations.
- Verification and output references: root's preserved RED receipt and the targeted Surefire result for this test class.
