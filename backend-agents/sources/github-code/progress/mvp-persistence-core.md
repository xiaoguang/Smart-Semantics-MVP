# Progress: mvp-persistence-core

- Status: COMPLETE
- Agent role: JSON candidate persistence, validation, and recorded-provider CLI implementer
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Implement only `src/main/java/**` plus this progress file for the approved MVP JSON sidecars, validation receipt, and Picocli recorded-provider commands. Do not modify tests, POM, design, rules, frozen inputs, or external state.
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; `progress/mvp-integration.md`; `progress/mvp-core.md`; `progress/mvp-persistence-tests.md`; current Java production/test sources; synthetic manifests and recorded JSON fixtures.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; the Maven project is untracked and intentionally implemented in place.

## Completed

- Read the inherited/scoped agent rules, required progress records, Maven configuration, relevant durable design seam, and every current Java source/test file.
- Confirmed existing root changes are unrelated and will be preserved.
- Identified the stable implementation boundary: archive reuses the verified generation core and stores only its verified trace/evidence and recorded structured responses; CLI never creates a live provider.
- Reproduced the exact required Java 17 RED baseline: test compilation fails only because `CandidateArchiveService` and `ValidationReceipt` are absent.
- Added a content-addressed `CandidateArchiveService` that stages and atomically installs exactly eight contract artifacts: one reader Markdown file and seven compact canonical JSON objects.
- Added fail-closed workspace conflict checks, immutable repeated-archive byte checks, persisted validation receipts, recorded document-hash/nine-heading validation, and archived JSON-backed trace lookup.
- Extended the stable Java seam with `ValidationReceipt` and in-memory `CodeToMarkdownAgent.validate` without changing existing generation or trace behavior.
- Retained the verified manifest evidence and structured R1/R2 responses inside the package-private generation result solely for persistence sidecars.
- Replaced the CLI shell with offline Picocli `generate` and `trace` commands. `generate` accepts only two recorded JSON objects and consumes each round once; `trace` reads the archive with no in-memory candidate dependency and rejects unsafe tokens/locators.
- Reviewed the archived candidate record so validation compares its explicit recorded document SHA-256 as well as the actual Markdown bytes and exact H2 shape.

## Current state

All scoped implementation and regression verification is complete. No network, live provider, customer source, or external runtime was used.

## Changed files

- progress/mvp-persistence-core.md
- src/main/java/com/linguan/codemd/mvp/CandidateArchiveService.java
- src/main/java/com/linguan/codemd/mvp/ValidationReceipt.java
- src/main/java/com/linguan/codemd/mvp/MvpGenerationCore.java
- src/main/java/com/linguan/codemd/mvp/CodeToMarkdownAgent.java
- src/main/java/com/linguan/codemd/mvp/DefaultCodeToMarkdownAgent.java
- src/main/java/com/linguan/codemd/cli/CodeMdCli.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing root changes preserved; scoped Maven project is untracked and in scope. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | RED (expected) | Test compilation fails only for absent `CandidateArchiveService` and `ValidationReceipt`; no fixture, dependency, or existing-core error occurs. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | BLOCKED at compile | `CandidateArchiveService` uses `this::isRegularNonLink` for a static helper in two layout checks; tests did not start. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | PASS | 4 tests, 0 failures, 0 errors. Archive writes the expected sidecars; validation fails on tampered Markdown; fresh CLI trace reads archived JSON. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | PASS | 12 tests, 0 failures, 0 errors across all six current MVP test classes. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest test` | PASS | Fresh post-review regression: 12 tests, 0 failures, 0 errors. |

## Decisions

- Keep all generated sidecars as compact canonical UTF-8 JSON objects; `document.md` remains the only generated Markdown artifact.
- Treat any existing non-identical or malformed workspace as a fail-closed conflict, so an archive never overwrites another candidate or repairs tampered reader-facing Markdown.
- Read recorded R1/R2 JSON as the sole CLI provider responses and consume each round exactly once; no live provider is implemented.
- Persist direct structured R1/R2 objects for the current single-flow MVP; a multiple-flow archive uses a deterministic wrapper that retains every individual response rather than silently dropping one.

## Blockers

- None.

## Exact next action

Hand the completed persistence/CLI slice and verification evidence to the integration coordinator.

## Resume checks

- Read this file and scoped `AGENTS.md`.
- Run `git status --short` from the repository root.
- Rerun the focused Maven selector before reporting success.
