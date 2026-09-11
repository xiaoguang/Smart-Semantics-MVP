# Progress: flow-signal-integrity-tests

- Status: IN_PROGRESS
- Agent role: Luna/xhigh bounded TDD regression preparation and test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Prepare and, after the root release gate, add one public flow-signal publication-integrity regression test.
- Approved inputs: Frozen two-entry guarded program-graph fixture, current Fact/Proof/public ledger APIs, Step 05 sections 8.1.1 and 8.6; no Provider, customer Maven, network, production, helper, schema, or design changes.
- Current branch/worktree: `codex/source-analysis-process-materials` at `12005e8`; shared worktree has unrelated/pre-existing edits.

## Completed

- Read the repository root, backend, source-code, and worktree-ancestor `AGENTS.md` instructions.
- Read the required TDD skill and Step 05 §8.1.1/§8.6 signal and publication contracts.
- Confirmed prepare-only gate: no Java test created and no Maven run.
- Root released the gate with the required no-baseline-first sequence.
- Added the single direct-public-constructor regression test and kept the valid M1 unpublished until all three bad attempts were rejected.
- The test constructs all three independent mutations before invoking the publisher: foreign other-Flow basis, omitted existing signal, and altered first-call anchor. Changed records use a null public ID and assert canonical ID changes.
- The three bad publishes fail before module installation with exact `FLOW_ACCOUNTING_INVARIANT_BROKEN`; the unchanged original then installs at the expected M1 address and reopens with full signal/outcome/fact/profile/ledger checks.

## Current state

- Existing `FlowCompilationModulePublisherTest` patterns and public flow/compiler records are being inspected for a direct-constructor, real-artifact regression.
- Planned single `@Test`: construct an unchanged two-entry compilation and three independent mutated signal sets (foreign Flow basis, omitted required signal, altered call anchor), assert canonical IDs recompute and each bad publication fails before any M1 install with `FLOW_ACCOUNTING_INVARIANT_BROKEN`, then publish the original at the same address and reopen canonical payload/full signals.

## Changed files

- `progress/flow-signal-integrity-tests.md` (owned; this file)
- `src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowSignalPublicationIntegrityTest.java` (owned; single new test file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS (read-only) | Worktree contains unrelated/pre-existing changes; only this task's progress/test paths are newly owned. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowSignalPublicationIntegrityTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowSignalPublicationIntegrityTest.java spotless:apply` | PASS | Spotless selected exactly 1 file; 1 changed to clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowSignalPublicationIntegrityTest.java spotless:check` | PASS | Spotless selected exactly 1 file; 0 needing changes. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FlowSignalPublicationIntegrityTest test` (post-format) | PASS | 1 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| `rg '^\\s*@Test\\b' src/test/java/org/sourceanalysis/app/analysis/flow/compiler/FlowSignalPublicationIntegrityTest.java` | PASS | Exactly one `@Test`. |
| owned whitespace check (`git diff --check` + untracked-file check) | PASS | No whitespace errors in owned paths. |

## Decisions

- Use current v2/M1 public contracts and `ProcessJoinSignalV1` public constructors; do not forge IDs, use reflection/private builders, mocks, or legacy POC types.
- Keep `FlowId` independent of signals; rely on public constructor canonical-ID recomputation and assert changed IDs for each changed record.
- Delay all test creation and Maven/Spotless verification until the root agent explicitly releases the gate.

## Blockers

- Bad candidates are all fully constructed before publisher assertions; no bad attempt can install M1 before semantic replay.
- The original compilation is published only after all three exact `FLOW_ACCOUNTING_INVARIANT_BROKEN` assertions, then reopened and checked for full signal payload preservation.

## Exact next action

- Report `COMPILE_READY` to root with the exact GREEN/Spotless results and release Maven ownership; do not commit or push.

## Resume checks

- Re-read this file, run `git status --short`, confirm root release, and verify no other agent owns the target test path before editing.
