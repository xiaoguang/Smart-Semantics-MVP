# Task 2：管伊佳五源 Fixture、来源文档与派生血缘执行报告

## 结论

Checkpoint 2 已实现为 `src/features/guanyijia-standardization-story/` 深模块。唯一业务 seam 由 `createGuanyijiaStandardizationStory({ policyDocuments? })` 创建，公开 `listSources()`、`compileSource({ sourceId, priorCompilations })` 与 `listConflictDefinitions()`。编译确定性、无存储副作用；本任务没有注册来源文档、推进标准化运行、修改 UI、Catalog、黄金 JSON、旧候选 Fixture 或 `modeling-evidence/`。

三份演示制度保存在模块的 `demo-policy/` 目录，并由冻结默认输入逐字对照测试保护。构造注入采用 `{ path, version, content }`，三者共同参与 snapshot 指纹；每个文件 locator 另保存内容 SHA-256。Semantica snapshot 由 policy snapshot 派生，只接受 `priorCompilations` 中同 snapshot 的制度编译作为有效上游。

## RED 证据

所有行为均通过定向 Node test 形成 RED → GREEN vertical slice：

1. 首次运行 `node --experimental-strip-types --test src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts`，退出码 `1`，`fail 1 / pass 0`。错误为 `ERR_MODULE_NOT_FOUND`，缺少测试要求的 `index.ts`；这是新 seam 缺失，不是既有模块故障。
2. 五源身份最小 GREEN 后，MySQL tracer 首跑为 `fail 1 / pass 0`，错误 `来源编译尚未实现`；实现当前 manifest 读取、81 个 blocks 与确定性投影后通过。
3. GitHub tracer 首跑为 `fail 1 / pass 0`，错误 `来源编译尚未实现：guanyijia_github`；实现固定 Commit、工作流和 debt 冲突后通过。
4. 官方文档 tracer 首跑为 `fail 1 / pass 0`，错误 `来源编译尚未实现：guanyijia_official_docs`；接入冻结 14 条官方 Evidence 后通过。
5. 演示制度 tracer 首跑为 `fail 1 / pass 0`，错误 `来源编译尚未实现：guanyijia_demo_policy`；实现三文件 parser、locator 与两项冲突后通过。
6. Semantica tracer 首跑为 `fail 1 / pass 0`，错误 `来源编译尚未实现：guanyijia_semantica_demo`；实现有效派生和缺上游降级分支后通过。
7. 制度变更隔离 tracer 首跑为 `fail 1 / pass 0`；具体失败是制度 locator 的 `contentSha256` 为 `undefined`。补入每文件内容 SHA-256 后通过，并证明只改变负库存 block、policy/semantica snapshot 与对应冲突 variant，三个真实来源编译不变。
8. 完成前只读审查指出 prior 完整性只按来源身份判断。新增损坏上游与空壳冲突 prior 的两个 tracer，首跑为 `fail 2 / pass 0`：删除制度 block/assertion Evidence、删除 locator 或损坏 Markdown 后 Semantica 实际仍为 `DERIVED_VALID`；清空 MySQL blocks 后 GitHub 实际仍引入 debt 冲突。加入投影完整性校验和逐 variant 校验后，该定向组为 `pass 2 / fail 0`。

黄金保护属于既有不变量，首跑即 GREEN；测试内另对 clone 删除一项 pending asset，保护断言能检出 63 → 62 的变异，避免把常量自等误当有效保护。

## GREEN 与实际计数

最终定向测试：

```text
tests 10
pass 10
fail 0
exit 0
```

逐源完整顺序编译的实际结果：

| 来源 | 九段 | blocks | assertions | 当前来源读取 Evidence | 本文引用 / locator | delta（新增 / 变更 / 新 gap） | 血缘 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `guanyijia_mysql` | 9 | 81 | 81 | 1458 | 75 / 75 | 81 / 0 / 4 | ROOT |
| `guanyijia_github` | 9 | 24 | 24 | 2252 | 20 / 20 | 22 / 2 / 2 | ROOT |
| `guanyijia_official_docs` | 9 | 17 | 17 | 14 | 14 / 14 | 9 / 8 / 1 | ROOT |
| `guanyijia_demo_policy` | 9 | 10 | 10 | 4 | 4 / 4 | 7 / 3 / 2 | ROOT |
| `guanyijia_semantica_demo` | 9 | 11 | 11 | 11 条派生 statement | 15 / 15（11 派生 + 4 上游） | 11 / 0 / 1 | DERIVED_VALID |

Semantica 缺少匹配 policy compilation 时仍有九段 section，但只生成 1 个 lineage gap block/assertion，状态为 `UPSTREAM_MISSING`，不产生 FACT。

MySQL 当前 manifest 实际值为：95 tables、2 views、1130 columns、270 indexes、35 procedures、0 functions、0 triggers、1 event、2 foreign keys、58 tenant tables、2 tenant views、17 DML digests，Evidence 文件总数 1458。保留 1 个 profile timeout 与 2 个无稳定主键 view sample skipped warning。63 项 `PENDING_ASSET` 是部署 TABLE Evidence 与 GitHub 32 核心 TABLE Evidence 的差集，不使用旧 93/1114 计数。

GitHub 当前 manifest 固定 `b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1`：436 selected files、32 tables、31 services、30 controllers、104 entities/VOs、61 Mapper Java、61 Mapper XML、573 Mapper statements、2 constants、1 tenancy file、142 UI vocabulary files、1 migration file、642 workflow methods、570 migration operations，Evidence 文件总数 2252。

官方核心文档 snapshot 为 `gyjerp-official-docs-20260813T031656Z`，读取摘要保持 4 pages / 20 claims，并完整使用当前 14 条冻结官方 Evidence 与 locator。

## 冲突与血缘

- `guanyijia_github` 在 MySQL `DEPLOYED_SCHEMA_NO_DEBT` 基准之后引入 `gyj-conflict-debt-schema`，incoming variant 为 `SOURCE_SCHEMA_HAS_DEBT`。
- `guanyijia_demo_policy` 在 GitHub 之后引入 `gyj-conflict-negative-stock`（`TENANT_CONFIG_CONTROLS` 对 `ALWAYS_FORBIDDEN`）和 `gyj-conflict-status-nine`（`REVIEWING_UNCONFIRMED_ENUM` 对 `PENDING_REVIEW`）。
- `guanyijia_semantica_demo` 不引入独立冲突，只 corroborate negative-stock 与 status-nine；五源中仍只有前四个 ROOT。
- Semantica 在派生前核对制度 compilation 的 blocks/assertions、sections、Markdown/hash、四条关键 block/Evidence 及 path/version/content SHA locator；删除任一上游引用或损坏正文都会降级为 `UPSTREAM_MISSING`。
- 三个冲突按 frozen snapshot、stableCode、normalized variant、对应 assertion、Evidence 与 locator 独立触发；空壳或错快照 prior 不触发，单项损坏不会阻断另一项仍可信的制度冲突。
- 制度库存时点条款保留为建议与实现证据缺口；`gap.current_stock_as_of` 和 63 项 `PENDING_ASSET` 只由 MySQL 编译产生。

## 黄金保护

编译五源后重新验证：

- Artifact ID：`artifact-guanyijia-v1-40c8572864bd`
- Markdown SHA-256：`5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210`
- Semantic payload SHA-256：`c55c2ebaf3e6cdc92d4ba5115692253a7f6b7e4442eeb44f5c2360a887594248`
- 对象计数：14 / 9 / 296 / 30 / 4 / 5 / 2 / 8 / 10 / 9 / 63 / 2
- 正式 Evidence Context 来源仍严格为 `guanyijia_mysql`、`guanyijia_github`、`guanyijia_official_docs`；不存在 demo-policy 或 Semantica source/evidence URI。

## 验证

```text
node --experimental-strip-types --test src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts
pass 10 / fail 0 / exit 0

npx tsc -p tsconfig.app.json --noEmit
exit 0

git diff --check
exit 0
```

TypeScript 首次检查只发现并定位到 `compiler.ts` 的一个未使用 type import（TS6133）；删除该残留导入后同一命令通过。仓库根目录当前把整个 `linguan-prototype-v2/` 显示为未跟踪目录，因此 `git diff --check` 只能证明已有跟踪 diff 没有空白错误，不能覆盖该目录内的新文件；另对本任务文件执行尾随空白扫描，结果为空。该共享工作区限制不会通过添加、提交或改写用户目录规避。

## 关注边界

- 本模块只编译冻结事实，没有把结果登记到 `SourceDocumentRuntime` 或 `StandardizationRunRuntime`；这是后续 Checkpoint 的明确职责。
- 冲突定义只有 variants、章节、影响对象和默认结果草案；解决命令、真实 patch 与 Git hunk 尚未实现。
- 默认制度正文同时以人类可读 `.md` 资产和可直接用于浏览器/Node 的冻结 TypeScript 输入存在；定向测试逐字比较两者，防止双份内容漂移。若后续构建链增加可靠的 raw Markdown 跨 Node/Vite loader，应收敛为单一文件导入。
- 当前 compiler 直接读取完整冻结 MySQL/GitHub fixture 以保证 manifest 与 locator 不漂移。模块尚未接入 UI；若后续打入首屏 bundle，应在不改变本 seam 的前提下生成瘦身 manifest，而不是回退为手写计数。
