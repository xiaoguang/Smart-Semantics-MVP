# Progress: fact candidate enumerator review

- Status: COMPLETE
- Agent role: Luna/xhigh bounded code reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Review only the current Proven Code Facts M1 candidate-enumeration slice. Do not modify production code, tests, fixtures, design documents, POM, or Git state.
- Approved inputs: `docs/analysis-steps/04-proven-code-facts.md` §8.0/M1 and §8.0.1, the scoped `AGENTS.md`, the M1 production files, the public M1 test, and its three canonical fixture files.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`.

## Completed

- Read the scoped instructions and the M1 candidate-enumeration contract before reviewing the implementation.
- Reviewed `FactCandidateEnumerator.java`, `FactCandidateSet.java`, `FactCandidateEnumeratorTest.java`, and the three M1 JSON fixtures at the exact current revisions.
- Compared the implementation and test assertions against the required boundaries: generic Java boundary only, actual graph material, registry-owned template and atom shape, registry atom order, fail-closed malformed input, and the exact public M1 package.
- Did not run Maven: the existing targeted GREEN result was available in the owned Terra progress record, and a fresh build was not essential to this read-only review.

## Current state

The slice is a valid narrow GREEN seam for parsing one reduced graph-index specimen and producing a deterministic in-memory list of generic boundary candidates. It is **not** contract-complete M1 and must not be promoted as a complete candidate denominator or as evidence that a real repository graph has been matched to an entry.

## Findings

### P0

None found in this slice. The implementation does not itself admit a `CodeFact`, construct a `Proof`, or claim an external SQL/message/search/API effect. The principal risk is false or untraceable candidate enumeration, which remains upstream of admission but is still a P1 correctness issue.

### P1-1 — Candidate applicability is an aggregate cross-product, not a graph-supported relation

- Location: `FactCandidateEnumerator.java:56-75`, with material reduced by `parseNodes` at lines 125-147 and `parseEdges` at lines 149-170.
- Evidence: once any `JAVA_BOUNDARY_INVOCATION` node exists, the loop creates one candidate for every parsed entry, every registry template, and every boundary node. The parser records only booleans such as “some call site exists”, “some call target exists”, and “some argument-to-boundary edge exists”. It never reads edge endpoints, call-site ownership, entry ownership, target identity, argument ordinal/origin, control context, or evidence-node linkage.
- Impact: an unrelated boundary node can be assigned to an unrelated entry and still be emitted as if it were a candidate for that flow. A later Proof builder cannot recover the missing relation from `subjectNodeIds` because the candidate does not carry the callsite or required edge IDs. This violates the M1 requirement that candidate enumeration consume actual graph material and can inflate or misassign the Fact denominator.
- Minimal follow-up test: add a two-entry fixture containing two boundary nodes and two disjoint call/data edge groups, then assert that each entry receives only its own applicable boundary candidate. Add a negative mutation with a boundary node lacking an entry-owned call/data/evidence closure and require either no candidate plus an explicit Gap disposition or a fail-closed `GRAPH_MATERIAL_INSUFFICIENT` error; never accept the cross-product.
- Minimal implementation slice: parse the existing persisted graph endpoint/ownership records into a `BoundaryMaterial` keyed by entry, callsite, and required edge IDs; instantiate a registry template only when its required graph roles are joined to that exact material. Do not infer external effects.

### P1-2 — The graph-index admission check accepts incomplete or unrelated graph descriptors

- Location: `FactCandidateEnumerator.java:108-123` and `GraphMaterial.requireBoundaryMaterial()` at lines 301-309.
- Evidence: `parseGraphKinds` checks only that descriptor IDs and `graphKind` values are distinct and that descriptor fields are nonblank. It does not validate the descriptor artifact type/schema against its graph kind or require the five graph kinds expected by the ProgramGraphs publication. `requireBoundaryMaterial` requires only `DATA_FLOW`, `EVIDENCE`, one CALL-owned node, one CALL-owned `CALL_TARGET`, and one DATA_FLOW-owned `ARGUMENT_TO_BOUNDARY`; it does not require a CALL graph descriptor, CONTROL graph, structure graph, or the graph-index references to those publications.
- Impact: a fabricated or incomplete index can pass with two unrelated descriptors, as the current fixture demonstrates. M1 may advertise candidates without the graph roots required by M2 Proof and without enough material to establish the required atom roles.
- Minimal follow-up test: mutate the fixture by removing each required graph descriptor, replacing a descriptor artifact type/schema, and supplying only the boolean-like node/edge catalog entries. Each mutation must be rejected with the stable input/reference failure, not produce candidates. Add a positive fixture containing the complete five-graph index and assert the exact graph-root set is retained.
- Minimal implementation slice: validate the exact ProgramGraphs graph descriptor set and schema/type pairing, then carry the graph artifact references/roots into the candidate-set wire. Do not treat presence of a node or edge kind as a substitute for the referenced graph publication.

### P1-3 — The public result is missing candidate-set identity, denominator accounting, and upstream roots

- Location: `FactCandidateSet.java:9-29` and `FactCandidateEnumerator.enumerate(...)` at lines 48-75.
- Evidence: the public result contains only `List<FactCandidate>`. It has no `candidateSetId`, graph IDs/roots, denominator record, upstream publication references, schema version, or publication controls required by §8.0.1. The implementation also has no ModuleArtifact envelope or persisted M1 artifact path.
- Impact: M2 cannot verify that the candidate set came from the exact reopened ApplicationDiscovery/ProgramGraphs publications, cannot distinguish a complete zero-candidate result from a silently omitted denominator, and cannot independently reopen the M1 output. The current in-memory list can be deterministic while still being untraceable or substituted between modules.
- Minimal follow-up test: serialize and fresh-reopen the M1 `ModuleArtifact` using a root-independent path; mutate a graph root, candidate-set identity, or denominator ID set and require rejection. Assert the exact candidate/atom denominator equations and the required upstream references are present.
- Minimal implementation slice: add the contract-defined immutable wire envelope and identity inputs around the existing candidate list, without adding Proof or external-effect fields. Install the module artifact only after canonical validation; keep M2 dependent on the reopened artifact rather than the Java object.

### P2 — Empty registry and direct public-record construction have weak fail-closed behavior

- Location: `FactCandidateEnumerator.java:173-195` allows an empty `factTemplates` array; `FactCandidateSet.java:39-50` does not validate each `RequiredAtom` element or reapply role/value/evidence allowlists when constructed directly.
- Impact: a valid-looking graph with a missing/empty registry can silently return zero candidates, and malformed direct record construction can throw an unnormalized `NullPointerException` or carry values outside the registry allowlists. This is less severe than the missing graph join because JSON enumeration currently validates the nested registry, but it leaves the public seam inconsistent.
- Minimal follow-up test: decide from the profile contract whether an empty registry is a valid “no applicable templates” result; if not, reject it with `FACT_PROFILE_INVALID`. Add direct-constructor null/unknown-role/value/evidence tests and normalize them to the same stable failure family.

## What the existing test proves

`FactCandidateEnumeratorTest` does establish a few useful properties:

- It reads canonical fixture bytes through `CanonicalJsonCodec.parseCanonical`, then invokes the public class in `org.sourceanalysis.app.analysis.fact.candidates`.
- Calling the same inputs twice produces byte-equal serialized records.
- The reduced fixture yields one generic `JAVA_BOUNDARY_INVOCATION` candidate with the expected candidate key/kind.
- The required atom names include the key invocation/target/argument/origin/control/evidence concepts.
- The serialized result does not contain the explicitly forbidden strings `SQL_EXECUTION`, `SQL_UPDATE_EFFECT`, or `EXTERNAL_EFFECT`.

## What the existing test does not prove

- It does not prove that a candidate's boundary node, callsite, target edge, argument edge, evidence, and entry belong to one actual graph path; the fixture contains only a reduced catalog and no endpoints.
- It does not prove all five graph kinds, descriptor type/schema pairing, graph roots, or persisted publication closure.
- It does not prove registry ownership: there is only one template, no template deletion/replacement mutation, and no assertion that the exact registry atom order (rather than merely atom-name presence) is preserved.
- It does not assert exact atom count, role, value type, or expected evidence-kind arrays; the `contains(...)` assertion could pass with extra, duplicate, or reordered atoms.
- It does not cover multiple entries, multiple boundary nodes, unrelated decoys, ambiguous/unsupported graph material, empty registries, duplicate IDs, unknown fields, wrong schema versions, or closure mutations.
- It does not prove a candidate-set identity, denominator/accounting equation, upstream-root binding, ModuleArtifact envelope, fresh reopen, or root-independent persistence.
- It does not test that malformed input fails closed, nor that a Proof deletion/graph mutation leaves enumeration unchanged while later Proof admission rejects it.
- It does not test the public records' direct-construction validation independently of the JSON parser.

## Decisions

- Treat this as a bounded candidate-list GREEN only; do not relabel it as complete M1 or as a real DepotHead graph match.
- Preserve the generic boundary rule: even after the graph join is corrected, this module may describe a Java invocation and Java-local argument provenance only. XML/SQL structure and external effects remain outside this candidate Fact and must not be promoted here.
- The smallest next work is the P1-1 graph-join RED/GREEN slice, followed by the P1-2 graph publication/root checks. Identity and persisted M1 publication can then be added without changing the candidate semantics.

## Blockers

- No blocking condition for this review. The implementation can proceed to the next bounded RED/GREEN slice, but the listed P1 findings must remain visible in the implementation audit.

## Exact next action

- Coordinator should hand the P1-1 two-entry/two-boundary graph-join test to the Sol/ultra design authority for confirmation of the existing ProgramGraphs endpoint wire, then to Terra/xhigh for the smallest implementation change. Do not change the eight-step architecture or external-boundary rule.

## Resume checks

- Read this progress file and the exact M1 contract before any follow-up.
- Confirm only the candidates package, its fixtures/tests, and this review progress are in scope.
- Re-run the recorded narrow selector only after a production change; do not infer full M1 acceptance from the current one-test pass.
