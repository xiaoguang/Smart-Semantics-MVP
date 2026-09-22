# 设计确认与历史三例验收边界

本页不授权新的模型调用。当前工作是将Step05→Activity详细设计整合进[主设计](../../DESIGN.md)与Modules，并用[真实材料推演](../../examples/activity-material-end-to-end-walkthrough.md)检查逻辑闭合。

## 本轮设计确认

检查同一真实packet的入口、完整方法/调用、候选、参数/控制/返回、XML/SQL限制怎样到达Activity请求；小包直接完整解释，大包无损去重后模型分页阅读与语义slice，各slice完整DRAFT/REVIEW。检查来源作用域、未读范围、部分失败、阶段复用、新批次和下游materialSource映射。

确认以实际保存材料与代码合同为依据，不要求本轮重跑JDT、Maven、产品模型或全仓实验。设计清楚可实现不表示模型能够自动准确解释全部业务。

## 真实三例发生了什么

历史固定输入/批次见[原三例记录](three-case-acceptance-result-20260916.md)。实际12次：1全局选择、3阅读检查、3事实草稿、3写作、2最终核对。采购最后核对超窗未发送；销售最后返回有7个未定义用法及业务范围错误；调拨完整保存但仍有两处配置限定错误。三例未全部通过。

可读性认可与事实准确性分开；预览不是全仓publication，不关闭326条全仓覆盖，也不执行全仓归并。原raw、失败诊断、旧Activity与more-findings.md原样保留，不自动修复或续跑。

## 后续实现与真实质量条件

- 新source discriminator、完整publication/basis、request-local ref映射与旧M10严格读取。
- 完整源码/条件/公式不因容量丢失，XML结构投影与实际已读scope诚实保存。
- 每入口/slice未读、未知和失败不被部分成功遮蔽；review使用完整实际DRAFT。
- 配置限制内的阶段attempt、backoff、手动新batch与成功阶段精确复用；不换Provider、不重扫、不自动进Step07。
- 只有后续明确授权的真实样本才能检验业务名称、条件、分支、金额用途、配置限定和跨对象联系；程序测试只证明结构与接线。

测试Terra/xhigh、代码Sol/xhigh、设计/调试Astra/ultra；业务生成为ChatGPT登录上下文Terra/xhigh。旧实验模型身份不改。只运行新增/直接覆盖测试，扩大或全套验证需要当前明确范围。
