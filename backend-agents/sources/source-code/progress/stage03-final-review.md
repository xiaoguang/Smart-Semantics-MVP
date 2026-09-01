# Stage03 Final Review

Status: IN_PROGRESS

Review activity: COMPLETE; acceptance remains BLOCKED by OPEN/PARTIAL P1
findings below. The progress status intentionally stays IN_PROGRESS until no
P0/P1 finding remains.

Scope: read-only final review of the current Stage03 M5–M7 implementation and
Stage03 tests. This work unit may modify only this progress record.

Review basis:

- Stage03 design and scoped source-agent instructions.
- `progress/stage03-code-review.md` and `progress/stage03-integrity-review.md`.
- Current Stage03 production sources and Stage03 direct tests.

Planned verification:

- Reclassify every prior P0/P1/P2 finding with exact file/line/test evidence.
- Run only direct Stage03 selectors.
- State separate single-flow acceptance and multi-flow readiness.

No production or test files were modified in this review.

## Verification

| Command | Result |
| --- | --- |
| `mvn -Dtest=Stage03GeneratorTest,Stage03IntegrityTest,Stage03CompletenessTest,Stage03SemanticTest,Stage03FormulaTest,Stage03ProofDensityTest,Stage03CapsuleTest,Stage03AnchorTest,Stage03JshErpBoundaryTest test` | PASS — 32 tests, 0 failures, 0 errors, 0 skipped; build success. |

The selector is direct Stage03 coverage only. The passing result establishes the
current bounded synthetic and zero-Capsule paths, not full M5–M7 acceptance.

## Finding status

The original review contained one P0, fourteen P1, and two P2 findings. The P0
is CLOSED; P2 status remains CLOSED (empty-R1 strictness) and PARTIAL (public
registry-construction normalization), as recorded by the prior integrity
review. The substantive final review is the P1 matrix below.

### P1 findings

1. **PARTIAL — task input is now a real single-Capsule package, but its
   identity/budget projection is incomplete.**

   Evidence: `Stage03Generator.java:1006-1043` serializes the Flow, Capsule,
   anchors, runtime, selected budgets and registry admission. The admission
   now includes child IDs/digests and full selected term/claim/question,
   technical, template, and ownership bindings at `1825-1867` and
   `1904-1943`; `Stage03CompletenessTest.java:31-63` passes. However the task
   input omits `knowledgeProfileId`, `nineSectionProfileId`, and the remaining
   `maxFlowInterpretations`, `requiredSectionCount`, `maxDocumentBytes`, and
   `maxReaderItems` budget fields (`1032-1038`). The task identity includes the
   whole budget/profile object at `168-171`, but the provider-visible package
   does not, so this finding is not fully closed.

2. **CLOSED — registry content digests and bundle identity are recomputed.**

   Evidence: `Stage03Generator.java:1691-1718` independently recomputes all
   six canonical child digests and the enclosing bundle ID; canonical content
   is implemented in `Stage03RegistryCanonicalizer.java:22-57`. The mutated
   content and malformed-entry regressions in
   `Stage03IntegrityTest.java:69-105` pass with stable `REGISTRY_INVALID`.

3. **PARTIAL — fallback totality is checked, but declared display templates do
   not control rendering.**

   Evidence: `RegistryIndex.uniqueFallback` at `2008-2017` enforces exactly one
   policy and a matching resolution slot; `technicalDisplay` at `1259-1263`
   returns `anchor.provenDisplay()` and never resolves
   `TechnicalDisplayPolicy.displayTemplateKey`. The invalid resolution-order
   mutation passes in `Stage03IntegrityTest.java:178-193`, but no declared
   display template is executed.

4. **PARTIAL — term atom-kind and claim-pattern checks are present, while
   claim/term reader bindings are not executed end-to-end.**

   Evidence: eligibility and pattern matching are implemented at
   `Stage03Generator.java:1945-2000` and task admission carries the fields at
   `1904-1919`; the mutation test passes at
   `Stage03IntegrityTest.java:149-176`. `admit` only stores the selected claim
   keys (`464-492`), and planner/rendering resolves the generic term/outcome/
   formula contracts rather than each claim's `readerTemplateKey`; the term's
   `technicalFallbackPolicyKey` is not used to select a reader display.

5. **OPEN — M6 hard-anchor merge, typed relations, and conflict ownership are
   absent.**

   Evidence: `assemble` creates Flow-qualified request/record/result objects at
   `529-539`, attaches all Flow atoms to one activity at `540-545`, returns
   `List.of()` relations and conflicts at `573-575`, and chooses same-kind
   meanings lexicographically at `637-645`. No direct Stage03 test constructs
   two compiled flows; `progress/stage03-capsule-tests.md:13,28` records that
   the attempted public two-entry fixtures produced zero compiled flows. Thus
   the design requirements for shared SQL/FQN anchors, typed edges, priority
   conflicts, and unique cross-flow owners remain unproven and unimplemented.

6. **OPEN — Capsule closure is shallow and FlowGap isolation is not enforced.**

   Evidence: `CapsuleContext.of` adds every `stage02.flowGaps()` to each flow
   context at `1317-1325`, while `validateClosure` checks only Flow ID, atom-ID
   set, and OutcomePath-ID set at `1458-1465`; it does not revalidate proofPack,
   fact/proof references, source/excerpt hashes and locators, obligations, or
   flow-local Gap ownership. `Stage03CapsuleTest.java:31-98` proves exact task
   serialization (and passes), but explicitly does not mutate/revalidate a
   malformed capsule or prove multi-flow isolation.

7. **CLOSED — question reason provenance and strict R2 closure.**

   Evidence: `resolveQuestionGaps` checks context membership and registry
   reason codes at `1187-1197`; `parseR2` rejects changed/expanded basis,
   duplicate/unknown/omitted reviews at `416-461`. The two negative regressions
   pass at `Stage03IntegrityTest.java:195-218`.

8. **CLOSED for the prior hardcoded/all-Flow formula finding — with an
   untested generality limitation.**

   Evidence: `formulaDefinitions` derives normalized formula, operators,
   operands, and the exact formula atom basis from public Stage02 facts at
   `578-609`; `assemble` uses that basis at `556-562`; typed field/metric items
   render the values at `760-816`. `Stage03FormulaTest.java:25-127` passes all
   actual-value, basis, and non-owning-reference assertions. The implementation
   only recognizes a one-atom `AVAILABLE_FORMULA` fact and does not model
   aggregate/time semantics, but it no longer has the original fixed literal
   or all-Flow basis defect.

9. **PARTIAL — body cleanliness now blocks the tested sensitive classes, but
   does not reject every arbitrary path form.**

   Evidence: `safeReaderText`/`containsForbiddenReaderContent` at
   `881-904` reject prompt/provider/runtime/model tokens, common absolute path
   prefixes, `../`, `.java/.xml/.yml`, internal IDs, SHA material, and template
   delimiters. The forbidden registry-value test passes at
   `Stage03SemanticTest.java:63-115`, and the final body check is at
   `997-1004`. Paths outside the listed prefixes/extensions (for example a
   non-source `/opt/.../note.txt` or Windows-style path) are not covered by the
   predicate, so the design's blanket absolute/relative-path prohibition is
   broader than the implementation.

10. **CLOSED — interpretation and Stage03 identities include canonical round
    receipt lineage.**

    Evidence: accepted R1/R2 receipts are canonicalized at `278-329`, included
    in the interpretation identity at `223-228`, and the resulting
    interpretation is included in the final result identity at `110-113`.
    `Stage03CompletenessTest.java:65-100` passes both semantic order
    normalization and valid-selection identity divergence.

11. **PARTIAL — provider exceptions are normalized, but failed-round lifecycle
    receipts and started/pre-start distinction are absent.**

    Evidence: `execute` maps arbitrary provider RuntimeExceptions to stable
    `MODEL_RESPONSE_INVALID` at `244-253`; transport/runtime gates are at
    `256-275`, and the normalization test passes at
    `Stage03IntegrityTest.java:233-243`. `ModelRoundReceipt` only represents a
    successful round (`ModelRoundReceipt.java:3-5`); `generate` returns no
    failure receipt carrying task/session/started state, so the design's
    post-start versus pre-start operational contract remains partial.

12. **CLOSED — maxReaderItems is enforced.**

    Evidence: the planner counts all section items and fails at
    `Stage03Generator.java:818-829`; the boundary assertion is
    `Stage03IntegrityTest.java:220-231`.

13. **OPEN — information-density responsibilities remain incomplete despite
    typed atom/meaning/Gap/Outcome/formula items.**

    Evidence: the planner creates those typed items at `677-816`, but the
    repository model still has no objects/activities/relations ReaderItems for
    the corresponding sections; empty-section fillers remain at `818-826`.
    `assemble` returns no relations (`573-575`) and generic objects/activities
    (`529-545`), so sections 3, 6, and often 8 can be only built-in scope/empty
    items. Existing tests cover atom/meaning/Gap conservation
    (`Stage03IntegrityTest.java:36-67`), Outcome semantics
    (`Stage03ProofDensityTest.java:24-75`), and formula density
    (`Stage03FormulaTest.java:25-127`), but not all section responsibilities
    in design §8.6.

14. **PARTIAL — anchors now carry proven public tokens, but are not a complete
    TechnicalAnchorCompiler.**

    Evidence: `CapsuleContext.anchors` consumes the replayed public
    `Stage01FlowView` entry/root nodes and Stage02 fact/atom/outcome/step data
    at `1329-1383`; keys are provenance-bearing primary bindings at
    `1392-1400`. `Stage03AnchorTest.java:46-97` passes and proves keys are not
    the old `sha256(flowSliceId + kind)` form. Nevertheless the construction is
    hardcoded to `HTTP_ENTRY`, `INVENTORY_LOAD`, `SUCCESS_RESULT`, the first
    OutcomePath, and the first shared step (`1346-1382`); it does not consume
    typed CFG edges or derive shared table/FQN anchor equivalence. Consequently
    the single-flow non-synthetic check is closed, while the cross-flow hard
    anchor requirement remains open under finding 5.

## Acceptance decision

### Current single-flow profile

**Not accepted as full Stage03 M5–M7.** The synthetic one-Flow path and the
fixed jshERP zero-Capsule boundary are operationally green (32 direct tests;
the boundary test asserts zero Provider calls and nine headings at
`Stage03JshErpBoundaryTest.java:66-103`). Full acceptance is blocked by P1
findings 1, 3–6, 9, 11, 13, and 14, especially missing capsule revalidation,
registry template execution, section-density responsibilities, and lifecycle
receipts.

### Multi-flow target

**Blocked.** There is no valid public fixture with two compiled Capsules in the
current direct Stage03 suite, and production returns Flow-qualified duplicate
objects, no relations/conflicts, global FlowGaps in every context, and no
cross-flow owner/anchor merge. The multi-flow design matrix (§12 rows 3, 11–14)
therefore remains unimplemented and unverified.

## Scope check

Only `progress/stage03-final-review.md` was added/modified by this review;
production, tests, fixtures, and design documents were not edited.
