# Task 6：真实审阅助手与按钮减法

## 目标

在管伊佳 Codex 工作台恢复固定底部审阅助手。助手可以解释当前来源、冲突、证据和影响对象，打开确定目标，并为当前结构化识别结果生成修改预览；所有输入、回复和确认都进入同一 `StandardizationRun.timeline`。

强约束：

- 助手不能直接修改文档、解决冲突、冻结交付物或触发 M4。
- 修改只有“建议 → 真实 Patch 预览 → 用户点击确认 → 复算预览 → Workbench 应用”一条路径。
- 自由文本中的“确认、同意、应用”不能触发修改。
- 冲突策略和理由仍由 Task 5 主区 Hunk 完成。
- 未知意图返回支持范围，不猜测、不执行。
- 不新增第二套 Conversation 存储，不在 React state 或独立 localStorage 保存审计真相。

## 架构与持久化

深化 `GuanyijiaWorkbenchRuntime`，不创建平行 Assistant Runtime。新增 `review-assistant.ts`、`review-assistant-types.ts`、`review-assistant-panel.tsx` 和定向测试。

运行事件是唯一审计真相：

```text
ASSISTANT_TURN_RECORDED
ASSISTANT_PATCH_CONFIRMED
DOCUMENT_REVISED
```

一次提问在一个 CAS 中写入一个内容寻址 turn artifact；Patch 完整预览单独内容寻址，turn 只引用它。确认修改时 `ASSISTANT_PATCH_CONFIRMED` 与 `DOCUMENT_REVISED` 必须同一次运行提交、顺序相邻、身份一致。

## 外部接口

```ts
type ReviewAssistantContextSelection = {
  timelineItemId?: string;
  sourceId?: string;
  documentId?: string;
  section?: ModelingDocumentSection;
  blockId?: string;
  conflictId?: string;
  evidenceRef?: string;
  objectRef?: ModelBrowserObjectRef;
};

type ReviewAssistantTarget =
  | { kind: 'SOURCE_DOCUMENT'; documentId: string; section?: ModelingDocumentSection; blockId?: string }
  | { kind: 'CONFLICT'; conflictId: string }
  | { kind: 'EVIDENCE'; sourceId: string; blockId: string; evidenceRef: string }
  | { kind: 'AFFECTED_OBJECT'; objectRef: ModelBrowserObjectRef };

type ReviewAssistantResponse =
  | { kind: 'SOURCE_EXPLANATION' | 'CONFLICT_EXPLANATION' | 'EVIDENCE_EXPLANATION'; title: string; body: string[]; targets: ReviewAssistantTarget[] }
  | { kind: 'AFFECTED_OBJECTS'; title: string; body: string[]; targets: ReviewAssistantTarget[] }
  | { kind: 'OPEN_TARGET'; title: string; body: string[]; target: ReviewAssistantTarget }
  | { kind: 'PATCH_PREVIEW'; title: string; body: string[]; proposal: AssistantStructuredChangeProposal }
  | { kind: 'BOUNDARY' | 'SUPPORTED_SCOPE'; title: string; body: string[] };

type GuanyijiaWorkbenchCommand =
  | ExistingCommands
  | {
      type: 'ASK_REVIEW_ASSISTANT';
      commandId: string;
      expectedRevision: number;
      actorUserId: string;
      message: string;
      selection: ReviewAssistantContextSelection;
    }
  | {
      type: 'CONFIRM_ASSISTANT_PATCH';
      commandId: string;
      expectedRevision: number;
      actorUserId: string;
      proposalId: string;
      expectedPreviewSha256: `sha256:${string}`;
    };
```

Workbench 增加每页 20 条的 `readAssistantHistory`；目标和 Proposal 必须从当前 run allowlist 投影，调用者不能上传自造 Patch、Locator 或任意 documentId。

## 确定性意图

输入 NFKC、trim、折叠空白；空输入拒绝，最大 4000 字符。优先级：

1. `建议把当前项名称/识别结果改为：…`
2. 打开／定位／跳到／查看。
3. 影响哪些对象。
4. 依据／证据／为什么。
5. 冲突／差异。
6. 来源／快照／Commit／读取范围。
7. 直接修改／替我应用／解决冲突／冻结／交付等越权意图。
8. 其他返回固定支持范围。

修改只允许 Task 4 的 `label/value`；stableCode、section、Evidence、影响对象和来源身份不可更改。Proposal 必须基于当前持久化 revision 生成真实 Block/Assertion/Markdown Diff。目标多匹配时最多列 3 个候选，不自动选择。

## 确认流程

点击“确认并应用此修改”后：

1. 从 run 的 assistant event 找 `proposalId`，不信任 UI 上传 change。
2. 重查项目、角色、运行阶段和 document revision。
3. 从当前持久化 blocks 重新生成 Preview。
4. 核对 `expectedPreviewSha256`。
5. 复用 Task 4 不可变 revise saga。
6. 单次 CAS 写确认事件和 `DOCUMENT_REVISED`。
7. 返回并聚焦新 revision 同一 block。

任何失败不得写确认事件或改变 run 指向。相同 commandId 幂等；改变消息、selection、proposal 或 hash 报指纹冲突；两个窗口同 revision 只能一个成功。

## 权限与安全

- 当前项目 ACTIVE 成员可问来源、冲突、证据和影响对象。
- Viewer/Reviewer/Publisher 可看建议和预览，但不可应用修改。
- Editor/Admin 可确认结构化修改。
- 每次命令在 Workbench seam 重查权限，不能只隐藏按钮。
- 目标必须属于当前 run；禁止跨项目、跨 run、任意 Content ref 读取。
- React 只以纯文本渲染，禁止 `dangerouslySetInnerHTML` 或自动执行 URL/SQL/Shell。
- 文档明确 IndexedDB/localStorage 不是防篡改安全边界。

## UI 与减法

```text
主区
├── 来源、文档、冲突和助手 Turn 的同序时间线
└── 工作台底部网格行 ReviewAssistantPanel（非 fixed／sticky）
右侧 Facts Inspector：只读
```

Composer 文案：

> 问我当前来源、差异、依据或影响对象；修改只会先生成预览。

- 不增加快捷按钮；发送为普通图标动作，不与阶段 Primary 竞争。
- Patch 卡展示 Block、Assertion、章节红绿 Diff、冲突和影响对象。
- Patch 待确认时隐藏“完成本份审阅”和冲突 Primary；唯一 Primary 为“确认并应用此修改”。
- 编辑卡打开时不能再生成第二份 Assistant Proposal。
- 每个 run/document 只允许最新一个未确认 proposal；旧 proposal 标为已替代。
- 右检查器除关闭/返回外不出现表单、策略、理由、应用或完整 Markdown。
- 删除任何“下一 Checkpoint 实现”no-op 和重复审阅入口。

## 响应式与大内容

- ≥1280px：Composer 位于主列的工作台底部，检查器 360–420px。
- 900–1279px：检查器 overlay，Composer 不被压缩。
- <900px：Composer 位于主线／文档／冲突全屏子页的工作台底部并预留 safe area；检查器关闭后恢复草稿、滚动和焦点。
- 展开对话时，历史在 Panel 内按受限高度滚动；Composer 始终留在同一可视工作台内。回答气泡以 border-box 尺寸和安全换行保持在 Panel 内。
- Textarea 最多 5 行；初始只加载最近 20 turn。
- 单回复最多 8 段，每段约 240 字；影响对象首屏 20；Evidence 只给摘要和目标。
- Diff 默认最多当前变更附近 120 行，不把整份 Markdown 塞入对话。

## TDD

先观察 RED，再最小 GREEN：

1. ask 命令/事件缺失；四类回答未绑定当前 run。
2. 输入与回复没有进入同一 timeline。
3. 未知/越权意图猜测或直接执行。
4. 修改建议没有真实 Block/Assertion/Markdown Diff。
5. Preview 读取默认 Fixture 而非当前 revision。
6. 确认不复算 SHA，陈旧 Proposal 仍应用。
7. command 指纹、CAS 并发和 saga 重试产生重复 revision/event。
8. 角色、inactive、cross-project 权限绕过。
9. Assistant 可以解冲突、冻结、交付或触发 M4。
10. 确认事件与 DOCUMENT_REVISED 非同 CAS/顺序或身份不一致。
11. 篡改 turn/proposal/content hash 未拒绝。
12. 初始加载超过 20 turn 或内联大 Markdown/Evidence。
13. 打开当前目标无 focus/scroll 可见反馈。
14. 页面出现多个 Primary 或右检查器出现业务操作。
15. 手机遮挡、草稿/滚动/焦点丢失。
16. 新模块导入 legacy document-alignment。
17. 黄金 V1、计数、零售路由和 Fixture 回归。

## 验证

```bash
node --experimental-strip-types --test \
  src/features/data-standardization/review-assistant.test.ts \
  src/features/data-standardization/guanyijia-workbench-runtime.test.ts \
  src/features/data-standardization/data-standardization.test.ts \
  src/features/standardization-run/standardization-run.test.ts \
  src/features/source-documents/source-documents.test.ts \
  src/features/guanyijia-standardization-story/guanyijia-standardization-story.test.ts

npx tsc -p tsconfig.app.json --noEmit
npx oxlint src/features/data-standardization src/features/standardization-run/runtime.ts src/features/standardization-run/types.ts
git diff --check
```

更新架构、动作层级、README 和 `docs/plans/task-6-review-assistant-report.md`。独立 reviewer 必须 Spec PASS / Quality PASS，Critical/Important 为 0。

## 不可修改

- 管伊佳黄金 V1、Catalog seed、Evidence Context 和黄金计数。
- 零售 Fixture、路由和 legacy runtime。
- `document-alignment`、Task 5 conflict artifact 语义、Deliverable、冻结、M4、发布。
- 备份、远端服务。
