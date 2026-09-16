# 跨候选阅读：实施与验收记录

状态：核心代码、容量及阅读检查的私有合同修正均已通过离线验证和最终本地 CI；真实实验尚未完成。本文只记录已经验证的事实，不以调用启动代替业务质量通过。

## 固定输入与保护范围

| 输入 | 已核对内容 |
| --- | --- |
| Activity 批次 | `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e`；326 条已审 Activity 与完整覆盖 |
| M10 来源运行 | `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`；有效材料可独立读取，不要求旧运行整体成功 |
| 原始目录批次 | `analysis-run:4125a702ec8489a65792e93d5d77cd1630b7933c3e34f928b93c9dba85ac3224`；完整目录合并任务的 DRAFT 与 REVIEW |
| 正式代码目录 | `backend-agents/sources/source-code`；本轮不在旧 private checkout 编辑 |
| 基线 | `6cfc83d`；设计保存提交 `414a54b`；实现分支 `codex/cross-object-process-reading` |

历史材料、模型记录和产物只读。`docs/supplements/more-findings.md` 保持原字节；无关根目录 `docs/research/` 不进入交付。本轮不重新运行 JDT、Builder 或 ActivityExplainer，也不生成九章。

## 已执行的直接验证

定向测试使用宿主 Java 17 和模块本地工具链：

```bash
mvn -t .mvn/toolchains.local.xml \
  -Dtest=FrozenProcessSourceCorpusTest,BusinessProcessReadingPipelineTest test
```

| 验证 | 实际结果 |
| --- | --- |
| 冻结文本读取 | 2 项通过：排序目录、准确行段、完整原文、字面量搜索、范围保护、CRLF、末行无换行和空文件 |
| 新阅读顺序 | 最新 Pipeline 9/9 通过：完整包、补读、context、跨包来源归一化、原始结果字节保留、损坏结果拒绝复用、轻导航、无损完整 Activity、共享 Schema 允许值及 CHECK 最终成员约束，以及另一候选 fatal 时成功 pair 仍立即保存 |
| 单次决策保存和读取 | 4 项直接行为测试通过；新版完整过程结果缺阅读包、映射或批次身份不符时明确拒绝，不增加源码 Proof 门槛 |
| 核心生产代码编译 | 稳定 Java 17 `clean test` 完成生产及测试编译；不是以编译通过代替语义验收 |
| 过程执行配置 | 新 v3 写入接口直接行为及正式离线入口通过：完整来源/目录谱系、原样问题、幂等写入、输入变化冲突；历史 v2 严格读取保留 |
| 正式 CLI 参数与固定输入 | 参数 5/5 通过；真实 Agent 从原 Activity、旧目录及冻结来源完成离线五文件发布：326 条处置、24 个候选，CLOSED/PARTIAL；替身输出不作为业务质量结论 |
| Canonical 发布与重开 | 13/13 通过；producer v3、五个公共文件及历史读取合同保持分离 |
| 最新定向回归 | Discovery 30、Prompt 5、Pipeline 9、单次决策 4、语义指纹 1，共 49 项零失败、零错误；严格响应 Schema 的 nullable 字段及最终成员检查包含在 Pipeline 中 |
| 容量最小修正 RED | 两项直接行为测试已实际运行：2 failures、0 errors；分别捕获 Activity 的两个额外重复字段，以及 CHECK 缺少共享引用定义。JSON 对象仍沿用 canonical 字段排序，不改变业务数组顺序 |
| 容量最小修正 GREEN | 三个直接测试类共 40 项通过，零失败、零错误；仅删除两项重复投影、共享原完整允许值集合，certainty 保留原表示。新增测试的对象键顺序与 fileKey/路径混淆已按现有合同校准，不要求生产改协议；Astra/ultra 的有限静态审查未发现交付级问题 |
| CHECK 最终成员合同 | 定向 RED 实际捕获 3 项失败、0 errors；最小实现后五个直接测试类 49/49 通过，包含完整阅读包、null 说明字段继承、Prompt v2、单次决策和语义指纹。新增约束只作用于 CHECK 的最终非空成员集合，不改全局选材 |
| 完整本地 CI | 包含容量及最终成员合同修正的干净构建通过：561 项，零失败、零错误、2 skipped；Spotless、SpotBugs、PMD 全部通过，10 分 49 秒，进程退出码 0。日志为本任务忽略目录中的 `inspection/reading-final-membership-local-ci.log`。CLI 四处相同运行 ID 转换复用一个私有函数，参数、错误码及 cause 不变；编译及质量工具仍绑定原 Java 17 工具链 |
| 原始材料字节对照 | Activity、coverage、M10、旧目录 pair 与 more-findings 全部与修改前校验值一致 |

RED 只证明测试能捕获旧行为，不代表新流程已经实现。后续验证应更新本表中的当前结论，不累计每次小修复日志。

真实请求容量按绑定会话核验，不按 API 宣传页或 JSON 字节数推定。本机当前 Codex 模型元数据中 Luna 的 `context_window` 为 272,000、有效比例为 95%；这只是本次环境观察，不是写死的框架容量。真实实验前仍需核对实际选材请求、完整阅读包与完整 REVIEW。不得静默截断，也不增加费用预算门槛。

## 真实实验与交付

| 实验 | 状态 | 输入隔离 |
| --- | --- | --- |
| A：有提示 2–3 个候选小样 | 全局响应已保存，但目录更新失败；批次 `analysis-run:01e9f1430470f04110e80e1f6d2c93e5f53ba6d2741d112edf88f71961193bc3`，未调用阅读检查或过程两轮 | 原始目录、326 Activity、冻结文本；不给答案 ID 或源码路径 |
| B：无提示全仓 | 全局选材通过；独立批次 `analysis-run:711d8936bd16a3a9749a5d94b28e5680059d738b1dcdb3c03696fb15a40e9888`，无关注问题、无复用来源，实际 27 个候选。容量修正后新批次 `analysis-run:799c88aca45a857c5e60df72b627f953cae7cd9ddae9653ef44ce87970d98cbb` 的两个 CHECK 已返回，但空最终成员列表被程序拒绝；未执行补读或过程两轮 | 同一原始输入的新独立批次；不继承 A 的候选、片段、包或回答 |

使用已批准的真实 Luna/high、Pro、并发 4、超时 3600。每个完成任务保留实际请求、决策、阅读记录、封闭包及完整 DRAFT/REVIEW。真实实验使用本次验证通过的固定 JAR；忽略目录中的薄验收驱动只调用既有阶段，并可在模型调用前导出实际请求检查原文及容量，不另建算法或公共入口。

最终成员合同修正后的固定 JAR 与本次 CI 打包结果逐字节一致，SHA256 为 `dd52c2e80f526b5871e3ed25f76af82959343cdc28632f8370b1540c8810e6f7`；容量修正及更早的旧 JAR 均保留用于对照。临时暂停的 IDE Java 构建器已在 CI 结束后自动恢复，实际状态 S。离线预览对 Provider 加仅供验收的失败保护，任何未匹配复用导致的模型调用都会先被阻止，不改变正式 Provider 或任务匹配算法。

最终记录须补齐：两轮运行身份、候选变化、补读内容、关键判断变化、各类实际调用数与耗时、复用数和未解决范围。正式五文件为 `business-processes.md`、catalog、coverage、`source-refs.jsonl`、`sources.md`。

B 的模型自主新增“库存单据与财务结算联动”“商品主数据与库存成本维护联动”“租户开通、用户配置与权限关联”，保留全部原候选。离线预览批次 `analysis-run:fae6b3d375fe01cdbefa0c36ba18a8ebf48f52ec623bf7c7862a6de765bfe596` 显式复用 B 的选材响应，零 Provider 调用导出两个完整 CHECK 输入。租户候选包含 12 条完整 Activity、769 项陈述、9 个实际来源片段；库存与结算候选包含 16 条完整 Activity、1165 项陈述、11 个来源片段。输入加实际 Schema 的两个编码估算分别为 299,901/318,522 与 377,417/402,160 token，超过本次观察的有效上下文。没有继续发出 CHECK，不截断材料；有限设计核查聚焦导航与机器编号的重复投影，完整 Activity、规则和选中原文必须保留。

容量修正后的零模型预览批次为 `analysis-run:0e8357591a106fdd9fe35683d73baa1f37f9496719f89b5158914a7cfd69143c`，显式复用 B 的同一成功选材任务。与真实 B 的修改前 `initial-reading-7f9ec5c80f99` 精确对照，CHECK 和补读前 DRAFT 的四份输入均只移除两个重复字段；所有原始字段、数组顺序、来源、statementDirectory 逐项一致，展开 `$ref` 后 Schema 约束完全一致。输入加 Schema 的估计如下（o200k / cl100k）：租户 CHECK 183,201 / 197,197，库存 CHECK 213,541 / 230,678；租户补读前 DRAFT 90,575 / 95,188，库存补读前 DRAFT 138,475 / 146,089。不是实际补读后的最终包或 REVIEW；后两者需另行核验。当前模型缓存未提供 `max_output_tokens`，不能把 Java 的输出字节上限当作 token 上限；Prompt、Codex 封装与实际生成空间仍分别核对，不宣称编码估计就是服务端精确剩余空间。

最终成员合同修正后的零模型预览批次为 `analysis-run:f825b65b3795d045bef2dcdc1a5342438bf4e456991d7145e97eedf707720c9e`，使用最终 CI 的固定 JAR，显式复用 B 的原选材结果，无 focus、无 A 输入。与容量修正后的预览逐字节对照：两个候选的 CHECK、补读前 DRAFT 输入和过程 Schema 全部一致，CHECK Schema 仅新增 `activityUses.minItems=1`；全局选材仍匹配复用，真实模型调用为 0。该预览不构成成功的真实 CHECK、补读或过程验收。

A 的一次真实选材响应提出“原始需求、后续订单、实际收发与结算回写”“订单退回与退货单独立办理路径”“两仓之间的物品调拨登记、审核与核对”三条新候选，以及 38 项首读请求。但替换旧库存单据候选后，`activity:72453762d8944e7428fc3db4aacb8920435e5c2f810a39ad21d6a2b6e364d761`（批量复制入库或出库单据并联动库存）既不在剩余候选中，也没有显式非成员处置；`changedActivityDispositions` 是空数组。按已批准合同，目录更新因此抛出 `PROCESS_READING_REMOVED_MEMBER_DISPOSITION_REQUIRED`。请求及原始响应完整保留，不人工修订模型输出、不自动重试、不据此宣称业务质量通过。无提示实验是已批准的另一独立实验，不续接这个失败批次。

## 当前未完成项

真实 B 的两个 CHECK 分别提出 16、15 项实际冻结文件补读请求，但均返回空 `activityUses`。原 Schema 允许空数组，程序却要求完整非空最终成员，提示词也未明确“未修改时仍须返回完整集合”；这是私有合同不一致，不代表 JDT 或冻结源码不足。Astra/ultra 已裁决按既有目标对齐并完成最小实现：CHECK Prompt 升 v2，`activityUses` 的 Schema 增加 `minItems: 1`，`contextActivityIds` 返回完整最终集合；三个 null 文本字段继续继承，处置继续增量。不新增阅读轮次，不人工修补原始响应。两个决策原文保留；其单次请求记录的 COMPLETED 不等于合法候选或已审过程完成。

本轮至此实际产品调用为 4 次：A 选材 1、独立 B 选材 1、B 阅读检查 2；过程 DRAFT、REVIEW、归并及新正式发布均为 0。修正后再次执行两个真实 CHECK 的明确授权仍待答复；未自动重试，未重新扫描或解释 Activity。

已完成启动任务的日志时间范围为：A 全局选材 4 分 19 秒、B 全局选材 4 分 23 秒、B 两个并行 CHECK 合计 1 分 59 秒。这是 CLI 启动、读取、等待、模型及保存的整体范围，不冒充单独 RPC 耗时或全仓结束时间；实际补读、过程两轮与归并尚未取得测量值。

核心阅读及正式入口已经实现，固定 326 条输入的正式离线入口、新私有合同的 49 项直接回归及最终干净本地 CI 均已通过。未完成项为真实实验和实现 PR。Git SSH 推送可用，GitHub 连接器没有可用的仓库安装授权，私库读取返回 404；凭据读取授权仍待明确答复，不绕过自动审批拒绝。

固定输入正式 CLI 的输入策略接线已修正：只增加过程输入读取器，原输出策略保持不变。离线批次 `analysis-run:e57720880bc22ee9a06b8d22192d068e28847c6ec822d884ac08fc31c78b9d3c` 已完成新阅读链及五文件发布。实际阅读包保存了完整 Activity 及 Java/XML/Vue 冻结原文，未运行 JDT 或重新解释 Activity。

实际选材输入容量检查发现导航层曾携带全量长 statement handle，占约 160 万字符。有限设计裁决已落地：导航仅保留 statementCount，完整 Activity 与候选包的可引用陈述保持不变。真实序列化请求与 Schema 的本地编码估算由约 96 万降至约 19.2–20.3 万 token；这是两种编码器的估算，不是 Pro 服务端精确计数。候选阅读包及完整 REVIEW 仍须分别检查，不静默截断。只读审查发现的两项问题均已修复并经直接测试验证：成功 pair 在完成队列中立即保存；阅读检查的三个可选候选说明使用 required nullable 字段。

测试编译曾出现大量未改动基础类不可见，不能算作业务流程的失败判决。只读排查确认 VS Code Java 构建器与 Maven 共用两处 `target` 输出；本次验证临时暂停该后台构建进程，构建结束后已恢复，不修改共享 POM 或持久编辑器设置。

格式化工具兼容性单独处理：现用 google-java-format 1.36.1 在 Java 17 宿主报运行异常，在显式工具 JDK 26 上成功。其官方最低运行版本为 JDK 21；完整质量构建因此显式选择 JDK 21+ Maven 宿主，应用编译及测试仍使用本地 Java 17 工具链，不修改应用或 POM 的目标版本。[官方版本说明](https://github.com/google/google-java-format/releases)

计划完成须同时满足程序正确性、真实业务效果与交付，不以 coverage 闭合或测试通过代替业务理解。
