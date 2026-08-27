# Task 6：真实审阅助手与按钮减法实施报告

## 结论

Checkpoint 6 已在管伊佳连续时间线工作台落地真实审阅助手，位于主区工作台的底部网格行（非 fixed／sticky）。助手只从当前 `StandardizationRun` 登记的持久化来源 revision 解释来源、冲突、Evidence 与影响对象，打开 allowlisted 目标，或为当前结构化 block 生成 Task 4 的真实修改 Preview。自由文本不会直接应用修改、解决冲突、冻结、交付或触发 M4。

审计真相没有另建 Conversation 存储：输入与确定性回复使用内容寻址 `ASSISTANT_TURN_RECORDED`；完整 Proposal 单独内容寻址，Turn 只保存引用；明确确认以一次 Run CAS 相邻写入 `ASSISTANT_PATCH_CONFIRMED → DOCUMENT_REVISED`。未实现 deliverable、冻结、M4 handoff 或发布，未修改管伊佳黄金 V1、零售 Fixture、legacy runtime 或旧 `document-alignment`。

## 深模块合同

- `review-assistant.ts` 是纯确定性 intent／回答投影：NFKC、trim、折叠空白，最大 4000 字符，固定优先级，不猜测未知意图。单回复最多 8 段、每段最多 240 字；影响对象正文最多 20 项，打开候选最多 3 项。
- `GuanyijiaWorkbenchRuntime` 仍是页面唯一 seam。`ASK_REVIEW_ASSISTANT` 只接受消息和当前选择；Runtime 从精确 Run revision 构建知识，目标只能来自当前 run allowlist。Patch intent 只允许当前文档 block 的 `label/value`，并复用 Story `reviseCompilation` 生成真实 Block／Assertion／章节／整份 Markdown diff、冲突影响与对象影响。
- `StandardizationRunRuntime` 对 Turn、Proposal、确认和相邻 revision 做内容引用、身份、顺序和唯一性检查。每个 Turn 冻结事件时点已生成来源文档的有序 revision 前缀；小型事件索引足以在不展开旧正文的情况下拒绝事件重排。管伊佳 validator 在写入、当前分页和确认 reload 时从这些历史 revision 重建确定性回复与 Proposal；同步伪造 Before、完整 Preview 或 SHA 不能自证。确认 validator 还在 CAS 前或读取含该 Proposal 的历史页时读取实际 after document，复算 blocks、assertions、sections、Markdown、冲突集合和 diff 摘要，避免提交或显示随后不可读的伪 revision。
- `CONFIRM_ASSISTANT_PATCH` 不接收 UI change。Workbench 从 Run 找到当前 document 最新未确认 Proposal，重查 ACTIVE／Editor 或 Admin、运行阶段和 document revision，重算 Preview SHA，再创建相邻不可变来源文档。两个窗口同 revision 只有一个成功；command fingerprint 保证幂等。来源 r2 与 pending 已写但 Run CAS 失败时，普通 ASK 可以插入，原确认以当前 Run revision 恢复同一 r2；pending 文档禁止产生覆盖原 Proposal 的新 Patch。ASK 也先冻结 fingerprint／`createdAt`，Run 成功而活动指针收尾失败时相同 commandId 精确恢复。历史上多次助手确认依 proposalId 恢复，不重复 revision／event。
- `readAssistantHistory` 首次只展开最近 20 turn，使用 `assistant-before:<sequence>` 继续分页；首屏不会读取页外 Turn／已确认完整 Proposal，对本页已确认 Proposal 则定向校验确认 Artifact、相邻 revision 和 after document 后才投影 `CONFIRMED`。若当前 `PENDING` Proposal 已落在 20 条之外，接口另返回至多一条 sticky pending card，Panel 用它替换一个普通可见 Turn，仍保持 20 条预算与唯一 Primary。只有最新且仍绑定 Run 当前 document revision 的未确认 Proposal 是 `PENDING`，旧 revision 或旧 Proposal 标为 `SUPERSEDED`。Turn 不内联完整 Markdown 或 Evidence。
- ACTIVE Viewer／Reviewer／Publisher 可提问和查看 Proposal，只有 Editor／Admin 可确认。权限在每条 Workbench 命令重新读取；inactive 成员连历史也不能读取。IndexedDB／localStorage 仅是原型持久化载体，不是抵御本机恶意篡改的安全边界。

## UI 合同

- `ReviewAssistantPanel` 位于主区工作台的底部网格行（非 fixed／sticky），发送是普通图标动作；Textarea 最多 5 行，文案明确“修改只会先生成预览”。展开历史在 Panel 内受限滚动，Composer 保持在同一可视工作台内；回答气泡以 border-box 尺寸和安全换行保持在 Panel 内。React 只渲染纯文本，不使用 `dangerouslySetInnerHTML`。
- Patch 卡展示 Block／Assertion Before／After、围绕真实增删 hunk 的最多 120 行当前章节红绿 Diff、冲突 title／ID／before→after 与对象 ID。待确认时隐藏“完成本份审阅”、“修改本项”和竞争阶段 Primary，“确认并应用此修改”是唯一 Primary。手工编辑卡打开时拒绝生成第二份 Assistant Proposal。
- 打开当前文档、block、Evidence、冲突或影响对象会定位现有页面目标并给予可见 focus／scroll；当前文档打开前记录触发元素与主线滚动，关闭后恢复。已审阅历史来源明确定位为时间线文档回执，不伪称打开正文。右侧事实检查器仍只有来源、authority、snapshot／commit、locator、lineage 和影响对象，没有业务操作。
- 900px 以下 Panel 位于主线／文档全屏子页的工作台底部并预留 `safe-area-inset-bottom`；`ResizeObserver` 将实际 Panel 高度投给主线和文档 bottom padding，长历史／Patch 不遮住最后内容。Composer 草稿留在 React state，不进入审计存储。

## RED → GREEN 证据

1. CP5 唯一 Minor 首先补直接测试：异步冲突 Preview 的成功、失败和 `finally` 都必须匹配最新 requestId；旧失败不再显示错误，旧 `finally` 不再提前结束 loading。
2. ASK 首次仍落到旧 `DOCUMENT_REVIEWED` 分支；加入命令与 Run 事件后从 `2/3` 转为 `3/3`。Proposal、确认和权限再逐片从 `3/4`、`4/5`、`5/6` 转绿。
3. UI 静态合同首次 `7/8`，缺少真实 Panel；接入固定底部 Panel、历史、确认、focus 和 safe area 后 `8/8`。
4. 第二次助手确认的指针收尾失败首次因错误读取第一条确认事件而不能恢复；恢复逻辑改为按 proposalId 从新到旧定位精确确认后转绿，最终 r1／r2／r3 各一份。
5. 合法 target 携带伪造回复可直接写 Run，攻击测试首次未拒绝；validator 改为从事件当时持久化 revision 重建知识并逐字节核对确定性回答后转绿。随后同步伪造 Before／Preview／SHA、未使用的任意 objectRef 和不存在 after revision 的攻击也都在 CAS 前拒绝。
6. 影响对象首次返回 5 个打开 target，违反最多 3 个候选；保留最多 20 条解释正文并把可执行导航 target 收敛到 3 个后转绿。
7. reviewer 复现来源 r2／pending 已写、Run 确认失败后插入普通 ASK 会让原确认永久失败；直接测试先 RED，恢复分支改用当前 Run revision 重试后 GREEN。pending 期间的新 Patch Proposal 同时被禁止，避免替代原 Proposal 后双向死锁。
8. reviewer 复现合法 Turn 可被重排到来源文档生成前，且 r1 无显式 document 选择的回答在后续 r2 reload 会被错误按 r2 重建；Turn 增加事件时点 revision 前缀并由 Run 小索引校验后，两种路径都按历史事实稳定。
9. reviewer 复现同 Run 的合法 objectRef 可跨 Block 串线；selection coherence 收紧为 timeline→source、document→source、block→document／section、Evidence／object→block 或 conflict，跨 Block／章节／时间线来源直接测试转绿。
10. reviewer 复现手工 r2 后旧 r1 Proposal 仍为 `PENDING` 并锁死 UI；状态投影增加当前 Run documentId／revision／阶段门禁后旧项为 `SUPERSEDED`。
11. reviewer 复现 ASK 在 Run 已提交、指针收尾失败后因重新取时产生不同 payload；ASK pending 先冻结 fingerprint／`createdAt`，同 commandId 重试精确复用已提交 Turn 后转绿。
12. 首屏容量测试首次约 29 秒并读取全部 22 个 Turn 与页外完整 Proposal；Run 改用轻量事件索引校验时序，Workbench 只展开当前页后约 1.2 秒，Turn 读取不超过 20 且页外 Proposal 读取为 0。
13. 两个 Workbench 并发确认同一 Proposal 的测试确认只有一个成功、Run 只有一条确认事件、来源文档只有一份 r2。
14. pending r2 存在时，“完成审阅”等竞争阶段命令虽会被下层 superseded invariant 间接拒绝，但错误路径不属于 Workbench saga；统一 seam 门禁后 COMPLETE、APPLY、新 Patch 和其他阶段写命令都明确拒绝，仅保留普通 ASK、只读 Preview 与原确认恢复。
15. 历史 Turn 初版仍沿用最终 Run 的 conflict／resolved／status；新增 GitHub debt r1 回答、r2 移除冲突的直接测试后，validator 改为从 Turn revisions 重建 compilation conflict set，并按事件前缀重建 resolved／status。
16. pending ASK 的 Run CAS 输给另一命令后曾把旧 expectedRevision 升级到新 revision；竞争测试确认现在找不到同 turnId 事件就只报 stale，不改投新事实。
17. cross-source conflict 与历史 timeline fallback 可串线；selection coherence 增加 conflict→introduced source，UI 只有所选时间线项自身含 conflict 才携带 conflictId。
18. 页外 `PENDING` Proposal 曾让 UI 恢复完成审阅／修改入口；独立 sticky pending 投影与 Panel 20 条合并后，Proposal 即使被 21 条普通回答挤出首页仍可见且保持唯一 Primary。
19. confirmed-only reload 仍深扫页外已确认 Proposal；Run 普通 read 现在只校验轻量、内容寻址的事件索引，当前确认和显式历史页才深读相应 Proposal，confirmed 页外 spy 为 0。
20. 移动端固定 112px padding 会被最高 420px Panel 遮挡；实际高度变量驱动三处 padding 后转绿。导航同时保存 target button／scroll，历史文档文案改为定位回执。
21. Patch 卡原先只显示影响数量且 Diff 固定 `slice(0,120)`；现在显示可审核的冲突／对象身份，并以尾部变更 hunk 为中心窗口，200 行尾部变更测试仍包含 ADDED／REMOVED 且总数 120。
22. 容量收敛后当前页 `CONFIRMED` 曾只信轻量事件 metadata；损坏确认正文的直接测试先 RED，历史页现在只对可见已确认 Proposal 批量深校验 confirmation／相邻 revision，页外确认首屏读取仍为 0、翻到该页才校验。

## 验证边界

最终验证仅运行 brief 指定的六个测试文件、app TypeScript、定向 oxlint 和 diff／trailing whitespace 检查；没有运行全仓测试、开发服务器或远端服务。父仓库视角整个 `linguan-prototype-v2/` 是既有 untracked 目录，因此 `git diff --check -- linguan-prototype-v2` 不会枚举内部文件，另以定向 trailing-whitespace 扫描补足。

```text
brief 六文件：tests 138 / pass 138 / fail 0 / exit 0
npx tsc --project tsconfig.app.json --noEmit：exit 0
brief 定向 oxlint：exit 0；命令同时报告 source-document-workspace.tsx 的 4 条既有 hooks warning
本 Checkpoint 变更文件定向 oxlint：exit 0，无 warning
git diff --check -- linguan-prototype-v2：exit 0（父仓目录为 untracked，不枚举内部）
定向 trailing-whitespace rg：无匹配（rg exit 1）
```

## 独立只读复审

独立 reviewer 对冻结快照逐项复现并关闭全部 Critical／Important：`Spec PASS / Quality PASS`，Critical 0／Important 0／Minor 1。其独立复跑证据为 brief 六文件 138／138、TypeScript exit 0、变更文件 oxlint exit 0、diff-check exit 0、定向 trailing-whitespace 无匹配。

唯一非阻断 Minor：多人共享 Run 历史的 Turn 卡仍统一把提问者标注为“你”，尽管持久化 `actorUserId` 保持真实。它不影响审计身份、权限、确定性回答、Proposal 或确认链；后续 UI 可依据当前查看者与 `actorUserId` 区分“你”和成员身份。
