# Progress: Stage 03 post-hardening integrity review

- Status: IN_PROGRESS
- Agent role: Independent Stage03 M5–M7 code-review agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Review only current Stage03 production against design and public integrity tests; write only this progress file.
- Approved inputs: scoped AGENTS instructions, `docs/stages/03-nine-section-generation.md`, `progress/stage03-code-review.md`, `Stage03IntegrityTest`, `Stage03Registries`, registry records/canonicalizer, and current Stage03 generator.
- Current branch/worktree: shared worktree; preserve unrelated parent/agent changes.

## Completed

- Created this owned progress file before review work.
- Read the source-scoped AGENTS and progress template.
- Read the complete Stage03 design, prior `stage03-code-review.md`, current `Stage03IntegrityTest`, all Stage03 public records, `Stage03Registries`, `Stage03RegistryCanonicalizer`, and the current `Stage03Generator`.
- Ran the direct Stage03 selectors after the parallel production compile completed.

## Current state

The current generator has materially closed canonical registry validation, typed plan item population, renderer consumption of typed items, task payload structure, strict empty-R1/R2 handling, fallback slot matching, provider exception normalization, and reader-item budgeting. The exact P0/P1/P2 status matrix below records remaining gaps. Because multiple P1 findings remain OPEN or PARTIAL, this review deliberately remains IN_PROGRESS and is not a complete approval.

## Changed files

- `progress/stage03-integrity-review.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest,Stage03IntegrityTest test` | PASS | Stage03 direct selectors: `Stage03IntegrityTest` 10 tests, `Stage03GeneratorTest` 13 tests, `Stage03JshErpBoundaryTest` 1 test; total 24 tests, 0 failures/errors/skips |

## Finding status

### P0

1. **CLOSED — typed reader plan is now consumed by the renderer.** `Stage03Generator.java:547-627` creates typed `PROVEN_VALUE`, `BUSINESS_TERM`, and `BOUNDED_QUESTION` items with one basis list per owned atom/meaning/gap and explicit owner maps; `Stage03Generator.java:661-711` renders only section items and their closed `ReaderSlot` values. `Stage03IntegrityTest.readerItemsOwnEveryBodyDispositionExactlyOnceAndMarkdownUsesTypedItems` passes. The separate density/boilerplate concern remains P1.13 below.

### P1

1. **PARTIAL — task payload is now a normalized Flow/Capsule, but binding detail is incomplete.** `Stage03Generator.java:722-756` emits Flow, Capsule, runtime, budget, profiles, and registry admission; `758-858` includes Flow steps/outcomes, facts/atoms/proofs, gaps, spans, and obligations. However `RegistryIndex.admissionInput` at `1222-1240` emits only eligible key arrays and fallback policy descriptors, not each term's minimum basis, each claim's required atom pattern, or child registry IDs/digests. `Stage03IntegrityTest.taskInputContainsOnlyNormalizedCapsuleDataAndFiniteRegistryAdmissionKeys` passes its presence checks but does not establish those missing bindings.

2. **CLOSED — registry content and bundle identity are recomputed.** `Stage03Generator.java:1091-1131` catches malformed content, recomputes all six canonical child digests at `1098-1113`, recomputes the enclosing bundle ID at `1114-1119`, and validates before indexing. `Stage03RegistryCanonicalizer.java:20-65` hashes canonical entry material. The stale-content, malformed-entry, eligibility/pattern, and fallback mutations in `Stage03IntegrityTest` pass with stable `REGISTRY_INVALID` behavior.

3. **PARTIAL — technical fallback slot execution is enforced, but template/registry ownership is not used.** `Stage03Generator.java:1306-1315` requires exactly one policy and a resolution-order slot matching the anchor; `971-981` executes that slot. `displayTemplateKey` is only validated by a naming regex at `1141-1155` and copied into task admission at `1234-1238`; sentence-template and section-ownership registries are never resolved by rendering. The invalid-slot mutation is rejected, but the policy's declared template still does not control output.

4. **PARTIAL — term atom-kind and claim pattern checks are present, but complete registry semantics remain unused.** `Stage03Generator.java:1243-1255` checks anchor, minimum basis, eligible atom roles, target kind, and `requiredAtomPatterns`; `1271-1298` matches patterns against allowed atom data. Yet the term fallback policy and claim `readerTemplateKey` are not enforced against templates/ownership, and no priority/selection conflict handling is present (`521-529`). The public mutation tests pass only the basic eligibility/pattern rejection portion.

5. **OPEN — M6 hard-anchor merge, relations, and conflict ownership are still absent.** `Stage03Generator.java:413-494` creates a new request/record/result object triplet per Flow, gives each activity all Flow atoms, and returns `List.of()` relations/conflicts at `491-493`; `521-529` chooses meanings lexicographically rather than by registry priority. No test or code path proves table/FQN/typed-edge merge or conflict receipts.

6. **OPEN — Capsule closure remains too shallow and Flow gaps are not isolated.** `Stage03Generator.java:1015-1041` adds every `stage02.flowGaps()` entry to every context at `1031-1033`; `1043-1049` validates only Flow ID, atom set, and Outcome path IDs. It does not verify fact IDs, proofPackId, span source/excerpt hashes, required atom/proof references, or Flow-local gap ownership. Full serialization in `798-858` is not equivalent to revalidation.

7. **CLOSED — question provenance and R2 closure no longer silently substitute.** `Stage03Generator.java:899-909` verifies question gap membership and allowed reason; `334-379` rejects non-empty R2 for empty R1, changed/expanded basis, and incomplete review sets. The integrity R2 substitution and empty-R1 negative tests pass with `MODEL_REVIEW_NOT_CLOSED`.

8. **OPEN — formula/metric semantics remain fixture-specific.** `Stage03Generator.java:474-477` creates the literal `available = onHand - reserved` whenever a fact kind is `AVAILABLE_FORMULA`, with all Flow atoms as basis; `479-481` collapses fields into one generic `field:proven`. No operand/operator/aggregate/time proof or formula-specific atom closure is checked.

9. **OPEN — body cleanliness still omits prompt/provider/path classes.** `Stage03Generator.java:653-658` and `713-719` reject a few IDs, hashes, `gpt-5.6`, `.java`, braces, and NUL only. They do not reject arbitrary prompt/provider/runtime tokens, absolute/relative paths, `.xml`/`.yml` locators, or unsafe registry display text. Since template/term text is only checked for blankness/limited tokens (`1161-1172`), leakage remains possible.

10. **OPEN — interpretation/result identity still omits round receipts and full interpretation lineage.** `Stage03Generator.java:210-218` computes the Flow interpretation ID from task ID, meanings, dispositions, gaps, and fallbacks but not `roundReceipts`; `103-105` computes Stage03 ID from model/plan/document/budget, and `488-490` computes the repository model from selected projections rather than all interpretation receipts. The existing determinism test checks equal IDs/Markdown for reordered responses but does not assert receipt-sensitive identity.

11. **PARTIAL — provider exceptions are normalized, but lifecycle receipt semantics are incomplete.** `Stage03Generator.java:234-243` maps provider RuntimeExceptions to stable `MODEL_RESPONSE_INVALID`, and transport checks are at `246-261`. `ModelRoundReceipt` creation at `263-265` retains response SHA, started receipt, and observed runtime, but no task/session identity or explicit pre-start vs post-start failure receipt is represented; there is no durable failed-round result because `Stage03Result` is success-only.

12. **CLOSED — `maxReaderItems` is enforced.** `Stage03Generator.java:607-608` counts all section items and fails with `RESOURCE_LIMIT_EXCEEDED`; the constrained-budget integrity test passes.

13. **OPEN — information density is still mostly fixed scope boilerplate.** `Stage03Generator.java:557-563` inserts one fixed `READER_SCOPE_V1` item into every section, and `647-650` supplies nine fixed sentences. Atom/meaning/gap items now exist, but there are no typed items for four Outcome paths, formula/field roles, hard-anchor relations, polarity/guard explanation, or distinct Gap-question content. The density requirement in design §9.6 and the prior review remains unmet even though the renderer is no longer bypassing the plan.

14. **OPEN — technical anchors are still synthetic Flow/kind hashes.** `Stage03Generator.java:1034-1038` derives every anchor from `flowSliceId + kind`; the generator never calls the public Stage01 `flowView` seam or binds Java type/table/terminal identities. Cross-Flow hard-anchor merge therefore cannot be proven safe.

### P2

1. **CLOSED — empty R1 requires empty R2.** `Stage03Generator.java:339-343` now rejects any non-empty reviews array when R1 has no proposals. The dedicated integrity regression passes.

2. **PARTIAL — generator registry indexing normalizes malformed input, but construction/freeze can still throw unchecked exceptions before `generate`.** `Stage03Generator.java:1091-1131` catches canonicalizer/index runtime failures and emits `REGISTRY_INVALID`; `1188-1199` rejects null entries. However public record constructors with null list elements can throw from `List.copyOf`, and `Stage03Registries.freeze`/canonicalizer (`Stage03Registries.java:12-34`, `Stage03RegistryCanonicalizer.java:171-203`) can expose `IllegalArgumentException` before the generator seam receives a bundle. The generator-side malformed-entry integrity test passes, but full public-construction normalization is not established.

## Decisions

- No production, test, fixture, or design edits are authorized in this review.
- Any remaining P0 or P1 finding keeps the final review status non-COMPLETE; findings must be recorded before verification is summarized.

## Blockers

- Review execution is complete and direct selectors pass, but approval is blocked by the OPEN/PARTIAL P1 findings recorded above. No test, fixture, production, or design edit is authorized in this review task.

## Exact next action

Production implementation must address the OPEN/PARTIAL P1 findings (especially hard-anchor merge/relations, full Capsule closure and Flow-local gaps, complete registry bindings/templates/ownership, formula semantics, body cleanliness/density, proven anchors, and identity/lifecycle receipts), then rerun the same Stage03 direct selectors. This review must remain IN_PROGRESS until no P0/P1 finding remains.

## Resume checks

- Re-read this file, run `git status --short`, and ensure all subsequent writes remain confined to this progress file.
