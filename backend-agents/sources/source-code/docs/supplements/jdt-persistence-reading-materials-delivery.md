# JDT、MyBatis 与 SQL 阅读材料交付核验

状态：2026-09-18，8项实施工作及固定仓库第1–5步验收完成；代码交付按实施计划办理。对应[实施计划](../plans/jdt-persistence-reading-materials-implementation-plan.md)。产品模型调用为0；本结论不表示所有入口导航成功或业务解释质量已验证。

## 已验证与待验证的边界

| 项目 | 当前结论 |
| --- | --- |
| 官方工具可行性 | 已通过，[实验结果](persistence-tool-feasibility-result.md)单独记录真实 XML、解析范围与限制 |
| 新生产链 | JDT 索引 → 可选 MyBatis/JSqlParser 材料 → 入口阅读材料，直接模块、保存/重开及 CLI 接线测试通过 |
| 旧生产链清理 | JavaParser、五图/Fact/Proof、严格 Flow/Capsule/M10 的生成代码退役；历史读取合同保留 |
| 历史读取与安装边界 | 10 项直接回归通过：五图读取链、Fact/Proof 保存内容、损坏与前驱检查、禁止新安装 M10 但保留 M11 |
| 本地 CI | 最终 `retirement-full-ci-2.log`：469/469 测试通过，Spotless、SpotBugs、PMD 通过，BUILD SUCCESS，用时 3 分 11 秒。应用与测试 Java17，质量宿主 JDK26；未启用真实 JDT IT |
| 固定仓库新材料 | 正式CLI退出0、FINISHED；325份阅读包、326条覆盖：325 COLLECTED_WITH_LIMITATIONS，1 NOT_COLLECTED |

## 验证环境

正式编辑目录不变。由于同目录 IDE 构建曾在 Maven 完成后改写 `target/classes`，验证使用忽略目录中的同输入副本 `.workspace/retirement-verification-layout/source-code`；同步后用校验和模式核对差异。该副本只用于构建验证，不是另一个实现来源。应用编译、测试仍使用 Java 17，格式化及质量检查使用独立 JDK21+ 宿主。

原始日志保存在 `.workspace/persistence-tool-feasibility-20260917/`。`retirement-isolated-history-6.log` 为 10/10 通过记录（49.032 秒）；`retirement-spotless-apply-2.log` 为最终格式化记录；`retirement-loopback-tests-1.log` 为模拟 HTTP 的 3/3 通过记录（7.583 秒），没有外部模型调用。最终 clean CI 已退出 0，日志为 `retirement-full-ci-2.log`；469 项比首轮多出的两项是审查后的 Mapper 边界回归。

真实验收首次启动在打开尚不存在的新 store 目录时退出，未启动 JDT。创建该独立目录后以相同配置启动，保留两次日志；当前运行 `analysis-run:5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac`，不覆盖旧运行。此启动环境修正不涉及生产代码或真实模型。

扫描期间的一个耗时样本：`/depotHead/batchAddDepotHeadAndDetail` 展开 433 个方法、1,437 个调用，用时 171.238 秒。一次线程快照显示应用等待 JDT `implementation` 响应，JDT 处于 `Publish Diagnostics` 的源码 reconcile/内部编译过程；没有启动客户 Maven、Gradle 或应用。快照保存在独立验收目录中。这只能定位当时的等待位置，不能证明该入口全部耗时由诊断造成，也不据此增加本轮优化范围。

## 审查修正

合法 MyBatis 同文件 `databaseId` 变体不能因为语句 id 重复而使 Step02 失败。目录只需提供 namespace/id/kind 线索与实际来源；具体数据库变体由 Step04 从原 XML 保留。无需自研 XML 定位器或增加业务规则。

存储层插件缺省/关闭时，Spring 项目没有 MyBatis 或 Mapper 也应保留 Java 阅读能力，不能把缺少可选框架当作输入损坏。针对这两项的直接 RED 已观测；目录现使用整份 XML 来源并按 `(namespace,id,kind)` 合并同资源线索，且不再以 POM MyBatis 信号作为目录前置条件。`review-mapper-green-2.log` 记录 40/40 通过（25.470 秒），包括上述边界、真实模块存储、CLI 和 Step04 XML/SQL 回归。原 namespace 字面量来源断言同步为整份 XML 的逐字节相等检查，没有改成仅检查引用非空。

质量检查不以宽泛抑制替代值对象契约。Step04/05 每个被排除的记录类型都在紧凑构造器中复制集合（及适用的映射）；SpotBugs 4.10 不能识别 Java 17 自动生成 accessor 上的该模式。`MapperXmlResourceView` 是唯一保留可变对象的逐类型排除：它只在同一运行、同一冻结来源内共享已安全解析的 DOM，消费者在 MyBatis 变换前克隆节点。该排除不意味着 DOM 可跨运行、跨来源或由转换操作直接改写。

配置命令的解析仍使用同一组选项、值转换和按模式的拒绝条件。为通过 PMD 的方法复杂度门槛，`Arguments.parse` 只负责前缀和逐项解码，已解码的同一记录再交给私有模式校验方法；不改变命令、允许/拒绝的选项组合或执行路径。

## 历史数据保护

旧冻结源码、326 条 Activity、原 M10、过程、模型 DRAFT/REVIEW 与日志均不覆盖。最终构建代码已通过生产 canonical store 和 typed reader 重新打开 326 份 M10 材料、326 条材料覆盖及 326 条 Activity（`historical-reopen-final.log`，退出 0）。正式 CLI 的历史纯重渲染退出 0：9,725 字节、SHA256 `59eb3f7e930f42f72a7206afcb377c31a9a1e098b83f12ddad113c3c3f110124`，没有 Provider。

旧 Java 索引、Flow、Capsule、M10、Activity 和 Activity coverage 的 SHA256 已再次与基线逐项比较，均保持原值。`more-findings.md` 保持 SHA256 `59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`。

## 清理与保留

相对实现起点，删除 42 个退役生产类和 63 个相关测试文件。删除的生产职责包括 JavaParser 引擎、严格五图生成、Fact 候选枚举／Proof 构建、严格 Flow/Capsule 生成及 M10 Builder；不再允许它们参与新生产安装。旧 wire 的 DTO、Schema、严格读取器及确定性历史报告渲染保留。

旧测试中的有效断言迁入当前路由、真实存储和历史读取 fixture。新材料发布、重开、参数、候选、容量、零模型调用及历史损坏拒绝的测试均进入最终 469 项检查。删除测试文件的数量不表示删除了同等数量的行为断言，也不以新测试数量证明真实仓库材料完整。

新生产保留现有 JDT LS/Core 导航与缓存，由 `analysis.persistence` 负责可选 XML/SQL 材料、`analysis.material` 负责一次组包及引用式保存。没有增加新的行业解析规则。运行数据、原模型结果、凭据和本机工具链不进入本次提交；仓库根无关 `docs/research/` 不纳入交付。

## 固定仓库真实验收结果

源码提交为`8c30ce7861570458920175e200bb2a6442713580`，冻结snapshot为`8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c`。新运行为`analysis-run:5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac`，第5步receipt为`1250eb155b69a6b06c690ab787efa0898a63c3248c7012c8c2ea0f9d77f9a39c`。实际CLI总耗时1906.06秒（31分46秒）；导航收集约28分31秒，导航发布22.145秒。没有独立记录的第4/5步耗时不作推算。

| 实测项 | 结果 |
| --- | --- |
| 导航 | 326个入口最终有处置；325取得上下文，1失败；9,189个方法、51,675个按入口保存的调用 |
| 查询复用 | 28,033次实际RPC/唯一查询键，115,250次缓存命中；关闭时的SESSION_SUMMARY已保存 |
| 插件启用 | 61份XML、573条语句、572条Java绑定、573份SQL分析；187 PARSED、162 PARTIAL、224 UNSUPPORTED |
| 同索引插件关闭 | DISABLED，工具/资源/语句/绑定/SQL分析均为0；同一snapshot、导航引用与Java索引，无再次JDT |
| 材料 | 325包、326条覆盖；容量排除单元0；完整入口正文存在，保存自包含字节计数全部匹配 |
| 文件体积 | 第3步索引88,587,381字节；第4步索引4,508,008字节；第5步引用式JSONL18,749,377字节；完整Markdown导出83,666,232字节 |
| 停止边界 | inspect显示reading materials完成，Activity/过程/报告均未执行；Provider调用0 |

失败入口为`/plugin/files`（`PluginController#getPluginFilePaths`），definition查询在30秒边界失败，保留为NOT_COLLECTED，不重试、不伪造材料。325包均含导航限制；SQL的PARTIAL/UNSUPPORTED仍保留原XML和原因，不能当作全部SQL已完整解析。关闭/启用插件的只读对照分别耗时6毫秒和1691毫秒，这不是完整CLI阶段耗时。

真实日志与状态保存在[独立验收目录](../../.workspace/jdt-persistence-acceptance-20260917/)。[完整材料](../../.workspace/jdt-persistence-acceptance-20260917/reading-materials.md)由正式artifact命令重开导出；[同索引开关及材料检查](../../.workspace/jdt-persistence-acceptance-20260917/persistence-toggle-check.log)退出0。

三个自动取得的材料样例分别为[新增单据](../../.workspace/jdt-persistence-acceptance-20260917/reading-sample-1.md)、[修改单据](../../.workspace/jdt-persistence-acceptance-20260917/reading-sample-2.md)、[审核/反审核](../../.workspace/jdt-persistence-acceptance-20260917/reading-sample-3.md)。对应443/440/242个方法、1468/1468/576个调用及58/58/28条持久化语句。这是共享通用入口的代码材料，不是三份新采购/销售/调拨业务流程。实际新增材料中，`DepotHeadMapper.insertSelective`已关联其Mapper XML及SQL分析；多个Java候选仍保留，未擅自唯一化。

本轮JDT LS实际构建为`1.61.0.202609021834`，旧索引记录为`1.61.0.202609031315`；Core均为`3.47.0.v20260828-1141`。旧记录没有RPC计数，不虚构加速比例；也不把两个构建的扫描耗时差归因于本次实现。新第5步材料尚不接入Activity，第6步消费设计留待后续讨论。
