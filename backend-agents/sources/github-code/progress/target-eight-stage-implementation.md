# Progress: target eight-stage implementation

- Status: IN_PROGRESS
- Agent role: Root implementation coordinator；跨阶段设计只由 Sol/ultra Design Authority 裁决
- Model: Coordinator；design authority = gpt-5.6-sol / ultra
- Started: 2026-08-31
- Last updated: 2026-09-01
- Scope: Replace the POC with the approved Java/Spring MVC/MyBatis eight-stage Agent in `target/`; add local Git-commit capture; preserve only historical progress records.
- Approved inputs: User-approved eight-stage implementation plan; local fixed jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; no live Provider, source network, customer Maven, or deployment.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation`

## Authoritative continuation summary

- Sol/ultra 已完成总体设计、Stage01–08、标准工具链计划与恢复边界收口。核心范围仍是完整仓库、五张图、Fact/Proof/Gap、逐入口 Flow/Capsule、逐 Flow R0/R1/R2、唯一 RepositoryKnowledge、唯一九章文档、Trace 和 52 项正式输出。
- Stage06 数量关系固定为 `N/E/R`、`E+2R` planned tasks 和 `E/R/R` shard denominators；一个 Flow 成功不能关闭全仓分析。
- active v0 只有 `QUEUED/RUNNING/FINISHED/FAILED` 和单进程 worker。编码 Agent 用 Git + 自己的 progress 续接；新 `executeStage` 可校验并复用已安装的上游业务 artifacts；同一 run 自动崩溃恢复只在非合同 TODO 中保存，不得实现。
- 唯一 public seam 是 `start/executeStage/inspect/artifact/render/validate/trace`。旧 `CodeToMarkdownAgent`、旧 CLI、旧 Stage01–04/POC 生产路径与所有兼容桥必须删除。
- authoritative docs 已通过 whitespace、JSON/JSONL、link、52-output、POC/recovery residue 检查，当前动作是建立 docs-only main 基线。现有 target Java/测试/POM/.gitignore 与实现 Agent progress 不进入该提交。
- 下方较早的 Round-1～4 叙述只保留为审计历史；其中任何 terminal-event recovery、resumer、journal 或自动修复描述均已被本节和 `docs/supplements/runtime-recovery-todo.md` 明确取代，不能作为续接指令。

## Completed

- Existing design checkpoint `eefab1a` was fast-forward pushed to `origin/main` without staging the unrelated handoff edit in the original checkout.
- Isolated implementation worktree created from `eefab1a`.
- Dispatched the Sol/ultra cross-stage design-contract update and the Luna/xhigh
  RED test slice for canonical target contracts.  They own separate files and
  do not alter production code.
- Observed the required RED for `TargetContractsTest`: production compilation
  passed and test compilation failed only because the four planned target
  contract types do not exist.
- Terra/xhigh implemented the target-contracts GREEN: six direct tests pass.
- Narrowed the Maven-output ignore rules so the intentional Java package
  `src/**/com/linguan/codemd/target/` is tracked normally while build output
  `.../github-code/target/` remains ignored.
- Sol/ultra completed the cross-stage capture/lifecycle/store-contract pass:
  object-db-only local Git capture, rootless registration requests, binary
  accounting, durable lifecycle/result separation, and the exact canonical
  artifact-store seam are now fixed in the target design.
- Corrected one Luna RED-test transcription error before production began:
  `ArtifactDescriptor` has the design-required seven components, including
  `mediaType`; the corrected selector is still RED only because the intended
  production seam does not yet exist.
- Independent Sol/ultra review identified P0 cross-stage contract omissions:
  module publication is defined, but typed stage-publication/run-publication
  seams, root hash formulas, and one aligned run-request/run-manifest contract
  are not yet sufficiently fixed for Terra to implement without inventing
  architecture. The artifact-store GREEN has been paused before production
  edits; a formal options package is being prepared for user confirmation.
- The completed review records eight P0 items: executable publication-root and
  receipt formulas; artifact-policy availability; safe typed-address grammar;
  stage/run publication seams; Stage01 M3 ordering; one run-request schema;
  Stage08 run-manifest placement/order; and a durable queue/event wire
  protocol. All are persisted in `progress/target-design-contract-review.md`.
- User explicitly approved the recommended resolution package. A new Sol/ultra
  design-authority pass is now resolving it in the overall and affected stage
  documents before any Luna/Terra work resumes.
- Coordinator audit found that the same obsolete receipt/self-root pattern
  remains in the publisher sections of Stages 02–07, not only Stages 01/08.
  The active Sol/ultra pass has therefore been instructed to normalize all
  eight publisher contracts to the approved specification-module → stage-store
  publication order before implementation resumes.
- Independent final Sol/ultra review completed the planned document-only
  mechanical checks (76 JSON units parse; output counts and v2 request are
  consistent) but rejected publication on four P1 example-contract mismatches.
  The errors are documentation-only: an incomplete Stage03 receipt example,
  class names where compiled module keys are required, stale exact-record
  examples in multiple stages, and malformed Stage08 typed-reference examples.
  No target code has been allowed to proceed on those examples.
- The first correction pass closed those four P1 groups, but a second wholly
  independent Sol/ultra review found nine additional P1 precision defects in
  the exact-fixture layer: placeholder IDs, fictional stage-publication
  artifacts, omitted direct-preimage references, validator/resumer addressing,
  an undefined capability-site shape, one ambiguous profile reference, one
  illegal enum, mutable registry key wording, and error-code drift. It found
  no P0 and no change to the approved target architecture. The same design
  authority has been assigned one bounded alignment pass before another review.
- The first independent exact-fixture review after that alignment found no P0
  but rejected the documents with sixteen P1 groups. The design formulas and
  DAG hold, but the fixture layer still conflates narrative values with replay
  values and leaves several record/closure contracts incomplete. The active
  Sol/ultra correction is therefore consolidating one explicit example taxonomy
  and closing locator, model-input, disposition, reader-item, validation,
  resumer, and public-address seams before final review.
- User approved the Sol/ultra standards-and-toolchain plan. It freezes mature
  parser, formatter, quality, test, and supply-chain responsibilities without
  changing the approved eight-stage business architecture; its POM/download
  work remains sequenced after the authoritative docs-only publication gate.
- Read-only environment preflight found Maven `3.9.16` and an installed
  Homebrew OpenJDK `17.0.19` toolchain. The shell default is JDK 26, so the
  future Toolchains gate must select the existing JDK 17 rather than silently
  compiling against the shell default. No JDK installation is needed.
- The same read-only preflight found Node `25.9.0`, npm `11.12.1`, and local
  `xmllint`/libxml2 `2.9.13`; Taplo and markdownlint-cli2 are not installed.
  The standards plan will treat the latter two as caller-driven additions, not
  as a reason to create replacement linters.
- Round-3 closed the earlier sixteen P1 fixture defects and passed its full
  mechanical gate, but the fresh independent Sol/ultra final review found six
  further bounded P1 closure gaps: final coverage-ledger publication, R0 input
  bytes, durable model-slot recovery, model-ineligible Flow admission, public
  artifact location, and the trace response/adapter contract. It found no P0
  and no requested change to the approved eight-stage business architecture.

## Superseded historical state（仅审计，不得据此续接）

- The target-contracts GREEN is complete. The corrected artifact-store RED is
  preserved, but its Terra GREEN is paused at a documented cross-stage design
  boundary. The read-only Sol/ultra review is converting the P0 findings into
  an approved Sol/ultra contract correction across the complete stage set. No
  production contract is being improvised.
- The earlier `target/contracts` GREEN is retained as a verified historical
  vertical slice, but it is now explicitly superseded for production by the
  approved policy-selected framed identity and five-field `ArtifactControls`
  contract. It must receive a fresh Luna RED and Terra replacement before any
  store implementation depends on it.
- Sol/ultra reports the shared DESIGN and Stage01's acyclic M3 → stage-store
  flow are patched. Remaining design work is the v2/control cleanup, Stage06/08
  synchronization, narrow Stage02–07 publisher-spec conversions, and focused
  contradiction checks before a new RED is dispatched.
- Sol/ultra has now reconciled the main DESIGN, Stages01/06/08 and begun the
  same conversion for Stage02. Coordinator review also caught a Stage08
  `document.md` media-type contradiction; the authority is explicitly adding
  the one permitted `text/markdown`/`RAW_UTF8` stage-payload rule before the
  final all-stage scan.
- Independent Luna/xhigh readiness audit completed in
  `progress/target-contract-replacement-readiness-review.md`: it confirms the
  old parser, caller-selected identity prefix, four-field controls, raw IDs,
  and installer constructor are all superseded. It identifies the exact fresh
  RED surface (policy-selected identity, typed IDs, three stores, receipts and
  Stage08/run-manifest ordering) and the limited behavior intents that remain
  reusable.
- User added the design-publication gate. The scoped `AGENTS.md` now requires
  every behavior-changing implementation slice to update the relevant design,
  commit that coherent docs-only change, and fast-forward push it to
  `origin/main` before code/tests begin; a discovered design correction repeats
  this gate before the slice resumes.
- The independent reviewer found no P0 architectural defect, but did find four
  P1 documentation defects that would cause an implementing Agent to invent
  compatibility rules. The original Sol/ultra design authority is correcting
  them now. A fresh independent review is required before the docs-only commit
  and push.
- A second independent review also found no P0, but rejected publication on
  nine remaining P1 exact-fixture inconsistencies. They are being corrected
  against already approved records and do not authorize production work yet.
- A dedicated exact-fixture review confirmed the underlying formulas and DAG,
  but rejected on sixteen P1 groups. The next design pass treats them as one
  system rather than as isolated sample edits: strict replay values, structural
  examples, and human narrative examples will have explicit distinct roles.
- The user has approved the standards/toolchain plan and default in-scope
  execution authority. The active Sol/ultra authority is continuing the
  non-conflicting docs-only Round-3 repair; no POM, code, test, dependency,
  provider, source-capture, or customer-runtime change may precede the
  docs-only review/commit/push gate.
- The current primary Sol/ultra design authority has completed the bounded
  Stage05/06/07/08 contract synchronization and corrected the shared DESIGN
  and scoped instruction wording to the same `N/E/I/R` rule. The public
  artifact counts remain `4/5/8/5/6/10/6/8 = 52`; the final coverage ledger
  and model-slot journal remain internal module artifacts or authorized fields,
  never silent reader-visible files. The new terminal-binding handoff is a
  field of existing Stage06 disposition artifacts, not an eleventh output.
- The Design Authority has now made the evidence-complete requirement concrete:
  Stage05 Capsule v4 carries bounded exact Fact/Gap/Outcome views alongside
  spans and projection obligations, and Stage06 copies that full view into all
  R0/R1/R2 input bytes. Old, misleading long JSON envelopes were removed from
  the live Stage05/06 implementation guidance rather than treated as a second
  wire contract.
- Stage05→06→07 now uses one exact `N/E/I/R` partition: Stage06 creates
  `E+2R` **planned slots**, each with a durable terminal outcome; persisted
  rounds and real Provider calls are a subset. `3E` applies only when every
  eligible Flow is R0-ready and every planned slot starts. R0 READY requires
  a nonempty same-Flow, Capsule-basis-closed proposal set; otherwise the Flow
  is a Gap/FAILED technical fallback with zero R1/R2 slots. Ineligible Flows
  retain their Stage05 Capsule/Gap and receive one explicit Stage07 decision.
- The all-Flow completion chain is acyclic: Stage07 embeds its draft without a
  self/public-root reference; Stage08 M1 reopens Stage07's declared bytes but
  only structurally validates embedded Stage01–06 references, then freezes the
  final ledger before the plan. Duplicated ledger draft/reader/owner values are
  now exact equality invariants. A raw source-bearing artifact cannot bypass
  the public path-free Trace projection: `artifact(COMPLETE_UTF8)` is allowed
  only for `PATH_FREE_COMPLETE_UTF8` policy entries; `trace.jsonl` stays a
  persisted `METADATA_ONLY` raw artifact. The second independent review is
  active; target code remains paused until it is clean and this docs-only
  baseline is committed and fast-forward pushed to `origin/main`.
- The second independent review is now complete: no P0, two bounded P1
  lifecycle/direct-preimage omissions, and two P2 type/code-list omissions.
  The next docs-only correction makes an R2 dependency skip a first-class
  terminal outcome, names the one permitted Stage06 registry preimage for
  Stage08 M1, and closes the related stable-code and public-view typing gaps.
- Those four repairs are now applied to the target DESIGN and Stage06/08
  documents. The lifecycle keeps `E+2R` planned slots intact: a non-successful
  R1 creates its already-planned R2's durable, non-started
  `SKIPPED_UPSTREAM_TERMINAL` outcome with an exact same-Flow upstream outcome
  reference. No Provider call, retry, new stage, or business behavior was
  introduced. The next action is the final mechanical and focused read-only
  audit before the docs-only commit.
- Round-3 then found one remaining P1 handoff defect: Stage07 could see the
  Stage06 task denominator but not the terminal outcome references for every
  planned slot. The next bounded design repair carries a typed terminal-slot
  reference in the existing R0 and final Flow disposition records and requires
  Stage07 to fresh-reopen those already-persisted private journal outcomes.
  This preserves the ten public Stage06 files and the `E+2R` equation rather
  than inventing an eleventh output or trusting memory/receipts.
- That Round-3 repair is now applied: both Stage06 disposition schemas expose
  `TerminalSlotBindingV1`; its exact task/slot/outcome relation, R2
  dependency-skip lineage, duplicate rejection, module direct-preimage
  behavior, Stage07 admission replay, independent validation, and resume
  checks are explicit. Private journal outcomes remain content-addressed and
  private; the existing public dispositions are their sole enumerator.
- The active Round-4 Sol/ultra review found and the coordinator corrected two
  bounded resume-contract omissions without changing any stage or output
  count: the global Stage06 records now carry the same terminal bindings as
  Stage06, and a pre-publication outcome has a write-once exact-key install
  carrier. Recovery can reopen that carrier only from a committed reservation;
  it never scans a journal or infers a result from an orphaned file.
- The same pass made early resume exact: M1's final coverage ledger is a direct
  preimage only after it exists, and a retryable confirmed-no-start attempt
  performs no outcome-install lookup or block. The independent reviewer is
  rereading these latest corrections before publication.
- The user confirmed that Round-4 must complete the automatic terminal-event
  recovery design rather than retreat to a fail-closed-only simplification.
  The corrected worker path will cover a pre-terminal crash while lifecycle is
  `RUNNING`: a recovered active worker derives the exact install address from
  the committed reservation, verifies the carrier/outcome, idempotently writes
  only a missing matching terminal event, and blocks on any ambiguity. The
  final review must state both covered crash boundaries and residual explicit
  new-run boundaries before docs publication.

## Changed files

- `progress/target-eight-stage-implementation.md`
- `progress/target-contracts-red-tests.md` and
  `src/test/java/com/linguan/codemd/target/contracts/TargetContractsTest.java`
  are completed, separately owned Luna outputs.
- `src/main/java/com/linguan/codemd/target/contracts/{CanonicalJson,ModuleArtifact,ArtifactReference,SourceLocator}.java`
  and `progress/target-contracts-green.md` are completed, separately owned
  Terra outputs.
- `.gitignore` and `backend-agents/sources/github-code/.gitignore`
- `docs/DESIGN.md` and the scoped `AGENTS.md` are the active docs-only
  authority edits; Stage05/06/07/08 repair progress is separately owned so
  this coordinator does not overwrite parallel work.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git push origin HEAD:main` | PASS | `977deff..eefab1a`, unrelated handoff remained unstaged |
| `git worktree add ... codex/github-code-target-implementation` | PASS | isolated worktree at `/private/tmp/linguan-github-code-target-implementation` |
| `mvn -Dtest=TargetContractsTest test` | expected RED | 11 missing-symbol errors for the planned target contracts; production compile passed |
| `mvn -Dtest=TargetContractsTest test` | PASS | 6 tests, 0 failures/errors/skips after the Terra GREEN slice |
| read-only local-Git adapter survey | PASS | JGit is not cached; a tightly constrained Git plumbing adapter avoids an unapproved dependency download |
| `mvn --version` and `/usr/libexec/java_home -V` | PASS | Maven `3.9.16`; Homebrew OpenJDK `17.0.19` is installed and will be the required Toolchains target; no JDK install is needed. |
| `node --version`, `npm --version`, and tool presence probe | PASS | Node `25.9.0`, npm `11.12.1`, and xmllint/libxml2 `2.9.13` are present; Taplo and markdownlint-cli2 are absent and remain deferred standard-tool additions. |
| `git diff --check -- AGENTS.md DESIGN.md stages/05 stages/06` | PASS | Current evidence-complete Capsule/R0-input and public-location documentation edits have no whitespace errors. |
| `git diff --check -- docs/DESIGN.md` | PASS | The all-Flow final-ledger/DAG synchronization has no whitespace errors. |
| `git diff --check -- AGENTS.md DESIGN.md stages progress` | PASS | The synchronized N/E/I/R, coverage-draft, and progress edits have no whitespace errors. |
| JSON/fence + scoped terminology scan | PASS | Edited docs pass `jq` JSON fence validation and fence parity; no obsolete `artifact-policy-registry-v1`, `3N`, `2N`, old eligibility names, or slots/calls conflation remains. |
| independent Round-2 current-byte architecture review | P1/P2 repair required | P0=0; repair R2 dependency-skip terminal outcome, Stage08 M1 named registry preimage, ledger error-code lists, and public ArtifactView type spellings before final gate. |
| Round-2 P1/P2 document correction | PASS — pending final re-review | DESIGN + Stage06/08 now declare the dependency-skip terminal contract, named M1 Stage06 registry preimage, ledger code, and ArtifactView enum spelling; no code/test/POM/runtime invocation. |
| Round-3 interim design review | P1 repair required | Stage06 public semantic dispositions must expose typed terminal-slot outcome references so Stage07 can fresh-reopen and validate every `E+2R` planned slot without relying on private module memory or a receipt count. |
| Round-3 terminal-binding correction mechanics | PASS — pending independent re-review | `git diff --check`, fence parity, 27 JSON-fence / 66-record parse, and stale-terminology scan passed after Stage06/07/08/DESIGN alignment; no runtime or model/source action occurred. |
| Round-4 lifecycle/resume correction mechanics | PASS — pending independent re-review | Latest `git diff --check`, 27 JSON-fence / 66-record parse, 174-fence parity, and stale-term scan pass after exact outcome-install and early-resume corrections; no runtime or model/source action occurred. |
| Round-4 lifecycle scope review | APPROVED TO COMPLETE | User retained automatic terminal-event repair. The design must make the RUNNING recovered-worker path executable and report its remaining fail-closed boundaries. |
| `git diff --check` (current docs-only scope) | PASS | No whitespace errors after the latest ResumeDecision, M4/validator preimage, registry, Stage03/06, and RunInspection alignment edits. |

## Superseded historical decisions（仅审计；恢复相关裁决已失效）

- Local capture reads the exact local Git commit tree, not the working tree.
- All tracked regular files count toward source completeness; non-text files are hash-verified and explicitly excluded from parsing.
- Runtime uses one persisted asynchronous worker. CLI drives that worker in the foreground; HTTP returns an accepted run reference.
- Legacy POC code has no compatibility obligation and will be removed only after the target pipeline is independently green.
- Root ignores distinguish Maven build output from the source package named `target`.
- The design authority will freeze the capture implementation boundary after
  the survey: prefer exact Git plumbing with no network and no worktree reads
  unless the detailed design identifies an in-process reader as necessary.
- Ruling: the six-field `ArtifactDescriptor` expectation was a test defect,
  not a new store variant. The canonical seven-field design remains binding;
  the test was corrected before any production code was written.
- Ruling: all Stage 01–07 publisher modules must publish an acyclic semantic
  publication specification only; `CanonicalStageArtifactStore` alone creates
  the stage root and receipt. This is a necessary application of the user-
  approved cross-stage persistence package, not a new business or architecture
  choice. Cost if wrong: a later design revision, but no unsafe self-referential
  artifact contract is allowed into production.
- Ruling: the existing `ModuleArtifact` identity (schema plus LF concatenation)
  and four-field controls are insufficient after the approved policy registry
  contract. Preserve their test evidence, but do not build subsequent modules
  on them; a fresh test-first replacement is required once the Sol document
  pass is complete. Cost if wrong: limited rework in the new target package;
  benefit: no ambiguity in every later artifact identity.
- Ruling: public observation must not expose raw artifact bytes merely because
  they are durable. The policy registry is therefore `v2` with a per-type
  `publicContentExposure`; raw source-bearing Trace/source artifacts are
  metadata-only and `TraceView` is the sole path-free validated source-hop
  projection. This is a bounded safety closure within the approved public
  Interface, not a new analysis stage or business goal.
- Ruling: a planned R2 cannot disappear when its same-Flow R1 has a terminal
  non-success. It receives a non-started, dependency-linked terminal outcome;
  this completes the already-approved "every planned slot has a terminal
  outcome" rule without adding a Provider call, retry, new stage, or business
  behavior.
- Ruling: M1 retains the exact Stage06 registry as a declared direct
  preimage solely for ReaderItem registry-lineage joining. It does not thereby
  reopen source-bearing Stage01–05 or any other Stage06 byte; every other
  earlier-stage relation remains a typed reference checked by M1 and fully
  byte-reopened only by the external validator.
- Ruling: terminal model-slot outcomes are not a new Stage06 publication. The
  public R0/final Flow dispositions contain their typed content-addressed
  bindings; Stage07, validator, and resumer must enumerate from those bindings
  and reject a missing, duplicate, cross-Flow, task-mismatched, or dependency-
  skip-mismatched outcome rather than scanning private journal storage.
- Ruling: before a public Stage06 disposition exists, a terminal model outcome
  is recoverable only through the immutable `RoundSlotOutcomeInstallV1` at the
  exact address derived from its already-committed reservation. This is a
  private crash-boundary carrier, not a Stage06 output, mutable ledger, or
  path-based artifact API. A retryable confirmed-no-start attempt bypasses it.

## Blockers

- 无外部 blocker。Sol/ultra 设计和最终机械核验已完成；Terra 仍暂停，直到当前 docs-only baseline 提交并快进推送到 `origin/main`。

## Exact next action

- 只提交并推送 authoritative docs 与设计/协调 progress 到 `origin/main`；排除 Java、测试、POM、`.gitignore` 和实现 Agent progress。
- 从已发布 main 开始落实 JDK17/Maven 标准工具链；随后让 Luna/xhigh 针对当前 canonical contracts 写 fresh RED，Terra/xhigh 才做对应 GREEN。不得恢复旧 POC、旧兼容接口或已延期的同 run 自动恢复实现。

## Resume checks

- Read this file, run `git status --short`, verify branch and `origin/main` relationship, then run only the named targeted Maven selector for the active module.
- 以 `Authoritative continuation summary` 和新的 `Exact next action` 为唯一续接入口；较早 Round-1～4 内容只是历史，不得覆盖当前设计。
