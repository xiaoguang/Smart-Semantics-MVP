# Progress: mvp-integration

- Status: COMPLETE
- Agent role: integration and implementation coordinator
- Model: gpt-5.6-sol
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: one-day GitHub code Agent MVP in this directory
- Approved inputs: jshERP commit 8c30ce7861570458920175e200bb2a6442713580; logged-in Codex session after preflight
- Current branch/worktree: /Users/yexiaoguang/Documents/ErpMock on codex/rag-frontend-phase-one; target directory is untracked and implemented in place

## Completed

- Read inherited Agent rules, the approved MVP plan, and the existing design.
- Confirmed the target directory has only README.md and DESIGN.md before implementation.
- Added the scoped progress contract and this initial progress record.
- Added the Java 17 Maven dependency and test-runner skeleton without any
  production classes.
- Recorded the JSON/JSONL-only machine-artifact contract requested during
  implementation.
- Confirmed the focused test suite is RED for only the still-absent MVP
  production API, then handed the implementation seam to the Terra core task.
- Integrated the Terra core after focused GREEN verification; a completion
  review corrected deterministic task ordering, candidate provenance identity,
  and multi-line locator ordering before handoff.
- Integrated JSON candidate sealing, offline validation, and recorded-provider
  CLI after the persistence acceptance tests passed.
- Attempted the expressly approved fixed-commit capture; the environment DNS
  could not resolve GitHub while fetching the required commit object. The only
  local frozen jshERP sidecar discovered has a different commit, so it was not
  substituted as this MVP's source input.
- Added the persisted CLI `validate` contract after its test-first implementation.
- Updated the runnable-JAR packaging binding and durable README to reflect the
  implemented MVP and the current real-source identity blocker.
- Packaged and launched the shaded CLI help after Maven Central supplied the
  two missing Shade-plugin build-time dependencies under explicit approval.
- Fetched and detached-checked-out the approved jshERP commit
  `8c30ce7861570458920175e200bb2a6442713580` in the ignored local frozen
  snapshot workspace; verified the checkout reports that exact SHA.
- Selected a small real `DepotHead` batch status flow and sealed its three
  source files, five exact evidence spans, and five locked facts in a local
  Flow Manifest. No client code was executed.
- Integrated and independently ran the current MVP, discovery, and CodeFact
  focused selector: 22 tests passed with zero failures or errors.
- Ran the one permitted Luna R1 against the sealed task package. Its response
  was schema-valid and used only the allowed evidence/fact references.
- Preserved, but did not admit, the R2 response: Codex `resume` reported that
  it resumed as Sol/high/workspace-write rather than the required
  Luna/xhigh/read-only execution. This is a model-run contract failure, not a
  source or evidence failure; no candidate will be generated from it and no
  automatic model retry will occur.
- Added and passed a focused runtime-receipt admission guard: provider, model,
  reasoning effort, and sandbox must all exactly match the frozen policy before
  a recorded model result is admissible.

## Current state

The one-day MVP is complete. The approved frozen source and a three-file,
five-evidence `DepotHead` Flow Manifest are sealed. One Luna R1 is valid; the
R2 attempt is retained as a rejected runtime-policy receipt. The independent
deterministic baseline API/CLI sealed a valid real-source candidate instead.
Static discovery now handles standard MyBatis DOCTYPE declarations without
external access, and all focused tests/package checks listed below are fresh.

## Changed files

- AGENTS.md
- .gitignore
- progress/TEMPLATE.md
- progress/mvp-integration.md
- pom.xml
- DESIGN.md
- src/test/java/com/linguan/codemd/mvp/MvpFixtures.java
- src/test/java/com/linguan/codemd/mvp/ManifestEvidenceVerificationTest.java
- src/test/java/com/linguan/codemd/mvp/InterpretationAdmissionGateTest.java
- src/test/java/com/linguan/codemd/mvp/NineSectionRenderingDeterminismTest.java
- src/test/java/com/linguan/codemd/mvp/TraceLocatorTest.java
- src/main/java/com/linguan/codemd/mvp/
- src/main/java/com/linguan/codemd/cli/CodeMdCli.java
- src/main/java/com/linguan/codemd/discovery/
- src/main/java/com/linguan/codemd/analysis/
- src/test/java/com/linguan/codemd/mvp/ModelRuntimeReceiptAdmissionTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | PASS | Existing root changes preserved; target had no prior tracked changes |
| Maven dependency cache inspection | PASS | Jackson 2.21.4, JUnit 5.13.4, Picocli 4.7.7 and required Maven plugins are locally available |
| `JAVA_HOME=...openjdk@17... mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | RED (expected) | Test compilation fails only for the absent planned MVP production API |
| `JAVA_HOME=...openjdk@17... mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | PASS | 8 tests, 0 failures, 0 errors after deterministic/provenance review fixes |
| `JAVA_HOME=...openjdk@17... mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | PASS | 12 tests, 0 failures, 0 errors |
| `git fetch --no-tags origin 8c30ce7861570458920175e200bb2a6442713580 && git checkout --detach 8c30ce7861570458920175e200bb2a6442713580` | PASS | Exact approved frozen commit fetched and checked out locally |
| `JAVA_HOME=...openjdk@17... mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest test` | PASS | 14 tests, 0 failures, 0 errors |
| `JAVA_HOME=...openjdk@17... mvn -DskipTests package` | PASS | Runnable shaded JAR created after approved Maven Central download of Shade's two missing build-time dependencies |
| `java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar --help` | PASS | Exposes generate, trace and validate commands |
| Focused MVP + discovery + CodeFact test selector | PASS | 22 tests, 0 failures, 0 errors |
| Codex Subscription R1 (`gpt-5.6-luna`, xhigh, read-only) | PASS | Schema-valid, allowlist-contained Flow Interpretation response |
| Codex Subscription R2 resume | FAILED AS DESIGNED | Runtime receipt reports `gpt-5.6-sol`, high, workspace-write; result retained but not admitted |
| `mvn -o -Dtest=ModelRuntimeReceiptAdmissionTest test` | RED then PASS | Missing production seam first; 4 tests passed after implementation |
| Combined MVP + Phase 1 + Phase 2 focused selector | PASS | 26 tests, 0 failures, 0 errors |
| `mvn -o -DskipTests package` and shaded JAR `--help` | PASS | Runnable Java 17 CLI exposes generate, trace, validate, inspect, discover |
| `mvn -o -Dtest=RepositoryDiscovererTest,CodeMdCliDiscoveryTest test` | PASS | 6 tests, 0 failures, 0 errors; includes standard MyBatis DOCTYPE regression |
| Frozen jshERP `discover` | PASS | 82 routes, 1,296 direct calls, 572 Mapper/XML bindings, 5 static UPDATE facts; only `DYNAMIC_SQL_UNRESOLVED` remains |
| Combined current direct-coverage selector | PASS | 28 tests, 0 failures, 0 errors; includes deterministic baseline and DOCTYPE regression |
| `mvn -o -DskipTests package` | PASS | Shaded Java 17 CLI package rebuilt after baseline core addition |
| Frozen jshERP `baseline` → `validate` → `trace` | PASS | Candidate `candidate:e74...317753`; no validation findings and five exact source spans traced |

## Decisions

- Work in the user-specified untracked target directory because a Git worktree would omit it.
- Keep the first MVP as one Maven module and use MyBatis only as parsed XML source input.
- Store all generated machine artifacts as canonical JSON or JSONL; Markdown is
  reserved for reader-facing output and durable human-maintained documentation.
- Do not replace the approved `8c30ce...` source identity with a different
  local sidecar.
- Treat provider model, reasoning effort, and sandbox identity as mandatory
  runtime receipt fields. A resumed model task that differs from its frozen
  contract is terminally non-admissible.

## Blockers

- No permission blocker. The model contract prevents silently retrying the
  invalid R2; this run keeps all source and model artifacts for audit.

## Exact next action

Begin the next phase in a new owned progress file: combine automatic CodeFact
results with discovered Flow slices. Do not alter the sealed MVP candidate or
retry its rejected model round.

## Completion state

- Status: COMPLETE for the one-day MVP scope.
- Delivered: Java 17/Maven core, recorded-model admission, runtime identity
  gate, deterministic baseline API/CLI/archive, source-only discovery and
  CodeFact seam, exactly-nine-section rendering, JSON sidecars, trace and
  validation.
- Real-source result: baseline candidate is valid; model-enhanced candidate is
  terminally rejected because the observed R2 runtime violated its policy.
- Next scope: Phase 2 integration of automatic CodeFact results into Flow
  assembly, with no reinterpretation of the sealed MVP candidate.

## Resume checks

- Read AGENTS.md and this file.
- Run git status --short from the repository root.
- Inspect the target Maven project state before continuing.
