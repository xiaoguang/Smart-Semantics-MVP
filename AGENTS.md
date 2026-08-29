# Linguan Prototype Instructions

## Scope and preserved baselines

- Work only in `linguan-prototype-v2` unless the user explicitly authorizes an
  external evidence capture or deployment.
- Preserve the formal Guanyijia V1 catalog, artifact, Markdown SHA, semantic
  SHA, fingerprint, and object counts. Evidence analysis may create a
  reviewable change candidate; it must never overwrite or publish V1.
- Preserve the retail V1/V2 flows. Guanyijia's data-standardization workbench is a
  separate path.

## Fixed source-snapshot rule

- Routine code changes, browser demo runs, recovery, tests, builds, hot
  reloads, and UI work must consume only the committed frozen demo evidence
  Bundle. They must not connect to MySQL, GitHub, official document sites,
  OCR, LLMs, or a source scanner.
- External capture is allowed only through the explicitly authorized
  `npm run evidence:guanyijia:capture` maintenance command (the current
  command is a secure local preflight, not an implicit source reader). The
  current candidate target is a committed, once-authorized Luna/ChatGPT-session
  asset; this repository does not currently expose executable
  `evidence:guanyijia:generate` or `evidence:guanyijia:freeze` commands.
  When the full factory adds them, they must be separately authorized,
  immutable-version-producing maintenance workflows that preserve prior
  versions for comparison and rollback.
- The current candidate Demo uses zero business rows: it may admit frozen DDL,
  stored procedures, static DML summaries and approved zero-row logic only.
  It must not read, retain or infer conclusions from Tenant 153 rows, samples,
  profiles, query results or bound parameters. A future, separately authorized
  full evidence factory may place redacted business samples only in a private
  evidence repository (using LFS for large binaries); they never enter this
  prototype repository, public Bundle or demo server.
- A missing or corrupt frozen Bundle must fail closed with an evidence-snapshot
  error. Never silently rescan a source or synthesize an excerpt.

## Demo content sidecar

- `../modeling-evidence/guanyijia/demo-content/snapshots/` owns a separate,
  immutable source-material sidecar for rich Demo reading material. It is not
  a formal Evidence Bundle and must not create or modify formal Claim,
  Assertion, Conflict, model, V1, Catalog, or golden-hash state.
- Its GitHub source tree is captured only at the approved fixed commit. Its
  official and ERP Markdown are explicitly `DEMO_AUTHORED`; its terminology
  graph is `DERIVED_DEMO`. Do not label those documents as official original
  text, current production policy, or independent facts.
- Only the explicit
  `npm run evidence:guanyijia:content:candidates:generate -- <sourceId>`
  maintenance command may invoke the logged-in Codex session. It handles one
  explicit source per invocation; it never retries automatically or reruns all
  five sources for one source. A source may produce at most two product-content
  candidates: Round 1 and one explicitly authorized Round 2 replacement.
  Infrastructure or integrity failures are fatal: stop and preserve the
  receipt rather than retrying. A V7
  candidate remains separate from a frozen sidecar until the maintainer creates
  a source-keyed selection with
  `npm run evidence:guanyijia:content:candidates:selection -- <selection.json>
  <sourceId=candidateId>…`, validates it with
  `npm run evidence:guanyijia:content:candidates:check -- <selection.json>`, and explicitly
  freezes it with `npm run evidence:guanyijia:content:freeze -- --selection <selection.json>`.
  `npm run evidence:guanyijia:content:check` is read-only.
  `npm run evidence:guanyijia:content:package` is a separate, explicit local
  publication step: it validates a fixed sidecar and writes the browser-safe,
  committed projection; it must not fetch Git, connect to a source, or call a
  model. `npm run evidence:guanyijia:content:package:check` only revalidates
  the fixed sidecar and generated projection. Normal development, tests,
  builds, recovery and Demo paths must invoke none of these commands, fetch
  Git, or call a model.
- A frozen sidecar version is append-only: create a new snapshot ID for every
  refresh and preserve old directories for comparison and rollback.
- The active, final sidecar for this work unit is
  `guanyijia-demo-content-v6-20260826`. The generic `content:check`,
  `content:package`, and `content:package:check` commands validate or publish
  V6 only; they must never imply that an unfrozen V7 candidate is active.
  V6 publication uses a structural no-shrink gate: it compares source/formal
  identities, all five nine-section documents, claim/evidence/trace mappings,
  the merged document, and the terminology graph. It deliberately does not
  use word count, Markdown line count, or file size as a quality or release
  criterion.
- `DemoContentReview` is the only seam from this sidecar to a standardization
  run. It binds an exact `runId + formal source identities` to an exact
  content snapshot SHA and must fail closed locally when either identity or
  content digest drifts. It must never fall back to a legacy template body.

## Evidence truthfulness

- A displayed evidence excerpt must be traceable to a stored raw artifact and
  an exact file line range, document section/page, SQL object, or source-code
  symbol. Do not render template text as if it were DDL, SQL, code, or an
  official-document quotation.
- Generated enterprise knowledge and target policies are proposals. Mark them
  `GENERATED_TARGET` and pending human confirmation; they are not observed
  production policy. Semantica is derived and never increases root-evidence
  count.
- Classify incomplete archived material honestly as `FROZEN_RECORD` and missing
  material as `GAP`; do not display either as source-code or official-document
  original text. The complete governance contract and its staged upgrade path
  live in `docs/architecture/multi-source-evidence-governance.md`.
- Every frozen identification has an assertion, Markdown anchor, and at least
  one real evidence fragment, except a clearly labeled generated-target gap.
- `AVAILABLE` evidence is not `ADMITTED` to a run. A source document remains
  source-pure; only exact `sourceId + snapshotId` values in an admitted
  `DOCUMENT_READY`/reviewed state may contribute to a comparison. Target
  policies, official gaps, and derived Semantica material become visible only
  after their own source is admitted.

## Model responsibilities

- Research, architecture, and option analysis: `gpt-5.6-sol` with `ultra`
  reasoning.
- Debugging: `gpt-5.6-sol` with `xhigh` reasoning.
- Production implementation: `gpt-5.6-terra` with `xhigh` reasoning.
- TDD test writing and OCR: `gpt-5.6-luna` with `xhigh` reasoning.
- Five-source bulk analysis and Markdown/knowledge-base proposal generation:
  `gpt-5.6-luna` with `xhigh` reasoning.
- A fatal V7 reader-candidate finding may be analyzed by `gpt-5.6-sol` with
  `ultra` reasoning. Its output is a bounded prompt-revision artifact, not
  product content; it may not weaken the frozen fact boundary, source identity,
  schema, traceability, or deterministic validators.
- The independently scoped `guanyijia-demo-content-v1-20260821` content
  sidecar is the deliberate exception: its one approved authoring pass uses
  `gpt-5.6-luna` with `medium` reasoning, as recorded in its generation
  manifest. It does not produce formal evidence or a model proposal.
- Any Codex analysis uses the logged-in ChatGPT/Codex session. Do not use an
  OpenAI API key or a metered API fallback. Verify `codex login status` before
  a maintenance generation run and fail instead of silently changing model or
  authentication mode.

## Maintenance permission preflight

- At the **start of a plan**, identify every non-routine capability it will
  require: a Codex subprocess/session-state write, network source access,
  remote deployment, or writes outside the workspace. Obtain the exact
  necessary approval before sending any source material to a model or starting
  parallel generation.
- A maintenance run that invokes a `codex` subprocess must preflight both the
  logged-in ChatGPT session and the subprocess's ability to initialize its
  local state. This includes any required write to the Codex state directory
  (for example `~/.codex/state_*.sqlite`); a successful `codex login status`
  alone is not sufficient proof that a sandboxed subprocess can start.
- If that preflight is denied or fails, stop **before** model invocation,
  preserve the diagnostic receipt, name the exact blocked capability/path, and
  request the narrowly scoped permission required to proceed. Do not label it
  as a model, quota, source-content, or schema failure; do not automatically
  retry after the permission failure.
- Once permission is granted, use only the already-approved maintenance
  command, keep the ChatGPT-session/no-API-key constraint, and record the
  permission preflight result in the generation receipt.

## Codex context safety and delegation

- Treat long Codex conversations as an operational resource, not as the
  durable project record. At a phase boundary, write a concise tracked
  handoff containing the approved contract, Git state, completed work,
  remaining work, and verification status, then continue in a fresh task.
- Never delegate with full conversation history from a large task. Sub-agents
  must start with no inherited history and receive only a bounded brief plus
  repository file paths. Do not repeat screenshots, raw logs, generated
  artifacts, or large command output in their prompts.
- On a 16 GB development host, use at most two sub-agents concurrently, and
  only for independent work that does not share core implementation files.
  Integration, builds, Playwright, and other memory-heavy verification remain
  serial.
- Use bounded command output and narrow tests. Reference large evidence and
  screenshots by local path instead of embedding or copying their contents.
- Use conservative rollover guardrails: prepare a handoff when a task rollout
  approaches 500 MB, and move work to a fresh task before it reaches 1 GB or
  before starting another major phase. Automatic model-context compaction is
  not a substitute for task rollover.
- If the Codex core process shows sustained memory growth or exceeds roughly
  half of host RAM, stop new delegation and heavy commands, preserve the
  handoff, and restart in a fresh task. Do not delete session history or state
  databases as an ad-hoc fix.

## UI and test rules

- Readable evidence and Markdown are primary. SHA, internal IDs, refs, actor
  IDs, and internal enums belong only in an explicit technical-details
  disclosure.
- Each visible action must issue a command, navigate to a target, or expand a
  panel. Do not ship controls that only repeat current state or show a toast.
- In the Guanyijia Demo, `SourceCenter mode="RUN_CONFIGURATION"` is the
  pre-run source board: its roles are fixed, its default tab is business
  instances, and its connector-family tab only creates an empty technical
  adapter instance. Only compatible `CONNECTED` instances may bind. A bound
  instance missing a frozen snapshot must block `开始资料整理` explicitly; it
  must never trigger scanning or manufacture a snapshot. Choices lock when
  the run starts. The board must not expose scanning, snapshot freezing, YAML
  import/export, or automatic eligibility. The generic `SNAPSHOT_OVERVIEW`
  remains read-only.
- The pre-run board selection is the small local key
  `linguan:guanyijia-source-board:v1`. It contains role-to-instance IDs only;
  Demo recovery must clear it so the default frozen instances return.
- The standardization flow may finalize a document that contains explicitly
  registered gaps or uncertain conclusions with the explicit author action
  `确认结果并定版`. Those items remain in the Markdown but are structurally
  excluded from AI-modeling candidates; they are not a failed run. It does not
  request an approval opinion or an independent-review action. Historical review
  records remain readable, but new Demo UI must not reintroduce those actions.
- Multi-tab access is allowed. Concurrent write correctness and live
  synchronization are explicitly deferred; keep existing revision/CAS guards
  but do not add a single-tab block.
- The no-live audit is intentionally narrow: re-review it only when a relevant
  maintenance entry point, capability import, package lifecycle path, or
  generation/capture interface changes. It is not a general proof about every
  JavaScript expression in the repository.
- Use TDD: a behavior test must fail for the intended missing behavior before
  the production implementation is written. Run only directly relevant tests
  unless the user asks for broader verification.

## Design review and generative-content planning gates

- Every implementation plan must explicitly inventory whether it invokes an
  LLM or other generative model to produce product content, evidence
  summaries, UI copy, documents, images, or other drift-prone artifacts.
  State `none` explicitly when no such generation is planned.
- Any new or changed user-facing design must be presented to the user before
  implementation. Prefer a Markdown wireframe plus terminology, state and
  interaction tables. Use a rendered page or visual prototype only when
  Markdown cannot express the design adequately.
- After approval, the exact accepted design must be written into the relevant
  current design document before production implementation begins. Code,
  tests and current-fact documentation must conform to that document. If
  implementation requires a design change, stop, update the design artifact
  and obtain review again before continuing.
- Every plan task that invokes a generative model must declare: (1) exact
  frozen inputs and output artifact; (2) ideal acceptance requirements; (3)
  task-specific fatal errors that must never pass; (4) deterministic
  validation or human review used after generation; (5) improvement round 1;
  (6) improvement round 2; (7) the decision rule after round 2; and (8)
  estimated execution time.
- A product-content generation unit consists of at most two candidates. Round
  1 is the initial candidate; Round 2 is the only allowed replacement. Never
  retry automatically or rerun unrelated sources or artifacts because one item
  needs improvement. A failed preflight before `thread.started` does not use a
  content round, but must be explicitly restarted after the capability is
  repaired; a started model session consumes its round even if it returns empty
  or invalid output.
- Before Round 2, a single `gpt-5.6-sol`/`ultra` prompt-diagnosis task may
  create a receipt-bound corrective addendum from the frozen input, the Round 1
  candidate, and named findings. It is not a product-content candidate, but it
  must itself declare ideal acceptance, fatal errors, deterministic validation,
  and human review. It may only add narrowly scoped corrective instructions;
  it must not introduce facts or weaken immutable source, schema, evidence, or
  safety constraints.
- Round 1 prioritizes factual correctness, source identity, required coverage,
  schema and structural validity. Round 2 fixes only named fatal findings or a
  user-approved systemic readability finding without broadening the approved
  scope. The source authoring model is `gpt-5.6-luna`/`xhigh`; prompt diagnosis
  is `gpt-5.6-sol`/`ultra`.
- A candidate is `IDEAL` only when all ideal-quality requirements pass. It is
  `REVIEWABLE_WITH_WARNINGS` only when all fatal-error checks pass and every
  remaining warning is recorded for human approval. Any fatal error is
  `FATAL`: after Round 1 it requires a valid prompt diagnosis before Round 2;
  after Round 2 it stops the candidate, selection, freeze, package, and
  deployment workflow. Do not create a third candidate. Preserve the output
  and receipts, then discuss the next approach with the user.
