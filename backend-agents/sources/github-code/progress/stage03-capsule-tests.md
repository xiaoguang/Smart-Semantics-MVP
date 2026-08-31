# Progress: Stage 03 capsule isolation and evidence closure RED tests

- Status: COMPLETE
- Agent role: Stage03 capsule-isolation test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add one or two bounded public-seam RED tests for capsule-local gap and evidence closure behavior. Modify only Stage03 tests/fixtures and this progress file.
- Approved seam: `Stage03Generator.generate(Stage03Request, StructuredModelProvider)` plus public Stage01/Stage02/Stage03 records.

## Plan

- Added a standard-scenario proof/span closure test that checks each Provider task carries exactly its capsule proof pack, facts, required proofs, and evidence span identities/hashes/excerpts, without foreign capsule material.
- Attempted the two-entry public fixture, but both the shared-service and independent second-entry variants were conservatively returned by Stage02 with zero compiled Flows; the invalid precondition test was removed. Two-flow gap isolation is deferred until a public fixture yields at least one compiled Flow and a distinct capsule/entry.
- Ran only the direct Stage03 selector; no production code was changed.

## Changed files

- `progress/stage03-capsule-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage03/Stage03CapsuleTest.java`

## Verification

| `mvn -Dtest=Stage03CapsuleTest test` | GREEN | Main/test compilation succeeded; Surefire ran 1 test with 0 failures, 0 errors, and 0 skipped. Exact capsule proof pack, fact/atom proof IDs, outcome required proof IDs, and evidence span source/excerpt identities were preserved in each provider task. |

## Decisions / blockers

- No private hooks or result tampering were used. The attempted two-entry fixture produced two `FLOW_FACT_NOT_ADMITTED` dispositions and zero `FlowSlice` values, so retaining that test would have made fixture flow count—not cross-flow isolation—the failure. A valid two-flow isolation slice remains deferred.
