# Progress: M8 first runner contract ruling

- Status: COMPLETE
- Agent role: Sol/ultra design authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Freeze the smallest public M8 P1/P2 runner/provider contract for one fresh-reopened M7 `MODEL_SAFE` two-Flow dry packet. Documentation only; no Java, tests, fixtures, or POM changes.
- Approved files: `AGENTS.md`, `docs/DESIGN.md`, `docs/analysis-steps/06-flow-interpretation.md`, and this progress record.

## Fixed constraints

- One existing M7 module publication is the only input; M8 must fresh-reopen exactly one `MODEL_SAFE` shard with two Flow cards and one owner relation.
- Exactly two scripted calls occur in order: P1 then P2. P2 is compiled only after an accepted P1 result.
- Provider-visible requests and responses use packet-local keys only; program-only bindings perform internal-ID translation and subset validation.
- P2 cannot add a Flow, relation, observation/evidence key, claim, or semantic slot beyond the corresponding P1 hypothesis.
- Automated acceptance uses a scripted provider only. No live model, retry, fallback, M9 publication, Step 06 artifact-count change, or full-run 57-output change.

## Current action

- Frozen the one-shard public seam, its program-only runtime source, the two exact scripted response bytes, and the no-expansion/no-retry boundary. No Java, test, fixture, POM, M9, or public artifact-count change was made.

## Decisions

- `BusinessProcessInterpretationRunner(CanonicalModuleArtifactStore, ProcessInputArtifactReader)` exposes `runInterpretation(BusinessProcessInterpretationRequest, ProcessModelProvider)` for exactly one named M7 shard.
- The business request carries the M7 publication, shard ID, and program-only expected-runtime reference. The provider request carries only canonical model-visible application bytes; its response carries observed runtime plus canonical response bytes.
- The frozen source of that runtime is exactly `profileBundleRef` JSON pointer `/flowInterpretation/processModelRuntimeRef`, a required third member of M6's existing `flowInterpretation` object. The first fixture writes one `scriptedRuntime` ref there before hashing the profile and reuses it as request expected and both observed runtime values.
- The bounded result retains the two validated provider responses with `providerCallCount=2` and `closed=true`; it does not pre-implement the full M8/M9 artifact graph.
- The first accepted script is exactly one P1 two-Flow/one-relation TRANSITION hypothesis followed by one P2 KEEP review. P2 has no fields capable of adding Flow, relation, basis/evidence, or claim content.

## Changed paths

- `AGENTS.md`
- `docs/DESIGN.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `progress/m8-first-runner-contract-ruling.md`

## Verification

- PASS: `git diff --check` (exit 0, no output). Docs-only scope intentionally ran no Maven selector.
