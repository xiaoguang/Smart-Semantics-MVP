# GitHub Code Agent Instructions

## Scope

- This directory owns only the Java/Maven code-to-nine-section Agent and its
  tests, fixtures, design, CLI, Java Interface, and any future local loopback
  HTTP adapter. Ownership does not imply that every listed surface is already
  implemented; `docs/DESIGN.md` and the current stage design own maturity facts.
- Do not modify V6, Pinned assets, formal models, formal evidence, existing
  source candidates, frontend code, or another source Agent's implementation.
- The v0 profile supports frozen Java/Spring MVC/MyBatis source only. JPA,
  message brokers, schedulers, batch, reflection, AOP, SpEL, WebFlux and
  unsupported constructs must become explicit gaps.

## Fixed jshERP offline acceptance source

- The user explicitly approved one read-only offline acceptance capture of
  `https://github.com/jishenghua/jshERP.git` at commit
  `8c30ce7861570458920175e200bb2a6442713580`.
- Capture may only populate this Agent's ignored local workspace. The analyzer
  must subsequently consume the generated immutable manifest, never `master`
  or a live working tree.
- Do not run the captured repository's Maven, plugins, tests, scripts, or
  application. Do not silently refresh the commit.

## Model and testing rules

- Critical reasoning, architecture, and important design documentation use
  `gpt-5.6-sol` with `ultra` reasoning.
- The sole target-design authority is `gpt-5.6-sol / ultra` (the Sol/ultra
  Design Authority). `gpt-5.6-sol / xhigh` is a debug role only and cannot
  approve architecture or contract changes.
- Production implementation uses `gpt-5.6-terra` with `xhigh` reasoning.
- TDD test writing uses `gpt-5.6-luna` with `xhigh` reasoning. LLM code review,
  bounded source reading, and nine-section Markdown proposal generation also
  use `gpt-5.6-luna` with `xhigh` reasoning. Debugging uses `gpt-5.6-sol` with
  `xhigh` reasoning.
- Automated tests use only a scripted fake Provider. They never invoke a live
  model, network source, API key, or customer build.
- A live Luna task requires the logged-in Codex session preflight and a frozen
  task package. For every eligible Flow, Stage 06 first uses one isolated
  `R0_REGISTRY_PROPOSAL`; the program validates all R0 results and freezes one
  `RepositoryInterpretationRegistry`, then R0-ready Flows use one finite-key R1
  interpretation and one R2 precision review. Let `N` be all Stage05 Flows,
  `E` the eligible subset, and `R` the R0-ready subset of `E`: Stage06 has
  `E + 2R` planned tasks and matching typed dispositions. Persisted rounds and
  started Provider calls are a subset of those tasks; only when every planned
  task starts and `R=E` are there `3E` calls. Ineligible Flows
  have zero Stage06 per-Flow objects and receive a Stage07 technical fallback.
  R0/R1/R2 never share context across Flows; R2 is not a product
  candidate replacement and does not consume another `ReaderCandidateRound`.
  Once a Provider call has started, never retry it, switch provider, or fall
  back to an API key automatically; fail the current run and preserve safe
  diagnostics.
- Stage 05's same persisted EvidenceCapsule is the sole source-evidence input
  for a Flow's R0/R1/R2. R0 may propose bounded business labels/purposes with
  explicit same-Capsule basis, but cannot create Facts, locators, Flows, or
  Markdown. An organization registry is an optional exact-match seed only;
  every seed-derived item still requires R0 and non-empty Capsule basis. R1/R2
  cannot start until all R0 dispositions are closed and the one repository
  registry has been atomically frozen.
- Each live-model receipt must record the observed provider, model, reasoning
  effort, and sandbox. A mismatch from the frozen task policy is terminal for
  that model-enhanced candidate: preserve the JSON failure receipt, do not
  render it, and do not silently retry or resume it with another runtime.
- Model configured Adapter identity, configured Auth Mode, and observed
  upstream provider are different fields. Never compare or persist one as if it
  were another. If the execution path does not keep them separate, or cannot
  verify any required identity, fail closed before admitting the response.
- The program, not Luna, validates paths, locators, hashes, source facts,
  evidence references, section ownership, and final Markdown.
- Every named target module follows the exact handoff in its stage document:
  Luna/xhigh writes one-behavior-at-a-time RED tests against public seams and
  independent goldens; Terra/xhigh starts only after observing the expected
  RED and implements the smallest GREEN vertical slice. Both use the listed
  targeted Maven selector only—never network, a live Provider, customer Maven,
  or private-implementation coupling.
- Luna/Terra must STOP a slice when an expected RED cannot be established,
  required upstream data is absent, implementation conflicts with the target,
  or schema/failure/model-boundary semantics would need to change. Record
  evidence/options in the task progress file and ask the Sol/ultra Design
  Authority. Never silently change a schema, golden, failure level, retry, or
  model boundary. Any cross-stage architecture, business-goal, trust, source,
  nine-section, safety, or model-boundary change also requires explicit user
  confirmation before design, RED, or implementation proceeds.

## Design, artifact reuse, and deferred runtime recovery

- `docs/DESIGN.md` is the authoritative target architecture. Current code, POC
  artifacts, tests, and maturity records may validate or falsify it, but must
  never silently weaken the target to match the implementation.
- Maintain exactly one current detailed target design for every production
  stage:
  - `docs/stages/01-freeze-source.md`
  - `docs/stages/02-discover-application-and-entries.md`
  - `docs/stages/03-build-five-program-graphs.md`
  - `docs/stages/04-prove-code-facts.md`
  - `docs/stages/05-compile-business-flows.md`
  - `docs/stages/06-interpret-one-flow-at-a-time.md`
  - `docs/stages/07-admit-and-merge-business-knowledge.md`
  - `docs/stages/08-build-nine-section-document-and-archive.md`
- `docs/stages/00-mvp.md` is a POC record, not a ninth target stage or an
  alternate production path.
- Before any POM, build configuration, production code, or test work, read
  `docs/plans/target-standards-and-toolchain-plan.md`. It is the approved
  execution standard for project-local JDK 17 Toolchains, formatter/quality
  gates, direct-selector command prefixes, Luna/Terra/Sol roles, progress
  discipline, and default-authority/platform boundaries; do not duplicate or
  weaken its detailed matrices here.
- When a work unit changes an invariant or architecture used by more than one
  stage, update `docs/DESIGN.md` in that same work unit.
- `docs/DESIGN.md` owns stable architecture and cross-stage invariants. A stage
  document owns that stage's current design, implemented result, tests, gaps,
  and exit conditions. README files are navigation and capability indexes.
- **Design-publication gate:** before any production-code, test, configuration,
  or behavior-changing implementation edit, update the applicable target
  design (`docs/DESIGN.md` and the affected stage document) first. Complete a
  coherent docs-only commit and fast-forward push that design to `origin/main`
  before starting the corresponding implementation slice. If implementation
  exposes a design correction, stop that slice, update and publish the design
  first, then resume from a fresh RED; never let an unpushed local design
  become an implementation-only contract.
- Target production persistence is stage-by-stage: every successful stage
  immediately installs canonical JSON/JSONL artifacts under its analysis run
  directory, records exact input/tool/profile/schema/prompt hashes, and
  preserves upstream artifacts when a downstream stage fails. Five standalone
  graph files are first-class Stage 03 artifacts. Final-only Candidate archive
  behavior is a current maturity fact, not the target contract.
- **Do not confuse three layers.** Coding Agent continuity uses Git plus that
  Agent's own `progress/*.md`. Stage/module JSON, JSONL and receipts are core
  business analysis products and explicit upstream/downstream handoff formats:
  a new stage execution may validate and consume them without rescanning source
  or reprocessing valid upstream stages. Neither layer is product runtime
  recovery. Same-run process-crash recovery, queue/worker takeover, model-call
  recovery and terminal repair are deferred in
  `docs/supplements/runtime-recovery-todo.md`; that supplement is not an
  implementation contract. Do not reintroduce any such mechanism unless the
  user explicitly approves a new design work unit.
- Active v0 does not resume an interrupted run. Preserve every fully installed
  JSON/JSONL artifact, receipt and diagnostic; mark the interrupted run failed,
  and let the caller create a new run or explicit downstream-stage execution.
  A Provider call that has started is never retried or switched automatically.
- A successful explicit execution ending before Stage08 is `FINISHED` with a
  null `analysisResult` and no root run manifest. Only an execution through
  Stage08 produces one of the four repository `AnalysisResult` values.
- Stage-internal handoff is persisted too. Every named module writes its exact
  schema-versioned canonical JSON/JSONL `ModuleArtifact` under the stage's
  `modules/<nn-module>/` directory. The next module must reopen and validate
  its ID/SHA/upstream/control references; it cannot consume a predecessor's
  private object or bypass the artifact. Each stage document fixes, per module,
  the problem, upstream preconditions, deterministic/LLM order, output schema
  and DepotHead example, invariants, Gap/fatal/artifact-reuse behavior, downstream guarantee,
  non-goals, public test seam, Luna RED brief, and Terra GREEN brief.
- Target scope is the complete frozen repository. The DepotHead eight-file
  `BOUNDED_PATH_SET` is only a walkthrough/local fixture and is never eligible
  to complete a repository analysis. Stage 02 inventories every entry; every
  entry is compiled to a Flow or gets an evidence-backed Gap/EXCLUDED
  disposition. Resource sharding may change scheduling only: shard denominator
  ID sets must be disjoint and their canonical union must equal the complete
  denominator.
- If a repository has N supported FlowSlices, Stage 05 persists N independent
  EvidenceCapsules. Let E be the eligible subset and R its R0-ready subset.
  Stage 06 persists E R0 dispositions, exactly one frozen
  RepositoryInterpretationRegistry, E final interpretation dispositions, and
  2R R1/R2 planned tasks and typed dispositions. Rounds/started calls are a
  verified subset; only an all-start `R=E` run has 3E calls. Ineligible Flows have zero
  Stage06 per-Flow objects, and Stage07 writes their unique technical-fallback
  admission decisions. Stage 07 preserves
  `registryProposalId -> provisionalKey -> interpretationProposalId ->
  selectedKey -> meaningId` while merging all admitted interpretations and
  deterministic facts into exactly one RepositoryKnowledge. Stage 08 generates exactly one
  repository-level NineSectionPlan and one `document.md`; per-Flow Markdown
  and pre-rendered-fragment concatenation are forbidden. A run completes only
  when its RepositoryCoverageLedger accounts for every file, site, entry,
  graph item, fact/atom, outcome, Flow, model proposal/disposition, knowledge
  item, and section owner. One Flow PASS never completes a repository analysis.
- The sole public target seam is one run-centric `RepositoryAnalysisAgent`
  with `start`, `executeStage`, `inspect`, `artifact`, `render`, `validate`,
  and `trace`. `executeStage` always creates a new execution identity from
  exact validated upstream publication references; it is not same-run resume.
  Java, CLI, and authenticated loopback HTTP are symmetric adapters.
  Artifact lookup always requires `runId + artifactId`; an optional expected
  `ArtifactLocation` is the sealed `STAGE_MODULE | VALIDATION_MODULE |
  STAGE_PUBLICATION | RUN_MANIFEST` union, and type/digest values are
  expected-value checks, never locators. External validator views do not
  invent stage fields, and trace requires a bounded hop budget
  with an optional expected Candidate identity. No public
  request or response may accept or expose a filesystem `Path`. `artifact`
  returns full bytes only for a registry policy explicitly marked
  `PATH_FREE_COMPLETE_UTF8`; raw source-bearing artifacts (including raw
  Trace records), prompts, and raw model responses are metadata-only. `trace`
  is the only validated, path-free public projection of source hops. Read-only
  observation methods never call a Provider or execute customer code.
- Progress files record only coding-work continuity state: scope, completed checks,
  changed paths, verification, blockers, and next action. They never replace or
  override overall or stage design.

## Documentation readability

- Overall and stage designs are function-first. Before introducing records,
  class names, Interfaces, schemas, identities, algorithms, budgets, security,
  failure codes, tests, or maturity, explain in this order: why the stage
  exists; its concrete input; step-by-step work; observable persisted
  artifacts; how downstream consumes them without reprocessing; success/Gap/
  fatal and explicit upstream-artifact reuse behavior; and program/model
  responsibilities.
- Every future stage design must point to its position on the single main flow
  in `docs/DESIGN.md` and use the real fixed DepotHead path as the shared walkthrough.
  Label real source, deterministic conclusions, model interpretation,
  unknowns, and illustrative target JSON separately.
- Include one real, bounded example with an explicit evidence boundary and an
  honest success, Gap, or rejection result. Never use a historical artifact as
  proof of current or target behavior.
- State explicitly how the stage's evidence confirms or changes the target
  design. A stage document is an implementation record and design-feedback
  surface, not a competing overall architecture.
- Keep target design and current maturity in separate sections. Use Chinese
  status labels for the current audit, and preserve the fixed jshERP result as
  Gap / zero Flow / zero Capsule until a new, directly verified run proves
  otherwise.
- In the reader layer, lead with Chinese terms and plain-language explanations.
  In technical-reference sections, retain exact code, Interface, field, command,
  and artifact names.

## Per-agent progress files

- Before modifying code, tests, configuration, or durable documentation, every
  root, sub-agent, and debug agent creates one tracked file at
  `progress/<task-slug>.md`. Its task brief must name that exact path.
- An agent owns only its own progress file. If it cannot create or update it,
  it must stop before changing implementation state.
- Use `progress/TEMPLATE.md`. Update the current state in place before a long
  command, after every verifiable step and test, on a blocker, and at task end.
  Do not append a chronological work log.
- Record scope, approvals, changed paths, completed work, test commands and
  concise results, decisions, blockers, and the exact next action. Do not put
  secrets, full prompts, large source excerpts, or large logs in progress.
- A coding Agent continuing interrupted work reads its progress file, checks
  `git status`, and verifies
  referenced artifacts and tests before continuing. Finished files are marked
  `COMPLETE` and retained.

## Runtime and output

- Use Java 17. Keep target v0 as one Maven module with package-private deep
  modules; do not introduce a premature multi-module reactor.
- MyBatis is source input only: use Java/XML parsing and never add or execute
  the MyBatis runtime. JPA is not in v0.
- The body always has exactly the nine agreed H2 sections. IDs, hashes, prompts
  and receipts belong in sidecars, never in normal business prose.
- `inspect` and other diagnostic views are read-only projections of already
  installed artifacts. They cannot inject files, locators, evidence, Facts, or
  scope into business outputs. Every item entering a Candidate, reader Markdown,
  or formal Trace must traverse fresh-validated Stage01–07 publications and
  their exact coverage/accounting; diagnostics cannot replace any stage
  publication or receipt.
- Fact admission requires semantic Evidence closure. A known Evidence ID,
  valid source/excerpt hashes, and closed references prove integrity and
  identity only. Every semantic atom claimed by a Fact must be supported by
  bytes inside Evidence spans that the Fact itself references, plus a
  deterministic Proof or an independently recorded audit proof. Evidence
  elsewhere in the Manifest cannot fill a missing atom unless the Fact
  explicitly references it. Reject an unsupported Fact or emit a Gap before
  Candidate construction.
- Reader admission is stronger than nine headings and a valid SHA. Every
  admitted fact must contribute its semantic atoms—kind, attributes,
  conditions, literal values, and relationships—to reader content, an attached
  technical basis, an explicit Gap, or a reasoned exclusion. Silent atom loss
  is fatal; raw word count or file size is not a substitute for this gate.
- A standard MyBatis mapper `DOCTYPE` declaration is acceptable source syntax.
  Parsing must disable external DTD, general/parameter entity, schema, and all
  network resolution; any attempt or inability to enforce those settings fails
  closed.
- Every Agent-produced machine artifact is canonical UTF-8 JSON (`*.json`) or
  append-only JSON Lines (`*.jsonl`). Do not create Java serialization,
  databases, binary caches, or private intermediate formats. The exceptions are
  the final reader-facing `document.md`, durable design/progress Markdown, and
  the immutable source files retained verbatim as inputs rather than generated
  artifacts.
