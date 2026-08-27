# Task 1：标准化运行、事件和逐源状态机执行报告

## 结论

Checkpoint 1 已实现为 `src/features/standardization-run/` 深模块。公共面由 `types.ts`、`StandardizationMetadataStore` 和 `createStandardizationRunRuntime({ metadataStore?, metadataStorage?, contentStore, now? })` 组成；运行读取严格绑定 `runId`，Storage adapter 的元数据键为 `linguan:standardization-runs:v1`，事件正文只通过 `payloadRef` 进入现有 `ContentAddressedStore`。

本任务未修改 Fixture、Catalog、UI、旧前端或后端，未启动服务，也未运行全仓测试。

## RED 证据

先新增真实行为测试，再运行：

```text
node --experimental-strip-types --test src/features/standardization-run/standardization-run.test.ts
```

首次结果：退出码 `1`。Node 报告 `ERR_MODULE_NOT_FOUND`，缺少测试所要求的 `src/features/standardization-run/runtime.ts`；测试文件自身被计为 `fail 1 / pass 0`。失败原因是新运行 seam 尚不存在，不是既有功能失败。

独立复审后继续逐项执行 RED：

1. 并发提交：`--test-name-pattern='并发命令'` 为 `fail 1 / pass 0`，两个 `Promise.allSettled` 结果实际均为 `fulfilled`，证明相同 expectedRevision 会互相覆盖。
2. 命令指纹：`--test-name-pattern='复用 commandId|跨项目复用|旧版 string'` 为 `fail 3 / pass 0`，三项均报告 `Missing expected rejection`，证明只绑定 runId 会伪幂等。
3. 损坏元数据：不可解析 JSON、重复 runId、过小 sequence 的定向测试为 `fail 3 / pass 0`；不支持 schemaVersion 的补充定向测试也为 `fail 1 / pass 0`。当前实现均未拒绝。
4. 载荷校验：`--test-name-pattern='独立复算 sha256'` 为 `fail 1 / pass 0`，篡改正文没有触发 rejection。
5. 五源覆盖：完整推进五来源的测试在正确实现上首跑即 GREEN，因此它是覆盖缺口而非现存功能缺陷。为证明测试有效，临时把状态机变异为“两个来源对齐即 READY_FOR_OUTPUT”，定向测试立即 `fail 1 / pass 0`，实际状态为 `READY_FOR_OUTPUT`、期望 `READY`；随后恢复正确生产逻辑。
6. 数值与身份边界：`--test-name-pattern='空 commandId|非整数 revision'` 为 `fail 2 / pass 0`，空 ID 与 NaN revision 均报告 `Missing expected rejection`。

第二轮独立复审继续逐项执行 RED：

1. 跨 Runtime 原子性：两个 Runtime 共享同一 `MemoryStorage` 和延迟内容存储，并发 `START_NEXT_SOURCE` 的定向组实际得到两个 `fulfilled`，期望一个成功、一个 revision 冲突，结果为 `fail 1 / pass 2`。同 realm 的同步 `CREATE_RUN` 回归首跑 GREEN；它用于守住 sequence 和 runId，不伪装成现存失败。
2. 嵌套元数据：run status、来源 `null`、重复 sourceId、断裂 order、非法 source status、破损 conflict 数组、不完整文档字段，以及时间线 `null`、断裂 sequence、错 runId、非法 type、空 actor、非法 payloadRef、重复 eventId 共 14 个代表场景全部先 `fail 14 / pass 0`，均为 `Missing expected rejection`。
3. 原型键 commandId：`toString`、`constructor`、`__proto__` 三个测试全部先失败，均被继承属性误判为已有命令并抛出“commandId已用于不同命令”。

第三轮独立复审继续逐项执行 RED：

1. 公共锁 seam 与浏览器降级：`--test-name-pattern='注入锁|浏览器没有 Web Locks|浏览器缺少 Web Locks|租约在内容写入后|相同 sourceName'` 为 `fail 5 / pass 0`。注入锁调用记录实际为空；浏览器锁模块不存在；租约失效测试报告 `Missing expected rejection`；同名来源创建后读取被 `sourceName重复` 拒绝。
2. 状态组合不变量：`--test-name-pattern='状态组合校验拒绝'` 为 `fail 5 / pass 0`。`READY + READING`、`READY_FOR_OUTPUT + PENDING`、缺交付身份的 `FROZEN`、没有未决冲突的 `CONFLICT_BLOCKED`、仍有未决冲突的 `ALIGNED` 全部报告 `Missing expected rejection`。

第四轮独立复审确认租约锁存在 fencing 空窗后继续逐项执行 RED：

1. 原子 metadata store：`--test-name-pattern='共享原子 metadata|浏览器 metadata store'` 为 `fail 3 / pass 0`。旧 Runtime 忽略注入 store，并发 START 返回旧 revision 错误而没有触发 CAS；并发 CREATE 实际两个都成功；浏览器原子 store 模块不存在。
2. 来源前缀和交付一致性：`--test-name-pattern='已对齐前缀|偷偷携带|缺少对应 REVIEW_SUBMITTED|payloadRef 必须与当前'` 为 `fail 4 / pass 0`，四项均报告 `Missing expected rejection`，证明旧加载器允许 `PENDING,ALIGNED,PENDING`、早期交付身份、缺失审核事件及 identity/payloadRef 分叉。

## GREEN 证据

写入最小 `types.ts` 与 `runtime.ts` 后首次运行有 `5 pass / 1 fail`。唯一失败是测试使用 `/当前正在读取的来源不是 GitHub/`，而实现返回了包含完整来源名的中文错误 `当前正在读取的来源不是 管伊佳 GitHub`；将测试收窄目标改为验证“错来源 + GitHub”语义的 `/当前正在读取的来源不是 .*GitHub/`，未改变生产逻辑。

初始实现相同定向测试结果：

```text
tests 6
pass 6
fail 0
exit 0
```

四轮复审后的最终实现以原子 metadata store 替代租约锁：浏览器使用 IndexedDB 单记录 transaction CAS，Node/测试 Storage adapter 在共享队列内复读并 CAS；同时保留 `commandId → { runId, fingerprint }`、无原型命令字典、完整嵌套/状态组合/交付时间线加载器、载荷 SHA-256 复算和数值边界校验。第四轮原子 store 定向组修复后为 `pass 3 / fail 0`，来源前缀和交付一致性组为 `pass 4 / fail 0`；最终完整定向测试结果：

```text
tests 50
pass 50
fail 0
exit 0
```

最终 TypeScript 定向验证：

```text
npx tsc -p tsconfig.app.json --noEmit
exit 0
```

格式检查：

```text
git diff --check
exit 0
```

仓库根目录当前把整个 `linguan-prototype-v2/` 显示为未跟踪目录，因此该命令只能证明已有跟踪 diff 没有空白错误，不能逐文件覆盖未跟踪内容；这是共享工作区状态限制，不通过添加或提交用户目录来规避。

## 验收覆盖与自检

- 五来源保持创建顺序；只有首个 `PENDING` 来源可开始，`READING` 或 `DOCUMENT_READY` 阶段不能开始下一来源。
- 五来源行为测试现完整推进 MySQL、GitHub、官方核心文档、制度 Markdown 与 Semantica，最终 revision 为 15、五步均为 `ALIGNED`，运行才进入 `READY_FOR_OUTPUT`。
- 完成来源读取一次增加 revision，但按顺序写入 `SOURCE_READ_COMPLETED` 和 `DOCUMENT_GENERATED`；连同开始事件形成 `STARTED → COMPLETED → DOCUMENT_GENERATED`。
- 无冲突审阅直接对齐并继续；有冲突审阅进入 `CONFLICT_BLOCKED`，漏解、多解或重复 conflict ID 均失败，精确覆盖才按每项写 `CONFLICT_RESOLVED`。
- 同一 Runtime 实例用延迟内容存储真实制造 await 交错；两个同 revision 命令只有一个成功，另一个收到 revision 冲突，最终 revision 与事件均只增加一次。
- 同一 Runtime 或两个 Runtime 共享 Storage adapter 时，以延迟内容写入制造相同 snapshot 的并发 START，最终都只有一个 CAS 成功；输家收到“运行已被其他窗口更新”，revision 与事件不被覆盖。
- 两个独立 Runtime 注入两个 adapter 并共享同一 fake atomic backend 时，并发 START 与并发 CREATE 都只有一个 CAS 成功。测试直接检查失败 CAS 计数、最终 revision、timeline 和 sequence，证明输家没有覆盖胜者或生成重复 runId。
- 浏览器 metadata store 直接选择 IndexedDB 单条记录，不依赖 Web Locks 或租约；CAS 的读版本、比较和写新版本位于同一 `readwrite` transaction。
- 同一 `commandId` 只有命令指纹完全一致时幂等；同 run 改 type/payload、CREATE 跨项目复用都失败。同一 `projectId + batchId` 仍不重复创建运行。
- `toString`、`constructor`、`__proto__` 均作为自有 commandId 正常执行并精确幂等，不再命中对象原型。
- 旧 string commandId 映射可以加载，但重试时因没有历史指纹而安全拒绝，要求调用方换用新 commandId。
- 不可解析 JSON、不支持 schema、重复 runId 和落后 sequence 均返回中文损坏错误并保留元数据原字节。
- 嵌套校验覆盖运行状态；来源身份、名称、连续 order、状态、冲突数组和文档字段；事件身份、连续 sequence、runId、type、actor、sourceId 和 payloadRef。状态组合还覆盖运行与来源阶段、未解决冲突、交付和审核身份；代表性破损在 `read()` 与 `execute()` 阶段都返回中文错误，原字节不变。
- 来源步骤必须形成 `ALIGNED* → 至多一个当前状态 → PENDING*`，`PENDING,ALIGNED,PENDING` 即使每个步骤自身结构合法也会被判为损坏。
- deliverable/review/handoff identity 只能出现在全部来源对齐后的运行阶段；交付事件必须唯一、顺序正确，且根据 identity 确定性 JSON 复算出的 SHA-256 必须匹配 `payloadRef`。
- 来源名称是展示字段，不承担身份唯一性；两个不同 `sourceId` 使用同一 `sourceName` 时可以创建并继续读取。
- 两个项目、两个 `runId` 分别读取和推进；不存在的 `runId` 返回 `null`，事件载荷读取也不会回落到其他运行。
- 大正文测试使用 2,000 次重复标记；元数据断言不含该正文，只含 `sha256:` 引用，并能通过 `runId + eventId` 从内容存储取回原文。自定义存储返回篡改正文时，Runtime 独立复算 SHA-256 并拒绝读取。
- 最后来源对齐后进入 `READY_FOR_OUTPUT`；四个交付命令必须依次执行，最终状态为 `HANDED_OFF`。
- revision 错误、越序、错来源和冲突集合错误的测试均复查 revision 未变化，避免写入半状态。
- 空 commandId、空 actor、非正整数 documentRevision、负数或小数读取计数均在写入前失败。
- 变异检查：删除上一来源审阅门禁会破坏顺序测试；交换完成事件顺序会破坏事件测试；把 payload 内联元数据会破坏存储测试；放宽冲突集合会破坏漏解/多解测试；按项目回落读取会破坏隔离测试；放宽交付顺序会破坏交付测试。

## 改动文件

- `src/features/standardization-run/types.ts`
- `src/features/standardization-run/standardization-metadata-store.ts`
- `src/features/standardization-run/runtime.ts`
- `src/features/standardization-run/standardization-run.test.ts`
- `docs/architecture/m3-m4-standardization.md`
- `docs/plans/task-1-standardization-run-report.md`

## 风险与边界

- Runtime 只登记 `documentId`、`deliverableId`、`reviewId` 和 `handoffId`，不在本 Checkpoint 内校验这些下游实体的真实内容；后续 Checkpoint 必须由各自 Runtime 先完成实体操作，再调用本 seam 登记状态。
- `READY_FOR_OUTPUT` 同时承载“可生成交付物”“已生成待提交”“已提交待冻结”三个轻量阶段，具体顺序由已登记的身份字段和事件门禁约束；这是当前 brief 给定运行状态集合下的最小实现。
- 浏览器依赖 IndexedDB 单记录 transaction CAS 保证跨 Runtime、跨 tab 原子提交；没有 IndexedDB 时拒绝读写。Node/测试的 Storage adapter 只承诺同 realm 原子性。测试覆盖浏览器 factory 选择和共享原子 backend 的 CAS 竞争，但本任务按约束未启动真实浏览器，因此跨 tab 行为仍依赖浏览器标准事务实现。
- 旧版 string commandId 记录无法恢复原命令指纹，因此选择可读但拒绝重试，而不是猜测幂等；调用方需生成新的 commandId。
- 损坏元数据会保留原字节并阻断读写，本 Checkpoint 不提供自动修复或清空入口。
- 内容存储写入发生在元数据提交之前。命令校验失败不会写入正文或元数据；若外部 `metadataStorage.setItem` 自身失败，内容寻址存储可能保留无引用但可去重的内容，Runtime 不提供垃圾回收。
