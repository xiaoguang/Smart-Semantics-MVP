# Progress: jdt-index-entry-scope-green

- Status: IN_PROGRESS
- Agent role: Bounded production green fix for JDT Java-code-index entry-scoped CALL persistence
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: After the Luna RED handoff and parent go-ahead, update only the Java-code-index publisher, reader, directly required v2 gates/policies/tool fixture mechanics, and current documentation facts required by the exact v2 contract. No live scan, model call, customer build, recovery subsystem, or new behavior beyond the approved protocol correction.
- Approved inputs: Parent-issued exact java-code-index-v2 contract; existing frozen fixtures and public regression selector; current JDT design documents.
- Current branch/worktree: Shared source-code worktree at /private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code

## Completed

- Read scoped AGENTS.md, the superpowers TDD guidance, and the repository TDD guidance.
- Checked the dirty worktree and preserved all pre-existing changes.
- Confirmed the current design states the exact v2 entry-scoped CALL protocol and historical v1 non-compatibility policy.
- Mapped the direct production seam: `JavaCodeIndexPublicationSpecifier` globally deduplicates calls with `putSame`, and `JavaCodeIndexReader` globally looks up membership call keys. The affected v1 gates are the flow publication/compiler/fact-reader paths; the frozen repository-run policy and public fixture contain mechanical v1 identities.
- Recorded the parent’s narrowed delivery boundary: support the selected four-entry package/report path only; no whole-repository rescan or model run is in this scope.
- Received the precise Luna RED handoff and parent authorization to start GREEN: the three-test direct selector has one expected error in `preservesEntryLocalCallProjectionsWhenPhysicalCallIsShared`, originating at the publisher's global `putSame` call; the existing baseline and source-syntax-conflict regression pass.
- Implemented the v2 publisher/reader wire and all mapped production version gates. CALL records now carry `{entryId,call}` under the required framed digest key; reader verifies that key, owner membership, one-to-one membership use, and the cross-entry source-inherent CallSite projection.
- Updated the frozen repository-run policy entry to accept the v2 index identity. The coordinated public fixture policy is now v2.
- Verified the final combined selector after the selected-entry work: all three Java-code-index tests and both selected-entry tests are GREEN. Focused Spotless passed for the index and selected-scope production Java files, and final `git diff --check` passed.

## Current state

- The bounded index-v2 production fix is green. The shared broader workflow selector is awaiting its test-owned reflective helper to provide the new explicit empty selection; production deliberately remains fail-fast on null. The parent owns the separately authorized four-entry materials run; no full-repository scan was started here.

## Changed files

- progress/jdt-index-entry-scope-green.md (owned progress)
- src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexPublicationSpecifier.java
- src/main/java/org/sourceanalysis/app/analysis/code/publish/JavaCodeIndexReader.java
- src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java
- src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java
- src/main/java/org/sourceanalysis/app/analysis/flow/compiler/PersistedFlowCompilationInputReader.java
- src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java
- tools/repository-run/jdt-artifact-policy-set-v1.json

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared worktree modifications present and preserved; no production changes from this agent before the GREEN authorization. |
| `git diff --check` | PASS | Final shared-diff whitespace check passed after the v2 and selected-scope edits. |
| `mvn -Dtest=JavaCodeIndexPublicationSpecifierTest,ProgramGraphsSelectedEntryExecutionTest test` | BLOCKED | Maven stopped before compilation because the user-level toolchain registry has no JDK 17 entry. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=JavaCodeIndexPublicationSpecifierTest,ProgramGraphsSelectedEntryExecutionTest test` | PASS | `JavaCodeIndexPublicationSpecifierTest`: 3 tests, 0 failures/errors. `ProgramGraphsSelectedEntryExecutionTest`: 2 tests, 0 failures/errors. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply -DspotlessFiles=<touched production Java files>` | PASS | Focused Spotless completed successfully. |

## Decisions

- Preserve METHOD as global and keep EntryCodeContext, Step05, and business schemas unchanged.
- Tests remain owned by Luna; its public fixture may receive only the coordinated v2 policy identity update.
- Keep `MODULE_VERSION` unchanged: the exact contract upgrades the index schema and direct policy/readers, while no new module protocol/version contract was authorized.
- No writer or reader path selects a CallSite target/resolution from another entry; the only cross-entry comparison is the approved source-inherent projection.

## Blockers

- None.

## Exact next action

- Parent/test owner must update the existing reflective workflow fixture to provide `List.of()` for the new explicit selection, then rerun its direct selector. Parent may otherwise run only the already-authorized four-entry materials scope.

## Resume checks

- Re-read this progress file, run `git status --short`, verify the Luna RED output and parent authorization, then inspect the current shared diff before production edits.
