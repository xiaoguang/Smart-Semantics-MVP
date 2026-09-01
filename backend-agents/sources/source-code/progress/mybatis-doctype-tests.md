# Progress: MyBatis DOCTYPE discovery RED test

- Status: COMPLETE
- Agent role: Phase 1 discovery test sub-agent
- Model: Codex sub-agent
- Started: 2026-08-29T14:53:19-02:30
- Last updated: 2026-08-29T14:55:45-02:30
- Scope: Add one synthetic-fixture RED test proving a standard external MyBatis mapper DOCTYPE is parsed without external retrieval and yields one mapper binding plus one static UPDATE fact.
- Approved inputs: Local repository implementation and synthetic temporary fixture only; no network, model, captured source, or production implementation changes.
- Current branch/worktree: codex/rag-frontend-phase-one; preserve unrelated pre-existing worktree changes.

## Completed

- Read repository, source-to-standard-markdown, and github-code scoped instructions.
- Inspected `RepositoryDiscovererTest` and `RepositoryDiscoverer`; confirmed current XML parser disallows all DOCTYPE declarations.

## Current state

- Progress file created before test edits.
- Added `src/test/java/com/linguan/codemd/discovery/RepositoryDiscovererTest.java` test
  `parsesStandardMyBatisDoctypeWithoutExternalRetrieval` using a temporary local fixture.
- The test is intentionally RED against the current implementation: the standard external
  DOCTYPE is rejected before local mapper statements are visited, so bindings and static
  UPDATE facts are empty.

## Changed files

- `progress/mybatis-doctype-tests.md` (this file)
- `src/test/java/com/linguan/codemd/discovery/RepositoryDiscovererTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=RepositoryDiscovererTest test` | RED (expected) | 4 tests run; 2 failures, 0 errors. DOCTYPE test observed 0 mapper bindings/updates; parser reports `DOCTYPE is disallowed when the feature ... disallow-doctype-decl ... is set to true`. |

## Decisions

- Use a local temporary repository fixture with the standard MyBatis external DOCTYPE declaration and a deliberately unreachable external system identifier; assertions will prove parsing completes offline through the expected binding/static UPDATE facts.
- Keep this subtask test-only: do not alter `RepositoryDiscoverer` or any production file.

## Blockers

- No blocker for this test-only subtask. Production parser change remains with the parent task.

## Exact next action

Parent task should update the XML parser to ignore the standard external DTD without retrieval,
then rerun only `RepositoryDiscovererTest` to turn this contract test GREEN.

## Resume checks

- Test and progress paths are recorded above; no further subtask action is pending.
