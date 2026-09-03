# Progress: Code structure graph Gap carrier RED

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: One M1 public-seam RED test for a source-located shared GraphGapDraft
- Approved inputs: M1 program-graphs design, shared GraphGapDraft contract, CodeStructureGraphBuilder public buildStructure seam, malformed Java fixture
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Read the scoped repository rules, M1 design, and current CodeStructureGraphBuilder seam.
- Selected the real malformed Java fixture `src/main/java/com/example/MalformedController.java`; it contains an unterminated return statement at lines 4–6 and is already handled as one explicit M1 candidate Gap.

## Current state

- Writing one bounded RED test only. A first selector attempt caught a test-only parenthesis typo; it was corrected before evaluating the intended RED. The expected production change is M1 draft schema v3 plus a shared five-field `GraphGapDraft` carrier.

## Changed files

- This progress file.
- `src/test/java/org/sourceanalysis/app/analysis/graph/CodeStructureGraphGapCarrierTest.java` (planned).

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CodeStructureGraphGapCarrierTest test` | RED | 1 test, 1 failure, 0 errors/skips. Expected failure: current draft is `program-graphs-code-structure-draft-v2`; the test requires v3 with the shared Gap carrier. |
| `git diff --check` (owned paths) | PASS | No whitespace errors. |

## Decisions

- The test uses the existing real malformed Java source and the public `buildStructure` method; it does not fabricate a synthetic gap value or depend on M2–M6.
- Reflection is used only to keep the intentional RED observable while the current v2 draft has no `gapDrafts()` accessor; the desired public property is asserted by name and shape.

## Blockers

- The first command attempt had a test-only unmatched parenthesis and was corrected before the RED run; it did not reach test execution.

## Exact next action

- No further action in this test slice. The production owner may implement the v3 carrier and rerun this selector.

## Resume checks

- Confirm only this progress file and the one new test changed.
- Confirmed: the failure is an assertion on the current v2 schema after the real malformed-Java fixture built successfully; no production code or existing test changed.
