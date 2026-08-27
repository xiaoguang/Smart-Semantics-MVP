# Task 3：Codex 式主时间线与事实检查器实施报告

## 完成范围

本 Checkpoint 只为 `guanyijia_erp` 增加连续时间线工作台。`group_retail_ops` 与未知系统继续进入原 `LegacyDataStandardizationPage`；未修改黄金 V1 JSON、Catalog seed、零售 Fixture、正式 Artifact 或旧 runtime，也未实现 Checkpoint 4–7 的冲突解决、结构化编辑、assistant composer、产物冻结与 M4 交接。

### 深 Runtime seam

- 新增 `guanyijia-workbench-runtime.ts`，页面只通过 `read(actorUserId)` 与 `execute(command)` 使用工作台。它内部组合 `StandardizationRunRuntime`、`GuanyijiaStandardizationStory` 与 `SourceDocumentRuntime`，支持的命令严格限于 `START_RUN`、`READ_NEXT_SOURCE`、`COMPLETE_CURRENT_DOCUMENT_REVIEW`。
- 五源身份与 canonical batchId 来自冻结故事。活动运行指针按 batchId 隔离，保存 project、故事版本、稳定 runId 与工作台 command 指纹；恢复只按 runId 读取，损坏、跨项目或跨故事版本指针明确失败。创建复用和恢复还会逐项校验五个 sourceId、sourceName、order，拒绝同 batch 的外部或残缺运行；合法的对象原型名 commandId 也按自有属性处理，不会误判为已使用命令。
- 每次读取真实编译一个来源并只登记一份九段来源文档。`COMPLETE_SOURCE_DOCUMENT` 事件只保存 documentId、revision、标题、章节数与 block 分类计数，不内联 Markdown 或 Evidence 正文。
- 完成审阅先把来源文档标记为 `READY_FOR_ALIGNMENT`，再推进运行。MySQL 完成后唯一下一动作是读取 GitHub；GitHub 完成后在具体欠款字段差异进入 `CONFLICT_BLOCKED`，只显示“处理1项来源差异（下一Checkpoint实现）”。
- snapshot 只投影轻量时间线、当前来源／文档、唯一下一动作和事实检查器。已完成事件是回执；只有真正未完成的读取、文档或冲突项保持展开。63 个 pending 资产不进入时间线，在文档中按每页 20 个 block 读取。

### 页面与交互

- 新增 `GuanyijiaStandardizationWorkbench`、`StandardizationTimeline` 与 `StandardizationFactsInspector`。管伊佳页顶部只保留故事名称、五源进度与次级“来源设置”；主区显示连续事件、当前任务和结构化九段文档，右侧只显示轻量事实与血缘。
- 文档按 FACT／INFERENCE／GAP／CONFLICT 分组，不默认显示原始 Markdown 或 Evidence ID。审阅动作使用生产导航 seam 设置当前 documentId、首个非 FACT 章节与稳定 focus target；重复打开仍滚动并聚焦标题。
- 桌面主文档最大宽度 920px，事实检查器宽度为 `clamp(360px, 30vw, 420px)`。900–1279px 使用 inspector overlay；低于 900px 时文档与 inspector 使用全屏子页，关闭后恢复主线滚动、章节选择与焦点。390px 规则继续收紧按钮与内容布局，避免横向溢出。
- 页面路由只在 `activeSystem === 'guanyijia_erp'` 时选择新工作台；原实现被原样保留为 `LegacyDataStandardizationPage`，没有复制或改写零售业务链。

## TDD 证据

实现按公开 seam 逐层 RED → GREEN：

1. 首个 runtime 测试因 `guanyijia-workbench-runtime.ts` 不存在而以 `ERR_MODULE_NOT_FOUND` RED；先实现 `START_RUN`，再逐一用失败测试推动 `READ_NEXT_SOURCE` 与 `COMPLETE_CURRENT_DOCUMENT_REVIEW`。
2. 时间线、审阅导航、20 项分页、facts inspector 与页面分流分别先以缺失导出或缺失投影 RED，再加入最小生产状态 seam 并转 GREEN。
3. GitHub 测试先因没有具体冲突投影 RED；实现运行事件到欠款字段差异和影响对象的轻量投影后转 GREEN，并锁定不能继续读取。
4. 重复点击测试先暴露工作台指针没有外层命令指纹；加入指纹持久化后，文档数、事件数与 revision 保持不变。
5. 故事版本隔离测试先暴露全局活动指针；指针键改为 canonical batchId 后，不同五源快照版本不再互相恢复。
6. `constructor` commandId 测试明确复现对象原型属性被误认为历史命令的 RED；读取改为 `Object.hasOwn` 后，同一命令可正常执行并幂等重试。
7. 首轮独立复审确认生产 story 仍从旧 final Artifact 读取官方 Evidence 和三个真实来源身份。依赖边界测试先以生产文件命中 `guanyijia-modeling-baseline` RED；新增独立 `official-documents-fixture.ts` 并让 story 从冻结 MySQL／GitHub manifest 与官方 fixture 取身份、证据、locator 后转 GREEN，黄金 Artifact 只保留在保护测试中。
8. 首轮复审还构造出同 project／batch 的非五源既有 run。测试先因底层 `CREATE_RUN` 复用该运行且工作台接受它而 RED；创建返回和指针恢复现在都严格验证五个 sourceId、sourceName、order，拒绝后不写活动指针。

## 数据与容量边界

- 时间线只消费运行元数据和小型事件摘要，不读取事件正文来渲染列表，也不包含 block ID 或 Evidence ID。
- 当前文档来自 `SourceDocumentRuntime` 已登记的独立来源文档；生产 story 和页面都不读取旧 final Artifact 来取得来源身份、官方 Evidence 或构造来源内容。
- 事实检查器仅展示来源标识、REAL／DEMO／DERIVED、快照／Commit／版本、authority、读取数量、当前 locator、血缘和影响对象。Semantica 固定说明为“派生来源，不增加独立佐证数”。
- 本 Checkpoint 没有启动开发服务器、浏览器或远端服务。响应式结论来自生产布局规则与直接行为 seam；未把它描述为截图或像素级 E2E 验证。

## 直接验证

- `node --experimental-strip-types --test src/features/data-standardization/guanyijia-workbench-runtime.test.ts src/features/data-standardization/data-standardization.test.ts`：19/19 通过。
- 加入直接受影响的 `src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts` 后：30/30 通过。
- `npx tsc -p tsconfig.app.json --noEmit`：通过，无诊断。
- 对本 Checkpoint 与直接修复文件运行定向 `oxlint`：通过，无 warning 或 error。
- `git diff --check`：通过；对本 Checkpoint 代码、测试、CSS 与文档执行尾随空白扫描，无匹配。

## 独立只读复审

首轮结论为 Spec FAIL／Quality FAIL：一项 Critical 是生产 story 从旧 final Artifact 构建官方来源文档；一项 Important 是同 batch 的非五源运行可被复用或恢复。两项都先由可执行 RED 复现，再按上述独立 fixture 与严格运行身份校验修复。

同一独立只读 reviewer 最终复核：

- Spec：PASS。
- Quality：PASS。
- Critical／Important／Minor：0／0／0。

Reviewer 同时复核其余 brief 边界仍成立：严格五源与黄金 V1 保护、稳定 runId 恢复、轻量 payload、CAS／幂等、九段与 20 项分页、审阅 section／focus、时间线回执／当前态、GitHub 具体冲突阻断、事实检查器边界、1280／900／390 布局规则、零售 legacy 分流和 Checkpoint 4–7 禁止范围。
