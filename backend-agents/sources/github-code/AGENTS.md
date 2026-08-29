# GitHub Code Agent Instructions

## Scope

- This directory owns only the Java/Maven code-to-nine-section Agent and its
  tests, fixtures, design, CLI, Java Interface, and any future local loopback
  HTTP adapter. Ownership does not imply that every listed surface is already
  implemented; `DESIGN.md` and the current stage design own maturity facts.
- Do not modify V6, Pinned assets, formal models, formal evidence, existing
  source candidates, frontend code, or another source Agent's implementation.
- The v0 profile supports frozen Java/Spring MVC/MyBatis source only. JPA,
  message brokers, schedulers, batch, reflection, AOP, SpEL, WebFlux and
  unsupported constructs must become explicit gaps.

## MVP capture exception

- The user explicitly approved one read-only MVP capture of
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
- Production implementation uses `gpt-5.6-terra` with `xhigh` reasoning.
- TDD test writing uses `gpt-5.6-luna` with `xhigh` reasoning. LLM code review,
  bounded source reading, and nine-section Markdown proposal generation also
  use `gpt-5.6-luna` with `xhigh` reasoning. Debugging uses `gpt-5.6-sol` with
  `xhigh` reasoning.
- Automated tests use only scripted or recorded providers. They never invoke a
  live model, network source, API key, or customer build.
- A live Luna task requires the logged-in Codex session preflight and a frozen
  task package. Its `FlowInterpretationRound` may use one R1 interpretation and
  one R2 precision review inside the same reader candidate; R2 is not a product
  candidate replacement and does not consume another `ReaderCandidateRound`.
  Never retry, switch provider, or fall back to an API key automatically.
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

## Design and recovery documents

- Maintain one current detailed design under `docs/stages/` for every stage.
  When a work unit changes an invariant or architecture used by more than one
  stage, update `DESIGN.md` in that same work unit.
- `DESIGN.md` owns stable architecture and cross-stage invariants. A stage
  document owns that stage's current design, implemented result, tests, gaps,
  and exit conditions. README files are navigation and capability indexes.
- Progress files record only resumable work state: scope, completed checks,
  changed paths, verification, blockers, and next action. They never replace or
  override overall or stage design.

## Documentation readability

- Every future stage design must point to its position on the single main flow
  in DESIGN.md. For each stage-owned module, state its input, deterministic
  process, output, and failure behavior.
- Include one real, bounded example with an explicit evidence boundary and an
  honest success, Gap, or rejection result. Never use a historical artifact as
  proof of current or target behavior.
- State explicitly how the stage's evidence confirms or changes the target
  design. A stage document is an implementation record and design-feedback
  surface, not a competing overall architecture.
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
- A resuming agent reads the progress file, checks `git status`, and verifies
  referenced artifacts and tests before continuing. Finished files are marked
  `COMPLETE` and retained.

## Runtime and output

- Use Java 17. Keep the MVP as one Maven module with package-private deep
  modules; do not introduce a premature multi-module reactor.
- MyBatis is source input only: use Java/XML parsing and never add or execute
  the MyBatis runtime. JPA is not in v0.
- The body always has exactly the nine agreed H2 sections. IDs, hashes, prompts
  and receipts belong in sidecars, never in normal business prose.
- `inspect`, `discover`, and directory-level `analyze` outputs are unfrozen
  diagnostics. Until their exact files, locators, evidence digests, facts, and
  scope are admitted into a verified Manifest/Evidence Pack, none may enter a
  Candidate, reader Markdown, or formal Trace.
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
