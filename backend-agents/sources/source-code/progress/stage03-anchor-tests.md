# Progress: Stage 03 proven-anchor RED test

- Status: COMPLETE
- Agent role: Stage03 proven-anchor test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Scope: Add one bounded public-seam RED test for proven Stage01/Stage02 anchor provenance. Modify only Stage03 tests and this progress file.
- Approved seam: `Stage03Generator.generate(Stage03Request, StructuredModelProvider)` plus public Stage01 flow-view, Stage02 Flow/Fact/Outcome, and Stage03 result records.

## Plan

- Compile the standard synthetic source through Stage01 and Stage02, then generate Stage03 with the scripted provider.
- Assert every task anchor and every admitted/fallback anchor has proven source identity/value from the public flow view or compiled flow, and that task/result anchor keys agree.
- Assert the implementation does not expose only the synthetic `sha256(flowSliceId + kind)` anchor construction.
- Run only `Stage03AnchorTest` and record exact RED evidence without changing production code.

## Changed files

- `progress/stage03-anchor-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage03/Stage03AnchorTest.java`

## Verification

| `mvn -Dtest=Stage03AnchorTest test` | RED | Main/test compilation succeeded; Surefire ran 1 test with 1 assertion failure and 0 errors. The first observed result anchor was `anchor:result:d1543b729f98982b09e3e2b8d68aeb9f3ee52aee743f16b01258b5b93e1e8146`, exactly the synthetic `sha256(flowSliceId + kind)` construction, so the proven-anchor contract failed closed. |

## Decisions / blockers

- No reflection, private methods, result tampering, or production test hooks will be used.
- The RED is intentionally assertion-only: all Stage01/Stage02 replay and Stage03 generation completed before the anchor provenance assertion; no production code was changed.
