# 生产接入与JavaParser历史读取

文件名保留导航稳定性。本页当前生产只有JDT；JavaParser是已退役producer的历史读取说明，不是待恢复的第二引擎。

## 1. 当前五步接线

| 步骤 | 实际调用与输入 | 新生产输出 |
| --- | --- | --- |
| 01 verified-source-inventory | 固定来源清单与安全文本读取 | VerifiedSourceInventory |
| 02 application-discovery | 同session catalog、精确方法位置、轻量Mapper目录 | 完整入口分母，合法UNRESTRICTED，Mapper候选 |
| 03 program-graphs | JDT collect与一次索引发布 | module7 java-code-index-v2 |
| 04 proven-code-facts | PersistenceAnalyzer，索引+冻结XML+可选插件 | module4 persistence-material-index-v1 |
| 05 business-flows | CodeReadingMaterialBuilder，已读Java/持久化索引 | module4 code-reading-material-set-v1 |

唯一 `source-analysis --config <path> plan-materials`执行01–05后结束，登记READING_MATERIALS_ONLY。不以FLOW_INTERPRETATION作为材料准备target，不启动Activity/Provider。

TechnicalAnalysisWorkflow及既有execution组合根拥有调用顺序；Publisher只保存已经组装的不可变结果，不重复collect/analyze/build。MapperXmlResourceView在02/04同来源运行内共享只读DOM，变换用副本。

## 2. 新模型消费接线目标

已实现CodeReadingMaterialReader重开完整Packet；当前模型CLI/Activity publisher仍是旧M10类型。目标只在[Activity接入合同](../activity-explanation/integration-contracts.md)列出的直接owners升版，Step03/04/05 schema不变。

新model batch明确声明CODE_READING_MATERIALS来源，重开Step05后投影/阅读；模型阶段不调用本页五步。Activity retry只重试失败模型stage，不使旧材料失效，不清JDT缓存或再次扫描。Step07按M11来源读取旧M10或新Step05，不隐式重跑Activity。

## 3. 历史JavaParser与严格输出怎样保留

旧JavaParser Adapter及严格图、Fact候选/Proof、Flow/Capsule与M10生成代码已退役。历史数据保留其真实engine、schema、receipt和来源；不得改标JDT或新XML增强。

| 历史保存内容 | 只读合同 |
| --- | --- |
| JavaParser配置与旧index/图 | 可解码既有state/hash；新Factory不能创建JavaParser、fallback或双writer |
| 五图/CodeStructure/GraphSet | 按原typed reader检查文件、端点/引用/前驱与hash；不重跑图builder |
| Fact/Proof/accounting | 原CLOSED、disposition与守恒意义不变；不枚举新候选、不补空accounting |
| flow compilation v6 / capsule projection v11 / flow-slices v6 / evidence-capsule v9 | 引用原index/context严格重开，不作为新05产物 |
| M10 business-materials /旧state-v3 | 保存材料原样重开，禁止新安装M10或伪装Step05引用 |
| 326旧Activity与模型DRAFT/REVIEW | 原模型/输入/正文/SHA不变；不因本轮设计强制重生成 |
| 历史报告 | reader/确定性renderer零Provider，只生成显式另存的展示文件 |

历史严格检查保留来源正确性；不把旧Proof闭合作为当前Java阅读或XML解释门槛。读取损坏历史结果应失败，不用重新计算producer掩盖缺失。

## 4. 最小版本和安装规则

当前 `repository-run-state-v4`只拥有Step05材料；`analysis-run-output-v5`只接受READING_MATERIALS_ONLY和readingMaterialCheckpoint。旧v3/v4 output按其原字段与owner严格读取；不原地塞新字段。

新Activity版本仅由接入合同维护。module/step政策和artifact查询必须精确区分新05 Step reference与旧M10 Module reference，不能放松为任意跨run引用。历史地址可读不代表允许新安装。

## 5. 直接验收和现状

直接测试覆盖新01–05链、准确前驱/类型、新材料重开完整正文、插件关闭零工具、01–05结束Provider0、历史五图/Fact/M10/Activity原字节、损坏拒绝、禁止新安装旧producer而保留M11。

当前5ceb111已完成这些生产退出与直接/469项历史clean CI；2026-09-18固定仓库新材料验收完成，数字见[交付核验](../../supplements/jdt-persistence-reading-materials-delivery.md)。本轮未运行构建或模型，未证明新的Activity消费链已经实现。

更早两引擎接入算法与当时版本迁移正文可查Git提交5ceb111的同一文件；不再列为开发任务。
