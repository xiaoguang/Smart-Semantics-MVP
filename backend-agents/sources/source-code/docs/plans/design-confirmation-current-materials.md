# 当前阅读材料与Activity接入：详细设计确认

状态：本轮文档整合完成，等待用户审阅设计；**没有实施新Activity接入，没有运行新的Activity/过程模型**。本页是本次设计收口，不是可自动执行的实施计划。

## 本次明确的目标

从已完成的Step05阅读材料直接进入Activity，再供现有Step07过程发现使用。保留八步与唯一公共Agent；Step05是材料owner，ActivityExplainer是唯一局部业务解释者，M10只读历史。有效合同已进入[主设计](../DESIGN.md)、[Step06](../analysis-steps/06-flow-interpretation.md)、[Activity Modules](../modules/activity-explanation/README.md)、[Step07 Modules](../modules/business-process-discovery/README.md)及[执行合同](../modules/model-job-execution.md)。

小包完整DRAFT/REVIEW；大包先无损去重，再给完整分页导航，由模型有界读取完整方法/statement及依赖并提出语义slice。各slice分别完整解释/审阅，程序稳定聚合多个Activity，不加巨大总REVIEW或有损摘要。未读、未解释和失败范围明确保留，部分成功不冒充入口完成。

新Activity包间按全局/账户限制并行，同包阶段/切片顺序；失败阶段按maxAttempts/backoff配置处理，成功DRAFT可复用，逐attempt保存。包局部失败隔离；共享来源/配置/认证不安全停止相应范围。人工重试通过显式新batch处理指定失败范围，保留旧状态与成功结果，不自动进入Step07。Step07现有无自动重试政策保持。

业务生成默认已登录ChatGPT的Codex上下文Terra/xhigh，设计/调试Astra/ultra，测试Terra/xhigh，生产代码Sol/xhigh；不修改旧模型身份或借文档任务改YAML。

## 当前事实与未实施版本

Step01–05 JDT-only/可选持久化/阅读材料保存重开已完成，现行state v4/output v5只支持READING_MATERIALS_ONLY。旧326条Activity依赖M10且原样保留。新目标repository-run-config-v3、model-job-execution-config-v4、analysis-run-output-v6、flow-interpretation-activity-explanations-v2、flow-interpretation-activity-coverage-v3、Activity私有reviewed-result-v4及Prompt/响应v3尚未实现，精确字段以[集成合同](../modules/activity-explanation/integration-contracts.md)为准。

coverage里的materialSource和packet/slice映射区分新Step05与历史M10，不新建公共manifest。材料sourceRunId与业务modelBatchId分开；局部source:1/S1必须按实际packet/request映射。部分M11可保存但modelBatchComplete=false，hasCompletedActivities须检查真实完整性/scope。目标v6沿用knowledgeCheckpoint/reportCheckpoint等既有业务字段名。

现有Step07已实现系统认识、CHECK最终保留、事实DRAFT→WRITE→最终RULE_REVIEW、私有结果v3和producer v4五文件。历史三例实际12请求未全通过：采购最终未发送、销售7个未定义用法及业务范围错误、调拨合法保存但有两处配置限定错误；可读性认可不等于准确性或全仓通过。Step08只保留历史读取/查询/渲染。

## 真实材料怎样检验设计

[完整推演](../examples/activity-material-end-to-end-walkthrough.md)核对固定新增单据packet：443方法、1468调用、23 XML、58 statements，约2,055,071 bytes。Controller→Service→Mapper关系、订金过滤/合计、缺省赋值范围都有实际材料出处。

推演同时保留真实限制：宽泛候选不等于确定dispatch；SQL UNSUPPORTED仍有完整XML；statement投影可能只映射整Resource来源，不能声称读完整文件；本packet缺常量定义，Step06应保留未知，Step07可经现有冻结reader补读同snapshot常量确认0/未审核，不回写旧Activity。拟Activity段落是人工设计表达，不是新模型结果。

这些检查使材料、读取、业务语义、来源、覆盖、失败、复用和下游接口可以逐项实现；不证明所有业务会被模型自动准确解释。

## 文档归并与验证

补充页已收敛为正式owner索引、研究缘由和历史特殊处理边界；历史delivery、experiment-comparison、three-case-acceptance-result和more-findings.md未改。旧实现/Prompt版本不再与目标混称。

本轮仅文档与本地保存材料只读核对，已实际完成：

- git diff --check通过；代码/配置范围没有本轮diff。
- more-findings.md SHA256仍为59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e。
- 父任务独立复核旧326条activity-explanations与coverage，两份文件SHA均与事前基线一致。
- 历史delivery、experiment-comparison、three-case-acceptance-result的git diff为空。
- 父任务完成42份变更/新增Markdown的251个本地文件与章节链接检查，未发现失效链接。

未运行测试、Maven/构建、JDT或产品模型，未修改代码/配置/快照/旧Activity，未commit或push。本任务临时progress已在结论收纳后删除，未动其他历史progress。

## 后续界限

本次停止在设计审阅。后续实施只按新增/直接覆盖范围测试新材料投影、完整阅读/语义scope、来源/coverage、阶段尝试与新批次复用、历史严格读取及Step07接入。真实Activity的事实准确性、业务表达和材料充分性需要后续明确样本验收。旧三例优化或全仓业务生成不由本设计自动启动。
