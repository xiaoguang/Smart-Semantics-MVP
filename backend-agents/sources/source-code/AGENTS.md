# Source Code Analysis Agent Instructions

## Scope and target identity

- This directory owns only the Java/Maven frozen-source-to-nine-section Agent,
  its tests, fixtures, design, CLI, Java Interface, and future authenticated
  loopback HTTP adapter.
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
  history and must not be executed as the current plan. A future implementation
  plan must be derived from the four deep Modules in the active design.
- Update and review the applicable target design before implementation. This
  instruction does not authorize a Git commit, push, model call, source scan,
  capture, freeze, package or deployment; follow the user's explicit scope.
- README is navigation/capability indexing. Analysis-step documents own their
  step's detailed design, tests, Gaps, stops and current maturity.
- docs/modules/java-code-engines/ owns the approved engine Interface,
  JDT/Core internals, common material contract and two-phase migration.
  These are internal Modules, not extra production analysis steps.

## Selectable Java code engines

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

## Eight analysis steps and the four deep Modules

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
- Step 06 uses exactly two target business Modules:
  BusinessMaterialBuilder and ActivityExplainer.
- Step 07 uses exactly one target business Module: ProcessExplainer.
- Step 08 uses exactly one target business Module: BusinessReportPublisher.
- These are internal Modules behind the sole RepositoryAnalysisAgent public
  Interface. Do not create a second POC namespace, parallel runtime, public
  Interface, compatibility alias or dual writer.

## Program and model responsibilities

- Critical architecture/design uses gpt-5.6-sol / ultra or gpt-6-astra /
  ultra. Production implementation uses gpt-5.6-terra / xhigh after design and
  RED. TDD test writing, bounded source reading and review use gpt-5.6-luna /
  xhigh. Product activity/process/report DRAFT and REVIEW use gpt-5.6-luna /
  high.
- Automated tests use frozen fixtures and a deterministic scripted Provider.
  They never invoke a live model, network source, API key or customer build.
- A live Luna task needs current explicit authorization, logged-in Codex
  session preflight, one frozen input package and declared limits. There is no
  API-key fallback.
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
- ProcessExplainer
  performs programmatic loose recall, then model-backed whole-process and
  bounded repository synthesis. BusinessReportPublisher lets the model author
  natural paragraph JSON and review the complete nine chapters; Java supplies
  Markdown styling.
- Each activity package, process group, repository summary and report uses at
  most one DRAFT plus one REVIEW. Capacity failure means zero requests plus a
  concrete uncovered reason. Once a request starts, transport/schema/runtime
  failure is fatal for that execution: never retry, switch Provider, fall back
  to an API key, replay or synthesize success.
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

- Strict Step 04 Proof remains authoritative for exact technical claims.
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
- Every Provider request supplies a closed JSON Schema generated from the
  current task profile and its scope-local allowlists (for example activity
  IDs and short refs). It must name the complete output shape, enums and
  capacity limits before a request starts; Java revalidates the returned JSON
  and remains the authority for reference scope and persistence.
- A model may reuse allowlisted refs and create scope-local business labels.
  It cannot create source refs, paths, lines, hashes, Facts, Proofs, Flows,
  human confirmations or external identities.
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
  The target methodCondition is explicit and versioned; do not silently place
  it in the current EntryPointV2 schema. Correct the current misclassification
  of UserController#getOrganizationUserTree and
  MaterialCategoryController#getMaterialCategoryTree with targeted future tests.

## Coverage, grouping and the nine chapters

- FlowSlice is an entry-rooted technical slice, not a smallest business
  process. Flow, activity and BusinessProcess are many-to-many. A missing Flow
  does not erase an entry when the same frozen snapshot has safe located
  material; preserve the technical Flow Gap.
- Every discovered entry must be ANALYZED, ANALYZED_WITH_GAPS or NOT_ANALYZED
  with a concrete reason. One packet/group PASS never completes a repository.
- Process recall may use calls, explicit identifiers, data relations, reviewed
  objects/terms and processJoinSignals. Cues do not prove order, causality,
  identity or merge. Same-name and different-name activities are not
  automatically merged; one activity may belong to several processes.
- A large repository is handled as complete reviewed activities, bounded
  overlapping groups, reviewed group summaries and one bounded repository
  synthesis. If summarization would omit groups/conditions/rules/formulas,
  mark the omitted items and repository coverage PARTIAL; never silently
  compress and claim completion.
- Zero entries or all entries not analyzed may yield a nine-chapter scope
  report, but semantic delivery remains INCOMPLETE.
- The final Markdown has exactly the shared NineSectionProfile H2 sections:
  文档说明、业务目标、业务对象、业务活动、字段与维度、对象关系、指标口径、
  示例问题、待确认事项.
- Chapter 7 uses only formulas/definitions present in reviewed input. With none,
  it explicitly says no definable metric was identified. Natural-language
  truthfulness is checked by whole-report Luna REVIEW and authorized human
  sample review, not a Java business-language parser.
- By default the renderer inserts a collapsible technical-basis area inside
  Chapter 1, with valid same-document anchors and raw file/lines/snippet from
  source-refs.jsonl. It creates no tenth H2 and does not depend on a future
  HTTP/frontend source viewer.

## Persistence, reuse and recovery

- Keep observable ModuleArtifact/receipt and step publications. On the normal
  trusted path, build/compile/project each owned result once and pass immutable
  typed views. Publishers serialize, check type/ID/ref/budget and atomically
  install; they must not replay the producer's algorithm.
- Disk/new-process/import reuse verifies stored identity/hash/schema/ref/basis
  and read source bytes at the boundary. Explicit independent audits and
  mutation tests may replay algorithms; ordinary internal consumers must not.
- New business checkpoints are:
  - Step 06: business-materials.jsonl, activity-explanations.jsonl,
    activity-coverage.json
  - Step 07: business-processes.jsonl, repository-business-knowledge.json,
    process-coverage.json
  - Step 08: business-report.json, source-refs.jsonl, document.md,
    report-validation.json
- Save business-materials after compilation and before the first model call.
  The target is to save each completed reviewed activity/process package
  immediately so later failures preserve completed checkpoints. Current
  ActivityExplainer/ProcessExplainer aggregate and publish after their loops;
  fixed module addresses cannot be repeatedly installed with differing bytes.
  Immediate per-package persistence remains an independent known gap and must
  not be claimed complete or expanded into a recovery subsystem. In-process
  callers may pass typed immutable objects and need not fresh-reopen between
  internal operations.
- A simple inputFingerprint covers actual content inputs excluding a new
  runId, actual Prompt content/version, effective model/output configuration
  and Module version. Reuse requires equality; Prompt text or effective input
  changes invalidate the checkpoint.
- Do not introduce per-internal-Module fresh reopen, layered hash chains, a
  fixed total output count, whole-record replacement state machines,
  bridge/reconciliation ledgers or complex same-run recovery.
- Active v0 never resumes or replays an uncertain started Provider call.
  Same-run crash/worker takeover and terminal repair remain deferred.

## Public seam and output rules

- The sole public target seam remains one run-centric RepositoryAnalysisAgent
  with start, executeStep, inspect, artifact, render, validate and trace.
  executeStep creates a new execution identity; it is not same-run resume.
- Java and CLI may arrive before authenticated loopback HTTP. Completing all
  three adapters is not a business-quality gate; HTTP and a second product
  Provider are deferred integrations.
- No public request/response accepts or exposes filesystem Path. Raw source,
  prompts and model responses remain protected according to existing artifact
  policy.
- Read-only observations never call a Provider, execute customer code, inject
  evidence or replace a publication.
- Fixed 52 reader-visible outputs and the prior 57-output implementation are
  not target completion criteria. Existing Step 01–05 technical publications
  remain; new business completion is defined by the checkpoints, coverage and
  one validated nine-section report above.
- Machine artifacts are UTF-8 JSON/JSONL. Exceptions remain document.md,
  durable design/progress Markdown and immutable source inputs retained
  verbatim.

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
  `analysis.knowledge.ProcessExplainer`, `ModelRuntimeIdentityV1`, EntryContext,
  facts, gaps, signals and SourceRefs. Current work must not restore or extend
  that retired route; it only verifies the active business chain.
- Adjust existing semantic packages and four business Modules. The approved
  analysis.code engine seam replaces hardwired Java parsing only; it does not
  authorize another business runtime, broad Wire Reset or storage/recovery
  subsystem. Preserve Git history, JavaParser algorithms and progress files.

## Testing and stop rules

- Use TDD for implementation: one behavior RED at the applicable deep Module
  Interface, then the smallest GREEN. Run only tests added by or directly
  covering the current change; never start a full suite without explicit user
  request.
- Tests assert observable outcomes through BusinessMaterialBuilder,
  ActivityExplainer, ProcessExplainer, BusinessReportPublisher or the existing
  Step 01–05 public seams. Do not test past the Interface merely to preserve
  retired shallow Modules.
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
- Use the explicit synthetic replenishment-to-receipt-to-payable-bill story
  for cross-process design. Never present it as jshERP behavior.
- Reader chapters use Chinese business language first; technical fields may
  appear as supporting detail. Keep target design and current maturity
  separate.

## Per-agent progress files

- Before modifying code, tests, configuration or durable documentation, every
  Agent creates one tracked progress/<task-slug>.md from progress/TEMPLATE.md.
  Each Agent owns only its file.
- Update current state in place before a long command, after every verifiable
  step/test, on a blocker and at task end. Do not append a chronological log.
- Record scope, approvals, changed paths, checks, decisions, blockers and the
  exact next action. Do not include secrets, full prompts, large source
  excerpts or logs.
- Continuing work reads its progress, checks Git status and verifies referenced
  artifacts/tests. Completed progress files remain tracked and unchanged.
