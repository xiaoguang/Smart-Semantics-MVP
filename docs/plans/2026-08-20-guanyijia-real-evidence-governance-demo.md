# 管伊佳真实多源证据治理 Demo 实施计划

> **For agentic workers:** Use `superpowers:subagent-driven-development`; complete one task and its review before starting the next.

**Goal:** 用少量真实、可复核的已有冻结证据替换模板化展示，演示互证、结构冲突、时间漂移、GAP、人工决定和候选治理结果；同时交付完整版 2–4 天治理架构的设计文档。

**Authority:** `docs/architecture/multi-source-evidence-governance.md` is the durable reference architecture. This plan implements its current Demo profile only. Formal Guanyijia V1 and retail V1/V2 remain unchanged.

## Global constraints

- Never connect to MySQL, GitHub, official sites, OCR, LLM APIs, or scanners during normal runtime, test, build, recovery, or browser demo.
- Use the existing local MySQL snapshot `20260813T032528Z-abb0502c7d79` and GitHub archive `20260813032126Z-5821d0ece9b1` only. No Tenant rows, samples, profiles, results, examples, or bound parameters enter the public candidate.
- MySQL raw bytes are `OBSERVED`; GitHub stored excerpts are `FROZEN_RECORD`, not full source; generated knowledge is `GENERATED_TARGET`; Semantica is `DERIVED`; missing official text is `GAP`.
- The candidate is not a formal V2: no private publication receipt/pin, no formal V1 mutation, and no silent gap filling.
- Generated knowledge is created once by a Luna xhigh Codex-session task from already admitted evidence. It must retain citations and be rejected or labelled GAP if citations do not validate.
- All visible UI actions navigate, expand, or issue a real command. Evidence and Markdown are primary; technical IDs/SHA/ref/enums remain disclosure-only.
- Multi-tab access remains allowed; concurrent-write correctness is out of scope.

## Task 1: Candidate evidence packet and deterministic relations

Create a deep read-only `CandidateEvidenceBundle` module. It returns exactly three topics with public excerpts, byte/line locators, evidence class, claims and relations:

1. Negative-stock configuration: MySQL `jsh_system_config.minus_stock_flag` and GitHub configuration-record excerpt corroborate tenant configuration.
2. Debt fields: deployed MySQL `jsh_depot_head` lacks `debt/last_debt`; GitHub migration/workflow record contains them, producing `CONFLICTS`.
3. Document status: deployed DDL records `0/1/2/3/9`; historical GitHub record describes `0/1/2`, producing `TEMPORAL_DRIFT` and a status-9 GAP.

The public interface owns `EvidenceClass`, `EvidenceFragment`, `NormalizedClaim`, `EvidenceRelation`, `CandidateEvidenceBundle`, `readCandidateEvidenceBundle()` and `compareClaims()`. Its implementation must validate all observed excerpts against fixed local raw bytes before returning, refuse sample/profile paths, and never fabricate an excerpt. The persisted public artifact contains only redacted excerpts and no private paths or business rows.

Write direct tests first for exact locator/byte verification, claim classifications, relation rules, public-redaction exclusion, and corruption failure. Then add the generated candidate data asset built from the existing local snapshot. Do not edit the formal V1 pinned bundle.

## Task 2: Generated target knowledge proposal

From the selected Task 1 evidence only, use one Luna xhigh ChatGPT/Codex-session analysis task to produce:

- a concise target-policy/knowledge Markdown document;
- structured target claims for negative stock and status 9;
- a manifest containing model, reasoning level, input digest, output digest and each cited `evidenceRef`.

No assertion may masquerade as an observed policy. A deterministic validator must reject unknown citations, an invalid evidence class, or any target claim missing `PENDING_HUMAN_CONFIRMATION`. Valid generated-target disagreement becomes a reviewable candidate; it does not change current facts. Store the frozen output alongside the public candidate asset, never invoke Codex at runtime.

## Task 3: Candidate review story and content-first projection

Wire the candidate packet into the existing Guanyijia five-source scenario without changing formal V1 inputs or resolution semantics:

- default source documents open on Markdown;
- the right-side evidence pane shows readable excerpt, location, evidence class and supported claim;
- Markdown paragraph, identification object and evidence selection use the same candidate target so they stay linked;
- present `CORROBORATES`, `CONFLICTS`, `TEMPORAL_DRIFT` and GAP as readable labels;
- use existing `KEEP_CURRENT`, `MERGE`, and `DEFER_AS_GAP` commands for the three decisions, preserving decision/audit/reload behavior;
- remove any template evidence shown for these three topics and move implementation metadata into technical details.

Write direct/workbench tests first and extend only the document-reentry/story E2E coverage needed to prove visible excerpts, navigation, decision and reload. Verify 1440, 1024 and 390 only.

## Task 4: Reference architecture and operational guidance

Create `docs/architecture/multi-source-evidence-governance.md` as the primary output. It must describe the complete 2–4-day target architecture from source capture through candidate/freeze/frontend projection, with input, processing, output, validation, failure and recovery for every stage.

Include the exact trust model, normalisation contract, deterministic comparison algorithm, LLM proposal boundary, decision/revision/CAS audit chain, replay model, and a worked Guanyijia walkthrough for the three topics.

Include an explicit implementation-tier table:

- **Full factory (2–4 days):** private raw repository/LFS, full Git/official source material, expanded DDL/SP/DML and authorized samples, multi-pass Luna validation, formal receipt/pin and V2 approval.
- **First reduced factory (10–16 hours):** all existing frozen assets admitted, broad claim coverage, controlled proposal run, complete candidate V2 bundle and focused E2E.
- **Current real Demo (this delivery):** three topics, selected observed/frozen records, one generated target proposal, no official raw documents, candidate-only freeze.

For each tier state the exact remaining GAP and the monotonic upgrade steps. Update `AGENTS.md`, snapshot maintenance guidance and demo guide so normal development never recaptures or regenerates sources; only an explicit maintenance workflow may do so.

## Verification

Run only direct checks, serially:

```bash
npm run test:evidence-factory
node --test scripts/evidence/guanyijia-admit.test.mjs
npm run test:source-documents
npm run test:standardization-run
npm run test:data-standardization
npm run test:demo-session
npx tsc -b --pretty false
npm run build
npm run test:e2e:cp8:story
node scripts/run-cp8-playwright.mjs tests/e2e/guanyijia-document-reentry.spec.ts
git diff --check
```

Run only tests directly changed by a task before the final command list. Do not deploy, publish, capture new sources, call a metered API, or alter formal V1 without a separate explicit request.
