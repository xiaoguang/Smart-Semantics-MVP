# Progress: artifact-foundation-design-brief

- Status: BLOCKED
- Agent role: Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Design clarification only for the first bounded Delivery 3 artifact-foundation vertical slice; no product code, tests, POM, target architecture documentation, Maven execution, commit, or push.
- Approved inputs: Published `docs/DESIGN.md`; `docs/plans/source-analysis-naming-and-delivery-plan.md`; `docs/plans/target-standards-and-toolchain-plan.md`; current semantic Wire Reset skeleton on `origin/main` base `a1297f4`.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read repository-root, worktree-root, backend, and source-code `AGENTS.md` instructions in full.
- Read `docs/DESIGN.md` in full and both implementation plans through all artifact-foundation, selector, toolchain, collaboration, and delivery sections.
- Inspected the complete current `pom.xml`, production/test source tree, existing wire guard/tests, package skeletons, orchestrator progress, and Git status.
- Confirmed the current branch has no artifact-foundation implementation; the only pre-existing untracked file is the orchestrator-owned `progress/source-analysis-artifact-foundation.md`.
- Completed an independent read-only skeleton audit; it confirmed there are no hidden resources, codec/store types, receipts, schemas, fixtures, or store tests.
- Reconciled the approved three-store wire, identity, atomic-publication, NOFOLLOW, collision, tamper, and selector contracts.

## Current state

- The published design is sufficient at the persisted-wire, identity-formula, directory-layout, and filesystem-behavior levels, but it is **not sufficient to write the required first public-seam RED without inventing Java Interface**. The exact blocker and docs-only correction are in the design brief below.
- No Maven command has run and no implementation file has changed.

## Changed files

- `backend-agents/sources/source-code/progress/artifact-foundation-design-brief.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Branch tracks `origin/main`; only orchestrator progress was untracked before this file. |
| `rg --files src/main src/test` | PASS | Only package skeletons, `AnalysisWireFormatGuard`, `UnsupportedAnalysisWireException`, `SourceAnalysisArchitectureTest`, and `PreResetWireRejectionTest` exist. |
| Read-only inspection of `pom.xml` | PASS | Approved Java 17/Jackson/AssertJ/jqwik/ArchUnit/Awaitility and quality-plugin foundation is already declared; this brief does not alter it. |
| `git diff --no-index --check /dev/null progress/artifact-foundation-design-brief.md` | PASS | No whitespace-error output; exit 1 is the expected no-index difference from an empty file. |

## Decisions

- Do not broaden the first slice into evidence records, runtime state, analysis-step publications, run manifests, archive behavior, or any business analysis stage.
- Do not let Luna choose codec methods, typed-ID component types, registry loading, or failure-code transport inside a test. Those are part of the public test seam and must be published first.
- Preserve all currently published JSON/wire/identity/filesystem semantics; the required correction is a Java Interface/selector clarification, not a schema or architecture redesign.

## Blockers

- `docs/DESIGN.md` publishes `CanonicalJsonCodec` with a constructor only, while both plans require `CanonicalJsonCodecTest`; there is no public behavior Luna can test through the Interface.
- `CanonicalArtifactPolicyRegistry` has `reference/resolve` but no published path-free loader/factory, although the required registry test and store setup must load exact `artifact-policy-registry-v2` bytes.
- Public address/reference records do not publish Java component types or parse factories for the required non-interchangeable typed IDs.
- Stable foundation failure codes are listed, but `install/reopen` publish neither a failure result nor a code-bearing exception Interface; collision/tamper/NOFOLLOW REDs cannot assert the stable code without testing messages or private state.
- The naming plan requires `AnalysisStepAddressTest`; the toolchain plan's frozen foundation selector table omits it. Luna must not decide which list is authoritative.

## Exact next action

- Make and publish the bounded docs-only correction specified below, fast-forward it to `origin/main`, then create a fresh implementation branch and give Luna the single first RED `CanonicalJsonCodecTest#encodesCanonicalObjectWithUtf8ByteOrderedKeys`.

## Resume checks

- Re-read this file, run `git status --short`, preserve the orchestrator progress file, and verify no product/POM/target-design changes occurred before using the final brief.

## Design-authority task brief

### Outcome and bounded delivery order

Delivery 3 is **not** one 3–5 hour implementation. It is an ordered foundation:

1. canonical JSON + immutable bytes + typed identity/address primitives;
2. policy registry;
3. `CanonicalModuleArtifactStore` over the shared private atomic engine;
4. `CanonicalAnalysisStepArtifactStore` reusing that engine;
5. `CanonicalRunManifestStore` reusing that engine;
6. four-state runtime and exterior validation only in later foundation slices.

The initial 3–5 hour slice is item 1 only. Treating all three stores, runtime, and validation as one slice would either exceed the bound or create shallow/pass-through seams. The three published store Interfaces remain the later test surface; no fourth generic store Interface is allowed.

### Required docs-only correction before RED

Publish one coherent correction to `docs/DESIGN.md` §13.3.1 and reconcile both plan selector tables. It must freeze, at minimum:

1. **Codec Interface.** Add exact public methods to the already-published class, with no Jackson configuration exposed:

   ```java
   public final class CanonicalJsonCodec {
       public CanonicalJsonCodec();
       public ImmutableBytes encodeCanonical(com.fasterxml.jackson.databind.JsonNode value);
       public com.fasterxml.jackson.databind.JsonNode parseCanonical(ImmutableBytes canonicalUtf8);
   }
   ```

   `encodeCanonical` returns compact strict UTF-8 with no BOM, no insignificant whitespace, and no final LF; object keys use unsigned UTF-8 byte order; arrays retain semantic order; strings use one frozen escaping rule; only exact integer representations allowed by the domain wire are admitted. `parseCanonical` rejects malformed UTF-8, BOM, duplicate keys, floating/exponent/negative-zero forms, invalid surrogate sequences, and any valid JSON bytes whose re-encoding is not byte-identical. Owner-specific unknown-field/schema checking remains with the owner parser/store; the codec does not guess a domain schema.

2. **Policy-registry construction.** Add one path-free public factory (either an exact static method on `CanonicalArtifactPolicyRegistry` or one named concrete loader) taking `ImmutableBytes` plus `CanonicalJsonCodec`, returning the published Interface after exact `artifact-policy-registry-v2` parse, ordering, uniqueness, identity, and SHA validation. Do not add a filesystem path or mutable registration method.

3. **Typed Java values.** Publish the component Java types/factories for the first slice: `ArtifactId`, `AnalysisRunId`, `Sha256Digest`, `ModuleArtifactRoot`, `ModuleReceiptId`, `AnalysisStepKey`, `AnalysisStepModuleAddress`, and `ArtifactReference`. Each textual value is parsed/validated at construction; fixed-prefix identities cannot be interchanged; no type exposes a `Path`. Keep the existing wire grammar `<prefix>:<64 lowercase hex>`, fixed-prefix rules, semantic-key registry, and numeric module mapping unchanged.

4. **Failure observation.** Publish one code-bearing foundation exception/result Interface for the existing stable codes (recommended: `ArtifactStoreException#code()`), with safe path-free detail only. This does not add new codes. It makes later REDs able to assert `MODULE_INSTALL_REQUEST_INVALID`, `MODULE_PAYLOAD_NOT_CANONICAL`, `MODULE_PUBLICATION_COLLISION`, `MODULE_PUBLICATION_INVALID`, `ANALYSIS_STEP_*`, `RUN_MANIFEST_*`, and `ATOMIC_MOVE_UNSUPPORTED` without message assertions or private filesystem inspection.

5. **Selectors.** Put `AnalysisStepAddressTest` in the same frozen foundation selector list in both plans, or explicitly remove it from both. Recommended: retain it between `CanonicalArtifactPolicyRegistryTest` and store tests because semantic address/path derivation is a prerequisite to NOFOLLOW store lookup.

No schema version, identity domain separator, artifact count, store Interface, directory layout, or failure classification changes are required.

### Public seams and class ownership after correction

- `org.sourceanalysis.app.artifact` owns the codec, immutable bytes, typed IDs/digests, policy values/registry, three store Interfaces/implementations, their request/reference/result records, `RunStoreBootstrap`, and the single package-private `AtomicCanonicalPublicationEngine`.
- `CanonicalModuleArtifactStore`, `CanonicalAnalysisStepArtifactStore`, and `CanonicalRunManifestStore` are the only shared persistence Interfaces. Tests and callers never receive a root/path accessor.
- `RunStoreBootstrap.open/openForTest` are the only public Path-taking store methods. `openForTest` requires an existing empty non-symlink temp directory and returns an opaque, closeable handle.
- `.evidence`, `.runtime`, and `.validation` remain untouched in the first slice except existing package markers.
- `AnalysisWireFormatGuard` remains the narrow generic header/legacy discriminator guard. It is not the canonical codec, owner schema validator, policy registry, or store reader.

### Persisted JSON, wire, and identity implications

- Canonical JSON is a byte contract, not pretty-printed Jackson output. The first golden must be hand-authored and independent of production code.
- IDs remain content-derived exactly by the published framed SHA-256 formulas. `runId` remains random only when the later runtime creates an execution; the first slice merely parses a fixture `analysis-run:<hex64>` address value.
- `ImmutableBytes.copyOf` and `copyToByteArray` retain the already-published defensive-copy contract; mutating either source or returned arrays cannot mutate identity material.
- Unknown fields/versions, numeric aliases, uppercase hex, extra colon, slash/backslash, dot segments, Unicode-normalized variants, wrong fixed prefix, and noncanonical bytes fail before any filesystem lookup.
- The initial slice emits no module, analysis-step, run-manifest, reader-visible, or business artifact.

### Filesystem semantics frozen for the subsequent store slices

- Typed address → private path mapping stays exactly:
  `runs/<encodedRunId>/steps/<NN-analysisStepKey>/modules/<NN-moduleKey>/`, analysis-step root under its step directory, and one root `run-manifest.json`.
- Every ancestor, target directory, staging directory, receipt, and payload is inspected with NOFOLLOW semantics. Caller strings are never passed directly to `resolve`; parse typed values, use closed mappings, then encode a single safe segment.
- Publication staging is an invisible sibling on the same filesystem. Write payloads first, force each file, write receipt last, force receipt and directory, fresh-reopen/recompute the entire set, then use one atomic move. `ATOMIC_MOVE` unsupported is fatal; there is no copy fallback.
- Reopen requires the exact declared file set and rejects missing/extra files, nested directories, or symlinks. It recomputes canonical bytes, IDs, sizes, SHA, descriptor root, receipt ID/SHA, controls, upstreams, and address.
- Identical reinstall returns `ALREADY_INSTALLED`; any byte/reference/descriptor difference at the same typed destination returns the store-specific collision code and preserves the original bytes. Staging residue is never a publication and is not runtime recovery.
- Analysis-step install must fresh-reopen its approved publisher/coordinator provenance, then install semantic payloads, optional archive, and receipt last. Run-manifest install fresh-reopens exactly eight ordered analysis-step references and installs the sole root manifest. Neither behavior belongs in the initial slice.

### Failure matrix for later one-behavior REDs

| Observable case | Required result |
| --- | --- |
| unsafe/mistyped ID, key, address, duplicate filename/artifact ID, empty payload set, wrong policy/media/envelope/prefix | fail before lookup; `MODULE_INSTALL_REQUEST_INVALID` or policy-specific published code |
| noncanonical JSON/JSONL, identity mismatch | `MODULE_PAYLOAD_NOT_CANONICAL`; no publication |
| destination absent, valid bytes | `INSTALLED` |
| destination complete and byte-identical | full fresh reopen, then `ALREADY_INSTALLED` |
| same destination, any differing expected byte/reference/descriptor | `MODULE_PUBLICATION_COLLISION`; original untouched |
| post-install tamper, missing/extra file, symlink/nested entry, receipt/root/SHA drift | `MODULE_PUBLICATION_INVALID`; no partial reopened value and no repair |
| unsupported atomic move or injected partial-install boundary | `ATOMIC_MOVE_UNSUPPORTED` or owning install failure; no installed receipt/publication |
| analysis-step/run-manifest collision or tamper | exact `ANALYSIS_STEP_*` / `RUN_MANIFEST_*` code; never overwrite |

### Non-goals

- No source capture, evidence locator/excerpt, runtime state machine, validation, archive, Provider, CLI/HTTP, analysis-step business module, fixture from historical pre-reset code, compatibility reader, JSON Schema bundle expansion, JSONL/RAW_UTF8 support, crash recovery, cleanup/repair protocol, or customer repository execution.
- No `common/shared/utils`, generic filesystem Adapter, in-memory fake store, caller-selected path/prefix, new schema, or new failure code.
- No POM change is needed for the first RED/GREEN; Jackson/JUnit/AssertJ and Java 17 primitives are already present.

### Exact recommended Luna RED after docs publication

- Own only `src/test/java/org/sourceanalysis/app/artifact/CanonicalJsonCodecTest.java`, an independent strict golden under `src/test/resources/analysis/foundation/canonical-json/` only if a file is useful, and Luna's own progress file.
- Add **one** test first: `encodesCanonicalObjectWithUtf8ByteOrderedKeys`.
- Build a `JsonNode` in deliberately noncanonical insertion order, including one nested object, one ordered array, one non-ASCII key, a boolean, null, and signed/unsigned in-range integers. Assert `encodeCanonical` equals one hand-authored compact UTF-8 byte sequence exactly and has no BOM/final LF. Do not derive expected bytes/ID with production code and do not mock the codec/hash.
- Run only:

  `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test`

- Expected RED: test compilation fails only because the newly published codec/immutable-byte Interface is not implemented. If it fails because the docs-only Interface is still absent/ambiguous, a selector falls through, or the toolchain/cache is unavailable, stop and report; do not alter the expected bytes or invent a method.
- Terra's smallest GREEN then owns only `CanonicalJsonCodec` and `ImmutableBytes`. After that GREEN, Luna adds the next single RED for `parseCanonical` rejection of one byte-different but valid JSON input; typed-address and policy tests follow before any store RED.
