# BusinessProcessPublisher

## 为什么存在

过程模型的直接输出需要经过引用和覆盖检查，并形成可供人审阅、可供九章继续使用的稳定结果。发布器不做语义判断，也不让 Markdown 格式成为新的模型任务。

## Interface

```java
BusinessProcessPublication publish(ProcessDiscoveryResult result);
```

输入必须是一个封闭的 `ProcessDiscoveryResult`，其中包含已归并目录、Activity/候选 coverage、过程 knowledge items、`directActivityKnowledgeItems`、SourceRef 集和输出运行身份。一次调用只发布一份仓库结果；发布器不重新打开 ReviewedActivity 或 JDT/source corpus。

## 程序工作

- 校验所有 Activity、ActivityUse、stage、rule、CatalogKnowledgeItem、process relation、statement ref 和 SourceRef 闭合。
- 校验每个 Activity 至少有过程 membership、支撑/独立或未分类处置。
- 校验 Discovery 已为 SUPPORT/STANDALONE/UNCLASSIFIED Activity 提供确定性 `directActivityKnowledgeItems`，且 owner 同时保留 ActivityId 与 disposition；不重新投影，不生成虚假 Process。
- 区分 semantic completeness 与 coverage completeness。
- Canonical 保存结构化结果，确定性渲染 `business-processes.md`。
- Markdown 使用业务语言和短 ref；完整代码仍在 `source-refs.jsonl`，不附几千行源码。

## 主 Markdown 结构

```text
# 仓库业务过程
## 业务目录
## <过程名称>
### 目的与适用范围
### 参与者与业务对象
### 过程步骤与分支
### 重要业务规则
### 结束结果
### 支撑活动与相关过程
### 待确认联系
### 来源引用
## 未归类与未处理范围
```

章节数不固定为九；每个过程按同一模板输出。它是业务过程验收产物，不是另一个 Activity 列表。

## 文件

正式交付：

- `repository-business-process-catalog.json`
- `process-coverage.json`
- `business-processes.md`
- 已有 `source-refs.jsonl`

可复用内部检查点：

- `activity-index-cards.jsonl`
- `business-process-candidates.jsonl`
- `reviewed-business-processes.jsonl`

内部检查点不是读者证据链，不要求九章模型读取它们。

## 失败

未知 ref、缺 Activity 处置、Process 无阶段也无明确 insufficient disposition、Markdown 丢失 JSON 中的条件/规则/知识项/结果、把 PARTIAL 写成完整均停止发布。无候选过程可以发布诚实的空目录和全部未分类原因，但不能宣称识别完成。PARTIAL 只接受已经闭合的容量或语义处置；Provider transport/runtime/schema、坏响应或 uncertain started request 是 fatal，不产生可供本发布器安装的正式结果。

## 下游保证

Step08 只从已发布 catalog 和 coverage 生成仓库级九章；catalog 已同时携带过程知识项和未入过程 Activity 的确定性知识投影，因此它不再读 326 个 Activity 重新发现过程或补章节。纯重渲染和来源查询零 Provider 调用。

## 测试与当前成熟度

通过同一 catalog 重渲染应逐字节一致；每个 JSON 规则在 Markdown 可见；所有短 ref 可查询；九章输入只含已归并过程。当前已有 BusinessReport 的 renderer 和 SourceRef 保存可借鉴，但本发布器尚未实现。
