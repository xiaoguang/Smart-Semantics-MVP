# Progress: mvp-tests

- Status: COMPLETE
- Agent role: MVP TDD test author
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add only Java 17/JUnit 5 MVP tests and synthetic fixtures for the frozen-manifest, gated interpretation, deterministic nine-section rendering, and trace contracts.
- Approved inputs: User-approved MVP plan; local synthetic fixtures only; no network, jshERP source, customer build, or live model.
- Current branch/worktree: /Users/yexiaoguang/Documents/ErpMock on codex/rag-frontend-phase-one; target is an untracked directory worked in place.

## Completed

- Read the scoped AGENTS.md, progress template, MVP integration progress, DESIGN.md, and pom.xml.
- Confirmed the existing Maven skeleton contains no Java production or test sources.
- Added a test-only synthetic frozen snapshot helper for a small Controller → Service → Mapper XML flow.
- Added focused tests for source/excerpt tampering, R1/R2 admission violations, nine-section rendering/determinism, and exact trace locators.

## Current state

The test contract is translated into compile-time references to the planned core API. Production types are intentionally absent so the first targeted Maven run must fail for the missing implementation, not for missing dependencies or Maven configuration.

## Changed files

- progress/mvp-tests.md
- src/test/java/com/linguan/codemd/mvp/MvpFixtures.java
- src/test/java/com/linguan/codemd/mvp/ManifestEvidenceVerificationTest.java
- src/test/java/com/linguan/codemd/mvp/InterpretationAdmissionGateTest.java
- src/test/java/com/linguan/codemd/mvp/NineSectionRenderingDeterminismTest.java
- src/test/java/com/linguan/codemd/mvp/TraceLocatorTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest test` | RED (expected) | Maven resolved dependencies and compiled the test source set; compilation failed only because the planned production API types are absent (`CodeToMarkdownAgent`, `DefaultCodeToMarkdownAgent`, `GenerationRequest`, `ModelProvider`, `ModelTask`, `CandidateReference`, `TraceQuery`, `TraceView`, `TraceEvidence`). |

## Decisions

- Use package `com.linguan.codemd.mvp` and a test-side `MvpFixtures` helper to keep tests readable while leaving source and manifest creation inside temporary directories.
- Keep scripted R1/R2 responses in test data; no provider call or network access is permitted.
- Assert exact nine H2 headings and deterministic UTF-8 bytes without asserting unstable display wording.
- Expected production seam is `DefaultCodeToMarkdownAgent`, `CodeToMarkdownAgent`, `GenerationRequest`, `ModelProvider`, `ModelTask`, `CandidateReference`, `TraceQuery`, `TraceView`, and `TraceEvidence`; test providers generate task-matched R1/R2 JSON.

## Blockers

- The planned production API and records do not yet exist; this is the intentional RED condition. No test-helper syntax or dependency failure remains.

## Exact next action

Production implementer should add the referenced core API and rerun the same targeted test selector under Java 17; preserve these tests as the MVP acceptance seam.

## Resume checks

- Read this file and the scoped AGENTS.md before continuing.
- Confirm only this progress file and `src/test/**` are in scope.
- Run `git status --short` from the repository root before further edits.
