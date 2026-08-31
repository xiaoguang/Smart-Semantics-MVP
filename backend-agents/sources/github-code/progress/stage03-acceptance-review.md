# Progress: Stage03 acceptance review

- Status: COMPLETE
- Agent role: Stage03 read-only acceptance reviewer
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Post-template, FlowGap-isolation, and reader-density M5–M7 acceptance review
- Approved inputs: Scoped AGENTS.md; Stage03 design/exit matrix; prior Stage03 reviews and current RED progress; current Stage03 records, generator, and direct tests
- Current branch/worktree: shared worktree; preserve unrelated changes

## Completed

- Created this owned progress file before review edits.
- Read the scoped instructions, Stage 03 design and exit matrix, prior final and
  post-multiflow reviews, the template/FlowGap/density progress records, current
  public Stage 03 records, registries/canonicalizer, generator, and direct tests.
- Reclassified the prior 14 P1 findings against the current implementation and
  separated bounded v0 behavior, remaining capability/GAP, and M8-owned work.

## Current state

- The current direct synthetic one-Flow, real two-Flow, and fixed jshERP
  zero-Capsule profiles all pass their bounded public-seam regressions. This is
  not a full M5--M7 exit because Capsule evidence closure is still shallow and
  several registry/M6 generality contracts are only partially represented.

## P1 reclassification

1. **CLOSED — task profile and budget projection.** `taskInput` now serializes
   all seven budget fields and all three profile IDs at
   `Stage03Generator.java:1242-1280`; `Stage03TaskBodyTest.java:29-51` passes.

2. **CLOSED — registry content and bundle identity.** `RegistryIndex.of` checks
   all six canonical child digests and recomputes the bundle ID at
   `Stage03Generator.java:2119-2157`, using
   `Stage03RegistryCanonicalizer.java:22-57`. Mutation/malformed-entry tests
   pass in `Stage03IntegrityTest.java:69-105`.

3. **CLOSED for the finite v0 template grammar — technical display execution.**
   `technicalDisplay` resolves and executes the policy's declared template at
   `Stage03Generator.java:1501-1526`; missing/invalid slots are rejected by
   `ReaderContracts.technicalDisplayTemplate` at `1995-2016` before Provider
   work. Both custom-template tests pass in
   `Stage03TemplateExecutionTest.java:25-78`. This closes the previous RED;
   arbitrary future template languages remain outside this finite grammar.

4. **PARTIAL — eligibility is enforced, but term/claim reader bindings are not
   end-to-end.** `termSupported`/`claimSupported` enforce anchor, minimum basis,
   eligible atom role, and required patterns at `Stage03Generator.java:2385-2398`,
   and complete bindings are task-visible (`2265-2307`). However admission only
   stores claim keys (`474-502`), while planning always uses fixed
   `READER_TERM_V1` (`820-831`); per-claim `readerTemplateKey` and the term's
   fallback policy do not select a claim-specific reader item. A term-backed
   meaning remains readable in the exercised profile, but a valid claim-only
   meaning has no localized value (`487-501`) and is rejected by the planner's
   nonempty-value gate (`821-824`), so this path can be fatal.

5. **PARTIAL — basic two-Flow hard-anchor merge and relations are now green;
   typed relation/conflict/owner semantics are incomplete.** The assembler
   merges equal object IDs and emits two flow links at
   `Stage03Generator.java:505-600`, and `Stage03MultiFlowTest.java:196-223`
   proves one shared same-table RECORD, distinct request anchors, nonempty
   relations, and unique atom IDs. `ObjectRelation` exposes endpoints/basis but
   no named relation kind (`ObjectRelation.java:5-10`); relation direction is
   hidden in the ID hash (`671-687`), success always supplies `List.of()`
   conflicts (`598-600`), term selection is by meaning ID rather than registry
   priority (`749-757`), and `KnowledgeAccounting` is flat lists only
   (`KnowledgeAccounting.java:5-12`). These are not fatal for the current
   no-conflict fixture, but the full M6 conflict/ownership contract is not closed.

6. **OPEN — full Capsule closure remains fatal; FlowGap isolation is closed.**
   `CapsuleContext.of` now admits only the current Flow's Gap IDs and validates
   their entry ownership (`Stage03Generator.java:1588-1612`), and
   `Stage03FlowGapIsolationTest`/`Stage03GapScopeTest` pass. But
   `validateClosure` only compares Flow ID, atom-ID set, OutcomePath-ID set,
   and expected Gap IDs (`1802-1811`); it does not revalidate proofPackId,
   fact/proof references, source locators/hashes/excerpts, supported span
   references, or projection obligations. `Stage03CapsuleTest.java:32-99`
   checks task serialization only. Per design §6.3/§10.3, successful M5 must
   reject an invalid evidence closure before model admission, so this remains
   a fatal full-exit blocker even though the honest public fixture cannot mutate
   a freshly replayed Stage02Result.

7. **CLOSED — strict R2 and question provenance.** `parseR2` requires the exact
   R1 proposal set and forbids basis/reference expansion at
   `Stage03Generator.java:426-472`; question Gap membership is checked at
   `1429-1434`. The negative integrity tests at `195-218` pass.

8. **CLOSED for the original formula defect; general formula support is a
   capability limitation.** `formulaDefinitions` derives actual formula value,
   operators, operands, and exact atom basis (`690-721`), and
   `Stage03FormulaTest` passes. The implementation intentionally recognizes
   only a one-atom `AVAILABLE_FORMULA`; aggregate/time semantics require a
   broader Stage 02 profile and are an explicit GAP, not a fixed-literal bug.

9. **PARTIAL — tested body cleanliness is closed for current v0 values, but the
   predicate is not a proof of every path spelling.** Reader values reject
   internal tokens, prompt/provider/runtime/model text, source extensions, and
   Unix/Windows/UNC/multi-segment paths at `Stage03Generator.java:1120-1138`.
   All 12 arbitrary path/value cases pass before Provider in
   `Stage03TaskBodyTest.java:54-79`. The finite regex still does not establish
   the design's blanket prohibition for every possible relative spelling (for
   example a single-slash path), so retain this as a non-fatal capability gap.

10. **CLOSED — canonical receipt-sensitive identities.** R1/R2 canonical
    receipts enter interpretation identity at `Stage03Generator.java:233-238`
    and the final identity at `119-126`; the order-normalization and changed
    selection checks pass in `Stage03CompletenessTest.java:65-100`.

11. **PARTIAL — Provider exceptions normalize, but failed-round lifecycle
    receipts do not.** `execute` maps arbitrary RuntimeExceptions to stable
    `MODEL_RESPONSE_INVALID` without retry at `Stage03Generator.java:254-264`,
    and the normalization test passes (`Stage03IntegrityTest.java:233-243`).
    `ModelRoundReceipt` contains only successful response hash, started receipt,
    and observed runtime (`ModelRoundReceipt.java:3-5`); `generate` returns no
    public pre-start/post-start failure receipt. Immutable archive, candidate
    slots, trace, and recovery are M8 responsibilities, but preserving a
    structured failure receipt for M8 is still a partial Stage 03 boundary.

12. **CLOSED — maxReaderItems.** The planner enforces the aggregate item budget
    at `Stage03Generator.java:1039-1040`; the exact limit regression passes at
    `Stage03IntegrityTest.java:220-231`.

13. **PARTIAL — current reader density is green for exercised M6 items, while
    generic section-duty gates remain incomplete.** The planner now creates and
    renders typed object/activity/relation/question items at
    `Stage03Generator.java:962-1028`; `Stage03ReaderDensityTest.java:28-115`
    passes the dual-flow ownership, basis/reference, Markdown coupling, and
    no-empty checks for sections 3/4/6/8. Nevertheless any section can still be
    filled by `EMPTY_SECTION` at `1029-1037`, and there is no independent gate
    proving every §8.6 duty (notably Flow purpose, field/metric coverage, and
    answerability) for all admissible selections. Thus density is accepted for
    the tested v0 positive profile, not closed as a general M7 contract.

14. **PARTIAL — proven anchors are sufficient for the current profile, not a
    general TechnicalAnchorCompiler.** Anchor keys consume replayed entry/root
    nodes and public fact/atom/outcome/step values at
    `Stage03Generator.java:1615-1675`; `Stage03AnchorTest.java:47-97` and the
    two-Flow tests pass. The implementation still hardcodes `HTTP_ENTRY`,
    `INVENTORY_LOAD`, `SUCCESS_RESULT`, the first OutcomePath, and the first
    shared step (`1632-1644`), so unsupported source shapes and richer typed CFG
    equivalence remain explicit capability GAPs.

## Changed files

- progress/stage03-acceptance-review.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest='Stage03*Test' test` | PASS | BUILD SUCCESS; 57 tests, 0 failures, 0 errors, 0 skipped. Main/test compilation succeeded. Per-class: Integrity 10, FlowGapIsolation 3, Semantic 2, ReaderDensity 1, Completeness 2, Generator 13, Formula 1, TemplateExecution 2, JshErpBoundary 1, MultiFlow 5, ProofDensity 1, GapScope 1, Capsule 1, Anchor 1, TaskBody 13. |
| `git diff --check -- progress/stage03-acceptance-review.md` | PASS | No whitespace errors in the only review-owned file. |

## Decisions

- Read-only review: no production, test, fixture, or design changes.
- Do not treat a green test count as complete acceptance; evaluate required design invariants and intentional RED contracts separately.
- **Bounded profile result: ACCEPTED as an operational regression profile.**
  The current static Java/Spring MVC/MyBatis fixtures have honest zero-Flow
  jshERP behavior (zero Provider calls, nine headings), a nonempty one-Flow
  positive path, and a nonempty two-Flow path with task-local evidence and
  same-table merge. The 57 green tests are evidence for those bounds only.
- **Stage03 M5--M7 exit: NOT ACCEPTED.** Full acceptance is blocked by the
  fatal Capsule closure gap (P1.6). P1.4, P1.5, P1.9, P1.11, P1.13, and P1.14
  remain partial contracts or capability limits; the claim-only selection path
  can additionally fail before rendering. This conclusion applies separately
  from M8 archive/trace/recovery delivery.
- **M8 boundary:** immutable Candidate/ReaderCandidateRound archive,
  validate/trace/recovery, and CLI/HTTP installation are not Stage03 acceptance
  criteria. Stage03 must still hand M8 a structured failure/started-state
  receipt; the current success-only public result does not expose that lifecycle.

## Blockers

- Full Capsule closure is the remaining fatal Stage03 acceptance blocker: the
  current public seam serializes evidence but does not independently verify all
  proof/fact/span/obligation closure invariants before Provider admission.
- Conflict/priority/explicit owner semantics, claim-specific template execution,
  broad path grammar, generic section-duty gates, and generalized anchors remain
  partial capability/design gaps; they do not invalidate the bounded no-conflict
  v0 fixtures that are green.

## Exact next action

- One next public-seam RED is recommended: extend the existing capsule fixture
  to produce a publicly reachable invalid proofPack/fact/proof/span/obligation
  reference (or a source mutation that makes the replayed closure inconsistent)
  and require stable `CAPSULE_CLOSURE_BROKEN` before any Provider task. Keep the
  current flow-local Gap and task-serialization regressions unchanged.

## Resume checks

- Review is COMPLETE. Only this progress file was modified; no production,
  test, fixture, or design path was edited. The exact selector above was the
  only test command run by this review.
