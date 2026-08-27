# Task 4：结构化来源文档审阅与真实 Revision Diff 实施报告

## 结论

Checkpoint 4 已把结构化来源文档 revision 收敛为单一事实链：`SourceDocumentBlock` 修改经 story 纯修订重建 assertion、九段 sections、Markdown、SHA、delta 与本源冲突，再由来源文档原子 CAS 生成不可变相邻 revision，最后用 `REVISE_SOURCE_DOCUMENT` 把运行关联到新 revision。工作台 preview 只计算真实差异；apply 使用可恢复 saga，刷新从运行登记的精确文档 identity 恢复，不回退默认 compilation。

本任务没有实现多来源冲突的最终解决、审阅助手、deliverable、冻结或 M4 交接；没有修改黄金 V1、Catalog seed 或零售 Fixture。旧的无 `blocksRef` 来源文档继续可读，但 Runtime 与 legacy 页面都明确禁止把章节正文当作结构化编辑入口。

## 深模块合同

### Source documents

- 通用 `StructuredValue`、`SourceDocumentBlock` 归入 `source-documents/types.ts`；管伊佳 story 只 re-export，不形成反向依赖。
- `SourceModelingDocument.blocksRef` 与其余三类内容一样使用 `sha256:<digest>`；`readBlocks` 独立复算 hash。没有 blocks 的旧文档返回明确只读错误，不猜测投影。
- `structured-projection.ts` 是 block → assertion → 九段 sections → Markdown 的共享确定性投影 seam。`revise` 一次校验完整 blocks/assertions/sections，要求 block/assertion ID 唯一且一一对应，并逐字段核对 section、statement、evidenceRefs、provenance 和完整九段投影；`markReady` 还会读取四类正文、重建 Markdown 并拒绝合法 SHA 但语义无关的正文。
- 四类正文写入成功后只做一次 metadata CAS；内容 store 返回的 ref 必须与本地复算 SHA 相等。成功时创建相邻 revision 并把旧 revision 标为 `SUPERSEDED`，旧内容字节不变；历史 revision 不能重新标为 ready 或分叉出重复 revision。
- 校验失败、内容存储失败、同值修订、stale revision 和 CAS loser 都不会覆盖 metadata 胜者或产生意外下一版。`getRevisionDiff` 从真实相邻内容返回 Block、Assertion、section 与整份 Markdown 的 Before／After 和行级差异。
- 浏览器默认 metadata backend 为 `linguan-source-document-metadata-v1` IndexedDB 单记录事务 CAS；Node／测试显式注入原子 store 或 Storage adapter。旧 metadata key `linguan:source-documents:v1` 保留。

### Story 与 run

- `reviseCompilation` 是纯函数式 seam，只接受指定 block 的 label/value 变化。wrapper 将当前及每份 prior compilation 与 story canonical base 对照，拒绝伪造 source identity、authority、lineage、summary、locator，以及 label/value 之外的 blockId、stableCode、section、evidence status、Evidence 与 affected refs。
- 修订从 blocks 重建 assertion、sections、Markdown、SHA、delta 和冲突 variant，并返回实际 compilation diff；no-op 明确拒绝。制度负库存测试证明只改变制度 compilation 与 `gyj-conflict-negative-stock` 对应 variant，三个真实来源和黄金 V1 保持不变。
- `StandardizationRunRuntime` 新增 `REVISE_SOURCE_DOCUMENT`／`DOCUMENT_REVISED`，只允许 `REVIEWING_DOCUMENT + DOCUMENT_READY` 的当前来源。命令替换 documentId、revision、introduced conflict IDs，事件 payload 仅保存文档身份与 changed block／section／object 摘要，不内联 Markdown。

### Workbench saga 与 UI

- Preview 从当前持久 compilation 调 story seam，返回真实 Block／Assertion Before／After、当前 section Markdown 行级 diff、受影响冲突和对象，不写文档或运行。
- Apply 顺序为 story 修订 → `SourceDocumentRuntime.revise` → 保存 pending apply 指针 → run `REVISE_SOURCE_DOCUMENT` → 完成指针。run CAS 失败时运行仍指向 r1；同一 commandId 重试校验并复用既有 r2，不创建 r3，成功后清理 pending 记录。
- `read()` 要求 run step 与文档的 project、documentCode、source、snapshot、revision 精确一致，再读取 blocks/assertions/sections/Markdown 并校验完整投影，恢复已修订 compilation、全部 revision 历史和相邻真实 diff。因此页面刷新仍显示持久化的新 revision，不能把“存在但身份不符”的文档当作当前文档。
- 主区每个可编辑 block 只有一个“修改本项”次动作。编辑 string 和带 `text` 的对象；若对象存在 string `normalized`，界面会单独编辑用于规则与冲突重算的标准值，其余技术字段只读；未知结构显示只读。编辑卡展示名称／识别结果、Block／Assertion Before／After、红删绿增 Markdown diff、冲突和对象影响；“应用修改”是卡内唯一 primary，卡打开时隐藏“完成本份审阅”。
- apply 后标题、时间线和历史入口显示新 revision，历史入口逐项展开实际 Before／After；生成事件保持当时 revision，最新 revised 事件为当前项，章节、block 选择和焦点目标保持。旧无-blocks 工作区只展示已持久化的 sections/assertions/Markdown，不暴露默认 compilation，不显示“修改本项”或完成审阅入口。

## RED → GREEN 证据

实现按纵向切片推进：

1. blocks slice 首次因 `blocksRef` 缺失 RED；加入通用 block 类型、register/readBlocks 与 hash 校验后 GREEN。
2. revision slice 首次因缺少真实 r1→r2、四引用与 diff RED；实现不可变 revision 和真实 diff 后 GREEN。
3. 完整性／CAS slice 首次为 `pass 0 / fail 2`；加入投影校验、no-op、stale、内容失败与共享 metadata CAS 后 GREEN，两个 Runtime 并发只有一个 r2。
4. story revise slice 先用制度负库存变更锁定影响范围，再实现纯 `reviseCompilation`；该文件最终 `pass 12 / fail 0`。
5. run slice 先证明错误状态与新事件缺失，再加入命令／状态／轻量 payload；新增 tracer `pass 1 / fail 0`。
6. workbench slice 先锁定完整 apply／reload／幂等／导航和 CAS 恢复，再实现 pending saga；随后增加连续 r2/r3、合法 SHA 错 Markdown、legacy 无 blocks 与精确 identity 回归。
7. UI 所用的 structured editor pure seam 覆盖 string、`{text,...}` 与未知结构只读，新增 tracer `pass 1 / fail 0`。
8. 首轮 TypeScript 检查因编辑命令联合不可判别及时间线图标表缺少 `DOCUMENT_REVISED` 而 RED（3 条诊断）；拆分命令分支并补齐图标后，同一命令 GREEN、无诊断。
9. 独立 reviewer 首轮指出 6 项 Important。每项先用直接回归证明 RED，再分别加入完整投影与 Markdown 校验、legacy 只读边界、逐事件 revision 历史、可编辑 normalized、SUPERSEDED 状态门禁，以及 story/workbench canonical identity 校验；修复后 brief 五文件合并验证 `pass 98 / fail 0`，tsc 无诊断。

## 直接验证

brief 指定的唯一测试集合：

```text
node --experimental-strip-types --test \
  src/features/source-documents/source-documents.test.ts \
  src/features/standardization-run/standardization-run.test.ts \
  src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts \
  src/features/data-standardization/data-standardization.test.ts

tests 98
pass 98
fail 0
exit 0
```

```text
npx tsc -p tsconfig.app.json --noEmit
exit 0
```

`git diff --check` 与最终 reviewer 结论记录在独立只读复审一节。

## 数据与失败边界

- 内容寻址写入可能在 CAS loser 后留下按 SHA 去重的无引用正文；这不构成元数据半状态。
- workbench 的跨 Runtime saga 不能回滚已创建的不可变 r2。run CAS 失败时 pending 指针使 r2 可恢复，运行的当前 identity 仍是 r1；同一命令重试完成关联。实现不清理 orphan revision，以免执行破坏性删除。
- Preview／apply 只在当前文档审阅态运行。`DOCUMENT_REVISED` 不改变来源顺序、不完成审阅，也不越权处理多来源冲突。
- 没有运行全仓测试、开发服务器、浏览器或远端服务；验证严格限 brief 的五个测试文件、app tsc 和 diff 检查。

## 独立只读复审

独立 reviewer 首轮在 `98/98` 定向测试与 tsc 通过的基础上给出 `Critical 0 / Important 6`，因此当时的结论为 `Spec FAIL / Quality FAIL`：

1. blocks/assertions 自洽但 sections 或 Markdown 可被替换为另一份合法 SHA 正文。
2. 无 `blocksRef` 的 legacy 文档回退默认 compilation，仍可编辑或完成审阅；run/doc identity 校验也不够精确。
3. 时间线使用当前文档重写旧生成事件，且 apply 后没有把最新 revised 事件标为当前项。
4. 带 `{ normalized, text }` 的值只能改 text，不能沿真实 UI 路径改变 policy variant。
5. `markReady` 可把 `SUPERSEDED` 历史 revision 恢复为 ready 并从历史处分叉。
6. story wrapper 没有把当前／prior compilation 的 authority、sourceClass 和 block 不可变身份字段与 canonical story 对照。

以上六项均已先补 RED 回归再修为 GREEN。独立 reviewer 对最终冻结快照逐项 closure，并独立复跑 brief 五文件 `tests 98 / pass 98 / fail 0 / exit 0` 与 `npx tsc -p tsconfig.app.json --noEmit`（exit 0）；最终 verdict 为 `Spec PASS / Quality PASS`，`Critical 0 / Important 0 / Minor 0`。reviewer 全程只读，未修改文件。
