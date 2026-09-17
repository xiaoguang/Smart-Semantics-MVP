# Progress: Reading capacity GREEN

- Status: IN_PROGRESS
- Agent role: Terra/xhigh bounded GREEN owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: After parent-verified RED, make only the approved CHECK input-navigation projection correction in `DefaultBusinessProcessDiscovery.readingCheckInput`, in addition to the already handed-off capacity and CHECK-contract deltas.
- Owning plan: `docs/supplements/cross-object-process-reconstruction/module-design.md` section 3 and acceptance items 13–14
- Approved inputs: Frozen reviewed Activities, saved catalog, verified frozen source corpus, and existing deterministic scripted-provider tests; no live/model/source calls
- Current branch/worktree: Formal source-code checkout `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/source-code`, branch `codex/cross-object-process-reading`; shared dirty worktree preserved

## Completed

- Read root, prototype, backend-agent, and source-code scoped guidance plus the TDD workflow.
- Confirmed the design requires only lossless removal of synthetic `statements` and `statementHandles` from the full Activity projection, retaining canonical `readingPacket.statementDirectory`.
- Confirmed the intended schema boundary: named local `$defs`/`$ref` for CHECK and process DRAFT/REVIEW repeated exact allowlists, without altering global `PROCESS_MATERIAL_SELECTION` input, schema, prompts, producers, fingerprints, persistence, canonical IDs, parser contracts, or public versions.
- Confirmed the existing Luna RED work owns direct test edits and the parent owns the one focused Maven execution.
- Inspected the exact builders: `FrozenCorpus.activityJson`, `readingCheckSchema` plus its request variants, and `processSchema` plus the four process reference sites. The existing global-selection builders remain separate call paths.
- Parent supplied verified RED evidence (`session 46501`, exit 1, two expected test failures) and explicit GREEN authorization.
- Applied the authorized production-only correction in `DefaultBusinessProcessDiscovery.java`.
- Parent reported the first post-capacity focused run had 39 passing tests and one test-fixture file-key expectation mismatch; no production change is authorized for that assertion.
- Parent supplied new verified RED evidence: six direct tests, three expected failures, zero errors. The missing behavior is CHECK `activityUses.minItems`, the v2 CHECK prompt mapping, and the v2 resource.
- Applied the authorized CHECK lower bound, v2 prompt mapping/resource, and removal of the untracked unconsumed v1 resource.
- Read the approved CHECK navigation brief. Parent observed the direct RED: `BusinessProcessReadingPipelineTest#readingCheckOmitsCompleteActivityNavigationAndRetainsFullProcessPacket` has one expected failure and zero errors because CHECK still includes `statementDirectory`.
- Applied the approved `readingCheckInput` projection only.

## Current state

The GREEN patch removes only generated `statementHandles` and `statements` from `FrozenCorpus.activityJson`; full original Activity fields, field values, source refs, body, order and arrays remain. `readingPacket.statementDirectory` stays the unique canonical handle directory.

CHECK now owns fresh local `$defs` for its repeated global Activity ID and frozen file-key allowlists. Each Activity-ID use/context/disposition and each file-key request variant uses a fresh `$ref`. Each process schema gets fresh local statement/source allowlist definitions, with fresh `$ref` items in ActivityUse, stage, rule and knowledge arrays. Existing certainty enums remain inline. The existing empty-array `maxItems: 0` behavior is retained.

For an empty process statement/source allowlist, its local definition is now the original `textSchema` rather than `enum: []`; with the existing referenced-array `maxItems: 0`, expansion exactly preserves the prior `enumArraySchema(empty)` contract.

This follow-up must require at least one final CHECK `activityUses` item and make the CHECK v2 resource state the full-final-set/empty-context/null-inheritance/incremental-disposition contract. It must not change global selection, parsing, public versions, producers, retries, frozen source, or historical model records.

The approved navigation correction is confined to CHECK input: omit `readingPacket.statementDirectory`; omit navigation cards for already-complete `reviewedActivities`; remove only `terms` from remaining unread cards. The global-selection input, complete packet, final process DRAFT/REVIEW, response schema, prompt, producer, refs and source remapping remain unchanged.

## Changed files

- progress/reading-capacity-green.md
- src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java
- src/main/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPromptCatalog.java
- src/main/resources/org/sourceanalysis/app/analysis/knowledge/process-reading-check-v2.txt

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | inspected | Branch is `codex/cross-object-process-reading`; substantial pre-existing shared worktree changes preserved. |
| Scoped instruction and TDD reads | PASS | Production needs an intended-behavior RED first; daily verification must be targeted and serial. |
| Parent focused RED (`session 46501`) | PASS (expected RED) | Exit 1; two failures: synthetic Activity fields still present and missing CHECK `$ref`. |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java` | PASS | No whitespace errors. |
| Empty process allowlist inspection | PASS | `$defs.statementRef`/`sourceRef` use `textSchema` when empty; all four referencing arrays keep `maxItems: 0`, matching the prior expanded constraints. |
| Parent focused CHECK-contract RED | PASS (expected RED) | Six direct tests; three expected failures and zero errors for minItems, v2 mapping, and missing v2 resource. |
| `git diff --check` for the two Java production files | PASS | No whitespace errors. |
| CHECK v2 resource inventory | PASS | `process-reading-check-v2.txt` is present; the untracked, unconsumed v1 resource was removed. |
| Parent focused navigation RED | PASS (expected RED) | `readingCheckOmitsCompleteActivityNavigationAndRetainsFullProcessPacket`: one failure, zero errors; CHECK still contains `statementDirectory`. |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java` | PASS | No whitespace errors after the bounded navigation projection. |
| Maven/model/JDT/Builder/Activity/nine-chapter runs | NOT RUN | Explicitly prohibited for this preparation slice. |

## Decisions

- The minimal GREEN patch is limited to `DefaultBusinessProcessDiscovery.java` after verified RED permission.
- Keep every original full Activity field, source ref, array element, order, and body. Remove only generated duplicate `statements[{handle,text}]` and `statementHandles` fields from that Activity projection.
- Preserve the complete unique canonical `readingPacket.statementDirectory`; no aliases, local-ID codec, truncation, fee budget, extra call, new framework, or parser/store/public-version change.
- CHECK uses named definitions only for the repeated global Activity ID and frozen file-key allowlists. Process DRAFT/REVIEW use named definitions only for repeated statement/source allowlists. `enumArraySchema` empty-list behavior remains unchanged.
- Parent clarified that certainty stays its existing inline three-value enum; this bounded correction shares only the long allowlists and does not broaden the schema refactor.
- Each schema object must remain fresh per job; no mutable schema node sharing.
- The initial untracked v1 CHECK prompt has no approved consumer after this task; remove only that untracked resource while preserving all historical model records and other sources.
- CHECK navigation may omit only redundant material already present in its complete packet: selected Activity cards and the statement directory; unread cards retain every original navigation field except `terms`.
- `readingCheckInput` now removes only its CHECK packet statement directory, skips cards whose IDs are in the already-complete packet, and removes only `terms` from each remaining card. The separate material-selection projection remains unchanged.

## Blockers

- No blocker. The parent owns focused GREEN/CI. This task must not run Maven or alter the Luna-owned tests.

## Exact next action

Hand off the completed CHECK navigation GREEN to the parent and stop editing while the parent runs the approved direct selector and CI.

## Resume checks

Read this progress file, rerun `git status --short --branch`, inspect Luna’s completed direct test assertions and the exact affected schema/input builder methods, then require the parent’s verified RED message before a production edit.

## Plan closeout destinations

- Durable decisions: `docs/supplements/cross-object-process-reconstruction/module-design.md` section 3 and acceptance items 13–14
- Remaining issues: parent delivery record
- Verification and output references: parent delivery record with the serial focused Maven selector and result

Keep this handoff while the plan is active; this bounded implementation task does not authorize a product or model run.
