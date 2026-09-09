# Progress: fact-registry-test-ruling

- Status: COMPLETE
- Agent role: Bounded design-authority test-contract interpretation
- Model: gpt-5.6-sol / ultra (configured by the root for this bounded design-authority task)
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: Decide the minimal legal migration of `FactCandidateRegistryDeterminismTest` under the published closed v3 Fact registry, check the directly related Step 05 exact-call signal-count consistency, and apply the root-authorized bounded correction only in Step 05 §8.1.2/§8.6; no implementation change.
- Approved inputs: Applicable `AGENTS.md` files, `progress/TEMPLATE.md`, Step 04 §8.0 and §8.0.2, Step 05 §8.1.2 and §8.6, named registry/candidate/test/projection sources and real fixtures, root-reported targeted-test results, and the root's explicit authorization to apply only the accepted Step 05 normative count correction.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code` (identity supplied by the root; Git calls are out of scope).

## Completed

- Read the applicable repository, backend-agent, and source-code instructions plus the progress template.
- Read Step 04 §8.0 and §8.0.2 and the named registry/candidate/test sources.
- Confirmed the two errors arise before assertions because the test invents boundary candidate keys while the v3 candidate union requires the published boundary key.
- Settled the registry test migration: use the real `JAVA_BOUNDARY_INVOCATION` and `JAVA_EXACT_CALL` templates with the existing standard two-entry fixture; both families contribute, complete/reduced counts become 6/2, and the atom-order test keeps the canonical boundary key while reversing only its full declared atom list.
- Read Step 05 §8.1.2/§8.6, the standard/shared fixture source shapes, the exact-candidate count assertions, and the existing exact-call Flow RED.
- Confirmed the standard fixture owns two admitted exact-call tuples per entry (Controller→Service and Service→interface), while the shared fixture owns two for the caller and one for the callee.
- After the root accepted the ruling and explicitly authorized the bounded docs-only change, corrected only the Step 05 §8.1.2 count/fallback/cutover sentences and §8.6's related migrated standard/shared/counter oracles.

## Current state

Both bounded rulings are complete. The Step 04 production constraint matches the published closed v3 family identity, so only the stale test must migrate. Separately, Step 05's exact-call projection rule requires every admitted exact tuple to produce one `EXPLICIT_CALL`, suppressing only a boundary `EXPLICIT_CALL` with the same `(entryId, invocationCallId, anchorKey)`. The standard fixture therefore becomes 4/4 signals, not 3/3; the shared caller/callee fixture becomes 4/3. Keeping 3/3 would require silently dropping the unmatched Controller→Service exact call and would contradict §8.1.2's first bullet and the cross-Flow handoff purpose.

The root accepted this ruling and authorized the exact Step 05 §8.1.2/§8.6 docs-only correction. That correction is applied and statically self-checked; production GREEN remains paused for root review and docs-only publication.

## Changed files

- `docs/analysis-steps/05-business-flows.md`
- `progress/fact-registry-test-ruling.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Narrow `rg`/`sed`/`nl` inspection only | PASS | Published v3 has exactly boundary, guard, and exact-call families; `FactCandidateSet` rejects a boundary candidate key other than `JAVA_BOUNDARY_INVOCATION`. |
| Root-reported targeted selector after schema-literal correction | ESTABLISHED INPUT | 3 tests: atom-order passes; template-order and deletion error at the pre-existing boundary-key check. |
| Standard fixture source plus `FactCandidateExactPathTest` | PASS (static/current asserted evidence) | Two entries each traverse Controller→Service and Service→interface; current test asserts 2 boundary + 4 exact candidates and no not-applicable disposition. |
| Shared fixture source plus exact-call Flow RED | PASS (static/current asserted evidence) | Three exact Facts: caller-only Controller→Service target plus the Service→interface call owned by both entries; the RED requires one exact `EXPLICIT_CALL` per owned exact Fact. |
| Step 05 §8.1.2/§8.6 consistency comparison | DESIGN CORRECTION REQUIRED | Exact priority deduplicates only a matching boundary tuple; it cannot remove a distinct internal exact tuple. Existing target 3/3 statements conflict with that rule. |
| `nl -ba` exact review of Step 05 lines 415–428 and 540–552 | PASS | Only the authorized §8.1.2 and §8.6 paragraphs contain the new rule/count wording: standard/early-return guarded 4/4, shared 4/3, counter 5/4. |
| Narrow `rg` count/version/output/trailing-whitespace checks | PASS | Old v3 whole-Flow 3/3 preservation wording is gone; the v2 boundary-only baseline is explicitly scoped, all four schema versions and the five/six output count remain unchanged, and neither changed file has trailing whitespace. |
| Maven/Java tests | NOT RUN (by scope) | This work changes documentation/progress only; root explicitly prohibited Maven and no production/test source was edited. |

## Decisions

- Do not weaken the production closed-union validation, add an API, retain a compatibility key, or amend the normative design merely to admit synthetic test keys.
- Prefer boundary+exact on `ProgramGraphsPublicFixture.create(...)` over boundary+guard: it reuses independently asserted 2+4 current fixture behavior, covers the new v3 family, and requires fewer test changes.
- For template-order, reorder only the two canonical template records and retain all four identity/semantics/disposition/denominator equality assertions; add an explicit assertion that both canonical keys contributed.
- For template deletion, rename away from “entry-boundary,” expect complete=6 (2 boundary, 4 exact) and reduced=2 (boundary only), assert surviving boundary candidates/keys are unchanged, and retain candidate-set identity inequality plus empty not-applicable denominator.
- For required-atom order, retain the existing reversal and identity-inequality assertions, but obtain the boundary template from `standardJavaFacts()` by canonical key; changing atom order remains semantic while no new candidate key is introduced.
- No Step 04 normative amendment or production change is required: §8.0 fixes the boundary/guard candidate keys and §8.0.2 adds exactly one `JAVA_EXACT_CALL` family while preserving the two prior variants. No clause permits `*_FIRST`/`*_SECOND` aliases.
- The minimal Step 05 normative correction is exactly the two statements that currently claim the whole-Flow 3/3 count survives v3:
  - In §8.1.2's fallback/count bullet, replace the whole-Flow count claim with: an exact Fact matching an existing boundary tuple changes only that tuple's selected basis/ID, so the boundary-associated triplet (`JAVA_TYPE_ANCHOR` + one prioritized `EXPLICIT_CALL` + `EXTERNAL_EFFECT_GAP`) remains; every other admitted exact tuple remains independently projected and adds one `EXPLICIT_CALL`.
  - In §8.6's v3 cutover bullet, replace “the existing 3/3 fixture continues the original 3/3 assertion” with preservation of that boundary-associated triplet plus all additional exact tuples. State the resulting current fixture oracles: standard and early-return guarded=4/4, shared caller/callee=4/3, and exact `if/else` counter approve/cancel=5/4. All admitted exact Facts project exactly once.
- The adjacent §8.6 3/3 statement remains accurate only as the explicitly pre-v3 boundary-only baseline; it must not be read or retained as the migrated v3 whole-Flow oracle. No broader normative section needs rewriting for this count correction.
- This Step 05 correction changes no source fixture, Step 03 graph, Fact registry, signal kind/wire/schema, priority tuple, external-effect/counter behavior, public artifact count, or Step 06 contract. It corrects only the documented/tested cardinality implied by existing v3 inputs and rules.
- Direct test expectations affected by the Step 05 cutover include the standard/early-return-guarded `processJoinSignals` size 3→4 and kind multiset to two `EXPLICIT_CALL` values; the exact `if/else` counter fixture becomes 5/4, and the shared positive should assert caller/callee totals 4/3. Any helper keyed only by `signalKind` must compare exact calls by tuple/Fact instead. A standard full-v3 Flow also owns three Facts per entry (one boundary + two exact), six unique Facts across both entries, rather than the old boundary-only 1/2 counts.

## Blockers

- None. Step 05 GREEN remains intentionally paused until the root reviews and publishes this bounded design correction under the repository gate.

## Exact next action

Root selectively reviews/stages the Step 05 §8.1.2/§8.6 hunks and this progress note, publishes the docs-only correction, then resumes the already-approved every-exact-tuple Step 05 GREEN and migrated test oracles.

## Resume checks

- Re-read this completed ruling plus Step 04 §8.0/§8.0.2 and Step 05 §8.1.2/§8.6 before changing either contract.
- Do not run Git, Maven, Java, network, or broad audit commands.
- Make no further edits unless the root reopens this completed ruling; preserve the two recorded changed files exactly for selective review/publication.
