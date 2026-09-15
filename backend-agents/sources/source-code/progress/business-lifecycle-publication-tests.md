# Progress: business lifecycle publication tests

- Status: COMPLETE (RED tests recorded)
- Agent role: Step07 publication RED-test owner
- Model: gpt-5.6-luna / xhigh; no product-model or source-capture call
- Started: 2026-09-15
- Scope: Add focused behavioral RED tests for the approved readable five-file Step07 publication contract. Production Java, resources, prompts, runtime, and historical artifacts are out of scope.
- Approved inputs: `docs/DESIGN.md` sections 9–11 and `docs/plans/business-process-discovery-and-reconstruction-change-design.md`; existing Step07 publication/query/run-output contracts and frozen fixtures.
- Current branch/worktree: `codex/business-lifecycle-readable-implementation` / nested source-code checkout

## Contract under test

The tests cover business-first stage prose and scoped rule sentences, deterministic
`sources.md` rendering and anchors, exactly five canonical Step07 payloads with
catalog/coverage v2 and publisher v2, exact-five fresh reopen and byte-identical
Markdown rerendering, public artifact-query visibility, and preservation of the
Activity name, stage narrative, and rule `activityUseIds` in JSON.

## Constraints

- Use existing scripted/frozen fixtures only; do not invoke a Provider, source scan,
  network, or generation workflow.
- Where the new source Markdown accessor or artifact-query key is not yet present,
  tests use reflection/JSON inspection so compilation remains valid and the failure
  is behavioral.
- Do not modify production, docs other than this progress record, resources,
  prompts, or runtime implementation.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated worktree changes preserved before this task. |
| Scoped source/design inspection | PASS | Read nearest AGENTS, approved DESIGN sections 9–11, change plan, fixtures, tests, publisher, reader, and renderer. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessProcessPublicationTest,PublicBusinessArtifactQueryContractTest test` | EXPECTED RED | 9 tests: 4 assertion failures and 3 reader errors from the old four-file/v1 publisher (missing sources.md, v2 schemas/producer, narrative/rule-use JSON and source links); query policy has no sources.md key. Test compilation succeeded. |
| `mvn -o -t .mvn/toolchains.xml spotless:apply` | PASS | Publication test was formatted; unrelated shared-worktree files were not staged. |
| `mvn -o -t .mvn/toolchains.xml spotless:check` | PASS | All 605 Java files clean after formatting. |
| `git diff --check` | PASS | No whitespace errors in the owned test/progress changes. |

## Changed files

- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPublicationTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/PublicBusinessArtifactQueryContractTest.java`
- `progress/business-lifecycle-publication-tests.md`

## Exact RED findings

- Canonical Step07 publication still installs four files, producer `v1`, and v1 catalog/coverage/Markdown schemas; it has no `sources.md` payload or source-view artifact type.
- The renderer emits stage variants in parentheses, bare `S1`-style list entries, and no deterministic `sources.md#sN` links or scoped rule-use prose.
- Catalog JSON omits `stage.narrative` and `rule.activityUseIds`; coverage JSON omits the Activity `name`.
- The reader rejects the current payload during fresh reopen because the serialized rule has no required activity-use IDs; the new exact-five/both-Markdown reopen contract is therefore intentionally RED.
- The public business-artifact policy has no `sources.md` key.

## Exact next action

The focused publication/query assertions are complete and intentionally RED. The
parent implementation task may now make the production publisher/reader/renderer
and artifact policy GREEN without changing this test contract.
