# JDT LS 源码导航可行性执行计划

> **状态：PLAN_ONLY。** 本文件只规划一次后续、独立授权的研究性试验；截至本文编写完成，JDT LS 尚未安装、启动、索引或测量。计划完成不等于工具通过。
>
> **For agentic workers:** 逐项使用本文 checkbox 交接。核心试验通过后立即停止工具选型；出现明确限制时先写 `LIMITED`/`FAIL` 结论并与用户讨论，不自动试 CodeQL、Joern 或其他分析器。

**Goal:** 用最小试验判断成熟的 Eclipse JDT Language Server 能否在固定 jshERP 源码快照中，从 Controller 调用点可靠定位真实 Service/helper 实现及包含条件、返回和副作用调用的完整方法体，形成一份 LLM 可直接阅读的 JSON 包。

**Architecture:** 将固定 commit 的全部主 Java 源码一次性投影为无构建文件的 unmanaged workspace；用轻量现有 LSP client 驱动 JDT LS。JavaParser 只枚举已访问方法中的调用语法位置与实参文本，JDT LS 的 declaration/definition/implementation 与 call hierarchy 负责绑定候选，`documentSymbol.range` 负责回取固定源码原文；不自建 Java 类型、重载、继承或包名解析器。

**Tech Stack:** 后续核定并固定的官方 JDT LS milestone、与该版本相容且至少 Java 21 的启动 JDK、独立 Java research harness、Eclipse LSP4J、现有已缓存 JavaParser 3.28.2 的 syntax-only AST、Jackson 2.21.4 和 UTF-8 JSON。不引入 Python/Java bridge；Serena 不进入这次试验。

**Spec:** 本试验服务于[八步接力](../DESIGN.md#2-一条端到端接力)、[03/04/05 职责边界](../DESIGN.md#3-为什么同时保留-030405)和[连贯代码材料](../DESIGN.md#5-连贯业务材料怎样形成)，不重写这些目标，也不直接产生 Step03 图、Step04 Proof、Step05 EntryContext 或业务报告。

## 0. 当前事实、权限和非目标

- 当前分支/工作树固定为 `codex/jdtls-source-navigation-feasibility` / `/private/tmp/linguan-source-analysis-process-design`，基线为 `db28f8d05291a807fcf5ee478073994664216337`。
- 已有只读 clone 为 `backend-agents/sources/source-code/.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580`；它的当前 HEAD 是 `ad6cf886…`，**不是**批准输入。任何读取必须显式寻址 `8c30ce7861570458920175e200bb2a6442713580`，不得 checkout、切换或使用 working tree。
- 当前 `PATH` 没有 `jdtls`。本机已有 JDK 17.0.19 和 26.0.1，没有 JDK 21；官方当前 README 要求 Java 21 起。后续先核对“固定 JDT LS milestone ↔ 启动 JDK”兼容性，不能仅因存在 JDK 26 就假定可用。
- 当前任务只授权本分支的计划与导航文档；不授权下载/安装、JDT LS 子进程、源码投影、索引或网络。后续开始试验前，必须单独获得安装官方预构建 server 与执行固定输入试验的明确授权。
- 本计划的生成式内容清单为 **none**：不调用产品模型，不生成九章。任何未来 Luna 解释必须在人审源码结果后另提包含冻结输入、输出、审阅和轮次约束的独立计划，不能成为本次工具通过门。
- 索引期间禁止网络，禁止客户 Maven/Gradle、plugin、test、script、application、class loading、annotation processor 和远程依赖解析。JDT 自身 parser/compiler binding 可以运行；设置只是纵深防护，不等于沙箱证明。
- 不修改现有八步、四个业务 Module、既有输出、公开 `RepositoryAnalysisAgent`、POM、生产 package、Schema、Fact/Proof 或 publication。研究成功只产生采用建议，后续生产接入仍需单独设计和批准。
- 不做全仓业务分析、完整静态调用完备性证明、CFG/数据流重建、无限递归 utility 展开、JPA/AOP/反射推断、Maven suite、工具矩阵或性能基准。

## 1. 假设、判定和停止条件

**待证假设 H1：** 在完全不导入客户构建的 unmanaged workspace 中，JDT LS 能把 `UserController#registerUser` 的调用 token 解析到实际 `service/UserService.java` 方法，并返回可用于读取完整方法体的精确源码范围；`service.*` 不会再让真实 `UserService` 消失。

当前具名缺陷正是自定义 resolver 看到 `UserController` 的 `com.jsh.erp.service.*` 后漏掉 `UserService`；本试验只验证成熟绑定能否消除这个缺陷，不修补旧 resolver。

**待证假设 H2：** 一个通用 walker 只拿入口位置和全量源码上下文，不拿手写 callee 文件/方法清单，也能保存每个已访问方法的完整原文，并给其中每个语法调用点一个诚实处置：唯一目标、多个候选、未解析、外部目标或显式边界/循环/诊断上限停止。

**判定只有三种：**

- `PASS`：两条真实入口均生成机器采集、可读、来源精确的 packet；主样本三项关键 Service 方法体齐全，次样本到达 Mapper 边界。oracle 只在输出落盘并计算摘要后读取，没有目标体或 callee 列表注入 navigator。
- `LIMITED`：安全启动和部分导航成立，但存在一个具名、可复现、会影响核心采用方式的限制，例如某类缺失依赖使关键绑定 unresolved，或范围不能稳定覆盖完整方法体。正常地保留多个真实 implementation 为 `CANDIDATES` 并不自动构成限制。
- `FAIL`：无法在禁止客户构建/网络的条件下初始化并索引；不能从主入口三处 token 到达真实 Service 方法体；来源不是精确 commit；或必须靠手写目标路径/自建 Java resolver 才能得到结果。`verdict.reasonClass` 必须区分 `ENVIRONMENT_OR_AUTHORIZATION` 与 `JDT_NAVIGATION_CAPABILITY`；前者不能被写成 JDT 能力失败。

若 `PASS`，立即停止，不评估 CodeQL/Joern。若 `LIMITED`/`FAIL`，也先停止；只有用户确认该限制值得换工具后才另写方案。不得自动形成 bakeoff 表。

## 2. 固定输入和隔离路径

后续试验只使用这些路径；`research/` 是隔离的 throwaway harness，不是生产 package，所有索引/安装/输出落在已忽略的 `.workspace/`：

| 用途 | 精确路径 |
| --- | --- |
| 研究说明与 release/client 决策 | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/README.md` |
| 独立 harness POM | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml` |
| 最小 materializer/LSP driver | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/NavigationProbe.java` |
| 独立 packet checker | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/PacketOracleCheck.java` |
| 直接 harness test | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/NavigationProbeTest.java` |
| 独立 oracle | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/checks/registration-oracle.json`、`financial-oracle.json`、可选 `structure-oracle.json`（后两项沿用同一 checks 前缀） |
| 可选微型结构 fixture | `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/fixtures/structure-case/src/` |
| 原始固定 archive | `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/input/jshERP-8c30ce7861570458920175e200bb2a6442713580.tar` |
| unmanaged Java 投影 | `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/projection/src/` |
| 静态资源投影 | `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/projection-resources/` |
| 固定 server/JDK/client 清单 | `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/tool-manifest.json` |
| JDT 配置、data、日志 | `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/runtime/configuration/`、`runtime/data/`、`runtime/logs/`（后两项沿用同一绝对前缀） |
| 结果 | `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/results/registration/`、`results/financial/`、`results/structure/`、`results/verdict/`（后面三项沿用同一绝对前缀） |

后续每个 Agent 在写自己交付前都从 `progress/TEMPLATE.md` 创建并持续更新唯一文件：设计/安装预检 `progress/jdtls-tool-preflight.md`，Luna checks `progress/jdtls-packet-checks.md`，Terra harness/试验 `progress/jdtls-harness.md`，触发时的 Sol 调试 `progress/jdtls-debug.md`，最终独立结论 `progress/jdtls-verdict.md`。不得共写同一 progress；每个文件都记录批准、版本/输入、命令、结果、blocker 和 exact next action。

Materializer 从 exact commit tree 中选择**全部** `*/src/main/java/**/*.java`，按 package-relative 路径投影到唯一 `projection/src/`；发生不同内容的路径碰撞即停止。`git archive` 可能受 `export-ignore`/`export-subst` 影响，所以 projected file set 和每份 Java bytes 必须逐项对照 exact tree 的 blob，而非因 archive 成功就宣称一致。它可以另存 mapper XML 供安全静态查询，但不得把 `pom.xml`、`build.gradle*`、`.project`、`.classpath`、`.factorypath`、`.settings/` 或生成目录放入 JDT workspace。初始 `referencedLibraries=[]`；后续若确有一个已命名缺失类型阻断主目标，只能在一次修正中加入人工核过 SHA-256 的可信本地 JAR，禁止 Maven resolver。

两条真实入口共享同一次全源码索引，只做两次 repository entry walk：

1. 主样本：`jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java:357-367`，入口 `POST /user/registerUser`（类级 `/user` 在第 39 行）。
2. 次样本：`jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java:181-196`，入口 `GET /accountHead/getFinancialBillNoByBillId`。

主样本的独立 oracle 核对而不指导导航：第 16 行为 `com.jsh.erp.service.*`；入口 token 位于 363 `validateCaptcha`、364 `checkLoginName`、365 `registerUser`。目标正文应由工具自己定位到 `service/UserService.java:297-319`、`:776-805`，以及 `registerUser` 的 annotation 607 与完整方法体 608–661；packet 可把 annotation 单独记录，也可有意把 source span 扩为 607–661，但不得丢失 608–661 的正文。审阅者应直接看出 `checkcode_flag` 条件、验证码读取/删除与错误、登录名重复限制、`DEFAULT_MANAGER` 限制、用户默认属性/状态、用户写入、租户/角色关系设置与租户默认值。oracle 文件绝不能作为 driver 输入。

次样本 oracle 核对 `AccountHeadService.java:442-444`、`datasource/mappers/AccountHeadMapperEx.java:44-45` 和 `mapper_xml/AccountHeadMapperEx.xml:150-155`。Mapper XML 仅在能廉价复用现有禁 DTD/entity/schema/network 的 namespace+id 静态能力时追加；SQL 缺失不阻断“获得 Service 完整正文”的通过条件。

## 3. 薄 navigator 合同

这只是试验内的最小记录形状，不是新的产品 Schema：

- JavaParser 无 Symbol Solver，只枚举已访问方法内 `MethodCallExpr`、`ObjectCreationExpr`、method reference 的 token range、原始调用文本和 actual argument 文本。这个集合是调用点分母。
- 对每个 token 分别请求 `textDocument/declaration`、`textDocument/definition`、必要时 `textDocument/implementation`；支持 `Location` 与 `LocationLink`，按 source URI + range 去重。接口/抽象方法不能从多个 implementation 中擅选一个，但 diagnostic limits 内每个 repository-local candidate 的 membership、signature/formals 和完整 body 都应保留；超限未展开必须记录。
- `textDocument/prepareCallHierarchy` 与 `callHierarchy/outgoingCalls` 只作辅助交叉核对。`outgoingCalls=[]` 不能证明没有调用，且其响应不被假定含 actual arguments。
- 每个唯一或候选的仓内、获准展开 target 先 `didOpen`，再用 `textDocument/documentSymbol` 的完整 `range` 从固定投影 bytes 切出正文；`selectionRange` 只定位名称，不能代替方法体。片段必须含 signature/formals、条件、try/catch、return 等原文，而非签名或调用列表；actual 文本与同 ordinal formal 并存只供阅读，不冒充完整 dataflow proof。
- LSP `Position` 使用 0-based line 与 UTF-16 code-unit character，`Range.end` 为 exclusive；JavaParser position 为 1-based。请求边界必须从固定 UTF-8 行前缀计算 UTF-16 offset，JavaParser 的 inclusive token end 要加上最后字符的 UTF-16 宽度再转成 LSP end；保存的源码行段统一为 1-based inclusive，并同时保留原始 LSP range，不能把 byte offset 当 character。
- 每个语法调用点都写 `resolution=UNIQUE|CANDIDATES|UNRESOLVED|EXTERNAL` 和 `disposition=EXPANDED|BOUNDARY_STOP|CYCLE_STOP|DIAGNOSTIC_LIMIT_STOP`；候选数组不得伪装唯一。Mapper interface、外部库、循环和诊断上限显式停止。
- 第一跳允许优先请求入口中 363/364/365 的准确 token 位置，因为它们没有提供目的文件或 target body；随后使用同一通用 walker。禁止把预期 `UserService` 路径、方法范围或 callee 清单传入 driver。
- 采用 breadth-first、确定性 source-position 顺序；单 entry 最大 30 个完整方法、深度 4、墙钟 15 分钟。上限只防 runaway 并形成诊断，不是产品业务预算；若关键 Service 正文因上限缺失，结论只能 `LIMITED`。
- packet 保留 snapshot commit、入口、每个方法的符号/相对路径/1-based 行段/完整 snippet、每个调用点的 syntax 文本/actual arguments/LSP 候选/处置，以及 diagnostics/limits。它不声称是 Proof、完整调用图或运行时执行结果。

下面只是**期望记录形状，不是 JDT LS 实测输出**；snippet 是固定 commit 的真实三行源码，用来防止以后把“找到签名”误判为“拿到正文”：

```json
{
  "exampleKind": "EXPECTED_RECORD_SHAPE_NOT_MEASURED",
  "snapshotCommit": "8c30ce7861570458920175e200bb2a6442713580",
  "method": {
    "symbol": "com.jsh.erp.service.AccountHeadService#getFinancialBillNoByBillId(java.lang.Long)",
    "path": "jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java",
    "range": {"startLine": 442, "endLine": 444},
    "formalParameters": [{"ordinal": 0, "name": "billId", "type": "java.lang.Long"}],
    "snippet": "    public List<AccountHead> getFinancialBillNoByBillId(Long billId) {\n        return accountHeadMapperEx.getFinancialBillNoByBillId(billId);\n    }"
  },
  "outgoingCalls": [
    {
      "site": {"line": 443, "text": "accountHeadMapperEx.getFinancialBillNoByBillId(billId)"},
      "actualArguments": ["billId"],
      "targetFormals": [{"ordinal": 0, "name": "billId", "type": "java.lang.Long"}],
      "resolution": "UNIQUE",
      "target": "com.jsh.erp.datasource.mappers.AccountHeadMapperEx#getFinancialBillNoByBillId(java.lang.Long)",
      "disposition": "BOUNDARY_STOP",
      "reason": "MAPPER_INTERFACE"
    }
  ]
}
```

## 4. Agent 顺序与粗略检查点

时间只帮助尽快止损，不是承诺的工具性能：无阻塞时应在授权安装后约 60–90 分钟看到第一条有用导航，含一次调试上限总计约 3–5 小时，而不是按天研究。

| 顺序 | Agent/model | 交付与检查点 | 粗略时间 |
| --- | --- | --- | --- |
| 1 | Sol/ultra 或 Astra/ultra 设计者 | 核对官方 milestone、SHA-256、Java 兼容范围、LSP4J 与离线执行防护；提出精确安装授权 | 20–30 分钟 |
| 2 | Luna/xhigh 检查者 | 只写简单 oracle/checker；保证 checker 与 driver 分离，预期来源取固定 Git object | 20–30 分钟 |
| 3 | Terra/xhigh harness Agent | 写最小 materializer/driver，启动一次 JDT session，先跑注册再跑财务入口 | 90–150 分钟 |
| 4 | Sol/xhigh debugger（仅失败触发） | 针对一个已命名的 release/runtime/source-root/trusted-JAR 配置问题修正一次 | 最多 30 分钟 |
| 5 | Sol/ultra 或 Astra/ultra reviewer | 对冻结 packets 和 oracle 结果给 `PASS|LIMITED|FAIL` 及采用建议 | 20–30 分钟 |

任一检查点遇到阻塞，先把输入身份、请求日志、诊断和下一步写入 `results/verdict/` 后停止；不循环重装、换 client、扩大 fixture 或重写 resolver。

## Task 1：核定并获批工具，不启动索引

**Consumes:** [JDT LS 官方 README](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/README.md)、[官方 milestone 目录](https://download.eclipse.org/jdtls/milestones/)、[Preferences.java](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/preferences/Preferences.java)。

**Produces:** `tool-manifest.json`，含精确 milestone、下载 URL、SHA-256、server executable、启动 JDK 路径/版本、client/版本、来源链接和授权状态。

- [ ] 查询官方 milestone 和 README，选择一个明确版本；验证其 checksum 与 Java 要求。GitHub `releases/latest` 没有可依赖的预构建 asset，不写“下载 GitHub latest”命令。
- [ ] 先只运行 `java -version` 与固定包的离线完整性检查；若选定 release 不正式支持当前 JDK 26，则请求安装兼容 JDK 21，不拿 JDK 17 启动。
- [ ] 从 JDT LS 官方构建使用的 LSP4J 版本中选择并固定相容版本；JavaParser/Jackson 固定复用已缓存 3.28.2/2.21.4。独立 POM 不继承或运行客户 POM，也不接入产品 reactor。
- [ ] 向用户申请一次窄授权：下载并校验 manifest 中的官方预构建 server、固定 LSP4J（以及确有必要的兼容 JDK），把所选 server 解压为 `.workspace/jdtls-source-navigation-feasibility/tools/selected/`，随后离线运行批准快照。授权前停止。

## Task 2：冻结全源码投影并建立独立 oracle

**Produces:** exact archive、投影 manifest、无构建文件的 `projection/`、互相隔离的 driver 与 checks。

- [ ] 先创建精确 trial output 目录，再用以下只读 Git 对象命令确认 commit 并导出 archive；不得 checkout clone：

  ```bash
  task_repo_root=/private/tmp/linguan-source-analysis-process-design
  task_trial_root=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility
  mkdir -p "$task_trial_root/input" "$task_trial_root/runtime/configuration" "$task_trial_root/runtime/data" "$task_trial_root/runtime/logs" "$task_trial_root/results/registration" "$task_trial_root/results/financial" "$task_trial_root/results/structure" "$task_trial_root/results/verdict"
  GIT_NO_LAZY_FETCH=1 git -C "$task_repo_root/backend-agents/sources/source-code/.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580" cat-file -e '8c30ce7861570458920175e200bb2a6442713580^{commit}'
  GIT_NO_LAZY_FETCH=1 git -C "$task_repo_root/backend-agents/sources/source-code/.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580" archive --format=tar --output="$task_trial_root/input/jshERP-8c30ce7861570458920175e200bb2a6442713580.tar" 8c30ce7861570458920175e200bb2a6442713580
  ```

- [ ] Terra 的 `NavigationProbe materialize` 合并全部 main Java source roots；用 exact `git ls-tree` 选集和 `git cat-file blob` bytes 核验原 path/blob、投影 path、SHA-256，拒绝 archive 缺项、替换、冲突和漂移。不得仅复制预期 Controller/Service。
- [ ] 用 `rg --files backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/projection` 检查无 Maven/Gradle/Eclipse/AP 配置；发现任一禁止文件即停止。
- [ ] Luna 的 oracle 只保存上述独立期望、来源行和业务审阅要点；driver 接口只接 `snapshotCommit + projectionRoot + entry file/range + safety limits`。运行前以直接检查证明 driver 不读取 `checks/`。
- [ ] 只构建/测试独立 research harness，不运行项目或客户 suite；授权依赖已入本地 cache 后使用：

  ```bash
  mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml -o -Dtest=NavigationProbeTest test
  mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml -o -DskipTests package
  java -jar backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/target/jdtls-source-navigation-feasibility.jar materialize --trial-root backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility --commit 8c30ce7861570458920175e200bb2a6442713580
  ```

## Task 3：单次离线初始化和索引

**Produces:** 一次 JDT session 日志、initialize capabilities、diagnostics 和可重复的 request JSONL；不产生产品 artifact。

- [ ] 在可强制禁网且只允许写 `runtime/`/`results/` 的执行环境中启动 manifest 的 server；若环境只能“希望它不联网”而不能禁止网络，直接 `FAIL`。wrapper 命令形状固定为：

  ```bash
  env -u CLIENT_PORT -u CLIENT_HOST backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/tools/selected/bin/jdtls -configuration backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/runtime/configuration -data backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/runtime/data
  ```

- [ ] 第一条协议请求就是下面的实际 nested `initialize`；禁用配置在任何 import/index 前进入 `initializationOptions.settings`。收到响应后发送 `initialized`，再以同一 settings 对象发送一次 `workspace/didChangeConfiguration`：

  ```json
  {
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "processId": null,
      "rootUri": "file:///private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/projection",
      "capabilities": {},
      "initializationOptions": {
        "settings": {
          "java": {
            "import": {
              "maven": {"enabled": false},
              "gradle": {"enabled": false, "annotationProcessing": {"enabled": false}}
            },
            "autobuild": {"enabled": false},
            "project": {"sourcePaths": ["src"], "referencedLibraries": []},
            "maven": {"downloadSources": false},
            "eclipse": {"downloadSources": false},
            "configuration": {"updateBuildConfiguration": "disabled"}
          }
        }
      }
    }
  }
  ```

- [ ] Client 把完整 `initialize`、`initialized`、`workspace/didChangeConfiguration` 及后续 textDocument/callHierarchy 请求原样保存到 JSONL；开始索引后不得偷偷改变上述 settings。
- [ ] 等待 JDT 的一次 workspace ready/index quiescence，最长 10 分钟；记录 diagnostics、子进程树和网络阻断状态。发现 Maven/Gradle/AP/customer app 启动尝试即终止，不能靠重试掩盖。
- [ ] Terra 只启动一个由下列命令拥有的 stdio server/client；Task 4 与 Task 5 是该进程内的顺序 phase，不增加 daemon、socket 或第二次 JDT index：

  ```bash
  java -jar backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/target/jdtls-source-navigation-feasibility.jar probe --cases registration,financial --trial-root backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility
  ```

## Task 4：先走注册入口

**Produces:** `results/registration/packet.json`、`requests.jsonl`、`diagnostics.json` 和 packet SHA-256。

- [ ] combined probe 的 registration phase 不接受 oracle/checks 作为导航参数；只接固定入口位置与通用 limits。

- [ ] `didOpen` Controller；用 `textDocument/documentSymbol` 锁定入口完整 range，并对该 range 做 syntax-only 调用点枚举。
- [ ] 对每个调用点发送 declaration/definition/implementation；对入口本身发送 prepareCallHierarchy/outgoingCalls 作旁证。先检查 363/364/365 token，但不提供目标路径。
- [ ] 对唯一和 repository-local candidate target 都依通用规则回取完整正文并继续有界 walk；保留 candidate membership，不选择 Spring runtime bean。每个已访问方法的每个 outgoing syntax call 都必须有处置；因上限未展开的候选也要记录。空 call hierarchy 不得减少分母。
- [ ] 先落盘并 hash registration packet，再由同一 orchestrator 调用隔离的 `PacketOracleCheck`；三项关键 Service 任一未获得完整 body、路径/range/snippet 不一致、或靠预期路径注入才得到结果，均不得通过 primary gate。primary 非 `PASS` 时有序 shutdown/exit 并停止，不进入 financial phase。
- [ ] 人工只凭 packet 判断是否能看出验证码配置/校验、登录名限制以及注册限制、默认值与持久化设置；不能打开 oracle 中的目标文件替 packet 补内容。

## Task 5：再走财务入口，必要时做一个微型结构诊断

**Produces:** `results/financial/packet.json`；仅在具名结构疑问存在时才产生 `results/structure/packet.json`。

- [ ] 只有 registration primary gate 通过，combined probe 才在同一 stdio server/client 与同一 data/index 内进入 financial phase；不得另起单 case 的 financial 进程。
- [ ] 在同一 JDT session/同一全源码 index 上用相同 driver 从财务 Controller 入口出发；核对 Controller try/catch/return、Service 三行完整 body、`billId` actual text 和 Mapper interface 边界处置。
- [ ] 若能安全、廉价复用已有 namespace+id 静态 XML 能力，则附加固定 XML 150–155 行；必须禁外部 DTD/entity/schema/network。否则写 `MAPPER_XML_NOT_ATTEMPTED`，不把 SQL 当 Service body gate。
- [ ] 只有两条真实 walk 暴露了无法区分的 interface 多实现、overload、inherited method 或 star import 问题时，才在独立 tiny unmanaged workspace 跑一个结构 fixture；jshERP 不重新索引，也不增加第三条真实入口。
- [ ] fixture 仅含中性 `Entry`、一个 interface、两个 implementation、一个 `String`/`int` overload、一个基类继承方法和一次 star import。期望多实现=`CANDIDATES`、精确 overload/继承=`UNIQUE`；不加型号、行业包名或大量 compiler case。

## Task 6：一次修正上限、结论和交接

**Produces:** `results/verdict/verdict.md`，状态只为 `PASS|LIMITED|FAIL`，附两份 packet 摘要、请求/环境身份、限制和下一建议。

- [ ] 若失败能精确归因于一个 release/runtime/source-root/可信本地 JAR 配置，Sol/xhigh 只修正该项一次，并保留 before/after；不改 walker 语义、不加手写目标、不增加网络或客户构建。
- [ ] Luna/xhigh 审核 combined probe 在每份 packet 落盘/hash 后产生的隔离 checker 结果；Sol/Astra reviewer 再读原 packet。checker 通过只证明样本合同，不能证明全仓、所有 Java 构造或业务报告完成。
- [ ] `PASS` 时建议“评审是否以 JDT LS 替换/增强 Step02/03 的底层定位”；不得直接接入生产。`LIMITED` 明确“可采用但需何种边界/补充”；`FAIL` 明确违反了哪条安全或导航假设。
- [ ] 更新 research README 与对应 progress，记录实际命令、版本、结果、未执行项和 exact next action；不提交 `.workspace` 产物，不合并 main，不自动启动替代工具。

## Resume

本计划编写完成后的**准确下一动作**是：由 Sol/ultra 或 Astra/ultra 执行 Task 1 的官方 release/JDK/client 核定，生成安装授权请求；在用户明确批准前不要下载或启动任何工具。

恢复时先确认分支、base 和 `git status --short`，再检查固定 clone 的 commit object 可读且 HEAD 仍不得作为输入。最终交接必须明确写“计划已完成；JDT LS 试验仍未运行”，直到 `results/verdict/verdict.md` 真正存在且经独立审阅。
