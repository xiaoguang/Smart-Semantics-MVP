# Progress: JDT LS source-navigation feasibility verdict

- Status: COMPLETE — STOPPED_FOR_USER_REVIEW
- Agent role: Task 6 independent feasibility reviewer
- Model: Sol/ultra-or-Astra/ultra reviewer tier; exact session model is not recorded in local evidence
- Started: 2026-09-12T10:33:39-02:30
- Last updated: 2026-09-12T10:47:52-02:30
- Scope: Independently review the frozen JDT LS trial evidence, write the sole formal Chinese `REPORT.md`, and add README navigation if needed. Do not start JDT LS, change the harness/code/tests/plan, invoke a model, integrate production, merge, commit, or evaluate another tool.
- Approved inputs: jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; JDT LS `1.61.0`; active corrected runtime/results; preserved Task 3 Buildship-failure runtime/results; independent oracles/checker; the historical registration material and Luna report named by the coordinator.
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the repository, backend, and source-code instructions; the complete feasibility plan and Task 6 brief; Task 1, Task 2, and Tasks 2–5 reports; all tracked harness, test, checker, oracle, README, and relevant progress files.
- Inspected the active corrected runtime/results and the preserved Task 3 failed runtime/results, including request journals, logs, diagnostics, packets, verdicts, manifests, and SHA-256 identities.
- Independently inspected the pinned JDT LS bytecode path that selects flat `SymbolInformation` for a client without hierarchical document-symbol capability and maps its location through `JDTUtils.toLocation(IJavaElement)` / `NAME_RANGE`.
- Verified the intended historical material path after correcting the coordinator's transcribed run ID; read the exact `/user/registerUser` material record and the complete historical Luna report.
- Wrote the sole formal Chinese feasibility deliverable, explicitly separating measured machine artifacts from oracle expectations, historical comparison, and intended downstream use.
- Updated the research README navigation and superseded its pre-correction runtime summary without changing harness, tests, plan, or ignored machine evidence.

## Current state

- The measured verdict is `FAIL` with reason class `JDT_NAVIGATION_CAPABILITY` and detail `DOCUMENT_SYMBOL_FULL_RANGE_UNAVAILABLE_UNDER_PRESCRIBED_EMPTY_CAPABILITIES`.
- The active registration packet has SHA-256 `3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424`, zero methods, zero calls, and `FULL_BODY_NOT_FOUND`; financial is `NOT_ENTERED` and no financial packet exists.
- The one allowed isolated Buildship-cache correction removed the prior forbidden metadata-network attempt from the corrected run. The preserved earlier run remains available for comparison.
- The active tool manifest has a stale `lastTrial.forbiddenActivity` value copied from the earlier run; active verdict/logs contradict it. The formal report discloses this evidence defect and does not treat the stale field as corrected-run truth.
- Request journals store request/notification parameters only, not raw server response bodies. The report does not claim an archived `documentSymbol` response.
- The formal report is complete and recommends no adoption of the current constrained JDT LS design. Work is stopped for joint user review; no production integration, main merge, product LLM run, nine-chapter sample, or second tool has been started.

## Changed files

- `backend-agents/sources/source-code/progress/jdtls-verdict.md`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/REPORT.md`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/README.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short && git branch --show-current` | PASS | Existing Task 1–5 work preserved; branch is `codex/jdtls-source-navigation-feasibility`. |
| Active packet/verdict inspection and SHA-256 checks | PASS | Packet digest `3c11aa20…`; methods/calls `0/0`; financial `NOT_ENTERED`. |
| Active/preserved request-journal inspection | PASS | Correct initialization settings and one `documentSymbol` request are recorded; no response payload field exists. |
| Pinned JDT LS `javap` inspection | PASS | Empty capabilities cannot advertise hierarchical support; flat outline uses `toLocation`, whose default is `NAME_RANGE`. |
| Historical material/report inspection | PASS | Corrected material run exists at `analysis-run--76cef36f…`; registration record is line 151; old report exists and records the Service rules/persistence as unknown. |
| Corrected packet checksum plus packet/verdict `jq` assertions | PASS | `packet.json: OK`; exact commit, empty methods/calls, `FULL_BODY_NOT_FOUND`, verdict reason, and financial `NOT_ENTERED` all match. |
| Financial/structure output counts and active/preserved comparison | PASS | Both unentered output directories contain zero files; active and preserved registration packets are byte-identical while diagnostics distinguish their environments. |
| Embedded/seeded Buildship-cache hash and manifest-conflict checks | PASS | Both cache sources hash to `c0d581d3…`; stale manifest fields were reproduced and disclosed rather than treated as current. |
| Report/README local-link and placeholder checks | PASS | Every local Markdown target exists; no draft placeholder or transcribed `76cef9…` path remains. |
| `git diff --check`, `git diff --cached --check`, and untracked-document whitespace checks | PASS | No whitespace errors after final document edits. |

## Decisions

- Treat the active verdict, packet, diagnostics, runtime logs, and request journal as the corrected-run evidence set; disclose the manifest's stale `lastTrial` conflict rather than silently reconciling it.
- Separate machine-collected packet facts from oracle expectations, historical comparison, and intended downstream use.
- Produce no synthetic method excerpt, second packet, business interpretation, or nine-chapter sample.

## Blockers

- None. The initial historical material path contained a transcribed run-ID error; the coordinator confirmed the exact existing `analysis-run--76cef36f…` path before use.

## Exact next action

- Stop here and review `research/jdtls-source-navigation-feasibility/REPORT.md` and the actual registration packet with the user. Make no production integration, merge, model call, new JDT run, or alternate-tool evaluation unless the user separately approves it after review.

## Resume checks

- Re-read this file, verify branch/status and all cited hashes, confirm no JDT LS process is started, and preserve all other agents' changes.
