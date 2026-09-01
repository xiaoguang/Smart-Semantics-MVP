# GitHub Code Agent 实施工期校准（2026-09-01）

本文是工期研究记录，不是目标设计或阶段合同。它回答两个问题：Stage01 实际用了多久；结合这次真实吞吐量，后续还需要多久。估算基于本地 Git、Progress、测试记录和当前源码，不把模型等待、网络等待或用户决策时间算作连续工程时间。

## 1. 能确认的 Stage01 时间

本地 Git 给出的三个精确时间点是：

| 事件 | Commit | 本地时间 |
| --- | --- | --- |
| 目标架构最终文档基线 | `a6de913` | 2026-09-01 05:59:53 -02:30 |
| 第一批 Foundation 提交 | `e8b64cb` | 2026-09-01 12:05:05 -02:30 |
| Stage01 实现提交 | `d497d14` | 2026-09-01 15:04:00 -02:30 |
| Stage01 推送记录提交 | `f9d7cf6` | 2026-09-01 15:04:46 -02:30 |

因此有两个不同但都真实的观察值：

- 严格从 Foundation 提交到 Stage01 推送记录，墙钟跨度是 **2 小时 59 分 41 秒**。
- 从最后一次目标架构文档提交到 Foundation 与 Stage01 都发布完，墙钟跨度是 **9 小时 4 分 53 秒**；其中 Foundation 第一段占 6 小时 5 分 12 秒。

复现命令：

```text
git show -s --format='%H %aI %cI %s' a6de913 e8b64cb d497d14 f9d7cf6
git reflog --date=iso-strict --format='%h %gD %gs %cd'
```

这些是可审计的 elapsed span，不是精确人时。现有 Progress 只记录到日期，没有任务开始/结束的时分秒；两个 Agent 可能在稳定接口两侧交错工作，Git commit 时间也只说明内容何时形成提交。因此不能声称“Stage01 恰好消耗 2.99 个单人工时”。

## 2. 这 9 小时实际交付了什么

代码量说明这不是只改了几个壳类型：

| 区间/分类 | 文件 | 新增 | 删除 |
| --- | ---: | ---: | ---: |
| `a6de913..e8b64cb` Foundation 第一段 | 20 | 1,515 | 1 |
| `e8b64cb..d497d14` 总提交 | 105 | 9,321 | 529 |
| 上述总提交中的 Foundation 收尾、POM 与 Store 测试 | 55 | 4,300 | 372 |
| 上述总提交中的 Stage01 生产代码与测试 | 39 | 4,476 | 0 |

复现命令使用 `git diff --numstat`，分类路径分别是 `target/artifacts`、`pom.xml`、`.mvn` 与 `target/stage01`。

Foundation 的直接门禁通过 8 个测试；Stage01 的 Capture、M1、M2、M3 直接 selector 通过 9 个测试。证据见 [Foundation Progress](../../progress/target-foundation-terra-green.md)、[Capture Progress](../../progress/target-stage01-capture.md)、[Source Index Progress](../../progress/target-stage01-source-index.md) 和 [Publication Progress](../../progress/target-stage01-publication.md)。

但是，这个结果是“持久化 synthetic vertical”，不是 Stage01 最终验收。当前设计审计明确保留以下工作：完整 run 输入注册和编排、预算分片、全部安全反例，以及固定 jshERP 完整 commit 的端到端离线运行；小型 repository 不能获得完整仓库完成资格。证据见 [Stage01 详细设计的当前实现差距](../stages/01-freeze-source.md#9-当前实现差距审计) 和 [总体设计当前审计](../DESIGN.md#15-当前实现审计与目标设计分开)。

所以，原来 Foundation `8–12h` 加 Stage01 `10–14h` 的合计 `18–26h`，与已交付纵切的 `9.08h` 相比，当前完成速度约为原估算的 **35%–50%**。这个比例不能直接外推为全部后续工作只需原计划一半：Foundation 仍缺 Run Manifest Store、最小 execution state 与其直接验收；Stage01 的完整仓库验收留在最终 Z 批次，而且 Stage03 数据流、Stage05 全仓流程闭合、Stage06 模型任务守恒和 Stage08 Trace 的语义难度显著更高。

## 3. 校准方法

后续估算使用三类修正，不用单一倍率：

1. **已证实的工程吞吐修正。** Canonical Store、Stage01 三模块和多轮 RED/GREEN 在约九小时内形成，说明原计划对普通 record、持久化和直接 selector 的缓冲偏大；这类模块按原估算的约 55%–75% 重新估计。
2. **语义关键路径不激进压缩。** 调用/控制/数据流、逐原子 Proof、跨 Flow 合并、Typed Trace 需要真实引用闭包和 mutation 测试，按原估算的约 75%–100% 保留。
3. **未完成内容不当作节省。** Foundation 剩余项单列；完整 jshERP Stage01–05 离线验收、旧实现删除、52 项 fresh reopen 和质量门仍全部保留在 Z。

表中的时间是最多两个 Agent、重型 Maven 串行、依赖已缓存时的**连续墙钟时间**。范围下界假设下一个 RED 准确且局部并行生效；上界包含一次正常的 fixture/实现纠正，但不包含架构变更、外部授权或实时 Luna 调用。

## 4. Stage02 当前状态与剩余时间

截至 2026-09-01 15:59 -02:30：

- M1 `ApplicationProfileDetector` 已通过 4 个直接测试。
- M2 已形成只因四个目标类型缺失而失败的准确 RED。
- M3、M4 尚未开始。

从 `f9d7cf6` 到上述状态不足 55 分钟，且这段时间同时包含 M1 和 M2 RED，因此只能把它当作 M1 的**墙钟上界证据**，不能当作精确人时。状态证据见 [M1 Progress](../../progress/target-stage02-application-profile.md) 和 [M2 Progress](../../progress/target-stage02-http-entry.md)。

| Stage02 部分 | 当前状态 | 继续所需连续时间 | 估算依据 |
| --- | --- | ---: | --- |
| M1 应用画像 | 已完成 | 0 | 4 个 direct tests 已通过；完成跨度小于 55 分钟且还包含 M2 RED |
| M2 HTTP 入口 | 准确 RED 已存在 | 1.5–2.5h | JavaParser route 组合、证据 span、动态注解 Gap、module publication |
| M3 Mapper 能力 | 未开始 | 2–3.5h | Java/XML namespace/statement 绑定、安全 DOCTYPE、动态/歧义 Gap |
| M4 发布与阶段门 | 未开始 | 1.5–2.5h | 四份 semantic payload、五文件 Stage publication、分母/accounting、文档与 push |

M2 与 M3 在 M1 稳定后可由两个 Agent 并行，因此 Stage02 的剩余墙钟不是三行简单求和，校准为 **4–7 小时**。

## 5. 后续各批次新估算

| 批次 | 主要剩余工作 | 原估算 | 校准后的连续时间 | 中位工作值 |
| --- | --- | ---: | ---: | ---: |
| F residual | Run Manifest Store、四状态 execution、原子安装 IT | 原 F 中未完成 | **3–5h** | 4h |
| Stage02 remainder | M2、M3、M4、阶段 review/commit/push | 原 Stage02 10–14h | **4–7h** | 5.5h |
| Stage03A | 代码结构图、调用图、控制流图 | 9–12h | **7–11h** | 9h |
| Stage03B | 数据流图、证据图、五图发布 | 9–12h | **8–13h** | 10.5h |
| Stage04 | Fact candidate、逐 atom Proof、Gap/accounting | 9–13h | **6–10h** | 8h |
| Stage05 | 全入口 Flow、Capsule、coverage 与双 Flow 隔离 | 11–17h | **8–13h** | 10.5h |
| Stage06A | 每 Flow R0 task/runner、唯一 Registry freeze | 5–7h | **4–6h** | 5h |
| Stage06B | R1/R2、三 Provider adapter、守恒与发布 | 6–9h | **5–8h** | 6.5h |
| Stage07 | proposal admission、硬锚点 merge、唯一仓库知识 | 6–9h | **5–8h** | 6.5h |
| Stage08A | 九章 planner、plan-only renderer、Typed Trace | 7–10h | **5–8h** | 6.5h |
| Stage08B | archive DAG、Run Manifest、独立 validator | 6–9h | **6–10h** | 8h |
| A | 同一 Core 的 Java、CLI、loopback HTTP | 8–10h | **6–9h** | 7.5h |
| Z | 双 Flow/0 Flow、完整 jshERP Stage01–05、52 项重开、旧实现删除、质量/release/双轴审阅 | 10–14h | **10–16h** | 13h |

Z 不下调的原因是它吸收了当前 Stage01 尚未完成的完整仓库与安全验收，同时还承担旧代码物理删除后的 clean-checkout 复验。Stage03B、Stage05、Stage08B 保留较宽上界，是因为它们分别承载跨层数据链、完整仓库分母和最终 Trace/归档 DAG，都是发现设计与实现错位概率最高的部分。

## 6. 总和与完成日期口径

从当前状态继续，剩余总量为：

- **可行下界：77 个连续小时，约 3.2 天**；要求 RED 准确、没有跨阶段合同变化，并充分利用允许的双 Agent 窗口。
- **中位工作值：约 100 个连续小时，约 4.2 天**。
- **保守上界：124 个连续小时，约 5.2 天**；包含正常的 fixture/实现返工和最终验收修正。

这比原计划当前剩余的约 `96–136h`（尚未单列 Foundation residual）略有下降，但不是按 Stage01 的 0.35–0.50 倍机械砍半。更可信的承诺口径是：**按现有速度不停执行，目标为约 4 天，计划区间 3.2–5.2 天**。

任何一个批次若超过其新上界 25%，应立即用 Progress 记录具体原因，并重新分类为：合同缺口、真实源码复杂度、fixture 返工、工具兼容或环境等待。不能用跳过证据闭包、阶段落盘、直接 selector、文档同步或逐 Stage 推送来追赶时间。

## 7. 置信度

- Stage01 的 commit 时间和测试结果：**高置信**。
- Stage01 的精确有效人时：**低置信**，因为缺少时分秒级任务日志且存在并行。
- Stage02 剩余：**中高置信**，M1 已完成且 M2 有准确 RED，接口不确定性较小。
- Stage03–05：**中等置信**，取决于完整 jshERP 对 Symbol Solver、动态 SQL 和数据流 Gap 的真实分布。
- Stage06–08、A：**中等置信**，合同详细但尚未在新 target 实现中验证所有有限键、归档和 Trace 组合。
- Z：**中低置信**，最易受完整仓库数据量、旧代码删除后的质量门和独立审阅修正影响。

因此应把约 100 小时用于持续调度，把 77–124 小时用于对外风险范围，而不是承诺单点完成时刻。
