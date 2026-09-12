# Progress: Task 5 local CI remediation

- Status: COMPLETE
- Agent role: Terra/xhigh production remediation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Repair the three full-local-CI failures assigned by the parent: Step 05 upstream
  receipt-lineage validation, neutral test-support package relocation, and the bounded
  Flow-signal integrity-test disposition.
- Approved inputs: Scoped `AGENTS.md`; parent-provided full-CI root-cause evidence; the named
  direct tests and current source tree.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Read the scoped operating rules and started root-cause investigation.
- Reproduced all three assigned failures with the direct selector: 10 tests, 3 assertion
  failures, 0 errors/skips.
- Confirmed the Step 05 specifier accepts the forged M2 module because `requireModule` checks
  only address/run/controls/payload descriptor, not the predecessor artifact list.
- Confirmed the architecture failure is limited to the neutral helper and its contract test under
  `org.sourceanalysis.app.testsupport`.
- Confirmed the Flow-signal publisher validates entry/fact/gap coverage but has no signal-basis
  closure check; it does not need to rerun `EntryRootedFlowCompiler` to reject the mutations.
- Re-ran the Flow-signal selector after adding the basis closure. The cross-Flow basis mutation was
  rejected; the missing-signal mutation was accepted because an ordinary publisher has no persisted
  denominator for the compiler's complete signal set. This is an intentional no-replay boundary, not
  a source-closure failure. A changed call-target anchor is still source-derivable from its admitted
  atom values, so the next minimal change validates that relation.
- Added the M1/M2 receipt-upstream equality check and a persisted Fact-atom closure in the Step 05
  specifier. The latter closes the self-consistent rehash mutation without replaying a publisher or
  projector algorithm.
- Relocated `BusinessFlowTestSupport` and its contract test to
  `org.sourceanalysis.app.analysis.flow.testsupport`; all 16 direct consumers now import the
  semantic Flow-owned helper, and no old test-support imports remain.
- Added the Flow publisher's cheap process-signal source closure: each signal's Fact, atom, Proof,
  evidence, locator, flow ownership and atom-derived `JAVA_TYPE`/`CALL_TARGET` anchor must match
  persisted inputs. It rejects foreign bases and anchor changes without rerunning the compiler.
- Replaced only the obsolete missing-signal assertion: a persisted predecessor does not carry the
  compiler's full process-signal denominator, so rejecting a self-consistent omission would require
  the explicitly prohibited compiler replay.

## Current state

- The assigned three local-CI failures are resolved and their direct tests are green. The parent
  still owns the required full local CI gate after shared-task integration.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/compiler/FlowCompilationModulePublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowSignalPublicationIntegrityTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/testsupport/BusinessFlowTestSupport.java`
- `src/test/java/org/sourceanalysis/app/analysis/flow/testsupport/BusinessFlowTestSupportContractTest.java`
- Deleted: `src/test/java/org/sourceanalysis/app/testsupport/BusinessFlowTestSupport.java`
- Deleted: `src/test/java/org/sourceanalysis/app/testsupport/BusinessFlowTestSupportContractTest.java`
- 16 direct test consumers updated only to use the relocated helper import.
- `progress/task5-local-ci-remediation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest,SourceAnalysisArchitectureTest,FlowSignalPublicationIntegrityTest test` | Expected RED | 10 tests: 3 failures, 0 errors/skips. Forged M2 accepted; two test-support packages rejected; three signal mutations accepted. |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest test` | PASS | 6 tests, 0 failures/errors/skips after persisted upstream and Fact-atom closure checks. |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=SourceAnalysisArchitectureTest test` | PASS | 3 tests, 0 failures/errors/skips after relocating the helper into the Flow test-support package. |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=FlowSignalPublicationIntegrityTest test` | Expected partial RED | 1 test, 1 assertion failure: foreign-basis rejection passed; missing self-consistent signal has no independently persisted denominator and was accepted. |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowProvenanceTest,SourceAnalysisArchitectureTest,FlowSignalPublicationIntegrityTest test` | PASS | 10 tests, 0 failures/errors/skips. Re-run after scoped formatting also PASS. |
| `MAVEN_OPTS=-Xmx8g mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowTestSupportContractTest,BusinessFlowCoverageTest,ActivityCoverageV2ContractTest,ActivityExplainerDirectEntryContextTest,ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest,ActivityOutputSchemaTest,ActivityPromptContractTest,BusinessMaterialBuilderFallbackTest,BusinessMaterialBuilderReplenishmentTest,BusinessMaterialBuilderTest,BusinessMaterialBuilderZeroEntryTest,ProcessKnowledgeCheckpointTest,BusinessReportCheckpointTest,PersistedBusinessRunExecutorTest,RepositoryAnalysisRunCoordinatorTest test` | PASS | 34 tests, 0 failures/errors/skips. |
| Scoped `spotless:apply` then `spotless:check` | PASS | Scoped check passed. `apply` noted the two deleted old helper files, but did not fail. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Do not modify Task 5 activity/process/report semantics or design documents.
- Do not replay the Step 05 compiler in an ordinary publisher; only a cheap persisted-source
  closure check is eligible for the Flow-signal issue.
- Replace only the obsolete missing-signal assertion. The publisher still rejects foreign source
  bases and anchors inconsistent with persisted Fact atoms; deriving the entire signal set would
  require the prohibited compiler replay.

## Blockers

- No Task-local blocker. The parent must still run the newly required full local CI after all shared
  remediations are integrated.

## Exact next action

- Hand the verified remediation back to the parent; do not commit or push from this task.

## Resume checks

- Re-read this progress file, inspect `git status --short`, and preserve the parent-owned Task 5
  semantic changes before any future local-CI work.
