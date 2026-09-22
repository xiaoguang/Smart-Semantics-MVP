# FrozenAnalysisCorpus

## 目的与Interface

统一打开Activity、其来源材料与同源冻结文本，让目录/选材/过程不再触发上游生产。它是BusinessProcessDiscovery内部Module，不是公共检索框架。

内部读取职责保持：

| 查询 | 返回 |
| --- | --- |
| activities / activity(id) | 全部导航 / 完整ReviewedActivity |
| statement(handle) | 原Activity字段与确切owner |
| source(ref) | 所属材料实际保存的完整片段 |
| files | 冻结文件键/相对路径/类型/行数 |
| read(fileKey,range或WHOLE_FILE) | 原文、实际范围、完整性与限制 |
| search(fileKeys,literal,context) | 字面量命中、上下文及未命中/省略说明 |

模型不能提交可执行命令或宿主Path。使用现有VerifiedSourceTextReader读取注册的冻结bytes；不导航、不查JavaCodeIndex、不读变化的checkout、不解析或执行客户代码。读取已冻结文件不是重跑JDT。

## 当前来源与目标双来源接入

当前代码接受旧Activity/coverage、M10 BusinessMaterialBuildResult与同源verified inventory。已有FrozenProcessSourceCorpus以稳定UTF-8相对路径顺序提供F键；一基闭区间读取保留LF/CRLF与末尾无换行文本。整文件或实际覆盖整文件才标complete。

目标Activity coverage v3内的materialSource、packet映射是来源manifest，**不新增公共manifest文件**：

- LEGACY_BUSINESS_MATERIALS：严格按历史Activity/coverage/receipt打开其M10材料。不得给旧326条结果回填新SQL、改sourceRefs或升级业务解释。
- CODE_READING_MATERIALS：按materialSource完整Step05 publication与sourceRunId重开code-reading-material-set-v1；原packet/sourceRef映射和所选slice来源确定新Activity的实际依据。

配置显式discriminator、精确版本/basis和输出owner由[Activity集成合同](../activity-explanation/integration-contracts.md)维护。不能按字段形状猜类型或碰到失败回退另一来源。新材料接入尚未实现，不能把当前M10构造签名视为通用Step05接口。

两类材料都提供同源只读视图；SourceRef在其材料/packet中唯一，跨包必须连同owner解析。Step05的source:1等内部键与模型请求S键不同，必须使用已保存映射，不能裸编号拼接。所有原始来源、覆盖与业务文本保留。

新Activity可能只读完整statement结构投影及依赖，而其Sref指整份XML原件。来源对象与私有已读unit/scope分别保留，不能因此声称Activity已读整文件。Step07按SOURCE_REF取整份XML仍受现有CHECK与容量合同；可通过既有冻结file range/literal search选具体原文，不新增精确行号取证模块。不能保证任意大XML自动装入一次过程请求。

## 程序保证与下一消费者

用输入checkpoint对应策略重开来源，输出策略仅用于新运行发布。Activity与材料可属于不同run，但源码basis须一致。磁盘重开校验完整引用、字节/hash/schema/ref/basis；进程内传已验证immutable view，不在每个内部查询重新生产。

为既有Activity可引用字段分配statement handle；保留原字段文本和owner，不建立Proof层。没有进入候选的支撑/独立/未分类Activity仍确定性投影其既有知识到Discovery result。

Assembler取得真实Activity与所选源码，封闭包后交模型。Publisher只验证结果和上游receipt，不回读原文提炼内容。完整Activity与新读原文相冲突时，过程可记录纠正，原Activity不变。

## Gap、fatal与直接验证

合法未命中、非文本或已读材料不足是reading gap，模型具体说明UNRESOLVED。未知文件、越界/逃逸路径、损坏输入、错owner、跨snapshot和未知statement/ref是fatal，不伪造空结果，不自动修复或重扫。

旧跨候选、XML/Vue读取、同源和零扫描已有实现；本次仅新增双来源目标。后续定向测试覆盖两类manifest严格选择、packet-local来源冲突、旧Activity字节不变、新Step05打开且上游调用0、同源验证和完整字段保留。
