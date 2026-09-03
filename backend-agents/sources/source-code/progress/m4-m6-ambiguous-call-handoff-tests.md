# Progress: M4/M6 ambiguous-call handoff tests

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Add one public-seam regression test proving an M2 ambiguous call Gap is preserved without downstream data-flow re-interpretation or duplicate Gap projection.
- Approved inputs: Scoped `AGENTS.md`; M2.1 ambiguity contract and GREEN evidence; existing M3/M4/M5/M6 public graph seams and frozen ambiguity fixture.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Added one bounded public-seam test using the existing String/Integer plus `null` ambiguity fixture.
- Built and fresh-reopened the real M2→M3→M4→M5→M6 graph chain.
- Verified M2 retains exactly one `CALL_TARGET_AMBIGUOUS` Gap with the original locator and ID.
- Verified M3/M4 emit no `recordStatus` call projection, Java boundary node, boundary transfer work item, or same-site data-flow Gap.
- Verified M6 `graph-gaps.jsonl` contains exactly one unchanged CALL Gap and no DATA_FLOW projection.

## Current state

The bounded ambiguity handoff test is green. The existing `call-graph-target-ambiguous` fixture is used through the public M1→M6 graph publication seams; no production behavior was changed.

## Changed files

- `progress/m4-m6-ambiguous-call-handoff-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/graph/AmbiguousCallHandoffTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AmbiguousCallHandoffTest test` | TEST COMPILE CORRECTED | Initial test-only compile exposed missing artifact import, stream helper, step gap-list argument, and JSON parser conversion; no production files changed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AmbiguousCallHandoffTest test` | TEST SETUP CORRECTED | First executable run exposed invalid upstream fixture module numbers; corrected the test fixture to use the registered source/discovery module addresses. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AmbiguousCallHandoffTest test` | PASS | 1 test, 0 failures/errors/skips; M2→M6 handoff assertions green |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/test/java/org/sourceanalysis/app/analysis/graph/AmbiguousCallHandoffTest.java spotless:apply` | PASS | Formatted owned test |
| `git diff --check -- src/test/java/org/sourceanalysis/app/analysis/graph/AmbiguousCallHandoffTest.java progress/m4-m6-ambiguous-call-handoff-tests.md` | PASS | No whitespace errors |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AmbiguousCallHandoffTest test` | PASS | 1 test, 0 failures/errors/skips after formatting |

## Decisions

- Preserve the existing five-graph design and test only the ambiguity handoff; do not add a new graph or technology-specific behavior.

## Blockers

## Exact next action

Parent may review and include this test in the M4/M5/M6 direct selectors. No production follow-up is warranted for this bounded contract.

## Resume checks

- Do not modify production code, design, POM, or another Agent's progress file.
- Keep the test fixture bounded to one ambiguous call site.
- Run only the direct selector for this test and `git diff --check`.
