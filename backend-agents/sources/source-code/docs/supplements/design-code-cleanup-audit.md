# 设计—代码清理审计

## 1. 当前结论

本轮清理已经把生产执行面收敛到一个入口和一条业务过程路线：

```text
source-analysis
  ├─ capture-local-git / start
  ├─ plan-materials
  ├─ execute-step --target flow-interpretation
  ├─ execute-step --target repository-knowledge
  └─ inspect / artifact / render
```

`RepositoryRunMain`、旧 `generate` 编排、singleton `ProcessExplainer` 和旧九章
生产器已经退出生产代码。当前 Step07 是
`BusinessProcessDiscovery → BusinessProcessPublisher`，发布五项 v2 过程产物。

历史九章没有被删除。旧 checkpoint 的 module 地址、artifact policy、数据类型、
严格 reader 和确定性 renderer 保留，因此已有 `document.md` 可以观察和重渲染，
但不能再通过退役 producer 生成新的旧格式结果。

本轮没有运行 JDT、没有重新生成 326 条 Activity、没有重新分析业务过程、没有
调用真实模型，也没有修改任何已有运行产物。

## 2. 唯一入口与运行合同

唯一配置入口是：

```bash
source-analysis --config /absolute/path/config.yaml <command>
```

配置路径必须是绝对路径。模型服务、并发、路由和认证环境变量引用只来自同一
`sourceAnalysis.modelJobs`；旧 `--provider-config` 直接作为非法参数拒绝。

`SourceAnalysisCli` 是唯一进程入口。内部 `ConfiguredSourceAnalysisRuntime` 只做
薄委派；运行编排在 `SourceAnalysisExecution`，YAML、模型服务和技术/
业务 profile 合同在 `RepositoryRunConfiguration`。旧的 2542 行主类没有整体换名
后继续充当第二个入口。

`start` 只创建 path-free `QUEUED` run。`execute-step` 可以接收显式 `--run`；该
run 必须仍为 `QUEUED`，且持久化请求必须与当前来源和配置完全一致。停止的 run
不会被重新激活；未传 `--run` 时只创建一次新 run。

执行意图明确分开：

| 意图 | 上游 | 正式输出 |
| --- | --- | --- |
| `PREPARE_MATERIALS` | 固定源码 | M10 business materials |
| `EXPLAIN_ACTIVITIES` | M10 checkpoint | Activity checkpoint 或一个明确小样 |
| `DISCOVER_PROCESSES` | 已审 Activity batch | Step07 五项过程产物 |

单材料小样可以结束且不伪造全仓 `AnalysisRunOutput`；`inspect` 能如实显示这种
finished preview。新 Activity-only 输出使用 `analysis-run-output-v4`；材料、当前
过程和历史完整报告的 v3 输出仍严格读取。

## 3. 已删除内容

### 3.1 生产代码

- `BusinessAnalysisWorkflow`、`PersistedBusinessRunExecutor` 及其旧结果/配置类型；
- singleton `ProcessExplainer`、旧 knowledge checkpoint producer/reader 和旧过程 DTO；
- `BusinessReportPublisher`、旧 report checkpoint producer、profile、prompt catalog 和请求；
- 旧 process-group/report Prompt 资源；
- 只为上述生产路线存在的转换方法和兼容执行意图。

### 3.2 测试

删除仅证明退役 producer 行为的单元测试和真实模型 IT，包括旧过程分组、旧九章
生成、旧完整 workflow 和旧 generate 复用测试。共享价值没有随类名一起删除：

- Activity 覆盖、Provider、并发、来源、存储和复用仍由当前模块测试覆盖；
- Step07 discovery/publication 继续覆盖目录、候选、归并、coverage 和五文件重开；
- 新增历史九章直接读取测试，不依赖已删除 producer；
- 当前 CLI、真实 Agent lifecycle 和输出登记有直接测试。

### 3.3 没有删除的历史合同

以下名称仍会出现在代码中，但只用于历史读取，不是第二条生产路线：

- `process-explainer` module address；
- `business-report-publisher` module address；
- historical complete-report v3 output kind；
- 旧 artifact policies 和 `BusinessOutputArtifactKey` 的历史报告键。

删除这些内容会使已经交付的报告不可读，因此不属于死代码。

## 4. 已确认并修复的设计—代码偏差

### 4.1 非无损合并不再使全仓失败

仓库归并模型可以建议 `MERGE_INTO`，但 Java 的机械无损条件可能不满足。当前行为：

- 保留两个完整原过程；
- 将目标处置收窄为 `KEEP`；
- 记录没有合并的原因；
- 不拼接阶段、不猜测二者关系、不追加模型调用。

未知 ID、损坏输入、非法引用等仍然 fatal，没有因该修复而放宽。

### 4.2 JavaParser 可以从正式配置选择

组合根不再以 `JDT_ENGINE_REQUIRED` 拒绝 `javaEngine: javaparser`。选择 JavaParser
时使用现有 adapter/算法且不启动 JDT；本轮没有为它增加通配 import、继承、重载
或 Symbol Solver 能力，也不承诺与 JDT 返回相同结果。

### 4.3 两处同调用重复读取已削减

- Step05 分支判断复用本次已经重开的 Step03 结果；
- Fact/Proof 发布在同一次调用中复用已验证 candidate view。

跨进程重开、来源/字节/结构校验和损坏拒绝继续保留；没有增加全局缓存或重新设计
证据体系。

### 4.4 本机构建配置不再提交

共享仓库只提交 `.mvn/toolchains.example.xml`。本机路径写入被忽略的
`.mvn/toolchains.local.xml`；CI 从 `JAVA_HOME` 生成相同形状的本地文件。宿主
Java 17 与 JDT tool JVM 仍是两个独立配置。

## 5. 保留与延期

### 5.1 明确保留

- JDT 与 JavaParser 两个引擎及其有效测试；
- Step01–05 技术产物、Fact/Proof、Flow/Capsule 和 canonical stores；
- M10、326 条已审 Activity、Step07 过程、DRAFT/REVIEW、journal 和来源文件；
- 当前业务过程 discovery/publisher；
- 历史报告 reader/renderer；
- JDT 调研程序和对照资产。

### 5.2 不在本轮扩大

[更多发现：跨对象业务串联与 Mapper SQL](more-findings.md) 保持原文不变，继续记录：

- 候选外已保存 Activity/源码的受控补读；
- Mapper XML/SQL 尚未进入业务阅读材料；
- “请购→采购订单→采购入库”等跨对象上层生命周期；
- 多 Maven module 的 JDT project 建模边界。

这些是业务发现增强，不是本轮死代码清理。它们不能以清理名义触发 JDT 扫描、
Activity 重跑或业务过程重跑。

新的 Step08 也仍是未来工作。设计要求它只消费当前 Step07 v2 catalog；不能恢复
旧 producer，不能重新从 raw Activity 发现过程。

## 6. Progress 与提交审计

`progress/` 是计划进行中的临时交接，不是永久产品文档。整个计划结束时：

1. 决定进入 `AGENTS.md` 和当前设计；
2. 遗留进入明确 backlog（本轮主要是 `more-findings.md`）；
3. 验证结果进入本审计；
4. 删除该计划及已结束计划的临时 progress，保留 `progress/TEMPLATE.md`。

运行产物、模型中间结果、JDT 材料和历史文档不属于 progress，不能随之删除。

提交清单不得包含：

- `.workspace` 或客户运行结果；
- Provider 凭据、登录目录或 API key；
- `.mvn/toolchains.local.xml` 或其他本机绝对路径配置；
- 仓库根无关 `docs/research/`；
- 对 [more-findings.md](more-findings.md) 的意外改写。

## 7. 验收状态

已完成的直接检查：

- production/test compilation；
- 唯一 CLI 参数与配置读取；
- Activity-only 和 process-catalog run output 重开；
- Local Agent 的材料、Activity、过程三种明确意图；
- 当前 Step07 discovery/publication；
- JavaParser 配置不启动 JDT；
- 非无损合并保留原过程；
- 历史九章 checkpoint reader/renderer；
- `git diff --check`；
- `more-findings.md` SHA-256 仍为
  `59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e`。

本模块完整本地 CI 使用两个串行 Maven 进程：

```bash
mvn -t .mvn/toolchains.local.xml spotless:check
mvn -t .mvn/toolchains.local.xml -Pquality clean verify
```

这里不是重复执行测试：第一条只检查格式，第二条编译并执行一次测试和质量检查。
实测将 `spotless:check` 与使用 Java 17 toolchain 的 `clean verify` 放进同一 Maven
进程，会使后续 forked test compilation 在宿主 Java 26 进程中出现虚假的生产类
`cannot find symbol`；拆成两个进程后，单独 `clean test-compile` 与完整质量构建均正常。

2026-09-16 的最终本地结果：

- Spotless 检查 572 个 Java 文件，无格式差异；
- 编译及测试通过：541 个测试，0 failures，0 errors，2 skipped；
- SpotBugs：0 bugs，0 errors；
- PMD 检查通过；
- `-Pquality clean verify` 总耗时 7 分 57 秒；
- 未启用 `real-jdt-it`，未运行外层工程测试，未调用真实模型；
- 首次受限沙箱运行只有 3 个 loopback HTTP 测试因禁止绑定端口而报错；允许本地
  回环端口后同一构建通过，确认不是产品代码失败。

提交与 PR 状态将在最终合入后补充到交付说明；本审计不记录易过时的分支号或
远端流水线状态。
