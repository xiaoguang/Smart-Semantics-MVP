# Source Code Analysis Agent Instructions

## 设计与实现解释规则

以下规则适用于本模块的设计与实现讨论、说明及文档：

- 解释设计与实现时，以当前代码和实际产物为依据。
- 明确区分：已实现、设计目标、推测、人工举例。
- 说某个数据被下游使用前，必须核对实际读写链路。
- 不把自己阅读源码得到的结论说成程序输出。
- 没有查证就说“未核实”，不要补充听起来合理的解释。
- 按“输入、处理、输出、实际消费者、当前问题”回答，先讲结论。

## 设计先用成熟工具

- 讨论和审查保持客观，不讨好，不用已经投入的代码或时间逆向证明方案合理。
- 增加能力前，先判断它是否属于本项目的核心分析职责；非核心能力优先交给用户、Agent 和现有工具，不先创建内部子系统。
- 遇到复杂能力时，设计的必做前置动作是搜索常用开源项目和现成产品，再查其官方文档/API/源码。先列已有方案能做什么、怎样直接使用或薄适配，再决定本项目要写什么；不得先完成自研方案，最后才补几条引用。
- 没完成上述检索，不得仅凭记忆断言“没有现成工具”。若仍提议自研，必须列出现成方案为何不适用、该能力为何属于核心且值得承担，并先向用户讨论确认。
- 即使需要某项能力，也优先调用成熟实现，只写最小适配。不得自行重建 Maven 下载/仓库/依赖管理、通用语言解释器、编译器或类似庞大基础系统。
- 没找到合适工具不等于应该自己造。先提出有限支持、外部准备或延期方案；只有明确属于核心且用户另行确认的范围才实施。
- 工具的已有输出先返回给当前 Agent 解释；不要为了让 Java 自动解释所有失败而新增分类、修复、审批或恢复框架。
- Maven交接不依赖某个Agent临时补全：Agent仅提供官方输出文件位置及已明确的模块/JDK选择；源码根、编译设置和受支持的模块边由Java从真实输出确定性提取并绑定R0。不得让Agent手填语义JSON替代尚未实现的消费者；既有Skill若仍指示手填v1，以本规则及技术设计为准，按待实施清单撤换。
- 本轮的具体工具选择和撤换范围见 docs/modules/technical-analysis/ 与 docs/plans/technical-analysis-cli-and-vue-cleanup-design.md；设计修改不是运行客户工具的授权。

## Scope and target identity

- This directory owns the Java/Maven frozen-source-to-business-process Agent,
  its tests, fixtures, design, CLI and Java Interface. Step08 preserves only
  historical reading/query/rendering; new report generation is outside scope.
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
- The historical step-aligned analysis packages retained during migration are:
  - org.sourceanalysis.app.analysis.inventory
  - org.sourceanalysis.app.analysis.discovery
  - org.sourceanalysis.app.analysis.graph
  - org.sourceanalysis.app.analysis.fact
  - org.sourceanalysis.app.analysis.flow
  - org.sourceanalysis.app.analysis.interpretation
  - org.sourceanalysis.app.analysis.knowledge
  - org.sourceanalysis.app.analysis.document
- The approved Step03–05 redesign adds internal owners
  org.sourceanalysis.app.analysis.persistence and
  org.sourceanalysis.app.analysis.material. They replace production Fact/Flow
  packaging responsibilities without adding numbered steps or public Agent APIs.
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

- Source preparation's approved target is documented in
  docs/analysis-steps/01-verified-source-inventory.md and its source-preparation
  module contracts. Contracts, source readers, publication, revisions, the
  independent CLI/Skill and the Step05/M10 old-material new-execution gates are
  implemented; the final directly related suite (168/168) and quality checks
  have passed on the same source state. The scoped plan is complete. No
  later-step CLI split or real analysis is authorized. Necessary shared
  source/exclusion and saved-material basis checks are included.
- A saved exclusion list alone is not proof that downstream exclusion works.
  Prepared-source public reading omits excluded files. Historical Step05/M10
  material lacks prepared provenance and effective exclusions, so new execution
  under PREPARED_SOURCE fails closed. Its actual LEGACY basis is reopened from
  Step01 metadata before new consumption. Keep
  SOURCE-PREPARATION-DOWNSTREAM-EXCLUSION is closed for this fail-closed scope:
  a published/reopened fixture verifies Java/XML exclusions through the reader,
  Java project and persistence request/view. Preserve historical artifacts; a source-version mismatch must
  not trigger automatic JDT, Activity, or business-process regeneration.

- Main design and owning analysis-step/Module documents define active contracts.
  Step01–05 JDT-only navigation, optional persistence enrichment and Step05
  reading materials are implemented. JavaParser, strict graph/Fact/Proof and
  Flow/Capsule/M10 producers are retired; exact historical readers remain.
- Step05-to-Activity consumption, its formal CLI, M11 v3 persistence and the
  Step07 dual-source reader are implemented under the approved full-Activity
  plan. Step06 and docs/modules/activity-explanation/ own projection, complete
  reading, model-selected semantic slices, staged DRAFT/REVIEW retry and
  source/coverage. The fixed 325-packet real run and local quality checks are
  recorded in docs/modules/activity-explanation/full-step05-acceptance-20260923.md;
  its one upstream JDT gap and partial semantic status remain explicit.
- Preserve historical 326 Activities, new 418 Activities, raw model results and
  more-findings.md. The approved end-to-end implementation plan permits offline
  completion/reuse fixes and Step07 improvements, followed by unguided process
  samples. Product focusQuestion stays null. Full process expansion requires
  user approval after sample display; JDT/Step01–05 reruns, Activity regeneration
  and new reports are not authorized. See docs/plans/end-to-end-business-delivery-implementation-plan.md.
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
- Step07 and docs/modules/business-process-discovery/ own implemented system
  assessment, focused reading, DRAFT -> WRITE -> final RULE_REVIEW, private
  three-stage saving and producer v4 publication. Active supplements are
  navigation/history, not a second contract owner. Historical three-case
  quality did not fully pass; readability is distinct from factual correctness
  and whole-repository acceptance. Do not regenerate saved Activities.

## Current technical route and retirement boundary

- Current production is JDT LS navigation plus the JDT Core syntax helper,
  optional MyBatis/JSqlParser material, and Step05 reading packets. JavaParser,
  five-graph/Fact/Proof/Flow/Capsule/M10 producers are retired, not enhancements
  to re-enable. Preserve only demonstrably needed historical readers, DTOs,
  schemas and generic artifact observation; audit unused typed readers before
  deleting their dedicated tests.
- The current four-operation target is owned by
  docs/modules/technical-analysis/ and
  docs/plans/technical-analysis-cli-and-vue-cleanup-design.md.
  Existing three-command production and fixed-source results are real; their
  nested-call accuracy and intermittent query waits are not fully resolved.
  Do not describe completed Maven handoff/READY/SQL/materials as unimplemented.
- The target separates collect-frontend (R1) from backend collect-code (R2).
  analyze-persistence (R3) consumes exact R2; assemble-materials (R4) consumes
  exact R1 and R3, obtains R2 from R3, and matches HTTP requests there.
  R0 source preparation is unchanged. R1 must not require JDT/Maven/backend.
  Each backend entry gets one self-contained JSON, plus an index and frontend
  coverage. This design is not yet production; preserve old Packet readers.
- Official Maven classpath/effective-POM handoff is already implemented.
  User or authorized Agent runs Maven; Java consumes evaluated outputs,
  binds R0 and target JDK, and uses existing JDT. No Maven launcher,
  downloader, parent/BOM/profile evaluator or all-file diagnostic proof system.
  Known input/project errors block; unconfirmed diagnostic coverage is disclosed.
- Approved narrow corrections use JDT's own reliable call/declaration binding
  and exact physical site to prevent nested-call misassociation, distinguish
  confirmed external calls from unknown targets/query failures, retain necessary
  frontend source units, and project ORDER BY already returned by JSqlParser.
  Preserve legitimate implementation candidates. Do not build another resolver,
  JS interpreter, annotation processor, SQL parser or logging-name filter.
  Lombok and lifecycle wait investigations remain bounded verification;
  unproved causes and limits must remain visible.
- New schemas, producer receipts, source/version checks, storage/query and
  fixtures must move together according to the single runtime version matrix.
  Entry evidence uses R0-relative source paths and bundle-relative files.
  Capacity failures cannot truncate content then claim complete evidence.
  Scope the variable-file canonical-store change to this artifact and verify
  more than 64 entry files through real install/reopen.
- One Skill will orchestrate the four real commands; it is not an approval
  server or alternate analysis implementation. Current documentation work
  does not authorize editing the executable Skill or running customer tools.
  plan-materials is already retired; do not restore it as a fallback.
- Preserve full Java bodies, physical call positions, actual/formal arguments,
  hierarchy/definition/necessary implementation candidates and entry-owned
  expansion state. Sharing a method or cached query is not sharing an entry's
  final conclusion. Keep one physical RPC per session query key. The approved
  timeout fix preserves the pending request after a caller's wait expires;
  later callers reuse its eventual result rather than a stale timeout.
  Final failures remain cached; no automatic RPC or entry retry is added.
- ProgramGraphsExecution and PersistedProgramGraphInputReader currently serve
  JDT; their historical names are not deletion evidence. Historical JavaParser
  configuration decoding cannot instantiate a producer or fallback.
- The new technical design ends at saved Step05 materials. Do not rerun JDT,
  customer builds, Activity or business-process models during design work.
  New Step05 frontend material needs explicit consumer version handling; never
  discard its fields silently to claim old model consumers support it.
- source-preparation's completed fail-closed scope is separate from the new
  prepared-source-to-technical-production connection. Preserve old artifacts
  and more-findings.md. Cross-source-version incremental reuse remains deferred.

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
- Keep all eight steps and useful persisted technical outputs. Step01 verifies
  source once; Step03 publishes JDT navigation and complete Java code; Step04
  adds optional persistence material; Step05 assembles reading materials with
  actual/formal arguments, controls, returns, boundaries and code fragments.
  Historic Proof truthfulness is preserved on reopen but does not gate reading.
  Do not introduce another evidence hierarchy.
- The pre-migration Step06 material path used BusinessMaterialBuilder and
  ActivityExplainer. The saved 326 reviewed Activities remain reusable. New
  material ownership is Step05; M10 is not newly installed and the existing
  Activity consumer is not executed in this scope.
- Step 07 exposes exactly two deep internal Interfaces:
  BusinessProcessDiscovery and BusinessProcessPublisher. Discovery owns the
  FrozenAnalysisCorpus, RepositoryBusinessCataloger,
  ProcessMaterialAssembler, CandidateProcessReconstructor and
  RepositoryProcessConsolidator internals. Publication owns the deterministic
  repository process catalog, coverage and business-processes.md. Do not expose
  those internals as new public Agent methods.
- Step08 has no production generator or new generation objective. Preserve
  historical reader, artifact query and deterministic renderer.
- These are internal Modules behind the sole RepositoryAnalysisAgent public
  Interface. Do not create a second POC namespace, parallel runtime, public
  Interface, compatibility alias or dual writer.

## Program and model responsibilities

- Current user-selected roles: direction, scheduling, design documents,
  debugging and review GPT-6 Sol / xhigh; test writing Luna / xhigh;
  production code Terra / xhigh.
  Product business generation defaults to Luna / high in the logged-in
  ChatGPT Codex context. This does not change historical runtime identity or
  authorize a YAML edit in a documentation-only task. One job keeps its binding.
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
  repository consolidation. Process rendering and historical report rendering
  remain zero-Provider; no new nine-chapter job is in scope. Shared Activities are immutable and membership is many-to-many.
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
  in the single YAML owner; the current model default is Luna/high; exact limits belong to YAML. These
  are in-flight job limits, not maxMaterialsToStart, Builder K or actual N.
  Eligible queued jobs are not skipped when a concurrency slot is unavailable.
  Multiple keys or fresh sessions sharing an account/project do not create
  independent quotas; declared/observable shared scopes use one Provider cap.
- Step05 is the sole current owner of entry reading-material relationships and
  related source excerpts. It consumes saved JDT methods/calls/candidates and
  optional persistence material, never reparses or re-navigates, and records a
  collected entry or a specific not-collected reason. Historical context and
  Capsule contracts remain readers only.
- Historical M10 material is strict read-only. New Activity projection directly
  consumes saved Step05; identical full bodies may be deduplicated, with all
  calls, candidates, arguments, control/returns and packet-local sources kept.
  Safe XML structure projection of saved Resource.rawSource is allowed; do not
  rerun JDT, PersistenceAnalyzer or JSqlParser. Java never uses an industry
  dictionary to assign business purpose, actor, outcome or process.
- ActivityExplainer reads a complete local activity package and REVIEW sees
  the complete actual DRAFT. Preserve full reviewed conditions, rules, formulas
  and narrative through process knowledge and final report, not only labels.
- An Activity package has an arbitrary positive number `N` of entries and
  scope-local keys `E1...EN`; package-local keys always map through the owning
  material to global entry IDs. Never hard-code four entries, compare by
  prefixes/substrings, or join bare `E1` across packages. Configuration `K`
  limits Builder packaging but is not the repository entry count.
- Historical M10 capacity contracts stay exact on reopen. New Step05 small
  packages use complete DRAFT/REVIEW; large packages use complete paged navigation
  and bounded model reading to select full semantic slices, each with its own
  DRAFT/complete REVIEW. No fixed-line chunks, lossy summaries or oversized
  whole-package merge/review. Keep partial slice scope explicit: one successful
  slice must not mark its whole entry fully explained.
- A structurally and scope-valid Activity DRAFT may omit entry keys. It still
  enters the one allowed REVIEW with the complete actual DRAFT and
  program-computed `missingEntryKeys`. Invalid JSON, unknown keys/refs, output
  budget violations follow the task's exact failure contract. Historical Activity
  and Step07 behavior stays unchanged; new Activity bounded stage attempts
  belong only to the model-job-execution and Activity Module contracts.
- The Activity REVIEW output has a required, possibly empty,
  `unexplainedEntries` key array. Reviewed activity keys union unexplained keys
  must equal the material keys and be disjoint; any remaining omission is
  fatal, with no third call. Java maps unexplained keys to program-owned
  `MODEL_NOT_EXPLAINED`, not a source/Proof Gap. Process/report model input
  groups them by material as `{materialContext, unexplainedEntryKeys,
  reasonCode}`, sends the context once, and keeps global IDs program-side.
  Current consumers retain the HTTP entries and reason; historical report content stays immutable.
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
  Reading may include any saved Activity, its exact M10 or new Step05 source, and verified text; new Step05 source selection is target-only;
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
- Step08 remains historical read/query/render only. Future generation needs
  its own scope/design; do not preserve a second active generation protocol.
- Historical Activity, initial catalog and repository summary keep their exact
  DRAFT/REVIEW protocol; current candidate processes use three named stages.
  New Activity stage retries are the sole designed exception and do not
  extend Step07 into an automatic repair loop. A new candidate private
  record must save all three; an old pair is not a completed three-stage job.
  Check actual context at each stage. Initial capacity failure means zero requests plus a
  concrete uncovered reason. Once a request starts, transport/schema/runtime
  failure is fatal for that execution: stop new job dispatch; already-started
  jobs whose own preceding stages are valid finish their remaining authorized stages under existing
  timeouts and preserve outputs. This remains Step07's failure policy.
  New Activity jobs isolate package-local failures and continue other jobs;
  unsafe shared source/config/auth stops its affected scope. All stage attempts
  are saved; partial Activity cannot auto-enter Step07. Never switch Provider or fall back to
  an API key, replay an old request or synthesize success. An explicitly started
  new model batch may execute incomplete jobs under section 7; old STARTED and
  FAILED records remain immutable. Provider request journals are scoped by
  modelBatchId so a new explicit batch cannot be blocked by an older batch's
  uncertain STARTED request; completed job reuse still uses the validated job
  result and fingerprint. A new batch is not an automatic retry loop.
- Historical M10 `maxMaterialsToStart` is the explicit per-execution ActivityExplainer launch
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
- SourceRef maps frozen file, exact saved range and snippet. Historical M10
  refs use their stored scope; Step05 source refs are packet-local and model S
  refs request-local. Resolve material/packet/request ownership before
  normalization; bare S1 or source:1 is never a cross-package join key.
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

## Coverage, grouping and historical nine chapters

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
- Historical zero-entry or unprocessed-entry reports remain INCOMPLETE; this
  does not authorize new report generation.
- Historical Markdown has exactly the shared NineSectionProfile H2 sections:
  文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、
  示例问题、待确认事项.
- `business-processes.md` is the primary readable artifact answering which
  business processes exist and how each proceeds. Historical nine-section
  documents remain readable; this task defines no new downstream generator.
- Historical Chapter 7 uses only formulas/definitions present in reviewed input. With none,
  it explicitly says no definable metric was identified. Natural-language
  truthfulness is checked by configured model review and authorized human
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
  - Historical Step06: business-materials.jsonl, activity-explanations.jsonl,
    activity-coverage.json. Target new Step06 reads Step05 instead of installing
    M10; Activity v2/coverage v3 and private attempts follow integration-contracts.
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
  Current Step07 keeps those public schemas, writes private reading-decision v2,
  reading-packet v1 and three-stage reviewed-result v3 with producer v4.
  Exact Prompt/response versions belong to the owning process Module.
  Map new refs before closing the result, not by fetching in Publisher.
  Step08 remains historical reading/query/rendering only.
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
- Historical Activity and current Step07 cross-batch reuse is explicit and limited to complete, validated, privately
  saved reviewed jobs with matching content, source mapping, task sequence,
  Prompt/schema/profile and service/account/model/effort. Candidate three-stage
  reuse requires DRAFT, WRITE and final RULE_REVIEW; other tasks keep pair reuse.
  Reuse neither isolated intermediate responses nor unvalidated final responses.
  A failed job starts its full authorized sequence only in an explicitly requested new
  batch; prior completed results and provenance are immutable. A changed group
  or knowledge input invalidates only dependent process/summary/report results.
- New Step05 Activity is the explicit stage-reuse exception: its complete saved
  reading plan/slice/stage can be reused only with exact input/prompt/schema/
  scope/binding checks under model-job-execution. Manual retry creates a new
  batch, never edits old FAILED/STARTED records or extends Step07 retries.
- Single reading decisions have their own complete-record validation/reuse;
  they cannot masquerade as DRAFT/REVIEW. Catalog-as-input reuse differs from
  exact job reuse. Implemented Step07 model-job-execution-config-v3 binds Activity,
  material, verified inventory, optional catalog and focus question before
  Provider startup; changed inputs cannot silently reuse the queued run.
  Keep historical v2 exact reads. New Activity targets execution-config-v4 with
  an explicit material discriminator; v3 cannot silently accept new Step05.
- Batch/output integration must preserve dual ownership: material belongs to
  sourceRunId; Activity/Knowledge/Report outputs belong to the new batch run.
  Current reading-only output is analysis-run-output-v5; new Activity targets v6.
  Preserve strict historical v3/v4 reads and cross-run checks. Root request candidate lineage stays fixed; operational batches do not
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
  a complete process catalog/coverage and business-processes.md; historical reports are not a new completion requirement.
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
- Tests assert observable outcomes through the Step01–05 public seams,
  historical Activity/reader contracts, BusinessProcessDiscovery,
  BusinessProcessPublisher, or the historical report reader/renderer. Test target
  internals only where needed to prove card completeness, source hydration or
  coverage conservation; do not test past an Interface merely to preserve the
  legacy singleton ProcessExplainer or other retired shallow Modules.
- Stop when the expected RED cannot be established, required upstream data is
  absent, input/ref/coverage cannot close, a started model request fails, or
  implementation needs a contract change. Update durable design and obtain
  the required review/authorization; do not silently broaden behavior.
- Contract uncertainty and debugging go to GPT-6 Sol/xhigh. User approval remains required for
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
