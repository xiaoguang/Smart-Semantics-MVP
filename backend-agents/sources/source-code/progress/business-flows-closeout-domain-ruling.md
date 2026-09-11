# Progress: business-flows-closeout-domain-ruling

- Status: COMPLETE
- Agent role: Bounded Step 05 design-authority classification-gap analysis
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Identify the smallest honest finite, source-provable domain-classification source/rule needed to satisfy the existing Step 05 domain-specific signal acceptance; analysis only, with no design, code, test, schema, fixture, source, runtime, Git, Maven, or network change.
- Approved inputs: Applicable instructions and skills; Step 05 §8.1.1/§8.6; `progress/process-signal-capability-audit.md`; current persisted CodeStructure, MyBatis mapper catalog, Fact/Proof, Flow signal, and directly relevant frozen fixture shapes; root-supplied branch/task facts.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-closeout` at `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code` (supplied by root; no Git command was run).

## Completed

- Read the applicable instructions plus the brainstorming, codebase-design, and systematic-debugging guidance and kept the work to a read-only contract-gap probe.
- Read Step 05 §8.1.1/§8.6 and the prior process-signal capability audit.
- Inspected only the directly relevant persisted Mapper catalog, CodeStructure/Call/Evidence graph, Fact registry/candidate/proof registry, and `ProcessJoinSignalV1` fields plus their frozen unit-fixture evidence.
- Separated the source-provable statement, “this entry-rooted exact call reaches this static MyBatis SQL table,” from the unavailable semantic statement, “this table is domain-specific rather than audit/log/infrastructure material.”
- Returned the bounded interim result to root and recorded the two honest authority choices below.

## Bounded ruling

### What is already source-provable

Current persisted public graph material is sufficient, without a Mapper-catalog or ProgramGraphs schema change, to prove this exact per-entry path:

1. An entry-owned Call graph `CALL_SITE` node.
2. Its `CALL_TARGET` edge with `resolution=EXACT` and `ruleId=java-static-field-receiver-call-v1` to a CodeStructure `METHOD` node.
3. A `JAVA_METHOD_TO_XML_STATEMENT` edge with `resolution=EXACT` and `ruleId=mybatis-namespace-signature-binding-v1` from that same method ID to an `XML_STATEMENT` node.
4. A CodeStructure `STATEMENT_CONTAINS_SQL` edge with `ruleId=sql-update-table-v1` to a `SQL_TABLE` node whose exact `canonicalValue` is the table anchor key.
5. Source-excerpt and rule-application Evidence for each required node/edge through the public Evidence graph.

The Mapper catalog supplies the Java-interface/method and XML namespace/statement candidates used upstream to build the exact mapper edge. Its persisted `javaInterfaceFqn`, method `signature`, XML namespace, `statementId`, `statementKind`, excerpts, and `bindingState=CANDIDATE_NOT_YET_BOUND` do not themselves assert domain meaning. The exact binding result is already retained in the Call graph, so Step 04 need not re-read or reinterpret Mapper XML.

The candidate must be scoped from the entry-owned call site and the exact path, not from `SQL_TABLE.owningEntryIds`: current CodeStructure construction gives SQL nodes the discovery entry set, which is not proof that every Flow uses that table. Static parsing is also deliberately finite: the current source rule proves only a static `UPDATE <table>` target (and separately the first `SET` column); dynamic SQL is a Gap. Therefore the minimum honest source fact is a single per-entry static-MyBatis-table-reference family. It proves a reference only, never statement execution, write success, external effect, state production, business order, or business object identity.

### What is not persisted

There is **no current typed field** on `SQL_TABLE`, `XML_STATEMENT`, mapper catalog records, program edges, Facts, atoms, Proofs, Evidence, or `FlowCompilationProfile` that classifies an exact anchor key as `DOMAIN_SPECIFIC` versus generic technical material. In particular:

- `ProgramNode.kind=SQL_TABLE` plus `canonicalValue` proves syntactic table identity, not whether the table is business, audit, log, tenancy, migration, or other infrastructure material.
- Mapper `statementKind` and the exact Java→XML binding prove mapper structure; they do not supply business classification.
- Current `FactRegistry` v3 has only boundary, guard, and exact-call families; `ProofRuleRegistry` v3 has no SQL-table/path subject categories.
- Current Proof closure accepts program subject Evidence only. It has no persisted, content-addressed invariant/profile reference from which a user-approved domain classification could be audited.
- Repository ownership, Java/XML names, raw source text, and table-name vocabulary cannot fill this field under the existing trust rules.

Consequently, current frozen graph material alone cannot honestly emit `SQL_TABLE_ANCHOR / SQL_TABLE / <exact key> / REFERENCES / DOMAIN_SPECIFIC / STATIC_STRUCTURE`. Treating every SQL table, every mapper update, or a name-shaped subset as domain-specific would add an unstated semantic invariant and could promote audit/log/infrastructure tables.

## Evidence-backed closeout choices

### Option A — preserve the existing §8.6 domain exit criterion (recommended)

Add the one source-only table-reference Fact described above, and separately accept a finite, content-addressed **user-approved exact table classification** input. Its minimum useful rule is exact membership on `(anchorKind=SQL_TABLE, anchorKey=SQL_TABLE.canonicalValue)`; no trim, case fold, suffix/prefix match, vocabulary inference, or repository-name fallback is legal. A matched entry authorizes only `DOMAIN_SPECIFIC`; an unclassified table remains a classification Gap and, if retained for audit, at most a `GENERIC_TECHNICAL` table signal. It never becomes a sequence or effect proof.

The Fact/Proof chain proves the entry-to-table structural reference; the user invariant proves only specificity. Keeping those authorities separate avoids falsely presenting a business classification as source Evidence. The resulting Step 05 signal is exactly `SQL_TABLE_ANCHOR / SQL_TABLE / <persisted canonical table> / REFERENCES / DOMAIN_SPECIFIC / STATIC_STRUCTURE`, with the source Fact/atom/Proof/Evidence/locator closure required by §8.1.1. It does not authorize `STATE_*`, `BUSINESS_OBJECT_*`, `PRODUCES`, or an external write claim.

This is the smallest trust-preserving route that leaves the current domain-specific acceptance intact, but it requires explicit user-level invariant authority and extra business input. No such authority exists in the current persisted artifacts.

### Option B — provide no extra business classification input

Retain the exact source-proved table reference as a Gap/generic structural signal and defer business semantics to Step 06's frozen R0/P1/P2 interpretation path. A shared generic table remains audit context only: it cannot create `SHARED_ANCHOR`, cannot prove sequence or causality, and cannot be promoted by name similarity.

This option is implementable without pretending a missing classifier exists, but it **does not satisfy the current Step 05 §8.6 domain-specific exit criterion**. Choosing it therefore requires explicit user acceptance of a normative change to that exit criterion; it is not an implementation discretion. All other Step 05 entry/Flow/Capsule bijection, ownership, Outcome, Fact/Proof/Evidence/source, Gap, signal, shard, eligibility, deletion-mutation, and 0/0 closure acceptance remains mandatory.

### Rejected source-only shortcuts

- Classify every `SQL_TABLE` or every `UPDATE` target as domain-specific.
- Infer domain status from table, mapper, method, parameter, Java type, route, or Chinese names.
- Use repository ownership or Mapper-catalog membership as a business classifier.
- Call a static SQL reference a state transition, executed effect, or business object.

Each shortcut exceeds what the persisted fields prove and conflicts with the existing generic-material guardrails.

## Contract and version impact if Option A is authorized

- **Unchanged upstream:** ApplicationDiscovery/Mapper catalog and ProgramGraphs public schemas; their current nodes, edges, IDs, resolutions, rule IDs, Evidence IDs, and source locators already carry the structural path.
- **Step 04 semantic cutover:** one additional closed Fact candidate family and its per-entry denominator; table-path candidate fields; Proof subject categories/allowances for the existing call, mapper-binding, XML-statement, SQL-table edge/node rules; accounting partition and all readers/publishers. Because v3 is the closed boundary/guard/exact-call registry, this is a v4 FactRegistry/ProofRuleRegistry/M1/M2 and four-public-ledger cutover, not a reinterpretation of v3 bytes.
- **Classification authority:** a finite exact-key mapping must be content-addressed, fresh-reopened, identity-bound, and transitively retained by the Flow publication. The smallest placement is the existing Flow compilation profile boundary (or an exact equivalent invariant reference); the current profile contains only limits and a caller-supplied `profileRef`, so it cannot yet carry or verify the mapping. This is a contract addition, not a raw-source parser or model subsystem.
- **Step 05 semantic cutover:** M1 must consume the new Step 04 publication plus classification input and project the existing `ProcessJoinSignalV1` fields; M2/Capsule and M3/public readers must preserve the new signal and its closure. Following the already frozen v3/v6/v3/v4 line, this semantic addition requires the next versions (M1 v4, M2 v7, public flow-slices v4, public evidence-capsule v5); unchanged v1 coverage/disposition/gap shapes need no field change. No new signal kind or `ProcessJoinSignalV1` field is required if the classification authority is durably bound at Flow-compilation level.
- **Consumers:** Step 04 candidate/proof/public-ledger readers and publishers; Step 05 persisted-input reader/compiler/profile, Capsule projector/publisher, and public publisher/readers; Step 06 only needs the bumped BusinessFlows artifact versions and can continue using the existing exact `DOMAIN_SPECIFIC SQL_TABLE_ANCHOR` pair rule. No Provider, runtime, graph subsystem, all-sixteen-family expansion, or output-artifact-count change is implied.

These version numbers describe the minimum honest coordinated replacement if the new semantic input is authorized; no version or design document was changed in this task.

## Impact if no authority is granted

The current exact-call/shared-source/counter/external-effect work can finish unchanged, and the static table path may later become proof-backed generic/Gap material. However, Step 05 cannot be declared fully accepted under the present §8.6 wording because there is no non-forged domain-specific signal. This limitation blocks only the domain acceptance and Step 06 `SHARED_ANCHOR` route; it does not invalidate proof-closed exact-call `PROVEN_HANDOFF`, R0 pending-only semantic cues, or the remaining mandatory Step 05 accounting and isolation guarantees.

## Changed files

- `progress/business-flows-closeout-domain-ruling.md`

## Verification

| Check | Result | Key output |
| --- | --- | --- |
| Applicable instructions/skills and progress template | PASS | Read-only bounded ruling; one owned progress file; no subdelegation. |
| Step 05 §8.1.1/§8.6 and prior capability audit | PASS | Current domain family is explicitly absent; positive signals require same-Flow Fact/Proof/Evidence/source closure. |
| Mapper catalog and CodeStructure/Call/Evidence graph records/builders/fixtures | PASS | Exact call→mapper method→XML statement→static update table path and Evidence are persisted; no domain-classification field exists. |
| Fact candidate/registry/proof registry and Flow signal/profile records | PASS | v3 Fact/Proof taxonomy lacks a table family and classification authority; the existing signal wire can carry the proposed table anchor. |
| Git, Maven, JVM, network, Provider, customer-source scan, design/code/test edits | NOT RUN | Explicitly prohibited. |

## Decisions

- No already-persisted typed domain classifier exists.
- Recommend Option A only if the user explicitly supplies/approves the finite exact-table invariant; source proof and semantic authority remain separate.
- Otherwise use Option B and amend the §8.6 exit criterion explicitly; do not claim current acceptance passed.
- Do not infer business status, effects, object identity, sequence, or causality from the structural table path.
- Generative model/product-content use: none.

## Blocker / required authority

The remaining choice is user-owned: either provide/authorize a finite exact SQL-table domain mapping, or authorize the narrower Step 05 acceptance that leaves classification to later frozen interpretation. Implementation cannot choose between these meanings.

## Exact next action

Root should ask the user for that one authority choice while continuing the already-approved Step 05 exact-call/shared-source/counter work, which is unaffected. Do not start the domain implementation or amend §8.6 until the choice is explicit.

## Resume checks

- This ruling is complete; do not reopen it as an architecture round.
- Do not edit `progress/fact-registry-test-ruling.md`.
- Preserve all non-owned worktree changes.
- No implementation is authorized by this analysis.
