# JDT LS 入口驱动源码上下文可行性实施计划

> **COMPLETED RESEARCH HISTORY：** 本调研已经完成并被正式 Java engine 实现吸收。不得从本页重跑实验或把调研 packet 当当前生产输出；当前目标见[业务过程发现与重建设计](business-process-discovery-and-reconstruction-change-design.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` task by task. Track the checkbox state; do not run customer builds, a model, or a second source tool.

**Goal:** 仅给 Controller 入口，自动展开仓库内可导航调用并组织完整方法正文，保留实参、形参、条件和返回，在外部、无源码、歧义及动态边界明确停下。

**Architecture:** 复用现有 frozen projection、JDT/JDK/LSP4J、cache、session 和 checker。JDT 只负责从调用 token 返回 target location/candidates；JavaParser 只从入口或 JDT 已返回的文件位置切 syntax body、调用原文和参数，不解析 target 名称。两真实案例共用一次 session，但独立运行、独立结论。

**Tech Stack:** JDT LS `1.61.0`、LSP4J `1.0.0`、JavaParser core `3.28.2`、Jackson `2.21.4`、JUnit 5；现有 isolated Maven research harness。

**Spec:** [jdtls-source-navigation-feasibility-design.md](jdtls-source-navigation-feasibility-design.md)

## Global constraints

- Frozen input remains jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; never use clone HEAD or working-tree bytes.
- Preserve the existing empty-capabilities packet, logs, report and hashes. New attempts use distinct paths and never overwrite historical evidence.
- No customer Maven/Gradle/plugin/test/script/application, annotation processor, dependency resolution, model call, nine-chapter generation, production integration or second tool.
- Every selected call site has a disposition. Missing a known core repository Service or hitting a resource limit is `INCOMPLETE`, not external-boundary `PASS`.
- Oracle/checks are read only after packet persistence and hash; target paths, method lists and business keywords never enter navigator input.
- Generative model inventory: **none**. Expected elapsed time after authorization: **2–4 hours**, including meaningful harness repairs; tool uncertainty is reported separately.

## Files

- Modify: `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/TrialRunner.java`
- Modify: `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/PacketOracleCheck.java`
- Modify: `research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/NavigationProbeTest.java`
- Modify only for attempt routing: `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/NavigationProbe.java`
- Modify after measurement: `research/jdtls-source-navigation-feasibility/README.md`
- Create after measurement: `research/jdtls-source-navigation-feasibility/GOAL-DRIVEN-REPORT.md`
- Do not modify production source, schemas, `checks/*.json` to guide traversal, or the historical `REPORT.md` body.

---

### Task 1: Protocol/body RED — Luna/xhigh

**Produces:** Direct tests for correct capability negotiation, paired responses and source-body recovery.

- [ ] Test initialize params for hierarchical document symbols, declaration/definition/implementation `linkSupport=true`, and call-hierarchy support.
- [ ] Test normalization of hierarchical `DocumentSymbol`, flat `SymbolInformation`, `Location`, `Location[]`, `LocationLink[]`, and `null`.
- [ ] Given only a returned name/selection position, require the exact enclosing method/constructor AST range, including real columns and its last line.
- [ ] Require the known entry range to produce its complete Controller body even when `documentSymbol` is empty.
- [ ] Require each journal record to pair full request params with the unfiltered result/error; the current request-only shape must fail.
- [ ] Run only:

  ```bash
  mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml -o -Dtest=NavigationProbeTest test
  ```

  Expected: the new assertions fail for the intended missing behavior; existing materialization/oracle tests remain green.

### Task 2: Correct negotiation, then measure one real Controller hop — Terra/xhigh

**Produces:** A preserved first-hop attempt proving that real entry call tokens can yield at least one required repository Service body before investing in the generic walker.

- [ ] Replace `capabilities={}` with the exact design JSON; retain existing offline settings, selected tools, cache, projection verification and one stdio session.
- [ ] Record params before dispatch and complete result/error after completion in one exchange; keep notifications separate and copy case slices.
- [ ] Preserve URI and start/end line+character. Do not reduce locations to lines or treat JavaParser columns as UTF-16 offsets.
- [ ] Parse and save the entry body from its frozen known range, enumerate every entry call in lexical source order, and send actual `textDocument/definition` requests. Materialize each returned repository target body but do not recurse into target calls yet. Do not consult oracle or preselect a Service.
- [ ] Match hierarchical symbols when present, then choose the smallest enclosing JavaParser method/constructor around the JDT anchor. Treat flat name ranges as anchors, not bodies; remove the `Integer.MAX_VALUE` workaround.
- [ ] Run Task 1's command. Expected: protocol/body tests pass without a live JDT process.
- [ ] Package the research harness offline, then run a distinct `goal-driven-first-hop-<attempt-id>` against the real registration Controller. Persist/hash paired definition exchanges and extracted bodies before the independent checker reads them.
- [ ] Gate further work on the after-output evidence: at least one required Controller→Service target must have been located automatically with a complete body. If not, classify the attempt `INCONCLUSIVE` or `LIMITATION` after diagnosis and stop before Task 3; do not spend time completing generic recursion.

### Task 3: Closed breadth-first packet — Luna RED, Terra GREEN

**Produces:** Every included method and call site is readable and has an honest outcome.

**Prerequisite:** Task 2's real first-hop Service-body gate passed. Its session may be closed cleanly after evidence capture; the final two-case measurement in Task 4 uses one fresh shared session/index.

- [ ] Add RED fixtures for nested method calls, object creation, explicit constructor calls and method references; explicitly catch the current missing `calls.add(callNode)` defect.
- [ ] Cover unique repository target, multiple candidates, repository unresolved, interface/no source, external URI, callback/async/reflection, cycle and resource limit.
- [ ] Keep all repository candidates and extract every available candidate body; enqueue each with a candidate marker without claiming one runtime dispatch. Only no-source candidates stop at the candidate boundary.
- [ ] Preserve external targets instead of filtering them to unresolved. A repository receiver with a missing dependency remains `REPOSITORY_TARGET_UNRESOLVED` and makes the case incomplete.
- [ ] Store exact call text, actuals and formals. Test fixed arity, constructors and varargs buckets; do not fake an exact pair for a vararg tail.
- [ ] Replace depth-first recursion with deterministic breadth-first traversal. Full snippets retain lexical `if/switch/try/catch/return/throw`; do not build graphs.
- [ ] Registration failure must not skip financial while the shared session is healthy. Protocol/environment fatal state records later cases as `UNAVAILABLE` with a reason.
- [ ] Strengthen `PacketOracleCheck` after persistence to validate frozen body identity, call-site outcome closure, core implementation presence and honest mappings. Business review points stay outside traversal and the structural gate.
- [ ] Run Task 1's command. Expected: all direct research tests pass; no customer or live JDT execution.

### Task 4: Measured session and evidence-first report — Terra, Sol/xhigh, Sol/ultra

**Produces:** Two real case packets, paired exchanges and `GOAL-DRIVEN-REPORT.md`.

- [ ] Create a sanitized `goal-driven-<attempt-id>` beneath the trial output; refuse an existing ID and leave historical active results/failed-runs untouched.
- [ ] Run only the research offline test/package and existing materializer verification, then one offline JDT session for `registration,financial` against the same projection/index.
- [ ] Persist/hash each packet and its paired exchanges before the checker. Report the first actual definition exchange that returns a repository target; do not call an expected response “measured.”
- [ ] On client/harness/index/environment faults, preserve the attempt as `INCONCLUSIVE`; Sol/xhigh may make a focused repair and remeasure. At 30–45 minutes without concrete advance, record the diagnosis and next decision—never relabel it as a JDT limitation.
- [ ] Classify each case separately: useful closed packet=`PASS`; missing core body/candidate or resource truncation=`INCOMPLETE`; reproducible JDT gap after valid use=`LIMITATION`; infrastructure/protocol fault=`INCONCLUSIVE`. Both cases must pass to complete the planned validation; one pass is only partial feasibility.
- [ ] Write the new report with these first sections:
  1. actual request/response → program-extracted full body, clearly splitting JDT and harness contributions;
  2. actual historical Controller-only registration material path/hash → actual new packet path/hash before/after table, comparing the bespoke resolver/material assembly rather than parser brands;
  3. a business narrative supported only by code actually present in the new packet, explaining future LLM benefit without claiming runtime success.
- [ ] Show both actual packets, or `UNAVAILABLE` plus attempt evidence. Never substitute oracle source, expected JSON, model output or a nine-chapter report.
- [ ] Update README navigation/current interpretation and run `git diff --check` plus `git diff --cached --check`.

## Agent progress and estimate

Every implementation Agent creates its own new file from `progress/TEMPLATE.md`; do not reopen or rewrite any completed `jdtls-*.md` progress file.

| Work | Agent | New progress path | Estimate |
| --- | --- | --- | --- |
| Task 1 protocol/body RED | Luna/xhigh | `progress/jdtls-goal-driven-tests.md` | 20–30 min |
| Task 2 thin harness + real first hop; Task 3 GREEN after gate | Terra/xhigh | `progress/jdtls-goal-driven-harness.md` | 75–135 min |
| Focused failure diagnosis, only if triggered | Sol/xhigh | `progress/jdtls-goal-driven-debug.md` | 0–30 min |
| Task 4 measurement/evidence review | Sol/ultra or Astra/ultra, with Terra run support | `progress/jdtls-goal-driven-review.md` | 25–45 min |

Expected total is **2–4 hours**. The estimate includes a bounded meaningful repair, not a promise that JDT will pass.

## Resume

Next implementation action: Task 1's Luna/xhigh RED tests. This documentation turn stops here—do not build, launch JDT, move artifacts, call a model, commit or push.
