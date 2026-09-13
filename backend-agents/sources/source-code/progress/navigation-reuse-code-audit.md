# Progress: navigation-reuse-code-audit

- Status: COMPLETE (限定核对)
- Agent role: Read-only design/code gap auditor
- Model: GPT-5 Codex
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Narrowed audit of only the newly approved optimizations: one index for multiple entry references, raw JDT-query reuse, one shared body, and unit-once/quality-no-rerun/real-JDT-IT classification. Broad Steps 01–05 and general design/quality audit are DEFERRED.
- Approved inputs: Frozen commit 080a86db04c4917b27c5a48c88ca136d11bd0f2b; current target design and scoped AGENTS; user-approved design: one index for multiple entry references, raw-query reuse, one shared body, unit once/quality no rerun/real JDT IT.
- Current branch/worktree: formal repository checkout at db28f8d; existing dirty pom.xml and .mvn/toolchains.xml are preserved and out of scope.

## Completed

- Read repository/backend/source-scoped AGENTS from the current checkout and the scoped AGENTS at the frozen baseline.
- Confirmed the formal checkout is at `db28f8d`; pre-existing dirty `pom.xml` and `.mvn/toolchains.xml` remain untouched.
- Verified one JDT session and one collector/index are reused: `PersistedTechnicalRunExecutor.java:115-126`, `JdtProjectSession.java:121-135,194-211`, `EntryCodeCollector.java:183-205`, and all entry collection calls in `ProgramGraphsExecution.java:353-371`. The suspected “new collector on each collect” is false.
- Verified raw JDT navigation results are not cached across entry collections: each fresh `EntryCodeCollector.collect` has fresh per-entry `included`/`rawCalls` state (`EntryCodeCollector.java:82-95`), and `JdtNavigationResolver.resolve` invokes outgoing, definition, and implementation operations for the current method/call (`JdtNavigationResolver.java:26-74`); the client issues those RPCs directly (`JdtLanguageServerClient.java:161-215`). `openDocument` only de-duplicates `didOpen` (`JdtLanguageServerClient.java:141-148`), not query results.
- Verified Step03 persistence already has one global `METHOD` body per `methodKey`, entry-owned `CALL` records, and membership references: `JavaCodeIndexPublicationSpecifier.java:265-295,311-360`; reader reconstructs methods globally and calls by `(entryId, callKey)` (`JavaCodeIndexReader.java:117-155,169-225`). This matches the entry-owned CALL/shared METHOD contract in `contracts-and-configuration.md:168-180`.
- Verified the downstream Step05 flow-compilation wire still repeats full code contexts per entry: `EntryContextAssembler.java:62-87` copies each entry context, while `FlowCompilationModulePublisher.java:410-415,537-558` serializes `codeContext` inside every `entryContext`. Thus shared-body-once is implemented at the Step03 index layer but remains a downstream publication gap. Minimum direction: one canonical method table plus per-entry references while retaining entry-owned call projections.
- CI workflow intends a separate quality gate (`.github/workflows/source-analysis.yml:49-53`), but the skip-property wiring is inconsistent: the workflow passes `-DskipTests`, while Surefire and Failsafe are configured from `${skipUTs}` (`pom.xml:285,294`). Therefore the source does not prove quality avoids rerunning tests; the observed quality command reran 442 tests. The quality profile itself only binds SpotBugs/PMD (`pom.xml:312-355`), and root Failsafe has no execution binding (`pom.xml:290-297`). This optimization is pending correction.
- Verified the real JDT acceptance test is misclassified for that contract: `JdtRealSourceCollectionTest` is a `*Test` class with a real frozen fixture/JDT setup and `@Test` (`src/test/java/org/sourceanalysis/app/analysis/code/jdt/JdtRealSourceCollectionTest.java:32-72`), so it is a Surefire/unit-phase candidate; it is not an `*IT`, while root Failsafe has no executions. Minimum direction: classify/move it as a real-JDT IT and add the bounded Failsafe execution; keep synthetic JDT seam tests as unit tests.

## Deferred

- Full Steps 01–05 alignment audit, broad storage/model-material audit, general compiler/recovery audit, and any unrequested implementation are explicitly DEFERRED to the next task.

## Changed files

- This progress file only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` (formal checkout) | PASS | Initial audit checkpoint had only pre-existing `.mvn/toolchains.xml` and `pom.xml`; the shared worktree now also shows peer-agent changes. This agent edited only this progress file. |
| `git rev-parse --show-toplevel` | PASS | Formal repository is `linguan-prototype-v2`; source scope is `backend-agents/sources/source-code`. |

## Decisions

- Use `git show 080a86db...:<path>` for all code/design evidence; do not treat the current dirty checkout as implementation baseline.
- Report bounded, line-anchored findings as IMPLEMENTED, PARTIAL, NEW DESIGN NOT IMPLEMENTED, or PRE-EXISTING DEVIATION.

## Blockers

- None.

## Exact next action

- Parent agent incorporates the above bounded evidence into the optimization design; no further audit expansion in this task.

## Resume checks

- Re-read this file and run `git status --short` before any further action. Do not build, access network/customer repositories, or edit implementation files.
