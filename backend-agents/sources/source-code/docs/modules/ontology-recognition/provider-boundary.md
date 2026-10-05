# 本体请求的输出边界

状态：2026-10-05，formal typed-v3、SDK字节/token适配、整次期限、局部失败及私有响应保存均已限定验收。实际O1/O2共18请求，8任务已审、1采购关系拒绝，O3有效四文件可查询。最后的正式响应读取大小/期限已直接RED→GREEN；20项直接组合及最终质量6695通过，旧三参数/MD路径保持原行为。订阅输出token仍NOT_ENFORCED_BY_ADAPTER；不承诺远端取消或模型零错误。实际证据与成本见[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)。

## 已核对的能力

- 现有`StructuredModelRequest.maxOutputBytes`控制最终结构化响应的UTF-8字节数。它不是token数。
- Responses适配器在本体请求提供独立token声明时，实际传递该token值，并单独核验响应字节数；6项直接HTTP fixture已通过。没有该声明的旧构造仍保留其原字节值传参行为，本轮不顺带修旧请求或重算旧指纹。
- Responses接口的`maxOutputTokens`是包括可见输出和推理token的上限，不能将它当作JSON文本长度。[官方接口](https://developers.openai.com/api/reference/java/resources/beta/subresources/responses/methods/create)
- 当前本机`codex exec --help`及官方CLI/配置参考未提供可核实的逐请求输出token上限参数。因此本轮不编造`-c max_output_tokens=...`等选项，也不宣称ChatGPT登录路径已经强制执行这个上限。[CLI参考](https://learn.chatgpt.com/docs/developer-commands?surface=cli)、[配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)

## 正式接线要求

本体请求分别保存`maxOutputBytes`、`requestedMaxOutputTokens`与实际适配器的`outputTokenLimitMode`。支持的适配器传递token配置；当前Codex订阅适配器记录`NOT_ENFORCED_BY_ADAPTER`，仍执行响应字节校验、请求次数和单次超时约束。不能用Prompt中的字数要求冒充工具强制上限。

`maxOutputTokens`不是已证明的上下文窗口；完整输入封套仍须独立检查。计数未知时明确披露。三个真实样例使用既定ChatGPT登录路径，不为了这个限制切换API-key或账户。验收记录分别报告请求的token配置、实际可执行限制与工具未支持部分。

新行为只适用于本体请求分支。`StructuredModelRequest`保留六参数构造和旧请求的六字段序列化；formal typed-v3以可空的ontology专用`requestedMaxOutputTokens`携带声明，并在私有formal journal保存它。旧请求不新增字段、不重算指纹、不改变原有Provider参数传递。本页描述的限制不阻碍零模型的材料准备、存储或查询；真实运行前必须在预检报告中展示。

## 初始化前检查与实际审阅输入

正式typed runner必须提供不创建Provider的输入准备接口，返回实际EXTRACT请求及其准确任务身份。运行时先核对来源、冻结包、已审目录和这个完整封套，再初始化模型适配器；不能用一个空Provider占位，或另写一套近似投影来宣称检查通过。

REVIEW还需要随后实际返回的原稿。因此它的完整封套只能在EXTRACT完成后、REVIEW发送前检查；原稿超限或保存失败时保留真实失败，不截断、不重新提取，也不把预先估算写成“已核验实际REVIEW”。本轮所有请求次数和失败派发仍计入明确上限。

## 执行边界与当前验证范围

确认结束的无效输出只结束本任务，独立任务继续；单请求超时须能可靠确认请求本身结束才可局部继续。当前requestEnded=true只证明本地CLI已退出，不证明远端取消；远端未知、认证/绑定错误或未确认本地终止停止实际Provider范围，CLI返回实际报告，不自动换模型或重发。局部判定须有阶段及结构化观察，不仅凭异常消息。预检前计数是预留，journal委托前STARTED也不证明进程已启动；缺可靠观察标未知。

订阅适配器把私有prompt文件用`ProcessBuilder.redirectInput`接到既有CLI `-` stdin，避免同步写入不读stdin的子进程；从调用入口起的一次deadline覆盖有限本地准备、启动前检查、剩余`waitFor`及formal本体响应文件的有界读取。formal请求通过受限命令重载只读取完整且不超过16 MiB的response；读取前、循环中和结束后均核对同一期限。超过该私有完整保存上限时返回已开始且已结束的`RESPONSE_BUDGET_EXCEEDED`，不读入/伪装截断正文；不带formal token声明的旧三参数命令仍走原行为，注入测试command也由默认委托保持兼容。超时仍以有界`terminateAndConfirm`核对本地进程，保留原`REQUEST_TIMEOUT / OUTCOME_UNKNOWN`及`requestStarted / requestEnded`实际本地字段；开始前耗尽期限如实为未启动的`UNKNOWN`。prompt、schema、stdout、stderr和response均在现有私有临时目录清理。此处不把本地退出或终止确认当作远端取消证明，不新增线程、恢复平台或旧MD语义改变。保存失败不继续增加不可追踪请求。具体控制流与未实现点由[详细设计§7–8](evidence-stability-and-failure-isolation.md#7-错误分类及准确处理)拥有。

本体v2观察把`reservedAttempts`（进入有限派发额度）与`confirmedStarted`、`confirmedEnded`（适配器实际确认的本地请求生命周期）分开。`outcomeUnknown`记录未观察到远端结果的尝试；它不是取消成功次数。初始化或登录检查失败不能算模型请求已经开始，委托前写入的`STARTED`日志也不能作为这个证明。旧`modelRequestsDispatched`字段及历史v1观察仍按原格式读取，不回填新计数。正式CLI的超时与初始化失败两项直接测试通过：前者为预留1、已启动1、本地结束1、远端未知1并停止后续派发；后者为预留1、已启动0、结束0、远端未知0。该验证不是远端取消证明，也不是三个真实样例验收。
