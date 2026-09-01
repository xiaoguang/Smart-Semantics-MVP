# Progress: target-contract-replacement-readiness-review

- Status: COMPLETE
- Agent role: Luna/xhigh read-only target-contract replacement readiness audit
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Compare `src/main/java/com/linguan/codemd/target/contracts/**` and its tests, with adjacent store-test coupling where needed for the §13.3.1 handoff, against `docs/DESIGN.md` §§13.3–13.3.1. No production, test, Maven, stage-document, network, model, or source-capture changes.
- Approved inputs: The three applicable `AGENTS.md` files; `docs/DESIGN.md` §§13.3–13.3.1; current target contract/store source and tests.
- Current branch/worktree: `/private/tmp/linguan-github-code-target-implementation` (pre-existing worktree changes preserved)

## Completed

- Read the repository-root, `backend-agents/`, and `sources/github-code/` `AGENTS.md` files before review.
- Read the finalized canonical-envelope, policy, typed-ID, and three-store contracts in `docs/DESIGN.md:1098-1320` and the framing/identity rules at `docs/DESIGN.md:1337-1407`.
- Audited the current target contracts and `TargetContractsTest`; separately noted adjacent `CanonicalModuleArtifactStoreTest` assumptions that would otherwise reintroduce the superseded parser and old constructor.
- Produced the replacement-readiness findings below. No Maven command was run, per task scope.

## Current state

### 1. Exact incompatible current code/test assumptions

#### Canonical JSON and identity

- `CanonicalJson.canonicalize` (`src/main/java/com/linguan/codemd/target/contracts/CanonicalJson.java:35-43,89-120`) uses Java `TreeMap` ordering and accepts an already-materialized `JsonNode`; it does not establish the target codec's exact canonical-byte/schema/strict-parse contract, including the explicitly UTF-8-byte ordering required for descriptor lists, and it is disconnected from policy-selected envelope identity (`docs/DESIGN.md:1363-1389`). `canonicalizeJsonl(List<JsonNode>, String)` (`CanonicalJson.java:50-80`) invents a caller-supplied semantic-key seam and sorts by Java `String`, while the target binds JSONL to the exact `(artifactType,schemaVersion)` policy and framing.
- `ModuleArtifact.parse(byte[], Class<T>)` (`ModuleArtifact.java:53-80`) is explicitly superseded by the design (`docs/DESIGN.md:1275-1276`). It has no expected address, policy registry, expected upstream/control values, or exact registered policy key; it also exposes payload/complete bytes directly. The adjacent test still imports/calls it (`src/test/java/com/linguan/codemd/target/artifacts/CanonicalModuleArtifactStoreTest.java:3-4,279-312`).
- `validateArtifactIdentity` (`ModuleArtifact.java:188-205`) takes the prefix from the caller and hashes `(schemaVersion + "\\n") || canonicalEnvelopeWithoutId`; the target requires the registry-selected prefix and the framed `canonical-module-artifact-id-v1` + schema + type + canonical-envelope preimage (`docs/DESIGN.md:1363-1389`). `TargetContractsTest` hard-codes the old `ARTIFACT_ID` and preimage (`TargetContractsTest.java:30-32,114-142`), so its golden cannot be reused.
- The current parser requires exactly four control fields (`ModuleArtifact.java:151-162`), omitting required `controls.artifactPolicyRegistryRef` (`docs/DESIGN.md:1115-1121`). Both current wire fixtures use the four-field shape (`TargetContractsTest.java:114-126`; adjacent store test `CanonicalModuleArtifactStoreTest.java:293-304`). Current `artifactType` accepts any uppercase token (`ModuleArtifact.java:29-31,65-66`) rather than the exact registry `(artifactType,schemaVersion)` policy key.

#### ArtifactReference, controls, and typed IDs

- `ArtifactReference` is in `com.linguan.codemd.target.contracts` and validates only a free-form nonblank `artifactId` plus lowercase SHA (`ArtifactReference.java:1-14`). The target records are in `com.linguan.codemd.target.artifacts` and require `ArtifactReference`, `ArtifactPolicyRegistryReference`, `ArtifactPolicyKey`, `CanonicalArtifactPolicy`, and five-component `ArtifactControls` (`docs/DESIGN.md:1221,1235-1239`). The target ID grammar is `<prefix>:<64 lowercase hex>`, with fixed-prefix/type checks before lookup (`docs/DESIGN.md:1298-1302`); the current value accepts IDs such as `run-request:depothead-round1` (`CanonicalModuleArtifactStoreTest.java:234-237`) and would accept a bare arbitrary ID.
- There is no registry reference in the current contracts, and the adjacent tests construct four-component `new ArtifactControls(A64, B64, C64, null)` (`CanonicalModuleArtifactStoreTest.java:147-154,230-240,257-270`) instead of the required non-null fifth `artifactPolicyRegistryRef` (`docs/DESIGN.md:1239,1296-1298`).
- `StageModuleAddress`, `ValidationModuleAddress`, and `ResumeModuleAddress` currently retain raw `String` IDs and generic token checks (`src/main/java/com/linguan/codemd/target/artifacts/StageModuleAddress.java:3-23`; `ValidationModuleAddress.java:3-12`; `ResumeModuleAddress.java:3-12`). They do not enforce the target typed-ID/fixed-prefix and closed stage/module registry rules (`docs/DESIGN.md:1298-1312`). `StageModuleAddress` also accepts any syntactically valid stage key rather than the closed eight-stage mapping.
- `SourceLocator` has directionally compatible root-independent path and exclusive coordinate checks (`SourceLocator.java:4-24`), but its test covers only one accepted path and one absolute-path rejection (`TargetContractsTest.java:86-94`). It does not establish the full canonical repository-relative/UTF-8-offset/no-basename machine-locator boundary in `docs/DESIGN.md:1096`.

#### Store handoff

- The current `target/contracts` package has no `CanonicalModuleArtifactStore`, `CanonicalStageArtifactStore`, `CanonicalRunManifestStore`, publication/reference/request records, or policy registry seam. The finalized public surface is exactly the three `install`/`reopen` interfaces and the registry-aware constructors (`docs/DESIGN.md:1151-1225,1230-1272`). The current `ModuleArtifact.canonicalBytes()`/`reference()` accessors (`ModuleArtifact.java:101-108`) cannot prove module receipt/root, address, controls, upstream refs, policy, or collision state.
- The adjacent module-store test still passes `new AtomicDirectoryInstaller()` to the constructor (`CanonicalModuleArtifactStoreTest.java:46-51,95-100,134-139,176-181,207-212`), but the finalized constructor is `(runStore, canonicalJson, artifactPolicies, limits)` and explicitly rejects that old parameter (`docs/DESIGN.md:1221-1225`). It also derives filesystem paths from raw IDs in test code (`CanonicalModuleArtifactStoreTest.java:322-325`), whereas the target requires typed-address encoding inside the opaque handle and forbids caller path derivation (`docs/DESIGN.md:1304-1312`).
- The adjacent test manually supplies artifact IDs and the old JSONL formula (`CanonicalModuleArtifactStoreTest.java:121-160,253-270`) instead of letting the exact policy select envelope/media/prefix and validating canonical bytes. Its tamper/idempotence intent may remain, but its request/golden/setup is incompatible.

### 2. Fresh Luna RED required before Terra code

The existing `TargetContractsTest` is not a valid final-contract RED: it compiles against the superseded parser and old golden, and the adjacent store RED compiles against the forbidden `AtomicDirectoryInstaller` constructor. Before Terra touches replacement code, Luna must produce a new, one-behavior-at-a-time RED that:

1. Uses only the exact public names and component orders in `docs/DESIGN.md:1153-1272`, including the five-field `ArtifactControls` and registry reference. The setup must install/use a path-free canonical policy-registry fixture and construct all three stores with the exact four constructor arguments in `docs/DESIGN.md:1221-1225`; no old parser or installer overload is a compatibility target.
2. Independently golden-tests all four policy-selected identity kinds and the module receipt/root self-exclusion/framing rules (`docs/DESIGN.md:1337-1407`). The golden must not calculate an expected ID through production code, caller-supplied prefix, newline concatenation, or `canonicalizeJsonl(..., semanticKey)`.
3. Tests registry authority: exact `(artifactType,schemaVersion)` lookup, constructor-registry reference equality with `ArtifactControls.artifactPolicyRegistryRef`, selected media/envelope/prefix, unknown/duplicate policy entries, noncanonical JSON/JSONL, zero-line JSONL policy gating, and rejection of caller-provided prefix/identity drift (`docs/DESIGN.md:1277-1279,1363-1389`).
4. Tests the typed-ID grammar and closed stage/module mapping before any filesystem lookup: uppercase/non-hex hashes, extra colons, slash/backslash/dot segments, wrong fixed prefixes, unregistered stage/module keys, and raw-ID path-segment use (`docs/DESIGN.md:1298-1312`). Keep the valid relative locator/exclusive-span behavior, but add the missing canonical/no-basename boundary.
5. Exercises module handoff only through `CanonicalModuleArtifactStore.install(ModuleInstallRequest)` and `reopen(ModulePublicationReference)`, with real `RunStoreBootstrap.openForTest` storage and `ImmutableBytes` defensive-copy assertions (`docs/DESIGN.md:1223,1281-1296,1314-1316`). Cover receipt-last atomic publication, exact directory set, idempotent replay, collision, tamper/partial rejection, and no partial bytes.
6. Adds independent stage and root-manifest REDs through the exact stage/run interfaces, including Stage08 provenance and semantic-set rules, fresh stage reopen, exactly eight ordered stage references, and the requirement that Stage08 receipt exists before root-manifest installation (`docs/DESIGN.md:1318-1320`).

The RED must fail because the replacement public seam is absent or incompatible—not by preserving a passing old parser test. Terra may begin only after Luna observes the expected targeted compile/test RED and records it; no new API, overload, caller path, fallback prefix, or schema compatibility behavior may be invented.

### 3. Assumptions that can remain

- Recursive object-key canonicalization, compact UTF-8 bytes, array-order preservation, and final-LF JSONL intent are valid behavioral goals (`CanonicalJson.java:31-50`; `TargetContractsTest.java:34-51,97-112`). They must move behind the target codec/store boundary and use the target byte-order, strict parsing, schema, and framed identity rules.
- Root-independent slash-separated source paths, UTF-8 byte offsets, 1-based positions, and exclusive-end ordering in `SourceLocator` are directionally reusable (`SourceLocator.java:3-24`). The current valid-path/absolute-path test intent can remain after the missing canonical and no-basename cases are added.
- Fail-closed rejection of unknown top-level fields and duplicate JSON fields is a sound safety property (`TargetContractsTest.java:66-84`), but must be tested through registry-aware canonical codec/store reopen rather than `ModuleArtifact.parse`.
- Defensive copying and immutable-value intent can remain (`ModuleArtifact.java:48-50,95-103`; `src/main/java/com/linguan/codemd/target/artifacts/ImmutableBytes.java:5-31`), with the public store boundary using the exact `ImmutableBytes` API and `VerifiedCanonicalPayload` contract (`docs/DESIGN.md:1281-1296`).
- Sorted/unique upstream and gap-reference intent, idempotent install, collision refusal, and tamper fail-closed behavior can remain as requirements. Their current String sorting, old IDs, constructor, and path helper are not reusable contracts.

### 4. No invented APIs / review boundary

- This report names only records/interfaces/constructors explicitly listed in `docs/DESIGN.md:1153-1272` and the exact `ImmutableBytes` API in `docs/DESIGN.md:1281-1293`.
- Do not add a compatibility `ModuleArtifact.parse` path, an `AtomicDirectoryInstaller` constructor, a caller-selected artifact prefix or JSONL semantic-key argument, a public filesystem locator, or a second identity formula. If old types remain temporarily, treat them as migration debt only; they must not appear in the new selector or golden (`docs/DESIGN.md:1275-1279`).
- No Stage document was reviewed or modified; no production/test code, Maven command, network/model/source call, or source capture was performed.

## Changed files

- `progress/target-contract-replacement-readiness-review.md` (this report only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `find /private/tmp/linguan-github-code-target-implementation -name AGENTS.md -print` | PASS | Read all three applicable instructions files: repository root, `backend-agents`, and `sources/github-code`. |
| `git status --short` | PASS | Pre-existing worktree changes were observed and preserved; only this report was added by this audit. |
| Maven/test commands | NOT RUN | Explicitly prohibited by the task. |

## Decisions

- Mark the current target contract slice not replacement-ready until a fresh Luna RED replaces the superseded parser/identity assumptions and covers the registry-aware three-store handoff.
- Treat `SourceLocator`, strict rejection, canonical UTF-8/array-order intent, defensive copying, and install lifecycle intent as salvageable behavior goals, not as evidence that the current public API matches the finalized contract.
- Keep this audit read-only and do not touch the Stage documents currently owned by the Sol Design Authority.

## Blockers

- Terra replacement implementation must wait for the fresh Luna RED described above. This is a contract-migration gate, not a request to weaken the finalized design.

## Exact next action

- Parent agent should hand the fresh RED brief to Luna, observe the expected targeted RED, then start Terra only against the registry-aware public seam.

## Resume checks

- Confirm this report remains the only file changed by this audit.
- Before any continuation, re-read `docs/DESIGN.md:1149-1320` and verify no Maven/network/model/source call is introduced.
