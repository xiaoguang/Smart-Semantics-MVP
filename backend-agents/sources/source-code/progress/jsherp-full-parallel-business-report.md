# Progress: jshERP full parallel business report

- Status: COMPLETE
- Agent role: Primary run coordinator
- Model: Product interpretation uses configured gpt-5.6-luna / high; coordination uses current root agent
- Started: 2026-09-13
- Last updated: 2026-09-14
- Scope: Execute the complete fixed jshERP commit through the persisted JDT material checkpoint, parallel Activity and Process jobs, one repository summary, and one reviewed nine-section Markdown. Preserve all technical and model artifacts for later comparison. Do not run the customer build/application. A minimal report-REVIEW transport fix became necessary after the real request exceeded the Codex hard character limit.
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
- Completed an independent Luna/high DRAFT plus full REVIEW for `POST /user/registerUser` from the selected 326-material checkpoint. Model batch `analysis-run:75e18f7383aa80f3d911fd172ef36f1b61e8f309312d005afe16637a63c2c776` finished successfully with one reviewed Activity, zero unexplained entries, and source-backed rules covering configurable captcha validation, duplicate-login checks, user defaults, persistence calls, tenant creation, and role association.
- Started full model batch `analysis-run:f239dc833cbcec76e3a9010e31db5c050e0d3342ef587b5b8bef8395a9289205` with four-way Luna/high concurrency and explicit reuse of the registration sample. It durably completed 19 Activity DRAFT+REVIEW jobs, then stopped as designed when two in-flight Activity DRAFT requests reached the configured 600-second Codex timeout. No downstream Process or Report task started and no JDT/Builder work ran.
- Started follow-up batch `analysis-run:f2b4365be5ad99a06f72a7bf8afe8d7a5d79038d7982858e965c05590167c143` with a 1800-second request timeout and byte-identical reuse of the prior 19 reviewed results. It durably reached 150/326 complete reviewed Activities before one different, small Activity DRAFT remained stuck until the 1800-second boundary. The stopped request was not one of the two earlier timeout tasks; no downstream task started.
- Completed all 326 Activity DRAFT+REVIEW jobs and preserved them as a reusable semantic checkpoint. No entry is unexplained; all 326 are honestly classified `ANALYZED_WITH_GAPS` because static material cannot prove runtime effects.
- Completed all 15 Process-group DRAFT+REVIEW jobs, producing 340 reviewed business processes, zero unmatched activities and zero unconsolidated processes. Transient long-ID response errors were retried only through explicit new model batches; completed work was reused.
- Completed the unique repository-summary DRAFT+REVIEW and saved the reviewed repository knowledge.
- Diagnosed the final report REVIEW failure with the retained raw Codex error: 1,049,183 input characters exceeded the 1,048,576-character hard limit. The repeated `activities` field alone occupied about 713,307 characters.
- Added a focused RED/GREEN change: report DRAFT still receives all activities, while report REVIEW receives the complete actual draft, processes, repository summary, coverage, unexplained range, confirmation topics and source allowlist without resending all activities. The real REVIEW input fell from about 2.28 MB to about 673 KB.
- Finished model batch `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e`. It published one reviewed nine-section Markdown, 2,102 independent source references and a `VALID` report receipt.

## Current state

- The original 326-material checkpoint remains durable under run `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`, together with its four failed `STARTED` model jobs.
- A second independent technical checkpoint is durable under run `analysis-run:6f4fe6eba49e2666b58880453816ad0eeb77911b009ab0a0409f94103a49db69`, with 325 materials and one explicit not-materialized entry.
- The fixed-material/model-batch separation is implemented on `main` at `c39fc3d7681c96f2f4fb710ca1993b3e0f3772de`. The original 326-material checkpoint has been explicitly exported to `repository-run-state-v3` and is the selected source for the authorized model batch; the 325-material comparison run is not selected.
- The user-authorized Luna/high generation is complete. Final model batch `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e` is `FINISHED`; the selected material source remains `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`.
- The final knowledge contains 326 reviewed activities, 340 reviewed processes, zero unexplained activities, zero unmatched activities and zero unconsolidated processes. The document has exactly nine H2 chapters and all 77 cited short references resolve in the 2,102-line `source-refs.jsonl`.

## Changed files

- `progress/jsherp-full-parallel-business-report.md`
- `src/main/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisher.java`
- `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisherTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/PersistedBusinessRunExecutorTest.java`
- `src/test/java/org/sourceanalysis/app/runtime/FourEntryBusinessSemanticChainTest.java`
- `docs/DESIGN.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/modules/model-job-execution.md`
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
| focused report RED | PASS | `BusinessReportPublisherTest` failed because REVIEW still contained `activities` |
| focused report GREEN and direct regression set | PASS | 12 tests, 0 failures |
| final `RepositoryRunMain --mode generate` | PASS | batch `6b510bbe...` FINISHED; 326 activities, 340 processes, one reviewed repository summary and one report |
| final report/source inspection | PASS | exactly 9 H2 sections; validation `VALID`; 77 cited refs, zero missing from 2,102 source records |
| `mvn -t .mvn/toolchains.xml -Pquality spotless:check verify` | PASS | 501 tests, 0 failures, 0 errors, 2 configured skips; SpotBugs 0; PMD pass; 7m58s. Loopback mock tests required the host execution context because the restricted sandbox forbids local socket binding. |

## Decisions

- Run all discovered entries (`selectedEntryIds: []`).
- Preserve evidence and model journals in a new run directory; never overwrite the four-entry baseline.
- Use one Codex Subscription Provider with global and Provider concurrency 4, Luna/high, no retries and no fallback.

## Blockers

- No execution blocker remains. Historical FAILED/STARTED requests are preserved in their original journals and are not treated as completed results.
- The inconsistent JDT outcome belongs only to the unselected 325-material comparison run and does not affect the complete 326-material checkpoint used by the final report.

## Exact next action

- Review the generated report with the user. Any future business-language improvement is a separate quality finding; do not rerun JDT or overwrite this comparison baseline.

## Resume checks

- Confirm both ignored configurations and state files agree, then inspect model journals before starting anything. A previous `STARTED` record must remain immutable; a user-authorized re-execution must receive a new model-batch identity while reusing validated material bytes.
