# Task 2：ReviewIssue 与标准文档审批实施报告

## 完成范围

本任务只实现领域合同、Runtime、M4 物化边界、直接测试和长期架构文档，没有修改 Task 3／4 的页面或响应式样式。

### 审核查询

- 新增`BatchOverview`、`ReviewIssue`、`Decision`、`ReviewConclusion`、证据索引和通用游标页类型。
- `ReviewQueryService`提供总览、默认未决问题、单问题结论和按“问题 + 根来源”读取的证据页。
- 证据正文加载器只接收当前页 Evidence ID；总览、问题和结论查询不触发加载。
- 冻结 artifact 中的证据索引保存来源、根来源、关联问题和规范 Evidence Record Checksum；查询和冻结都会独立复算内容 SHA，并核对加载结果的来源与根来源。

### 标准文档修订与审批

- 零售来源按 MySQL → GitHub → Semantica → SharePoint → MongoDB → Elasticsearch → MinIO → Kafka 物化 r1-r8。Evidence 数量为 4/7/9/12/15/18/21/25；问题在 r2/r6/r8 出现；r3 Lineage gap 在 r4 清除。每版九段正文和结构化断言都只投影当版可用 Evidence、Claim和模型对象；r8 编译结果保持 7 实体、5 事件、84 字段、15 关系、11 维度、12 指标。
- 每版`delta`记录新增来源、快照、Evidence和Claim；`derivedFromArtifactId`形成不可变链。
- 三项零售阻断必须由一个原子命令全部决定并形成 r9；部分决定会被拒绝。已决定内容写回第九段但不再编译成 unresolved。
- 管伊佳只接纳`READY`的 MySQL 和 GitHub。未绑定真实 manifest 的 SharePoint、Semantica 不进入 revision、fingerprint、Claim或Evidence；欠款结构冲突使用同一 Decision 和审批合同。61 张扩展表待归类和状态 9 常量依据不足是显式`WARNING` ReviewIssue，可以保持开放冻结，但不会伪装成已确认模型事实。
- 标准化文档状态机为`AWAITING_CONFIRMATION → AWAITING_REVIEW → APPROVED → FROZEN`。只有作者可记录决定和确认，审核批准人必须不同于作者。
- 冻结校验作者／审核 SHA、Markdown SHA、章节就绪度、Validation error、一般 gap、开放 blocker/error/gap、Lineage、Evidence Locator和来源索引 Checksum。

### M4 边界

- `IMPORT_TO_AI`继续只接收`FROZEN` artifact 和精确 Markdown SHA，并以 artifact ID + SHA 幂等登记。
- 标准 Markdown frontmatter 保存 artifact／项目／文档／来源身份和 canonical assertions SHA；编译、导入和物化都独立复算`markdown.content` SHA与 byteLength，并从正文解析 canonical 九段、核对 frontmatter 身份与 assertions digest。`materializeModelingDocument`还校验 frozen、artifact ID、revision和候选来源 SHA，再从 canonical artifact 重新编译并逐项核对候选全量内容；相同输入已经进入同一草稿时不重复写入对象。
- 删除零售与管伊佳按`projectId`选择预制 proposal 的分支；标准化生成物统一以实际编译候选为真相。
- 人工上传的已知 Markdown 仍保留 SHA 兼容适配器，但必须是登记后的冻结 artifact。

## TDD 记录

实现按公开 seam 逐个执行 RED → GREEN：

1. `runtime.review`不存在，查询测试以`TypeError`失败；实现懒加载查询服务后通过。
2. 完整零售批次只能生成 r1，修订链测试以`1 !== 8`失败；实现来源累积后通过。
3. 管伊佳只能生成 r1，合同测试以`1 !== 2`失败；过滤未绑定来源并累积真实来源后通过。
4. 旧 Runtime 可直接冻结，审批测试以“Missing expected rejection”失败；实现 Decision、作者确认、异人审核和冻结门禁后通过。
5. 管伊佳冻结时无法加载自身证据，测试以“证据不存在”失败；接入真实 manifest Locator和Checksum后通过。
6. M4 接受不匹配候选并进入项目硬编码 proposal，测试未得到预期 SHA 拒绝；收紧统一物化边界后通过。
7. 来源内容被替换且伪造内部自洽 Checksum 时仍可冻结，测试以“Missing expected rejection”失败；冻结来源索引 Checksum后通过。
8. 派生来源缺上游只报告普通 gap，Lineage测试得到错误消息；独立 Lineage门禁后通过。
9. 独立复审发现仅比来源三元组可伪造 candidate items；新增伪造对象回归测试，以冻结 Markdown 重编译结果执行全量比对后通过。
10. 独立复审发现证据加载器可沿用索引 SHA 但篡改正文；新增篡改正文和 Locator 回归测试，以独立内容复算 SHA、来源与根来源核对后通过。
11. 独立复审发现 r1 正文提前宣称缺席来源、r8 候选不完整；新增逐版正文和 r8 完整大模型计数断言，以当版 Evidence/Claim 投影九段后通过。
12. 独立复审发现部分决定可以形成非原子 r9；新增部分决定拒绝测试后，完整零售批次强制一次覆盖三项开放阻断。
13. 二次复审复现冻结后篡改 artifact sections 仍能注入对象；新增 sections 篡改、Markdown 内容篡改、持久化状态篡改和自定义`toJSON`绕过测试，以正文 SHA、canonical 章节解析和属性级规范序列化关闭。
14. 三次复审复现仅篡改未签名 assertions 可注入维度与 Evidence；新增 assertions 篡改测试，把 canonical assertions digest 写入 Markdown frontmatter并纳入正文 SHA 信封后关闭。

## 直接验证

最终验证结果：

- `npm run test:modeling-document`：13/13 通过。
- `./node_modules/.bin/tsc -p tsconfig.app.json --noEmit`：通过，无诊断。

Task 3／4 页面尚未接入新的决定、确认、审核动作；这是计划中的后续范围，不是本任务通过旧页面绕过状态机的兼容许可。
