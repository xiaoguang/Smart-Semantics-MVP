# Progress: jshERP full parallel business report

- Status: IN_PROGRESS
- Agent role: Primary run coordinator
- Model: Product interpretation uses configured gpt-5.6-luna / high; coordination uses current root agent
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Execute the complete fixed jshERP commit through the persisted JDT material checkpoint, parallel Activity and Process jobs, one repository summary, and one reviewed nine-section Markdown. Preserve all technical and model artifacts for later comparison. Do not modify production code or run the customer build/application.
- Approved inputs: User approval in the current task; fixed commit `8c30ce7861570458920175e200bb2a6442713580`; existing ChatGPT-authenticated Codex context; global/Pro concurrency 4.
- Current branch/worktree: `main` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Verified formal checkout and `origin/main` both resolve to `df5e8d4edc66549774c9d1817fa870c31245d801`; worktree was clean before this progress file.
- Verified the fixed source commit resolves from the approved local object database, has zero missing objects, and the prior measured tree contains 719 files.
- Verified JDT LS, its tool JDK, Java 17 runtime classes/classpath, and ChatGPT Codex login are present.
- Verified current account usage permits ordinary Codex requests; no API Provider or API-key fallback is configured.
- Completed the current-production full JDT materials run with 8 GiB heap and exit code 0. It discovered and navigated all 326 entries, persisted an 85 MiB shared navigation index, and built 326 navigated business materials plus 326 coverage records.
- Measured persisted model packets before any Provider call: minimum 4,696 characters, maximum 449,473, average 54,645; every material owns exactly one entry and all 326 are explicitly `MATERIAL_WITH_GAPS / ENTRY_SOURCE_FALLBACK` because the JDT route does not manufacture strict Flow/Proof enrichment.
- Preserved the first model attempt after four Activity DRAFT jobs reached `STARTED` and failed with `ACTIVITY_PROVIDER_FAILED_AFTER_START`; no request was replayed and no later phase began.
- Confirmed a source-free Luna/high request succeeds outside the restricted sandbox, isolating the first attempt to the Java-to-Codex host boundary rather than source material or account availability.
- Completed a fresh host-boundary preparation run with exit code 0. JDT traversed all 326 entry seeds, published a 61,778-record / 88,670,739-byte navigation index, and persisted 325 business materials plus all 326 coverage records.
- Recorded the single missing material exactly: `entry:1318576cb7a3cd24f419b8990b5783fa3d598857e2bcc982ce0a0244150e33c2`, `MaterialExtendController#getList`, `GET /materialsExtend/info`, with `JDT_QUERY_FAILED` after the 30-second definition boundary. The same entry succeeded in the preceding full run and requires an isolated JDT comparison.

## Current state

- The original 326-material checkpoint remains durable under run `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`, together with its four failed `STARTED` model jobs.
- A second independent technical checkpoint is durable under run `analysis-run:6f4fe6eba49e2666b58880453816ad0eeb77911b009ab0a0409f94103a49db69`, with 325 materials and one explicit not-materialized entry.
- Per the user's latest direction, no Luna generation is started from the second checkpoint until the model-batch reuse mechanism is discussed. The desired behavior is to reuse one validated technical/material checkpoint across separately identified model execution batches rather than rerun JDT after a Provider failure.

## Changed files

- `progress/jsherp-full-parallel-business-report.md`
- Ignored run artifacts under `.workspace/jsherp-full-parallel-20260913/`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short && git rev-parse HEAD && git rev-parse origin/main` | PASS | Clean start; both refs `df5e8d4...` |
| `git cat-file` / missing-object traversal | PASS | fixed commit present; zero missing objects |
| `codex login status` | PASS | Logged in using ChatGPT |
| runtime/JDT/classpath presence checks | PASS | all required local tools present |
| `RepositoryRunMain --mode materials-only` | PASS | 326/326 JDT entries; 326 materials; exit 0; about 27 minutes |
| persisted material inspection | PASS | 326 materials + 326 coverage; full packet/source-reference records retained |
| source-free host Luna/high health check | PASS | exit 0; exact `READY` response |
| fresh `RepositoryRunMain --mode materials-only` | PASS WITH ONE EXPLICIT GAP | 326 seeds traversed; 325 materials + 326 coverage; exit 0 |
| fresh persisted checkpoint inspection | PASS | one `NOT_MATERIALIZED` entry; all other 325 entries `MATERIAL_WITH_GAPS` |

## Decisions

- Run all discovered entries (`selectedEntryIds: []`).
- Preserve evidence and model journals in a new run directory; never overwrite the four-entry baseline.
- Use one Codex Subscription Provider with global and Provider concurrency 4, Luna/high, no retries and no fallback.

## Blockers

- The current run coordinator binds technical checkpoint and model journal state too tightly. A failed Provider batch cannot create a new auditable model execution over the same validated materials, so the available CLI forces unnecessary JDT recomputation.
- One entry has an inconsistent JDT outcome across the two full runs and needs an isolated comparison before claiming the second checkpoint covers all discovered entries.

## Exact next action

- Review the minimal checkpoint/model-batch separation with the user before any further Luna call. Then isolate the one inconsistent JDT entry and choose the exact complete checkpoint for the authorized model batch.

## Resume checks

- Confirm both ignored configurations and state files agree, then inspect model journals before starting anything. A previous `STARTED` record must remain immutable; a user-authorized re-execution must receive a new model-batch identity while reusing validated material bytes.
