# Progress: four-entry saved-material source readiness review

- Status: COMPLETE
- Agent role: bounded source-readiness reviewer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: inspect only the four `COLLECTED` Step05 `codeContext` records from the saved jshERP run and trace their minimum controller/service source evidence.
- Approved inputs: saved `flow-slices.json` under `.workspace/comparison-baselines/jdt-four-entries-20260913.FzzCl8/technical-run/steps/05-business-flows/`, plus frozen captured source files referenced by those contexts.
- Current branch/worktree: shared `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code` worktree

## Completed

- Read the nearest `AGENTS.md` and confirmed this is read-only observation: no compile, scan, Provider call, or customer build.
- Created this progress record before editing it with review findings.
- Read the saved Step05 `flow-slices.json` (`business-flows-flow-slices-v5`) and verified 326 entry contexts: 4 `COLLECTED` and 322 `NOT_COLLECTED`; every uncollected context has `collectionReason=NOT_SELECTED_FOR_SAMPLE`, `codeContext=null`, and `strictTechnicalContext=null`.
- Inspected only the four collected `codeContext` roots and their minimum named Controller/Service bodies from the frozen capture. The context sizes are 242/576 (batch status), 8/11 (financial lookup), 443/1468 (document creation), and 140/317 (registration) methods/calls; the larger contexts were not enumerated wholesale.

## Readiness findings

The following is a source-readiness review of already persisted technical material. It has not been sent to a model and does not claim that any endpoint ran successfully.

| Collected entry | Persisted source ranges read | Core statements the material supports | Conditions / effects that remain unconfirmed |
| --- | --- | --- | --- |
| `POST /user/registerUser` | `UserController.java:351-367`; `UserService.java:291-319, 607-672, 770-805`; `UserBusinessService.java:61-76` | Registration orchestration: set username, optionally validate and consume a CAPTCHA, reject duplicate login names, insert a normal user, set tenant linkage, create a `UserRole` relation, create a tenant with defaults, and return a success object when no exception escapes. | CAPTCHA platform flag/Redis state, duplicate-name and default-manager branches, mapper inserts/updates, transaction commit, tenant/role/log persistence, and actual account creation are unobserved. The source does not prove password hashing or a successful external side effect. |
| `GET /accountHead/getFinancialBillNoByBillId` | `AccountHeadController.java:175-196`; `AccountHeadService.java:442-444` | Given a bill ID, the controller delegates to a service that queries associated financial bill records and returns them as response data; caught exceptions map to code 500 and `获取数据失败`. | `AccountHeadMapperEx.getFinancialBillNoByBillId` is persisted as declaration-only (`AccountHeadMapperEx.java:44-45`), so record existence, query semantics, payment/receipt completion, and DB success cannot be asserted. |
| `POST /depotHead/addDepotHeadAndDetail` | `DepotHeadController.java:606-621`; `DepotHeadService.java:1204-1312`; direct guards `DepotHeadService.java:589-606, 1425-1476` | Creates a depot-head record and detail rows after duplicate-submit, bill-number, linked-order, settlement-account, multi-account amount, deposit, and attachment-count checks; applies initial status/pay defaults; handles prepaid advance checks; then saves details, updates debt/deposit, and logs. | Current user, Redis, mapper/detail writes, supplier advance, linked-order/deposit values, transaction commit, log delivery, and any resulting stock/business effect are unobserved. A source call to a downstream detail saver is not proof that persistence succeeded. |
| `POST /depotHead/batchSetStatus` | `DepotHeadController.java:172-191`; `DepotHeadService.java:741-822`; stock guard `DepotItemService.java:1501-1534` | Batch audit/reversal is guarded by current status and purchase status; configured force-audit paths may check ordinary/SKU/batch stock, update current stock, persist the status, and log the audit/reversal. | Runtime configuration flags, input record states, stock availability, mapper update count, transaction commit, current-stock mutation, and log delivery are unknown. The controller’s success JSON is conditional on the service returning a positive update count, not observed evidence of a completed run. |

## Current state

- Four-entry source readiness review is complete; findings are recorded above for the later bounded Luna business-output review.

## Changed files

- `progress/jdt-four-source-readiness-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `jq` inspection of saved `flow-slices.json` | PASS | 326 contexts; 4 collected, 322 not selected; all 322 uncollected contexts have null code/strict contexts. |
| Targeted persisted-context/source reads with `jq` and `sed` | PASS | Four collected roots and named Service bodies/source ranges reviewed; no runtime or model invocation. |

## Decisions

- Treat persisted Step05 material as evidence already produced by the technical run; do not claim it has been sent to a model.
- Keep the review to four collected entries and the named service methods; do not enumerate the repository-wide method set.

## Blockers

- None known.

## Exact next action

- Hand this table to the parent for the later Luna draft/review; keep the persisted technical artifact and source evidence as the boundary.

## Resume checks

- Do not call Maven, source discovery, a Provider, or any live scan.
- Preserve all unrelated worktree changes.
