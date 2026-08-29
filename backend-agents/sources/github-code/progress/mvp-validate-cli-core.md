# Progress: mvp-validate-cli-core

- Status: COMPLETE
- Agent role: Offline `code-md validate` CLI production implementer
- Model: gpt-5.6-terra (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Register and implement only the `code-md validate --workspace PATH --candidate-id ID` Picocli command under `src/main/java/**`, plus this progress file. Do not modify tests, POM, durable design, rules, frozen inputs, or external state.
- Approved inputs: Scoped `AGENTS.md`; `progress/TEMPLATE.md`; all MVP progress records, especially `progress/mvp-validate-cli-tests.md`; existing Java production and test sources; synthetic test fixtures and recorded JSON only.
- Current branch/worktree: `/Users/yexiaoguang/Documents/ErpMock` on `codex/rag-frontend-phase-one`; the target Maven project is untracked and shared.

## Completed

- Read the inherited and scoped repository rules, template, all related MVP progress files, Maven configuration, and all current Java production/test sources.
- Confirmed pre-existing root worktree changes are unrelated and will be preserved.
- Identified the acceptance contract: safely read only `workspace/candidate.json`, reject unsafe path/symlink/malformed or mismatched identity, rebuild `CandidateReference`, call `CandidateArchiveService.validate`, and emit exactly one UTF-8 JSON receipt to stdout for both valid and invalid validation results.
- Reproduced the focused Java 17 RED baseline: both tests fail only because `validate` is not registered, and the invalid-candidate path has no JSON receipt.
- Registered the offline `validate` subcommand. It reads the candidate sidecar only as a direct, non-link regular file in the real workspace and rejects an unexpected schema, fields, candidate ID, digest, or requested identity before invoking archive validation.
- Made completed invalid validation return `1` only after emitting its JSON receipt; valid validation returns `0`. Exceptions continue through Picocli and no branch rewrites `document.md`.
- Passed the direct RED-to-GREEN test and the complete existing MVP selector with no test failures or errors.

## Current state

Complete. The command validates a real workspace only after its non-link `candidate.json` passes strict schema, hash, canonical candidate-ID, direct-child, and requested-ID checks. It reads Markdown only without following links; the archive service remains responsible for findings and receipt persistence.

## Changed files

- progress/mvp-validate-cli-core.md
- src/main/java/com/linguan/codemd/cli/CodeMdCli.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing root changes preserved; scoped Maven project is untracked and in scope. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CodeMdCliValidateTest test` | RED (expected) | 2 tests, 0 errors: valid workspace returns exit 2 because `validate` is unregistered; tampered workspace has no stdout JSON receipt. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=CodeMdCliValidateTest test` | PASS | 2 tests, 0 failures, 0 errors: a fresh CLI writes one valid JSON receipt; a tampered Markdown writes `valid=false`, exits nonzero, persists the receipt, and remains unchanged. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,TraceLocatorTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest test` | PASS | Fresh final verification: 14 tests, 0 failures, 0 errors across all existing MVP test classes. |

## Decisions

- Keep `CandidateArchiveService` as the validation authority; the CLI only performs safe candidate-sidecar admission and receipt serialization.
- Continue using compact UTF-8 JSON objects for all machine artifacts; validation does not rewrite `document.md`.
- Emit only the five requested receipt fields to command stdout; use exit code `1` only for a completed invalid receipt and retain Picocli's nonzero exception behavior for admission or I/O failures.

## Blockers

- None.

## Exact next action

None — task complete.

## Resume checks

- Read this progress file and scoped `AGENTS.md`.
- Run `git status --short` from `/Users/yexiaoguang/Documents/ErpMock`.
- Rerun the focused Maven selector before reporting success.
