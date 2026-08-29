# Progress: runtime-receipt-tests

- Status: COMPLETE
- Agent role: model-runtime receipt TDD test author
- Model: gpt-5.6-luna (xhigh)
- Started: 2026-08-29
- Last updated: 2026-08-29
- Scope: Add only the Java/JSON RED tests and this progress record for the MVP model-runtime receipt admission seam.
- Approved inputs: Scoped AGENTS.md, DESIGN.md runtime-attempt contract, local recorded JSON only; no network, live model, provider, or customer build.
- Current branch/worktree: /Users/yexiaoguang/Documents/ErpMock on codex/rag-frontend-phase-one; target Maven module is shared and untracked.

## Completed

- Read the scoped AGENTS.md, progress template, existing MVP progress, README, DESIGN.md runtime sections, pom.xml, and current MVP source/tests.
- Confirmed the current public MVP API has no seam that admits a recorded model result against a frozen runtime policy.
- Added the RED test source and handed the exact four-type public API proposal to the parent production agent.

## Current state

The Java test contract is now written first. It requires an immutable frozen policy, an immutable recorded result receipt, and one admission result that is accepted only when provider/model/reasoning-effort/sandbox all match exactly. Three focused parameterized cases independently prove model, reasoning-effort, and sandbox drift are fatal; one exact-match case proves acceptance. The recorded task result remains opaque JSON data and no test invokes a provider.

## Changed files

- progress/runtime-receipt-tests.md
- src/test/java/com/linguan/codemd/mvp/ModelRuntimeReceiptAdmissionTest.java

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing unrelated root changes preserved; target files are shared in the untracked MVP directory. |
| `JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home mvn -o -Dtest=ModelRuntimeReceiptAdmissionTest test` | RED (expected) | Test compilation fails only because `ModelRuntimePolicy`, `ModelRuntimeReceipt`, `RuntimeAdmissionReceipt`, and `ModelRuntimeReceiptAdmission` are absent; no provider, network, or customer build was invoked. |

## Decisions

- Keep the public seam minimal: `ModelRuntimePolicy`, `ModelRuntimeReceipt`, `ModelRuntimeReceiptAdmission`, and `RuntimeAdmissionReceipt` in `com.linguan.codemd.mvp`.
- Model runtime identity is provider, model, reasoning effort, and sandbox; all are mandatory and exact-match fields.
- A recorded JSON result is opaque to this seam; admission checks only the observed runtime receipt against the frozen policy and never calls a real provider.

## Blockers

- None. The absent production API is the intentional RED handoff, not an execution blocker.

## Exact next action

Parent production agent should implement only the four proposed public types required by the test, then rerun the same targeted selector for GREEN.

## Resume checks

- Read this file and the scoped AGENTS.md before continuing.
- Run `git status --short` from the repository root.
- Confirm only this progress file and the runtime-receipt test/JSON fixtures are in scope.
