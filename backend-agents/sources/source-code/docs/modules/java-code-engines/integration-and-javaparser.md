# 生产接入与JavaParser历史读取

文件名保留导航稳定性。本页当前生产只有JDT；JavaParser是已退役producer的历史读取说明，不是待恢复的第二引擎。

## 1. 当前已实现接线（新四操作目标另列）

| 步骤 | 实际调用与输入 | 新生产输出 |
| --- | --- | --- |
| 01 verified-source-inventory | 固定来源清单与安全文本读取 | VerifiedSourceInventory |
| 02 application-discovery | 同session catalog、精确方法位置、轻量Mapper目录 | 完整入口分母，合法UNRESTRICTED，Mapper候选 |
| 03 program-graphs | JDT collect与一次索引发布 | module7 java-code-index-v2 |
| 04 proven-code-facts | PersistenceAnalyzer，索引+冻结XML+可选插件 | module4 persistence-material-index-v1 |
| 05 business-flows | CodeReadingMaterialBuilder，已读Java/持久化/前端索引 | module4 code-reading-material-set-v2 |

`source-analysis --config <path> plan-materials`、其 `materials-only` 一键01–05编排和直接CLI同名命令均已退役；它们在读取配置或调用Agent前拒绝。历史 `READING_MATERIALS_ONLY` 保存输出仍按其原有schema只读，不触发Activity/Provider，也不构成一条可运行的命令替代。技术入口由`collect-code`、`analyze-persistence`和`assemble-materials`串联，详见[技术运行合同](../technical-analysis/cli-and-runtime.md)。

当前三个技术命令各自拥有其跨运行顺序并复用底层发现、分析和组包模块；Publisher只保存已经组装的不可变结果，不重复collect/analyze/build。MapperXmlResourceView使用同源XML；各次操作重新打开已保存原文而不重新导航，变换用副本。新目标前端独立R1、后端R2、持久化R3、组装R4，见技术运行合同；不是改写旧运行编号。

## 2. 当前模型消费与本次技术边界

已实现CodeReadingMaterialReader重开完整后端Packet，Activity执行已能直接消费Step05 v1，并保留旧M10分支；不能再写成“只有旧M10”。418条已存新Activity的范围完成性见[Activity当前状态](../activity-explanation/README.md)，数量不等于全部必需范围已经完成。

外部Maven交接及技术前端Packet v2已有固定源码结果；本次目标是四个独立操作、调用归属/状态修正、完整单元/SQL排序投影与每入口自包含JSON。准确版本见[运行矩阵](../technical-analysis/cli-and-runtime.md#5-版本与兼容)。环境已知缺项阻断、诊断覆盖未知披露后继续不变；新入口证据未适配的业务入口必须在Provider初始化前拒绝，不静默丢字段或重新生成Activity。

新model batch明确声明CODE_READING_MATERIALS来源，重开Step05后投影/阅读；模型阶段不调用本页五步。Activity retry只重试失败模型stage，不使旧材料失效，不清JDT缓存或再次扫描。Step07按M11来源读取旧M10或新Step05，不隐式重跑Activity。

## 3. 历史JavaParser与严格输出怎样保留

旧JavaParser Adapter及严格图、Fact候选/Proof、Flow/Capsule与M10生成代码已退役。历史数据保留其真实engine、schema、receipt和来源；不得改标JDT或新XML增强。

| 历史保存内容 | 只读合同 |
| --- | --- |
| JavaParser配置与旧index/图 | 可解码既有state/hash；新Factory不能创建JavaParser、fallback或双writer |
| 五图/CodeStructure/GraphSet | 原始artifact可按原版本查询；无实际消费者的typed reader/DTO/专属测试进入精确删除核对，不因“历史”笼统保留 |
| Fact/Proof/accounting | 不改写原结果含义；确有历史材料消费者的读取最小集合保留，无消费者链删除；不枚举新候选或补空accounting |
| flow compilation v6 / capsule projection v11 / flow-slices v6 / evidence-capsule v9 | 引用原index/context严格重开，不作为新05产物 |
| M10 business-materials /旧state-v3 | 保存材料原样重开，禁止新安装M10或伪装Step05引用 |
| 326旧Activity与模型DRAFT/REVIEW | 原模型/输入/正文/SHA不变；不因本轮设计强制重生成 |
| 历史报告 | reader/确定性renderer零Provider，只生成显式另存的展示文件 |

历史严格检查保留来源正确性；不把旧Proof闭合作为当前Java阅读或XML解释门槛。读取损坏历史结果应失败，不用重新计算producer掩盖缺失。上述删除须按[依次清理设计](../../plans/technical-analysis-cli-and-vue-cleanup-design.md)核对真实消费者、反射和序列化；本轮设计任务没有删除代码。

## 4. 最小版本和安装规则

现有历史业务state v4和技术state v5分别读取；公共request v4/output v8、原三命令生产已经接通。本次目标request v5/output v9和state v6的ENTRY_EVIDENCE_SET表达四操作及R4双分支，所有输入保留准确owner；旧格式不原地塞新字段。

新Activity版本仅由接入合同维护。module/step政策和artifact查询必须精确区分新05 Step reference与旧M10 Module reference，不能放松为任意跨run引用。历史地址可读不代表允许新安装。

## 5. 直接验收和现状

直接测试覆盖新01–05链、准确前驱/类型、新材料重开完整正文、插件关闭零工具、01–05结束Provider0、历史五图/Fact/M10/Activity原字节、损坏拒绝、禁止新安装旧producer而保留M11。

历史提交5ceb111完成了当时的生产退出及469项clean CI；2026-09-18固定仓库材料验收数字见[交付核验](../../supplements/jdt-persistence-reading-materials-delivery.md)。这些不是当前未提交工作区或本次新增技术设计的测试结果。当前Activity消费后端Packet已有实现，原三个技术命令READY/受阻、前端关联和Step05 v2也已贯通；固定源码准确性仍未通过。新增四操作及entry证据尚未实施。本轮文档同步未运行构建或模型。

更早两引擎接入算法与当时版本迁移正文可查Git提交5ceb111的同一文件；不再列为开发任务。
