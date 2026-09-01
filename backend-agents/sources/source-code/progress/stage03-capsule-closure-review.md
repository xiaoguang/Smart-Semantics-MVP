# Stage03 Capsule Closure Post-GREEN Review

Status: COMPLETE

Scope: Read-only review of `Stage03CapsuleClosureValidator` and its integration with
`Stage03Generator` against the Stage03 closure contract and the public closure tests.

Review questions:

- Does honest single-flow and multi-flow closure validate without false rejection?
- Are capsule IDs, schemas, budgets, paths, facts, atoms, proof records, spans, and
  projection obligations independently closed?
- Are malformed inputs normalized to stable Stage03 errors rather than raw exceptions?
- Does closure validation happen before any provider call?

## Verification

- `mvn -Dtest=Stage03CapsuleClosureTest test` — PASS, 2 tests, 0 failures,
  0 errors, 0 skipped; build success at 2026-08-30 10:52:35 -02:30.
- `mvn -Dtest=Stage03MultiFlowTest test` — PASS, 5 tests, 0 failures,
  0 errors, 0 skipped; build success at 2026-08-30 10:54:14 -02:30.

The multi-flow selector confirms that independent capsules with overlapping
source concerns across flows are not rejected by the validator: validation is
performed per `(FlowSlice, EvidenceCapsule)`, while same-capsule overlapping
spans remain correctly rejected by the Stage02 projection contract. Generator
ordering is also direct: all closure validations at `Stage03Generator.java:104-114`
complete before the first Provider call at `Stage03Generator.java:198`.

## Findings

### P1 — proof-edge and graph provenance is not independently closed

`Stage03CapsuleClosureValidator.java:424-441` validates a proof's atom/fact
membership, proof nodes, source file/span hashes, and that required edge IDs
exist with endpoints in the proof node set. It never compares each
`ProofEdge.repositoryEdgeId`, endpoint repository node IDs, or `ruleId` with the
replayed public Stage01 graph/`Stage01FlowView`, and it does not bind a
`ProofNode.repositoryNodeId` to that graph. A coordinated mutation of a
`ProofPack` can therefore remain structurally self-consistent while referring
to a non-existent or wrong graph edge. `Stage02Compiler` has the needed
comparison in its graph-aware gate (`Stage02Compiler.java:1026-1039`), but the
new Stage03 validator does not repeat it. This is a P1 closure gap for the
approved package seam, even though `Stage03Generator` normally obtains fresh
Stage01/02 records through replay first.

### P1 — committed identity/profile closure is incomplete in the validator

The validator checks only schema strings and Stage01-ID equality at
`Stage03CapsuleClosureValidator.java:61-64`, and checks that the proof-pack ID
is nonblank at `:382-387`. It recomputes the Capsule ID at `:206-212`, but does
not independently recompute the canonical `ProofPack.proofPackId`, the
`VerifiedSnapshot.snapshotId`/Stage01 result identity, or the Stage02 result
identity/profile digests. Thus coordinated record mutations can preserve the
stored IDs while changing the committed evidence graph. Generator replay
guards the expected Stage02 result at `Stage03Generator.java:89-95`, but the
closure validator itself is not a complete identity gate as required by the
M5 closure contract.

### P1 — source path and symlink policy is delegated rather than enforced

`FrozenSources.java` equivalent logic in
`Stage03CapsuleClosureValidator.java:475-510` accepts any nonblank inventory
path and resolves it with `root.resolve(path).normalize()` plus
`startsWith(root)`, then calls `Files.readAllBytes` without `NOFOLLOW_LINKS`
or a regular-file/segment check. Absolute paths under the root and symlink
replacement are not independently rejected by this validator. Stage01/02
replay normally performs stricter `safeSource` checks before this call, but a
closure gate must not rely on that prior check for path/source closure, and a
root mutation between replay and validation can otherwise be followed.

### P1 — expectation-gap scope is membership-only, not flow-local provenance

`validateFlow` at `Stage03CapsuleClosureValidator.java:117-127` and
`validateGaps` at `:223-244` accept an expectation gap whenever its global
`gapId` exists and appears in the candidate Flow; they do not verify the
gap's searched/observation scope against the Flow's entry/root nodes. A
foreign expectation-gap record can therefore be copied into a mutated Flow
and Capsule and pass this closure gate. Normal two-flow replay remains green
(`Stage03MultiFlowTest`, above), but this validator does not independently
prove the Flow-local Gap invariant.

## Closed checks

- No P0 was found.
- Raw `RuntimeException`/`IOException` from the validator body are normalized
  by `Stage03CapsuleClosureValidator.java:57-76` to stable
  `CAPSULE_CLOSURE_BROKEN`.
- Capsule schema, flow/capsule linkage, allowed fact/atom membership, atom
  value/role/name/proof linkage, source/excerpt hashes, span IDs, obligation
  coverage/IDs, byte accounting, and Capsule ID are checked at
  `Stage03CapsuleClosureValidator.java:159-213` and `:247-314`.
- Provider admission is after the all-flow validator pass, and the permitted
  direct selectors above showed no honest single- or multi-flow false
  rejection.

## Final adjudication

The follow-up adjudication in
`progress/stage03-capsule-closure-adjudication.md` treats
`Stage03Generator.generate(Stage03Request, StructuredModelProvider)` as the
external Stage03 boundary. That boundary accepts a replayable Stage02 request,
not caller-supplied Stage01/Stage02/Capsule records, and performs fresh Stage01
and Stage02 compilation before invoking this package-private validator.

Against that bounded frozen-immutable profile, the four findings above have
the following final disposition:

| Finding | Final disposition |
| --- | --- |
| Proof-edge and graph provenance not duplicated inside the validator | Closed by composed replay: fresh Stage02 proof closure checks graph edge IDs, endpoints, and rules before `Stage03Generator.java:104-114` invokes the validator. Reopen only for a future persisted/cached-record seam. |
| Committed identity/profile closure not duplicated inside the validator | Closed by composed replay: fresh Stage01/Stage02 compilation recomputes identities and `Stage03Generator.java:89-95` checks the expected Stage02 identity before Provider work. |
| Weaker path/symlink checks in the validator | Future cross-stage source-reader hardening, not a bounded Stage03 fatal: fresh Stage01/Stage02 replay rejects static unsafe paths/symlinks and changed bytes fail SHA before Provider admission. |
| Expectation-Gap scope not duplicated inside the validator | Closed by composed replay: Stage02 entry-owned Gap selection is committed into Flow/Capsule and Stage02 identities, preventing foreign Gap injection through the external seam. |

The source-reader item remains a documented future hardening concern if
concurrently mutable/untrusted roots or naked persisted records become
supported. This classification agrees with
`progress/stage03-capsule-closure-adjudication.md`; no contradiction with the
current external interface was found.

## Decision

For the current bounded static Java/SpringMVC/MyBatis profile with immutable
frozen input and mandatory Stage01→Stage02 replay, no P0/P1 remains reachable
through the public Generator seam. The review is therefore **COMPLETE**. This
file records only the bounded read-only review; no production, test, or design
files were modified.
