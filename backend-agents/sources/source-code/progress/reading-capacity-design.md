# Progress: Reading-check capacity design

- Status: COMPLETE (approved CHECK projection documentation and one-time tenant correction procedure frozen)
- Agent role: bounded design/documentation authority; no production implementation, model, Maven or subagent calls
- Model: gpt-6-astra / ultra
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: synchronize the explicitly approved CHECK-only navigation projection and exact one-time tenant reference correction; freeze the immutable import/provenance procedure using existing private functions
- Owning plan: docs/supplements/cross-object-process-reconstruction/acceptance.md and parent cross-object reading coordination
- Approved inputs: fixed 326 reviewed Activities; successful unguided selection 711d8936bd16a3a9749a5d94b28e5680059d738b1dcdb3c03696fb15a40e9888; offline preview fae6b3d375fe01cdbefa0c36ba18a8ebf48f52ec623bf7c7862a6de765bfe596
- Current branch/worktree: formal source-code checkout at 7d4dc01; only parent delivery/progress and preserved untracked progress are dirty

## Completed

- Read root, prototype, backend and source-code constraints and current module design/acceptance.
- Located actual preview input/schema files and their owning input/schema builders.
- Compared lossless duplicate removal plus shared exact enum definitions using both existing local token encodings; no model, build or production command was invoked.
- Recorded the bounded pending implementation delta and direct acceptance in the owning module design and acceptance document.
- Subsequently reviewed the minimum GREEN implementation read-only: full original Activity fields remain; the two synthetic duplicate arrays alone were removed; all local definitions/reference nodes are freshly allocated; empty process-ref arrays still expand to text items plus maxItems 0; global-selection builders and their existing common enum helpers remain separate and unchanged.
- Confirmed the named CHECK contract mismatch against the production Schema, Prompt and candidateUses parser, then synchronized the approved clarification into module-design, acceptance item 15 and prompts.zh-CN only.
- Completed final static review of the implemented CHECK minItems 1, PromptCatalog v2 mapping and v2 resource. The resource retains the previously inspected v1 business instructions and adds only the approved complete-final-set, null inheritance and delta-disposition clarification. No delivery-blocking finding in this bounded review.
- Synchronized the two explicitly approved deltas into module-design, acceptance, prompts and README; froze the one-time zero-call import procedure using existing exact-reuse, parse/fingerprint/source-mapping and immutable store functions. Selected existing reusedFromModelBatchId provenance chain instead of production metadata propagation.

## Current state

The user has now explicitly approved the CHECK-only projection (omit its unused statementDirectory, duplicate navigation cards for complete reviewed Activities, and terms on unread navigation cards) and the one-time tenant wrong-type handle mapping. This turn may edit only the four owning design documents, this progress file and the ignored one-page correction procedure. Original provider records, failed batch, all full Activity/source text and production code remain untouched. The exact correction pointer and unique destination have been verified. Normal process reuse drops extra container fields but always writes reusedFromModelBatchId: parent selected the smallest existing immutable provenance chain, so no production field propagation or new test protocol is required. Preserve every source batch/job and the original manifest; future records may locate reviewCorrection through the chain instead of repeating it.

Completed exactly five source-backed assertion checks. Accepted inventory pair b745bc59784554d5d246803bb0e0e64dcc6c44d2ddfcfcb821140da975f453f2 contains materially useful business semantics: concrete default/initial status values, audit/unaudit predicates, optional original-bill association and conditional progress write-back, rather than only receive/validate/save/return stages. Tenant raw response 428a6cf0bb5887a6280a8d4d3ed4f3e14b7239f827236d3ff43d9cf1be1f13bd also describes the tenant/user/role handoff and distinguishes UI selection from backend writing, but remains rejected for PROCESS_RULE_ACTIVITY_USE_INVALID and is not an accepted process. No additional consequential semantic error was established within the five checked assertions. This bounded sample says nothing about full repository acceptance. No raw response or product artifact was modified.

### Five reviewed assertions and actual packet sources

| Assertion | Actual packet source and conclusion |
| --- | --- |
| Inventory defaults/initial values | Candidate 02 S2111 DepotHeadService.java 1238–1245: empty status becomes 0, purchase_status is initialized unconditionally to 0, null pay_type becomes 现付; S2104 AccountHeadService.java 323–326 defaults financial status to 0. The output preserves these concrete values. |
| Audit/unaudit and progress guards | Candidate 02 S2111 DepotHeadService.java 742–803: audit target 1 requires current 0; unaudit target 0 requires current 1 and purchase_status 0; purchase_status 2/3 explicitly reject. Matches the reviewed rule; inventory checks remain conditional on actual switches. |
| Optional association, quantity check and progress write-back | Candidate 02 S2113 DepotItemService.java 521–533, 694–776: linkNumber/linkApply and quantity fields trigger checks, the over-link flag controls overrun rejection, empty batch results map to status 1 and partially fulfilled nonzero original lines map to 3; relevant subtype/link branches control status or purchase_status writes. S2106 AccountItemService.java 128–130 sets billId only when billNumber is supplied. The output does not invent mandatory source bills or a fixed inventory-to-finance sequence. |
| Tenant/user/role linkage | Candidate 01 S2117 TenantService.java 96–101 and S2118 UserService.java 608–658: initial user ID becomes user tenantId, UserRole keyid/tenantId and Tenant tenantId; empty limit/expiry uses configured defaults. The raw tenant explanation matches this concrete handoff. |
| UI/backend distinction | Candidate 01 S2113 RoleFunctionModal.vue 113–145 and S2124 UserDepotModal.vue 88–128: tree loading is separate from explicit confirmation, which chooses addUserBusiness/editUserBusiness after querying the association. The raw process separates selection support from independent backend write paths and does not claim a fixed automatic write sequence. |

## Changed files

- progress/reading-capacity-design.md
- docs/supplements/cross-object-process-reconstruction/module-design.md
- docs/supplements/cross-object-process-reconstruction/acceptance.md
- docs/supplements/cross-object-process-reconstruction/prompts.zh-CN.md
- docs/supplements/cross-object-process-reconstruction/README.md
- .workspace/cross-object-reading-v3-20260916/inspection/approved-tenant-correction-procedure.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | inspected | Shared worktree is dirty; this source tree is currently untracked by outer repository. |
| Read actual preview paths | found | Candidate 01 and 02 input/schema JSON are present. |
| In-memory token estimate of actual initial-reading-7f9ec5c80f99 exports using cached tiktoken | observed | CHECK 01 after input/schema dedup: 183203 / 197199; CHECK 02: 213543 / 230680 tokens (o200k / cl100k). |
| Same estimate of actual process-before-check exports | observed | DRAFT 01 after dedup: 90675 / 95286; DRAFT 02: 138489 / 146103 tokens. These precede any CHECK supplement and exclude Prompt/envelope/generation. |
| Read-only GREEN builder inspection | PASS within bounded review | Fresh root definitions and reference objects per invocation; original Activity/source fields, canonical directory, inline certainty and global-selection path preserved. |
| Attempted existing preview comparator | NOT VALID AS A BEFORE/AFTER CHECK | ec16453f682c was an older fake-selection package, not post-GREEN B. Candidate identity/materials differ; stopped that comparison. Parent will compare actual post-GREEN B with 7f9ec5c80f99. |
| CHECK contract read-only inspection | CONFIRMED | Schema activityUses had no minItems; production Prompt omitted full-final-set instructions; parser explicitly rejects empty uses. |
| Scoped git diff --check and reread of three updated contracts | PASS | Complete final collections, nullable-text inheritance, delta dispositions, private Prompt v2 and unchanged global selection are consistent. No production/test/build/model actions. |
| Final static CHECK implementation review | PASS within bounded scope | CHECK minItems 1 only; v2 map/resource aligned; prior business requirements preserved; no new parser, business constraint, source-read round, version bump or raw-response mutation path. |
| Guided-A actual input field counts (o200k / cl100k) | OBSERVED | Every CHECK has full global navigation 79594/89240, file directory 24560/24240 and Schema 23108/23045. Candidate 01 full Activities 38259/48066, source text 79505/78771, statement directory 61487/61315; candidate 02 29584/37312, 91354/90727, 46147/45862; candidate 03 26784/33609, 79505/78771, 41852/41672. |
| Pure duplicate-only proposed CHECK projection plus existing Prompt/wrapper/Schema | INSUFFICIENT CONSERVATIVELY | 01=241268/259060; 02=245584/261573; 03=231250/246275. Already-read duplicate card counts 19/14/13. Existing Prompt and known Provider wrapper cost approximately 620/875. |
| Exact navigation source-ref list sharing | NOT USEFUL | 6182 ref occurrences, 1640 distinct refs, but all 326 complete lists are distinct; sharing lists increases size. |
| One bounded alternative: additionally omit unread navigation terms | MEASURED, NOT IMPLEMENTED | Known Prompt/input/Schema totals 01=223544/237236, 02=227316/239094, 03=212839/223596; exact original selected Activity/source objects and all Activity/file recall identities were compared unchanged in memory. |
| One-pass B sample review | COMPLETE | Exactly five assertions checked against actual packet source; no new consequential semantic error established. Inventory accepted and tenant raw-rejected are reported separately. |
| Known tenant invalid rule reference | CONFIRMED, NOT CHANGED BY THIS AGENT | 用户角色关联 contains activity:f557933b36ff5eb47a60b6c300ceb86e99d271431bc499dfd636c879ab05207c/activitySteps/9 in activityUseLocalIds; the process has the unique matching useLocalId activity:f557933b36ff5eb47a60b6c300ceb86e99d271431bc499dfd636c879ab05207c. User subsequently explicitly approved only this mapping. |
| Final scoped documentation diff and reread | PASS | Four durable docs consistently preserve full Activity/source text, unchanged global/final process input, CHECK v2, one fixed correction, immutable raw/FAILED run and existing provenance chain; git diff --check exited 0. |
| Original tenant records and fixed-pointer recheck | PASS, read only | DRAFT file SHA 059bd620efd8d026399f578a067662b7a1822dbdddd10d2f906cb0e7a45e2855; REVIEW file SHA e52cd9693c5fb5a7cc932e059474d308319e99c9a96a9c5a7849d4f60471fc1e. Exact pointer and unique target, REVIEW.actualDraft, original complete packet and both Schema values all matched. |
| Process reuse provenance seam inspection | Existing chain sufficient | reopenProcessPair omits extra metadata, but saveProcessPair(reused=true) always writes its actual source batch ID. Stable jobKey allows following preserved batch records back to imported reviewCorrection/manifest; no production seam addition required. |

## Decisions

- Product generation in this design task: none.
- No build, tests, JDT, Builder, Activity or nine-chapter invocation.
- No new parser, persistence subsystem, public API or arbitrary capacity threshold.
- Prefer duplicate removal plus named shared enum definitions over short-key conversion: both observed first packets fit without changing canonical identifiers, parsing or persistence.
- Largest estimated CHECK input plus Schema is 230680 tokens; observed effective 258400 context leaves 27720 before Prompt/envelope/generation. This is an observation, not an invented budget gate or a claim that every later supplemented packet fits.
- Target production file is DefaultBusinessProcessDiscovery.java only unless direct tests expose another necessary seam. Target directly affected tests: BusinessProcessReadingPipelineTest, BusinessProcessDiscoveryTest and BusinessProcessAcceptanceSampleTest; scripted providers must take refs from unchanged packet statementDirectory rather than removed synthetic arrays.
- Shared Schema definitions are task-local to CHECK/process. Do not change materialSelectionSchema or common helpers in a way that changes its serialized bytes. Existing fingerprint hashes already include actual input/Schema; producer/container versions remain unchanged.
- Named subsequent correction: only CHECK activityUses receives minItems 1; private CHECK Prompt v2 explicitly requires complete final activityUses/contextActivityIds, with unchanged sets echoed in full. Empty context is a real empty final set, not inheritance. changedActivityDispositions stays delta and the three nullable text fields keep null inheritance. Global selection is untouched; no public, producer or output-container version change.
- Existing empty-member raw responses remain immutable and must not be programmatically patched into valid decisions. Repeating real CHECK requires explicit user authorization; this design task does not provide it.
- Guided-A diagnosis proposal is confined to readingCheckInput: remove only its statementDirectory, filter navigation cards whose complete Activity is already in this packet, then (only if explicitly approved) omit terms from remaining navigation cards. Leave packetInput/processInput, all final DRAFT/REVIEW packets, readingCheckSchema allowlists, global selection and canonical IDs unchanged. No parser, alias framework, source truncation, business keywords or extra reading call.
- The third projection change is a model-visible navigation tradeoff and has now received explicit user approval; names, business purposes, business objects and source refs still support recall for every unread Activity, but exact terminology-only cues are absent from CHECK navigation. The preceding global selection still sees its full navigation.
- Current processDiscovery maxModelOutputBytes is 500000; CodexSubscription passes no matching max-output-token setting. This byte limit is not a configured token reserve and cannot substantiate a claim that remaining context covers every possible output. The measured headroom excludes CLI/runtime overhead and actual generation.
- Approved tenant import uses original B745 exact decisions through discoverCatalogSample/prepareReadingPackets under zero-call guards, both packets for normalizeSources, inventory exact pair reuse and tenant-only fixed mapping. Reuse misses stop; a new CHECK projection may intentionally invalidate original CHECK fingerprints, so the import must use an already verified implementation with matching original input, never bypass fingerprints.
- Only the first imported tenant record needs reviewCorrection={manifestPath,manifestSha256}. Later explicit reuse follows existing immutable reusedFromModelBatchId/same-jobKey records to that manifest; retain the entire source chain. No new production fields, resolver, parser, protocol, output version or automatic repair is needed.

## Blockers

- None for the design audit. Actual product requests remain outside this subtask. Prompt/envelope/output headroom and supplemented final packets require parent observation before any dependent product calls.

## Exact next action

Return the frozen procedure and four synchronized documents to parent. Parent may implement/run the explicitly authorized ignored utility; this subtask does not execute it, add tests/production changes, invoke builds/models or complete the owning plan.

## Resume checks

Read this progress file, inspect git status, and re-open the cited actual preview files without modifying them.

## Plan closeout destinations

- Durable decisions: docs/supplements/cross-object-process-reconstruction/module-design.md
- Remaining issues: parent task capacity and product-call authorization decisions
- Verification and output references: parent delivery record

Keep this handoff while the plan is active; the subtask does not complete the owning plan.
