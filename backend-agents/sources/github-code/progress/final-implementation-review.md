# Final Implementation Review

Status: IN_PROGRESS

Fixed point: `HEAD` (`22bb16481a00a95ce2dbccf1f3281044bc8e6cb8`)

Scope: third independent source review of the Stage 01--04 bounded-v0 implementation after the final-audit, Round-2 addendum, and recovery-debug slices. Stage 01--04 sources are primarily untracked, so production and tests were inspected directly rather than through `git diff`.

Review activity:

- Re-read the root/scoped guidance, `DESIGN.md`, all four Stage specifications, `progress/target-architecture-implementation.md`, and the latest final-audit/addendum/recovery records.
- Re-reviewed the five P1 groups remaining after the preceding review, including production paths rather than test names alone. An independent Standards/security pass was also completed.
- Did not run Maven, as requested. The latest implementation record reports Stage 04 `90/90`, Stage 03 `69/69`, and Stage 01/02 `79/79`, all with zero failures/errors/skips. Those results are acknowledged, not independently rerun, and do not cover the source-level triggers below.
- No P0 was identified. Four production P1 groups remain, so bounded-v0 is not accepted.

## Re-review disposition

| Previous item | Disposition | Evidence |
| --- | --- | --- |
| Candidate address | CLOSED | Requested ID, directory, sidecar, and reference are bound by `CandidateArchive.referenceFor`; substitution coverage remains present. |
| Receipt origin / coherent audit / recovery prefix | PARTIAL, P1 remains | Byte-identical Round-1 receipts in Round 2 resolve to the parent slot, but other receipt fields and missing-ledger/prefix cases remain self-attested. |
| Fact / term / fallback / empty / Gap Trace | PARTIAL, P1 remains | Fact Proof/source and empty-section accounting are present; term, Flow fallback, and non-Stage01 Gap exact refs remain incomplete. |
| Public Round-2 retry / recovery / terminal | CLOSED | `resumePersistedSlot` is shared by both rounds; direct tests cover fresh retry, installed-Candidate recovery, and stable terminal replay. |
| Unaffected-Flow Round-2 reuse | CLOSED | Parent rounds/receipts are selected only for unaffected Flows and retained byte-for-byte. |
| Receipt-bound Sol/ultra filesystem addendum | PARTIAL, P1 remains | The bounded append-only store exists and defaults fail closed, but its positive path manufactures the claimed observed diagnosis identity. |
| Candidate / receipt / source / config bounded reads | PARTIAL, P1 remains | Candidate/validation-receipt collisions, Candidate byte aggregate, record files, and target config are bounded; source reopens and directory aggregates are not. |

## Remaining P1 findings

### P1 -- Receipt audit is still partly self-attested, and recovery can bless a non-persisted started-event suffix

- Locations: `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java:186-225,326-345,632-699`; `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java:142-211,219-264`; `src/main/java/com/linguan/codemd/stage04/CandidateAssembler.java:656-677`.
- Minimal trigger A: install an otherwise canonical archive without `workspace/series`. `resolveStartedEvent` returns `UNVERIFIABLE`, and `lifecycleEventClosureCheck` records `LIFECYCLE_EVENT_UNVERIFIABLE` as `PASS`, so public validation can return `valid=true` without external lifecycle evidence.
- Minimal trigger B: keep the real started event unchanged and change only receipt `taskSpecId`. `exactRoundClosure` never compares it with `model.task.taskSpecId`, and the receipt-ID formula omits it. Alternatively change `preflightReceiptId`/`attemptId`, recompute `generationReceiptId`, the linked model field, receipt root, sidecar, and manifest. The ledger resolves only started event/ordinal, while Candidate stable identity excludes the receipt audit root. The receipt schema also still omits the designed `providerPolicyId`.
- Minimal trigger C: remove a suffix of durable `THREAD_STARTED` events while retaining an installed Candidate whose receipts name them. `validateStartedEventSuffix` simulates the missing events and `recover` appends `RECOVERED_COMPLETION` without restoring them. `Stage04PersistedRecoveryTest:98-138` positively asserts a two-receipt fixture completes with only one persisted start and does not assert ordinary validation of the completed state.
- Gate breach: every receipt must bind task/policy/runtime/preflight/attempt plus the exact persisted started event/order; the sink must fsync each start before content (`docs/stages/04-runtime-archive-trace-recovery.md:368-372,563,573-581`). A local formula, `UNVERIFIABLE` PASS, or simulated event is not real ledger evidence.
- Existing test gap: `Stage04FinalAuditTraceTest:48-94` changes only started event ID/ordinal with a present ledger; it misses the three triggers above.
- Minimum fix: require `VERIFIED` evidence for every bounded-v0 Candidate; cross-check every receipt task field and provider policy; resolve preflight/attempt/start from durable events; allow recovery only when all receipt starts already exist. Add missing-ledger, taskSpec-only, preflight/attempt, policy, and recovered-normal-validation tests.

### P1 -- Typed Trace remains incomplete for term, Flow fallback, and supported non-Stage01 Gaps

- Locations: `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java:914-1017,1020-1063,1276-1294`; producer paths `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java:883-890,1188-1200`.
- Minimal trigger A: trace a valid `ADMITTED_TERM`. The path validates value/minimum basis/eligible atom kinds, but emits the term key as its own registry-entry target; it neither names the term registry ID nor recomputes/exposes required priority.
- Minimal trigger B: trace a valid Flow technical fallback. The path reaches policy/template/basis/Proof/source, but does not return the task spec ID or replay policy `resolutionOrder`; `taskAnchorFor` merges equal anchors across tasks and loses which task admitted the slot.
- Minimal trigger C: produce a supported Stage02 `FlowGap` or Stage03 `InterpretationGap`. Both enter `ownedGapIds` and become `BOUNDED_QUESTION`, but `gapLineage` only looks in Stage01 `gap-ledger.json`; on a miss it returns a generic container. Even its Stage01 branch omits question key, question registry ID, and allowed reason/template refs.
- Gate breach: section 12 requires exact term registry/priority, fallback policy/template/task/selection, and Gap question/registry/provenance refs (`docs/stages/04-runtime-archive-trace-recovery.md:590-612`). Full validation before Trace does not make an incomplete successful `TraceView` complete.
- Existing test gap: `Stage04FinalAuditTraceTest:97-162` covers Flow fallback/empty plus only a Stage01 expectation Gap. It does not exercise the missing refs or other supported Gap origins.
- Minimum fix: carry and validate exact registry/task/question refs; recompute term priority and fallback slot selection; add typed FlowGap and InterpretationGap provenance branches. Add complete positive assertions for each supported kind/origin.

### P1 -- The filesystem addendum store fabricates the claimed observed Sol/ultra diagnosis receipt

- Locations: `src/main/java/com/linguan/codemd/stage04/FilesystemCorrectiveAddendumStore.java:49-70`; `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendum.java:61-71`; `src/main/java/com/linguan/codemd/stage04/CorrectiveAddendumDiagnosisReceipt.java:24-51`; confirming test `src/test/java/com/linguan/codemd/stage04/Stage04Round2AddendumTest.java:183-241`.
- Minimal trigger: submit a legacy v1 addendum whose directives copy the archived fatal findings. `record` validates parent/findings, then `CorrectiveAddendum.archive` calls `issued`, which hardcodes both expected and observed model=`gpt-5.6-sol`, reasoning=`ultra`, and read-only sandbox/access before hashing those constants. No diagnosis result, provider receipt, task/input/output identity, or independently observed runtime enters the store. The test's positive path deliberately uses this object.
- Gate breach: fatal Round 2 requires an already archived, receipt-bound Sol/ultra diagnosis, not caller directives plus a self-asserted observed identity (`DESIGN.md:1357`; `docs/stages/04-runtime-archive-trace-recovery.md:157,185,393`).
- Minimum fix: have a separately authorized diagnosis workflow produce a canonical receipt bound to exact parent/validation/finding set/task/input/output and independently observed runtime; the store should verify/archive it, never synthesize observed fields. If that capability is outside bounded-v0, reject all fatal improvements explicitly. Add genuine-positive, missing, forged, cross-parent/finding, and runtime-drift tests.

### P1 -- Bounded admission still occurs after unbounded source and directory allocations

- Locations: source reopen `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java:1314-1333`, plus replay reopens `src/main/java/com/linguan/codemd/stage02/Stage02Compiler.java:735-749` and `src/main/java/com/linguan/codemd/stage03/Stage03CapsuleClosureValidator.java:498-510`; directory enumeration `src/main/java/com/linguan/codemd/stage04/CandidateValidationTrace.java:1818-1825`, `DefaultCodeToMarkdownAgent.java:383-409`, `FilesystemSourceRegistry.java:145-162`, and `CandidateSeriesLedger.java:544-549`.
- Minimal trigger A: grow/replace a registered source after preceding replay admission but before Trace reopen. `reopenSpan` calls `Files.readAllBytes` before comparing verified size/SHA; Stage02/03 replay has the same primitive. Safety currently depends on the declared no-concurrent-mutation assumption, not the reader.
- Minimal trigger B: populate Candidate, `candidates/`, registration, or ledger-event directories with a very large entry count. Each path materializes `Files.list(...).toList()` before enforcing the exact set, locating a candidate, or validating state. Per-file byte caps do not cap this metadata allocation.
- Closed portion: Candidate and validation-receipt collision comparisons, Candidate artifact byte aggregate, registration/ledger record bytes, and target CLI/loopback config now use bounded/no-follow reads. `Stage04FinalAuditBoundsTest` covers those byte cases, not source reopen or entry-count aggregates.
- Gate breach: source, registry, Candidate, and ledger state are untrusted; file-count, per-file, and total-byte budgets must precede allocation (`DESIGN.md:189`; `docs/stages/04-runtime-archive-trace-recovery.md:653-658`).
- Minimum fix: route every source reopen through one no-follow reader capped by verified size and profile maximum; stream directories with explicit exact/count budgets and stop before collecting over limit. Add growing/sparse source and limit+1 directory-cardinality tests.

## Standards note

The durable Stage 04 status/matrices still report `67/67` and all seven earlier P1s (`docs/stages/04-runtime-archive-trace-recovery.md:5,739-749,773-789`; `DESIGN.md:17,1406,1415-1421`). After the production disposition is fixed, update those current facts in place to the reported `90/90`, the closed gates, and the remaining gates as required by root `AGENTS.md`; do not mark Stage 04 accepted while any P1 above remains.

## Final disposition

- P0: none.
- P1: four production groups remain: receipt/ledger, typed Trace, diagnosis provenance, and bounded admission.
- Closed: Candidate address; public Round-1/Round-2 retry/recovery/terminal for legitimate persisted states; unaffected-Flow byte reuse; Candidate/receipt/config collision and Candidate byte aggregate.
- Acceptance: **not accepted**. Keep this record `IN_PROGRESS`; Stage 04 remains acceptance-review-in-progress within the local-frozen/scripted-provider bounded-v0 boundary.
