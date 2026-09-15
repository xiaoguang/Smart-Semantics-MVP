# ProcessMaterialAssembler

## 为什么存在

索引卡足以发现候选，但不足以写具体规则。若过程模型只看到卡片或短摘要，它会产生“状态允许时修改”这类空话。本模块按候选成员重新打开完整 Activity，并让模型可以选择性核对已保存源码。

## Interface

```java
ProcessMaterial assemble(
    CandidateProcess candidate,
    FrozenAnalysisCorpus corpus,
    ProcessMaterialProfile profile);

ProcessReviewMaterial resolveSourceRequests(
    ProcessMaterial material,
    CandidateProcessDraft draft,
    FrozenAnalysisCorpus corpus);
```

第一步在 DRAFT 前执行；第二步只解析 DRAFT 中已允许的 ref 请求，为 REVIEW 增加源码片段。两者均为零 Provider、零导航操作。

## 程序工作

- 按候选 ActivityId 读取完整 ReviewedActivity，不使用卡片替代。
- 保留 purpose、participants、objects、inputs、conditions、activitySteps、results、rules、formulas、questions、limitations 和 refs。
- 给可引用数组项生成 ActivityStatementRef。
- 提供短 SourceRef 目录：ref、所属 Activity/方法、片段用途摘要；完整文件/行号留在程序侧。
- 根据候选用途提示通用 Activity 可能包含多个 variant，但不替模型选择销售/采购等分支。
- DRAFT 请求 ref 后，从保存的源码/JDT正文返回选中的真实片段；未知 ref 失败。

## 输出

`ProcessMaterial` 是候选 DRAFT 的自包含输入；`ProcessReviewMaterial` 额外包含实际 DRAFT 和其请求的源码片段。共享 Activity 可以进入多个候选材料，保持不可变。

## 预算

先保留完整 Activity 的条件、规则和结果，再去除重复技术元数据。若单候选仍过大，退回 Cataloger 的候选拆分决策或记 NOT_PROCESSED_CAPACITY；不能按数组中间截断后声称完整。

## 失败

候选引用未知 Activity、Activity 与 corpus 来源不一致、statement handle 越界、SourceRef 不在 Activity allowlist、保存源码缺失均明确失败。失败不重跑 JDT，也不向模型发送半包。

## 下游保证

Reconstructor 可以逐字访问具体规则，并在 REVIEW 中核对真正重要的源码，而无需读取五图、hash 或完整证据链。

## 测试与当前成熟度

Assembler 已实现：目录卡只负责召回，候选请求重新装入完整 Activity、稳定 statement handle 与允许的 SourceRef；DRAFT 请求的源码在 REVIEW 前由程序从固定 corpus 解析。直接测试覆盖重叠 ActivityUse、非法引用和保存结果复用；真实仓库材料质量仍在最终验收中检查。
