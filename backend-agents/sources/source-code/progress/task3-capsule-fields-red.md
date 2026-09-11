# Progress: Task 3 Capsule wire reduction RED

- Status: COMPLETE
- Agent role: Luna/xhigh TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Add one public-seam RED for the Capsule wire reduction and legacy-version rejection.
- Approved inputs: Task 3 of the approved cleanup and scalable activity coverage plan; existing public graph fixture, publishers, stores, and readers.
- Current branch/worktree: shared Task 3 worktree; no commit or push by this agent

## Completed

- Read repository, backend, source-scoped instructions, TDD guidance, and the Task 3 design sections.
- Confirmed the current publisher still emits capsule-projection v8 and evidence-capsule v6 and serializes both retired registry proposal basis fields.

## Current state

- The public-seam RED is complete and ready for the Terra implementation.
- The test uses `ProgramGraphsPublicFixture`, the formal flow compiler, capsule projector/publisher, Step05 publisher/reader, and the public artifact policy lookup. It uses no reflection.

## Changed files

- `progress/task3-capsule-fields-red.md` (this file)
- `src/test/java/org/sourceanalysis/app/analysis/flow/capsule/CapsuleWireReductionTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=CapsuleWireReductionTest test` | RED (expected) | 1 test, 1 failure, 0 errors, 0 skipped; grouped failures show projection v8 instead of v9, evidence capsule v6 instead of v7, both retired fields still serialized in projected/public capsules, and both retired artifact policies still resolve. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The RED asserts the new v9/v7 schema versions, absence of both retired registry basis fields, preservation of flow/fact/gap/signal/context/source material, and rejection of old policy versions through the public artifact-store boundary.
- No reflection, production change, existing assertion change, live Provider, customer source, or customer Maven invocation.

## Blockers

- None.

## Exact next action

- Terra removes the two fields from the capsule model/projector/publisher and updates only the owning projection/evidence schema readers and policies from v8/v6 to v9/v7. The existing flow/fact/gap/signal/context/source payload remains intact.

## Resume checks

- Read this file first, then inspect only the new test and `git status --short`.
- Do not edit production, design, or another agent's progress file.
