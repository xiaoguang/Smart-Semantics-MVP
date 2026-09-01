# Progress: target design contract review

- Status: COMPLETE
- Agent role: Independent Sol/ultra design-contract reviewer
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-31 18:04:08 NDT
- Last updated: 2026-08-31 18:14:26 NDT
- Scope: Read-only review of the current uncommitted target design changes for Capture, Stage 01, artifact persistence, lifecycle/result, Stage 08 archive, and the public analysis Interface.
- Approved inputs: User-approved eight-stage implementation plan; repository, prototype, backend, and GitHub Code Agent AGENTS.md; current docs/DESIGN.md and Stage 01/08 detailed designs.
- Current branch/worktree: codex/github-code-target-implementation at /private/tmp/linguan-github-code-target-implementation

## Completed

- Read all applicable AGENTS.md files and the code-review/codebase-design review guidance.
- Confirmed this task owns only this progress file and must not edit design, production, test, or build files.
- Read the current 1,379-line overall design, 349-line Stage 01 design, and 555-line Stage 08 design completely.
- Compared the target capture, persistence, lifecycle/result, Stage 08 outputs, and product Interface against the approved eight-stage plan and scoped repository invariants.
- Reported immediate blocking contradictions to the root Design Authority before Terra could silently choose a contract.

## Current state

The design is directionally aligned with the approved target, but eight P0 contract gaps/contradictions make the current persistence/capture/runtime implementation non-unique. The current module-store GREEN may implement only behavior whose identity and policy are already exact; it cannot choose the missing formulas, registries, address encoding, stage/run store, or cross-stage records.

### Standards axis

- PASS: the three documents are function-first, preserve target-vs-current maturity, keep the full-repository denominator, restrict the LLM to Stage 06, and preserve exactly one repository-level nine-section document.
- BLOCKING: the scoped AGENTS rule says Luna/Terra must not guess schema, identity, failure, stage, safety, or public-Interface semantics. The unresolved seams below therefore require a Sol/ultra design correction before their RED/GREEN slices.

### Spec axis — P0 blockers

#### P0-1 — Module/stage roots and receipt identity have no executable formula

- Evidence: `docs/DESIGN.md:1036-1040,1105,1140-1142,1170`; `docs/stages/01-freeze-source.md:253`; `docs/stages/08-build-nine-section-document-and-archive.md:72`.
- Problem: the documents state which payload bytes a root covers, but never fix the domain separator, canonical descriptor fields, ordering, length framing, or exact `moduleArtifactRoot`, `stageArtifactRoot`, and `moduleReceiptId` formulas. A store can return any nonblank internally consistent value and still pass the current public behavior, so independently written goldens cannot prove content identity.
- Why it blocks the current store: `ModulePublicationReference` and idempotent collision/reopen decisions depend on these identities; Terra would be inventing a cross-stage identity contract.
- Compatible option A: define versioned descriptor-list formulas, for example a canonical array sorted by `fileName` whose entries include `fileName/artifactType/schemaVersion/artifactId/mediaType/sizeBytes/sha256`, prefixed by `module-artifact-root-v1\n` or `stage-artifact-root-v1\n`; define the receipt ID as a separately domain-separated hash of receipt-without-ID. Update DESIGN §13.3.1, `ModuleReceipt`, stage receipt schemas, and independent golden tests.
- Compatible option B: represent each publication manifest/receipt as a normal canonical `ModuleArtifact<PublicationManifest>` and use its ordinary artifact identity as the publication root/reference, removing the second unspecified root-ID algorithm. Update `ModulePublicationReference`, `ModuleReceipt`, stage receipt/publication records, and every upstream reference. This is a broader identity-DAG change.

#### P0-2 — The store is required to validate a registry it cannot access

- Evidence: `docs/DESIGN.md:1052-1082` fixes the four-constructor dependency list; `1100-1103` fixes payload fields; `1161-1170` requires `artifactType` to resolve to a registered type-prefix and permits empty JSONL only when the stage schema says so.
- Problem: neither constructor nor request carries an artifact-policy/schema registry. Therefore the store cannot distinguish registered vs invented type-prefix mappings, nor know whether exact-zero-byte JSONL is legal. The current RED uses `TEST_ARTIFACT -> test-artifact`, a mapping absent from the contract.
- Why it blocks the current store: Terra must hard-code test types, derive a prefix contrary to the text, skip required validation, or change the fixed constructor.
- Compatible option A: add a content-addressed `CanonicalArtifactPolicyRegistry` dependency to the constructor, fixing `artifactType + schemaVersion -> prefix/mediaType/emptyJsonlAllowed/envelopeKind`. Update §13.3.1 constructor, test setup, `ArtifactControls/schemaBundleSha256`, and reopen validation.
- Compatible option B: move this policy into each `CanonicalModulePayload` as a verified `ArtifactPolicyRef`/registered descriptor signed by the schema bundle, while the generic store validates canonical bytes and the referenced policy. Update `CanonicalModulePayload`, `ModuleInstallRequest`, receipt descriptors, and tests. Do not silently reduce the store to unchecked caller assertions.

#### P0-3 — Typed publication addresses are converted to paths without a grammar or encoding

- Evidence: `docs/DESIGN.md:1090-1093,1132-1142` derives directories directly from `runId/stageKey/moduleKey/validationId/resumeDecisionId` but fixes no allowed alphabet, length, dot-segment rule, or reversible filesystem encoding.
- Problem: these are strings, not safe path segments. Rejecting public `Path` does not prevent `../`, slash, backslash, device-name, or oversized-segment injection through IDs/keys.
- Why it blocks the current store: directory derivation is the store's first security operation; any implementation choice changes accepted wire values and cross-platform identity behavior.
- Compatible option A: replace raw strings at the store seam with validated value types and exact grammars/lengths; make stage/module keys closed enums and require IDs to match registered `<prefix>:<hex64>` forms, rejecting separators/dot segments before I/O. Update address records and negative tests.
- Compatible option B: keep logical IDs unrestricted within bounded UTF-8 rules but derive physical segments from a fixed base32/SHA-256 encoding, store/reverify the logical address in the receipt, and never concatenate raw values. Update address-to-directory algorithm, receipt fields, reopen logic, and path-layout tests.

#### P0-4 — The only store seam cannot publish stage or run artifacts

- Evidence: `docs/DESIGN.md:143-164` requires module publication followed by atomic stage publication; `1042-1142` exposes only `CanonicalModuleArtifactStore` with module/validation/resume module addresses; Stage 01 M3 requires four stage files at `docs/stages/01-freeze-source.md:231-243`; Stage 08 requires its public set and run manifest at `docs/stages/08-build-nine-section-document-and-archive.md:51-72,179-191`.
- Problem: no typed request/reference/interface can atomically install a stage public set, its stage receipt, or the whole-run manifest. Calling the module store cannot produce `stages/<stage>/source-input.json` or the unique run manifest without inventing paths and root rules.
- Why it blocks the current store: expanding `CanonicalModuleArtifactStore` ad hoc would violate its exact fixed seam; leaving it module-only makes Stage01 M3 and Stage08 M4 impossible.
- Compatible option A: retain the current module store and define separate deep `CanonicalStageArtifactStore` and `CanonicalRunManifestStore` seams with typed addresses, exact payload/root/receipt order, collision/reopen semantics, and no caller paths. Update DESIGN, Stage01 M3, Stage08 M4, and publisher REDs.
- Compatible option B: replace it with one generalized `CanonicalPublicationStore` whose sealed address/request variants cover MODULE, STAGE, VALIDATION, RESUME, and RUN_ROOT and whose policy fixes allowed file sets and receipt kinds. Update the current store Interface/records/tests before further production work.

#### P0-5 — Stage 01 M3 has a future-receipt dependency and inconsistent file count

- Evidence: `docs/stages/01-freeze-source.md:237-243` says the stage receipt excludes itself and M3 is first installed as a module; `253,262` define M3 `publishedArtifacts[4]` including `stage-receipt.json`; `docs/DESIGN.md:652` says a stage receipt's artifact list/root exclude itself and a parent records its SHA.
- Problem: the M3 module artifact is required to name/hash a stage receipt that is supposed to be computed last, while the exact parent that records the receipt SHA is unspecified. Depending on whether the stage receipt also binds M3, this is either a direct cycle or an undefined installation order.
- Why it blocks the current store: the M3 module payload bytes, module receipt, stage receipt, and collision identity cannot be computed in one unambiguous order.
- Compatible option A: make M3 publication describe exactly the three stage payloads only; install M3, then the stage store writes those three payloads and computes `stage-receipt.json` last, binding M3's module reference. A later stage/run publication records the stage-receipt SHA. Update `publishedArtifacts[3]`, example JSON, and Stage01Reference.
- Compatible option B: remove M3 as a normal module publication and make it the typed stage-publication transaction itself; its single immutable result is a `StagePublicationReference` covering three payloads plus a receipt, with no nested `stage01-publication.json`. Update Stage01 module count, module handoff, and tests.

#### P0-6 — `analysis-run-request-v1` has two incompatible schemas

- Evidence: `docs/DESIGN.md:730-742` defines `profileBundleRef`, inline `resourceBudget`, optional organization seed, `readerCandidateRound`, and `parentCandidateId`; `docs/stages/08-build-nine-section-document-and-archive.md:277-286` defines `profileRef`, `budgetRef`, omits seed/round/parent, and changes prompt-ref shape. Stage01 consumes the same named version at `docs/stages/01-freeze-source.md:13,206`.
- Problem: one schema version has incompatible names, cardinality, and identity material.
- Why it blocks the current store: run-request artifact bytes/ID are upstream refs in module receipts; choosing either shape changes every run/module identity and CLI/HTTP parser.
- Compatible option A: declare DESIGN §13.2 authoritative, update Stage08's record and all examples to `profileBundleRef/resourceBudget/organizationRegistrySeedRef?/readerCandidateRound/parentCandidateId`, then add one exact schema/identity table.
- Compatible option B: make all controls/budget/round lineage content-addressed references in a revised `analysis-run-request-v2`, update DESIGN, Stage01, Stage06, Stage08, adapters, and ID formulas, and reject v1 rather than guessing migration.

#### P0-7 — Stage 08's eight-file publication and run manifest are not one exact contract

- Evidence: the overall run tree puts `run-manifest.json` at run root (`docs/DESIGN.md:121-141`); Stage08 calls it one of eight files in the Stage08 directory (`docs/stages/08-build-nine-section-document-and-archive.md:55-72`). Stage08's role table promises run-request/control hashes and receipt SHA (`68,72`), but its `RunManifest` record omits them (`342-351`) and M4's nested wire schema is smaller again (`225-232`). `validation-baseline.json`, `archive-manifest.json`, public `candidate.json`, and the projection from M3's JSON-array TraceSet to public `trace.jsonl` do not have exact standalone schemas/identity mappings.
- Problem: file location, byte preimages, file schemas, and dependency order are underdetermined; `publishedArtifacts[8]` cannot be independently reconstructed.
- Why it blocks the current store: no store address represents the chosen location, and no request can supply eight independently validated canonical payloads without M4 inventing transformations.
- Compatible option A: keep one root-level run manifest as shown in DESIGN; treat it as the eighth logical Stage08 publication but give it a typed RUN_ROOT address. Define exact standalone schemas/extraction for all eight files, a stage root over the five semantic files, then archive manifest → stage receipt → root run manifest → M4 publication in that order.
- Compatible option B: put the sole run manifest in the Stage08 directory, update the overall tree and lookup/bootstrap rules accordingly, and define the same eight exact schemas/order there; the run store locates it through a fixed typed Stage08 address, not a second root copy. Either option must remove the duplicate/incomplete `RunManifest` definitions.

#### P0-8 — Durable queue/events are described as states, not as a persistent wire protocol

- Evidence: `docs/DESIGN.md:654-680` and Stage08 `467-500` give transitions, but no exact `run-events.jsonl` event record, event ID/hash chain, queue-entry/claim/lease record, append/CAS rule, idempotency-key binding, or crash fold for a process that dies after request install but before queue append.
- Problem: two implementations can produce different current states and replay behavior from the same bytes while both claiming the prose transitions.
- Why it blocks the current store/runtime composition: `AnalysisRunReference.latestEventId`, resume decisions, single-worker ownership, and `RUN_FINISHED` recovery cannot be verified or content-addressed; module receipts alone do not establish queue/lifecycle state.
- Compatible option A: define canonical `RunEventV1` JSONL with sequence, previousEventSha256, event type, request/manifest/result refs, worker claim generation, idempotency key digest, and deterministic fold; use an OS-lock-protected append and durable FIFO index with explicit crash recovery.
- Compatible option B: store immutable per-event canonical JSON objects plus an atomically replaced content-addressed queue/state index, with events retained for audit; define exact claim/release generations and fold from indexed event refs. Update `AnalysisRunReference`, `RunInspection`, resumer, HTTP/CLI idempotency tests, and run layout.

### P1 corrections (do not justify silent implementation choices)

- `docs/stages/01-freeze-source.md:71` says a registry record containing a Path is rejected, while `27` requires the private registry to hold a storage locator. Say explicitly that only the public `SourceRegistration` is path-free; the private registry implementation necessarily has a local locator.
- Git CLI isolation is incomplete at `docs/DESIGN.md:314` and Stage01 `23-25`: freeze an environment/config allowlist and unset or reject ambient `GIT_DIR`, work-tree/index/object/alternate/config/replace variables; verify object format SHA-1 and safe git-dir resolution. Otherwise `--git-dir` alone does not prove which object database was read.
- Stage01 accounting is inconsistent between `docs/DESIGN.md:205-207,286`, Stage01 `139-142`: either successful Stage01 verifies every regular file, or `sourceFileIds` must include explicit unverified-file dispositions in addition to text/media. Fix the ID-set equations for incomplete/paused diagnostics; binary remains verified media, never a verification Gap.
- Stage08 calls `validate` read-only at `docs/stages/08-build-nine-section-document-and-archive.md:261`, but it installs an append-only validation module at `193-205`. Clarify it is Candidate/run-nonmutating but storage-writing and idempotent; only inspect/artifact/render/trace are side-effect-free observations.

## Changed files

- backend-agents/sources/github-code/progress/target-design-contract-review.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing shared implementation/doc changes identified; no reviewer-owned file existed before this task. |
| Complete line-numbered read of the three target documents | PASS | 2,283 lines reviewed; all findings cite current line locations. |
| `git diff --check -- backend-agents/sources/github-code/progress/target-design-contract-review.md` | PASS | No whitespace errors. |

## Decisions

- Treat the approved eight-stage plan and scoped AGENTS target invariants as the Spec axis.
- Treat repository documentation/readability/progress rules and deep-module seam principles as the Standards axis.
- Do not edit the reviewed documents; report exact resolutions to the Design Authority.
- Classify a contradiction as P0 when Luna/Terra cannot construct an independent golden or secure public seam without choosing schema/identity/stage/lifecycle semantics.
- The module store may continue only after P0-1 through P0-3 are resolved; Stage01/08 publishers additionally require P0-4 through P0-8.

## Blockers

- Eight P0 design blockers listed above. They are design-authority work, not Terra implementation choices.

## Exact next action

Root Sol/ultra Design Authority presents the compatible options for required user confirmation, updates the overall/stage contracts, then Luna rewrites or extends RED tests against the resolved seams before Terra continues.

## Resume checks

- Re-read this file.
- Confirm only this progress file is reviewer-owned.
- Re-check `git status --short` because other agents share the worktree.
- Re-open the cited lines after any design edit; line numbers may move.
- Verify P0-1 through P0-3 before accepting `target/artifacts` GREEN.
