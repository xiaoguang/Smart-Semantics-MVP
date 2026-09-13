# Progress: four-package semantic review

- Status: COMPLETE
- Agent role: Bounded read-only semantic reviewer for the four-package sample
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Audit the four saved reviewed activities against supplied frozen-source snippets and the exact original material set. Registration is covered below; the follow-up covers creation, batch, finance, and the preview-driver safety boundary. No implementation, test, prompt, design, generated-output, Maven, or model-call work.
- Approved inputs: `.workspace/jsherp-jdt-luna-run.5Oqj9Y/inspection/generated/c72113fbf205b2d92d36aeea8b56dfcbcb3f563f1c7bb68489c42933fc501dc9-activity.json`; registration material in `stores/runs/analysis-run--92bdb80fc76b5050c45612994811bae5ea74eb4d09cf733b9436b738dfbb6f12/steps/06-flow-interpretation/modules/10-business-material-builder/business-materials.jsonl`; registration journals `request-ad8236bfb62455a77a6ee13b6cba26db502572cfff34d718845d96f1a7f428b0.json` and `request-09837af1b6f12df74d62ca82a7a9601a1ae471dacfe0b1cf00ebe2c645456053.json`; frozen snapshot blobs and configured Spring 5.0.4 `spring-core` for helper-semantics verification.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the scoped `AGENTS.md`; preserved all existing worktree changes.
- Confirmed the registration material has the expected `NAVIGATED_SOURCE` entry, one entry key, 126 allowlisted SourceRefs, and the complete UserController/UserService call-chain observations.
- Decoded the exact registration ACTIVITY_DRAFT and ACTIVITY_REVIEW journal inputs/outputs. The generated registration activity is the same reviewed content, with runtime-owned wrapper fields added.
- Audited every registration activity step, rule, result, condition, formula, limitation, question, participant, and trigger/input against the supplied source.
- Verified `UserService.checkLoginName` imports `org.springframework.util.StringUtils` and calls `StringUtils.isEmpty` at frozen `UserService.java` lines 7 and 776-805. Configured Spring 5.0.4 bytecode shows `isEmpty(Object)` checks only `null` or exact `""`, not trimming.
- Verified the frozen `User.setLoginName` setter trims at lines 54-60, so a raw HTTP/normal setter-bound whitespace-only login is normalized before the endpoint reaches duplicate checking.

## Current state

Registration content is largely source-faithful and appropriately conservative about persistence/runtime outcomes. No severe business-factual error was found. Findings are precision/quality backlog items:

1. `conditions` says “登录名为空或仅含空白时跳过重复检查.” As a statement about the direct `StringUtils.isEmpty` call this overstates behavior: whitespace is non-empty in Spring 5.0.4 and the check executes. The endpoint’s frozen `User.setLoginName(...trim())` normalization makes the statement effectively true for ordinary HTTP/setter-bound whitespace input. Recommended wording: “登录名经 `User` setter 规范化后为空时跳过；`checkLoginName` 本身只把 null/空字符串判为空.” Classify as conditional/precision issue, not an unqualified endpoint defect.
2. `triggerOrInput` lists “管理角色标识” alongside request data. `UserController.manageRoleId` is `@Value("${manage.roleId}")` configuration (frozen UserController lines 44-45), passed into the service at line 365; it is not supplied by the HTTP caller. Label it as configured role input/dependency.
3. The scope limitation “未提供用户、用户关联、用户更新和租户保存接口的实现” is overbroad. The supplied snippets include the service bodies for user-business insertion (`S450`) and tenant-ID update (`S454`), including their mapper calls. The missing boundary is the mapper/database implementation and schema/constraint evidence. Narrow the limitation to that boundary; the related “cannot confirm actual write result” conclusion remains sound.
4. The formula’s “空值片段处理” is interpretive. The source performs literal replacements of `[0]` and `[]`; it does not establish that role ID `0` is semantically an empty value. Describe the exact replacements unless the constant’s domain is supplied.

No other reviewed step/rule/result was found to contradict the supplied source. In particular, captcha gating/deletion and case-insensitive comparison, duplicate-count behavior, default manager rejection, user/tenant-ID assignment, role-array text normalization, null request/log branch, tenant defaults, and success-envelope qualification all match the snippets. The generated output is untouched.

## Changed files

- `progress/four-package-semantic-review.md` only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS (read-only) | Existing user/agent changes observed and preserved; no audit edits outside this progress file. |
| `jq` inspection of registration artifact/material | PASS | Activity ID maps to the registration entry; material contains the full source-ref packet and two material records (material plus coverage). |
| Decode `request-ad8236...` and `request-09837...` journal base64 fields | PASS | Registration DRAFT and REVIEW envelopes are complete; review output matches generated reviewed activity content. |
| `rg`/`nl` over frozen snapshot blobs | PASS | `UserService` uses Spring `StringUtils` at lines 7/783; `User.setLoginName` trims at lines 58-60; `UserController.manageRoleId` is injected config at lines 44-45. |
| `javap -classpath spring-core-5.0.4.RELEASE.jar -c -p org.springframework.util.StringUtils` | PASS | `isEmpty(Object)` branches only on null or exact empty string; no trim. |

## Decisions

- Report the whitespace condition as a conditional precision issue because direct helper semantics and endpoint setter normalization differ.
- Treat role-ID labeling, missing-boundary wording, and `[0]`/`[]` characterization as conservative/verbose quality issues, not runtime failures.
- Do not run Maven/tests, call a model, retry a journal request, or alter any generated artifact.

## Blockers

- None for the bounded registration audit.

## Exact next action

- Parent agent should incorporate the registration findings and the bounded follow-up below into the cross-package quality report.

## Follow-up: creation, batch, finance, and preview safety

- Inputs were the original `analysis-run:92bdb80...` BusinessMaterialBuilder JSONL and the saved reviewed activities for creation (`3959e96f...`), batch (`6ffe31a1...`), finance (`ec3aa529...`), and registration (`c72113fb...`). Creation has 59 SourceRefs (58 unique because `S390` is repeated), batch has 26/26 unique refs, and finance has 3/3 unique refs; every referenced ref resolves in its material and every selected activity is bound to its configured entry/material. No severe source-factual error was found in creation, batch, or finance.

### Nonfatal wording backlog

1. Creation says the current user is obtained “from the request-associated session.” `S361` shows `UserService.getCurrentUser()` using `RequestContextHolder`; the explicit request is passed later to detail/log paths. Describe this as request-context/session-backed lookup rather than direct request input.
2. Creation calls duplicate-log cleanup a “session-cache user identifier.” `S179`/`S183` show a Redis hash scan that deletes the `userId` field when user/module/IP/time match; “matching Redis user marker” is more precise than a generic session-cache claim.
3. Creation’s result “异常分支会中断当前业务路径” is broader than the material supports because several read/write helpers catch and delegate failures (`S365`, `S366`, `S382`, `S392`). Keep the explicit business exceptions, but qualify the result as “uncaught/propagated exceptions interrupt the path; helper failure handling is not fully expanded.”
4. Batch’s first step calls the parsed input IDs a “待处理编号列表”; `S165` first parses all IDs and only then filters into `dhIds`. Rename that step to “解析输入编号列表并按状态筛选待处理单据.” Its later status, stock, formula, update, logging, and response claims match `S4`, `S165`–`S212`.
5. Batch says log errors “抛出代码定义的数据写入异常.” `S179` catches the exception and calls `JshException.writeFail`; the helper’s exact outward behavior is not in the material. Report this as delegated error handling, not a guaranteed concrete exception unless that helper is supplied.
6. Finance is source-faithful for the controller envelope (`S217`) and its explicit service call (`S218`/`S219`). The empty participant field is a presentation omission (the HTTP caller/system are implicit), not a business-factual defect; the service’s query semantics remain correctly listed as unavailable.

### Preview-driver bounded safety review

- The input selects the original materials JSONL, the exact four saved activity files, the configured four entry IDs, source run `FAILED`, and formal run `RUNNING`; the preview destination was absent before launch. The driver’s source has no `ActivityExplainer` or JDT scan, reads both run states without lifecycle mutation, rejects an existing output destination, validates exact four samples, material/entry/coverage bindings, all activity SourceRefs, and the 326-entry denominator (322 `NOT_ANALYZED`).
- The dry-run was independently executed by the parent and passed with `FOUR_PACKAGE_PREVIEW_DRY_RUN_READY`, 4 reviewed activities, 326 coverage entries, 322 `NOT_ANALYZED`, 30 longest saved activity steps, 457 unique source refs, source run `FAILED`, formal run `RUNNING`, and `providerCalls=0`. Original activity/material/journal backups were reported byte-unchanged.
- Static review of preview mode shows only the new preview directory is written; knowledge is saved before report publication, the checkpoint is removed from preview knowledge, and the handoff/manifest retain `previewOnly=true`, formal `RUNNING`, and “not a module receipt/formal completion.” Process/report calls use the existing journal-backed `codex_subscription` / `gpt-5.6-luna` / `high` / read-only runtime identity. GO for the already-authorized process/report preview, subject to the parent’s live-output and journal-diff checks; no further activity calls or history rewrites are indicated.

## Final bounded report-draft audit

- Exact completed `BUSINESS_REPORT_DRAFT` journal: `models/journal/request-fc0114da82f7fcd57dbf9b3140aed589ec82121fc3e6356974f4d70c2fcdca2a.json`, status `COMPLETED`, with a nine-section report. The corresponding report `REVIEW` request remains `STARTED` after the provider failed after start; therefore this is an unreviewed draft, not a reviewed product output.
- The draft contains 61 unique SourceRefs, all of which exist in the original 457-reference material set. It covers the four saved activities and preserves the analyzed/not-analyzed boundary. The reviewed knowledge has 4 processes, 4 activities, 326 coverage rows (`4 ANALYZED_WITH_GAPS`, `322 NOT_ANALYZED`), zero unexplained activity entries, zero unmatched activity IDs, and no unbound activity/material/source-ref relationship found.
- No severe unsupported business outcome was found in the draft’s four activity summaries. Its claims about save/update behavior, response envelopes, status/configuration branches, and query limitations are conservative and consistent with the saved activities/material; it does not claim a successful runtime or final persistence.

### Concrete draft traceability defect

- Section 7’s formulas are mostly cited to unrelated refs even though the formulas themselves are supported elsewhere in the material: inventory/SKU formulas cite `S179` (log), `S188`–`S191` (configuration getters) instead of `S167`/`S169`; moving-average cost cites `S188`–`S191` instead of `S367`; debt/deposit cites `S182`/`S183`/`S390` (material/Redis/serial) instead of `S359`/`S363`/`S364`; and batch response cites `S193`/`S194` (unit lookup/conversion) instead of `S4`/`S203`/`S204`. Section 8 repeats some of these weak mappings for its confirmation questions. This is a material citation/traceability quality defect for the unreviewed draft, not evidence that the formula text itself is false.
- Additional lower-severity citation imprecision appears in Section 6: the paragraph claiming order/payment/deposit/debt linkage cites warehouse/material/Redis/serial refs (`S176`–`S183`, `S385`, `S390`) rather than the direct creation refs (`S361`, `S363`, `S364`). The wording remains conservative, but the citation set should be corrected before treating the draft as final.
- No retry or report-review success was inferred. The parent should label any rendered artifact as an unreviewed draft/baseline and retain the provider failure blocker alongside this citation finding.

## Resume checks

- If resumed, re-read this file, re-check `git status --short`, and verify the same runroot/artifact paths before any further read-only inspection.
