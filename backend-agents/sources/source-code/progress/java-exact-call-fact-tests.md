# Progress: exact-call Fact candidate RED

- Status: COMPLETE (bounded RED handoff)
- Agent role: Luna/xhigh TDD test writer
- Model: gpt-5.6-luna/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Step 04 §8.0.2 minimum `JAVA_EXACT_CALL` candidate-enumerator RED plus the smallest synthetic persisted-graph fixture factory.
- Approved inputs: Frozen synthetic Java/Spring graph fixture only; no customer source, Provider, network, source scan, schema, policy, or production edits.
- Current branch/worktree: `codex/source-analysis-process-materials` / `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Re-read the repository TDD instructions and confirmed the public candidate/graph seams are the test boundary.
- Checked `git status --short`; existing implementation, design, and test edits are shared work in progress and remain preserved.
- Created this progress handoff before touching Java.

## Current state

- Completed the full Step 04 §8.0.2 contract and both implementation plans; the public graph/evidence seams support independent persisted derivation of call-site owners, exact call edges, and METHOD endpoints.
- Root released the Java/Maven gate after Terra’s formatting, direct aggregate, and offline package baseline; the bounded exact-call test and fixture edits are applied and formatted.
- The persisted dual-root/shared-callsite premise is GREEN: 2 exact CALL_TARGET edges, 3 owner-expanded expected rows, one target `com.example.OrderService#dispatch(java.lang.String)`, one shared call-site owned by both discovered entries, and all 3 call-site/edge/METHOD evidence closures present.
- The intended M1 RED is stable: standard registry enumeration returns 0 `JAVA_EXACT_CALL` candidates against the 3 independently-derived expected rows.

## Changed files

- `progress/java-exact-call-fact-tests.md`
- `src/test/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumeratorTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing shared changes observed and preserved. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=FactCandidateEnumeratorTest#enumeratesExactCallForEachPersistedCallTargetAndOwningEntry test` | RED (expected) | 1 test, 1 failure, 0 errors, 0 skips; expected size 3, actual exact-candidate size 0 at the missing-family assertion. |
| Same exact method selector after formatting | RED (expected, stable) | 1 test, 1 failure, 0 errors, 0 skips; same line/assertion and raw count. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute FactCandidateEnumeratorTest.java>,<absolute ProgramGraphsPublicFixture.java> spotless:apply` | PASS | Spotless selected exactly 2 files; both changed to clean. |
| Same absolute two-file selector with `spotless:check` | PASS | Spotless selected exactly 2 files; 0 need changes. |
| `git diff --check` (two owned Java files) | PASS | No whitespace errors. |

## Decisions

- Derive exact-call target method, caller/owner entries, and call-target edge from fresh reopened persisted graph artifacts, not from candidate-enumerator output.
- Assert the `JAVA_EXACT_CALL` denominator per owning entry and `CALL_TARGET` edge, with the four §8.0.2 atoms and target `METHOD` evidence bindings.
- Keep the synthetic graph acyclic and use real supported Java constructs/receiver fields; cross-entry calls are plain Java calls, not HTTP-effect claims.
- The fixture shape will use two discovered roots: `OrderController#approve` calls `OrderService#dispatch`, while a second HTTP entry roots directly at `OrderService#dispatch`; the shared `ApprovalClient.record` call-site is then independently expected to have both owners. All target/owner tuples remain derived from fresh-reopened public graphs.

## Exact next action

- Release Maven to root with the raw RED and premise counts; Terra owns the eventual v3 GREEN.

## Resume checks

- Re-read this progress file and preserve the two owned Java files plus this bounded RED evidence for Terra’s v3 implementation.
