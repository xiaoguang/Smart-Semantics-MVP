# TODO：多标签协作写入

当前 Demo 允许多个浏览器标签进入工作区；角色仍可在同一标签内切换。底层 CAS/revision 保护继续保留，但本轮不承诺多个标签同时写入同一运行状态。以下能力未来单独规划：

- 允许两个浏览器标签同时打开同一个 `StandardizationRun`。
- 用真实共享 IndexedDB metadata 复现同 revision 并发写入，证明只有一个 CAS 胜者。
- 输家刷新后读取精确胜者 revision，保留助手草稿、选择和滚动锚点，清除陈旧 Preview，且绝不自动重放写命令。
- 覆盖标签关闭、崩溃、离线恢复、角色变化和 pending saga 收尾。
- 在承诺跨标签协同时，必须完成上述浏览器 E2E 与独立 Spec／Quality 审查。

这项 TODO 不改变现有来源决定、冻结交付物、管伊佳黄金 V1 或零售 Catalog。
