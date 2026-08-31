# Progress: Stage03 post-multiflow review

- Status: COMPLETE
- Agent role: read-only Stage03 post-multiflow reviewer
- Model: gpt-5.6-luna xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Reclassify the original Stage03 P1 findings after the current multi-flow implementation
- Write scope: this progress file only
- Acceptance conclusion: bounded direct tests are green, but full Stage03 acceptance remains blocked by the OPEN/PARTIAL findings below

## Inputs read

- `sources/github-code/AGENTS.md` and `backend-agents/AGENTS.md`
- `docs/stages/03-nine-section-generation.md`
- `progress/stage03-final-review.md`
- `progress/stage03-task-body-tests.md`
- `progress/stage03-multiflow-tests.md`
- `progress/stage03-nine-section-generation-core.md`
- `progress/stage03-multiflow-design.md`
- Current public Stage03 records, `Stage03Generator`, registries/canonicalizer,
  and all direct Stage03 tests

## Verification

Required direct selector, run once after the static review baseline:

```text
mvn -Dtest='Stage03*Test' test
```

Result: BUILD SUCCESS; 51 tests, 0 failures, 0 errors, 0 skipped. Per-class
counts were Integrity 10, Semantic 2, Completeness 2, Generator 13, Formula 1,
JshErpBoundary 1, MultiFlow 5, ProofDensity 1, TaskBody 13, GapScope 1,
Capsule 1, and Anchor 1.

The green selector proves the current scripted synthetic, two-flow, and
zero-Capsule boundary regressions. It does not by itself prove all design
responsibilities, especially cases not represented by the public fixtures.

## Original P1 reclassification

1. **CLOSED — complete task profile/budget projection.**

   `Stage03Generator.java:1139-1177` serializes all seven budget fields and
   interpretation, knowledge, and nine-section profile IDs into the provider
   input. `Stage03TaskBodyTest.java:29-52` asserts each value against the public
   `Stage03Request` and budget records and passes. The task identity still
   includes the same request/profile/budget material at
   `Stage03Generator.java:177-181`.

2. **CLOSED — registry content digests and bundle identity are recomputed.**

   `Stage03Generator.java:1895-1932` recomputes each child digest through
   `Stage03RegistryCanonicalizer` and recomputes the bundle ID before accepting
   the bundle. The canonicalizer sorts identity-bearing entries and preserves
   display resolution order (`Stage03RegistryCanonicalizer.java:22-57,
   76-168`). Mutated content and malformed entries fail with stable
   `REGISTRY_INVALID` in `Stage03IntegrityTest.java:69-105`.

3. **PARTIAL — fallback totality is enforced, but the declared display template
   is still not executed.**

   `RegistryIndex.uniqueFallback` checks exactly one policy and a matching
   proven resolution slot (`Stage03Generator.java:2207-2222`). However
   `technicalDisplay` returns `anchor.provenDisplay()` after checking the slot
   and never resolves `TechnicalDisplayPolicy.displayTemplateKey`
   (`Stage03Generator.java:1398-1402`). The existing mutation test only proves
   invalid resolution order is rejected (`Stage03IntegrityTest.java:178-193`),
   not that a valid custom display template controls output.

4. **PARTIAL — term atom-kind and claim-pattern admission works, but term/
   claim reader bindings are not executed end-to-end.**

   `termSupported` and `claimSupported` enforce anchor, minimum basis, eligible
   atom roles, and required atom patterns (`Stage03Generator.java:2150-2163`);
   the task carries complete binding records (`2030-2148`), and the mutation
   regression passes (`Stage03IntegrityTest.java:149-176`). But `admit` stores
   selected claim keys (`474-502`) and the planner invokes fixed semantic
   templates (`787-941`); per-claim `readerTemplateKey` and the term's fallback
   binding do not select or render a claim-specific item.

5. **PARTIAL — basic multi-flow hard-anchor merge and relations now pass, but
   relation typing, conflict handling, and explicit ownership remain absent.**

   The current assembler keys objects by `kind + anchorKey`, collects object
   candidates, and creates per-flow links (`Stage03Generator.java:510-600,
   620-687`). The public two-flow regression passes same-table RECORD merging,
   distinct request anchors, relation presence, and unique atom IDs
   (`Stage03MultiFlowTest.java:195-223`). Nevertheless `ObjectRelation` has no
   `relationKind` field (`ObjectRelation.java:5-7`), success always returns
   `List.of()` conflicts (`Stage03Generator.java:598-600`), and
   `meaningsByKind` chooses by meaning ID rather than registry priority
   (`Stage03Generator.java:749-757`). `KnowledgeAccounting` exposes only flat
   atom/meaning/Gap lists (`KnowledgeAccounting.java:5-12`), not an explicit
   owner map. Thus the P1 is not fully closed.

6. **OPEN — Capsule closure and FlowGap isolation remain shallow.**

   `CapsuleContext.of` still inserts every `stage02.flowGaps()` into every
   capsule context (`Stage03Generator.java:1463-1472`). `validateClosure` checks
   only Flow ID, atom-ID set, and OutcomePath-ID set
   (`Stage03Generator.java:1662-1668`); it does not revalidate proofPack ID,
   fact/proof references, source/excerpt hashes and locators, obligations, or
   flow-local ownership. `Stage03CapsuleTest.java:31-99` and
   `Stage03MultiFlowTest.java:89-193` prove serialized task locality, but do
   not mutate a capsule or exercise a foreign FlowGap through parser admission.

7. **CLOSED — question reason provenance and strict R2 closure.**

   `resolveQuestionGaps` verifies context membership and registered reason
   codes (`Stage03Generator.java:1326-1336`); `parseR2` rejects changed,
   expanded, duplicate, omitted, or unknown reviews (`426-472`). The question
   mutation and empty-R1/nonempty-R2 regressions pass at
   `Stage03IntegrityTest.java:195-218`.

8. **CLOSED for the original fixed/all-flow formula defect — general formula
   coverage remains intentionally limited.**

   `formulaDefinitions` derives the normalized value, operators, operands, and
   exact formula atom basis from public Stage02 facts
   (`Stage03Generator.java:690-722`); M6/M7 preserve that basis and render
   typed field/metric items (`578-583`, `884-941`).
   `Stage03FormulaTest.java:25-127` passes actual-value and basis assertions.
   The implementation still models only one-atom `AVAILABLE_FORMULA` facts and
   not aggregate/time semantics, but that is a remaining capability limitation,
   not the original fixed-literal/all-atoms finding.

9. **PARTIAL — tested body cleanliness is broad, but the prohibition is not
   proven for every path form.**

   `safeReaderText` and `containsFilesystemPath` reject internal IDs/SHA,
   prompt/provider/runtime/model tokens, Unix absolute paths, Windows drive and
   UNC paths, `../`, source extensions, and multi-segment relative paths
   (`Stage03Generator.java:1012-1035`). The 12 arbitrary path/value cases pass
   before Provider execution (`Stage03TaskBodyTest.java:54-79`). The relative
   expression requires at least two slash segments and `ensureReaderClean`
   has a narrower direct extension check (`1129-1135`), so the design's blanket
   arbitrary absolute/relative-path prohibition remains broader than the
   covered predicate.

10. **CLOSED — interpretation and Stage03 identity include canonical round
    receipt lineage.**

    R1/R2 receipts are canonicalized and included in the interpretation ID
    (`Stage03Generator.java:233-238`); the final ID includes interpretations,
    model, plan, Markdown SHA, and budget (`87-126`).
    `Stage03CompletenessTest.java:65-100` passes semantic JSON ordering
    normalization and valid-selection identity divergence.

11. **PARTIAL — provider exceptions normalize to stable failure, but failed
    round lifecycle receipts and started/pre-start distinction are absent.**

    `execute` maps arbitrary provider RuntimeExceptions to
    `MODEL_RESPONSE_INVALID` without retry (`Stage03Generator.java:254-264`),
    and the normalization regression passes (`Stage03IntegrityTest.java:233-243`).
    `ModelRoundReceipt` contains only a successful round's hash, receipt ID,
    and observed identity (`ModelRoundReceipt.java:3-5`); there is no public
    failed-round receipt carrying session/task/started state.

12. **CLOSED — maxReaderItems is enforced.**

    The planner counts all section items and fails over budget
    (`Stage03Generator.java:942-953`). The boundary regression passes with
    `RESOURCE_LIMIT_EXCEEDED` (`Stage03IntegrityTest.java:220-231`).

13. **OPEN — information-density responsibilities remain incomplete.**

    The planner emits typed atom, meaning, Gap, Outcome, and formula items
    (`Stage03Generator.java:787-941`), but it does not emit typed ReaderItems
    for BusinessObject, BusinessActivity, ObjectRelation, or the model's
    answerable questions. Empty-section fillers remain the fallback
    (`942-950`), while M6 still creates generic activity/object structures and
    the section-6 relation content is not represented in the plan
    (`505-601`). Existing conservation, outcome, and formula tests therefore
    do not establish all §8.6 section duties.

14. **PARTIAL — anchors now carry proven public tokens and pass current
    single/two-flow checks, but remain a hardcoded incomplete compiler.**

    `CapsuleContext.anchors` consumes replayed Stage01 entry/root nodes and
    Stage02 fact/atom/outcome/step data (`Stage03Generator.java:1475-1535`), and
    `Stage03AnchorTest.java:46-97` proves non-synthetic provenance. The
    multi-flow test also proves same-table RECORD and distinct request behavior
    (`Stage03MultiFlowTest.java:195-223`). However anchor construction still
    selects fixed fact kinds (`HTTP_ENTRY`, `INVENTORY_LOAD`, `SUCCESS_RESULT`),
    the first OutcomePath and first shared step (`1492-1504`), and there is no
    general typed CFG-edge/equivalence compiler. The single-flow and current
    fixture profile pass; general anchor completeness is not closed.

## Acceptance decision

### Current single-flow profile

Not accepted as full Stage03 M5–M7. The bounded synthetic, direct task/body,
formula/outcome/conservation, identity, and fixed jshERP zero-Capsule paths are
green, but P1 3, 4, 6, 9, 11, 13, and 14 remain PARTIAL/OPEN. In particular,
template execution, capsule closure, reader section responsibilities, and
failed-round lifecycle are not complete design evidence.

### Multi-flow target

The public fixture is no longer blocked at Stage02: it genuinely produces two
FlowSlices and two EvidenceCapsules, and the current multi-flow selector proves
two isolated R1/R2 task pairs, local fact/atom/proof/span/Gap payloads, same-table
merge, distinct request anchors, relation presence, and pending Gap provenance.
The target is still blocked for full acceptance by P1 5, 6, 13, and 14: no
relation kind/conflict/explicit owner contract, global FlowGap context leakage,
missing typed reader density, and incomplete general anchor compilation.

## Smallest next public-seam RED recommendation

Add one scripted public-seam mutation test for a valid custom
`TechnicalDisplayPolicy.displayTemplateKey` whose rendered display differs from
`Anchor.provenDisplay`; assert the task/model/Markdown uses the declared
template output (or fails closed if that template is not executable). This is
the smallest remaining gap that directly converts P1.3 from a static finding
into a deterministic behavior contract without needing another fixture or
private hook.

## Scope check

Only `progress/stage03-post-multiflow-review.md` was created/modified in this
review. Production, tests, fixtures, and design documents were not edited.
