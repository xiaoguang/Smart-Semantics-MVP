# Progress: e2e-activity-reliability-tests

- Status: COMPLETE — Task 1 direct-test handoff is green on root's consolidated, fresh 79-test run and Spotless verification. Task 2 remains read-only until its separate write authorization.
- Agent role: Task 1 direct behavior-test owner (Terra/xhigh).
- Scope: Direct Task 1 tests, this progress record, and the bounded task-workspace report only. No production code/docs, model calls, source scans, commits, or pushes.
- Working branch: `codex/step05-activity-full-generation`.
- Test execution: Maven requires root's single-slot approval and the root-owned untracked `pom.e2e-local-verification.xml`; no Maven is currently running from this task.

## Direct-test changes

- `ActivityLargePacketFormalEntryTest`
  - Existing explicit retry preserves the stable `interpretationScope.sliceKey`, rather than guessing scope identity from method ordering.
  - Formal saved-plan corruption requires `ACTIVITY_READING_PLAN_REUSE_INVALID` and no Provider fallback.
  - Formal two-entry, three-slice capacity case requires S1/S3 public partial results, `ANALYZED_WITH_GAPS`/`ACTIVITY_READING_INCOMPLETE`, and no S2 Provider call. A bounded large packet enters reading, then only S2's two-entry scope exceeds `maxActivitiesPerMaterial=1` during the ordinary stage preflight; it avoids guessed byte thresholds.
  - New formal S1-success/S2-authentication/S3-not-started regression requires public and private retention of S1 while the affected binding queue stops.
  - A claimed complete oversized aggregate with a missing saved slice `REVIEW/success.json` requires `ACTIVITY_STAGE_SUCCESS_INVALID` and zero new Provider calls.
- `ActivityReadingCoordinatorGuardrailTest`: configured unknown-unit and duplicate-slice retry fixtures now have sufficient selection capacity and assert that invalid decisions are not persisted as success.
- `ProcessCodexSubscriptionCommandTest` and `ProcessCodexSubscriptionFreeTextFailureTest`: plain child-process stdout/stderr words (`rate limit`, model, capacity, quota, authentication, schema, context) must be `UNKNOWN`, non-retryable, and absent from public messages; raw diagnostic bytes remain private. No unofficial Codex error JSON is fabricated. Existing explicit timeout/termination and structured-failure coverage remains intact.
- `ActivitySliceAggregationTest`: removed an unused import left after moving the capacity behavior to the formal request boundary.

## Observed verification

| Command | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | passed (before latest test additions) | No whitespace errors. Re-run before handoff. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ProcessCodexSubscriptionFreeTextFailureTest test` | RED: 1 failure, 0 errors | First free-text `rate limit` case was classified `RATE_LIMITED`, not required `UNKNOWN`; report at `.workspace/end-to-end-business-delivery-20260923/maven-target/surefire-reports`. Later diagnostic cases did not execute after that first assertion failed. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityReadingCoordinatorGuardrailTest,ActivityLargePacketFormalEntryTest test` | 18 Guardrail passed; LargePacket 10 tests, 3 failures, 0 errors | Frozen snapshot was `ccd5a96e70304e79cce06eae521d92bb68ea6cf3` before and after. Failures exposed damaged-plan fallback, loss of S1 on later failure, and loss of S1/S3 around the original capacity fixture. This was a mid-repair diagnostic, not final GREEN. |
| Root-owned frozen nine-class direct run | 54 tests, 1 failure, 0 errors | Saved-plan corruption, missing slice REVIEW success, S1/auth/S3 binding-stop, reuse/retry, guardrails, and free-text `UNKNOWN` regressions passed. The sole failure was the new two-entry capacity fixture: S3 still declared the former second-entry M3 dependency, so reading failed before the intended S2 stage preflight. The fixture now uses `[M1,M4]`; it is unrun after that correction. |
| `mvn -f pom.e2e-local-verification.xml -t .mvn/toolchains.local.xml -Dtest=ActivityLargePacketFormalEntryTest test` | RED: 15 tests, 3 failures, 0 errors | All three final integrity regressions reached their assertions. A modified empty-plan `packetId`, a claimed nonempty plan with an unknown required unit, and a missing REVIEW success from an earlier completed slice each silently proceeded instead of hard-failing. |
| Root-owned consolidated Task 1 direct run | GREEN: 79 tests, 0 failures/errors | Fresh run after Sol's minimum integrity fixes. It includes the corrected S3 `[M1,M4]` capacity fixture and the final saved-artifact RED cases. |
| Root-owned Spotless verification | passed | Formatting passed after the final test and source changes. |

## Verified Task 1 behaviors

- Production must reopen and validate any claimed saved plan/packet/source mapping before reuse; damaged claimed results hard-fail rather than replan.
- A packet-local S2 failure must retain independent S1/S3 results publicly and privately while marking the packet incomplete. A shared authentication/configuration/quota failure halts later work on that binding but cannot erase a completed S1.
- The updated capacity fixture is deterministic rather than byte-tuned: two entry IDs share M1, then S1 uses `[M1,M2]`, S2 uses `[M1,M3]` with both entries, and S3 uses `[M1,M4]`. Only S2 exceeds `maxActivitiesPerMaterial=1`; it must prove S1/S3 execution and S2 zero invocation.
- Free-form process diagnostics remain `UNKNOWN`; reliable structured failures and confirmed-versus-uncertain process termination remain separately classified by their existing direct coverage.
- A valid empty saved plan with a modified or missing `packetId`, or a claimed plan whose required/shared-unit contract is inconsistent with its unchanged reading packet, must throw `ACTIVITY_READING_PLAN_REUSE_INVALID` before a Provider call.
- If S1 was fully reviewed while S2 failed without an aggregate, deleting S1's saved REVIEW success before explicit reuse must throw `ACTIVITY_STAGE_SUCCESS_INVALID` before a Provider call; it must not regenerate S1.

Task 1 testing is complete. Task 2 has a separate private preparation record and remains untouched in `src/test` until root grants its first vertical-test write permission.
