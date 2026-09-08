# Source Code Analysis Agent Instructions

## Scope and target identity

- This directory owns only the Java/Maven frozen-source-to-nine-section Agent,
  its tests, fixtures, design, CLI, Java Interface, and future authenticated
  loopback HTTP adapter.
- The target directory is `backend-agents/sources/source-code/`, the Maven
  coordinate is `org.sourceanalysis:source-code-analysis-agent`, the display
  name is `Source Code Analysis Agent`, and the Java root is
  `org.sourceanalysis.app`.
- The approved directory, Maven, and Java-package reset is complete. The old
  `sources/github-code/` path may occur only in Git history, preserved
  progress/history documents, completed migration plans, or unsupported-wire
  test data; it is not an active alias, symlink, compatibility reader, or
  second implementation.
- Do not modify V6, Pinned assets, formal models, formal evidence, existing
  source candidates, frontend code, or another Source Agent's implementation.
- Target v0 supports frozen Java/Spring MVC/MyBatis source only. JPA, message
  brokers, schedulers, batch, reflection, AOP, SpEL, WebFlux, and unsupported
  constructs become explicit Gaps.

## Target package registry

- The public application seam lives at `org.sourceanalysis.app`.
- The eight analysis packages are exactly:
  - `org.sourceanalysis.app.analysis.inventory`
  - `org.sourceanalysis.app.analysis.discovery`
  - `org.sourceanalysis.app.analysis.graph`
  - `org.sourceanalysis.app.analysis.fact`
  - `org.sourceanalysis.app.analysis.flow`
  - `org.sourceanalysis.app.analysis.interpretation`
  - `org.sourceanalysis.app.analysis.knowledge`
  - `org.sourceanalysis.app.analysis.document`
- The cross-cutting roots are exactly:
  - `org.sourceanalysis.app.capture.localgit`
  - `org.sourceanalysis.app.artifact`
  - `org.sourceanalysis.app.evidence`
  - `org.sourceanalysis.app.runtime`
  - `org.sourceanalysis.app.validation`
  - `org.sourceanalysis.app.adapter.cli`
  - `org.sourceanalysis.app.adapter.http`
  - `org.sourceanalysis.app.adapter.provider`
- Future database analysis uses `org.sourceanalysis.db.analysis`; it is
  documentation-only in this work and must not be created here.
- Do not create `target`, `mvp`, numbered analysis packages, `common`,
  `shared`, `misc`, `utils`, `codemd`, `github`, or `linguan` in target Java,
  Maven, artifact, schema, type, command, or fixture names. Do not create a
  generic helper root: put each rule at the semantic seam that owns it.

## Fixed jshERP offline acceptance source

- The user explicitly approved one read-only offline acceptance capture of
  `https://github.com/jishenghua/jshERP.git` at commit
  `8c30ce7861570458920175e200bb2a6442713580`.
- Capture may populate only this Agent's ignored local workspace. Analysis
  consumes the generated immutable manifest, never `master` or a live working
  tree.
- Do not run the captured repository's Maven, plugins, tests, scripts, or
  application. Do not silently refresh the commit.

## Model and testing rules

- Critical reasoning, architecture, and important design documentation use
  `gpt-5.6-sol` with `ultra` reasoning. The sole design authority is
  `gpt-5.6-sol / ultra`; `gpt-5.6-sol / xhigh` is a debug role only.
- Production implementation uses `gpt-5.6-terra / xhigh` only after target
  design and corresponding RED are frozen. TDD test writing, bounded source
  reading, R0/R1/R2/P1/P2 execution, code review, and reader-slot validation
  use `gpt-5.6-luna / xhigh`.
- Automated tests use only frozen fixtures and a scripted fake Provider. They
  never invoke a live model, network source, API key, or customer build.
- A live Luna task requires current explicit authorization, the logged-in
  Codex-session preflight, and one frozen task package.
- For every eligible Flow, flow interpretation runs one isolated
  `R0_REGISTRY_PROPOSAL`. The program closes all R0 dispositions and freezes
  one `RepositoryInterpretationRegistry`; each R0-ready Flow then receives one
  finite-key R1 interpretation and one R2 precision review.
- Let `N` be all business Flows, `E` the eligible subset, and `R` the R0-ready
  subset of `E`. The **local R0/R1/R2 lane** has `E + 2R` planned tasks and
  matching typed dispositions. Only when every local task starts and `R = E`
  are there `3E` local Provider calls. Ineligible Flows have no **local**
  interpretation task/round/candidate/disposition, but remain in deterministic
  process grouping and process-admission consideration and receive a local
  repository-knowledge technical fallback.
- The same persisted EvidenceCapsule is the sole source-evidence input for a
  Flow's R0/R1/R2. R0 may propose bounded business labels/purposes with
  same-Capsule basis; it cannot create Facts, locators, Flows, or Markdown.
- `Flow` is one entry-rooted local auditable activity; `BusinessProcess` is an
  end-to-end process across Flows, and the relationship is many-to-many.
  BusinessFlows emits evidence-backed `processJoinSignals`; signals are clues,
  never proof of sequence, causality, uniqueness, or an external effect.
- P1/P2 are the sole bounded multi-Flow model exception. They read only a
  program-built, recursively path-free `ProcessModelPacketV1`; path-bearing
  persisted group material is program-only. P2 may only KEEP, NARROW, DROP,
  or PENDING_CONFIRMATION, and every protected reference set is a subset of
  the same P1 hypothesis. Only deterministic review Gaps may be added, and
  they cannot support a claim.
- Let `C` be candidate cross-Flow edges, `G` the groups covering every Flow,
  `A` all persisted ownership shards, and `S` the model-safe subset of `A`.
  Every edge has exactly one owner across `A`; every shard has one process
  disposition, including no-model shards with zero model objects. Planned model tasks are exactly
  `E + 2R + 2S`; actual calls are exactly
  `E + R + accepted local R1 + S + accepted process P1`. Unrun R2/P2 tasks
  persist `NOT_RUN_UPSTREAM_FAILED`.
- Once a Provider call starts, never retry it, switch provider, fall back to
  an API key, or resume it automatically. Fail the current run and preserve
  safe diagnostics.
- Configured adapter identity, configured auth mode, expected runtime, and
  observed upstream provider are separate fields. Any missing or mismatched
  required identity fails closed.
- The program, not the model, validates paths, locators, hashes, source facts,
  evidence references, section ownership, and final Markdown.
- Each target module follows its analysis-step document. Luna/xhigh writes one
  behavior-at-a-time RED tests against public seams and independent goldens;
  Terra/xhigh begins after the expected RED and implements the smallest GREEN
  vertical slice. Use only the listed targeted Maven selector.
- Stop a slice when the expected RED cannot be established, required upstream
  data is absent, implementation conflicts with design, or schema/failure/
  model-boundary semantics would need to change. Record evidence and ask the
  Sol/ultra Design Authority; do not silently change the contract.
- Contract or schema uncertainty goes to Sol/ultra. Sol/ultra may approve a
  bounded local or adjacent-module protocol adjustment only after recording
  its rationale, affected contracts/consumers, preserved invariants, and
  fail-closed migration rule in durable design. User approval is reserved for
  changes to the eight step set/order/key, fixed nine-section contract,
  evidence/Proof/Trace trust rule, model-visible material or responsibility,
  cross-step identity/publication, public `RepositoryAnalysisAgent`, or the
  formal artifact/accounting boundary including 57 outputs. Sol/xhigh performs
  root-cause debugging only. Scripted providers are the automated default;
  live Luna needs separate authorization and preflight, and there is never an
  API fallback.

## Authoritative design and publication gate

- `docs/DESIGN.md` is the authoritative target architecture. Current code,
  historical artifacts, tests, and maturity records may validate or falsify
  it but never weaken it silently.
- Maintain exactly one detailed design for each production analysis step:
  - `docs/analysis-steps/01-verified-source-inventory.md`
  - `docs/analysis-steps/02-application-discovery.md`
  - `docs/analysis-steps/03-program-graphs.md`
  - `docs/analysis-steps/04-proven-code-facts.md`
  - `docs/analysis-steps/05-business-flows.md`
  - `docs/analysis-steps/06-flow-interpretation.md`
  - `docs/analysis-steps/07-repository-knowledge.md`
  - `docs/analysis-steps/08-nine-section-document.md`
- `docs/history/` contains history-only records. They are not target
  navigation, a production contract, or evidence of current behavior.
- Before POM, build configuration, production code, test, fixture, schema, or
  behavior changes, read both implementation plans under `docs/plans/`.
- **Design-publication gate:** update the applicable target design first,
  complete a coherent docs-only commit, and fast-forward push it to
  `origin/main` before implementation begins. If implementation exposes a
  correction, stop, publish the corrected design, and resume from a fresh RED.
- `docs/DESIGN.md` owns stable architecture and cross-analysis-step
  invariants. Each analysis-step document owns that step's design,
  implementation audit, tests, Gaps, and exit conditions. README is navigation
  and capability indexing.

## Analysis-step and runtime vocabulary

- The closed semantic keys and their ordered runtime directories are:
  - `verified-source-inventory` → `steps/01-verified-source-inventory/`
  - `application-discovery` → `steps/02-application-discovery/`
  - `program-graphs` → `steps/03-program-graphs/`
  - `proven-code-facts` → `steps/04-proven-code-facts/`
  - `business-flows` → `steps/05-business-flows/`
  - `flow-interpretation` → `steps/06-flow-interpretation/`
  - `repository-knowledge` → `steps/07-repository-knowledge/`
  - `nine-section-document` → `steps/08-nine-section-document/`
- Numerical prefixes order documentation and runtime directories only. Java
  types, fields, package names, schema versions, artifact IDs, tests, and
  commands use semantic names.
- Target types use `AnalysisStep` only for the generic abstraction and use
  semantic prefixes such as `VerifiedSourceInventoryReference` for concrete
  outputs. Public execution is `executeStep(AnalysisStepExecutionRequest)`;
  no future type or field uses a numbered step name.
- Every named module writes one or more schema-versioned canonical JSON/JSONL
  `ModuleArtifact` payloads under `modules/<nn-module>/`, then installs a
  receipt last. The next module must fresh-reopen the payload and receipt and
  validate ID/SHA/upstream/control references.
- Every successful analysis step immediately installs its official semantic
  outputs and one semantic receipt under its run directory. Five standalone
  program graphs are first-class outputs. Downstream failures preserve valid
  upstream publications.

## Full Wire Reset

- The structural Wire Reset has moved the source directory, replaced the
  Maven identity and Java namespace, and deleted pre-reset production/test
  packages and obsolete fixtures. Subsequent implementation writes only the
  new semantic wire and must not reintroduce removed code.
- Do not add compatibility readers, legacy aliases, migration bridges,
  symlinks, old-to-new translators, dual writers, or fallback discovery.
  Pre-reset paths, descriptors, schema versions, receipt names, package
  identities, and numbered types fail closed with no new publication.
- Preserve Git history and every historical `progress/*.md` file. Those are
  engineering audit records, not runtime inputs.
- The removed pre-reset tree under `com.linguan.codemd` contained bounded
  inventory/discovery vertical slices and older implementations. Their
  results survive only as historical engineering evidence; they are not
  current capability, a target naming exception, or a reusable compatibility
  seam.

## Persistence, reuse, and recovery boundary

- Coding continuity uses Git plus each Agent's own `progress/*.md`. Module,
  analysis-step, run, and validation artifacts are business products. Neither
  layer is product runtime recovery.
- Active v0 does not resume an interrupted run. Preserve fully installed
  artifacts/receipts and diagnostics, mark the run `FAILED`, and let the
  caller create a new run or an explicit new analysis-step execution from
  exact validated upstream publication references.
- Same-run process-crash recovery, queue/worker takeover, Provider-call
  recovery, and terminal repair remain deferred in
  `docs/supplements/runtime-recovery-todo.md`. Do not implement them without a
  new user-approved design work unit.
- An explicit execution ending before the nine-section document is `FINISHED`
  with `analysisResult=null` and no root run manifest. Only an execution
  through the nine-section document produces one of the four repository
  `AnalysisResult` values.
- Under the same frozen partition profile and budget, resource sharding and
  scheduling order cannot change canonical bytes. Changing shard size/budget
  is a control change and may change shard/task/downstream identities, but
  shard denominator ID sets remain disjoint, their canonical union equals the
  complete denominator, and the fixed upstream candidate/group sets do not
  change.
- The DepotHead eight-file `BOUNDED_PATH_SET` is a walkthrough/local fixture,
  never repository-completion eligible. Every discovered entry becomes one
  Flow or one evidence-backed Gap/EXCLUDED disposition.
- A run completes only when `RepositoryCoverageLedger` accounts for every
  file, site, entry, graph item, fact/atom, outcome, Flow, model proposal/
  disposition, knowledge item, and section owner. One Flow PASS never
  completes a repository analysis.

## Public seam and output rules

- The sole public target seam is one run-centric `RepositoryAnalysisAgent`
  with `start`, `executeStep`, `inspect`, `artifact`, `render`, `validate`,
  and `trace`. `executeStep` creates a new execution identity; it is not
  same-run resume.
- Java, CLI, and authenticated loopback HTTP are symmetric adapters. No public
  request/response accepts or exposes filesystem `Path`.
- Artifact lookup requires `runId + artifactId`; optional expected
  `ArtifactLocation` is the sealed `ANALYSIS_STEP_MODULE |
  VALIDATION_MODULE | ANALYSIS_STEP_PUBLICATION | RUN_MANIFEST` union.
  Type/digest values are expected-value checks, never locators.
- `artifact` returns full bytes only when registry policy is
  `PATH_FREE_COMPLETE_UTF8`. Raw source-bearing artifacts, raw Trace, prompts,
  and raw model responses are metadata-only. `trace` is the sole validated,
  path-free source-hop projection.
- Read-only observations never call a Provider, execute customer code, inject
  evidence, or replace an analysis-step publication/receipt.
- The final body has exactly the nine agreed H2 sections. IDs, hashes, prompts,
  and receipts remain sidecars, not normal business prose.
- Preserve exactly 47 semantic analysis-step payloads, eight semantic
  receipts, one `nine-section-archive-manifest.json`, and one root
  `run-manifest.json`: 57 reader-visible run outputs. The five added semantic
  payloads all belong to `flow-interpretation`; its key and Step 06 name do
  not change. Module artifacts/
  receipts and exterior validation publications are excluded from this count.
- Every Agent-produced machine artifact is canonical UTF-8 JSON or append-only
  JSONL. Exceptions are final `document.md`, durable design/progress Markdown,
  and immutable source inputs retained verbatim.

## Evidence and reader admission

- Fact admission requires semantic Evidence closure. Valid hashes and closed
  references prove integrity only; every claimed semantic atom must be
  supported by bytes inside Evidence spans the Fact references, plus a
  deterministic Proof or independent audit proof.
- Reader admission requires every admitted atom—kind, attributes, conditions,
  literal values, and relationships—to appear in reader content, attached
  technical basis, an explicit Gap, or a reasoned exclusion. Silent atom loss
  is fatal; word count and file size are not quality gates.
- RepositoryKnowledge has zero model calls, admits local meanings before
  process claims, preserves conflicts/alternatives/pending confirmations, and
  assigns every Flow to at least one BusinessProcess, independent activity, or
  explicit-Gap unassigned membership. Certainty is only `SOURCE_CONFIRMED`,
  `EVIDENCE_SUPPORTED_INFERENCE`, or `PENDING_CONFIRMATION`.
- Chapter 4 is process-first. Its process ReaderItems trace through Process
  Knowledge and admission, hypothesis, typed ProcessInterpretationDisposition,
  P1/P2 task/round/receipt, group/signal, Flow/Capsule, Fact/Proof/Evidence,
  and SourceExcerpt. A P2 NOT_RUN trace keeps its task/disposition and omits a
  fabricated P2 round/receipt/review. Body prose hides IDs, SHA, paths, and
  technical enums; unproved external effects remain pending.
- Standard MyBatis mapper `DOCTYPE` syntax is accepted only with external DTD,
  general/parameter entity, schema, and all network resolution disabled.
  Inability to enforce those settings fails closed.

## Documentation readability

- Overall and analysis-step designs are function-first: why it exists,
  concrete input, work, observable artifacts, downstream consumption,
  success/Gap/fatal and reuse, program/model roles, then records/identity/
  algorithm/budget/security/failure/tests/maturity.
- Every analysis-step design points to the one main flow and uses the fixed
  DepotHead path as the real bounded walkthrough. Cross-Flow acceptance also
  uses an explicitly synthetic replenishment-to-settlement story that must
  never be presented as jshERP behavior. Label real source, deterministic
  conclusions, model interpretation, unknowns, and illustrative JSON
  separately.
- Keep target design and current maturity in separate sections. Current audits
  use Chinese status labels and keep fixed jshERP at Gap / zero Flow / zero
  Capsule until a new directly verified run proves otherwise.
- Reader sections lead with Chinese terms and plain language. Technical
  reference sections retain exact future code, Interface, field, command, and
  artifact names.

## Per-agent progress files

- Before modifying code, tests, configuration, or durable documentation, every
  Agent creates one tracked `progress/<task-slug>.md` from
  `progress/TEMPLATE.md`. Each Agent owns only its file.
- Update current state in place before a long command, after every verifiable
  step/test, on a blocker, and at task end. Do not append a chronological log.
- Record scope, approvals, changed paths, checks, decisions, blockers, and the
  exact next action. Do not include secrets, full prompts, large source
  excerpts, or logs.
- Continuing work reads its progress, checks Git status, and verifies referenced
  artifacts/tests. Completed progress files remain tracked and unchanged.
