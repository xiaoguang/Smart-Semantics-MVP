# 前端选择回调与保存材料：薄实验

状态：**两个真实页面及改名 fixture 的有限采集、组装和重开验证通过。** 正式 R1/R4、CLI 未修改，本体提取未执行；本结果不能作为全仓前端覆盖或业务关联验收通过的依据。

## 要回答的问题

现有前端采集保留了打开关联单据列表的函数，却没有登记处理选择结果的回调及提交保存实现。能否复用已有 `vue-eslint-parser`，用小量 AST 适配保留这些完整原文和明确的静态关联，再与原 R4 后端/SQL 材料组成一份可独立阅读的实验文件？

用户已批准这一实验。采购订单与采购入库页面是具名验收范围，不是通用算法中的业务词表。另用改名的非 ERP fixture 检查共享组件、不同页面实例和动态未知。

## 输入与当前缺口

- 固定 R4：`analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe`。
- 同源 R0：`analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7`，Git `8c30ce7861570458920175e200bb2a6442713580`。
- R4 引用的前端 R1：`analysis-run:fcbd79f8f14f28c5c14aea1d98b9c415458b02cd4cbf407b58b06145cc951bf4`。
- R1 将两个 Vue 标为已解析，但仅登记采购订单页 411–423 行的三个查询函数、采购入库页 454–462 行的两个查询函数；没有 `linkBillListOk` 的单元。
- 现有保存单据入口 `/depotHead/addDepotHeadAndDetail` 的 R4 前端单元、确定请求和候选请求数量均为零。

`PARSED` 只表示语法读取成功，不证明页面所有动作都已形成材料。此前的查询链验收不能替代选择结果与保存链验收。

## 实验处理与责任

1. 从已固定源码副本取得相关前端文本，核对 R4 所绑定前端文件的实际 SHA；不读取客户当前工作区，不修改旧源码或产物。
2. 用已有 parser 读取模板和脚本 AST。保存完整函数/相关源码原文；识别字面组件事件、`$emit`、本地或明确 mixin 方法调用及 HTTP 封装调用。
3. 页面实例分别展开。显式 import、ref、事件名和实参与形参可建立技术关联；动态名称、冲突目标及缺少实现保留限制。不同页面不因共享组件而合并参数。
4. HTTP 方法和路由只采用原文中可以明确读取的值；新增和修改的分支条件保留。无法确定部署 baseURL/context-path 时仍标为候选，不伪造确定前后端匹配。
5. 生成单独的实验入口 JSON，保留原 R4 完整 Java/XML/SQL 与限制，附前端补充材料。重新读取输出，核验原文和上游身份没有改变。

程序只采集和组织技术观察，不解释中文业务，不证明任意 JavaScript 数据流、不执行异步代码、客户应用、SQL 或 Maven。不写新的语言解释器或证据运行框架。

## 通过与不通过

需要同时检查：

- 选择回调完整进入输出，不能只存命中行。
- 子组件发出事件、父模板绑定及父回调可以按已保存位置相互关联。
- 保存按钮、mixin 调用、数据整理和 HTTP 保存封装均有完整原文；异步回调内部的明确调用不被遗漏。
- 新增/修改的 URL、方法和条件没有错误组合；无法确认时明确保留。
- 与原 R4 后端和持久化材料组合后，可从同一个实验文件取回这些材料；原材料保持不变。
- 改名 fixture 不依赖采购名称、`linkId`、特定方法名或路径；共享页面实例不串用。

仅保存原文但未确认某条连接时，记录部分结果，不能冒称完整确定链。通过只证明这一有限 Vue2 模式的材料采集可行，不证明全仓前端覆盖、运行时数据流、业务解释或本体识别已经通过。

## 输出及停止边界

实验代码和结果保存在忽略的 `.workspace/frontend-selection-save-probe-20261002/`，明确标为实验数据，不能安装为正式 R4 或被现有本体消费者静默接收。旧 R1/R4 及模型记录保持不变。

本轮业务模型、JDT、Maven调用均为零。两个真实页面与直接 fixtures 检查结束后报告结论；是否正式补齐 R1/R4 和重跑有限前端范围，另行按实验结果讨论。

## 实际结果

### 1. 实际输入、处理与输出

2026-10-02，探针以两个页面路径、同源固定源码根、R4目录和明确的`@`别名为输入；配置没有填写要采集的函数、字段或业务答案。实际使用现有`vue-eslint-parser@10.4.1`，不是新增Vue/JavaScript解析器。

先核对R4已绑定的307个前端文件摘要，再从两个页面的静态import、组件和mixin取得7个参与文件。最终保存两个独立页面上下文、94个完整函数/箭头函数源码单元（两个上下文分别44、50个，不能视作94个不同源码方法），以及这7个文件的完整原文。共享正文保留一份文件原文，页面中的参数用途仍分开。

| 验证内容 | 采购订单页面 | 采购入库页面 |
| --- | --- | --- |
| 完整`linkBillListOk`原文 | 424–478行 | 463–540行 |
| `info.linkId = info.id` | 在完整回调内保留，第443行 | 在完整回调内保留，第487行 |
| 子组件事件实参与父回调形参 | 子组件9个实参；父回调3个形参 | 同一子组件9个实参；父回调8个形参 |
| 保存请求观察 | `POST /depotHead/addDepotHeadAndDetail`及`PUT /depotHead/updateDepotHeadAndDetail` | 同样两种请求，各属本页面上下文 |
| 请求分支 | `this.model.id`不成立时POST/add，成立时PUT/edit | 同一原文条件，未与另一个页面实例混用 |

子组件实参数量大于父回调声明的形参数量也原样保存，不能为了让记录整齐而补造形参或删实参。本次不执行事件，未证明任何一次实际页面操作成功。

两类材料分别保存，并没有把它们猜成一条已证明的运行时数据流：

- **选择端**：`LinkBillList.handleOk`中字面`$emit('ok', …)` → 父模板`@ok="linkBillListOk"` → 完整父回调。
- **保存端**：带准确模板位置的点击观察 → 明确mixin调用 → `handleOk`内完整`.then`箭头函数 → `classifyIntoFormData`及`request` → 完整`httpAction`封装。`classifyIntoFormData`内的`rows: JSON.stringify(detailArr)`原文保留。

静态HTTP方法和路由匹配出4条`SAME_METHOD_AND_ROUTE_CANDIDATE_ONLY`观察，分属两个后端入口。部署`baseURL`和地址条件未确认，仍为候选，不升级成确定调用者。两个实验入口JSON均含`baselineEvidence`（原R4的完整字段和源码）及`experimentalFrontendSupplement`（两个页面上下文和完整相关前端文本）。本轮实际消费者只有探针重开器、直接测试和独立核验脚本；正式CLI、本体consumer没有读取这些实验文件。

### 2. 可以在同一文件核对什么

新增入口的实验JSON内，已同时保留以下真实原文：

```javascript
// 两个页面的完整选择回调内部
info.linkId = info.id

// 完整数据整理方法内部
rows: JSON.stringify(detailArr)
```

```java
// 原R4中DepotItemService.saveDetials的完整正文内部
if (StringUtil.isExist(rowObj.get("linkId"))) {
    depotItem.setLinkId(rowObj.getLong("linkId"));
}
```

原R4的`batchInsert` Mapper XML含`link_id`，完整XML及其SQL分析也保留。上述摘录是**人工定位到的已保存原文**，不是探针输出的本体关系或业务结论。探针没有自动证明`detailArr`中每个值均源自该回调，没有执行SQL，也没有核对完整请购→采购订单→入库→结算生命周期。

因此，可以确认本次提出的材料遗漏可在有限模式下补齐；不能说本体模型已经读取并审阅了这条关联。

### 3. 实际验证命令及结果

命令均从本模块目录执行，探针及fixture在忽略的工作目录内：

```bash
node --test .workspace/frontend-selection-save-probe-20261002/selection-save-probe.test.cjs
node .workspace/frontend-selection-save-probe-20261002/run-probe.cjs \
  .workspace/frontend-selection-save-probe-20261002/real-config-final.json
node .workspace/frontend-selection-save-probe-20261002/verify-real.cjs \
  .workspace/frontend-selection-save-probe-20261002/real-config-final.json
```

- 新增直接测试：**5通过、0失败、0跳过**。改名的非ERP fixture覆盖共享组件、两个不同页面URL、动态事件、缺少mixin、HTTP封装签名及有限URL/方法分支；runner测试使用实际`methodCondition`合同，检查完整基线字段和原文件字节未变。
- 真实探针最终运行及重新读取：exit 0，两个页面、7个完整文件、4条请求观察、4条候选匹配、2份实验入口JSON。
- 独立核验：exit 0。重新读取固定源码并用parser核对完整方法范围；检查每个单元原文、9个实参、3/8个形参、模板独立位置、方法→实际箭头→内部调用的图记录、POST/PUT条件、原R4字段及来源摘要。
- 已观察到原helper不能登记选择/保存材料的RED，以及探针对未知实例API记录不足的RED。模板位置和Promise图的最终断言通过，但它们在相关修正之后加入，**没有单独的预修正RED记录**，不补写测试历史。
- Java/JDT、Maven、SQL执行、客户应用、业务Provider初始化及业务模型请求均为**0**。此前本体实验累计23/26次未因本实验增加。

探针采集代码352行、runner117行；新增测试562行。它们是被批准的可丢弃实验，不是另一套正式证据发布框架。未运行Java全量测试或无关仓库套件。

### 4. 输出及体积

相对本模块的输出目录为`.workspace/frontend-selection-save-probe-20261002/real-output-final/`。

| 文件 | 实测UTF-8字节 | 用途 |
| --- | ---: | --- |
| `frontend-probe.json` | 422,870 | 两个页面上下文、静态观察和7个完整相关文件 |
| `entry-52f92156ebd5637227b8ed73282258ce9e2864167951baf8cb1bb2658ac2ae81.experimental.json` | 5,443,085 | 新增单据入口的原R4＋前端补充 |
| `entry-59341c6f5003e267298205d48ff5cb012d139a563b6803953ecbfbd4869c237b.experimental.json` | 1,323,773 | 修改单据入口的原R4＋前端补充 |
| `summary.json`、`verification.json` | 以实际文件为准 | 运行计数及独立核验结果 |

两个入口文件是缩进JSON；去掉排版后的实际JSON字节分别为3,902,060及1,013,494，原R4相应为3,530,376及641,800。本结果**没有解决模型阅读容量**，不将几MB的实验入口文件直接当成适合小上下文模型的阅读包。两个页面的有限模式没有留下探针专用未解析项（`limitationCount=0`），不等于没有部署、运行时、全仓覆盖和本体消费者限制。

### 5. 调试中实际出现的问题

首轮探针已取得回调和保存请求，但runner错读不存在的`entry.method`，因此0个入口匹配；真实R4用的是`methodCondition.methods`。修正后才产生两份入口文件。这是实验runner的合同读取错误，**不是JDT、SQL或客户源码错误**。

随后校正模板点击的不同位置，以及异步记录中“外层方法→实际箭头函数→内部直接调用”的关系；不再把任意点击命名为保存、不再把箭头误指到其中调用的方法。`$emit`/`$nextTick`等实例调用保存原表达式和`INSTANCE_METHOD_NOT_EXPANDED`，不伪称已经完成工具绑定或缺少mixin。

`real-output`、`real-output-v2`、`real-output-v3`均原地保留；最终结论仅以`real-output-final`及对应最终代码为准。没有覆盖历史尝试或自动增加模型重试。

### 6. 历史保护核对与后续边界

- 原342个R4文件按固定清单计算的集合摘要仍为`e693a1bf7649751f82bddd4843c4f9edb58512c21e3da8a2277cec747601fcb2`。
- R4入口索引摘要仍为`22857198f71fd77918447e4c5f16a8109f88f7efaeb0795e3d9a9c1196620fb1`。
- 原R1前端索引摘要仍为`6a049d8f6b0b71473cc2c583f99fae315f1ad160d5b0dd417424abbf89d375fe`。
- `more-findings.md`摘要仍为`59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`。
- 最终`probe.cjs`摘要为`00f97548de24079481d7da42832908d43aa414a22556c91a97f15b821476c9e1`。

**结论：有限材料采集与局部组装可行；正式修复尚未实施。** 需要正式接入R1的来源单元、静态连接和请求保存，再由原R4发布/重开链消费；不得把实验JSON改版本号安装成R4。该待办继续保留在backlog第27项。

未被本实验验证的内容包括：全仓页面动作覆盖、复杂JavaScript求值、完整变量数据流、运行时部署连通、业务关系正确性、类型化本体提取及模型上下文容量。本实验结束在证据补齐可行性，不自动展开这些任务。
