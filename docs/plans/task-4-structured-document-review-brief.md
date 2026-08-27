# Task 4：结构化来源文档审阅与真实 Revision Diff

## 目标

让用户在 Codex 主区内修改来源文档的结构化识别结果，并一次事务地生成：

```text
结构化 Block 新值
→ 对应 Assertion
→ 九段章节 Markdown
→ 新 SHA
→ 新 SourceDocument revision
→ DOCUMENT_REVISED 运行事件
→ 重新计算本源冲突
```

不得再出现“只改正文，断言和冲突仍是旧值”，也不得用字符数冒充 Diff。

## 通用类型归位

- 将 `SourceDocumentBlock`、`StructuredValue` 等业务无关类型放入 `source-documents` 深模块；管伊佳 story 可以 re-export，避免 source-documents 依赖项目模块。
- `SourceModelingDocument`新增可选且内容寻址的 `blocksRef`。旧文档没有 blocksRef 时继续只读兼容，不猜测结构化块。
- `RegisterSourceDocumentInput`允许保存 blocks；管伊佳新工作台必须保存。

## SourceDocumentRuntime 深化

新增最小接口：

```ts
readBlocks(documentId): Promise<SourceDocumentBlock[]>;
revise(input: {
  documentId: string;
  expectedRevision: number;
  sections: SourceDocumentSections;
  assertions: StructuredModelingAssertion[];
  blocks: SourceDocumentBlock[];
  actorUserId: string;
}): Promise<SourceModelingDocument>;
getRevisionDiff(beforeId, afterId): Promise<SourceDocumentRevisionDiff>;
```

规则：

- `revise`一次生成一个新documentId/revision；旧 revision 标记 `SUPERSEDED`，原内容不可覆盖。
- sections/assertions/blocks/Markdown分别内容寻址；Markdown继续由九段章节和 assertions 确定性生成。
- 所有 blockId/assertionId 唯一；每个 block 必须有且仅有对应 assertion，section/evidenceRefs语义一致。
- 任一内容写入或校验失败不得提交元数据；CAS失败不覆盖别窗口 revision。
- 浏览器默认使用 IndexedDB CAS 元数据；Node测试可注入原子 store。不要继续让新工作台用不安全 localStorage写入。
- 旧 localStorage key 保留；若做迁移必须复制式、幂等、失败继续只读旧状态。

## Story revision seam

在 `GuanyijiaStandardizationStory` 增加纯函数式能力，名字可调整但接口必须深：

```ts
reviseCompilation(input: {
  compilation: SourceDocumentCompilation;
  priorCompilations: SourceDocumentCompilation[];
  changes: Array<{
    blockId: string;
    label?: string;
    value?: StructuredValue;
  }>;
}): {
  compilation: SourceDocumentCompilation;
  diff: SourceCompilationDiff;
};
```

约束：

- 只允许改 label/value；blockId、stableCode、source、section、evidence status、evidence refs、affected refs不可伪造。
- 根据新 blocks 重新生成 assertions、sections、Markdown、SHA、delta和冲突投影。
- 同值修改拒绝或返回明确 no-op，不能制造 revision。
- 只改变指定 block及对应 assertion/章节；其他来源编译、其他章节和黄金V1完全不变。
- 制度修改必须能真实改变对应 `negative-stock` 或 `status-nine` variant；真实三源不变。

## StandardizationRun 扩展

新增命令／事件：

```text
REVISE_SOURCE_DOCUMENT
DOCUMENT_REVISED
```

仅允许当前 run 为 `REVIEWING_DOCUMENT` 且 source为 `DOCUMENT_READY`。命令登记新 documentId/revision、替换本源 `introducedConflictIds`，写小型 diff 摘要 payload，不内联 Markdown。Expected revision、commandId、CAS、事件校验继续使用 Checkpoint 1 契约。

## Workbench Runtime

新增命令：

```ts
PREVIEW_CURRENT_BLOCK_CHANGE
APPLY_CURRENT_BLOCK_CHANGE
```

若 preview 保持纯函数，可作为单独方法，不必写 command log。Apply 必须：

1. 读取当前 source document blocks。
2. 调 story `reviseCompilation`。
3. 一次调用 `SourceDocumentRuntime.revise`。
4. 调 `REVISE_SOURCE_DOCUMENT`登记新revision与冲突。
5. 返回新 snapshot，当前document指向新revision，timeline出现真实 DOCUMENT_REVISED。

跨步骤失败不能出现 run指向不存在文档；如无法做到跨两个Runtime原子，使用可恢复 saga：先创建不可变文档，再CAS登记run；run CAS失败时新文档保持未引用但不替换当前revision，重试同commandId幂等关联同一结果。

## UI

在主区当前文档中：

- 每个可修改 block 只有一个次动作“修改本项”。
- 点击后在主区展开编辑卡，不放到右检查器。
- 编辑 `名称` 与人类可读 `识别结果`；复杂 StructuredValue使用与对象类型对应的字段表单，首版至少支持 string 和 `{text:string,...}`，未知结构只读。
- “预览修改”展示真实：
  - Block Before/After。
  - Assertion Before/After。
  - 当前章节 Markdown 行级 Diff（删除红、增加绿）。
  - 受影响冲突与对象。
- 唯一主操作“应用修改”；取消为次动作。
- 应用后标题显示新revision，时间线记录谁修改了哪一项；旧revision可在“历史版本”次级入口查看。
- “完成本份审阅”仍是审阅阶段唯一primary，编辑卡打开时隐藏/禁用，避免双主操作。

本 Task不实现多来源 Git hunk 冲突解决；这里只处理单份来源文档内部修订。

## TDD

先写 RED，再实现：

1. register保存blocks；readBlocks校验hash；legacy无blocks明确只读。
2. revise一次产生r2，r1内容/字节不变，四类内容SHA同步更新。
3. block/assertion/section不一致、篡改Evidence、duplicate ID、stale revision均拒绝且不写半状态。
4. 同值patch不产生revision。
5. story修改制度负库存block只改变policy+对应variant；MySQL/GitHub/官方不变。
6. run `DOCUMENT_REVISED`只在正确状态，更新document revision/conflict ids，payload轻量。
7. workbench apply走完整链；刷新后仍读r2，不回退默认compilation。
8. 重复apply command幂等，不产生r3。
9. preview包含block/assertion/Markdown真实Before/After与受影响冲突。
10. navigation/focus在应用后仍保持当前章节与block。

定向测试：

```bash
node --experimental-strip-types --test src/features/source-documents/source-documents.test.ts src/features/standardization-run/standardization-run.test.ts src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts src/features/data-standardization/guanyijia-workbench-runtime.test.ts src/features/data-standardization/data-standardization.test.ts
npx tsc -p tsconfig.app.json --noEmit
git diff --check
```

更新架构文档和 `docs/plans/task-4-structured-document-review-report.md`。完成后独立只读审查必须 `Spec PASS / Quality PASS`，Critical/Important为0。

## 不可修改

- 黄金 V1、Catalog seed、零售 Fixture与legacy流程。
- 冲突最终解决、审阅助手、deliverable、M4。
- 备份、远端服务。
