# 业务联系优先的本体识别实施计划

状态：2026-10-05，用户已授权实施。完成状态由 progress/business-link-first-execution.md 和最终验收记录给出，本文不将设计保存等同实现通过。

依据：[详细设计](../modules/ontology-recognition/business-link-first-design.md)。本文执行用户确认的九步计划；字段、版本、失败语义及完整材料要求以该设计为准。

## 目标、架构与边界

已有 R4/R0 → Java 共同项/控制分支导航与紧凑完整原文 → 模型最小对象/具体联系 → Java 四文件发布及可查看业务图 → 新运行识别操作、身份、规则与分析定义 → 新发布。

复用现有四个本体命令、Agent、canonical store、Provider 和一次审阅/失败隔离。Java17、Jackson、固定 Mermaid11.12.0 离线视图。旧 MD、Activity、过程、九章及原始 R0/R4不变；不运行客户工具或重做取证，不增加解释器、语义布局引擎、向量/图数据库或自动修稿系统。

固定输入：

- R4：analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744。
- R0：analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7。
- 继续 codex/ontology-recognition；保留已有暂存、实验和无关工作。

## 实施步骤

各开发步骤：直接行为 RED → 最小实现 → 定向 GREEN → 同步当前事实文档。只执行直接覆盖测试，同时一个重型构建。

| 步骤 | 交付与检查点 | 初估 |
| --- | --- | --- |
| 0 | 保存基线、计划、进度与保护摘要；验证固定输入 | 0.5–1h |
| 1 | 新合同、producer/reader/schema/policy/query/fixtures同批接通；旧格式准确恢复 | 3–5h |
| 2 | CONTROL_REFERENCE K、具体共同项、一跳导航、源码→页面上下文反查；多实例不串用 | 4–6h |
| 3 | callRows/callUses精确去重、CT/CO、完整所选正文/用途/限制可逆；完整 EXTRACT/REVIEW成本 | 4–6h |
| 4 | scope-v2 SKELETON、typed-v4 displayRole/clueDispositions、一次审阅、遗漏K处置 | 4–6h |
| 5 | ENRICHMENT objectSources、准确已审对象小目录、O3显式依赖闭包、循环/缺源零派发 | 3–5h |
| 6 | 四文件发布、recognitionLayers、ONTOLOGY_BUSINESS_OVERVIEW离线HTML及准确来源查询 | 4–6h |
| 7 | Skill、示例、scripted Provider正式骨架→细化链、定向回归与静态质量 | 2–3h |
| 8 | 两条已实验联系先交实际图，再验财务操作/期间统计；成本、来源、未知及实现PR | 1–2h＋模型运行 |

工程初估26–40h，步骤3结束校准未开始部分，工具/模型等待单列。

## 接口及版本

四命令保持 prepare-ontology / identify-ontology / relate-ontology / publish-ontology。现有 artifact 新增闭合只读键 ONTOLOGY_BUSINESS_OVERVIEW 输出派生HTML；业务事实仍只有四个正式JSON/JSONL文件。图使用固定Mermaid离线资源，严格转义不可信文本，语义/方向来自已审定义。

版本依设计§14：corpus-v2/projection-v3/O0 producer-v2；scope-v2；reading-packet/model-reading-v5与EXACT_ROWS_WITH_USES_V1；decision-result-v5/reading-state-v2/reading-input-response-v4；identification/relations-v3；typed-v4/ontology-v2；publisher-v3/coverage-review-v3。source-v1、config-v1、公共request-v6/output-v10不变。未知版本拒绝；旧记录不转换。

scope-v2 purpose只有SKELETON、ENRICHMENT；每题objectSources明确{identificationRun,questionId}。SKELETON仅OBJECT且外部来源为空。细化ACTION/ANALYTIC依赖本题前置或明确外部已审OBJECT，B目录准确映射原owner。O3必须显式选择全部语义上游，不自动补入。

## 验证重点

1. 去重误合并：逐物理调用位置、候选/实参/状态/展开差异及各入口用途完整恢复。
2. 页面上下文串用：同一组件不同页面、未传参数、未知地址、冲突候选保持独立。
3. 跨运行串源：裸短号不可跨题使用，缺上游/未审对象/循环在Provider前拒绝。
4. 覆盖缩小：未请求、漏答、失败、容量阻断保留完整分母；骨架不冒称全本体覆盖。
5. 渲染改事实：对象角色由模型审阅，孤立/未知对象可见，箭头不倒转，脚本/指令注入不可执行。

离线测试扩展现有正式材料、阅读、typed、runtime与assembler测试；增加总览 renderer/query。非ERP、改名、公共Service假关联和多态使用同一规则。正式链使用scripted Provider，零生产模型。

定向GREEN之后同状态质量检查：

```bash
JAVA_HOME="$SOURCE_ANALYSIS_QUALITY_JAVA_HOME" \
mvn -t .mvn/toolchains.local.xml -Pquality \
  -DskipUTs -DskipITs spotless:check verify
```

实际验收从新O0导航和模型选择组织材料，不导入PoC冻结包或正确答案。先检验来源编号及双仓库联系，再用财务和期间统计检验细化。完整保留请求、原稿、审阅、来源、成本和未知；材料能力与模型质量分别判断。字节不是token；没有可靠计数写未知；不宣称任意小窗口、全仓、完整采购生命周期或另一系统同等质量。

## 执行和交付

GPT-6 Sol/xhigh调度、debug和临时编码；Luna/xhigh直接测试。最多两个工作Agent。既有产品Luna/high绑定保持；每O1/O2最多12请求、4阅读轮、每轮8动作、并发1、零自动重试。未配置DDL则不新增解析。沿用明确范围授权，同范围程序缺陷持续修复，不把历史累计请求数作永久阻断。

交付图、四文件本体、完整映射/材料/实际请求、四命令和同一Skill、设计—实现—测试对应及验收记录。选择本轮相关改动提交PR；历史数据、本机配置、凭据、无关修改不提交，不强推。进度及worker handoff只在整个计划完成并归入验收记录后清理。
