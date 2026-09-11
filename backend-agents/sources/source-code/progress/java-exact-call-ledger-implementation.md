# Progress: exact-call M3 ledger implementation

- Status: COMPLETE
- Agent role: Terra/xhigh implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: M3 v3 public Fact ledger publication and exactly four M3 engine-policy registration replacements after Luna's confirmed RED.
- Approved inputs: Step04 §8.0.2 M3 v3 conservation contract, current Fact ledger publisher/accounting source, engine policy registration, and Luna's frozen public-seam RED.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record before production edits.
- Read the current M3 Fact ledger publisher/accounting path, four engine policies, and the Step04 §8.0.2 v3 replacement contract.
- Mapped the bounded replacement: the four M3 standalone schemas and module version move to v3; the accounting body adds `exactCallCandidateDenominatorKeys`; closure verifies the UTF-8-sorted disjoint union of boundary, guard, and exact candidate keys equals the full M2 candidate keys; the engine replaces only the four M3 policy versions.
- Root confirmed Luna's exact M3 RED: all three exact Facts and twelve exact Proofs persist, then the legacy two-family accounting closure raises `FACT_ACCOUNTING_INVARIANT_BROKEN`.
- Replaced the four M3 standalone schemas and module version with v3, added the exact-call denominator-key projection, and changed closure to compare the full candidate denominator against the duplicate-rejecting boundary, guard, and exact partition union.
- Replaced only the four M3 public policy registrations with their v3 schemas; existing M1/M2 and unrelated engine registrations remain untouched.

## Current state

- The bounded M3 ledger publication slice is complete: the ledger emits the v3 M3 projection, the engine accepts its four v3 public standalone payloads, and the direct M3 selector plus the post-format three-class regression pass. The test author's corrected guard-ledger oracle derives the four exact keys from candidates while retaining its boundary, guard, and external-gap checks. The publication remains exactly four semantic payloads and one receipt; no compatibility reader or Step05 work was added.
- No contract incompatibility was found. Existing Fact/Proof/disposition/gap projection is generic over candidate kind, while external-effect gaps remain explicitly boundary-only. Exact candidates add to the public facts/proofs/accounting union without a synthetic external-effect gap or a fifth payload.

## Changed files

- `progress/java-exact-call-ledger-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/publish/FactLedgerPublicationSpecifier.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (four M3 public-policy entries only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Root/Luna exact M3 selector | RED (expected) | 1 test, 0 failures, 1 error: `FACT_ACCOUNTING_INVARIANT_BROKEN` at the old two-family accounting closure after persisted exact M2 Facts/Proofs. |
| Root/Luna exact M3 selector after production change | PASS | 1 test, 0 failures, 0 errors. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProvenCodeFactsPublicationSpecifierTest,GuardConditionFactLedgerTest,FactCandidateExactUpstreamTest test` | BLOCKED ON TEST-MIGRATION ORACLE ERROR | 5 tests, 1 failure, 0 errors: the new M3 class (3) and exact-upstream (1) passed; the newly migrated `GuardConditionFactLedgerTest` line 81 expected zero exact denominator keys but observed four valid `JAVA_EXACT_CALL` keys. |
| Luna guard-ledger test-only correction | PASS | 1 test, 0 failures, 0 errors; exact keys derive from the four candidates while the two boundary keys, one guard key, and two external gaps remain asserted. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute two owned production files> spotless:apply` | PASS | Spotless selected 2 files; 0 changed and 2 already clean. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=<absolute two owned production files> spotless:check` | PASS | Spotless selected 2 files; 0 need changes. |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProvenCodeFactsPublicationSpecifierTest,GuardConditionFactLedgerTest,FactCandidateExactUpstreamTest test` | PASS | 5 tests, 0 failures, 0 errors, 0 skipped: 3 M3 publication, 1 guard-ledger, 1 exact-upstream. |

## Decisions

- Preserve the full M2 Fact/Proof/disposition/gap projections and the four semantic payload plus receipt shape.
- Add the v3 exact-call denominator partition only through the M3 public accounting/projection contract: boundary, guard, and exact sets must be disjoint and union to the complete M1 candidate denominator.

## Blockers

- None for this bounded M3 publication slice. Follow-on Step05 or other reader/cutover work remains outside this progress record.

## Exact next action

- Release Maven. Await a separately confirmed next RED before any new production work.

## Resume checks

- Do not reopen this completed slice for Step05, compatibility, or a fifth payload; preserve the v3 M3 contract unless a separately confirmed RED authorizes work.
