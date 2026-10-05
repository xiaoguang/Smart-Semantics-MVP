# 按入口组装最终技术证据

状态：2026-09-29工程与固定源码技术交付已完成，详见[最终验收](../supplements/technical-entry-evidence-acceptance-20260929.md)。新R4每后端entryId一份自包含JSON，正式CLI／Agent／存储发布、重开、可移植源码位置和artifact查询已验证；最终339份入口文件、51条前端请求处置。运行时地址、全量逐边准确性和长等待根因仍有限制，不以技术交付声称它们都已解决。旧47包属于历史，不覆盖当前R4。

固定key仍为business-flows，目录仍为steps/05-business-flows；owner为analysis.material，不新增Flow/Fact/Proof或业务推理。完整字段、文件和容量合同唯一归属[入口证据详细设计](../modules/technical-analysis/entry-evidence.md)，命令与版本归[运行合同](../modules/technical-analysis/cli-and-runtime.md)。

## 1. 输入

当前命令：

```text
source-analysis --config <technical-v3.yaml> assemble-materials
  --frontend-run <R1> --persistence-run <R3>
```

R1是独立前端索引；R3是持久化运行，其中保存准确后端R2的Step02/03引用；R0是三者相同的来源及排除版本。不能额外传一个不同R2，也不能寻找最近成功结果。

运行组合根重开并核验上述输入，按前端保存范围从R0恢复完整source units。新 `EntryEvidenceAssembler` 只接收已验证不可变对象：前端请求/单元、后端入口/Java索引、持久化索引及明确组装配置；不接收parser、Provider或任意客户目录。它使用现有运行与canonical store，不创建平行运行系统。旧 `CodeReadingMaterialReader` 保留为历史读取器；旧Builder生产代码已退出。

## 2. 内部依次做什么

1. **核对上游。** R1/R2/R3同R0，R3恰好引用所消费R2，所有receipt/版本/有效范围正确。错误来源直接拒绝，不能靠人工确认绕过。
2. **连接HTTP请求与入口。** 将现有method/path匹配逻辑从前端发现器移来，消费已保存请求和后端methodCondition/route。保存唯一、多候选、无匹配、未确定，以及地址条件。这里不再解析Vue或配置脚本。
3. **建立入口记录。** 对后端完整分母逐entryId处理，包括未收集入口；一个Controller的不同映射不合并，一个entry可有多个页面调用者。
4. **读取Java材料。** 依据该entry所属method/call集合取完整正文、有序实参/形参、目标/实现、条件/返回及展开限制。一个方法一份，调用边保留全部；不把调用树再复制一份。
5. **连接Mapper/XML/SQL。** 依准确Mapper身份纳入绑定、statement完整结构、资源/依赖和保存的SQL分析。外部库正常边界不读库正文，错误或未知候选不能伪装成确定SQL路径。
6. **加入前端原文。** 完整保存与每个匹配请求相关的页面实例、事件、组件/mixin、请求包装和必要参数构造单元。R1没取得的单元仍为缺口，R4不能自行补解析。
7. **保存限制及覆盖。** 唯一请求进入入口，歧义请求标候选，无匹配请求留在全仓前端覆盖中。后端失败仍有一份入口结果，不删分母。
8. **核对资源并发布。** 按实际JSON计量；不足时返回具名问题，不截正文。通过后一次原子安装索引、每入口JSON和前端覆盖，重开检查后登记运行结果。

仅第2项有限的HTTP匹配由旧前端模块迁入；其它步骤均组织已保存材料，零JDT/Node/XML/SQL parser。没有任何一步调用LLM解释业务。

## 3. 输出与消费者

```text
entry-evidence-index.json
entry-<完整entryIdhex>.json   （每个后端入口一份）
frontend-coverage.jsonl
现有发布回执
```

每个入口文件包含HTTP入口、页面用法/完整单元、Java方法/调用、Mapper绑定、完整XML/SQL及限制。具体必填字段、自包含范围、去重和路径见[字段合同](../modules/technical-analysis/entry-evidence.md#5-每份入口-json-的字段合同)。

- **技术消费者**从总索引查entryId并读该JSON，不必手工连五份上游文件，也不解析Markdown。
- **artifact/inspect**按准确R4和版本查询，未安装结果不返回虚构路径；按需技术Markdown不再是默认主数据。
- **新Ontology分支（待实现）**只直接消费新R4及同R0原文，不接旧分析。详见[设计](../modules/ontology-recognition/README.md)和[实施计划](../plans/ontology-recognition-implementation-plan.md)。
- **旧Activity/Process路由**未适配本格式；模型初始化前明确拒绝。此前418条Activity消费的是历史Step05 v1，不得把旧版消费说成R4接通；本体不补这项兼容。
- **历史reader**继续按准确v1/v2重开旧Packet、旧Markdown和上游，不重写旧选择、ID或覆盖。

自包含意味着本文件保存已声明范围的完整正文；不是工具已经证明每个Vue入口都能连到SQL。不经过SQL的后端入口、未匹配页面、正常外部边界和工具未知均合法存在，但状态必须区分。

## 4. 原v1/v2与本轮迁移

| 当前实现 | 本轮调整 |
| --- | --- |
| HEADER/PACKET/ENTRY_COVERAGE/FRONTEND_COVERAGE混在code-reading-materials.jsonl | 新producer v3写入口目录＋entry文件＋前端覆盖；旧文件只读 |
| entryId排序后按入口数/Markdown容量合并 | entryId逐一生成，排序只为存储稳定 |
| 后端包先形成，再按余量追加前端 | 该入口全部已取得前后端单元一起组织，不能因其它入口挤掉前端 |
| 只消费前端已存唯一ENTRY_LINK | 读取独立R1请求，在R4生成关系；歧义/无匹配不删除 |
| 一个packet内短SourceRef，跨包可能重名 | 入口文件使用明确单元身份和sourceRefs，禁止裸短号跨入口关联 |
| 字节数按技术Markdown计算 | 按实际canonical JSON与store预算计算 |
| 原子store只接受固定单文件、默认64payload | 为新producer增加manifest约束的变长文件集合及预算；非任意路径通配 |
| reader按选择引用恢复上游正文 | 新入口文件自包含；读取器验证已保存集合，不重新组装 |

这不是恢复旧Flow/Capsule编译器。新生产使用 `EntryEvidenceSet`、`EntryEvidencePublisher` 和 `EntryEvidenceReader`；旧Packet由原Reader按历史版本读取。两个格式不共用无版本分支，也不把旧字段默认为新格式字段。

## 5. 异常与完整性

所有已发现entryId必须对应结果。Java NOT_COLLECTED保留原原因；已确认外部调用不作为代码缺失；未知目标、查询失败、SQL部分解析及前端地址条件分别保存。一个成功入口不代表全仓完成。

来源错、断引用、call owner错、资源超限、写盘失败不能发布成功集合。已有R1/R2/R3不删除，也不为组装失败重新运行parser。报告先保存再结束运行；磁盘完全不可写时只返回实际可保存范围。

容量不够不触发删条件、短摘要或另开混合包；调整显式配置后创建新R4。业务模型失败也不得回头改这份技术证据。

## 6. 验收

以正式CLI、真实Agent/store/reader验证：
- 一个entry完整Controller→Service→Mapper/XML和前端材料进入同一文件；请求及参数未知不补造。
- 不相关入口分开；共享方法去重但每入口calls不串；递归有限引用。
- 后端无前端、前端无匹配、多匹配、关闭前端、持久化关闭和7个历史失败入口均保留真实状态。
- 新排序字段及getQueryParams必要单元保留；具名错边不再进入确定材料；正常外部边界不误报。
- 超过64入口、错误身份/路径/上游、容量/写盘失败、历史v1/v2重开及新业务入口拒绝。
- 前端请求和后端入口分母各自闭合，不预设新结果必须仍为339/51。
- JDT/Node/SQL解析/业务模型启动次数均0。

## 7. 当前实现事实

历史2026-09-18后端材料为325包/326入口、1个导航缺口，后来用于418条Activity；保留[历史验收](../supplements/jdt-persistence-reading-materials-delivery.md)。
2026-09-28技术v2旧R3为47包/339入口/51前端处置，采购查询材料已保存，2条前端请求容量未选，Java准确性尚未通过，见[固定源码验收](../supplements/technical-analysis-fixed-source-acceptance.md)。
2026-09-29最终四命令及R4已按[最终验收](../supplements/technical-entry-evidence-acceptance-20260929.md)交付。此前曾出现R2发布失败、R4绝对URI及位置投影问题，属于已记录的中间失败，不能仍写成当前未完成；也不能删除这些历史失败。具名准确性检查和技术交付通过，不等于全量每条调用关系均获语义证明。新本体consumer及其自动选材/语义验收尚未实施。
