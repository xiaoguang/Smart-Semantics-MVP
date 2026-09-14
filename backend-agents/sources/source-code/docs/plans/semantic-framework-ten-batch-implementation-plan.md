# Semantic Framework Ten-Batch Implementation Plan

> **SUPERSEDED / 不可继续执行（2026-09-10，2026-09-14再次确认）：** 本计划已被 [业务优先总体设计](../DESIGN.md) 取代。旧六模块 Step 06、三模块 Step 07、四模块 Step 08、固定 52 项、逐模块 fresh-reopen、bridge/reconciliation ledger 和三 adapter 同时完成都不再是目标核心路径。下一份实施计划须从[业务过程发现与重建设计变更清单](business-process-discovery-and-reconstruction-change-design.md)生成；不得继续本文件，也不得恢复后来同样被取代的 singleton `ProcessExplainer` 路线。
>
> 下文仅保留为历史设计与估算依据。任何时间/批次描述都不是新的工期承诺；实际工期必须在一个真实核心材料包、第二领域材料包和整仓样本依次测量后再估算。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan batch by batch. Each implementation behavior starts with one direct Luna/xhigh RED and proceeds to the smallest Terra/xhigh GREEN.

**Goal:** Deliver one complete frozen-Java-repository analysis that turns reliable source material into reviewed business semantics, one repository knowledge model, and one traceable nine-section Markdown candidate.

**Architecture:** Java owns frozen input, technical evidence, bounded material/packet construction, validation, identity, accounting, admission, and publication. Luna/high receives only DRY bounded packets and performs local and cross-flow DRAFT+REVIEW; human authorization remains required for live calls and material business confirmation.

**Tech Stack:** Java 17, Maven, canonical UTF-8 JSON/JSONL stores, scripted Provider tests, separately authorized Codex Subscription Luna/high execution.

**Spec:** `docs/DESIGN.md` and `docs/analysis-steps/01-verified-source-inventory.md` through `08-nine-section-document.md`.

## Global constraints

- Target v0 reads only explicitly named immutable Java/Spring MVC/MyBatis source. It never runs customer Maven, plugins, tests, scripts, class loading, or application code; source capture or refresh requires separate current authorization.
- Automated tests use frozen fixtures and a deterministic scripted Provider only. A live Luna/high run requires current explicit authorization, logged-in Codex-session preflight, exact frozen input, and exact task budget; there is no API-key fallback, automatic retry, provider switch, or same-run resume.
- Model-visible input is bounded, DRY business-reading material with packet-local handles and necessary short repository-relative excerpts. Hashes, receipts, full Fact/Proof/Evidence chains, reverse bindings, artifact/run/publication IDs, credentials, controls, archival metadata, and host paths remain program-only.
- A model may propose interpretations and hypotheses, not Facts, Proofs, source locations, human confirmations, external effects, or final Markdown. Java admits only complete reviewed records and preserves pending, dropped, conflicting, omitted, failed, and Gap outcomes without fabrication.
- Internal DRAFT/REVIEW remains within one immutable reader candidate. The shared maximum of two `ReaderCandidateRound`s, Round-2 named-findings authorization, source identity, candidate immutability, selection/freeze/package/activation separation, and post-Round-2 fatal rule remain unchanged.
- Every worker owns only its `progress/<task-slug>.md`, updates current state in place, preserves unrelated dirty work and historical progress, and runs only tests added by or directly covering its batch.
- Wire changes are explicit cutovers: no legacy alias, compatibility reader, dual writer, fallback discovery, or silent reinterpretation of old R0/finite-key artifacts.

## Exact final run inventory

The approved reader-visible run contains exactly:

~~~text
42 semantic analysis-step payloads
+ 8 analysis-step semantic receipts
+ 1 nine-section-archive-manifest.json
+ 1 runs/<runId>/run-manifest.json
= 52 outputs
~~~

Module payloads/receipts, model-safe packets, prompts, raw responses, generation receipts, and exterior validation publications are persisted under their existing access policies but are not part of these 52 outputs. The prior 57-output wire is migration input or history only; no batch may preserve 57 as a target acceptance count.

## Batch 1 — 真实运行基础

**Scope:** Save a rollback baseline; synchronize instructions/design navigation; implement production store open plus cross-process reopen; invoke the existing Steps 01–05 through the final run-core prefix; verify fixed-source availability without fetching or mutating it.

**Exit criteria:** The rollback point and dirty baseline are recorded; scoped instructions and this plan name the approved semantic route and 52 outputs; the production store opens and a separately created process can reopen exact installed bytes/receipts; the run-core prefix fresh-reopens each completed Step 01–05 publication and honestly stops on Gap/fatal; fixed-source availability is recorded from read-only checks. This closeout covers only instruction synchronization and store open/reopen work: run-prefix integration is not complete and Batch 1 remains `IN_PROGRESS` until every criterion above is evidenced.

## Batch 2 — 编译业务阅读材料

**Scope:** Implement Step 06 M1 `semantic-material` and M2 `semantic-packet`: prefer Flow/Capsule, retain safe entry-source fallback for noFlow entries, and project minimal DRY model JSON with program-only reverse bindings.

**Exit criteria:** Every entry has one material disposition; safe noFlow material preserves its technical Gap and exact frozen-source locator; every model handle has one reverse binding; model JSON excludes hashes, full proof chains, secrets, host paths, and duplicated material; oversize/review-capacity cases produce zero calls plus typed accounting.

## Batch 3 — 完整局部业务解释

**Scope:** Implement Step 06 M3 with scripted local DRAFT/REVIEW for rich purpose, roles, objects, inputs, conditions, activities, results, rules, fields/dimensions, metrics, example questions, terms, mappings, and unresolved questions; REVIEW operates on complete actual records.

**Exit criteria:** Each model-safe scope runs `k` bounded DRAFT tasks plus one REVIEW; KEEP/NARROW/DROP/PENDING covers every complete record; NARROW/PENDING carries a complete replacement/retained record; illegal basis/key/evidence-class expansion fails closed; requests, responses, observed identity, receipts, reviews, dispositions, and counts are persisted without live calls in automated tests.

## Batch 4 — 接入 Luna、验证语义

**Scope:** Add the Codex Subscription adapter and, under separate current authorization, run small frozen Luna/high samples whose domains and expected limitations can be reviewed manually. Automated tests continue to use fixtures/scripted Provider only.

**Exit criteria:** Configured adapter/auth and expected runtime are distinct from observed provider/model/reasoning/sandbox and match exactly; started-call failure has no retry/switch/API fallback; authorized sample review checks meaning, false actor/effect, omissions, and confirmation burden. Without current authorization the live sample is `NOT RUN`, the batch stays pending, and no alternate live mechanism is used.

## Batch 5 — 重建跨流程业务过程

**Scope:** Implement Step 06 M4/M5: high-recall process contexts followed by bounded Luna process/bridge/reconciliation DRAFT+REVIEW over reviewed local semantics.

**Exit criteria:** Candidate cues never become proof; every fragment, candidate relation, bridge need, omission, process, mapping, and reviewable record has one owner/disposition; processes support branches, alternatives, loops, Flow/noFlow and many-to-many membership; unproved order, identity, actors, and effects remain reviewed hypotheses or confirmation questions.

## Batch 6 — 收口 Step 06

**Scope:** Implement M6 sequential publish/reopen, install the nine Step 06 semantic payloads plus `flow-interpretation-receipt.json`, and remove the old semantic R0/finite-key route from the compiled target wire.

**Exit criteria:** M1–M6 publications fresh-reopen in order; Step 06 installs exactly nine semantic payloads and its receipt last; technical coverage and semantic completeness remain separate; every actual DRAFT/REVIEW/disposition is accounted; old 14-payload/R0/finite-key inputs fail closed with no alias or dual reader.

## Batch 7 — 唯一仓库知识

**Scope:** Implement RepositoryKnowledge admission, merge, optional version-bound human confirmation, conflict preservation, accounting, and five-payload publication.

**Exit criteria:** Exactly one repository knowledge object is published; every complete reviewed record has one admission and every nested target inherits it; reviewed mappings alone affect alias/object/process membership; noFlow refs remain valid; pending/conflicting/excluded content and stale confirmations remain visible; Step 07 makes zero Provider requests and installs five payloads plus receipt.

## Batch 8 — 九章、Trace、archive

**Scope:** Implement deterministic nine-section planning/rendering, typed Trace, immutable Candidate/validation baseline, Step 08 publication, and archive sequencing.

**Exit criteria:** The nine H2 sections occur exactly once in fixed order; Markdown contains no internal IDs, hashes, paths, enums, prompts, or provider metadata; every reader semantic ref traces through admission and actual DRAFT/REVIEW to Proof-backed or precisely located frozen material without invented hops; Step 08 installs five payloads, archive manifest, then receipt, with zero Provider requests.

## Batch 9 — common runtime

**Scope:** Complete the common runtime: public Java `RepositoryAnalysisAgent`, symmetric CLI and authenticated loopback HTTP adapters, root manifest, validator, and OpenAI-compatible boundary behavior.

**Exit criteria:** `start/executeStep/inspect/artifact/render/validate/trace` are symmetric and path-free; observations are read-only and zero-Provider; artifact access policies keep source/model-sensitive bytes metadata-only; the validator closes exact 52-output inventory and installs the root manifest last; explicit step execution is a new identity, not same-run recovery.

## Batch 10 — full fixed jshERP acceptance

**Scope:** After separate source, budget, and model authorization, run the full fixed jshERP acceptance from approved commit through the complete runtime and manually assess the resulting business semantics and nine-section candidate.

**Exit criteria:** The exact immutable source is available and independently verified; every technical and semantic denominator is closed or reasoned; actual provider request counts, receipts, gaps, pending confirmations, Trace, archive, and 52-output root manifest validate; manual review finds no fabricated actor, order, rule, metric, or external effect. Missing source objects, authorization, budget, or a started-call failure blocks acceptance and is reported without refresh, retry, substitution, or weakened evidence.

## Batch gate

Do not claim a batch complete from a partial slice. Record each exit criterion's fresh command/artifact evidence in the owning progress file; unresolved criteria keep the batch `IN_PROGRESS` or explicitly blocked. A change to the eight steps, fixed nine sections, evidence/Proof/Trace trust, model-visible responsibility, human authorization, public interface, or exact 52-output accounting returns to the user and Sol/ultra design authority before implementation continues.
