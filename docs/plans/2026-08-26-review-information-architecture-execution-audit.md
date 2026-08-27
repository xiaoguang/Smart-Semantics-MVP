# 审阅信息架构与全 Demo 文案净化：执行状态核查

核查时间：2026-08-26。本文只记录当前工作树与已经实际执行的定向命令。它不把“存在一段代码”或“某个默认页面能打开”误写成全流程验收。

## 固定边界

本轮没有删除或覆盖 `../linguan-prototype-v2-backup/`、`../linguan-prototype-v2-discarded-20260825/`、旧内容快照、正式管伊佳 V1、零售 V1/V2、原始 SQL／DDL、Pinned Bundle 或黄金摘要；也没有重新连接 MySQL、GitHub、外部文档站点或运行 OCR、扫描器。

普通运行、测试、构建和恢复仍只读取冻结材料。浏览器测试在 `5202` 临时端口运行完毕后已关闭。

## 已完成并已验证

### 1. 正确性门禁

- 控制器在打开历史文档时拒绝完成审阅、修改和冲突处理等写命令；不能只靠 UI 隐藏按钮。
- 来源或运行状态读取失败时，来源板被锁定，只保留恢复或重新读取路径。
- 来源管理的连接、快照与运行前绑定合同通过 21 条定向测试。

### 2. 审阅信息架构

- 工作台工具行使用进度、`来源资料`和`运行来源`；页面不再以旧 formal block 的 11／3／67／0 作为业务统计。
- `审阅结论`是关键结论、资料缺口和对象明细的精确阅读集合；`待核对`、`来源材料`、`跨源发现`分别保留自己的精确 ID 集合。
- 所有五个来源使用相同的两页签合同：`审阅清单`与`标准化文档`；独立“依据追踪”页签已移除。标准化文档默认提供连续的业务阅读版，并可切换到同一 Revision 的只读 Markdown 源文；它不重复任务卡、跨源发现或依据按钮。
- 依据在所属结论原位展开，数据库优先显示字段结构表，源码与 DDL 是按需展开的可复核材料。
- Markdown 定位可以返回对应结论并恢复位置和焦点。
- 历史版本显示各自的 `createdAt`、版本归属和该版修改摘要；连续修订不再使用相同的“更新时间”。

### 3. 唯一可读内容投影与质量门禁

- `ValidatedReviewContentBundle` 已是五源审阅清单、Markdown 与内联依据的共同已校验投影。
- 当前不可变内容版本 `guanyijia-demo-content-v4-20260826` 已由一次已登录的 Codex ChatGPT session、Luna high 维护生成；其 generation manifest 记录了 session、输入／输出摘要和 prompt 版本，内容与浏览器投影均已校验。
- Claim 显式绑定章节、业务用途、Markdown 锚点和全部依据；不再使用序号轮询分章、空章节占位或 Markdown 邻近文本反推结论。
- GitHub 文档不再泄漏 TRACE、HTML 注释、完整提交摘要或跨源判断；文档和制度来源只显示相关章节摘录，术语图以术语／关系阅读视图呈现，不显示原始 JSON。
- 新增并启用文案门禁：拒绝乱码、HTML 实体、内部标识、原始 JSON、重复文本、超过 140 字或超过三句的可见段落。已修正“整段演示制度文本直接进入依据区”的问题，改为最多三句的可读摘录，原始材料不变。

### 4. 全 Demo 的默认可见文案

- 数据标准化、AI 建模、本体建模、指标配置、业务规则、同义词、时间语义的默认可见页面均通过内部标识和 HTML 实体泄漏审计。
- 已在五源主故事中覆盖数据库、GitHub、业务说明、制度草案和术语图的审阅页面；GitHub 只显示已保存代码片段，不冒充完整源码。
- 业务页面不默认显示 SHA、内部 Ref／ID、内部枚举、原始 JSON 或底层错误消息。用户主动打开源码／DDL时保留表名、字段名、路径和行号以便复核。

### 5. 布局与交互

- 覆盖文档层时，`来源资料`触发器属于当前文档层，不再被底层工作台遮挡。
- 已执行完整 16 项响应式矩阵，覆盖 1440、1280、1024、768、390、320、720×500、内联资料区、抽屉、全屏文档、冲突预览与完整审阅状态矩阵；全部通过。
- 剧本化修改、原位依据、跨源发现、资料区焦点与滚动反馈在数据库审阅 E2E 中通过。
- 中等宽度的“来源资料”以抽屉打开／关闭，窄屏为全屏层；右栏上半区固定为五个来源，下半区为默认展开、可回看的业务流程。双方来源材料仅在对应发现下方原位展开，资料详情以审阅助手的单例“当前讨论”承载。

## 尚未完成的部分

### 深层可达页面的人工逐屏审校

自动化已覆盖全部主页面默认首屏、五源完整故事和代表响应式状态；它不等同于人工逐一打开每个 AI 建模／本体建模子对话框后的语言编辑审校。该工作未在本轮假装完成，已登记为[延后任务](future-ai-ontology-deep-copy-audit-todo.md)：待下一轮 AI／本体页面稳定后，再人工逐屏执行。

2026-08-26 的浏览器审校准备已确认本地应用可启动，但进入这些深层页面需要在浏览器中输入本地演示账号密码。该输入属于需要操作当下确认的登录动作；没有绕过登录或读取浏览器存储。因此这里仍如实保持“未执行”。

## 已执行验证

```text
npx tsc -b --pretty false
通过

npm run evidence:guanyijia:content:check
npm run evidence:guanyijia:content:package:check
node --test scripts/evidence/guanyijia-demo-content-generate.test.mjs
通过（当前 v4 快照及其打包投影）

npm run test:source-management
通过：21 / 21

node --experimental-strip-types --test \
  src/features/data-standardization/business-copy-quality.test.ts \
  src/features/guanyijia-evidence-factory/source-review-document.test.ts
通过：27 / 27

npm run test:data-standardization
通过：76 / 76

node scripts/run-cp8-playwright.mjs \
  tests/e2e/demo-copy-audit.spec.ts \
  tests/e2e/guanyijia-curated-review.spec.ts
通过：7 / 7

node --experimental-strip-types --test \
  src/features/data-standardization/finding-expansion.test.ts \
  src/features/data-standardization/standardized-document-projection.test.ts \
  src/features/data-standardization/standardized-document-reader.test.ts
通过：3 / 3

node scripts/run-cp8-playwright.mjs tests/e2e/guanyijia-document-reentry.spec.ts
通过：4 / 4

node scripts/run-cp8-playwright.mjs \
  tests/e2e/guanyijia-curated-review.spec.ts \
  tests/e2e/guanyijia-five-source-story.spec.ts
通过：7 / 7

node scripts/run-cp8-playwright.mjs tests/e2e/standardization-responsive.spec.ts
通过：16 / 16

node scripts/run-cp8-playwright.mjs tests/e2e/guanyijia-five-source-story.spec.ts
通过：1 / 1

node scripts/run-cp8-playwright.mjs \
  tests/e2e/standardization-protected.spec.ts \
  tests/e2e/standardization-zoom.spec.ts
通过：5 / 5

npx tsc -b --pretty false
通过

npm run build
通过

npm run evidence:guanyijia:content:package:check
通过（只校验已冻结内容包；不读取外部来源、不调用模型）

git diff --check
通过
```

上述浏览器命令全部在临时 `5202` 端口完成并关闭。根仓的 `git diff --check` 已通过；这个原型目录在根仓中未跟踪，因此该命令不能替代文件级审阅或已经执行的类型、构建与浏览器验证。

## 结论

| 计划区域 | 当前状态 |
| --- | --- |
| 正确性门禁 | 已完成并定向验证 |
| 审阅清单、统计、内联依据、Markdown 返回、历史版本 | 已完成并定向验证 |
| 五源唯一阅读投影与 v4 可读内容净化 | 已完成并定向验证 |
| 主页面与五源主故事的文案净化 | 已完成并定向验证 |
| 全宽度响应式矩阵 | 已完成并定向验证 |
| 新的不可变 Luna 内容版本（v4） | 已完成并通过快照／投影校验 |
| 深层 AI／本体子对话框的人工逐屏语言编辑 | 未执行 |
| 标准化主故事、保护／缩放路径与构建 | 已完成并定向验证 |
| 根仓差异检查 | 已通过 |

所以，不能把整份计划称为“无条件全部完成”。本地实现、一次性内容生成与直接自动化覆盖的部分已经收口；深层人工审校仍必须如实保留。
