# 管伊佳固定来源快照

`guanyijia-five-source-v1` 的 Demo 证据不是每次运行时扫描得到的。仓库内的
`src/features/guanyijia-standardization-story/pinned-source-snapshot.generated.ts` 是版本化、
不可变的预编译 Bundle，包含五份来源文档的读取摘要、九段 Markdown、结构化识别项、断言、
证据定位和来源差异投影。`pinned-source-snapshot.ts` 在使用前校验来源身份和 Bundle SHA，
并为阅读界面投影结论内依据与人类可读证据摘录。结论内依据只说明单一来源内部的“来源依据 → 审阅结论 → 文档段落”；跨来源关系必须等待参与来源被本次运行准入。

普通 Demo、页面刷新、测试、构建、热更新和“恢复演示”均只读取此 Bundle。浏览器
`localStorage` 是可删除的镜像层，不是证据来源；删除它后应用会恢复 Bundle，绝不自动回源扫描。
Bundle 缺失或校验失败会阻止运行，并提示“演示快照不可用”。

## 维护边界

仅在明确批准更新冻结来源或编译器语义后，才可以执行：

```bash
npm run snapshot:guanyijia:refresh
```

该命令只根据仓库内已有的冻结输入重新编译并写入 Bundle。它不连接数据库、不访问 GitHub、
不抓取文档站点、不会执行 OCR，也不会调用 LLM。若要重新扫描任何外部来源，必须先取得单独的
维护授权，生成可审计的新冻结输入，再运行此维护命令。不要在普通代码修改、Demo 恢复或测试失败时
运行刷新命令。

旧 Bundle 应保留在版本控制中，以便比较、审阅和回滚。用户在 Demo 内修改来源文档时，会创建独立
revision；这些 revision 不会反写或替换固定 Bundle。

当前真实治理 Demo 还会读取一个候选证据叠加层：它从同一批已冻结材料中挑选少量真实片段，用于展示互证、结构冲突、时间漂移和资料缺口。该候选层不回源、不读取业务行，也不是正式 V2；参见 [多源证据治理参考架构](architecture/multi-source-evidence-governance.md)。

与正式来源 Bundle 分离的丰富 Demo 阅读材料保存在 `modeling-evidence/guanyijia/demo-content/snapshots/` 的
内容 sidecar。最近一次已冻结的富内容版本是 `guanyijia-demo-content-v7-20260827`，并通过 `DemoContentReview` 接入资料审阅工作台：启动运行时
将 `runId`、五项正式 `snapshotId` 与内容包 SHA 精确绑定，后续刷新、继续审阅与恢复都只读同一版本。它提供固定
Git 源码树的故事行段、演示业务资料、ERP 制度和术语图，但不产生正式证据链或修改 V1；身份或摘要损坏会让受影响
来源 fail closed，而不会回退旧模板。V7 以精确 V6 为不可变前序，只改善人类阅读投影；V6 和更早版本继续供历史运行精确读取。其维护、打包、校验与回滚规则见 [Demo 内容快照维护手册](guanyijia-demo-content-snapshot.md)。
