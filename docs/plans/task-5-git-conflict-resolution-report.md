# Task 5：Git 式来源冲突解决与语义 Patch 实施报告

## 结论

Checkpoint 5 已把管伊佳三项来源差异收敛为一条可复算的决定链：持久化来源 revision → Current／Incoming／Corroborating Hunk → 人工策略与理由 → 结构化 Patch／`USER_CONFIRMED` assertion／真实 Markdown 行级 diff／provenance → 内容寻址 Artifact → 单项 `CONFLICT_RESOLVED`。Workbench 是页面唯一 seam；来源文档保持不可变事实，冲突决定不回写来源文档。

未实现助手、deliverable、冻结、M4 或发布；未修改黄金 V1、Catalog seed、零售 Fixture 或旧 `document-alignment`。

## 深模块合同

- Story 纯函数以 `sourceId + stableCode + normalizedValue + evidenceRefs` 从指定持久化 revision 精确构建 Hunk。四策略由同一投影生成 result、Patch、assertions、对象处置、行级 diff、provenance、Hunk SHA 与 Preview SHA。
- Run 的 `RESOLVE_SOURCE_CONFLICT` 每次只接收第一项未决冲突的完整 Artifact。命令／加载都会复算内容引用、Hunk、Preview、Patch/result、block/assertion、diff 与 provenance；管伊佳 validator 还会从 Run 登记的持久化 revision 重建可信 Preview，不能让 Artifact 自证 Hunk。`resolvedConflictIds` 只能是已验证事件的顺序投影。第一项后继续阻断，最后一项才对齐。
- Workbench 的 `previewCurrentConflict` 和 `RESOLVE_CURRENT_CONFLICT` 每次重新读取精确文档 revision；UI 只提交策略、中文理由与 Hunk SHA。真实应用时间和决定指纹先写入 pending pointer；Run CAS 已成功而指针收尾失败时，同命令恢复原 Artifact，不重复事件或改写 `decidedAt`。事件刷新后仍由 Story 从决定时已有 revision 前缀逐字复算，不读默认 Fixture。
- GitHub 只引入 debt；Policy 依次引入 negative stock 与 status 9；Official 不引入冲突；Semantica 只追加两条 `CONFLICT_CORROBORATED` 回执。加载时每条回执必须回链时间线上此前已验证的决定，并通过 Story 的 DERIVED corroborator 身份校验；按来源汇总后的 ID 列表还必须精确等于登记持久化 revision 重建 compilation 的 `corroboratedConflictIds`，不能重排、伪造、重复或删除。
- 主区展示 Git 式 Hunk、Result、对象处置、红删绿增 diff 与决定控件，唯一 primary 是“应用本项决定”。右侧检查器保持纯事实。

## 三项主演示结果

1. `gyj-conflict-debt-schema` 使用 `KEEP_CURRENT`：部署数据库继续作为当前工作标准，`receivable_debt=EXCLUDED`，订金能力保持 `CANDIDATE_ONLY`，源码结论仅保留 provenance。
2. `gyj-conflict-negative-stock` 使用 `MERGE`：保留 GitHub 按租户配置控制的实现事实，新增“统一禁止负库存制度尚未落地” GAP，规则为 `CANDIDATE_ONLY`。
3. `gyj-conflict-status-nine` 使用 `DEFER_AS_GAP`：保留状态字段但不发布状态 9 枚举成员，审核规则保持候选并新增待确认 GAP。

## RED → GREEN 证据

1. Story Hunk 首次 `12/13`，缺少 `buildConflictHunk`；实现精确 revision 定位后 `13/13`。
2. 策略投影首次 `13/14`，缺少 `previewConflictResolution`；实现四策略统一投影后 `14/14`。
3. Run 单项命令首次 `50/51`，新命令没有分支并返回 `undefined`；实现单项状态转换、完整 Artifact 和事件投影后转绿。
4. Workbench 覆盖真实持久 revision、刷新和五源完整主链；GitHub debt、Official、Policy 两项、Semantica 只佐证均转绿并到达 `READY_FOR_OUTPUT`。
5. 负面测试用攻击者同步重算 Preview SHA 的方式篡改 assertion、diff 与 provenance，首次暴露通用结构校验不足；Story 完整纯重投影 validator 注入 Run 后，definition side、assertion ID、GAP、disposition、Patch、diff、provenance 与 resolution ID 全部在 CAS 前复算。
6. 二轮攻击进一步同步伪造 Hunk block 全文、Hunk SHA 和所有派生 Result／Patch／Diff／provenance／Preview SHA，证明只从 Artifact 重投影仍会自证。Run validator 改为异步读取运行登记的持久化 revision 并重建 expected Preview 后，生产边界直接测试转绿。
7. Semantica receipt 被重排到对应决定之前并重编号时首次仍可加载；Run 改为只接受截至当前事件位置已经验证的 resolution 投影。同步伪造 receipt event source 与 payload 为 Official 的第二条攻击，由 Story corroborator validator 拒绝。
8. 进一步互换 GitHub `DOCUMENT_REVIEWED`／`CONFLICT_FOUND`，或把 receipt 移到 Semantica `SOURCE_READ_STARTED` 之前并重编号，首次仍可加载。Run 新增每来源完整 lifecycle projector、来源区间不交错和交付前置约束，并拒绝重复 `(sourceId, conflictId)` receipt，三条直接攻击测试转绿。
9. 从最终五源时间线删除一条 Semantica receipt 并重编号，首次仍能以单条佐证进入 `READY_FOR_OUTPUT`。Run 新增按来源 receipt-set validator；生产 validator 沿当前文档 revision 血缘回到首次生成 revision，重建 Semantica compilation，并要求事件集合逐项等于当时的 `corroboratedConflictIds`，删除攻击转绿。含 receipt 的完成命令若缺集合 validator 也必须在 CAS 前拒绝，直接测试确认不会提交“已写入但不可读”状态。
10. Semantica 仅修改 `value.normalized` 时，Story 已生成 changed block 和新 corroboration 投影，但 Run 因 `changedSections=[]` 错误拒绝。Run 改为只强制 `changedBlockIds` 非空；完整五源 Workbench 测试确认 r2 成功、当前投影更新、历史 receipt 仍绑定生成 r1 且可继续审阅到 `READY_FOR_OUTPUT`。随后把两条 receipt 移到 `DOCUMENT_REVISED` 后并重编号的攻击也已转绿：来源生命周期明确要求生成 receipt 全部早于第一条 revision。
11. 两个独立 Runtime 在同 revision 并发决定同一项，测试确认仅一个 CAS 成功，最终只有一条事件和一个 resolved ID。
12. TypeScript 首次发现当前 conflict ID 未收窄、时间线缺少两类图标和 Artifact parser unknown；修复后 `tsc` 无诊断。

## 验证边界

最终验证仅运行 brief 指定的五个测试文件、legacy `document-alignment`、app TypeScript、变更文件 oxlint 与 trailing whitespace 检查；没有运行全仓测试、开发服务器或远端服务。工作区中的整个 `linguan-prototype-v2/` 在父仓库视角为既有 untracked 目录，所以 `git diff --check -- linguan-prototype-v2` 不会枚举内部文件，另以定向 trailing-whitespace 扫描补足。

```text
brief 五文件：tests 109 / pass 109 / fail 0 / exit 0
legacy document-alignment：tests 5 / pass 5 / fail 0 / exit 0
npx tsc -p tsconfig.app.json --noEmit：exit 0
变更 TypeScript/TSX 定向 oxlint：exit 0，无 warning
```

## 独立只读复审

独立 reviewer 首轮在初始 `104/104` 基础上判定 `Critical 0 / Important 5`，因此当时不通过：Run 可逆序决定、决定时间与恢复不真实、同步重算 SHA 后仍可篡改 assertion identity／GAP／disposition、异步策略预览可出现所见与保存不一致、blocked 右栏没有当前 Hunk locator／影响对象。

五项均先补直接 RED，再修为 GREEN：Run 强制 first-unresolved 且加载核对 `CONFLICT_FOUND` 前置与事件前缀；Workbench 使用真实 `now()` 并以 pending resolution pointer 恢复 Run 已成功但指针收尾失败；Story 完整纯重投影 validator 注入 Run；UI 以 request token 丢弃旧策略响应并在 apply 核对 strategy；facts inspector 与 CURRENT 时间线项投影 Hunk incoming block。

二轮 reviewer 又复现 Artifact 自带 Hunk 可以在同步篡改全文并重算整链后自证，以及 Semantica receipt 可以重排到决定之前；两项均补直接攻击 RED。实现以运行登记的持久化 revision 重建 expected Preview，并以事件位置之前的 validated resolution 投影和 Story corroborator identity 校验 receipt。后续复核又发现 review/found 及 receipt/source lifecycle 仍可局部重排、单条 receipt 可被删除、normalized-only revision 被错误阻断，因此加载新增完整来源事件状态机、来源区间、receipt 唯一性和生成 revision compilation exact-set 校验，并允许 changedSections 为空。

READY-5 冻结快照独立最终 verdict：**Spec PASS / Quality PASS**；`Critical 0 / Important 0 / Minor 1`。Reviewer 独立复跑 brief 五文件 `109/109`、legacy `5/5`、TypeScript、定向 oxlint、diff 与 trailing 检查均通过。唯一非阻断 Minor 是策略预览的过期失败／`finally` 仍可能短暂显示旧错误或提前结束 loading；成功结果已有 request token，apply 仍核对 conflict／strategy／Hunk，因此不会保存与所见策略不一致的决定。
