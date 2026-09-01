# Progress: Stage 03 M5–M7 implementation review

- Status: COMPLETE
- Agent role: independent Stage 03 code-review agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: read-only review of `src/main/java/com/linguan/codemd/stage03/` and its directly related Stage 03 tests; only this progress file was added.
- Inputs: repository/prototype/backend/scoped `AGENTS.md`, `docs/stages/03-nine-section-generation.md`, Stage 03 production records/generator, Stage 03 tests/fixtures.

## Verification

| Command | Result | Evidence |
| --- | --- | --- |
| `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest test` | PASS | 14 tests, 0 failures/errors/skips; 169 main sources compiled. This is a narrow behavioral check, not evidence that the untested invariants below hold. |
| `rg -n "sentenceTemplates|sectionOwnership|eligibleAtomKinds|requiredAtomPatterns|maxReaderItems|flowGaps|ReaderItem|relations|roundReceipts" src/main/java/com/linguan/codemd/stage03/Stage03Generator.java` | REVIEW | Confirms registry template/ownership fields, term atom-kind eligibility, claim patterns, and `maxReaderItems` are not consumed by generation; `ReaderItem`/relations are placeholders. |
| `git status --short --untracked-files=all -- progress/stage03-code-review.md` | PASS | Only this owned review file is in scope for this task. Existing repository/parent worktree changes were preserved. |

## Findings

### P0

1. **Typed reader AST falsely claims conservation while the renderer bypasses it.**
   - File/lines: `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java:557-580`, `583-618`.
   - `plan(...)` marks every atom, meaning, and gap as `READER_BODY` with generic owners, but creates one `ReaderItem` per section with empty slots and empty atom/meaning/gap basis. `render(...)` ignores every `ReaderItem`, template registry, ownership registry, outcome, field, relation, and gap provenance and emits fixed prose from a few list counts/strings.
   - Repro: run the passing positive selector, inspect the returned result, and assert that all `ReaderItem` basis lists are empty while `nineSectionPlan.atomDispositions().size()==20` and `gapDispositions()` is nonempty. The resulting Markdown therefore cannot be reconstructed from the plan and silently loses proven semantic atoms, meanings, and Gap provenance. This violates the reader-admission safety gate even though the nine-heading smoke assertion passes.

### P1

1. **Provider task input is not a frozen Flow/Capsule package or controlled-key allowlist.**
   - File/lines: `Stage03Generator.java:639-661`.
   - `taskInput(...)` contains only IDs, six synthetic anchor keys, atom IDs, gap IDs, one registry bundle ID, and one profile ID. It omits Flow steps/outcome closures, allowed facts/atom values/proofs, spans, term/claim/question bindings, registry child IDs/digests, runtime policy, output-schema identity, and budget. The scripted fixture obtains atoms from its private `Stage02Result`, not from the task payload, which masks this.
   - Repro: record `provider.tasks().get(0).inputJson()` in the passing test; it has no `allowedFacts`, `outcomePaths`, `modelEvidenceSpans`, or eligible registry entries. A real Provider cannot make an evidence-grounded finite selection from that task.

2. **Registry digests are not content digests and the bundle identity is not recomputed.**
   - File/lines: `Stage03Generator.java:879-905`, result/task identity at `100-106`, `156-166`.
   - `validateRegistry(...)` checks `sha256(id)` only; it never canonicalizes registry entries. `registryBundleId` is merely trusted and child registry IDs/digests are absent from the result/task identity. A caller can replace a term localized value, claim pattern, question reason, or fallback policy while retaining the old `registryId`, `sha256`, and bundle ID, and the changed registry is accepted.
   - Repro: construct a `BusinessTermRegistry` with the same schema/registryId/sha256 as the positive bundle but a different `localizedValue`, place it in a `RegistryBundle` retaining the original bundle ID, and run the same request. Generation succeeds and the document changes; no `REGISTRY_INVALID` is raised.

3. **Technical fallback totality checks only policy count; registry policy semantics are ignored.**
   - File/lines: `Stage03Generator.java:908-918`, `952-965`, `775-783`.
   - A non-empty `resolutionOrder` and unique `(policyKey, anchorKind)` are enough. The implementation never resolves a listed slot or checks `displayTemplateKey`; `technicalDisplay(...)` switches to hardcoded generic strings instead of using the selected policy. Thus a policy can be unexecutable while the fallback gate reports success, and changing a valid registry display policy has no effect.

4. **Business-term eligibility, fallback policy, and claim registry patterns are not enforced.**
   - File/lines: `Stage03Generator.java:381-420`, registry indexing `876-949`.
   - Term admission checks only anchor kind and `minimumBasisAtomIds`; `eligibleAtomKinds` and `technicalFallbackPolicyKey` are ignored. Claim admission is a hardcoded switch for three claim names/fact kinds and never evaluates `ClaimEntry.requiredAtomPatterns`, target kind, or reader template. A known term/claim with an incompatible declared basis can therefore be admitted or narrowed using implementation-specific fixture facts.
   - Repro: change a positive registry term's `eligibleAtomKinds` to a disjoint list (while retaining the unchecked digest) or give a claim a nonmatching required pattern. The positive selection still follows the hardcoded branch and can be admitted.

5. **M6 does not merge hard anchors and does not detect atom/meaning/Gap ownership conflicts.**
   - File/lines: `Stage03Generator.java:423-504`, `531-539`, `557-568`.
   - Every Flow creates new REQUEST/RECORD/RESULT objects; `relations` is always `List.of()`, and no SQL-table/FQN/typed-edge merge is attempted. Atoms are collapsed into a set, one activity receives all Flow atom IDs, and plan dispositions are generated from sets without checking exactly one semantic owner, reasoned exclusion, or cross-Flow duplicate Fact ownership. Meanings are arbitrarily selected by lexicographically smallest generated ID rather than registry priority; `conflicts` is always empty.
   - Repro: feed a two-entry Stage 02 fixture with a shared proven table/type (or two meanings for the same anchor). The code path still appends one object triplet per Flow, returns no relation/conflict, and chooses by `meaningId`, not hard-anchor/priority rules.

6. **Flow-local Gap isolation and Capsule closure are incomplete.**
   - File/lines: `Stage03Generator.java:818-853`.
   - `CapsuleContext.of(...)` adds every `stage02.flowGaps()` to every Flow's model context instead of filtering by `flow.gapIds()`/entry. `validateClosure()` checks only Flow ID, atom-ID set, and outcome-path-ID set; it does not validate proofPackId, spans, fact IDs, required atom/proof closures, or allowed-gap references. Different Flow sessions can therefore receive another entry's Gaps, and malformed proof/closure details can pass this stage.

7. **Question basis validation and R2 closure contain silent substitutions.**
   - File/lines: `Stage03Generator.java:698-713`, `364-371`.
   - If a question's response gap ID is any known context gap, `resolveQuestionGaps(...)` returns it without checking the QuestionRegistry's allowed reason codes. In R2, a one-element `retainedBasisGapIds` that does not match R1 is silently replaced with the original gap (`364-366`) instead of failing closed. These paths violate strict question provenance and the “R2 cannot expand/change evidence” contract.

8. **Formula/metric generation and field modeling are fixture-specific, not proven from atom semantics.**
   - File/lines: `Stage03Generator.java:484-490`, `590-608`.
   - Any fact of kind `AVAILABLE_FORMULA` creates the literal metric `available = onHand - reserved` with every Flow atom as basis. Fields collapse to one generic `field:proven` entry. No formula operator/operand or aggregate/time basis is checked, so another profile using the same fact kind can receive an invented formula/metric.

9. **Reader-body cleanliness is incomplete for registry-supplied display values.**
   - File/lines: `Stage03Generator.java:630-637`, registry validation `879-929`.
   - The check blocks a few ID prefixes, SHA-shaped strings, `gpt-5.6`, `.java`, and NUL, but does not reject prompt/provider/runtime tokens, arbitrary absolute/relative paths, `.xml`/`.yml` locators, or open registry display text. Since `localizedValue` is never validated and registry content digest is unchecked, a registry value such as `/repo/mapper.xml prompt provider` can reach business prose without a failure.

10. **Interpretation identity omits raw round receipts despite the documented identity chain.**
    - File/lines: `Stage03Generator.java:211-215`, `100-106`.
    - `roundReceipts` contain response SHA/runtime, but `flowInterpretationResultId`, `repositoryBusinessModelId`, and `stage03ResultId` do not include receipts. Reordering otherwise equivalent R1/R2 JSON changes receipt hashes while leaving result identity unchanged. Conversely, unverified registry content can change output under the same bundle identity.
    - Repro: the existing response-order determinism test passes because it compares only result ID/Markdown; compare `flowInterpretations().get(0).roundReceipts()` from the two results and observe different response hashes with the same Stage03 ID.

11. **Provider failures are not normalized and started/pre-start semantics are lost.**
    - File/lines: `Stage03Generator.java:168-178`, `231-250`; `Stage03Exception.java:3-14`.
    - Exceptions thrown directly by `provider.execute(...)` escape as arbitrary exceptions; there is no stable failure receipt or distinction between a pre-start failure and an empty/invalid response after `startedReceiptId`. The returned receipt also omits task/session identity. This prevents the required fail-closed operational handling and makes Provider exceptions observable as implementation details.

12. **`maxReaderItems` is never enforced.**
    - File/lines: `Stage03ResourceBudget.java:4-7`; `Stage03Generator.java:109-130`, `557-580`.
    - The budget field is declared but never read. An arbitrarily large plan can be created under a budget that explicitly limits reader items; only task input, response, document bytes, flow count, round count, and section count are checked.

13. **Information-density gates are replaced by fixed boilerplate.**
    - File/lines: `Stage03Generator.java:590-612`.
    - Positive prose does not enumerate guards/polarities, four Outcomes, fields, typed relations, or five distinct Gap questions. The renderer emits generic one-line explanations and a repeated pending-question sentence. It can satisfy heading/count tests while failing the documented chapter responsibilities and answerability/Gap visibility requirements.

14. **Technical anchors are generated from `(flowSliceId, kind)` rather than proven Stage 01/02 structure.**
    - File/lines: `Stage03Generator.java:818-840`; `generate(...)` `70-99`.
    - Stage 01 `flowView`/typed CFG nodes are not consumed. REQUEST/RESULT/RECORD/ACTIVITY/OUTCOME keys are synthetic hashes and displays are generic. This loses FQN/table/terminal identity required for safe cross-Flow merge and allows the model to select anchors not backed by a proven source node.

### P2

1. **Empty-R1 handling accepts arbitrary R2 review objects and discards them.**
   - File/lines: `Stage03Generator.java:324-338`.
   - With no R1 proposals, the parser validates only object shape and decision enum, does not require an empty review list or reject unknown proposal keys, and returns `Map.of()`. This does not currently create a meaning, but it weakens the strict two-round protocol and can hide Provider contract violations.

2. **Registry/index null or malformed nested entries can escape as raw `NullPointerException`.**
   - File/lines: `Stage03Generator.java:897-929`; public registry records generally call `List.copyOf(...)` without semantic validation.
   - `unique(...)` and `displays(...)` dereference entries/fields without a uniform stable failure wrapper. A malformed registry should produce `REGISTRY_INVALID`, not an incidental runtime exception.

## Summary

- P0: 1 finding — the returned typed plan and reader body are not semantically connected, so claimed atom/meaning/Gap conservation is false.
- P1: 14 findings — task/registry integrity, fallback/eligibility, M6 ownership/merge, R1/R2 provenance, fixture-specific semantics, body cleanliness, identity, Provider lifecycle, budgets, density, and proven anchors are incomplete.
- P2: 2 findings — weaker empty-R1 strictness and incidental malformed-registry exceptions.
- The narrow Stage03 selector is green because its scripted positive fixture matches the implementation's hardcoded synthetic assumptions and does not inspect typed reader items, registry content mutation, multi-Flow merge, or Provider failure receipts.
