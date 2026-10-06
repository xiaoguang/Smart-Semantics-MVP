# 精简材料、跨段对象类型对应与局部业务图：实施计划

状态：9步工程与有限正式链路执行已完成，交付核对见[本轮验收](../supplements/ontology-lean-type-formal-20261006-acceptance.md)。正式图连接三对象来源编号关系；小窗口及跨项目质量未通过或未验证，不以代码完成覆盖这些限制。功能规范以[连贯材料设计§17](../modules/ontology-recognition/coherent-link-material-design.md#17-精简材料与跨段类型对应的正式设计)为准，差距见[implementation-delta §9](../modules/ontology-recognition/implementation-delta.md#9-成功薄实验的正式接线差距)。本文保存本轮执行范围，不将旧 PoC 当作生产完成证据。

## 目标与保护

固定 R4→Java组织紧凑完整实现→局部 LINK→两段共同对象类型比较→执行已审对应→四文件及可查看局部图。

保留现有四命令、Agent、Provider、canonical store、一次审阅、失败隔离与 Mermaid。不重跑取证，不改原始R0/R4，不修改旧MD、Activity、过程、九章；不新增解释器、相似度平台、图数据库、自动修稿或第五个命令，不做全仓本体和完整采购生命周期。

固定输入：

- R4：analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744。
- R0：analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7。
- 优先复用 O0：analysis-run:63ebafa5f5b0bd3b0a461e6c4ce975f7b99cfc990e2fa67779d357aab5da0885，先核对 Corpus、规则和准确来源。

## 顺序与门禁

每个开发步骤采用直接失败测试→最小实现→定向回归→当前事实文档同步。历史待执行请求不得套用新规则。

| 步骤 | 工作 | 通过标志 | 工程初估 |
| --- | --- | --- | --- |
| 0 | 既有分支、改动归属、输入及保护摘要 | 新工作不混原暂存PoC，历史可复核 | 0.5–1小时 |
| 1 | 新合同和准确任务身份 | Schema、生产、读取、安装同步；完整候选分母 | 3–4小时 |
| 2 | packet/model-v7 精简 | 正文、用途、参数、候选、限制可逆；完整封套成本 | 4–6小时 |
| 3 | LINK-v2 固定投影 | 一次提取／审阅；端点闭合；坏任务局部拒绝 | 2–3小时 |
| 4 | 专用类型对应 | 已审来源取得两端原文；独立 TYPE_COMPARE 终态 | 4–6小时 |
| 5 | 确定性组装与局部图 | 原关系保留，无补边、反向、实例统一；四文件重开 | 3–5小时 |
| 6 | 同一Skill、示例和查询说明 | 宿主无需聊天记忆或补材料；GENERAL不变 | 1–2小时 |
| 7 | 离线贯穿与质量检查 | 正式Agent/store、错误路径、旧MD隔离通过 | 2–3小时 |
| 8 | 有限正式验收及PR交付 | 图、本体、来源、成本、限制齐全 | 1–2小时核验＋运行 |

总计21–33工程小时，不含模型／工具等待；步骤2后校准剩余，不重计既有投入。

### 0：基线

继续 codex/ontology-recognition 既有工作树，不重置、不整体暂存。保护旧MD代码／Prompt／Schema、历史结果及more-findings.md；保存独立 progress，其他未结束计划保留。

### 1：合同与身份

selection-v4 的 RELATE 必须明确 GENERAL_RELATE 或 OBJECT_TYPE_CORRESPONDENCE。每候选保存父问题、声明taskId、稳定pairRef、两端完整定义身份；每对独立任务，按身份关联处置。未准备只记处置，不伪造jobKey。Schema、Reader、producer、私有任务结果、策略、模块／步骤安装和fixture同批支持。

### 2：精简包

修改既有 ReadingPacket／ModelProjection。完整Java、Vue、XML、SQL正文逐字保留。便利AST字段、重复声明及未选调用详情留私有；选定调用保留位置、实参、目标、候选、观察及有序用途，复用字典。逐位置冲突／未知／失败仍入模。页面实例、参数、未传参数、地址条件不合并。保存未入模字段、未读目录和可逆核验。用同材料比较新旧EXTRACT／REVIEW；范围缩小和表达压缩分开报告。

### 3：LINK-v2

只要骨架对象、联系、条件、来源、未知及K处置。mechanism为text/objectKeys/evidenceRefs；conditions再含unknowns。Java固定投影到既有semanticItem，不解析中文补字段。Java分配内部编号，检查最终端点及全部结构化引用。身份、属性、基数未调查明确未知。可读坏稿进一次REVIEW，最终非法只拒绝当前任务，无第三轮；不加客户词分类规则。

### 4：类型比较

现有O2内窄组件，无新CLI或多轮选材。候选信号为准确同名、backing重合或保存的技术绑定交集，均非合并许可。每对读取两端实际引用的完整前端类型／用途和一跳上下文；无前端取准确完整后端。区分本次S和仅上游存在引用。仅SAME_OBJECT_TYPE／DISTINCT／UNRESOLVED；前两类需引用两端本次原文。部分子类型重合不能全节点统一。预算／材料／依赖不足仍保留全候选分母。类型相同不表示同一笔记录。

### 5：组装与图

仅把已审SAME_OBJECT_TYPE规范化为组装器现有SAME_OBJECT，保存OBJECT_TYPE_EQUIVALENCE语义、原响应、规则及完整身份。canonical确定选择真实端点，保留原定义／来源／全部关系。DISTINCT／UNRESOLVED不合并也不改成业务无关。局部图只选经过已审共同节点的既存跨组有向连续两边；没有就显示没有。主图短标签、详情完整条件／未知；其它对象、孤立节点、边、视图有可见分母。方向不改成办理順序。

四文件保持 ontology.json、ontology-coverage.json、ontology-sources.jsonl、ontology-review.json。

### 6：Skill

prepare-ontology→identify-ontology→必要时relate-ontology→publish-ontology。LINK用link/linkReview；类型比较用relate/review，未显式覆盖时按profile选专用默认Prompt，GENERAL默认不变。实际Prompt和适配Schema保存并参与身份。非零退出仍查报告，Agent不补源码／业务答案／模型JSON。O0/O3/查询零Provider。

### 7：离线验证

仅本轮新增和直接覆盖测试：同材料可逆／篡改拒绝、实例隔离、同名异义与共表多类型、错Corpus／同短号／未审／缺上游在Provider前拒绝、中间失败与全失败终态、类型不变实例／基数／效果、图不改事实／注入、正式Agent/store/artifact搬移重开、新ACTION/ANALYTIC scripted消费、旧MD行为不变。

定向测试后，质量宿主JDK21+、应用Java17、一个重型构建：

```bash
JAVA_HOME="$SOURCE_ANALYSIS_QUALITY_JAVA_HOME" \
mvn -t .mvn/toolchains.local.xml -Pquality \
  -DskipUTs -DskipITs spotless:check verify
```

### 8：正式验收

同一O0新建两段来源编号LINK→专用类型比较→O3。沿已有线索恢复当前准确K，记录显式定向恢复，不宣称自主全仓发现。Java正式组织材料，不导入PoC包或正确本体，Prompt不含采购链答案。各LINK两次、每类型对两次，每运行最多12次并发1，无自动重试／回退。核对请购、订单、入库是否通过原关系和已审类型决定连通。保留旧来源误指，交付全部实际请求、草稿、审阅、导航、映射、成本及限制。

框架、模型质量、容量、范围分别判定；无可靠token验证不宣称4k/8k。验收后相关实现、测试、设计、Skill和被引用验收说明通过PR交付；原暂存PoC、运行数据、本机配置、凭据不混入，不强推、不自动合main。

## 版本合同

| 边界 | 目标 |
| --- | --- |
| packet/model | v7；EXACT_LEAN_LINK_BUNDLE_V2／link-bundle-rule-v2 |
| LINK | candidate/review-v2 |
| O2/O3 selection | v4 |
| TYPE_COMPARE | candidate/review-v1 |
| 已审目录／私有任务结果 | v5 |
| O1/O2阶段及producer | v5 |
| O3producer／coverage／review | v5 |
| 操作观察／策略 | v4／v5 |
| ontology／source | ontology-v2／source-v1保持 |

public命令／Agent、config-v1、scope-v3、request-v6/output-v10、Corpus/O0保持。历史v6包、LINK-v1、selection-v3不转换，新策略只显式启用。

## 执行角色和完成含义

主Agent编码／调度／debug，Luna/xhigh直接测试；最多两个工作Agent，共享接线串行，一个重型构建。产品模型现有已授权Luna/high，同范围确定代码缺陷持续修复，模型误读不无限修稿；扩大来源、范围或改变架构才讨论。

完成是成功实验同方法进入四命令、图可保存重开追溯，不是再次交付手工补材料的实验。
