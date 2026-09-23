# 局部活动解释

> [总体设计](../DESIGN.md)；固定 key：`flow-interpretation`，目录：`steps/06-flow-interpretation/`。分支51b6625已实现新Step05直接消费、长材料阅读及阶段retry，真实保存418条Activity。审查修复草稿尚未验证；本次待评审增量见[端到端收尾设计](../end-to-end-business-delivery-design.md)。M10生产已退役，历史读取保留。

## 1. 为什么存在

Step05已保存完整Java与可选XML/SQL材料。Step06解释“这个入口附近有哪些业务行为、触发/拒绝条件是什么、对象怎样改变、结果和未知范围是什么”。它不把HTTP入口直接当端到端过程；Step07才负责跨入口过程发现与重建。

一个新增单据技术包可能经过许多通用Service，并涉及不同类型分支。Activity可以是多个局部业务范围，一个entry也可以被多个Activity引用；不强制一个包只出一个Activity，不按固定行业字典拆分。

## 2. 输入与模块分工

| 模块 | 输入 | 程序步骤 | 输出/下一消费者 |
| --- | --- | --- | --- |
| 已有CodeReadingMaterialReader | 完整Step05 ref | 验来源/上游并恢复完整Packet | ActivityMaterialProjector |
| ActivityMaterialProjector | 不可变Packet+reading profile | 短ref、去重复、完整方法/statement依赖、真实计量 | 模型阅读视图与导航 |
| ActivityReadingCoordinator | 视图+实际Provider容量 | 小包直接；大包导航分页、有限补读、完整slice | 独立可解释的完整阅读包与未处理范围 |
| ActivityExplainer | 一个包/各slice完整阅读包 | DRAFT/REVIEW、结构/scope验证、成功stage保存 | 完整ReviewedActivity |
| 既有job pool/store/publisher的增量 | 不可变stage与结果 | 两级并发、阶段retry、稳定聚合、来源/coverage | M11；已完成范围供Step07 |

详细输入、算法、失败与测试见[Activity模块](../modules/activity-explanation/README.md)。新路径不再输入BusinessMaterialBuildResult；历史M10在明确旧kind中按原reader处理，不能包装新材料冒充旧M10。

## 3. 程序与模型各做什么

程序验证来源、引用、入口/slice分母、容量和保存；完整原文来自固定材料。程序不判断“应属于采购还是销售”、不推导所有条件是否必经、不用语言黑名单判业务语义。

新业务模型默认现有Codex登录服务 `Luna / high`，可由唯一YAML显式配置；历史Terra/xhigh结果保持原身份。每次独立请求显式发实际材料；不能依赖当前对话或旧模型会话记忆。

模型负责：

- 选择大包中需要读的完整实现与局部业务范围，并明确未读/不能关联的内容。
- 从完整方法和XML条件解释触发、输入、规则、动作、对象变化、返回及范围。
- REVIEW核对同slice完整材料与完整实际DRAFT，保留具体谓词、公式、拒绝条件和variant适用范围。
- 不声称某次请求/SQL实际成功，不制造岗位制度、外部付款/实物收货或未见的唯一性。
- 出现明确构造对象并调用保存的代码时可表述“系统生成并保存对象”，仍是代码定义行为。

## 4. 阅读与生成序列

```text
完整Step05 Packet重开（零模型）
  → 去重投影、实际容量预检
  → 小包：完整包DRAFT → 完整包 + 实际DRAFT → REVIEW
  → 大包：主干 + 全范围导航分页 → 有限补读/范围决策
          → 多slice：各自完整阅读包DRAFT → 完整实际REVIEW
  → 成功stage即时保存
  → 多个完整ReviewedActivity稳定聚合 + 包/slice/entry coverage
```

一个slice不得由任意行段或源码摘要代替。完整共同调用方、条件、参数、返回/异常与SQL依赖必须可读；若共同方法包含多个variant，模型只把本slice实际适用的规则写入。不能为了容量将条件列与值分开，也不能把其所有分支规则复制到每个slice。

全导航本身可分页，未展示页/未读单元保留；页/补读/分解上限是可调安全终止。不可拆完整方法或闭包仍过大时，明确容量未处理。多个局部稿总和过大时由程序保存多Activity，无全包摘要合并循环。详细闭合规则见[大包阅读](../modules/activity-explanation/large-material-reading.md)。

## 5. Activity字段与覆盖

ReviewedActivity业务字段保持：name、businessPurpose、participants、businessObjects、triggerOrInput、conditions、activitySteps、codeDefinedResults、businessRules、formulasOrMetrics、terms、questions、scopeLimitations、entryIds、sourceRefs、certainty。certainty沿用 `DIRECT_CODE_BEHAVIOR / REASONABLE_INFERENCE / NEEDS_CONFIRMATION`。

每个slice使用所属包E1…EN的允许子集；DRAFT允许缺入口，程序计算missingEntryKeys，REVIEW必填可空unexplainedEntries：

```text
本slice reviewed activity keys ∪ unexplainedEntries = 本slice entryKeys
两集合不相交；未知key/ref拒绝
```

MODEL_NOT_EXPLAINED只表示合法REVIEW明确未解释，不是来源缺失、容量失败或retry耗尽。包级还必须关闭所有必需slice；一个slice成功不能把同entry其他失败范围标为完整。当前coverage-v3只有entry级处置，完整阅读细节在私有记录。本次目标coverage-v4增加最小packetCompletion，不复制逐unit台账；普通未选辅助源码不等于必需失败，历史无法判断则明确UNDETERMINED。

例如旧已审“批量强制结单”保留“逐项状态必须等于3，否则抛异常；通过后设为2；更新数量大于0才返回成功信息；状态正式含义待确认”。这比方法名翻译具体，但它仍是局部Activity，不能直接宣布完整订单生命周期。

## 6. 并发、retry与部分失败

一个packet是一个job，不同packet按全局和Provider双上限并行；包内页/轮/slice/stage/attempt顺序。默认Activity每stage maxAttempts=3（含首次），1关闭；退避与timeout独立，瞬时故障默认可retry，JSON/未知ref仅显式配置才retry。

DRAFT成功立即保存；REVIEW失败只retry REVIEW。每attempt有新request身份和不可变记录，同stage单飞，不覆盖失败响应。配置/来源/认证/容量问题不靠retry解决。单包穷尽继续其他包；共享运行安全错误按[模型执行](../modules/model-job-execution.md)停止相应binding或batch。

所有包有终态后保存成功Activity和失败清单；若仍有失败或必需未处理范围，run非成功、CLI非零、不自动Step07。全部所需范围成功闭合才按既有成功路径结束。用户可通过现有CLI新增的显式失败范围选项创建新batch，精确复用成功stage；不重扫、重规划旧成功范围或自动重启。参数及版本见[接入合同](../modules/activity-explanation/integration-contracts.md)。

## 7. 发布与下游

M11地址保持 `module11/activity-explainer`，当前module v3只有 `activity-explanations.jsonl` 与 `activity-coverage.json`，schema为explanations-v2/coverage-v3。来源映射实际在Activity行和运行的readingMaterialCheckpoint，不在coverage的materialSource对象。本次覆盖升v4、producer升v4，正文v2保持。材料属于sourceRunId，模型输出属于各自modelBatchId；精确字段见接入合同。

部分成功M11可读，但activityBatchComplete=false及必需范围检查阻止自动下游；共享来源/结构损坏则不安装不可信aggregate。Step07只按显式来源读取旧M10或新Step05，卡片导航不能代替完整Activity与源码，也不能因此重新跑本步。

保存的326个旧ReviewedActivity、326条入口处置及原M10完全保留，其原模型/限制不变。新Step05的325包、326覆盖及1个导航失败是另一份材料验收，不能据此重标旧Activity或要求全量重跑。

## 8. 当前成熟度与直接验收

已实现：新材料投影、XML依赖、阅读/slice、阶段保存和retry、M11 v3/output-v6、正式CLI及Step07来源接线。真实325包生成418条，1个原导航缺口仍在。旧提交544测试通过不覆盖未验证修复草稿；剩余包括阅读计划正确复用、必需范围、公开部分成功、错误分类、先校验后SUCCESS和可配置reading上限。

本次目标先离线核对418条的原阅读计划和成功阶段，以显式只复用的新批次形成可靠范围记录，不改旧正文。Step07只增加同入口导航关系，模型可将相近切片作为同一阶段的多个资料；不按名称删除、不把18条统计切片当18步办理流程。不自动重跑Activity。

Terra先写直接行为RED，Sol实现，Astra裁决与debug。测试覆盖完整真实请求材料、REVIEW保留长规则/公式、多entry多slice覆盖、候选/SQL条件、容量停止、DRAFT成功后REVIEW重试、失败隔离、新batch复用和零重扫。只跑直接覆盖测试，重型命令串行。

既有小包/大包和325包真实结果见[原验收](../modules/activity-explanation/full-step05-acceptance-20260923.md)，之后发现的问题见[收口记录](../modules/activity-explanation/post-review-handoff-20260923.md)。本轮只修改设计，没有追加模型调用；后续过程样例与全仓需重新按明确范围安排，文档闭合不等于语义质量已验证。
