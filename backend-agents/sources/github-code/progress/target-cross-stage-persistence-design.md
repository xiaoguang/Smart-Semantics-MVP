# Progress: Cross-stage target persistence design

- Status: COMPLETE
- Agent role: sole Sol/ultra Design Authority
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-31
- Last updated: 2026-09-01
- Scope: Record the user-approved cross-stage persistence contract in authoritative GitHub Code Agent design/stage documents and maintain the approved plan/navigation references only; do not change code, tests, Maven configuration, runtime state, or source captures.
- Approved inputs: The eight approved persistence decisions in the task brief, including the P1 registry/path and Git CLI isolation corrections; no further architecture choice is authorized.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read the repository, backend-agent, and GitHub Code Agent `AGENTS.md` files in full.
- Confirmed pre-existing worktree changes and the docs-only ownership boundary for this task.
- Loaded the applicable design, domain-modeling, and planning instructions.
- Patched the first shared DESIGN tranche: the run-tree locations, stage-store-last receipt rule, Stage01 denominator equations, and isolated Git CLI environment contract now match the approved package.
- Completed the main DESIGN contract tranche: three deep stores with shared private atomic machinery, content-addressed policy registry, safe address value types, framed descriptor/root/receipt formulas, exact `analysis-run-request-v2`, Stage08 root-manifest order, and durable hashed event/claim protocol.
- Closed the module/stage media distinction: module payloads remain canonical JSON/JSONL, while the stage store alone accepts the policy-selected Stage08 `text/markdown` / `RAW_UTF8` artifact and validates its exact bytes and identity.
- Reworked Stage01 M3 into an acyclic exact-three semantic publication: M3 has its own module receipt first; `CanonicalStageArtifactStore` reopens it, installs the three public payloads, and creates the stage receipt last. Removed the old self-referential root/receipt example.
- Aligned Stage06 with `analysis-run-request-v2` and replaced its cyclic publisher summary with an exact-nine semantic `InterpretationPublicationSpecifier` followed by the stage store.
- Defined Stage08's acyclic eight-output sequence and standalone schemas: five semantic stage payloads, archive manifest, stage receipt, root run manifest, then the M4 coordinator publication/receipt; validation and durable event/resume language now point to the shared DESIGN contract.
- Replaced Stage02's cyclic publisher/root example with an exact-four semantic `Stage02PublicationSpecifier`; its module receipt precedes the stage store's five-file reader-visible set.
- Replaced Stage03's cyclic graph-set publisher with an exact-seven semantic `ProgramGraphSetPublicationSpecifier`; the stage store alone adds the eighth reader-visible receipt.
- Replaced Stage04's cyclic fact-ledger publisher with an exact-four semantic `FactLedgerPublicationSpecifier`; the stage store alone adds the fifth reader-visible receipt.
- Replaced Stage05's cyclic flow publisher with an exact-five semantic `FlowPublicationSpecifier`; the stage store alone adds the sixth reader-visible receipt.
- Replaced Stage07's cyclic knowledge publisher with an exact-five semantic `KnowledgePublicationSpecifier`; the stage store alone adds the sixth reader-visible receipt.
- Retired the registry-blind public `ModuleArtifact.parse(byte[], Class<T>)` target seam: policy/envelope/identity validation is owned exclusively by `CanonicalModuleArtifactStore.install/reopen`, with domain decoding only after a `VerifiedCanonicalPayload` is returned.
- Closed Stage08's media-policy edge: module publications cannot contain raw Markdown; only `CanonicalStageArtifactStore` accepts the registered `STAGE08_DOCUMENT_MARKDOWN` / `stage08-document-markdown-v1` `text/markdown` + `RAW_UTF8` payload and validates its canonical bytes and identity.
- Completed a fresh focused consistency pass and recorded exact selector impact; no code, test, POM, source-capture, Maven, model, network, commit, or push action was performed.
- Correction pass: repaired the authoritative Stage03 receipt illustration with the full M6 `ModulePublicationReference.address`, `profileSha256`, and all seven UTF-8 filename-ordered semantic descriptors; also froze the rule that persisted producer/module/fixture ownership uses only the compiled lowercase `moduleKey`, while implementation class names remain descriptive metadata.
- Correction pass: aligned every Stage01–08 walkthrough `module`, technical `producer.module`, and test-fixture leaf with the compiled registry (`request-admission` through `archive`, plus `run-validator`/`run-resumer`); removed publisher/class-name fixture aliases and supplied Stage02's previously unspecified `publish` fixture owner.
- Correction pass: made the Stage01 capture receipt field-complete and grammar-valid, replaced the M1/M2/M3 examples with compact exact wire fixtures, kept M1/M2 as `ModuleArtifact<T>` envelopes while emitting M3 as two standalone JSON documents plus JSONL, and closed snapshot identity/policy/count/partition/shard/accounting fields without stale count aliases.
- Correction pass: made the Stage08 ReaderItem illustration field-complete without an undeclared `sectionKey`, converted every ArtifactReference in the executable examples to `<prefix>:<64 lowercase hex>`, converted renderer profiles to typed references, and made M4 carry a full Stage08 `StagePublicationReference.address` plus the dedicated `RunManifestReference(address, runManifestId, runManifestSha256)` shape while remaining observation-only and acyclic.
- Correction pass: aligned Stage02's ApplicationProfile and EntryPoint illustrations, module schemas, brief, and record list on the same exact fields (`inventoryScopeKind`, signal dispositions, typed capability-profile reference, route parts, handler FQN, parameter names, and route locators); removed stale profile/entry aliases from the walkthrough.
- Correction pass: separated Stage04 `CodeFact`, nested `FactAtom`, `Proof`, `FactDisposition`, and `AtomDisposition` into exact non-overlapping records; replaced the invalid `atomIds`/proof/disposition example with a compact schema-valid fixture containing full proof closure and nullable disposition fields.
- Correction pass: completed Stage06 registry dispositions with required gap/failure/reason fields, expanded R1/R2 tasks with exact input/schema/prompt hashes and expected runtime, and completed ModelRound/GenerationReceipt examples with response hashes, attempt/start lineage, ordinal, and observed runtime; aligned the M6 implementation leaf to `publish`.
- Correction pass: replaced Stage07's stale knowledge projection with the complete `KnowledgeItem` record (`knowledgeItemId`, typed anchor ID, lineage, owner-flow and outcome-path sets), aligned the walkthrough name, and removed the undeclared `ownedFactAtomIds` accounting field while preserving the declared ownership denominator.
- Closed all four independent-review P1 groups and passed the fresh docs-only verification gate. The correction is ready for a new independent Sol/ultra review; no additional architecture choice or user approval is required.
- Round-2 P1-1/2: made the DESIGN StageReceipt illustration grammar-valid and structurally typed, removed every fake `stageNN-publication:*` reference, and made Stage08 M4 preserve all eight complete `StagePublicationReference` values plus the dedicated `RunManifestReference` without changing the acyclic install order.
- Round-2 P1-3: rewrote every exact module envelope's `upstreamArtifacts` as a sorted set of the direct semantic/preimage `ArtifactReference` values that the module actually reads; publisher-specification dependencies remain bound once by their install request/receipt, and runtime ledger/lock facts are not forged as content artifacts.
- Round-2 P1-4/5: separated the validator and resumer into run-level module-01 `ValidationModuleAddress`/`ResumeModuleAddress` contracts with exact external fixture/production roots, leaving Stage08 at M1-M4; froze one exact `CapabilitySite` shape and two closed site kinds used consistently by Stage02 M2/M3.
- Round-2 P1-6/7: made every Stage03 `graphProfileRef` a full `graph-profile` ArtifactReference repeated in both payload and direct upstream set, and replaced the illegal Stage05 atom-set obligation with two existing `ATOM_DIRECT_SEMANTICS` obligations.
- Round-2 P1-8/9: made `NARROW` preserve `selectedKey == provisionalKey` byte-for-byte across DESIGN/Stage06/Stage07, normalized the stable new-run failure code to `RUN_NEW_RUN_REQUIRED`, and gave Stage01 the full shared policy/module/stage store request-invalid/collision/publication-invalid code families.
- Round-3 Stage02 resume unit: froze `CapabilityEvidenceRefV2(kind,sourceExcerpt,artifactEvidence)` with a versioned artifact-evidence variant, added two field-complete isolated `CapabilitySiteV2` structural specimens using `SourceLocatorV1`/continuous `SourceExcerptV1`, and removed stale v1 wire-version claims from the narrative projections. The narrative string locators remain explicitly non-wire and cannot seed fixtures or cross-stage remaps.
- Round-3 Stage03 unit: retained the corrected Stage01/02 typed receipt lineage; versioned M5/public evidence schemas to `stage03-evidence-graph-draft-v2` / `stage03-evidence-graph-v2`; froze the closed `EvidenceNodeV2` source-excerpt/rule-application union; added two complete structural variants; and isolated the five large DepotHead graph projections as narrative so their legacy string locators/story IDs cannot be mistaken for wire or cross-stage remapping.
- Round-3 Stage05 unit: versioned the capsule projection to `stage05-capsule-projection-v3`, replaced the parallel locator/hash/excerpt fields with one complete `SourceExcerptV1`, required discontinuous evidence to be separate ordered spans, aligned `EvidenceCapsule` on span/obligation IDs, added two field-complete continuous structural specimens, fixed the authoritative cross-reference to DESIGN §13.2, and isolated the legacy DepotHead projection as narrative rather than a replayable wire fixture.
- Round-3 Stage06 unit: versioned R1/R2 tasks and execution to `stage06-flow-task-set-v3` / `stage06-model-execution-set-v3`; persisted the full canonical provider `inputJson` (including the closed Capsule view and full same-Flow registry items) beside its recomputed hash; made every `ModelRound.startedReceiptId` equal the corresponding `GenerationReceipt.receiptId`; and froze the framed `flowInterpretationDispositionId` formula used by the standalone disposition and repository coverage ledger.
- Round-3 Stage07 unit: made every JSON fence locally classified, retained exact `NARROW` key preservation, versioned admission to `stage07-admission-decision-set-v3`, and added the direct `stage06DispositionId` link so Stage07 and RepositoryCoverageLedger preserve the computed Stage06 disposition identity rather than remapping by flow.
- Round-3 Stage08 unit: replaced the obsolete synthetic six-envelope projection with exact closed `ReaderItemV3`/template-slot and typed `TraceRecordV3`/`TraceHopV3` contracts; kept only a minimal strict renderer byte golden whose nine H2 UTF-8 lines recompute to 150 bytes and SHA-256 `67e742a00672cbd230b230b4c29ef36a0aa9aa5ea2b6c460e286160e58fca4e5`.
- Round-3 Stage08 DAG hygiene: removed the last prose-only future-publication dependency by stating that M2 reads the already installed M1 plan-draft module payload, while the post-run external rerender/validator reads the later public `nine-section-plan.json`; both paths are plan-only and must produce identical Markdown bytes.
- Round-3 exterior closure: versioned validator/resumer to v4/v3, bound every actual direct artifact/source preimage and the same eight typed stage publications/root chain, typed the validator's M4 link as the exact `ModulePublicationReference`, closed validation check/error codes, made committed-event references an ordered subset of envelope upstreams, and exposed one sealed `STAGE | VALIDATION | RESUME` publication address through core/CLI/HTTP inspection.
- Round-3 identity/taxonomy closure: made `nine-section-plan.json.artifactId` the sole standalone self ID while public `nineSectionPlanId` aliases it, classified every JSON/JSONL fence as narrative, non-replay structural, or strict replay, and prohibited implicit ID remaps or structural specimens from being used as replay goldens.
- Round-3 receipt shape closure: aligned the DESIGN Stage03 structural receipt and Stage08 standalone schema on one exact `stage-receipt-v1` field vocabulary (`stageNumber`, `publicationProvenance`, `upstreamStageReferences`, descriptor-valued `archiveManifest`, status/gap accounting), removing the last alternate Stage08-only field aliases.
- Round-3 Stage08 record closure: aligned the public text records with the exact standalone tables by restoring request/candidate/run-manifest schema versions, the plan repository cardinality, canonical artifact metadata, typed references, and the same component order; the text block no longer implies a smaller alternate wire shape.
- Added the approved standards/toolchain plan to scoped README and AGENTS navigation; implementation agents must read it before POM/config/code/test work. These concise links do not alter the authoritative eight-stage architecture.
- Completed the comprehensive Round-3 docs-only gate after the final Stage08 record/DAG corrections: all JSON/JSONL units parse, every machine-data fence has exactly one example taxonomy, structural/strict IDs and SHAs satisfy grammar, all source excerpts use the one typed continuous-span contract, module addresses/upstreams match the compiled registry, all 34 module briefs bind exact upstreams, stage output counts remain `4/5/8/5/6/10/6/8`, all 19 Round-3 contract assertions and 11 Stage08 DAG assertions pass, the strict renderer golden recomputes, stale terms are absent, plan/navigation/links/fences are clean, and no architecture decision remains open.

## Current state

- **ROUND-3 COMPLETE (2026-09-01):** The sixteen exact-fixture P1 groups are corrected within the approved architecture, the comprehensive docs-only gate passes, and this package is ready for a fresh independent Sol/ultra architecture review. No new cross-stage business or architecture decision was needed.
- The separate approved standards/toolchain plan at `docs/plans/target-standards-and-toolchain-plan.md` is complete, discoverable from README/AGENTS, and does not conflict with this contract. Its future foundation work remains separate from this docs-only correction.
- The complete reviewer-owned source of truth was applied without weakening exact records/formulas: narrative snippets remain non-wire, structural specimens remain field/type complete but non-replayable, and the sole strict replay golden is the independently recomputed Stage08 renderer byte seam.
- Round-2 independent review found zero P0 and nine local-alignment P1 groups covering exact IDs, typed stage references, direct dependency closure, external validator/resumer addresses, Stage02 capability sites, Stage03 graph profile identity, a Stage05 enum, Stage07 NARROW lineage, and normalized lifecycle/store errors.
- All nine round-2 P1 corrections are applied and the fresh full docs-only mechanical gate passed. Each finding was checked against the already approved architecture; none required a new cross-stage decision.
- The complete cross-stage contract remains acyclic and path-free: module publication/specification precedes stage publication, the stage receipt is last in its stage set, the root manifest follows Stage08, and M4 observes the finished references without contributing to either root.
- Existing changes outside the named design/plan/navigation/progress documents remain untouched. No code, test, POM, `.gitignore`, source capture, Maven, model, network, commit, or push action was performed by this task.

## Corrected review classes and line sets

- P1-1: `docs/DESIGN.md:639-668` — complete Stage03 M6 `ModulePublicationReference.address`, `profileSha256`, and seven filename-ordered semantic descriptors.
- P1-2: `docs/DESIGN.md:1318-1333` plus all Stage01-08 module walkthroughs, `producer.module` fields, module directories, and fixture paths — exact compiled lowercase `moduleKey` ownership; implementation class names are separate metadata only.
- P1-3 Stage01: `docs/stages/01-freeze-source.md:29-52,109-117,239-268` — field-complete capture receipt, corrected denominators, typed M3 source refs, exact envelope/standalone separation, policy and verified snapshot fields.
- P1-3 Stage02: `docs/stages/02-discover-application-and-entries.md:23-87,207-217,231-249` — exact `ApplicationProfile`, typed capability reference, and `EntryPoint` records/examples.
- P1-3 Stage04: `docs/stages/04-prove-code-facts.md:181-231` — exact `CodeFact`, `FactAtom`, `Proof`, `FactDisposition`, and `AtomDisposition` shapes.
- P1-3 Stage06: `docs/stages/06-interpret-one-flow-at-a-time.md:207-220,246-273` — exact registry disposition, R0/R1/R2 task hashes/runtime, `ModelRound`, and `GenerationReceipt` fields.
- P1-3 Stage07: `docs/stages/07-admit-and-merge-business-knowledge.md:65-82,190-202` — complete `KnowledgeItem` and declared accounting fields only.
- P1-4 Stage08: `docs/stages/08-build-nine-section-document-and-archive.md:24-49,240-269` — complete `ReaderItem`, grammar-valid IDs, full `StagePublicationReference.address`, and typed `RunManifestReference`, with observation-only M4 ordering.

### Round-3 corrected review classes and line sets

- Example taxonomy and replay boundary: `docs/DESIGN.md:44-52` and each Stage01–08 document `:5` — exactly `NARRATIVE_ILLUSTRATION | STRUCTURAL_WIRE_SPECIMEN | STRICT_REPLAY_GOLDEN`; structural specimens stay field/type complete but explicitly non-replayable, with no silent cross-specimen ID remap.
- P1-1 Stage03 receipt lineage: `docs/DESIGN.md:635-704` — full Stage01/02 `StagePublicationReference` values, M6 specification-module address, seven ordered semantic descriptors, and one exact `stage-receipt-v1` vocabulary.
- P1-2 Stage01 M3 closure: `docs/stages/01-freeze-source.md:239-275` — typed `Stage01PublicationSpecificationInputV1`, direct M1/M2 payload plus run-request/frozen-request four-ref preimage, exact-three payload install, and receipt-last stage publication without hidden controls or self-cycle.
- P1-3/4 unified source and Stage02 capability contracts: `docs/DESIGN.md:1256-1273`; `docs/stages/02-discover-application-and-entries.md:205-240` — one `SourceLocatorV1`/`SourceExcerptV1`, closed signal/site/evidence enums, versioned artifact evidence, and two complete isolated `CapabilitySiteV2` variants.
- P1-3 Stage03/Stage05 source evidence: `docs/stages/03-build-five-program-graphs.md:238-277`; `docs/stages/05-compile-business-flows.md:187-216` — closed `EvidenceNodeV2`, versioned evidence graph, `ModelEvidenceSpanV3`, continuous raw spans, and explicit splitting of discontinuous evidence.
- P1-5 replay identity boundaries: `docs/DESIGN.md:44-52` plus the adjacent classification before every JSON/JSONL fence in Stages01–08 — no illustrative/story ID is an implicit cross-stage remap; strict fixtures must own and recompute their complete preimage closure.
- P1-6/7/8 Stage06 recoverability and identity: `docs/stages/06-interpret-one-flow-at-a-time.md:204-323` and `docs/DESIGN.md:1231-1239` — v3 R1/R2 tasks persist complete canonical `inputJson`, round `startedReceiptId` equals the matching generation receipt, and the framed `flowInterpretationDispositionId` is shared with the repository ledger.
- P1-8 Stage07 lineage: `docs/stages/07-admit-and-merge-business-knowledge.md:194-216,217-320` — admission v3 binds the exact Stage06 disposition identity and preserves `selectedKey == provisionalKey` for NARROW while narrowing only allowed decision/basis/meaning eligibility.
- P1-9 Reader/Trace union: `docs/DESIGN.md:1074-1160,1244-1254`; `docs/stages/08-build-nine-section-document-and-archive.md:239-267,350-478` — eight closed ReaderItem/template/typed-slot pairs, typed EMPTY profile/reason identity, five-variant Trace hops, and one profile/source lineage.
- P1-10 strict renderer: `docs/stages/08-build-nine-section-document-and-archive.md:302-320` — exact nine-H2 UTF-8/LF bytes recompute to 150 bytes and SHA-256 `67e742a00672cbd230b230b4c29ef36a0aa9aa5ea2b6c460e286160e58fca4e5`.
- P1-11–15 coverage/validator/resumer closure: `docs/DESIGN.md:1140-1173,1246-1254`; `docs/stages/08-build-nine-section-document-and-archive.md:268-281,456-478,606-625` — one coverage-ledger reference, v4 validator/v3 resumer direct-preimage sets, eight typed stage refs/root equality, committed-event subset semantics, and closed validation check/error codes.
- P1-16 exterior inspection: `docs/DESIGN.md:1183-1242`; `docs/stages/08-build-nine-section-document-and-archive.md:322-532` — public query/view uses the sealed `STAGE | VALIDATION | RESUME` module publication address, so external modules never forge a stage number.
- Stage08 DAG/self-identity closure: `docs/stages/08-build-nine-section-document-and-archive.md:268-320,522-532` — M2 reads only installed M1 plan-draft bytes; eight standalone schemas and `five semantic → archive → receipt → root manifest → M4` order are exact; `artifactId` is the sole plan wire self-ID and public `nineSectionPlanId` aliases it.
- Approved-plan discoverability/host toolchain: `README.md:34`, `AGENTS.md:98-105`, `docs/plans/target-standards-and-toolchain-plan.md:33-66,193-219,375-431` — approved/default-authorized status, project-local `.mvn/toolchains.xml`, `mvn -t` prefix, deferred/environment gates, and concise implementation-agent navigation.

### Round-2 corrected review classes and line sets

- P1-1: `docs/DESIGN.md:627-671` — the exact Stage03 `StageReceipt` fixture now uses grammar-valid content IDs, a full M6 `ModulePublicationReference.address`, and all seven semantic descriptors.
- P1-2: `docs/DESIGN.md:1246-1352`; module briefs/fixture ownership in `docs/stages/01-freeze-source.md:203-245`, `02-discover-application-and-entries.md:142-184`, `03-build-five-program-graphs.md:147-235`, `04-prove-code-facts.md:129-175`, `05-compile-business-flows.md:140-186`, `06-interpret-one-flow-at-a-time.md:113-201`, `07-admit-and-merge-business-knowledge.md:138-184`, and `08-build-nine-section-document-and-archive.md:145-229` — every persisted owner, producer address, module directory, and fixture leaf uses the compiled lowercase `moduleKey`; implementation class names remain separate metadata.
- P1-3: the exact upstream tables/fixtures at Stage01 `:252-269`, Stage02 `:205-224`, Stage03 `:238-260`, Stage04 `:178-192`, Stage05 `:189-199`, Stage06 `:203-225`, Stage07 `:190-205`, and Stage08 `:235-273` bind every direct content/preimage dependency; seven publisher-specification install requests bind their dependencies once without forging wrappers.
- P1-4: `docs/DESIGN.md:1246-1250,1298-1302,1340-1352`; `docs/stages/08-build-nine-section-document-and-archive.md:145-229,235-273` — validation/resume are external module-01 address variants with external fixture/production roots; Stage08 remains M1-M4 and M4 observes eight typed stage references plus one typed run-manifest reference without a self-cycle.
- P1-5: `docs/stages/02-discover-application-and-entries.md:205-224,264` — one exact `CapabilitySite` record, two closed kinds, and field-complete M2/M3 fixtures.
- P1-6: `docs/stages/03-build-five-program-graphs.md:238-260,278,341` — `graphProfileRef` is one full `graph-profile` `ArtifactReference` in every M1-M5 upstream set and graph payload.
- P1-7: `docs/stages/05-compile-business-flows.md:189-199,296` — the fixture uses two declared `ATOM_DIRECT_SEMANTICS` obligations; the undeclared set-level enum appears only in an explicit prohibition.
- P1-8: `docs/DESIGN.md:516,553,958`; `docs/stages/06-interpret-one-flow-at-a-time.md:294`; `docs/stages/07-admit-and-merge-business-knowledge.md:44,150,190-205,300` — `NARROW` preserves `selectedKey == provisionalKey` byte-for-byte and narrows only decision, closed basis, or meaning eligibility.
- P1-9: `docs/DESIGN.md:780,1531`; `docs/stages/01-freeze-source.md:318`; `docs/stages/08-build-nine-section-document-and-archive.md:242,556` — the runtime failure is consistently `RUN_NEW_RUN_REQUIRED`, while Stage01 lists the exact shared module/stage store request, collision, invalid-publication, policy, and atomic-move codes.

## Changed files

- `progress/target-cross-stage-persistence-design.md` (this task's progress record)
- `docs/DESIGN.md` (authoritative cross-stage contract)
- `docs/stages/01-freeze-source.md`
- `docs/stages/02-discover-application-and-entries.md`
- `docs/stages/03-build-five-program-graphs.md`
- `docs/stages/04-prove-code-facts.md`
- `docs/stages/05-compile-business-flows.md`
- `docs/stages/06-interpret-one-flow-at-a-time.md`
- `docs/stages/07-admit-and-merge-business-knowledge.md`
- `docs/stages/08-build-nine-section-document-and-archive.md`
- `README.md` (one plan-navigation link)
- `AGENTS.md` (one implementation-agent plan-reading rule and sealed artifact-view terminology)
- `docs/plans/target-standards-and-toolchain-plan.md` (approved status/default-authority and project-local Toolchains maintenance)
- `progress/target-standards-and-toolchain-plan.md` (separate owned plan-progress record)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `node <<'NODE'` fenced JSON/JSONL parser over DESIGN + Stages01–08 | PASS | `files=9 json_blocks=13 jsonl_blocks=18 jsonl_records=64 fence_errors=0 parse_errors=0`. |
| same parser's taxonomy and structural/strict grammar pass | PASS | `classified_json_jsonl_blocks=31 taxonomy_errors=0`; `structural_blocks=11 strict_blocks=1 artifact_ids=111 sha_values=165 root_ids=4 run_ids=13 grammar_errors=0`. |
| `node <<'NODE'` structural `SourceExcerptV1`/`SourceLocatorV1` validator | PASS | `structural_source_excerpts=11 typed_locators=11 source_contract_errors=0`. |
| `node <<'NODE'` compiled stage/module address and upstream ordering validator over every JSON/JSONL projection | PASS | `producer_addresses=21 stage_addresses=21 upstream_groups=21 upstream_refs=152 mapping_or_order_errors=0`. |
| `node <<'NODE'` Stage01–08 module-brief/upstream/schema-table validator | PASS | `stage_docs=8 module_contract_sections=34 exact_upstream_briefs=34 wire_schema_rows=30 publisher_dependency_groups=7 errors=0`. |
| `node <<'NODE'` observable stage-output table counter | PASS | `observable_file_counts=S1:4,S2:5,S3:8,S4:5,S5:6,S6:10,S7:6,S8:8 errors=0`. |
| `node <<'NODE'` fixed Round-3 P1-1..16 plus M2/self-ID/order contract checklist | PASS | `contract_checks=19 passed=19 errors=0`; Stage03 upstream/descriptors `2/7`, Reader kinds `8`, validation codes `9`, R1/R2 tasks `2`. |
| `node <<'NODE'` Stage08 standalone/DAG validator | PASS | `stage08_dag_assertions=11 passed=11 errors=0`; `standalone_outputs=8 stage_modules=4 external_modules=2`. |
| `node <<'NODE'` strict renderer byte recomputation | PASS | `renderer_headings=9 utf8_bytes=150 sha256=67e742a00672cbd230b230b4c29ef36a0aa9aa5ea2b6c460e286160e58fca4e5 embedded_match=true`. |
| `node <<'NODE'` stale-term/fake-publication scan | PASS | request-v1, fake stage-publication IDs, Stage08 M5/M6, old exterior paths/versions/receipt aliases/profile/source aliases and M2 future-public-plan dependency are all `0`; `residual_errors=0`. |
| `node <<'NODE'` prohibition-only/lifecycle code scan | PASS | `ATOM_SET_DIRECT_SEMANTICS=1` and `REPOSITORY_SCOPE_NOT_COMPLETE=1`, both only explicit prohibitions; standalone `NEW_RUN_REQUIRED=0`. |
| `node <<'NODE'` approved plan/navigation gate | PASS | `plan_lines=431 checkbox_tasks=51 official_links=23 executable_maven_commands=12 shorthand_selector_mentions=1 bad_maven_prefixes=0 stale_approval_markers=0 navigation_links=2 errors=0`. |
| `node <<'NODE'` local-link and all-doc fence checks | PASS | `files=12 local_links=41 broken_local_links=0`; `files=14 opened_fences=84 unclosed_fences=0`. |
| `git diff --check -- <scoped tracked docs>` plus a trailing-whitespace scan over the 14 scoped docs/progress files | PASS | Exit 0/no output; no whitespace errors. Status/name/numstat was inspected separately so unrelated pre-existing implementation/config changes remain outside this task. |
| `git status --short -- <scope>`, `git diff --name-only -- <tracked scope>`, `git diff --numstat -- <tracked scope>` | PASS | Tracked: AGENTS `+18/-2`, README `+1/-0`, DESIGN `+765/-109`, Stage01 `+196/-94`, Stage02 `+111/-54`, Stage03 `+65/-38`, Stage04 `+41/-31`, Stage05 `+46/-28`, Stage06 `+90/-45`, Stage07 `+55/-31`, Stage08 `+314/-172`. The final plan and both owned progress files are untracked new docs; `docs/plans/` contains only `target-standards-and-toolchain-plan.md`. |

No Maven or test selector was run because this work unit was expressly docs-only.

## Selector impact

- `Stage01ArtifactPublisherTest` is replaced by `Stage01PublicationSpecifierTest`.
- `Stage02ArtifactPublisherTest` is replaced by `Stage02PublicationSpecifierTest`.
- `Stage03ProgramGraphSetPublisherTest` is replaced by `Stage03ProgramGraphSetPublicationSpecifierTest`.
- `Stage04FactLedgerPublisherTest` is replaced by `Stage04FactLedgerPublicationSpecifierTest`.
- `Stage05FlowArtifactPublisherTest` is replaced by `Stage05FlowPublicationSpecifierTest`.
- `Stage06InterpretationArtifactPublisherTest` is replaced by `Stage06InterpretationPublicationSpecifierTest`.
- `Stage07KnowledgeArtifactPublisherTest` is replaced by `Stage07KnowledgePublicationSpecifierTest`.
- `Stage08CandidateRunArchiverTest` keeps its name but its contract expands to exact request-v2 lineage, the five-semantic → archive → stage receipt → root manifest → M4 order, every crash/recovery boundary, and real module/stage/run-manifest stores.
- `Stage08IndependentRunValidatorTest` is replaced by external `IndependentRunValidatorTest`, with fixtures at `src/test/resources/target/validation/run-validator/`.
- `Stage08RunResumerTest` is replaced by external `RunResumerTest`, with fixtures at `src/test/resources/target/resume/run-resumer/`.
- Future shared-persistence selectors must cover the three public stores and the content-addressed policy registry; any selector or golden using the retired `ModuleArtifact.parse(byte[], Class<T>)` seam is invalid. This design work did not rename or edit existing test files.

## Decisions

- Treat the user's “批准推荐方案” plus the supplied eight-item package as the completed architectural approval gate.
- Preserve target/current audit separation, complete-repository scope, and stage-document-only maturity claims.
- Use `CanonicalModuleArtifactStore`, sibling `CanonicalStageArtifactStore`, and `CanonicalRunManifestStore` as deep, path-free public seams backed by shared private atomic-filesystem machinery.

## Blockers

- None.

## Exact next action

- Launch a fresh independent Sol/ultra architecture review over this completed docs package. If it passes, the parent task may perform the user-directed docs-only commit/non-force push; implementation/toolchain foundation work starts only afterward under the approved plan.

## Resume checks

- Re-read this file and run `git status --short`.
- Reconfirm that no code, test, POM, source capture, Maven, model, or network action is in scope.
- Reinspect diffs for authoritative docs before any review correction so concurrent/pre-existing edits are preserved.
