# Progress: Fact missing-path fixture lineage diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh bounded read-only debugger
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Determine whether `FactCandidateMissingPathTest.persistedMissingArgumentEvidenceRejectsOnlyItsEntryBoundaryCombination` fails because its public-store mutation fixture did not rebind source/discovery provenance, or because the strict production reader rejects a valid publication.
- Approved inputs: The exact failing test/helper, `PersistedFactCandidateInputReader` at the reported stack, and only necessary public publisher/carrier fields. No Maven, Java/test/design/source/Provider/network changes or sub-agent.
- Worktree: `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`; preserve all concurrent WIP.

## Current evidence

- Root observed the real baseline chain pass and the mutation branch fail at `PersistedFactCandidateInputReader.sourcePublicationReference:502`, called from `parseDiscovery:272` / `reopen:115`, with `PROOF_PACK_REFERENCE_BROKEN` before the test's intended missing-argument assertion.
- `FactCandidateMissingPathTest.java:147-164` fresh-reopens the base Source/Discovery publications, republishes Source at a new `missing-path-run`, then calls `copyStep(baseDiscovery, ...)`. `copyStep` obtains all semantic bytes directly from the base publication (`:244-275`), so the new Discovery receipt names `sourceStep.reference()` as upstream while its `capability-report.json` still embeds the old Source publication reference.
- The real writer owns this field: `ApplicationDiscoveryPublicationSpecifier.java:195-204` writes `capability-report.json.verifiedSourceInventoryPublicationRef` from the exact supplied `VerifiedSourceInventoryReference`. For the no-entry branch it also writes the same full Source reference at `noEntryDisposition.sourceInventoryPublicationRef` (`:220-228`).
- The strict reader is behaving as designed. `PersistedFactCandidateInputReader.java:165-178` first accepts the copied publications' same-run/control/receipt-upstream closure. It then requires the capability report's full Source step key, artifact root, receipt ID, and receipt SHA to equal the freshly reopened Source publication (`:475-502`). The copied Source semantic payloads can retain the same root, but moving them to a new run/publisher receipt changes receipt identity/SHA; stale base Discovery bytes therefore must fail at line 502.
- No other Discovery semantic carrier needs changing for this concrete fixture. `application-profile.json` binds Source semantic payload references rather than the Source publication receipt, and those payloads were copied byte-identically; the entry and mapper payloads contain no Source publication reference. This fixture has nonempty entries, so `noEntryDisposition` is null. The graph step is installed only after the new Discovery and already names `discoveryStep.reference()` in its public upstream list (`FactCandidateMissingPathTest.java:196-205`).

## Root cause and minimum fix

- Classification: invalid mutation-fixture republisher provenance, not a production reader defect.
- Minimum owner location: `FactCandidateMissingPathTest.java:156-164,244-275`. Before publishing the copied Discovery, deep-copy only `capability-report.json`, replace `verifiedSourceInventoryPublicationRef` with the four fields from `sourceStep.reference()` (`analysisStepKey`, `analysisStepArtifactRoot`, `analysisStepReceiptId`, `analysisStepReceiptSha256`), and rehash it through the existing `standalonePayload` helper. Let `copyStep` accept/use this prepared payload list instead of always deriving unchanged bytes internally.
- For this exact two-entry test, do not mutate `noEntryDisposition`. If the helper is deliberately generalized for zero-entry fixtures, also rewrite non-null `noEntryDisposition.sourceInventoryPublicationRef` to the same new Source reference. Keep the other three Discovery payloads and all semantic IDs unchanged.
- Do not weaken or remove `sourcePublicationReference`; it is the real writer/reader receipt-lineage contract and correctly rejected the stale copied carrier before graph enumeration.

## Changed files

- `progress/fact-missing-path-fixture-lineage-diagnosis.md` (owned diagnosis only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Maven | NOT RUN | Explicitly prohibited; root owns the active Maven session. |
| Static writer/fixture/reader trace | PASS | The real writer uses the current Source publication, the fixture retained the base Source receipt inside capability bytes, and the reader rejected that exact mismatch. |

## Exact next action

- Test owner should apply only the capability-report Source-reference rebind/rehash in the mutation helper, then let root rerun the already active bounded selector under its Maven lease.

## Constraints preserved

- No Maven or Java/test/production/design/source/Provider/network action was performed.
- No reader relaxation, new schema, public API, private store access, or sub-agent was introduced.
