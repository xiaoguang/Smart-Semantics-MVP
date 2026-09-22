# 入口阅读材料

> [总体设计](../DESIGN.md)；固定 key：`business-flows`，目录：`steps/05-business-flows/`。当前唯一材料 owner 为 `analysis.material`；本页替代旧 Flow/Capsule 编译/投影生产职责。

## 1. 为什么存在

上游已有完整方法、调用候选和可选 XML/SQL。本步将它们组织成按入口可读、可保存并可重开的技术材料，让下一步无需重新导航。材料包不是业务过程；一个通用新增单据入口不能仅因分包被宣布为采购、销售、调拨三个独立过程。

Step05保存技术原件的选择引用和来源；[Step06](06-flow-interpretation.md)拥有针对模型容量的阅读投影、选材和Activity解释。技术包能完整导出，不代表能一次放入模型。

## 2. 输入与 Interface

`CodeReadingMaterialBuilder.build(CodeReadingMaterialRequest)` 返回 `CodeReadingMaterialSet`。请求只接收已经读好的不可变 JavaCodeIndex、PersistenceMaterialIndex、完整上游publication和CodeReadingMaterialProfile；不接收Provider、JDT、parser、reader或任意源码路径。

| 输入 | 使用 |
| --- | --- |
| JavaCodeIndex.entries() | 完整已发现入口分母，包括未收集入口 |
| 共享METHOD + 入口CALL | 原文、候选、参数、控制/返回、成员归属 |
| PersistenceMaterialIndex | Mapper关联与完整XML/SQL材料；关闭时为明确DISABLED对象 |
| source/discovery/navigation/persistence refs | 保存与重开时验证同源和前驱 |
| technical.readingMaterials | 必填正值 maxPacketUtf8Bytes、maxEntriesPerPacket，参与材料身份 |

旧 business.material 片段行数限制不能近似转成新profile；旧technical.flow/capsule不是新准备命令前置要求。

## 3. 程序如何组包

1. 遍历完整入口清单；上游未收集入口直接保存具体原因。
2. 从入口完整声明/正文起，按现有调用首次遍历顺序选择完整方法。并列项用稳定key排序，循环只保留引用。
3. 每个call保留entryId、调用位置、实参和各候选的形参/展开状态；共享方法正文一份，调用发生点不合并。
4. 命中Mapper时同时加入完整声明和所关联statement、相关资源/依赖、绑定与可用SQL结构。插件关闭不造空XML。
5. 为所选METHOD/XML_RESOURCE分配短SourceRef，保存其单元与精确已知位置；同一packet中同一ref只能映射一处。当前source:1等编号会在不同packet重复，跨包身份必须包含publication + packetId + sourceRef。原文不同时复制进refs与modelPacket。
6. 使用同一个确定性Markdown格式器计量完整自包含投影UTF-8字节，包含代码、调用、参数、XML/SQL与限制；不是估计行数。
7. 完整单元放不下时记录该入口未选单元和原因。最小完整入口单元也放不下则NOT_COLLECTED。不得切半方法、XML条件或插入的列值。
8. 发布一次材料选择和覆盖，按需导出技术预览；结束于Step05，Provider不初始化。

当前单包选择遵守入口与材料容量，不能以“已被另一个包选中”吞掉本入口受影响调用。模型需要范围外材料时首先报告原包未选/导航限制；Step06不能偷偷扩大source selection或重新生成Step05。

## 4. 保存合同与重开

地址：Step05 module4 `code-reading-materials`；文件：`code-reading-materials.jsonl`；schema：`code-reading-material-set-v1`；artifact：`CODE_READING_MATERIAL_SET`。保存的是引用式canonical材料，不是Markdown。

| 记录 | 保存/内存职责 |
| --- | --- |
| HEADER | sourceInventory、navigation/persistence publication、snapshot、实际profile；保存侧同时核对discovery |
| PACKET | packetId、入口、method/call选择引用、persistence选择引用、SourceRef位置、unselectedUnits、limitations、自包含字节数 |
| ENTRY_COVERAGE | entryId、packetIds、COLLECTED/COLLECTED_WITH_LIMITATIONS/NOT_COLLECTED、具体限制 |

METHOD正文唯一派生存储在Step03，XML Resource原文唯一派生存储在Step04；冻结原始快照仍保留。没有第二份M10/modelPacket canonical正文。

`CodeReadingMaterialReader.reopen(AnalysisStepPublicationReference)` 已实现：

1. 检查Step05 receipt、准确schema/producer/类型、来源与完整上游引用。
2. 一次重开所需Java与持久化索引，按已保存选择引用恢复原对象。
3. 验证入口/调用owner、SourceRef位置、已选/未选范围与实际投影字节。
4. 返回完整只读Packet，包含真实MethodCode、EntryCall、PersistenceSelection；不再调用build、analyze、collect或重新选择单元。

普通可信同进程直接传不可变对象；跨进程在reader seam核验，不让每个内部函数重新打开整个索引。

## 5. 技术预览与模型投影

`CodeReadingMaterialMarkdown.render/renderPacket`导出完整技术材料，文件/行号、调用树、Java正文、逐入口calls、XML/SQL与未选列表均可见。重开后逐字节一致，预览不是下一生产输入。

新模型路径直接接收Packet：移除重复导航呈现、只显示短ref、按完整statement及依赖生成XML结构投影；保留Java方法原文和所有条件。该职责见[Activity材料投影](../modules/activity-explanation/material-projection.md)，不修改本页canonical schema或既有技术Markdown。

真实新增单据样例有443方法、1,468calls、23份XML资源、58条statement，自包含2,055,071字节；因此“已有325份包”不能推出“模型可一次阅读325份”。具体长材料策略由Step06在实际请求预算上决策。

## 6. 输出怎样被使用

| 消费者 | 读取方式 |
| --- | --- |
| plan-materials / artifact | 材料准备止于本步；artifact按指定format导出JSONL或Markdown |
| 新Activity目标 | 新batch绑定完整Step05 reference，reader恢复Packet再投影，不调用旧M10 Builder |
| Step07目标新corpus | 按Activity的明确material来源重开已保存代码；旧M10分支继续只读 |
| 历史结果 | 旧Flow/Capsule/M10/326Activity原字节、来源与schema不变，不被自动升级 |

现有 `repository-run-state-v4`保存CODE_READING_MATERIALS checkpoint，`analysis-run-output-v5`只表示READING_MATERIALS_ONLY。模型batch接线目标需升级自己的execution/output合同；本材料状态不为了retry改写。完整版本见[接入合同](../modules/activity-explanation/integration-contracts.md)。

## 7. 失败与覆盖

所有发现入口都保留技术处置。COLLECTED_WITH_LIMITATIONS 表示材料取得但有具体导航/SQL/选择限制，不是业务分析通过。多个包可关联一个入口；裸E1不能跨包关联。

来源漂移、断引用、字段/schema错、CALL owner混用、字节数不匹配或发布失败均失败关闭，保留已完成上游。容量排除和工具已记录限制是明确未处理范围，不自动重扫、换parser或刷新源。

Activity模型失败、重试或手动新batch不改变本步材料；更改模型、Prompt、并发、日志目录也不使技术材料失效。实际取材profile、插件或来源变化必须另行显式准备新材料。

## 8. 测试与当前成熟度

直接测试应观察完整Controller/Service/Mapper与XML能到达重开后的Packet，多个候选、重复实参、循环、共享方法/入口CALL不串；关闭插件零parser、容量边界不截正文、未知ref/来源错拒绝、空入口合法、技术导出字节一致、模型/JDT调用为0。

当前实现与固定仓库验收已完成：325包、326条覆盖，325 COLLECTED_WITH_LIMITATIONS、1 NOT_COLLECTED，容量排除0；全部完整入口正文及自包含字节计数核对通过。技术索引88,587,381字节、持久化4,508,008字节、引用式材料18,749,377字节、完整Markdown83,666,232字节。来源为[交付核验](../supplements/jdt-persistence-reading-materials-delivery.md)。

新Step06消费尚未实现。本轮文档明确了目标接口，未调用模型，也未用“材料完成”代替Activity质量验收。历史Flow/Capsule算法及版本可在Git `5ceb111` 的本页查阅；新运行不恢复其producer。
