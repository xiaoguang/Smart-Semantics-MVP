# Progress: mvp-core

- Status: COMPLETE
- Agent role: deterministic Java MVP production-core implementer
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Implement only the first-day deterministic Java production core under `src/main/java/**`, the compileable minimal CLI shell, and this progress record. No tests, POM, immutable source input, network, customer-source reads, Maven execution of a customer application, or live model calls.
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; `progress/mvp-integration.md`; `progress/mvp-tests.md`; existing Maven configuration; supplied synthetic test contract; Flow Manifest and scripted provider JSON only.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; target Maven directory is untracked and intentionally implemented in place.

## Completed

- Read the scoped repository rules, progress template and related MVP progress files, POM, all five supplied MVP test sources, and the relevant MVP design constraints.
- Confirmed pre-existing root worktree changes are unrelated and will be preserved.
- Identified the required stable seam: `CodeToMarkdownAgent`, `GenerationRequest`, `ModelProvider`, and the small output/trace records; verification, admission, rendering, and in-memory candidate lookup stay package-private.
- Reproduced the intended RED baseline with Java 17: test compilation fails exclusively because the planned MVP production API is absent.
- Added the Java 17 public seam, compileable Picocli shell, and package-private core for manifest/source/excerpt verification, two-round JSON admission, deterministic nine-section rendering, candidate SHA identity, and in-memory trace lookup.
- Diagnosed the first focused GREEN run: Java compilation stopped before tests because the newly added core references `TreeMap` without importing `java.util.TreeMap`.
- Applied that single import repair and reran the required selector successfully: all eight supplied tests pass under Java 17.
- Completion review corrected two determinism/provenance faults: task identity now preserves lexicographic Fact/Evidence ordering, and a candidate ID now combines Markdown content identity with verified snapshot and trace identities while `candidateContentId` remains the Markdown UTF-8 SHA-256.
- Corrected multi-line locator ordering so end-column comparison applies only when the locator starts and ends on the same line.

## Current state

The deterministic first-day core is implemented and its supplied focused acceptance selector is green. The remaining CLI/provider persistence and live-runtime work stays explicitly outside this task's scope.

## Changed files

- progress/mvp-core.md
- src/main/java/com/linguan/codemd/mvp/CodeToMarkdownAgent.java
- src/main/java/com/linguan/codemd/mvp/DefaultCodeToMarkdownAgent.java
- src/main/java/com/linguan/codemd/mvp/GenerationRequest.java
- src/main/java/com/linguan/codemd/mvp/ModelProvider.java
- src/main/java/com/linguan/codemd/mvp/ModelTask.java
- src/main/java/com/linguan/codemd/mvp/CandidateReference.java
- src/main/java/com/linguan/codemd/mvp/TraceQuery.java
- src/main/java/com/linguan/codemd/mvp/TraceView.java
- src/main/java/com/linguan/codemd/mvp/TraceEvidence.java
- src/main/java/com/linguan/codemd/mvp/MvpGenerationCore.java
- src/main/java/com/linguan/codemd/cli/CodeMdCli.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Root contains unrelated existing and untracked work; target Maven directory is untracked and in scope. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | RED (expected) | Java 17 test compilation failed only because the nine planned MVP production types are absent; no dependency or test-fixture configuration error. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | BLOCKED at compile | `MvpGenerationCore.java` has four `TreeMap` references but lacks the single Java utility import; Surefire tests did not start. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | PASS | Java 17 compiled 11 production and 5 test sources; Surefire ran 8 tests with 0 failures and 0 errors. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | PASS | After completion-review fixes, Java 17 compiled 11 production and 5 test sources; Surefire ran 8 tests with 0 failures and 0 errors. |

## Decisions

- Keep one Maven module and limit public production types to the test-required Java seam.
- Use Jackson only for Flow Manifest and scripted R1/R2 response parsing; all generated-machine-artifact concepts remain canonical JSON/JSONL and no serialized/binary/DB state is introduced.
- Fail closed with the required stable error-code tokens; final Markdown is generated deterministically by program code, never by the provider.
- Keep multi-line locator columns as declared manifest coordinates; the excerpt hash is the authority for exact frozen line-span content, while same-line locators must retain forward column order.

## Blockers

- None.

## Exact next action

Hand the completed production core and focused verification result to the integration coordinator.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from `/Users/yexiaoguang/Documents/ErpMock`.
- Inspect `src/main/java` and rerun the focused Maven selector before reporting success.
