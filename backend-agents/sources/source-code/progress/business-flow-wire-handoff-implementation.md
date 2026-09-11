# Progress: business-flow wire handoff implementation

- Status: COMPLETE
- Agent role: Terra/xhigh wire-handoff implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Apply the published Step05 version-only M2/M3/public-reader wire cutover in exactly `CapsuleProjectionModulePublisher.java`, `FlowPublicationSpecifier.java`, `AtomicCanonicalPublicationEngine.java`, `RegistryProposalTaskCompiler.java`, and `FiniteKeyFlowTaskCompiler.java`. No shape, behavior, test, fixture, runtime, domain, or Step06 expansion.
- Approved inputs: Frozen coordinated four-class RED; published Step05 §§8.1.2, 8.4, and 8.6; current established M1 v3, M2 v6, public Flow v3, and public Capsule v4 wire contracts.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record from `progress/TEMPLATE.md` before production edits.
- Replaced only the published M2/M3/public-reader schema gates across the five owned production files, preserving M2 `MODULE_VERSION=v5`, M3 `MODULE_VERSION=v1`, all payload shapes, counts, identity preimages, and strict closures.
- Reconciled the invalid mid-run report read against final raw reports and root's no-process check; then ran a new four-class Maven session to numeric exit 0 after scoped formatting.

## Current state

- Root independently read the frozen coordinated RED: the four bounded classes total 14 tests with 3 failures and 11 errors. The failures all begin at writers/readers rejecting old M2/public schema versions under the matching new policy registrations.
- Maven is exclusively released to this slice. The implementation is restricted to matching published version gates and preserves all existing payload shapes, counts, identity preimages, coverage/disposition/gap semantics, strict checks, and module versions.
- Applied the sole cutover mapping: M2 schema/policy v6; M3 M1 v3 and M2 v6 input gates plus Flow v3/Capsule v4 output gates; and R0/R1-R2 public readers for Flow v3/Capsule v4. M2 `MODULE_VERSION=v5` and M3 `MODULE_VERSION=v1` remain unchanged.
- The final raw Surefire reports from the mapped four-class selector are all GREEN: M2 publisher 3/0/0/0, M3 public 4/0/0/0, R0 4/0/0/0, and R1/R2 3/0/0/0 (14 total). Root independently verified those reports and an escalated executable-name/PID-only check found no active Java, Maven, or JDB process.
- The earlier blocker report was retracted: this agent read report files while the initial selector invocation was still completing after the tool's 30.2-second yield. The original invocation's final numeric exit code was not captured because its result output was discarded before the tool's process metadata was read; that unknown exit code is not claimed. A later JDWP command exited 1 before tests because the sandbox denied socket binding and did not modify the final four reports.
- The required fresh post-format selector ran in an explicitly polled Maven session and completed with numeric exit 0: 14 tests, 0 failures, 0 errors, 0 skips. Its final raw reports were read only after that exit.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleProjectionModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/flow/publish/FlowPublicationSpecifier.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/proposal/RegistryProposalTaskCompiler.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/model/FiniteKeyFlowTaskCompiler.java`
- `progress/business-flow-wire-handoff-implementation.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Luna/root coordinated four-class selector | RED (confirmed) | 14 tests: 3 failures, 11 errors. Existing M2 writers reject the updated matching schema policies before downstream behavior. |
| First post-mapping four-class selector report read | Invalid mid-run observation (retracted) | This agent read stale/pre-final report contents before the original Maven invocation completed. It is not verification evidence. |
| Final raw reports from the post-mapping four-class selector | PASS (root verified) | M2 publisher 3/0/0/0 (6.959s); M3 public 4/0/0/0 (11.58s); R0 4/0/0/0 (13.31s); R1/R2 3/0/0/0 (9.065s); 14 total. The original command's numeric exit code was not retained. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest -Dmaven.surefire.debug test` | Environment diagnostic failed | Numeric exit 1 before tests because JDWP socket binding is denied (`Operation not permitted`); this did not modify the final four selector reports. No source/test state changed. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<five exact absolute owned Java paths> spotless:apply` | PASS (numeric exit 0) | Exactly 5 files selected; 0 changed, 5 already clean, 0 cache-skipped. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<five exact absolute owned Java paths> spotless:check` | PASS (numeric exit 0) | Exactly 5 files selected; 0 needed changes, 5 cache-skipped after apply. |
| Fresh `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleProjectionModulePublisherTest,BusinessFlowsPublicationSpecifierTest,RegistryProposalTaskCompilerTest,FiniteKeyFlowTaskCompilerTest test` | PASS (numeric exit 0) | Maven session `56619` was polled to final exit 0: 14 tests, 0 failures, 0 errors, 0 skips. Raw reports: M2 3/0/0/0 (6.877s), M3 4/0/0/0 (10.56s), R0 4/0/0/0 (13.69s), R1/R2 3/0/0/0 (9.497s). |
| Final scoped `git diff --check` | PASS (numeric exit 0) | No whitespace errors in the five production files or this progress file. |

## Decisions

- Use one published wire version at each seam: M2 capsule projection v6, M3 consumes M1 v3/M2 v6 and emits Flow v3/Capsule v4, and R0/R1-R2 read the public v3/v4 artifacts.
- Do not infer an extra producer module version or introduce a compatibility/dual-reader branch.

## Blockers

- None. The prior blocker was an invalid mid-run report read and has been retracted. Maven is released for the next coordinated slice.

## Exact next action

- Do not extend this completed version-only handoff. The next coordinated slice owns subsequent publication/public-reader work, not runtime or Step06 features.

## Resume checks

- Do not change tests, fixtures, schemas beyond listed version gates, domain classification, runtime, provider behavior, or Step06 features. Format exactly the five owned production files only after a positive selector.
