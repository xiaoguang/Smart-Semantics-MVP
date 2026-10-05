# 相关证据轻量实验：实际结果与结论

状态：2026-10-01，实验已结束。**当前方案未通过跨入口业务联系验收，不扩大正式实施。**

这里的“未通过”不是证明技术重合度没有价值，也不是证明模型读原文后不能理解业务。财务题得到经过原文审阅的有限查询结论，但没有读到关键写入侧；采购题在阅读协议校验处停止，尚无业务输出可评价。

设计见[轻量POC](../modules/ontology-recognition/related-evidence-poc.md)。本次不是六类本体提取或局部本体组装，不发布 `ontology.json`。

## 1. 实际输入及调用

| 项目 | 实际值 |
| --- | --- |
| 固定R4 | `analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe` |
| R4目录摘要 | `22857198f71fd77918447e4c5f16a8109f88f7efaeb0795e3d9a9c1196620fb1` |
| R4声明的同源R0 | `analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7` |
| 本次读取 | R4完整已保存单元；核对R0归属，没有额外读取R0源码 |
| 导航范围 | 人工限定的20入口、190对；重合报告频次分母为339入口 |
| 请求配置 | `gpt-6-luna / high`，ChatGPT登录、read-only，无API-key回退 |
| 实际请求 | 财务4次＋采购2次＝6次；无选材或提取重试 |
| 既有总额度 | 此前15次＋本次6次＝21/26；本次最多8次，没有用完 |
| 未执行 | 取证、JDT、Maven、DDL解析、Activity、业务过程、正式全仓本体 |

两题独立使用原始R4与同一重合报告。采购没有继承财务的模型答案或定向阅读包。20入口不是全仓自主发现结果，不能以本次结果推断全339入口覆盖或准确率。

临时程序、配置及记录在忽略的[独立实验目录](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/)，没有接入正式生产入口。

## 2. 实际导航与离线检查

模型看到全部20张基础卡，以及全部190对的紧凑分通道统计。14个通道的名称用C1～C14表示，附完整对照表；每个通道保留共同项数量、Jaccard、稀有性加权Jaccard。

现有canonical保存器不接受浮点节点，所以J/W以数值文本传递，未测值仍为null。逐项离线对照证明：20入口、190对、2660项通道结果的数值、零值和未测状态均未改变。没有隐藏对照入口或剪裁所选源码。

必须说明尚未做到的部分：

- 本次未给模型逐对的全部共同材料名称清单，只给分通道统计及每入口少量线索。
- 入口线索按现有Corpus的lookupKey选取，不是已经实现了“罕见共同业务项优先”的分组算法。
- 线索的`savedUnitHandleUses`是保存单元使用次数，不是重合报告中的不同入口频次。
- 没有状态字段写入→读取的自动索引；未完成该强信号的独立验收。
- 没有只看接口名称的对照模型运行，不能计算“相似度提高了多少准确率”。

| 请求 | 实际输入JSON字节 | 完整阅读包投影字节 |
| --- | ---: | ---: |
| 财务SELECT | 122,532 | 0 |
| 财务READING_CHECK | 23,224 | 9,759 |
| 财务OBSERVE | 23,001 | 9,759 |
| 财务REVIEW | 25,107 | 9,759 |
| 采购SELECT | 122,660 | 0 |
| 采购READING_CHECK | 26,504 | 0 |

输入字节不等于token数，未据此证明适合任意小上下文模型。首次导航仍约120KiB，比财务实际原文包大很多；正式路线不能直接照搬“全对统计全部送入”的做法。

## 3. 财务题：查询侧成立，写入交接未证明

实际问题：

> 财务记录如何关联业务单据？请只选择一条跨Controller的记录标识传递、保存或查询机制核对；金额、欠款及其它范围若未读到请列为未调查。

### 3.1 模型选了什么、实际读了什么

选择：

- `POST /depotHead/addDepotHeadAndDetail`；
- `GET /accountHead/getFinancialBillNoByBillId`。

首批请求两个Controller完整方法及财务单号查询XML语句。程序同时保留所属8处调用。因此封包为2个Java方法＋8处调用＋1条完整XML语句，共11单元。

READING_CHECK没有补读，并明确说明没有读到`DepotHeadService.addDepotHeadAndDetail`实现。它没有请求建立财务明细关联的`AccountItemService.saveDetials`。

私有完整包17,181字节，模型投影9,759字节。离线逐项比较证明：两个Java完整正文、条件/参数/返回，8处调用表达式与实参，以及完整XML结构树均保留；不是用摘要替代正文。

实际结果见[完整包](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/financial-output/complete-private-packet.json)和[最终审阅稿](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/financial-output/review.json)。

### 3.2 审阅后的具体结论

最终3条观察：

1. **确认：**财务查询Controller把请求的`billId`传给`AccountHeadService#getFinancialBillNoByBillId`。未读该Service实现，不能宣布它已经与SQL形成完整确定调用链。
2. **确认：**所读XML以`ai.bill_id=#{billId}`过滤财务明细，通过`ai.header_id=ah.id`取得财务单据号`ah.bill_no`，并有主表与明细的逻辑删除过滤。
3. **有限推断：**Controller注释把`billId`称为出入库单据ID；结合该查询用途，可以理解为按业务单据ID查询关联财务单号。没有证明新增出入库单据时如何建立这个关联。

实际XML原文的一部分：

```sql
select ah.bill_no from jsh_account_head ah
left join jsh_account_item ai
  on ah.id=ai.header_id and ifnull(ai.delete_flag,'0') !='1'
where ai.bill_id=#{billId}
and ifnull(ah.delete_flag,'0') !='1'
```

这段只解释“已有财务明细关联时如何查询”，不解释“新增采购/出入库单据会自动生成财务记录”，也不证明实际收付款发生。

REVIEW纠正了两个具体问题：撤回未读Service却串成确定调用链的表述；限制“业务单据ID”的推断范围，并完整保留逻辑删除条件。最终短引用均可映射到实际已读单元，没有人工回填包外答案。

### 3.3 为什么未达跨入口交接目标

写入侧原文存在于固定R4，但未被本轮模型选入：

- 财务明细保存中，`billNumber`如何查到业务单据，再赋给`AccountItem.billId`；
- `AccountItemMapper.insertSelective`如何保存`bill_id`；
- 这些实现与具体入口之间的实际调用。

这是**关键材料没有被选读**，不是已读完整桥接材料后仍推理错误。独立人工走查只用于验收，没有进入模型输入。

SELECT的理由主要依据接口用途，没有明确说明用了哪一项具体J/W统计。因此不能把有限查询结论归功于相似度。

### 3.4 运行中的协议问题及处理

OBSERVE把`J1: 引用说明`等文本放入`evidenceRefs`，程序拒绝；原始三次请求/响应及失败manifest保留。

没有重做前三个阶段。离线证明原文包和实际观察未变后，仅派发原定尚未执行的第四次REVIEW；其Schema将引用限定为实际已读单元枚举。审阅修正稿另存。原失败manifest没有被改成成功。

所以原[manifest](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/financial-output/manifest.json)记3次停点，另存的[审阅完成记录](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/financial-output/remaining-review-result.json)记第4次及合计4次。不能把两份相加成7次。

## 4. 采购题：阅读协议失败，未进入业务判断

实际问题：

> 采购相关单据与其它单据、库存或资金记录有哪些联系？请先选择其中一条有界的标识交接或字段使用联系核对，其余范围列为未调查。

模型选择`/depotHead/list`与`/depotHead/findInOutDetail`，并称技术重合可辅助优先调查。它没有自行建立请购→订单→入库链。

### 4.1 目录上限的歧义

模型请求两个入口各25项目录。原Schema允许每个请求limit≤25，输入写`maxPageItems=25`，程序却按整轮合计25限制，因此停止。

这是临时驱动的协议歧义，不是采购业务推理错误。本次明确改为最多两页、每页25、总50项目录；保留原始选择，不再调用SELECT。正文70,000字节及每题最多4次请求不变。原上限停点保存在`original-selection-stop-manifest.json`，不是新增模型尝试。

取得的两页分别有158、80个可导航单元，本轮显示25＋25；剩余133＋55如实披露。查询卡只是标题和大小，没有源码正文。

### 4.2 最终停止原因

第2次请求的`readingPacket=null`：此前只查目录，没有已读正文。

响应同时返回：

```json
{
  "retainedRefs": ["E3"],
  "requests": [
    {"kind": "READ_UNIT", "ref": "N79", "purpose": "读取选中的DepotHeadService.select"}
  ]
}
```

以上节选只为说明错误，不是完整原始响应。

`N79`是合法待读单元；但`E3`是入口编号，不是已读原文编号。在空阅读包中，合法保留集合只能为空。程序在执行N79读取前拒绝响应，保存`LIGHT_RELATION_PROBE_RETENTION_INVALID`。

因此：

- 已有2次模型请求；
- 没有把N79正文交给模型；
- OBSERVE、REVIEW均未派发；
- 没有可验收的采购关系结果，不能说模型读完原文后推理失败。

原始输入/响应见[采购记录目录](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/journal-procurement/)，最终停点见[采购manifest](../../.workspace/ontology-recognition-poc-20261001/light-relation-probe-20261001/procurement-output/manifest.json)。

## 5. 哪些结论成立，哪些没有证明

| 项目 | 本次结论 |
| --- | --- |
| 技术重合可计算并传给模型 | 已验证20入口、190对，数据未变 |
| 可以请求完整原文，并保留正文及限制 | 财务包已验证 |
| 原文REVIEW能纠正具体过度推断 | 财务题已看到实际修正 |
| 相似度确实改善自主选材 | 未证明，没有A/B且理由不够具体 |
| 自主读到跨Controller关键桥接材料 | 财务未达到；采购未进入正文 |
| 状态写入→另一入口读取的强信号 | 未完成本次验证 |
| 完整采购链及财务金额/欠款规则 | 未验证 |
| 六类类型化本体与局部组装 | 本次未执行，不算通过 |

## 6. 已核实的问题与最小后续方向

目前不宜建设正式全仓本体路径。优先修清两类实际问题：

1. **让协议在Schema里明确，而不是只靠提示词。**入口E、待读单元N、已读正文J/C/X分别使用当前视图的合法枚举；空包的保留集合强制为空。每页、每轮上限分别命名。不能只修最后的来源引用而遗漏READING_CHECK。
2. **使关键完整实现容易被选中。**当前少量入口线索没有直接展示已读方法的内部目标；财务题因此停留在Controller。可利用已经保存的方法/调用索引提供具名目标导航，不需要新增解析器或把摘要当事实。具体共同项与页面用途也不能只用一个Jaccard值替代。

这些是后续小范围修正的候选方向，不是已实施或保证成功。尤其不应继续调复杂权重来掩盖“关键代码没读到”，也不应自动纠正错类编号后宣称链路通过。

## 7. 时间、保存与验证边界

请求文件保存到响应文件保存的实测间隔：

| 题目 | SELECT | READING_CHECK | OBSERVE | REVIEW |
| --- | ---: | ---: | ---: | ---: |
| 财务 | 32.084秒 | 32.451秒 | 38.084秒 | 54.617秒 |
| 采购 | 37.436秒 | 24.767秒 | 未执行 | 未执行 |

这些由文件时间测得，包括Provider等待、解析和保存，不是纯模型推理时间。排队与模型内部时间没有独立数据。R4重开、临时驱动调试及人工核验耗时不能从这些值推出。

实际保存了6份唯一请求journal，均有响应及Luna/high配置身份。没有自动重复请求，没有正式本体成功回执。临时阶段文件名`success.json`仅表示该阶段响应结构被保存；来源/状态校验结果须结合失败manifest，不能据文件名宣布实验成功。

只编译了忽略目录中的临时Java驱动，并运行对应离线数值/正文/引用检查；没有运行Maven、正式模块构建或无关测试套件。历史模型记录和固定证据没有写入操作。`more-findings.md`摘要仍为：

```text
59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e
```

**最终决定：本次实验结束，保留局部查询结果和两类断点；不据此扩大正式实施，不宣称已经识别本体，也不宣称相似度方向已被否定。**
