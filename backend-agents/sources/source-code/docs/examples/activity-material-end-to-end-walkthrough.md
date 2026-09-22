# 一份真实材料如何变成Activity，再进入业务过程

本页使用已经保存的冻结源码/Step05材料检验设计闭合；**没有执行新Activity模型，没有生成新的业务结果**。下列“拟解释内容”“拟阅读请求”都是设计推演，不能登记为模型输出或真实验收通过。

## 1. 实际拿到的输入

固定jshERP commit为8c30ce7861570458920175e200bb2a6442713580。当前Step05样本是POST /depotHead/addDepotHeadAndDetail：

- [完整阅读投影](../../.workspace/jdt-persistence-acceptance-20260917/reading-sample-1.md)，packet:d317aeb5a274fccdc28767b9f3838b19ef68463de197a4a8d7c517bacb124d3f。
- [实际Step05 JSONL](../../.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/05-business-flows/code-reading-materials.jsonl)；本packet约2,055,071 bytes，含443方法、1468调用、23 XML resources、58 statements。
- [同次冻结文件清单](../../.workspace/jdt-persistence-acceptance-20260917/stores/runs/analysis-run--5b84e5dbf38421e205c7a045685ff7332464b7f94ddce71ddfc966b0bbc1b2ac/steps/01-verified-source-inventory/source-inventory.jsonl)。

JSONL才是保存输入；约2.9万行样本Markdown含共享XML等重复展示，是供人阅读的投影，不能当作模型已经读过的prompt或据此估计最终输入大小。当前新材料路线停在READING_MATERIALS_ONLY。

unselected为空只说明保存材料未登记被主动移出的项，不证明443个候选方法均实际调用，也不保证常量、运行时配置或所有业务知识都已收集。样本里有宽泛JDT候选和UNRESOLVED边界，必须原样保留不确定性。

## 2. Controller、Service与SQL怎样接上

| 实际材料 | 已保存位置 | 可以支持什么 |
| --- | --- | --- |
| M1 DepotHeadController.addDepotHeadAndDetail | 原Controller 606–621行；样本约3250行起 | body.info和body.rows分别传到Service，随后返回标准结果 |
| M5 DepotHeadService.addDepotHeadAndDetail | 原Service 1204–1312行；样本3314行起 | 单号/关联/账户校验、缺省赋值、订金校验、保存主表/明细及后续调用 |
| DepotHeadMapper.insertSelective | XML完整原件和动态语句保留；SQL分析状态UNSUPPORTED | 可读动态插入原文；不能把静态副本只剩insert into当成源码缺失 |
| DepotHeadMapperEx.getFinishDepositByNumberExceptCurrent | Mapper参数关联与XML；样本27022行起有PARSED分析 | sum(deposit)，同link_number、排除当前number、排除已删除记录 |

M1传入beanJson/rows/request，与M5的形式参数按实际调用关系连接。M5里面的mapper调用既有定位也有候选；例如insertSelective还出现AccountHeadMapper候选，不能因名称相同认定两个表都会写。模型需要结合接收对象、参数与所给原文判断，仍不能覆盖JDT记录制造唯一dispatch。

Step04的SQL解析是辅助材料。insertSelective的分析副本因动态XML被省略而解析失败，完整XML仍在Resource.rawSource；这不阻挡模型阅读原始if/trim等条件。getFinishDeposit查询则提供实际过滤与聚合。两种状态可以同时存在，不能把UNSUPPORTED写成“没有SQL”或把PARSED当成运行成功。

statement节点没有新造精确源码行号；其SourceRef可能指整份XML原件。Step06允许安全解析已保存rawSource抽取完整statement及include依赖，不再运行PersistenceAnalyzer/JSqlParser，也不宣称结构投影天然有精确行段。模型只看到某statement投影时，不得因Sref映射整文件就声称已读完整XML。私有已读unit/scope与原件来源分别保存。

## 3. 这段Service确实写了哪些限定

以下是对实际原文的人工核对，不是Activity模型结果。

| 原文事实 | 拟Activity应保留的完整意思 | 不能写成 |
| --- | --- | --- |
| checkIsBillNumberExist(0L,number)>0抛异常 | 单号已存在时拒绝新增 | 一定生成唯一单号或某次建单成功 |
| linkNumber非空且linkApply非空才拒绝 | 两种关联不能同时填写；此检查没有要求至少填一种 | 必须关联订单，或必须先有请购 |
| subType为采购/采购退货/销售/销售退货且两种账户输入均空才拒绝 | 此账户检查限于列出的四种子类型 | 所有单据都必须相同结算账户 |
| status为空才设BILLS_STATUS_UN_AUDIT | 缺省状态才赋该常量，已传状态不在此处覆盖 | 每次新增都强制未审核，或本包已证明该值为0 |
| payType==null才用“现付” | 没有payType值时默认现付；非null值保留 | 空字符串也必然变现付 |
| deposit!=null且linkNumber非空才查累计值 | 有订金值且关联订单时才进入本项检查 | 所有单据都必须检查/填写订金 |
| 关联订单changeAmount!=null才比较 | 当前deposit+累计已扣deposit不得大于关联订单changeAmount绝对值 | 当前应付金额不能超过订单总额 |
| SQL过滤同link_number、不同number、未删除 | 累计排除当前单据与已删除单据 | 所有单据或含本单金额的无条件合计 |

订金规则的设计表达可以是：

> 登记单据时，如填写本次抵扣订金并关联订单，系统核对累计抵扣额；只有关联订单记录了订金金额时，已抵扣额加本次抵扣额才与该订单订金金额的绝对值比较。已抵扣额只统计关联同一订单、排除本单且未删除的其他单据，超额拒绝新增。此项检查不要求所有单据都关联订单或抵扣订金。

演示数值仅用于理解比较边界：假设关联金额绝对值100、已有累计60，则本次40不因本比较拒绝，本次41会拒绝；关联金额为空时本比较不执行。这不是客户数据，也不表示其他校验一定通过。

M5还显示主表insertSelective、明细saveDetials、欠款/剩余订金等调用。要解释其完整业务效果必须读到对应实际完整方法与必要依赖，不能仅用调用名声称付款完成或真实库存变化。静态代码可描述所定义的保存调用，不能证明某次事务成功。

## 4. 目标Step06如何处理这个大包

程序先核对Step05完整publication、entry/packet范围和保存字节，建立请求局部映射：E对应本入口，M/C对应真实方法/调用，S对应该请求实际可见来源。Step05内部source:1会在不同packet重新出现；请求S1也会重新编号，必须保留packet+sourceRef→request S的映射。

投影只合并完全相同的正文：M5只发一份，所有调用位置/参数/返回/候选仍各自可查；同一XML原件只保留一份，statement与依赖使用明确结构引用。不能把443方法按固定行数拆块，也不能先总结成“校验并保存单据”丢掉上表条件。

完整无损投影若仍超出配置容量，就走[大材料阅读](../modules/activity-explanation/large-material-reading.md)：

1. 提供全部可分页导航，保留每个方法/调用/候选、完整性、持久化关联与边界；导航不是已读正文。
2. 模型读取完整M1/M5及业务相关方法/statement依赖，判断哪些范围可独立解释。一个合理调查方向是“新增单据和关联限制”，另一个可能是“订金校验及回写”；这仅是本例人工推演，不能写入通用Prompt当期望分类。
3. 模型提出完整语义slice，程序只核验真实句柄/参数/调用/控制/返回及XML依赖闭包。语义合理性仍由模型审阅，不由Java行业规则打分。
4. 每个slice分别DRAFT→同一完整材料+实际DRAFT的REVIEW，返回完整Activity。程序稳定聚合，不再发巨大总REVIEW或摘要合并。
5. 未读范围、超预算、仍有关键缺口或某slice失败具体登记。同入口一个slice已审，不得覆盖其他未闭合slice。

拟输出的一条Activity可能称“新增单据及明细”，包含上述条件与来源；也可能拆为多个局部Activity。这只是目标输出形状示例，不能捏造Activity ID或伪称真实Terra返回。其目的、对象、条件、步骤、结果、规则、公式、问题和限制按现有完整业务字段保存。

## 5. 一个真实缺口：缺省状态的数值

本packet的Service使用BILLS_STATUS_UN_AUDIT，但样本方法正文中没有该常量定义，不能从名字推断数值0。Step06当前目标只消费所绑定packet，不能越过输入去读全仓或补造常量。它应保留“缺省赋此常量；具体值/业务名称待确认”的范围说明。

本轮人工另行读到同一冻结来源的[BusinessConstants原件](../../.workspace/jsherp-full-parallel-host-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs/350db160e813b10187c4f6b78ce7eaca1c1bbc34b93682cea15366176c8111e8)：69行注释写0未审核，71行定义BILLS_STATUS_UN_AUDIT="0"。同次新source-inventory第27行记录该仓库相对路径、SHA与size，和这份旧capture blob一致。这是额外实际读取的冻结文件，**不在上述Step05包的已读方法正文中**。

目标Step07可通过已有FrozenAnalysisCorpus文件/行范围或字面量请求获取这份常量原文，把它放进实际过程reading packet后确认“status为空时默认未审核（0）”。原Activity保持未知及其原来源，不回填、重算或改写。这个接点不需要常量解析器、证据层或重新跑Step04；目前只作设计推演，未执行Step07模型。

## 6. Activity如何进入过程

目标Activity coverage v3的materialSource存完整Step05 publication、sourceRunId及版本，并带packet/slice映射；不新增公共manifest文件。Step07按它打开新材料，旧326条Activity继续打开各自旧M10。原件来源与Activity实际已读范围都保留，不能把较大来源对象冒充较大阅读范围。

完整Activity先进入目录发现/全局选材；要把“新增单据”作为某个订单或入出库用法，模型必须在同源全仓导航中选对应完整活动和原文。它可以补读上节常量、页面操作、修改/审核或数量进度方法；不能因本例出现linkNumber就编出必经“请购→订单→入库→付款”。

如果Step07的SOURCE_REF打开整份大XML，仍受现行一次CHECK和实际容量检查；并非一定能装下。可用已有冻结文件范围/字面量查询选取实际关键原文，不新造细行号取证模块。读完仍缺则保留UNRESOLVED。

候选随后事实DRAFT→WRITE→最终RULE_REVIEW。最后核对同时看完整Activity、真实补读原文、实际DRAFT和WRITE，防止“只有缺省时赋值”变“每次强制赋值”，或订金变应付总额。最终过程的规则限定ActivityUse，来源统一映射；Publisher从完整最终结构确定性输出业务正文与来源页，不改写原Activity。

## 7. 并发、失败和逻辑闭合

假设本包REVIEW发生允许重试的瞬态失败，下一次只执行该REVIEW，复用成功DRAFT及同一完整输入；每次attempt保存，maxAttempts包含首次。超过上限只终止该包，其他包在共享来源/配置/认证仍安全时继续。若同入口还有未完成slice，不得自动进入Step07。人工重试另起批次，旧失败记录保留。

这里的stage retry仅是新Activity设计，不改变Step07无自动重试。采购旧样本最终超窗不能因本设计而自动续跑；旧326条Activity也不能因此重做。

本例已能检查材料owner、真实条件、SourceRef作用域、XML原文与投影、大包语义范围、常量未知到Step07补读、失败保存和业务发布之间的接力。它证明设计可以逐项讨论与实现，不证明模型会自动正确解释全部业务。新Activity集成、定向fixture与明确授权的真实质量验收仍待后续实施。
