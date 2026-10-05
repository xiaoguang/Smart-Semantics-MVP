# 两个联系的人工辅助材料与局部本体概念验证

状态：2026-10-02，**本次材料格式、Java机械准备和两条联系的局部识别概念验证通过**。这不是自主选材、全仓本体或全部业务规则验收。自动选材的32次历史请求仍未通过；本次使用独立授权的4次Luna/high请求，无重复提取、无模型重试。

## 1. 四个要求的实际结论

| 要求 | 实际验证 | 结论边界 |
| --- | --- | --- |
| 材料能用于业务联系及局部本体识别 | 两题各一次EXTRACT、一次原文REVIEW；最终7个局部对象、5条关系、6个操作、2个金额计算定义 | 对当前两题成立，不等于全仓覆盖或所有模型都正确 |
| 输入简短 | 财务实际EXTRACT输入13,718字节，采购54,524字节；REVIEW输入16,481/59,292字节 | 不是token数或任意小窗口适配证明 |
| 必要原文完整可追溯 | 27个选定完整单元逐字核对，文件摘要与同源R0一致；源码正文11,506/49,995字节 | 对本题关键建立/使用两端成立，未附全部被调用helper或整仓所有规则；具体未知保留 |
| 准备可由Java、且不依赖模型 | 208行薄Java投影器已编译运行；改变输出目录重复运行，两个输入和私有映射均逐字节一致；准备阶段模型调用0 | 正式CLI接线未实施；本次语义选材清单是人工补齐，不冒充自主选材 |

Java只读取已保存JSON、按明确清单取完整单元或完整源码区间、去重、分配短编号、汇总技术限制并保存可逆映射。没有生成业务摘要，没有模型依赖，没有Java/JavaScript/XML/SQL解释器。**已实跑Java准备，不只是声称“以后可以”。**

## 2. 固定来源、输入及执行方式

- 固定R4：`analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe`。
- 同源R0：`analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`。
- 材料与运行目录：`.workspace/assisted-two-link-poc-20261002/`。
- Java准备器：`AssistedTwoLinkMaterialProjector.java`；最终选择清单：`selection-final.json`。
- 实际输入：`inputs-final/finance.json`、`inputs-final/procurement.json`；私有原文、调用观察及来源：`inputs-final/private-provenance.json`。
- 审计：`source-audit-before-model.json`、`java-repeatability-audit.json`、独立`audit.md/audit.cjs`、`final-audit.json`。

模型输入只有：

```json
{
  "question": "当前具体问题",
  "units": [
    {"ref":"S1","language":"JAVA","owner":"声明或页面用途","symbol":"单元名称","text":"完整方法/函数/XML原文"}
  ],
  "contexts": ["实际技术限制和未提供范围"]
}
```

长ID、重复调用记录、完整资源及可逆映射留在程序侧；**不是把完整原文换成宿主Agent的业务摘要**。同一短编号按题作用域解释。页面源码来自已保存同R0的实验补充，明确标记非正式R4，未宣称本次修复了正式前端采集。

两个普通问题：

1. 财务明细如何与业务单据建立来源联系，并在查询时使用？
2. 采购入库选择订单明细时，来源明细怎样传给入库单并保存？

模型没有收到正确答案或旧模型结论。通用提示只约定对象/关系/操作/可选分析口径、实际来源引用与未知的表达。业务类名和字段仅来自选中原文。

## 3. 输入大小和原文范围

| 项目 | 财务 | 采购 |
| --- | ---: | ---: |
| 完整单元 | 10 | 17 |
| 完整正文UTF-8字节 | 11,506 | 49,995 |
| 可读缩进JSON文件字节 | 14,130 | 55,210 |
| Provider实际canonical EXTRACT输入 | 13,718 | 54,524 |
| EXTRACT封套，含Prompt、Schema及20KB输出余量 | 37,883 | 78,899 |
| 实际REVIEW输入，含实际提取稿 | 16,481 | 59,292 |
| 实际REVIEW封套，含输出余量 | 40,666 | 83,687 |

缩进文件与canonical输入大小不同，不是用了两版源码。输入字节较小不能冒称任意token窗口适配；本次实际使用gpt-6-luna/high。

财务含完整保存Controller/主Service/明细Service/插入方法、业务单据按单号查询方法、财务查询Controller/Service/Mapper，以及完整insert/select XML。采购含完整选择回调、明细确认、表单组装、请求封装、Controller、主/明细Service、insert XML；并带事件标签、提交url对象、提交方法及选择列表show的完整短单元。`saveDetials`完整26,585字节正文未截断，也未随附394条重复调用明细挤占模型输入。

## 4. 实际识别结果与原文核验

### 财务：建立和使用两端已经接上

审阅结果有3个对象、2条关系、2个操作；维度/指标为空，没有凑数。

主联系是：明细中有业务单号时，保存方法按该单号查业务单据，取其id赋给财务明细的billId；insert XML按条件写入bill_id。查询端按ai.bill_id过滤，并经ai.header_id=ah.id关联财务主表，返回ah.bill_no。查询没有直接JOIN业务单据表，也没有证明收付款已经发生。

依据分别是本题S3/S5/S6和S7–S10。最终稿明确保留未提供的readFail/writeFail、Long重载和欠款相关辅助方法，不再把未知异常行为写成必然正常返回。

### 采购：来源明细编号与来源订单号分开

审阅结果有4个对象、3条关系、4个操作、2个金额计算定义；维度为空。

核心明细联系：页面回调原文`info.linkId = info.id`；该明细进入提交rows；后端保存方法取linkId设置DepotItem.linkId；batchInsert XML写入jsh_depot_item.link_id。依据为S2/S3/S10/S13。来源订单主表的number则通过另一条linkNumber→link_number联系保存，不能把订单主表和订单明细混成一个对象。最终REVIEW修正了这一区别。

保存源码中的具体guard仍是`StringUtil.isExist(rowObj.get("linkId"))`，XML主表guard是`linkNumber != null`。模型对它们概括为“包含/非空”的措辞不应替代原始条件，也没有证明缺失helper的内部语义。核心字段交接已识别，不宣称所有拒绝条件、分批数量、状态回写或生命周期完整核验。

两个金额定义直接来自S3：主表totalPrice取明细allPrice合计的负值；changeAmount作符号转换。有源码计算式和粒度，**不等于已经确认两个企业经营KPI**。维度未返回属于本轮模型输出范围，不证明源码没有维度。

## 5. 模型错误与实际四次调用

第一次采购提取稿已经识别明细联系，但operations.target写成“ O1 -> O2；订单号传至 O3 ”及“ O2、O3 ”，违反单个对象ID合同。原始响应和首次运行FAILED状态保留，没有暗改原稿或补业务答案。

前三次为财务EXTRACT/REVIEW、采购EXTRACT。剩余第4次直接对**同一采购原文和实际错误提取稿**做REVIEW，并加入通用结构校验说明；没有重新选材、重新提取或自动重试。最终REVIEW使用有效对象ID，增加有源码支持的订单主表对象，拆分相应操作。

- 原运行：`analysis-run:3dc4d3799d86a613ce818f8e6c9fc7474eda2a45fdafc2ffef3f4a42e0cddd2f`，3次调用，原失败状态不改写。
- 剩余单次REVIEW：`analysis-run:42af207cc64f8aef44711dbaf096e12b2d7208568f31ac4134eae6caba1c856c`，1次调用。
- 本次合计4/4；原自动选材32次另计，均保持原记录。
- 原文和实际草稿传递、57处最终来源引用、对象端点均已核验。

这是模型格式错误及原文REVIEW中的实际修正，不是材料缺失。以后可在已有REVIEW中提供结构诊断，不必先用额外模型请求重写草稿；本次没有实现正式阶段恢复框架。

## 6. 程序组装与交付

`LocalReviewedOntologyAssembler.java`已实际运行，按已审类型化字段和题内来源编号组装：

- `local-ontology.experimental.json`：19,833字节，两个题分别保存完整definitions及sourceIndex。
- 财务最终稿：`real-output/outputs/finance.json`。
- 采购原稿和最终审阅：`review-only-output/procurement.json`。

程序不读中文来猜关系、不按同名合并对象、不把两题的O1或S1当成同一个编号。引用作用域为caseId＋局部编号；跨题对象统一与正式本体Schema以后设计，本轮不虚构已实现。

## 7. 已验证和未验证分开

**可以据此采用的方向：**完整取证保存在程序侧；针对具体联系，由选择清单驱动Java生成原文优先、短编号、限制明确的阅读材料；模型直接返回小的类型化定义，再用同一原文审阅；Java按字段组装。材料准备不需要LLM理解业务。

**仍未验证/未实施：**自主语义选材；正式CLI/Skill接线；全仓本体；全部身份唯一性及基数；完整指标/维度体系；任意弱模型或更小窗口保证。客户模型误读和遗漏仍可能发生，不以此要求框架自建语义解释器或强模型回退。

R4的342个发布文件与more-findings摘要均与记录一致；旧Activity和旧模型结果未改写。未重跑取证、Activity、业务流程、DDL或客户代码。
