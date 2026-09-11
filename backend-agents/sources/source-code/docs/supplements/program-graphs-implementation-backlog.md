# 程序图：稳定能力与当前待办

权威设计是 [Step03](../analysis-steps/03-program-graphs.md)，当前实施次序是 [连贯代码上下文计划](../plans/coherent-code-context-implementation-plan.md)。本页更新当前事实与剩余范围，不重新设计五图，也不把历史测试记录当本轮实测。

## 1. 已有能力

CodeStructureGraphBuilder、CallGraphBuilder、ControlFlowGraphBuilder、DataFlowGraphBuilder、EvidenceGraphBuilder、ProgramGraphSetPublicationSpecifier 和 ProgramGraphsExecution 已存在，输出五图、graph-index、graph-gaps 与 receipt。固定完整 jshERP 719 文件已有图/Fact 运行保存证据，public Agent、runtime、CLI 与业务工作流也已存在。

既有 57/57、47/47 等 bounded selector 结果留在其各自 progress 记录，不能汇总成这次新测试通过。完整所有 Java/框架构造或整仓业务质量仍不能由一次运行证明。

## 2. 保留的技术边界

- 五图只建代码关系与位置，不生成业务分类、Process 或行业 Proof。
- call target 只有已有唯一绑定才是 EXACT；ambiguous/unsupported 不补边。
- frozen Java 内 actual→formal 与 boundary argument 分别表示；Mapper XML 静态结构不等于外部值传播或执行成功。
- 每个已声明 graph element 的 owner、endpoint、source/rule basis 准确；局部 Gap 与仓库 scope Gap 分开。
- 完整 source/entry/candidate 分母不缩小；一条成功切片不代表整仓完成。
- 技术产物、canonical 身份和原子安装保留；普通同进程复用 immutable views，磁盘/import 重开时检查保存 identity/hash/schema/ref/basis。
- M6 序列化/校验已有图，不重新跑 builders；Step05 是入口上下文唯一拥有者，Step06 不再直接从图重建另一条链。

## 3. 已完成并由当前测试保持的接力

| 已实现能力 | 保持边界 | 已有验收重点 |
| --- | --- | --- |
| Step05 从 graph/data refs 保存 actual/formal 与 boundary 关系 | Controller→Service formal 与 Service→Mapper boundary 不混写 | EntryContext/Capsule/Builder 直接测试保持参数位置与来源 |
| Step05 从安全 locator 保留 try/catch 等 SOURCE_CONTEXT | 不把源码结构冒充当前 CFG 的异常边 | 模型材料保留分支原文并准确写 basis |
| Step05 保存带代码 EntryContext，Builder 直接消费 | Builder 不扫描 Java、不重建 graph/Proof 或猜 callee | 一个对象即可读调用/传参/分支/返回/限制 |
| 普通 reader/publisher 复用 owner immutable 结果 | 磁盘/import 边界仍核验 identity/hash/schema/ref/basis | 不重复 enumerate/compile/project；损坏 bytes/refs 仍失败 |

无 strict Fact 不阻断安全阅读，无完整 Flow 也由 Step05 保存上下文。无图的有界源码候选定位由 Step05 标未证明绑定；不因此新增 resolver 或图层兼容通道。固定财务历史 run 没有 Step05 publication 的事实不否定当前通用实现，也不能跨 run 补造该样本已完成链。

## 4. 仍有限制但不阻断本次业务材料交付的能力

| 能力范围 | 现状/正确处理 |
| --- | --- |
| M1 全部多模块/config/Mapper include 形状 | 按现有 parser 支持范围保留结构和精确 Gap，不猜外部资源 |
| M2 泛化 dispatch、classpath、递归/循环 worklist | 保持明确 UNKNOWN/AMBIGUOUS 和有限预算；不把静态候选定位变成 exact |
| M3 任意 nested/exception/loop/control join | 已支持结构照常用，缺口以源码上下文可读展示；不先穷举全部路径 |
| M4 alias/branch joins/完整 return-use 形状 | 已知边保留，未知处停；不跨 Mapper/XML 推断外部数据效果 |
| M5/M6 扩大 mutation/跨根/资源矩阵 | 修改相关规则才跑对应 targeted cases，不作为所有业务阅读的新前置 |

这些是真实支持范围，不应触发“重新推导八步架构”。只有当前改动确实涉及某规则才补对应 Luna RED/Terra GREEN；不能为了财务查询材料先重建完整 static analyzer。

## 5. 下一动作与测试限度

当前实施计划不再修改 Step03/04 稳定算法、Step05 consumer 或 ordinary replay 路径。后续只在安全清理旧 interpretation 消费者时保持这些直接测试：参数边与 boundary 准确传递、源码上下文不冒充图、wrong owner/ref/source 拒绝、全部入口 coverage。

未来实施只跑新增或直接覆盖变更的 selector，不执行全套 Maven，不运行客户代码、网络、capture 或真实 Provider，除非获得新的明确授权。本轮为文档同步，未运行任何这些命令。历史 DepotHead Gap/0 Flow 与有界正例保留原有含义，不改写为新的全仓验收。
