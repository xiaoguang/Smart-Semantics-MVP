# Progress: fact-candidate-evidence-support-red

- Status: COMPLETE
- Agent role: Luna/xhigh RED test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Establish the M1 persisted Evidence support-kind closure RED.
- Approved inputs: Step 04 M1 contract, persisted public ProgramGraphs fixture, current Fact candidate reader seam.
- Current branch/worktree: codex/source-analysis-proven-code-facts

## Completed

- Added `FactCandidateEvidenceSupportKindTest` using the complete persisted public graph fixture.
- Mutates exactly one `CALL` graph edge's Evidence support kind and rebuilds the Evidence payload and
  graph index through the existing test-only canonical helpers.

## Current state

The test mutates one persisted Evidence edge whose subject is a program edge from
`SUPPORTS_PROGRAM_EDGE` to `SUPPORTS_PROGRAM_NODE`, rebuild the Evidence graph and graph index
through the public test helpers, then require fresh Fact input reopening to reject the mismatch.

## Changed files

- `progress/fact-candidate-evidence-support-red.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateEvidenceSupportKindTest test` | RED CONFIRMED | 1 test, 1 failure, 0 errors, 0 skipped; `Expecting code to raise a throwable` at the required reopen assertion |
| `git diff --check` | PASS | No whitespace errors |

## Decisions

- Use a complete canonical two-entry graph publication and mutate only one Evidence edge kind.
- Keep the test before enumeration: a support-kind mismatch is malformed persisted Evidence closure,
  so `PersistedFactCandidateInputReader.reopen` must fail with `PROOF_PACK_REFERENCE_BROKEN`.
- Do not infer or assert any external-system behavior.

## Blockers

- Production Fact input reopening currently accepts a `SUPPORTS_PROGRAM_NODE` edge whose subject
  is a real `CALL` program edge; this is the expected M1 RED for the next Terra fix.

## Exact next action

- RED is established. Parent agent may now assign the production GREEN fix; this task does not modify
  production code or re-run the test after that fix.

## Resume checks

- Confirm only this progress file and the new test are owned by this task.
- Confirm production, design, POM, and existing tests remain untouched.
