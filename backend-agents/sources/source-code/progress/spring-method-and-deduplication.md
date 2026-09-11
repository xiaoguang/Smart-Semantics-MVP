# Progress: Spring method condition and ordinary-path deduplication

- Status: COMPLETE
- Agent role: Primary implementation agent
- Model: gpt-5.6-terra / xhigh for production changes; direct TDD verification in the current task
- Started: 2026-09-11 11:31 UTC
- Last updated: 2026-09-11 12:16 UTC
- Scope: Implement the approved Step02 Spring `@RequestMapping` omitted/empty method compatibility and remove the remaining ordinary-path Fact reopen re-enumeration without weakening reopen validation.
- Approved inputs: User-approved "连贯代码材料到九章业务文档：现有实现修正计划"; Step02/Step05 design; frozen fixtures only.
- Current branch/worktree: codex/source-analysis-business-flows-closeout / /private/tmp/linguan-source-analysis-process-design

## Completed

- Read the Step02 method-condition contract and current discovery implementation.
- Confirmed the current defect: method-level `@RequestMapping` without a single `RequestMethod` becomes `UNSPECIFIED_HTTP_METHOD` and is published as a Gap, although Spring treats it as unrestricted.
- Confirmed the current `HttpEntryPoint.method` string and public JSON `method` field cannot represent the approved unrestricted-versus-explicit-set contract; direct consumers are limited to discovery publication and persisted graph/material readers.
- Wrote and observed the intended RED for both `@RequestMapping` with omitted `method` and `method={}`: each returned zero entries under the old implementation.
- Added `HttpMethodCondition` as the authoritative `UNRESTRICTED`/`EXPLICIT` condition. The discoverer now parses all supported RequestMethod enum values, combines class and method conditions without creating synthetic entries, and does not invent GET, HEAD, or OPTIONS handlers.
- Persisted the condition in discovery module and public entry JSON. Direct graph, Fact, flow and material readers consume the condition; the retained `method` string is derived presentation text, never the authoritative condition.
- Added a persisted-entry assertion and direct regression for class/method condition combination.
- Wrote and observed the RED for a normal Fact-candidate reopen seam: the old reader had no non-audit reopen API because every consumer re-enumerated candidates.
- Added normal two-argument reopen that validates stored publication/basis/shape without rerunning enumeration. The three-argument overload remains an explicit audit/test replay. Fact ledger and proof readers now use the normal path.

## Current state

- Phase 2 implementation and its direct verification are complete. No business module, source capture behavior or model call path changed. Full-repository discovery remains a later acceptance run; the targeted regression verifies the legal Spring mapping contract and its direct persisted consumers.

## Changed files

- progress/spring-method-and-deduplication.md
- src/main/java/org/sourceanalysis/app/analysis/discovery/HttpMethodCondition.java
- src/main/java/org/sourceanalysis/app/analysis/discovery/HttpEntryPoint.java
- src/main/java/org/sourceanalysis/app/analysis/discovery/SpringHttpEntryDiscoverer.java
- src/main/java/org/sourceanalysis/app/analysis/discovery/HttpEntryDiscoveryModulePublisher.java
- src/main/java/org/sourceanalysis/app/analysis/graph/PersistedProgramGraphInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateSetReader.java
- src/main/java/org/sourceanalysis/app/analysis/fact/publish/FactLedgerPublicationSpecifier.java
- src/main/java/org/sourceanalysis/app/analysis/fact/proofs/PersistedProofDecisionSetReader.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/interpretation/material/BusinessMaterialBuilder.java
- src/test/java/org/sourceanalysis/app/analysis/discovery/SpringHttpEntryDiscovererTest.java
- src/test/java/org/sourceanalysis/app/analysis/discovery/ApplicationDiscoveryPublicationSpecifierTest.java
- src/test/java/org/sourceanalysis/app/analysis/graph/PersistedProgramGraphInputReaderTest.java
- src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateModuleReaderTest.java
- docs/analysis-steps/02-application-discovery.md
- docs/DESIGN.md
- README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Read-only inspection of `SpringHttpEntryDiscoverer` and Step02 contract | PASS | Current branch emits `UNSPECIFIED_HTTP_METHOD` when `RequestMapping` has no explicit single method. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=SpringHttpEntryDiscovererTest#discoversARequestMappingWithoutAnExplicitHttpMethodAsOneUnrestrictedEntry+discoversARequestMappingWithAnEmptyHttpMethodArrayAsOneUnrestrictedEntry test` | Expected RED | 2 tests, 2 assertion failures: old discovery returned no entry for either legal mapping. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=SpringHttpEntryDiscovererTest test` | PASS | 12 tests, 0 failures/errors: unrestricted, explicit and combined method conditions are one handler condition. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleReaderTest#freshReopenReturnsTypedCandidateSetAndRejectsPersistedPayloadTamper test` | Expected RED then PASS | Initial normal-reopen seam was absent; final test reopens without a registry, preserves audit replay and rejects payload tampering. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o -Dtest=SpringHttpEntryDiscovererTest,HttpEntryDiscoveryModulePublisherTest,ApplicationDiscoveryPublicationSpecifierTest,PersistedProgramGraphInputReaderTest,FactCandidateModuleReaderTest,ProofDecisionSetModuleArtifactTest,ProvenCodeFactsPublicationSpecifierTest,EntryRootedFlowCompilerTest test` | PASS | 34 tests, 0 failures/errors: discovery condition survives public storage and Fact/graph/flow consumers; normal Fact readers close. |
| `MAVEN_OPTS='-Xmx8g' mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 590 Java files clean. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Use the approved `HttpMethodCondition` shape (`UNRESTRICTED` with no methods, or `EXPLICIT` with a canonical nonempty method set), not a fake GET or an overloaded blank/sentinel string.
- Do not split one unrestricted handler into one business entry per HTTP verb.
- Ordinary downstream Fact consumption verifies immutable stored data; only the named audit overload re-enumerates candidates against a registry.

## Blockers

- None.

## Exact next action

- Start Phase 3 by tracing the persisted business-material → Activity DRAFT/REVIEW → Process → Report public path with a scripted Provider. Do not call a live Provider.

## Resume checks

- Read this file and `docs/analysis-steps/02-application-discovery.md`, inspect `git status --short`, then run only `SpringHttpEntryDiscovererTest` until the public discovery contract is green.
