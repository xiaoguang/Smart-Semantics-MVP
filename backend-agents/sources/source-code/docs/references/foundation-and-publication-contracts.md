# 来源、发布与信任合同

本附录服务[总体设计](../DESIGN.md)，维护来源完整性、计算owner、保存与业务真实性。逐步算法由各步骤拥有，执行/重试由[模型执行](../modules/model-job-execution.md)拥有，目标版本/双来源由[Activity集成](../modules/activity-explanation/integration-contracts.md)拥有。

## 1. 依据能说明什么

| 依据 | 能说明 | 不自动说明 |
| --- | --- | --- |
| 冻结source/SourceExcerpt | 指定文件、范围与原文 | 某次运行成功、完整业务含义 |
| JDT索引/候选/调用 | 已观察声明、正文、导航关系和边界 | 唯一动态dispatch、全部运行路径 |
| Step04持久化材料 | 已读XML、绑定/SQL分析结果与具体限制 | 解析失败等于无源码、SQL实际执行效果 |
| Step05阅读包 | 已收集入口/方法/调用/控制/返回/持久化材料 | 全仓知识已读、每个候选都实际调用 |
| 历史Fact/Proof | 原版本严格技术规则成立 | 业务解释必须局限于这些证明 |
| ReviewedActivity/Process | 基于实际阅读材料的解释、certainty和来源 | 人工确认制度、实际付款/库存等外部事实 |

技术候选/SQL未知与业务未知分别记录；没有旧Fact/Flow不抹去安全可读源码。业务模型可以解释代码定义的保存动作，不声称某次事务已成功。来源引用定位原文，不要求每词一条Proof。

## 2. 唯一计算owner

Step01拥有冻结来源，Step02拥有入口分母，Step03拥有JDT导航与完整Java材料，Step04拥有可选持久化材料，Step05拥有入口阅读关系/覆盖。当前JDT-only生产已实现；旧JavaParser/图/Fact/Flow/Capsule/M10仅历史读取。

新Step06内部ActivityMaterialProjector负责无损投影及来源映射，ReadingCoordinator负责按模型决定取回实际完整单元，ActivityExplainer唯一负责业务解释。允许安全结构读取保存的XML rawSource，不运行JDT/PersistenceAnalyzer/JSqlParser或另建行业语义分析器。

Step07 Cataloger发现候选，Assembler读取/封包，Reconstructor已实现DRAFT→WRITE→最后RULE_REVIEW，Consolidator做既有无损关系裁决，Publisher只校验/排版。完整原文在DRAFT前到位，最终核对看到实际写作全文。Step08只读历史report，不启动新生成。

## 3. 保存与来源作用域

使用现有CanonicalJsonCodec、typed引用、原子store与receipt。owner计算一次后传immutable view；publisher不重复生产算法。磁盘/新进程/import/跨run重开才核验exact file set、身份/hash/schema/ref/basis和实际读取字节；正常内部调用不反复扫描。

完整公式见[Canonical附录](canonical-persistence-identity-contracts.md)，公共请求/SourceLocator/Envelope见[公共合同](inherited-public-and-module-contracts.md)。不新增hash链、恢复框架、通用manifest store或固定52/57文件目标。

历史M10 SourceRef保持其原scope；新Step05 sourceRef属于packet，模型S属于请求。任何跨包汇总都先按owner映射原件，不能裸S1/source:1 join。一个Sref若指整XML原件，不表示模型已经读完整文件；实际结构投影/已读unit与原件来源分别保存。

新Activity coverage的materialSource和packet/slice映射是来源manifest，非新公共文件。源码材料属于sourceRunId，Activity/过程属于相应modelBatchId；显式discriminator选择历史M10 ModulePublication或新Step05 AnalysisStepPublication，不按shape猜版本或强转类型。

## 4. Job保存、失败与复用

当前Stage07完整三阶段job立即私有保存，随后按原ordinal聚合；正式归并/发布等待所有处置闭合。raw请求/响应、失败记录、来源材料与326旧Activity不可覆盖。publisher可重开上游receipt校验basis，不回读正文重新解释。

新Activity设计保存每次attempt，成功阶段可在同输入/Schema/Prompt/binding/读取范围严格匹配后复用。maxAttempts包含首次，失败阶段单独重试；包局部失败不影响其他安全包，共享来源/配置/认证不安全时停止相应故障域。部分M11可以保存，但modelBatchComplete=false，不能自动进入Step07；精确合同由执行Module拥有。

这不改变Step07无自动重试：fatal停止新派发，已开始且前置合法的job排空保存，不归并/发布；显式新batch只复用完整匹配三阶段，旧pair/孤立DRAFT/WRITE不可当完成。材料/source不因模型失败重跑。

指纹含实际内容/来源映射、Prompt/Schema、任务序列、profile与服务/账户/model/effort；新runId、并发、日志路径、完成顺序不改变业务内容。持久化地址仍属于真实owner，不得以指纹相同跳过陌生bytes完整性验证。

## 5. 模型与覆盖

业务默认已登录ChatGPT Codex上下文Terra/xhigh，任务绑定固定；不自动改Provider或API fallback，历史身份不改。具体认证/配额见模型执行合同，本轮未调用产品模型。自动验证仅用冻结fixture/scripted Provider。

完整材料、Prompt/Schema和输出余量用于保守预检，不声称掌握服务端精确token计账。明显超容量保存原因，不静默剪裁条件/公式或删稿。模型决定业务阅读范围，Java验证句柄/真实原文/集合/容量，不能用行业规则宣称语义充分。

所有入口、packet/slice、Activity、候选和过程都有明确处置。未读范围、部分成功、缺常量值、宽泛候选与合法材料不足照实保留。Activity保留DIRECT_CODE_BEHAVIOR/REASONABLE_INFERENCE/NEEDS_CONFIRMATION，Process保留CONFIRMED/INFERRED/UNRESOLVED；不混用wire枚举。非法JSON/未知ref、错来源、损坏字节或虚假完整不能以低置信度隐藏。

## 6. 当前实现与验证界限

Step01–05取材已完成；新Step05→Activity、阶段retry和目标双来源接入未实现。Step07系统认识、聚焦CHECK、三阶段、私有v3、producer v4/五文件及历史重开已实现。历史三例12请求没有全部准确性通过，详见[实测](../supplements/cross-object-process-reconstruction/three-case-acceptance-result-20260916.md)。

[真实材料推演](../examples/activity-material-end-to-end-walkthrough.md)明确区分保存原文、目标投影、拟Activity与Step07补读；不冒充新模型结果。设计逻辑闭合、程序测试、可读性认可和实际语义质量分别报告。本轮不重跑代码/测试/模型或扩大全仓范围。
