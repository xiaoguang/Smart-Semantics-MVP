# Progress: Reading capacity RED

- Status: COMPLETE (RED tests prepared; parent runs the focused selector)
- Agent role: Luna/xhigh bounded RED-test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Direct tests for lossless fullActivity projection, canonical statementDirectory reuse, and private CHECK/process schema enum sharing
- Owning plan: docs/supplements/cross-object-process-reconstruction/
- Approved inputs: Frozen reviewed Activities, saved catalog, verified source corpus, and the existing deterministic scripted-provider fixtures only
- Current branch/worktree: Formal source-code checkout `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`, branch `codex/cross-object-process-reading`; unrelated changes preserved

## Completed

- Read repository, prototype, backend, and source-code instructions.
- Read the TDD skill and codebase-design skill, including the required good-test guidance.
- Ran `git status --short` before edits and confirmed unrelated worktree changes.
- Located the existing reading-pipeline tests and relevant fullActivity/readingPacket/schema implementations.
- Added two focused RED contracts to `BusinessProcessReadingPipelineTest`: lossless original Activity fields with no synthetic duplicate fields and valid canonical statementDirectory; private CHECK/process long-allowlist `$defs`/`$ref` reuse while preserving the material-selection wire.
- Adapted the three affected scripted providers in Pipeline, AcceptanceSample, and Discovery to look up statement refs from `readingPacket.statementDirectory` using exact `activityId/` prefixes.
- Removed the initially considered certainty `$ref` requirement per the approved scope; certainty enum structure remains asserted unchanged.
- Made the full-Activity field-name assertion order-independent after the focused RED exposed canonical object-key ordering as an unrelated test brittleness.
- Corrected the frozen source file-key expectation to the existing `F1`–`F4` selector contract; relative paths remain separate `path` metadata.
- Added the narrow reading-check non-empty `activityUses` schema RED assertion and strengthened the existing prompt contract to require explicit complete final `activityUses` and `contextActivityIds` wording.
- Updated the prompt resource-map RED expectation to the adjudicated `process-reading-check-v2.txt` resource.
- Added the approved finite CHECK-navigation RED test: no CHECK `statementDirectory`, no already-complete Activity cards, remaining unread navigation without only `terms`, and unchanged full selected Activity/process packet content.

## Current state

The RED slice now proves that every original fullActivity field survives exactly once while only synthetic `statements` and `statementHandles` are removed, that canonical `readingPacket.statementDirectory` handles remain valid, and that CHECK plus DRAFT/REVIEW private schemas share the approved repeated activity/file and statement/source allowlists through `$defs`/`$ref` without changing material selection or public IDs. It also requires finite CHECK navigation, non-empty final reading-check `activityUses`, nullable identity fields, empty context, disposition deltas, and explicit complete-final prompt wording. Certainty enums remain inline and unchanged.

## Changed files

- progress/reading-capacity-red.md
- `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessReadingPipelineTest.java`
- `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessAcceptanceSampleTest.java`
- `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessDiscoveryTest.java`
- `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPromptV2ContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Shared worktree already contains unrelated frontend/prototype changes and other agents' source-code work. |
| Scoped instruction/skill reads | PASS | Source ownership, frozen-input, TDD RED, and deep-module seam constraints loaded. |
| `git diff --check` (formal source-code repo) | PASS | No whitespace errors in the bounded test edits. |
| Targeted occurrence review | PASS | No scripted-provider handle lookup remains on removed per-Activity `statementHandles`; only the intentional RED assertions remain. |
| Focused RED run by parent | PASS (expected RED) | 2 tests, 2 failures, 0 errors: synthetic Activity fields and missing private schema `$ref`; field-order brittleness corrected afterward. |
| Focused GREEN feedback | APPLIED | Corrected only the test's file-key allowlist expectation from relative paths to `F1`–`F4`; production file-key behavior unchanged. |
| New targeted RED assertions | PREPARED | `activityUses.minItems=1` and prompt wording for complete final `activityUses`/`contextActivityIds`; no Maven run. |
| Finite CHECK navigation RED | PREPARED | New `readingCheckOmitsCompleteActivityNavigationAndRetainsFullProcessPacket` test; no Maven run. |

## Decisions

- Production code, schema builders, material-selection input/schema, public types, parsers, persistence, and fixtures remain untouched.
- Tests exercise the existing BusinessProcessDiscovery/reading-pipeline seam and use canonical `statementDirectory` lookup by exact activity prefix where the old synthetic per-Activity list was previously referenced.
- Only the approved long allowlists are required to move behind private `$defs`/`$ref`: CHECK activity IDs and frozen file keys; process statement refs and source refs. Certainty stays inline.
- No Maven or other heavy command will run in this RED slice; the parent will run the exact focused selector after all RED edits land.
- The existing nullable `name`/`purpose`/`scope` checks remain intact; no minimum was added to context IDs, and disposition-delta behavior remains covered by the existing pipeline fixture.
- The v2 prompt resource is intentionally absent until the production GREEN edit lands; this test change is expected to catch the stale v1 mapping.

## Blockers

- No blocker. The current RED should fail in `DefaultBusinessProcessDiscovery.FrozenCorpus.activityJson` for synthetic fields, then in `readingCheckSchema`/`processSchema` for missing `$ref` definitions after the first failure is addressed.

## Exact next action

Parent should run focused RED selectors for `BusinessProcessReadingPipelineTest#readingCheckOmitsCompleteActivityNavigationAndRetainsFullProcessPacket`, `BusinessProcessReadingPipelineTest#checksEachCandidateOnceAndPutsSupplementedXmlAndVueInBothRounds`, and `BusinessProcessPromptV2ContractTest#readingPromptsDescribeTheSingleCheckAndActualReadBoundary`, then proceed to Terra GREEN.

## Resume checks

Read this progress file, rerun `git status --short`, and inspect only the current reading-pipeline test and its direct helper/production interfaces before any further test edit.

## Plan closeout destinations

- Durable decisions: approved `docs/supplements/cross-object-process-reconstruction/module-design.md` and `acceptance.md`
- Remaining issues: parent delivery record
- Verification and output references: parent delivery record with focused test selector and RED output
