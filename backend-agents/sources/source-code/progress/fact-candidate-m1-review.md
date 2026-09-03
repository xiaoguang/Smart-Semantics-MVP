# Progress: Fact candidate M1 review

- Status: COMPLETE
- Agent role: Luna/xhigh independent code reviewer
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Read-only review of the uncommitted M1 candidate enumerator, persisted input reader, typed records, exact-path tests, missing-path tests, and test-only graph fixture.
- Approved inputs: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/04-proven-code-facts.md`, both implementation plans, `progress/proven-code-facts-delivery.md`, and current uncommitted M1 files.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts`

## Completed

- Read scoped engineering rules and the authoritative M1 contract.
- Reviewed all current files in `analysis/fact/candidates`, the exact-path and persisted-mutation tests, and `ProgramGraphsPublicFixture`.
- Compared the implementation with the six exact-join obligations, lineage/reference checks, hash/identity rules, denominator rules, and the no-external-effect boundary.

## Current state

- The typed M1 seam is a meaningful replacement for the former raw JSON cross-product and its positive two-entry/two-boundary test is appropriately persisted/public-wire based.
- M1 is not ready for acceptance: several persisted mutations can still produce an apparently applicable candidate or preserve the same candidate-set identity after candidate semantics change.

## Findings

### P1 — graph edge endpoints are not closed before exact joining

- `PersistedFactCandidateInputReader.parseProgramEdges` accepts `fromNodeId` and `toNodeId` as IDs but never checks that they exist in the graph's `nodes` map. `FactCandidateInputs.PublicProgramGraph` checks map-key equality and owner subsets only; it does not check edge endpoints.
- `FactCandidateEnumerator.exactPath` then treats a `CALL_TARGET` edge as exact when it has the right kind, resolution, and `fromNodeId`, regardless of a ghost `toNodeId`. It similarly accepts an `ARGUMENT_TO_BOUNDARY` edge when its endpoints match the variant IDs, even when the argument node is absent from the data graph.
- This is a direct violation of the M1 exact endpoint closure and can emit an applicable candidate from a mutated persisted graph rather than `NOT_APPLICABLE`/fatal. Minimal RED: mutate only the call-target `toNodeId` or argument `fromNodeId`, update the mutated payload/index, and require the affected entry-boundary combination not to be applicable while the other entry remains applicable. Minimal fix: validate every graph edge endpoint (and guard endpoint) against that graph's nodes before enumeration, or make the relation missing and produce the scoped disposition according to the M1 contract.

### P1 — graph/profile/source reference fields are parsed but not cross-validated

- `parseProgramGraph` requires `graphProfileRef`, `graphId`, and the graph header fields syntactically, but does not read `graphProfileRef` or `graphId` into `ParsedProgramGraph`. `validateGraphIdentity` compares only snapshot, application profile, and entry IDs. `validateIndex` checks only descriptor `graphKind` and `artifactRef`; descriptor `fileName`, `artifactType`, `schemaVersion`, and `graphId` are not compared with the actual payload.
- `parseDiscovery` requires `capabilityProfileRef`, `sourceInventoryRef`, and `verifiedSnapshotRef` in `application-profile.json` but never compares them with the supplied verified-source publication or with the capability/step lineage. `validateMapperCatalog` also validates syntax but not its advertised catalog/coverage relationship.
- A same-run/same-snapshot publication set can therefore mix a different graph profile, graph IDs, or a discovery payload pointing at a different source inventory while M1 still reaches the enumerator. This weakens the fatal `PROOF_PACK_REFERENCE_BROKEN` gate and makes a fresh reopen not a complete lineage check. Minimal RED: mutate each of graph profile ref, graph ID/index descriptor, and discovery source/verified-snapshot ref while preserving bytes/descriptor integrity; require `reopen` to fail with the stable fatal code. Minimal fix: retain and compare all profile/root/reference fields against the expected publication references and index descriptors.

### P1 — source excerpt hash and locator invariants are not verified

- `validateSourceExcerpt` checks that `rawUtf8Sha256` parses as a `Sha256Digest`, but does not hash `rawUtf8` and compare the result. `validateLocator` checks only textual path and numeric convertibility; it permits negative ranges, reversed byte ranges, absolute paths, traversal, and invalid line/column values because it never constructs `SourceLocatorV1`.
- The production `SourceExcerptV1` and `SourceLocatorV1` constructors do enforce these invariants, but the persisted reader bypasses them with a `JsonNode` projection. A mutated Evidence graph with changed excerpt bytes or an unsafe locator can still close in `PublicEvidenceGraph.closureFor` and allow an applicable candidate.
- Minimal RED: mutate only `rawUtf8` or its declared hash, and separately mutate locator range/path; require fatal/reference rejection (or, if the step contract assigns it to relation failure, a non-applicable disposition) before a candidate is emitted. Minimal fix: instantiate the existing value types or reproduce their exact checks and compute the digest over the decoded UTF-8 bytes.

### P1 — candidate-set identity omits material candidate semantics

- `FactCandidateSet.identity` includes roots, denominator keys, invocation/call-target IDs, static target strings, argument edge IDs, control IDs, and the disposition material. It omits `FactCandidate.requiredAtoms`, `orderedArguments` (argument node IDs and Java-local origins), and `evidenceBySubject` (source/rule evidence IDs).
- Consequently, changing required registry atoms, Java-local origins, or evidence closure can change the returned candidate while leaving the same `candidateSetId`. The registry schema/template identity is also absent; `FactRegistry` accepts custom candidate templates but that content does not participate in the identity.
- The candidate material uses unframed `|` and newline concatenation, while the architecture requires framed canonical identity material. Although normal parser output is usually ASCII-safe, the public registry/static text seam permits delimiter-bearing text and makes identity collision behavior unspecified.
- Minimal RED: create two otherwise equal inputs differing only in evidence IDs, local-origin IDs, or registry atom order/content and assert different candidate-set identities; create equivalent reordered inputs and assert the same identity. Minimal fix: include every wire-visible candidate/registry field in a versioned, length-framed identity preimage (or persist a content-addressed registry reference and include it).

### P1 — empty/unknown boundary ownership silently disappears from the denominator

- `PublicProgramNode` permits an empty `owningEntryIds` list. `FactCandidateEnumerator` filters owners through `inputs.entryIds()` and emits no candidate or disposition when a boundary has no owner (or only an owner outside the entry set).
- The M1 contract forbids silent denominator loss: every applicable `entry + boundary + template` must be represented by a candidate or scoped `NOT_APPLICABLE`, and ownership/entry closure must be explicit. This malformed boundary is neither represented nor rejected.
- Minimal RED: persist a boundary with empty or foreign ownership while keeping the rest of the graph valid; require a fatal reference failure or an explicit graph/entry gap, never an empty candidate denominator. Minimal fix: reject boundary nodes without a valid common entry owner at input validation, or publish a typed disposition keyed to the unresolved owner according to the chosen contract.

### P1 — evidence support edge kind is discarded, allowing node/edge support confusion

- `parseEvidenceGraph` accepts `SUPPORTS_PROGRAM_NODE` or `SUPPORTS_PROGRAM_EDGE`, but `FactCandidateInputs.EvidenceEdge` does not retain the parsed edge kind. `PublicEvidenceGraph.closureFor` filters only graph kind and subject ID and accepts either support kind.
- A persisted evidence edge can therefore claim an edge subject through a node-support relation (or vice versa) and still satisfy source-plus-rule closure. This is not the exact Evidence closure promised by the M1 contract.
- Minimal RED: mutate only the evidence edge kind for a boundary node or call-target edge and require closure to fail. Minimal fix: retain the support kind and require `SUPPORTS_PROGRAM_NODE` for node subjects and `SUPPORTS_PROGRAM_EDGE` for edge subjects.

### P2 — public malformed-construction failures are not consistently normalized

- `FactCandidateInputs` reads `programGraphs.get(CODE_STRUCTURE).applicationProfileId()` before confirming the closed graph-kind set, so a malformed public map can throw a raw `NullPointerException`. Several constructors call methods on nullable map keys/values before the stable failure type is reached.
- `FactCandidateSet` accepts arbitrary five unique root references without verifying that they equal the five graph/evidence roots, and public candidate/disposition records do not verify template keys against the supplied registry. The persisted reader path is stricter, so these are seam-hardening issues rather than the primary positive-path defect.
- Minimal RED: malformed public constructor inputs should produce the stable reference/profile error, and roots/template keys should be cross-checked before constructing a candidate set.

### P2 — current tests prove the happy join but not the required field-level closure

- `FactCandidateExactPathTest` checks count, entry IDs, kind, uniqueness, and empty not-applicable dispositions, but does not assert call/target identity, ordered argument bindings and local origins, control IDs/polarity, evidence subject closure, required atom values/order, or candidate-set identity.
- `FactCandidateMissingPathTest` removes evidence from one argument edge and proves isolation, which is valid and useful, but it does not cover the endpoint, owner, control, boundary evidence, registry, profile/reference, or hash mutations listed above. Its `ProgramGraphsPublicFixture.SourceMaterial.reader()` returns the same in-memory `VerifiedSourceTextSet` for every reference, so the test does not itself prove source-reference rebinding is rejected.
- Minimal RED additions should remain persisted public-graph mutations, one mutation per behavior, without using drafts or Fact raw JSON as M1 input.

## Changed files

- `progress/fact-candidate-m1-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only pre-existing/uncommitted M1 files and this progress file are present. |
| static source review | PASS | All M1 candidate/reader/fixture/test files inspected against the Step 04 M1 contract. |
| `git diff --check` | PASS | Review progress file has no whitespace errors. |

## Decisions

- This review will report only code-observable P0/P1/P2 findings and minimal follow-up RED suggestions; it will not edit implementation or tests.
- No P0 was found in the bounded M1 seam; the P1 findings above are acceptance-blocking because they can emit candidates from semantically malformed persisted inputs or keep changed candidate semantics under the same identity.

## Blockers

- M1 should not be marked accepted until the P1 endpoint, lineage, excerpt-hash, identity, ownership, and Evidence-edge-kind mutations are covered and fixed or explicitly adjudicated by Sol/ultra.

## Exact next action

- Parent agent should use this report to create the minimum RED set, then have Terra fix only the confirmed M1 seam. Do not start M2 Proof implementation from the current acceptance-blocking state.

## Resume checks

- Recheck `git diff --check`; do not run Maven concurrently with another agent.
