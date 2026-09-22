# ActivityMaterialProjector：完整单元的模型投影

状态：目标设计，尚未实现。它隐藏模型输入的去重、ref转换、XML读取与计量；不改变Step05 canonical材料，不调用JDT、PersistenceAnalyzer或SQL parser。

## 1. 输入与外部 Interface

概念Interface为 `project(Packet, ActivityReadingProfile) -> ActivityMaterialView`，随后 `materialize(view, UnitSelection) -> ActivityReadingPacket`。二者是Activity内部职责，不增加RepositoryAnalysisAgent方法。Packet必须来自已经验证的CodeReadingMaterialReader；profile在模型batch开始前固定。

程序持有完整Step05 reference、packetId、entryId、methodKey、statementRef、SourceReference映射与原文。模型只看本包短键：

| 键 | 含义 |
| --- | --- |
| E1…EN | 本包入口；最终由程序映射全局entryId |
| M1… | 一个完整MethodCode，含真实名称/声明和完整正文 |
| C1… | 一个入口所属调用发生点；相同物理调用在不同入口可有不同展开结果 |
| X1… | 一个statement变体及其完整依赖；databaseId候选不擅自合并 |
| S1… | 允许引用的原件来源；XML块没有精确行号时指整个原Resource |
| U1… | 实际存在但尚未取为正文的单元导航键；不能作为已读sourceRef |

这些编号是packet-local稳定排序映射；不是业务身份。不同packet的E1/M1绝不相连。slice共享本包映射，不重新随完成顺序编号。

## 2. 先确定性去重，再判断能否直接阅读

完整模型视图保留入口trigger、全部选中方法原文、必要有序调用/参数表、完整statement依赖和全部限制。去掉的只有重复表示及程序信息：

- 调用树与逐call全量长ID列表用一份短调用表/导航关系替代；保留所有候选、实参/形参、条件位置与返回使用线索。
- 方法完整源码只出现一次；正文已含控制表达式时，controls/exits只作短定位导航，不重复整段源码。
- 默认发送完整XML statement及其依赖，不同时发送整个Resource和同一statement；源码Resource原件仍可回查。
- SQL AST默认不随每个statement重复发送。模型可请求已保存的该statement AST；提供时连同PARSED/PARTIAL/UNSUPPORTED、变换和完整XML，不能用AST代替原条件。
- 全局ID、hash、receipt、绝对/仓库路径、行号、工具日志、Provider/并发/预算配置不进入业务请求。方法/参数/表列名称是理解代码需要的原文，不按字符长度压缩或改名。

去重是无业务取舍的呈现变化；任何未发送的非重复单元都有导航处置。不能通过删除注释、异常、return、调用候选或“像工具函数”的方法来宣称无损。

## 3. XML statement 与 include 依赖怎样完整取得

当前Step04保存了Statement.xmlSubtree和DependencyRef，但sql片段/resultMap正文只在Resource.rawSource中。因此投影遵守以下明确接线：

1. statement直接取保存的完整XmlNode，按原顺序渲染标签、属性、TEXT/CDATA和children；标为“冻结XML结构投影”。
2. 对依赖，只在Packet已选择的Resource.rawSource上，复用现有安全JAXP/MyBatis节点读取能力；资源在本次投影中只读一次，缓存只随本包/不可变材料视图存在。
3. 按原namespace/id和保存依赖取完整sql/resultMap节点；继承resultMap、静态跨资源include、selectKey及嵌套依赖一并取得。循环/动态ref/重复候选继续保留，不用第一个覆盖。
4. 不调用Step04分析器、不重跑JSqlParser、XMLIncludeTransformer执行流程、OGNL或客户配置。原include引用和完整被引用节点并列，模型能看到调用位置与条件。
5. 标准DOCTYPE与外部读取禁用使用相同安全策略。安全设置失败为非retry错误；缺失依赖/无法定位为明确未取得范围。
6. 原来源仍指保存的整份Resource；不伪造节点精确行号，也不把DOM重排文本说成逐字原文。用户查看原件时读取Resource原文。
7. 不能安全投影某依赖时，可提供完整已保存Resource作为完整单元；仍超容量则报依赖/容量未处理，绝不以SQL AST填补。

这只是读取已有冻结XML以形成模型视图，不建立新XML/SQL解析器或改变Step04 schema。资源缺失不能从当前checkout、网络或其他snapshot补齐。

## 4. 原子阅读单元与闭包

Java原子单元是完整方法/构造器/initializer/lambda记录；无body声明仍完整保留。SQL原子单元是整个statement及相关include/resultMap/selectKey依赖；`insertSelective`列与值所在两组条件必须在同一单元。

调用闭包指本次业务阅读所需的完整入口/调用方方法、被选方法、调用发生点、真实参数和返回/异常使用、该调用的候选/限制及XML依赖。闭包不等于全图传递闭包，也不声称已证明所有数据流。Java可沿已有调用/controls检查结构引用；模型判断哪个branch和variant适用。

如果给完整共同方法带入多个variant，REVIEW必须按本slice的触发与参数检查哪些条件适用；不能因为源码出现在包中就把其所有规则复制给每个slice。无法判定适用范围时保留具体NEEDS_CONFIRMATION/scopeLimitations。

## 5. 实际容量与输出

`ActivityReadingPacket`私有v1包含：entryKeys、unit/source allowlists、selected完整单元、调用与参数导航、slice purpose/scope、已读/未读范围、限制。来源完整映射留在私有程序envelope，不混入模型内容。

容量预检使用将要提交的真实序列化Prompt、Schema、输入及配置的输出/协议/推理预留。输入UTF-8字节上限精确检查；context容量使用已有适用的离线计数或保守字节估算，与配置声明的上限比较。它是容量安排依据，不是服务端精确fit保证，不要求证明隐藏tokenizer或推理开销。每次DRAFT和REVIEW前复核，REVIEW计入完整实际DRAFT，不能裁去条件来通过预检。

缺少必需容量配置、数值非法或实际可见输入明显超过硬限制时，在请求开始前返回对应容量原因，实际模型调用为0；不知道隐藏实现不构成拒绝理由。Provider实际容量拒绝保存为PROVIDER_INPUT_CAPACITY_EXCEEDED，不按瞬时故障重复同一输入。计量方式/结果与projectionVersion参与阶段输入fingerprint；技术Packet.selfContainedUtf8Bytes保持其原技术Markdown含义，不拿它当模型容量。

## 6. 下游、失败与直接测试

输出交ActivityReadingCoordinator和ActivityExplainer。同一冻结Packet/相同profile/相同选择的投影确定性；保存后重开得到相同实际请求文本，不能靠旧model会话记忆补材料。

来源漂移、未知单元、XML安全配置、闭包断引用、映射冲突为输入错误；方法或完整闭包过大为容量未处理；未解析JDT/动态SQL继续保留原限制。retry不会改变这些来源事实。

Terra直接测试完整方法条件/return/异常、实参多次出现、所有candidate、XML动态列值和include依赖、AST可选与状态、DOM投影不冒充原文、同ref稳定、跨packet不串、真实计量边界、业务字段未知时不猜、零JDT/SQL parser/模型调用。真实2MB样例只可离线测投影大小；本轮未运行测量，不能预报压缩比例或业务质量。
