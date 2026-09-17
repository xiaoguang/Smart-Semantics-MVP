# Source Code Analysis Agent Instructions

## Scope and target identity

- This directory owns only the Java/Maven frozen-source-to-business-process-
  catalog-and-nine-section Agent, its tests, fixtures, design, CLI, Java
  Interface, and future authenticated loopback HTTP adapter.
- The target directory is backend-agents/sources/source-code/, the Maven
  coordinate is org.sourceanalysis:source-code-analysis-agent, the display
  name is Source Code Analysis Agent, and the Java root is
  org.sourceanalysis.app.
- The approved directory, Maven, and Java-package reset is complete. The old
  sources/github-code/ path may occur only in Git history, preserved
  progress/history documents, completed migration plans, or unsupported-wire
  test data; it is not an active alias, symlink, compatibility reader, or
  second implementation.
- Do not modify V6, Pinned assets, formal models, formal evidence, existing
  source candidates, frontend code, shared contracts, or another Source
  Agent's implementation unless the user explicitly adds that scope.
- Target v0 supports frozen Java/Spring MVC/MyBatis source only. JPA, message
  brokers, schedulers, batch, reflection, AOP, SpEL, WebFlux, and unsupported
  constructs become explicit Gaps.

## Target package registry

- The public application seam lives at org.sourceanalysis.app.
- The eight analysis packages are exactly:
  - org.sourceanalysis.app.analysis.inventory
  - org.sourceanalysis.app.analysis.discovery
  - org.sourceanalysis.app.analysis.graph
  - org.sourceanalysis.app.analysis.fact
  - org.sourceanalysis.app.analysis.flow
  - org.sourceanalysis.app.analysis.interpretation
  - org.sourceanalysis.app.analysis.knowledge
  - org.sourceanalysis.app.analysis.document
- The cross-cutting roots are exactly:
  - org.sourceanalysis.app.analysis.code (approved Java engine seam; not a ninth step)
  - org.sourceanalysis.app.capture.localgit
  - org.sourceanalysis.app.artifact
  - org.sourceanalysis.app.evidence
  - org.sourceanalysis.app.runtime
  - org.sourceanalysis.app.validation
  - org.sourceanalysis.app.adapter.cli
  - org.sourceanalysis.app.adapter.http
  - org.sourceanalysis.app.adapter.provider
- Future database analysis uses org.sourceanalysis.db.analysis; it is
  documentation-only here and must not be created in this source Agent.
- Do not create target, mvp, numbered analysis packages, common, shared, misc,
  utils, codemd, github, or linguan in target Java, Maven, artifact, schema,
  type, command, or fixture names. Put each rule at the semantic seam that owns
  it.

## Fixed jshERP offline acceptance source

- The user approved one read-only offline acceptance capture of
  https://github.com/jishenghua/jshERP.git at commit
  8c30ce7861570458920175e200bb2a6442713580.
- Capture may populate only this Agent's ignored local workspace. Analysis
  consumes the generated immutable manifest, never master or a live working
  tree.
- Do not run the captured repository's Maven, plugins, tests, scripts, or
  application. Do not silently refresh the commit.
- The DepotHead eight-file BOUNDED_PATH_SET is a walkthrough/local fixture,
  never repository-completion eligible. Do not present an old Gap/zero-Flow
  audit as a new measured result.

## Authoritative target design

- The 2026-09-17 approved Step01–05 redesign is documented in
  docs/supplements/cross-object-process-reconstruction/jdt-persistence-reading-materials.md.
  It supersedes the older production requirements to retain JavaParser,
  graph/Fact/Proof producers and Flow/Capsule/M10 material packaging. Those
  algorithms remain current code pending migration, not future requirements.
  Keep JDT navigation/Core/caching and strict historical reads; never rewrite
  the saved 326 Activities or model outputs. The target introduces internal
  analysis.persistence and analysis.material modules, not new analysis steps.
- This work is design-only until an implementation plan is authorized. Its
  acceptance sequence is A: official MyBatis/JSqlParser tool experiment;
  B: full integration only after A passes; C: verify steps 01–05 and stop.
  Do not execute Step06 or later, initialize a Provider or regenerate Activity.
  New material consumption by Step06 is explicitly not designed yet.
  Record earlier unresolved implementation issues in
  docs/supplements/implementation-lessons-and-followups.md; do not fix them
  or rerun business models under this upstream scope. Preserve more-findings.md.
- docs/DESIGN.md is the target architecture. Current code, schemas, tests and
  historical artifacts may validate or falsify it but never silently weaken
  it.
- Maintain exactly one detailed design for each production analysis step:
  - docs/analysis-steps/01-verified-source-inventory.md
  - docs/analysis-steps/02-application-discovery.md
  - docs/analysis-steps/03-program-graphs.md
  - docs/analysis-steps/04-proven-code-facts.md
  - docs/analysis-steps/05-business-flows.md
  - docs/analysis-steps/06-flow-interpretation.md
  - docs/analysis-steps/07-repository-knowledge.md
  - docs/analysis-steps/08-nine-section-document.md
- docs/history/ is history only. It is not target navigation or a production
  contract.
- docs/plans/semantic-framework-ten-batch-implementation-plan.md is superseded
  history and must not be executed as the current plan. The next implementation
  plan must be derived from the active business-process discovery Modules and
  the explicit delta recorded in
  docs/plans/business-process-discovery-and-reconstruction-change-design.md.
- Update and review the applicable target design before implementation. This
  instruction does not authorize a Git commit, push, model call, source scan,
  capture, freeze, package or deployment; follow the user's explicit scope.
- README is navigation/capability indexing. Analysis-step documents own their
  step's detailed design, tests, Gaps, stops and current maturity.
- docs/modules/java-code-engines/ owns the approved engine Interface,
  JDT/Core internals, common material contract and two-phase migration.
  These are internal Modules, not extra production analysis steps.
- docs/modules/model-job-execution.md owns the implemented Java 17 model job
  pool, single YAML configuration, Provider/auth binding,
  private per-job saving, failure handling and direct verification. Synchronize
  its affected active consumers; preserve historical runs and completed plans.
  Section 7 owns the implemented material-checkpoint/model-batch separation,
  explicit v2-to-v3 state export and reviewed-job reuse. Do not reopen old plans.
- docs/modules/business-process-discovery/ owns the target Step07 deep-module
  split, ActivityUse/process contracts and deterministic process publication.
  Its target/current labels are mandatory: none of its unimplemented target
  outputs may be described as already delivered.
- docs/supplements/cross-object-process-reconstruction/ owns the implemented
  reading delta and the approved system-assessment/focused-selection target.
  business-reasoning-and-writing.md now owns candidate-only factual DRAFT ->
  WRITE -> final RULE_REVIEW. This three-stage target is not yet production
  implementation; implementation-status.md separates existing code and remaining
  changes. Current acceptance is limited to three explicitly selected examples;
  do not resume whole-repository generation after samples without discussion.
  It replaces candidate-member-only M10
  access and DRAFT-before-full-source with old-catalog input reuse, global
  material selection, one model reading check per candidate, optional actual
  supplemental reads, and a complete packet before process DRAFT. Preserve
  more-findings.md. Documentation edits do not authorize implementation or
  product calls, and never authorize regenerating saved Activities.

## Migration baseline: previously selectable Java code engines

This section records the existing two-engine implementation and its original
migration sequence. Future production follows the supplement above: JDT only,
optional persistence enrichment and one Step05 material owner. Preserve
useful JDT behavior below, not the retired requirement to keep JavaParser
or strict graph/Fact/Flow producers.

- The independent JDT path and the follow-up JavaParser adapter were implemented
  and accepted on 2026-09-12:
  configuration/session, JDT Core syntax, JDT LS navigation, discovery,
  navigation publication, truthful NOT_PRODUCED strict facts, Step05
  contexts, persisted business material and the scripted report chain are
  connected. JavaParser additionally preserves its existing seven graph
  payloads, strict Fact/Proof path, Flow/Capsule path and the same persisted
  business consumers. Do not reopen these as unimplemented design.
- First implement the JDT route independently. Protocols and direct
  producers/readers may change for a sound JDT design without accommodating
  JavaParser. Preserve JavaParser code. Only in the second phase adapt it to
  the completed contract and restore CURRENT capability; do not add symbol
  resolution features or require JDT-equivalent coverage.
- YAML selects exactly jdt or javaparser for a run. No automatic fallback,
  merged engines, dual writers or compatibility readers. An unknown selection
  is an explicit error, not silent use of another engine.
- On the JDT route, JDT LS owns navigation/resolution; a syntax-only JDT Core
  helper on the tool JVM owns Java declarations, full bodies and call syntax.
  Neither discovery nor BusinessMaterialBuilder may secretly invoke
  JavaParser. Do not implement Java wildcard imports, inheritance, overload
  resolution or Spring runtime dispatch by guessed names.
- A hierarchy hit is not grounds to skip implementation lookup for virtual
  or abstract/interface targets. Preserve every candidate, constructor,
  deferred callback and unresolved/boundary call with its actual source.
- Step02 entry records persist both the engine-neutral methodKey and the full
  declaration SourceRange. A handler name/FQN alone is never an overload key.
- The syntax-only JDT Core helper is the separately built same-repository
  tools/jdt-syntax-helper artifact. Launch it with the configured tool JDK and
  enforce the jdt-syntax-v1 JSONL timeout/exit/protocol contract; stdout is
  protocol-only and every fatal protocol/process condition remains explicit.
- Engine-normalized code material is sufficient input to Step05. Existing
  five-graph/Fact/strict-Flow capability is optional technical enrichment,
  never a JDT reading gate. Missing enrichment is NOT_PRODUCED with a reason,
  not fake empty graphs, fake Proof or a smaller entry denominator.
- java-code-index is PROGRAM_GRAPHS module 7, not a new analysis step. On the
  JDT route Step03 publishes index+receipt; Step04 publishes only v4
  NOT_PRODUCED fact-accounting (nullable counts)+receipt. Exact artifact-set
  allowlists, policies, readers and fixtures must describe these actual sets.
- Preserve full method code and its conditions/returns in the material path.
  Verify that Service bodies reach actual model input, not only an index.
  Evidence exists to locate code, not to repeatedly re-prove ordinary reads.
- Follow docs/plans/navigation-reuse-and-readable-report-design.md for the
  implemented optimization: cache each
  distinct JDT operation/location once per frozen, ready session; share method
  bodies, not entry-specific expansion state. Keep index v2 METHOD sharing and
  entry-owned CALL. Persist Step05/Capsule references and hydrate full immutable
  contexts before creating model packets; never send unresolved internal keys
  instead of the source a model needs. No cache service or recovery subsystem.
- All subsequent edits belong in the formal source-code checkout, not the
  /private/tmp research worktree. Preserve prior comparison artifacts. The
  uncommitted blanket Java 25 changes on the older appmod branch are not the
  verified JDT baseline; main application Java 17 and tool JVM remain separate.

## Eight analysis steps and the business deep Modules

- The closed semantic keys and runtime directories remain:
  - verified-source-inventory -> steps/01-verified-source-inventory/
  - application-discovery -> steps/02-application-discovery/
  - program-graphs -> steps/03-program-graphs/
  - proven-code-facts -> steps/04-proven-code-facts/
  - business-flows -> steps/05-business-flows/
  - flow-interpretation -> steps/06-flow-interpretation/
  - repository-knowledge -> steps/07-repository-knowledge/
  - nine-section-document -> steps/08-nine-section-document/
- Numerical prefixes order docs/directories only. Java types, fields, packages,
  schemas, artifact IDs, tests and commands use semantic names.
- Keep all eight steps and useful persisted technical outputs. Step 01 verifies
  source once; Step03 indexes selected-engine code relationships and reports
  actual graph-enrichment availability; Step04 adds optional strict Facts or
  records that enhancement was not produced; Step05 assembles per-entry context
  with actual/formal arguments, controls, returns, boundaries and code fragments.
  Keep exact Proof truthfulness. Incomplete strict Proof is not the sole gate
  for reading safely located code. Remove repeated ordinary-path enumeration,
  compilation and projection; do not introduce another evidence hierarchy.
- The pre-migration Step06 material path uses two implemented business Modules:
  BusinessMaterialBuilder and ActivityExplainer. The saved 326 reviewed
  Activities remain reusable. The new material owner moves to Step05;
  the existing Activity consumer is not migrated or executed in this scope.
- Step 07 exposes exactly two deep internal Interfaces:
  BusinessProcessDiscovery and BusinessProcessPublisher. Discovery owns the
  FrozenAnalysisCorpus, RepositoryBusinessCataloger,
  ProcessMaterialAssembler, CandidateProcessReconstructor and
  RepositoryProcessConsolidator internals. Publication owns the deterministic
  repository process catalog, coverage and business-processes.md. Do not expose
  those internals as new public Agent methods.
- Step 08 has no current production generator. Its future design allows exactly
  one business Module consuming the consolidated process catalog, never raw
  Activities or the retired singleton-process output. Preserve the historical
  report reader and deterministic renderer without reviving the old producer.
- These are internal Modules behind the sole RepositoryAnalysisAgent public
  Interface. Do not create a second POC namespace, parallel runtime, public
  Interface, compatibility alias or dual writer.

## Program and model responsibilities

- Critical architecture/design uses gpt-5.6-sol / ultra or gpt-6-astra /
  ultra. Production implementation uses gpt-5.6-terra / xhigh after design and
  RED. TDD test writing, bounded source reading and review use gpt-5.6-luna /
  xhigh. Product activity/process/report DRAFT and REVIEW default to configured
  Codex Pro gpt-5.6-luna / high; explicit other Provider/model services follow
  the approved model-job design. One job keeps one binding for both rounds.
- Automated tests use frozen fixtures and a deterministic scripted Provider.
  They never invoke a live model, network source, API key or customer build.
- Live product tasks need current explicit authorization, one frozen input
  package, declared limits and preflight of the bound authentication context.
  A subscription Provider enforces ChatGPT auth, blocks API environment
  overrides and never buys credits or falls back to a metered route. No CLI
  switch preventing use of already-paid credits has been verified; require
  account-side checks instead of promising zero paid-credit use. Explicit API
  Providers are approved as a design option only and need authorization for
  each actual run; they never receive a failed subscription job as fallback.
- An Activity, initial catalog or repository-consolidation job remains DRAFT
  then complete REVIEW and saving. Candidate-process jobs alone target fixed
  factual DRAFT -> WRITE -> final RULE_REVIEW, with the final reviewer seeing
  the original complete packet, actual DRAFT and actual WRITE. WRITE receives
  the complete factual draft. Preserve complete structured process fields;
  after final review, no model may rewrite the published prose. Existing
  Activity jobs remain complete and reusable. Target Step07 first runs bounded
  repository-catalog discovery over compact Activity cards, then reconstructs
  overlapping candidate processes in parallel from complete selected
  Activities and requested saved source excerpts, then runs one bounded
  repository consolidation. A future whole-nine-chapter report job may follow
  the published catalog; deterministic process rendering and historical report rendering remain
  zero-Provider. Shared Activities are immutable and membership is many-to-many.
- System type/business hypotheses belong in the existing global selection
  model call, after reading saved repository descriptions and all navigation.
  No fixed ERP/CRM/WMS classifier or industry routing is allowed. Business
  knowledge proposes falsifiable reading questions, never guaranteed features.
  Selection and one candidate reading check may retain, remove or supplement
  material according to those questions; Java only performs actual reads.
- For the cross-object target, saved catalog input skips its original model
  jobs. Global selection and each candidate reading check are separately saved
  single-decision jobs, not reviewed pairs. Every eligible candidate gets one
  reading check; the model returns a possibly empty supplemental request list.
  Java retrieves, never decides semantic sufficiency. Remaining gaps stay
  explicit; no third selection, automatic repair or Activity regeneration.
- Configure only global and each Provider/account service maxConcurrentJobs
  in the single YAML owner; defaults are global 4 and Pro Luna/high 4. These
  are in-flight job limits, not maxMaterialsToStart, Builder K or actual N.
  Eligible queued jobs are not skipped when a concurrency slot is unavailable.
  Multiple keys or fresh sessions sharing an account/project do not create
  independent quotas; declared/observable shared scopes use one Provider cap.
- Step 05 is the sole owner of entry-context relationships and related source
  excerpts, including safe entries without a strict Flow. Its existing files
  hold the context; Capsule is its budgeted projection, not another chain model.
- Step05 consumes the selected engine's normalized methods/calls/candidates
  and optional actual graph relationships. Facts/Proofs keep their strict
  meaning but never filter safely located code from business reading.
- Step05 is context-first and uses the versions frozen in the Java engine
  contract. It never reparses or re-navigates; each entry has a collected
  context or a specific not-collected reason, and each collected context has
  a Capsule even when strict Flow is not produced.
- BusinessMaterialBuilder only packages Step 05 contexts, selects complete
  in-budget units and maps SourceRefs. It must not independently reconstruct
  a direct callee from source text. Java does not use an
  industry dictionary to assign purpose, actor, action, outcome or process.
- ActivityExplainer reads a complete local activity package and REVIEW sees
  the complete actual DRAFT. Preserve full reviewed conditions, rules, formulas
  and narrative through process knowledge and final report, not only labels.
- An Activity package has an arbitrary positive number `N` of entries and
  scope-local keys `E1...EN`; package-local keys always map through the owning
  material to global entry IDs. Never hard-code four entries, compare by
  prefixes/substrings, or join bare `E1` across packages. Configuration `K`
  limits Builder packaging but is not the repository entry count.
- Before an Activity Provider starts, the effective profile must be able to
  express `N` independent activities, every activity must be able to reference
  any subset of `E1...EN`, and the complete DRAFT plus REVIEW envelope must fit
  the real serialized input/output budgets. ActivityExplainer does not split
  or truncate an already-built material; incompatible material receives a
  concrete zero-request coverage reason.
- A structurally and scope-valid Activity DRAFT may omit entry keys. It still
  enters the one allowed REVIEW with the complete actual DRAFT and
  program-computed `missingEntryKeys`. Invalid JSON, unknown keys/refs, output
  budget violations, or a failed started request remain fatal and never enter
  a repair path.
- The Activity REVIEW output has a required, possibly empty,
  `unexplainedEntries` key array. Reviewed activity keys union unexplained keys
  must equal the material keys and be disjoint; any remaining omission is
  fatal, with no third call. Java maps unexplained keys to program-owned
  `MODEL_NOT_EXPLAINED`, not a source/Proof Gap. Process/report model input
  groups them by material as `{materialContext, unexplainedEntryKeys,
  reasonCode}`, sends the context once, and keeps global IDs program-side.
  Chapter 9 names the corresponding HTTP entries and reason category.
- `PARTIAL` and `INCOMPLETE` in this semantic design are document-quality and
  acceptance conclusions, not new runtime/report enums. Closing a coverage
  set with unexplained entries cannot pass complete business acceptance.
- RepositoryBusinessCataloger uses compact, deterministic cards only to find
  business areas, aliases and overlapping candidate membership. Java must not
  seed sales, purchasing or another domain vocabulary and must not infer
  business order from names, shared tables or file proximity.
- ProcessMaterialAssembler validates candidate Activity IDs, reloads complete
  reviewed records once per distinct Activity, preserves each variant use,
  exposes statement handles and executes requests within the same frozen corpus.
  Target reading may include any saved Activity, M10 excerpt and verified text;
  new excerpts need no old Activity owner or new Proof. Inject the existing
  VerifiedSourceTextReader, not a live checkout reader. Do not add
  JavaCodeIndex/MethodKey navigation or reparse for reading. It never
  reruns JDT, JavaParser, BusinessMaterialBuilder or ActivityExplainer.
- CandidateProcessReconstructor explains the selected Activity variants and
  exact rules in factual DRAFT, writes readable business content in WRITE,
  then checks the actual written content in final RULE_REVIEW. Original source,
  complete facts and actual writing must reach that final check; it returns
  the complete corrected process plus concise correction notes. The implemented
  three-stage path is undergoing the approved three-case real acceptance; it is
  not a completed whole-repository quality validation. Java validates
  structure and refs, not Chinese business entailment. Keep the original
  Activities immutable when a new source reading corrects their interpretation.
- RepositoryProcessConsolidator reads the existing complete reviewed Process
  JSON, not a new lossy summary. It decides KEEP/MERGE_INTO/REJECT and relations,
  but cannot rewrite prose. Only structurally identical full stage sequences
  after use-ID normalization may MERGE_INTO; differing sequences stay separate
  with relationships rather than being concatenated into a false lifecycle.
  BusinessProcessPublisher deterministically renders the closed result.
- Any future Step08 publisher consumes only that consolidated catalog, lets the
  model author and review the fixed nine-chapter presentation, and cannot
  discover, merge, split or reorder processes. Java supplies Markdown styling.
- Each Activity, initial catalog and repository summary uses at most its
  existing DRAFT/REVIEW pair; the approved candidate-process target uses exactly
  the three named stages, not an automatic repair loop. A new candidate private
  record must save all three; an old pair is not a completed three-stage job.
  Check actual context at each stage. Initial capacity failure means zero requests plus a
  concrete uncovered reason. Once a request starts, transport/schema/runtime
  failure is fatal for that execution: stop new job dispatch; already-started
  jobs whose own preceding stages are valid finish their remaining authorized stages under existing
  timeouts and preserve outputs. Collect terminal outcomes, then fail without
  downstream success. Never automatically retry, switch Provider, fall back to
  an API key, replay an old request or synthesize success. An explicitly started
  new model batch may execute incomplete jobs under section 7; old STARTED and
  FAILED records remain immutable. Provider request journals are scoped by
  modelBatchId so a new explicit batch cannot be blocked by an older batch's
  uncertain STARTED request; completed job reuse still uses the validated job
  result and fingerprint. A new batch is not an automatic retry loop.
- `maxMaterialsToStart` is the explicit per-execution ActivityExplainer launch
  cap. Use the dedicated `FLOW_INTERPRETATION` materials-only target for
  zero-Provider planning; a final-document run requires a positive cap. A cap
  of one supports one-package quality checks before a wider run; packages
  beyond the cap receive `NOT_ANALYZED_EXECUTION_CAPACITY`, never implicit
  queueing or model calls.
- Internal DRAFT/REVIEW tasks contribute to one Reader Candidate. A frozen
  source still permits at most ReaderCandidateRound 1 and one separately
  authorized Round 2 replacement for named findings. Do not manufacture more
  candidates through internal tasks.
- Validate product quality in order: one small real core package, a second
  domain package, then the full repository. Do not promise elapsed time before
  these samples are measured.
- Observe/inspect, edit, and render are distinct. Observation and pure
  rerendering are zero-Provider operations; editing business content is an
  explicitly authorized generative action.

## Business source, technical Proof and truthful language

- Historical Step04 Proof retains its exact technical meaning; the new
  Step04 produces persistence materials, not new Proofs.
  Business semantics do not require one Proof/owner/accounting record per
  natural-language atom.
- The minimum business source is a program-created short SourceRef that maps
  to one frozen repository-relative file, exact line range and snippet.
  Within one BusinessMaterialSet a ref is globally unique: the same ref points
  to exactly one location and repeated use of the same snippet reuses the ref.
- Model packets contain only allowlisted short refs, necessary snippets,
  technical observations and limitations. They do not contain source paths,
  line numbers, hashes, full Proof/Evidence chains, run/artifact/publication
  identity, Provider controls, credentials, budgets or host paths.
- The material-selection/reading-check tasks are a narrow navigation exception:
  they may see frozen repository-relative file keys, paths and line ranges and
  request literal searches or reads. They never see host absolute paths or
  execute source commands. Only program-resolved saved text becomes a ref;
  seeing a filename is not reading its content.
- Every Provider request supplies a closed JSON Schema generated from the
  current task profile and its scope-local allowlists (for example activity
  IDs and short refs). It must name the complete output shape, enums and
  capacity limits before a request starts; Java revalidates the returned JSON
  and remains the authority for reference scope and persistence.
- A model may reuse allowlisted refs and create scope-local business labels.
  It cannot create source refs, paths, lines, hashes, Facts, Proofs, Flows,
  human confirmations or external identities.
- Compact Activity cards are discovery/navigation material only. A card may
  suggest a candidate membership, but it cannot authorize a detailed stage,
  rule or transition. Those require the complete selected Activity and, where
  needed, an excerpt fetched from the already saved source corpus.
- Preserve concrete predicates. If reviewed material says `status=0` permits
  update, `status=1` is required for unaudit, or purchase status 2/3 rejects an
  operation, no later model or renderer may replace that with only “状态允许”
  or “满足条件”. When the source does not establish the predicate, say exactly
  what is unresolved rather than inventing a generic rule.
- Process claims use exactly `CONFIRMED`, `INFERRED` or `UNRESOLVED`.
  `CONFIRMED` needs direct saved Activity/source support; `INFERRED` is a model
  business connection consistent with that support; `UNRESOLVED` records
  conflicting or missing information. Java validates the enum and references,
  but does not score or hard-code domain meaning.
- Clear source context that constructs an object and calls a persistence
  operation may be described as “the system generates and saves the object.”
  This is code-defined behavior, not proof that a particular run succeeded.
  When source only shows an ambiguous boundary call, narrow the statement.
- If source clearly defines inventory registration or accounting-record
  creation, the report may describe that process action. It must not claim a
  particular run succeeded, a physical/external result occurred, or payment
  completed without runtime/external evidence.
- Never invent job roles, policy, unique orders, success counts, business
  sequence, accounting, stock effects, formulas or metrics. Reasonable
  cross-entry inference is allowed when supported by the package, with
  uncertainty concentrated at the activity/process paragraph rather than
  repeated after every sentence.
- Standard MyBatis mapper DOCTYPE is accepted only with all external DTD,
  general/parameter entity, schema and network resolution disabled. Inability
  to enforce those settings fails closed.

## Spring entry legality

- @RequestMapping with omitted method or method={} is valid, never a Gap just
  for being unrestricted. No restriction on either level means UNRESTRICTED;
  one explicit level retains that set; two explicit levels combine by union
  as Spring RequestMethodsRequestCondition does, not intersection.
- Do not invent GET or separate business activities for framework HEAD/OPTIONS.
  The explicit versioned methodCondition and discovery v3 reader are
  implemented. UserController#getOrganizationUserTree and
  MaterialCategoryController#getMaterialCategoryTree are now accepted as
  unrestricted routes. A future full-repository rerun may reconfirm the total
  denominator, but this rule is not pending implementation.

## Coverage, grouping and the nine chapters

- FlowSlice is an entry-rooted technical slice, not a smallest business
  process. Flow, activity and BusinessProcess are many-to-many. A missing Flow
  does not erase an entry when the same frozen snapshot has safe located
  material; preserve the technical Flow Gap.
- Every discovered entry must be ANALYZED, ANALYZED_WITH_GAPS or NOT_ANALYZED
  with a concrete reason. One packet/group PASS never completes a repository.
- A deterministic Activity index card uses literal existing reviewed fields,
  including businessRules, conditions, steps, objects, inputs and results. It never
  truncates or replaces the full reviewed Activity. Catalog discovery uses all
  cards, possibly through bounded shards plus one consolidation, and must close
  every card as candidate member, support/standalone activity, unclassified or
  failed with a concrete reason.
- Candidate groups are semantic, bounded, overlapping and model-proposed; they
  are not consecutive chunks of 24 records and not Java joins on a domain word.
  Calls, identifiers, data relations, reviewed objects and source statements
  are context after a candidate exists. Cues do not prove order, causality,
  identity or merge. Same-name and different-name activities are not
  automatically merged; one Activity may have several process-specific
  ActivityUse records, including multiple distinct variants within one candidate.
  Deduplicate complete Activity bodies, not distinct (ActivityId, variant) uses.
  Do not silently downgrade PROCESS_MEMBER when its candidate membership is
  absent. For a sharded full-repository catalog, the complete DRAFT owns the
  Activity denominator. If REVIEW corrupts only its redundant 326-entry echo
  through duplicate or missing IDs, Java retains the complete DRAFT ledger and
  recomputes PROCESS_MEMBER from the reviewed candidate membership; a complete,
  unique REVIEW ledger that still declares an orphan PROCESS_MEMBER remains an
  error. Existing denominators remain Activity/candidate/reviewed process;
  variant omission is checked by REVIEW and sample semantic acceptance, not
  falsely advertised as caught by an ActivityId set comparison.
- Detailed process output must structurally retain purpose/scope, ActivityUse,
  ordered and optional stages with required business narrative, entry and
  rejection conditions, actions,
  state changes, outcomes, transitions, exact business rules, certainty,
  statement refs and source refs. Rules have explicit activityUseIds; those IDs
  describe business applicability, not evidence ownership. Any existing ref in
  the actual reading packet may support a rule, including context Activities
  and text not owned by a member. Only unknown or unprovided refs are rejected.
  A valid whole-method ref does not establish that every
  subtype obeys every branch.
  Models own that semantic review; Java must not implement Chinese entailment
  checks or an industry keyword blacklist. Query/statistics/configuration Activities may
  support a process without being forced into its main chronological stages.
- Incremental selection inherits untouched candidate/Activity dispositions,
  updates membership from final candidates and requires a remaining disposition
  when the last membership is removed. Reading only as context does not make an
  Activity a process step. Reuse existing coverage; no fourth business ledger.
- Guided and unguided acceptance independently start from the same original
  saved catalog, Activities and source. Unguided runs must not inherit guided
  candidates, read requests, packets or results. Report semantic findings and
  actual calls separately; guided success does not prove autonomous discovery.
- The final catalog must carry the actual selected OBJECT,
  FIELD_OR_DIMENSION, OBJECT_RELATION, FORMULA_OR_METRIC and QUESTION text with
  owner, certainty and refs. For SUPPORT/STANDALONE/UNCLASSIFIED Activities,
  BusinessProcessDiscovery deterministically projects this content from the
  complete reviewed Activity into ProcessDiscoveryResult. The publisher only
  validates and publishes that closed result; neither component may drop it or
  manufacture a fake Process.
- Repository consolidation compares overlapping reviewed candidates, preserves
  alternatives and conflicts, and closes every candidate/Activity disposition.
  Its model input is a deterministic business-complete projection: process
  identity, purpose, scope, participants, objects, Activity uses, stage
  narratives and predicates, rules, results and unresolved connections. Repeated
  statement/source evidence fields stay in the stored full processes; only the
  small process-level source-ref allowlist is sent for relation citations.
  If the single consolidation REVIEW omits a process from its long decision
  ledger, the program preserves that original reviewed process with KEEP. An
  unknown or duplicate process decision remains fatal; omission never deletes
  or synthesizes business content.
  It may not silently compress away conditions, rules, formulas or unresolved
  scope and then claim completion.
- Zero entries or all entries not analyzed may yield a nine-chapter scope
  report, but semantic delivery remains INCOMPLETE.
- The final Markdown has exactly the shared NineSectionProfile H2 sections:
  文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、
  示例问题、待确认事项.
- `business-processes.md` is the primary readable artifact answering which
  business processes exist and how each proceeds. The nine-section document is
  a downstream repository view; it cannot repair a missing process by reading
  raw Activities or rediscovering relationships.
- Chapter 7 uses only formulas/definitions present in reviewed input. With none,
  it explicitly says no definable metric was identified. Natural-language
  truthfulness is checked by whole-report Luna REVIEW and authorized human
  sample review, not a Java business-language parser.
- The implemented Step08 renderer uses business prose and plain short refs only.
  Store raw file/lines/snippet in the existing separate source-refs.jsonl;
  do not append source blocks to Chapter 1, another chapter, or a tenth H2.
  Remove links to deleted same-document source anchors. Keep model inputs and
  reviewed chapter text unchanged; pure rerender calls no Provider and never
  overwrites historical comparison artifacts. This Step08 behavior is implemented.
- The current Step07 correction instead requires business stage narrative and
  clickable relative source navigation. With direct sourceRefs, a stage links
  to its process source index, then to sources.md file/line/snippet anchors.
  sources.md is a deterministic view of existing SourceReference, not new
  evidence or a frontend. Source blocks stay out of business-processes.md.
  Source links are optional and may be empty. Do not require a missing-link
  explanation, enrichment, special acceptance work or extra model call. Reuse
  existing source fields and do not block delivery over this non-core detail;
  focus on readable business steps, branches, concrete rules and outcomes.
- A technical receive/validate/execute/return template does not pass lifecycle
  acceptance merely by containing multiple stages. Actual business uses,
  branch-scoped rules and readable object transitions must pass model REVIEW
  and real sample review before scaling. No hardcoded business names in prompts.

## Persistence, reuse and recovery

- Keep observable ModuleArtifact/receipt and step publications. On the normal
  trusted path, build/compile/project each owned result once and pass immutable
  typed views. Publishers serialize, check type/ID/ref/budget and atomically
  install; they must not replay the producer's algorithm.
- Disk/new-process/import reuse verifies stored identity/hash/schema/ref/basis
  and read source bytes at the boundary. Explicit independent audits and
  mutation tests may replay algorithms; ordinary internal consumers must not.
- Implemented business checkpoints are:
  - Step 06: business-materials.jsonl, activity-explanations.jsonl,
    activity-coverage.json
  - Step 08: business-report.json, source-refs.jsonl, document.md,
    report-validation.json
- Legacy business-processes.jsonl/repository-business-knowledge.json are not
  consumed by the current process-specific route. Step07 v1 catalog/candidate/
  reconstruction/consolidation and four-file publication are implemented.
  The historical 46-process result does not prove lifecycle quality. Five-file
  v2 publication is already implemented:
  catalog v2, coverage v2, business-process Markdown v2, unchanged source refs
  JSONL v1, and new sources Markdown v1. Synchronize exact-set checks, producer,
  reader, registry and artifact query; keep upstream versions and old results.
  No changes to public Agent methods, PROCESS_CATALOG or the three owners.
  The cross-object target keeps those public schemas, adds private reading
  decision/packet v1 and process Prompt/response v3, and updates producers.
  Map new refs before closing the result, not by fetching in Publisher.
  Future Step08 input migration remains outside this correction.
- Save business-materials after compilation and before the first model call.
  The approved job design has the coordinator save each complete reviewed job
  to a private run result immediately, then aggregate in stable material/group
  order and publish once at the existing fixed address. Target process
  publication waits for catalog discovery, every candidate disposition and
  repository consolidation. Activity jobs and target candidate-reconstruction
  jobs execute with bounded two-level concurrency and private per-job saving
  before stable aggregate publication. Workers do
  not mutate shared collections or repeatedly install differing bytes at one
  address. Keep Provider/job journal namespaces isolated, execution metadata
  outside model input, and each job submitted once. The approved Step07
  publication may replace its legacy payload set, but it adds no public Agent
  method, recovery subsystem or runtime state enum. In-process callers pass
  immutable objects without per-internal-operation fresh reopen.
- A simple inputFingerprint covers actual content inputs excluding a new
  runId, actual Prompt content/version, effective model/output configuration
  and Module version. Reuse requires equality; Prompt text or effective input
  changes invalidate the checkpoint.
- Do not introduce per-internal-Module fresh reopen, layered hash chains, a
  fixed total output count, whole-record replacement state machines,
  bridge/reconciliation ledgers or complex same-run recovery.
- Active v0 never resumes or replays an uncertain started Provider call.
  Same-run crash/worker takeover and terminal repair remain deferred.
- Preserve validated business materials independently of model outcomes.
  materialsCheckpointId names the existing receipt; reads require the full
  typed reference, saved material profile/producer and basis. sourceRunId stays
  the producer; a newly allocated AnalysisRunId is the modelBatchId/output owner.
  Direct model-batch execution must not call Capture, JDT, graph/Fact/Flow or
  BusinessMaterialBuilder again. Failed source-run state alone does not
  invalidate a completed material artifact. Do not delete journals, reset
  failure states or rescan merely to obtain new model request identities.
- Cross-batch reuse is explicit and limited to complete, validated, privately
  saved reviewed jobs with matching content, source mapping, task sequence,
  Prompt/schema/profile and service/account/model/effort. Candidate three-stage
  reuse requires DRAFT, WRITE and final RULE_REVIEW; other tasks keep pair reuse.
  Reuse neither isolated intermediate responses nor unvalidated final responses.
  A failed job starts its full authorized sequence only in an explicitly requested new
  batch; prior completed results and provenance are immutable. A changed group
  or knowledge input invalidates only dependent process/summary/report results.
- Single reading decisions have their own complete-record validation/reuse;
  they cannot masquerade as DRAFT/REVIEW. Catalog-as-input reuse differs from
  exact job reuse. Target model-job-execution-config-v3 binds Activity,
  material, verified inventory, optional catalog and focus question before
  Provider startup; changed inputs cannot silently reuse the queued run.
  Keep strict historical v2 reads; do not change source/material formats.
- Batch/output integration must preserve dual ownership: material belongs to
  sourceRunId; Activity/Knowledge/Report outputs belong to the new batch run.
  Synchronize writers, current analysis-run-output-v4 and historical v3 readers; never broadly disable cross-run
  checks. Root request candidate lineage stays fixed; operational batches do not
  mint extra Reader Candidates or bypass ROUND_2 findings. Use the implemented
  source-analysis/Agent composition; new catalog/reading options are connected
  and directly verified. Real full-corpus quality is recorded separately in the
  supplement delivery record. No new public Agent methods,
  recovery system or parallel evidence framework is authorized by this design.
- Explicit material-state export reads existing artifacts and writes a new
  private versioned state; it never silently converts/overwrites old state or
  invokes a scanner. Keep runtime config, batch IDs, hashes and retry diagnostics
  out of model packets. Honor an existing scoped authorization without repeatedly
  asking, but never start another batch while the user has paused for discussion.

## Public seam and output rules

- The sole public target seam remains one run-centric RepositoryAnalysisAgent
  with start, executeStep, inspect, artifact, render, validate and trace.
  start allocates the execution identity; executeStep runs its matching QUEUED
  run and never reactivates an ended run. It is not same-run resume.
- Java and CLI may arrive before authenticated loopback HTTP. Completing all
  three adapters is not a business-quality gate; HTTP remains deferred.
  Explicit API service configuration and adapter are implemented as part of
  the model-job execution design. Independent model batches, sourceRun/output
  ownership and explicit reviewed-job reuse are implemented; do not describe
  them as pending or use a model failure to trigger JDT again.
  The design alone authorizes neither live API calls nor billing.
- No public request/response accepts or exposes filesystem Path. Raw source,
  prompts and model responses remain protected according to existing artifact
  policy.
- Read-only observations never call a Provider, execute customer code, inject
  evidence or replace a publication.
- Fixed 52 reader-visible outputs and the prior 57-output implementation are
  not target completion criteria. Existing Step 01–05 technical publications
  remain; new business completion is defined by the reviewed Activity
  checkpoint, consolidated process catalog/coverage, business-processes.md and
  one validated nine-section report.
- Machine artifacts are UTF-8 JSON/JSONL. Exceptions remain document.md,
  durable design Markdown, in-plan progress Markdown and immutable source
  inputs. Immutable source inputs remain verbatim; progress lifecycle follows
  the plan-closeout rule below.

## Full Wire Reset and migration

- The structural Wire Reset moved this Agent and replaced Maven/Java identity.
  Do not reintroduce pre-reset packages, paths or numbered types.
- Do not add compatibility readers, legacy aliases, migration bridges,
  symlinks, dual writers or fallback discovery.
- Mandatory R0 registry proposal, finite business keys, R1/R2/P1/P2 target
  routes, six-module Step 06, three-module Step 07, four-module Step 08 and
  their fixed output inventories are retired targets. Existing Java/schema/
  fixture/artifact instances are migration input only; do not extend them.
- The approved cleanup removed the old
  `analysis.interpretation.{model,proposal,registry,process}` production and
  test packages only after shared current-test fixtures move to neutral
  testsupport. Retire old Step06 addresses 1-9 and their artifact branches,
  then remove only the two registry-proposal Capsule fields with the owning
  schema versions. Preserve current addresses 10/11,
  `ModelRuntimeIdentityV1`, EntryContext, facts, gaps, signals and SourceRefs.
  The legacy analysis.knowledge.ProcessExplainer singleton-process route and
  its producer have been removed after the process-specific Step07 route became
  authoritative. Do not restore it or confuse it with the older deleted R0/P1 route.
  Refine the existing BusinessProcessDiscovery/Publisher only.
- Adjust existing semantic packages and four business Modules. The approved
  analysis.code engine seam replaces hardwired Java parsing only; it does not
  authorize another business runtime, broad Wire Reset or storage/recovery
  subsystem. Preserve Git history; JavaParser algorithms now retire under the
  2026-09-17 supplement rather than being maintained. Retain active-plan
  progress for handoff; remove completed-plan temporary progress only through
  the plan-closeout rule below.

## Testing and stop rules

- One full local CI delivery executes each test once: Surefire for ordinary
  fixture tests, explicit Failsafe real-jdt-it profile for installed-JDT tests,
  then SpotBugs/PMD without rerunning tests. Retain all useful assertions.
  Keep skipUTs and skipITs independent and make commands match POM properties;
  passing skipTests when POM reads skipUTs does not prove tests were skipped.
  Daily RED/GREEN remains targeted; this is not permission for a full suite on
  every edit. No customer build or live product model in these tests.

- Use TDD for implementation: one behavior RED at the applicable deep Module
  Interface, then the smallest GREEN. Run only tests added by or directly
  covering the current change; never start a full suite without explicit user
  request.
- Tests assert observable outcomes through BusinessMaterialBuilder,
  ActivityExplainer, BusinessProcessDiscovery, BusinessProcessPublisher,
  the historical report reader/renderer, or the existing Step 01–05 public seams. Test target
  internals only where needed to prove card completeness, source hydration or
  coverage conservation; do not test past an Interface merely to preserve the
  legacy singleton ProcessExplainer or other retired shallow Modules.
- Stop when the expected RED cannot be established, required upstream data is
  absent, input/ref/coverage cannot close, a started model request fails, or
  implementation needs a contract change. Update durable design and obtain
  the required review/authorization; do not silently broaden behavior.
- Contract uncertainty goes to Sol/ultra. User approval remains required for
  changes to the eight steps/order/keys, fixed nine chapters, source/Proof
  trust, model-visible material/responsibility, public RepositoryAnalysisAgent
  or shared candidate contract.

## Documentation readability

- Designs are function-first: why, concrete input example, program action,
  model action, output example, direct downstream use, Gap/stop/reuse, then
  development tests and current maturity.
- Explain actual input, owned computation, concrete output, next consumer and
  failures in every step. Remove contradictory retired gates in place; do not
  prepend a disclaimer while leaving must-replay or closed-Proof-only reading
  requirements active. Preserve current implementation facts separately.
- Use the fixed-source sales lifecycle walkthrough for the primary closed-loop
  design acceptance, with actual saved conditions clearly separated from
  target process reconstruction. A synthetic replenishment-to-receipt-to-
  payable-bill story may test portability, but never present it as jshERP
  behavior.
- Reader chapters use Chinese business language first; technical fields may
  appear as supporting detail. Keep target design and current maturity
  separate.

## Per-agent progress files

- Before modifying code, tests, configuration or durable documentation, every
  Agent creates one tracked progress/<task-slug>.md from progress/TEMPLATE.md.
  Record the owning plan. Each Agent updates only its file during execution.
- Update current state in place before a long command, after every verifiable
  step/test, on a blocker and at task end. Do not append a chronological log.
- Record scope, approvals, changed paths, checks, decisions, blockers and the
  exact next action. Do not include secrets, full prompts, large source
  excerpts or logs.
- Continuing work reads its progress, checks Git status and verifies referenced
  artifacts/tests. Keep a completed subtask's progress while its owning plan
  still needs that handoff; one Agent finishing does not finish the whole plan.
- At whole-plan closeout, put durable decisions into the owning design,
  remaining issues into the backlog, and final verification/output references
  into the delivery record. Then remove that plan's explicitly identified
  temporary per-Agent progress files. Do not create a second progress archive;
  committed historical versions remain available in Git. Keep progress/TEMPLATE.md.
- Do not classify all historical files by a COMPLETE label alone. Resolve the
  owning plan and preserve any unresolved handoff before removing its files;
  never use a broad progress-directory deletion or rewrite Git history.
- Progress cleanup does not delete source snapshots, JDT materials, reviewed
  Activities, model DRAFT/REVIEW results, journals, publications or retained
  comparison artifacts. Those are product/run data, not development handoffs.

## Shared rules and local configuration

- Commit concise, stable AGENTS.md rules shared by the team; link to detailed
  designs instead of copying their changing implementation state or run logs.
- Commit portable Maven/build configuration and required toolchain versions.
  Do not commit credentials or machine-specific installation paths as shared
  configuration. Keep local path values in ignored local configuration or
  generate them from explicit environment inputs, with a committed template
  or setup instruction when needed.
- The shared Maven toolchain is `.mvn/toolchains.example.xml`; each developer
  generates ignored `.mvn/toolchains.local.xml` from an explicit Java 17 home.
  Keep application compilation and tests on the explicit Java 17 toolchain;
  ordinary Maven development commands may use Java 17. The configured
  google-java-format 1.36.1 needs a JDK 21+ Maven host for Spotless/full quality
  verification; select that tool JVM explicitly without upgrading the application.
  JDT uses its separate tool JVM. Never commit the generated local file or
  silently fall back to the shell JDK.
