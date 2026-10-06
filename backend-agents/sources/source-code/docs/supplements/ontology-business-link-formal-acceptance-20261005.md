# 业务联系优先：正式交付及验收结论

状态：正式工程路径、四文件安装/重开和有限真实样例已经完成；**业务覆盖与小窗口目标仅部分通过**。最终129项新增/直接回归全绿，同代码静态质量通过。实际图和财务/统计细化已保存，不宣称完整采购生命周期、自主全仓发现或跨项目同等模型质量。

拥有设计为[业务联系优先设计](../modules/ontology-recognition/business-link-first-design.md)，执行范围为[九步计划](../plans/business-link-first-implementation-plan.md)。工程提交75fa3915407460222162d17085bc80c75c1e2a7d包含70个具名本轮文件，已正常推送并更新现有[PR35](https://github.com/xiaoguang/Smart-Semantics-MVP/pull/35)，远端head核对一致；不强推、不合入main。12前序暂存文件保持；运行材料保留在忽略的本地store，不进入提交。

## 1. 固定输入与正式运行链

| 用途 | 准确运行ID | 实际终态 |
| --- | --- | --- |
| R0源码准备 | analysis-run:73d2c60e237ff5d2668c19ded542294d398c5302d298a362eedc23f6296f91d7 | 已有固定输入，不改写 |
| R4入口证据 | analysis-run:1bcd11687fd7ab082d6b7a51c5218758bb0d60af0677e8d34f717d702e4b6744 | 已有固定输入，不改写 |
| O0资料及技术导航 | analysis-run:04d56272ae18654c8e1ed12df26cdbf9852ffc7e28fdd39d16b511d00be01e15 | COMPLETED/FINISHED，零模型 |
| O1来源编号/双仓库对象 | analysis-run:4370678252531494f77429da6cacd63c7b4fa27279cd9578bcadfdc8f97e2536 | 两OBJECT REVIEWED |
| O1财务/统计对象 | analysis-run:2b0499b7376bb0919707949735786e2810849e546c13a463106e328bbf5295ff | 两OBJECT REVIEWED |
| O2四题关系 | analysis-run:3bf1e0d0108f4589cbb0c3f23d71d81badbd8b68f4e48d7745b992ce30065d14 | 四RELATE REVIEWED |
| O3先交骨架 | analysis-run:fe5ca91576214b03df5872b8e3b7d2f91ef1768242aee963d7025680100d368d | COMPLETED/FINISHED，零模型 |
| O1跨运行细化 | analysis-run:a0e86a3e213dbc37dc6ac2e24516e60ccd52eb0faf6c82d8131971a8496df17a | 财务ACTION/统计ANALYTIC REVIEWED |
| O3扩展本体 | analysis-run:99c5158f375ef0dd2e7383129b13d3c61870f0e9a6eb5bb6cc3e40a8f1eb34e0 | COMPLETED/FINISHED，零模型 |

O0是正式prepare-ontology、Agent登记及canonical store结果：corpus-v2、projection-v3，14,670,466字节，339入口、15,726单元别名、2,881技术线索。Corpus大小不是请求大小，K不是业务结论。文件SHA256为4fb3471a831cf11944cd80157850211a4a87fa8e1e31daf8b7e3fbf16d2fc16c。

最终O3显式选择三个O1及一个O2，准确上游闭包通过；先交的骨架O3未被覆盖。最终10项声明任务均REVIEWED，四层为COMPLETE_FOR_DECLARED_SCOPE，semanticExhaustiveness=UNDETERMINED。**COMPLETE只表示这些声明任务已结束，不表示识别了整个系统，也不表示模型没遗漏。**

## 2. 实际交付物及重开

最终运行目录相对既有技术store为：

```text
runs/analysis-run--99c5158f375ef0dd2e7383129b13d3c61870f0e9a6eb5bb6cc3e40a8f1eb34e0/
  steps/07-repository-knowledge/
    ontology.json
    ontology-coverage.json
    ontology-sources.jsonl
    ontology-review.json
    repository-knowledge-receipt.json
    modules/05-ontology-publisher/  同四文件及模块回执
```

| 文件 | 实际字节 | 意义 |
| --- | ---: | --- |
| ontology.json | 460239 | 19对象、22联系、7操作、4规则、6维度、6组成度量、6指标定义 |
| ontology-coverage.json | 33946 | 10项任务处置、各层范围、线索及阅读分母 |
| ontology-sources.jsonl | 73556 | 定义→准确冻结材料及来源映射 |
| ontology-review.json | 54484 | 已审任务、修正、未知及准确语义上游 |
| 派生业务HTML | 4000105 | 通过ONTOLOGY_BUSINESS_OVERVIEW返回，含离线Mermaid，不是第五个事实产物 |

正式inspect及四个artifact查询全部exit0，返回字节与已安装文件逐一相同。HTML查询exit0，含离线资源及实际定义。发布为DRAFT_REVIEWABLE，六指标全部NOT_EXECUTABLE。未运行指标、Action或客户SQL。

HTML方框/箭头来自保存定义，不由模型另画图。已审canonical身份只画一个框，原定义仍可查看；确认的连接机制不因基数等细节未知降级，已知组装反证仍显示虚线。真实浏览器SVG布局**未验收**：工具拒绝本地file URL，未绕行localhost、其它浏览器或协议。字符串、安全、方向及长标签测试不是浏览器布局证明。

## 3. 图中实际有什么，缺什么

| 局部范围 | 实际识别 | 不能写成什么 |
| --- | --- | --- |
| 来源编号 | 库存业务单据、单据明细；linkNumber来源编号、headerId归属、linkId来源明细联系，3条关系 | 没有分立的请购单、采购订单、采购入库对象；不能画成已识别完整采购链 |
| 双仓库标识 | 明细、主单、商品、扩展规格及计量单位；headerId/materialId/materialExtendId关联，两个仓库编号保存/比较，4条关系 | 没有独立仓库对象；方向和库存效果仍未知，不等于调拨已完成 |
| 财务 | FinancialRecord、FinancialRecordLine、BusinessBill；主从归属、bill_id关联及金额影响欠款，2条关系 | 不证明真实付款发生，也不把7操作当作7个顺序步骤 |
| 期间统计 | 六种交易类别及用户、角色、审核配置；用户角色查询、六类创建人范围、六类审核过滤，13条关系 | 同一表上的类别及权限联系不自动组成采购生命周期 |

来源编号题最初O1使用MODEL导航/选择；后续RELATE有限接续使用实际模型已选8项用途，由Java机械恢复和冻结，没有人工补对象、正文或正确答案。该8项为5Java+2完整XML资源+1语句，**没有选入Vue单元**；这使本轮不能证明采购页面建立端已经完整阅读，也不能证明关系自动选材已通过。原自动O2未完成的READ及编号错误记录保持不变。

财务7操作是保存财务主明细、保存明细、聚合财务金额、取得欠款、更新最终欠款、查询财务单号、批量修正欠款。4规则为财务单号重复拒绝、转账账户复用拒绝、明细为空拒绝、财务金额影响业务欠款。公式/过滤/异常依据与未知在正式定义中，结构合法不等于业务解释必然正确。

统计6维度为期间、移动类型、交易子类型、创建人访问范围、审核过滤、价格可见上下文。6组成量为采购/采购退货/销售/销售退货金额及零售/零售退货绝对金额。6指标是3净额（采购减退货、销售减退货、零售减退货）及其3权限展示变体；**不是6个独立经营KPI**。货币/单位、角色常量意义、审核状态含义、运行时动态SQL及价格遮蔽调用冲突保留未知。

统计权限辅助parseHomePriceByLimit完整670字符正文，RoleService.java第235–254行，以U11052/S4实际进入EXTRACT和REVIEW。补读没有消除原NAVIGATION_CONFLICT，也没有将候选升级为确定调用。

## 4. 实际材料、成本及可逆核验

最终审计10任务、139次私有单元出现、20份实际EXTRACT/REVIEW请求：O0别名摘要与冻结单元一致，完整Java.sourceText、XML.rawSource/xmlSubtree、SQL.analysisCopy与实际模型材料逐字一致，差异0；同一任务EXTRACT/REVIEW使用同一对象目录。用途和逐调用不确定性的可逆编码另有直接测试。

| 任务 | EXTRACT字节 | REVIEW字节 |
| --- | ---: | ---: |
| 来源编号OBJECT | 193761 | 208434 |
| 双仓库OBJECT | 225312 | 243086 |
| 财务OBJECT | 124918 | 148850 |
| 统计OBJECT | 79225 | 110309 |
| 来源编号RELATE | 325065 | 336874 |
| 双仓库RELATE | 237538 | 248695 |
| 财务RELATE | 144617 | 151668 |
| 统计RELATE | 102973 | 127228 |
| 财务ACTION | 160495 | 186134 |
| 统计ANALYTIC | 110102 | 139738 |

相同完整单元、Prompt、目录及实际草稿下，用生产decodeCallRows还原有序展开调用再比较：压缩收益0.4%–21.3%。双仓库OBJECT/RELATE约20%，来源RELATE约0.8%，财务ACTION REVIEW约0.4%。这是同材料紧凑调用与展开调用对照，不冒称精确历史v4转换；少选正文不计作压缩收益。

实际封套79,225–336,874字节（约77.4–329.0KiB），无可靠token计数。大完整XML和不重复的调用仍占容量；**小窗口目标未通过**，不以字节下降、能跑Luna或提高限额代替。输出4096-token配置在现订阅适配器为NOT_ENFORCED_BY_ADAPTER，响应字节检查独立有效。

当前发布谱系实际26请求（旧骨架10、财务/统计OBJECT4、四RELATE8、细化4），全部确认开始/结束、未知0。此前失败批次另有真实计数，不声称整次开发只有26调用。O0/O3/query均0；无第三轮修稿、模型/账户回退或自动重试。

## 5. 已修缺陷与历史失败保留

| 确定性缺陷 | 当前修复及验证 |
| --- | --- |
| Provider不支持formal-reading的allOf/oneOf | 仅Provider Schema副本窄适配；本地语义校验不放松，实际后续阅读无HTTP400复现 |
| Prompt/Schema/执行器动作及容量不一致 | 实际请求带配置maxItems/maximum及字节上限；直接RED→GREEN |
| O1/O2错误读取旧O0阅读控制 | 新路线使用本阶段控制；已结束旧437导航64事实不反写成16 |
| 新producer接受旧Corpus规则 | 显式准确版本准入；历史查询及原生产规则保留 |
| 当前草稿O/P与历史目录B混淆 | 通用Prompt明确命名空间；新财务3对象保留，未手工恢复删除稿 |
| cardinality未知误画虚线 | 确认机制保持实线，未知及已知反证保留 |
| 已审canonical对象重复画框 | 一个canonical框，原定义/来源仍保留；未知目标/链/环拒绝 |
| 外部OBJECT依赖仅在无本题OBJECT时检查 | question级objectSources适用全部任务；依赖、目录、身份一致，坏外部OBJECT零派发 |
| 有效来源问题没有OBJECT时提前退出 | 准确保存DEPENDENCY_NOT_REVIEWED/UNPROCESSED；不存在的问题仍来源准入拒绝 |

曾失败O1/O2保持原状态、原响应及未知观察。源码未修改；结构坏稿只进入原定一次REVIEW，最终仍坏就局部拒绝。统计细化首稿12项机械引用诊断由这一次REVIEW修正；没有第三次模型修稿或导入手工修正JSON。

## 6. 设计—实现—直接测试对应

| 计划步骤 | 实现拥有者 | 最终直接验证及实际结果 |
| --- | --- | --- |
| 0基线保护 | 保护清单、显式路径选择 | 790文件（704历史+86相对保护）SHA/字节不变；12前序暂存PoC文件保持 |
| 1合同/历史 | ScopeReader、TypedValidator、SavedTaskContract、两层canonical publication | ScopeV2/TypedV4/Artifact/CanonicalStore合同；明确旧版本拒绝及准确重开 |
| 2线索/页面反查 | EvidenceCorpus | CorpusV2合同的保存控制K、准确上下文身份及多实例用途；共用Service不自动成关系 |
| 3紧凑材料 | ReadingPacket、ModelProjection | FormalMaterial可逆调用/用途/候选/完整封套；20真实请求正文核验及同材料成本 |
| 4骨架 | TypedTaskRunner、ReadingCoordinator、正式runtime | OBJECT/RELATE正式Agent-store；MODEL成功/失败、K分母保留、本阶段控制 |
| 5跨运行细化 | ScopeReader、ConfiguredRuntime、SavedTaskContract | 精确外部目录、全部任务依赖、无OBJECT/坏OBJECT零派发、O3准确上游并集 |
| 6图及发布 | ScopedAssembler、BusinessOverviewRenderer、ArtifactQuery | 角色/方向/未知/孤立/canonical/注入拒绝；正式四文件字节一致、离线HTML返回 |
| 7Skill/贯穿 | source-analysis共享Skill及reference | scripted骨架→细化→新发布；实际当前资源/示例/链接校验；8旧共享路径回归 |
| 8真实局部 | 正式O0/O1/O2/O3及来源审计 | 已交实际骨架及细化；业务具体类别、自动关系选材、小窗口及浏览器呈现局部未通过/未验 |

最终统一129项新增/直接测试，0失败/错误/跳过，127秒。包含8旧共享CLI/MD/历史复用回归，不重复计数、不运行整仓或客户JDT。随后**同代码**质量101秒通过：Spotless778干净、SpotBugs0发现/错误、PMD通过，UT/IT跳过；质量宿主JDK26，编译测试Java17。未提高阈值或排除规则。最终Sol限定只读分支复审未发现剩余确定性缺陷。

Skill packaged validator因Python缺YAML未启动；共享说明链接、JSON示例及限定合同审查已核对，不能写成全部宿主产品安装或验证通过。

新增/保留边界：保留四命令、现有Agent/store/Provider、旧MD/Activity/process/history及JDT。新增窄合同、材料投影、正式v2 Prompt、保存核验、离线Mermaid视图；没有新增Maven管理、解释器、图数据库、相似度平台或修稿系统。前序12份暂存实验代码不进入本轮提交，也未注册为正式命令。无客户构建、JDT重跑、DDL增加、Activity/过程/九章生成。

## 7. 剩余验收及backlog

工程和实际材料交付已完成；backlog32仅工程/发布子项关闭，以下验收仍OPEN：

1. 模型未区分具体请购/订单/入库类型，关系接续未自主读全页面建立端；本轮不能提供参考图中的完整采购主链。
2. 完整封套仍偏大，小窗口适配未证明；不是模型能力好坏可以替代的框架容量结论。
3. 真实浏览器呈现、独立其它系统/宿主质量未验收；无自动全仓业务覆盖承诺。

第28项“简洁而完备、模型质量与框架责任分离”长期硬约束保留。下一方案不得靠扩大上下文、客户专用答案、源码截断或从拒绝稿捞取关系补成功。
