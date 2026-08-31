# Progress: Stage 01 M2 repository tests

- Status: COMPLETE
- Agent role: Stage 01 M2 TDD RED test writer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add only Stage 01 M2 behavior tests and test-only fixture helpers; do not implement production code.
- Approved inputs: Synthetic six-file reservation-v1 fixture, Stage 01 M1 API/fixture/tests, RepositoryDiscoverer tests, and sections 6/9/11 of `docs/stages/01-proven-source-facts.md`.
- Current branch/worktree: Shared worktree; preserve unrelated existing changes.

## Completed

- Read repository, prototype, backend, and github-code AGENTS instructions.
- Read Stage 01 M2, bounded synthetic fixture, and TDD/mutation sections.
- Read the existing M1 public API, fixture helper, M1 contract tests, and RepositoryDiscoverer tests.

## Current state

- M1 production seam exists as `Stage01Analyzer.verify(FrozenRepositoryRequest)`.
- M2 public `understand(FrozenRepositoryRequest)` seam and result/model/report types are present in the current production worktree.
- Added `M2TestSupport`, `M2Fixtures`, `RepositoryUnderstandingRouteCallTest`,
  `RepositoryUnderstandingMyBatisTest`, and `CapabilityAccountingTest`.
- A current-production baseline selector was attempted before this regression slice;
  it is blocked in production compilation by five pre-existing `RepositoryCompiler`
  errors (`maxNodeDepth`, `springAnnotation`, `xmlNodeCount`, and the
  `buildControlFlows` signature mismatch).
- The parent subsequently repaired those production compile blockers; the regression
  selector now reaches Surefire and reports assertion RED only for the requested
  missing behaviors.
- A fresh pre-slice run of the existing M2 selector completed successfully (18
  tests, 0 failures) against the current production worktree. This establishes the
  baseline before adding the independent integrity regression slice.

## Current regression slice

- Add only `RepositoryIntegrityRegressionTest.java` with a test-local request
  builder and literal expected values for profile binding/parser selection,
  mapper-location filtering, entry ownership, snapshot-sensitive identities,
  exclusive locators/spans, M1 observed-size limits, and M2 AST/XML budgets.
- Run the exact new-test selector and retain RED assertions against current
  production; do not implement the behavior in this task.

## Independent integrity regression slice

- Added `RepositoryIntegrityRegressionTest.java` only. Its local builder copies
  the six fixture files directly, carries literal file metadata, computes its own
  inventory/receipt proof, and uses the built-in Java 8/17 profile digests.
- The profile tests catch arbitrary digest acceptance and Java 17 parsing under the
  Java 8 profile instead of an explicit parse/unsupported gap.
- The mapper-location test catches a declared XML decoy outside
  `classpath*:mappers/*.xml` being emitted as a `CONFIG_RESOLVES_MAPPER` edge.
- The two-entry ownership test catches every call site being tagged with all HTTP
  entries instead of only its reachable owner.
- The identity test catches node, edge, capability-site, repository-model, and
  capability-report IDs that do not vary with a revision or changed source bytes.
- The locator/span test catches inclusive end columns and semantic byte slices
  drifting apart; it fixes the expected call span to literal bytes 367..388,
  exclusive end column 51, and the reviewed SHA-256.
- The observed-growth M1 test catches reading past `maxFileBytes` until a later
  size mismatch, rather than failing with `M1_RESOURCE_LIMIT_EXCEEDED` before M2.
- AST and XML budget tests catch `OVER_LIMIT` accounting being emitted while
  exact Java/XML nodes continue past their corresponding limits.

## Changed files

- `progress/stage01-repository-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage01/M2TestSupport.java`
- `src/test/java/com/linguan/codemd/stage01/M2Fixtures.java`
- `src/test/java/com/linguan/codemd/stage01/RepositoryUnderstandingRouteCallTest.java`
- `src/test/java/com/linguan/codemd/stage01/RepositoryUnderstandingMyBatisTest.java`
- `src/test/java/com/linguan/codemd/stage01/CapabilityAccountingTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated changes preserved; no task files changed before this progress file. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` | RED (expected) | testCompile reports only missing `RepositoryUnderstanding` and `Stage01Analyzer.understand(FrozenRepositoryRequest)` (20 compile diagnostics); no production code was added. |
| `git diff --check` | PASS | No whitespace errors in the task changes. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` (current production baseline) | BLOCKED/RED | Production compile stops on five existing `RepositoryCompiler.java` errors before tests run. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` (regression selector) | RED (expected) | Production and test compilation pass; 18 tests run, existing tests pass, and 10 requested assertions fail on missing P1 behavior. |
| `mvn -Dtest=RepositoryUnderstandingRouteCallTest,RepositoryUnderstandingMyBatisTest,CapabilityAccountingTest -DfailIfNoTests=false test` (fresh pre-slice baseline) | PASS | 18 tests run, 0 failures against the current production worktree. |
| `mvn -Dtest=RepositoryIntegrityRegressionTest -DfailIfNoTests=false test` | RED (expected) | Test compilation passes; 9 tests run, 6 assertion failures expose profile digest/parser, mapper decoy, entry ownership, snapshot-sensitive IDs, and exclusive-end locator bugs. Three budget/M1 guards are currently green. |
| `git diff --check -- src/test/java/com/linguan/codemd/stage01/RepositoryIntegrityRegressionTest.java progress/stage01-repository-tests.md` | PASS | No whitespace errors. |

## Decisions

- Tests will use explicit literal expectations for route, direct-call, mapper binding, CFG terminal, security, dynamic, ambiguity, and coverage behavior; they will not compute expected values through production helpers.
- The malicious extra controller test will declare only the six frozen files and assert identical snapshot/model/report identities, proving bounded read-only inventory behavior.
- A dynamic XML mutation will be declared as a test-only temporary variant with independently declared bytes and will assert an explicit unsupported/dynamic gap with no exact SQL fact.
- `sixFileReservationBuildsRouteCallsMapperBindingsAndFourTerminals` catches missing route composition, receiver-typed call binding, mapper method/statement edge closure, and CFG terminal enumeration.
- `publicM2SeamReturnsUnderstandingAndDoesNotAcceptAnUnverifiedPath` catches a missing public `RepositoryUnderstanding` return contract or an unsafe `Path` bypass seam.
- `publicM2SeamRunsM1AndRejectsHashDriftBeforeSemanticUnderstanding` catches M2 parsing unverified bytes instead of delegating through M1.
- `undeclaredControllerUnderSnapshotRootCannotChangeUnderstandingIdentityOrGraph` catches illegal directory rescans and identity pollution by undeclared files.
- `standardMyBatisDoctypeBindsBothStatementsWithoutExternalRetrieval` catches XXE/DOCTYPE network retrieval and failed namespace/statement binding.
- `dynamicIfAndDollarSqlIsAnExplicitGapWithoutExactSqlBinding` catches dynamic SQL being admitted as static SQL.
- The two ambiguity tests catch overload or same-simple-name receiver guessing and omitted AMBIGUOUS coverage.
- This regression slice adds literal budget disposition/count checks, insert/delete
  unsupported SQL, duplicate statement-id ambiguity, import-resolved Spring annotation
  checks, and wildcard/arity call-resolution gaps.
- `maxAstNodes`, `maxXmlNodes`, `maxSqlChars`, and `maxControlFlowNodes` tests each
  catch the corresponding budget being ignored instead of emitting an `OVER_LIMIT`
  site and incrementing `overLimitReachableSites`.
- Insert/delete tests catch v0 admitting unsupported SQL operations with static SQL
  nodes/facts rather than `UNSUPPORTED/UNSUPPORTED_SQL_OPERATION` gaps.
- Duplicate statement-id test catches an exact `METHOD_STATEMENT` edge surviving
  namespace ambiguity instead of both statements becoming `AMBIGUOUS`.
- Local mapping, wildcard import, and arity tests catch simple-name/import or
  method-name-only guesses producing exact Spring entries/call targets.

## Blockers

- The initial production compile blocker was repaired by the parent; the current
  selector reaches 10 expected assertion RED failures for the requested behaviors.
  No production implementation was added in this task.
- The independent selector reaches Surefire with only behavior RED (6 failures);
  no compile blocker remains. The three currently green guards are retained as
  regression coverage for behavior already present in the current production.

## Exact next action

- Parent implementation should make the six independent integrity assertions GREEN
  and rerun `mvn -Dtest=RepositoryIntegrityRegressionTest -DfailIfNoTests=false test`.
  This test-writing task is complete; no implementation was made here.

## Resume checks

- Re-read this file, run `git status --short`, and verify only this progress file plus new M2 test paths are changed before continuing.
