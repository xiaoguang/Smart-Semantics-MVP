# JDT LS 源码导航可行性报告

> **后续解释（2026-09-12）：** 本文完整保留空 capabilities 试验的历史测量、正文与 hashes。由于该会话在入口正文回取处停止，既未发送 definition/declaration/implementation，也未保存 server response body，文中的 `FAIL/JDT_NAVIGATION_CAPABILITY` 只适用于当时规定的 client 配置与 body-range 合同，现不再作为 JDT 导航能力结论。后续目标和判定见[入口驱动设计](../../docs/plans/jdtls-source-navigation-feasibility-design.md)；在新实测完成前，工具对目标的结论为 `INCONCLUSIVE`。

> **最终结论：`FAIL`**
>
> **reasonClass：`JDT_NAVIGATION_CAPABILITY`**
>
> **reasonDetail：`DOCUMENT_SYMBOL_FULL_RANGE_UNAVAILABLE_UNDER_PRESCRIBED_EMPTY_CAPABILITIES`**

本报告是本次研究性试验唯一正式的人类可读交付物。机器交付物是试验实际写出的源码导航 packet、请求日志、diagnostics 和 verdict；它们不等同于业务报告、Proof、完整调用图或运行时执行证据。本次没有发起产品 LLM/业务内容生成，也没有生成九章报告。

## 1. 审阅结论

固定 jshERP 源码在无客户构建文件的 unmanaged workspace 中完成了投影；JDT LS 在隔离、禁网的修正后会话中到达 `ServiceReady`。但注册入口的 `textDocument/documentSymbol` 结果无法提供计划要求的完整方法体范围，walker 在入口处写出 `FULL_BODY_NOT_FOUND` 后停止：没有保存 Controller 方法正文，没有枚举调用点，也没有发出 declaration、definition、implementation 或 call-hierarchy 请求。

因此：

- H1 未通过：没有从 `UserController#registerUser` 到达三个真实 `UserService` 方法体。
- H2 未通过：实际 packet 没有方法、调用、实参/形参对应、条件、返回或副作用调用。
- 注册 primary gate 为 `FAIL`；财务 phase 为 `NOT_ENTERED`，不存在第二份 packet。
- 该结果不证明 JDT LS 的 declaration/definition/implementation 绑定本身错误，因为本次执行在发出这些请求前已经停止。
- 当前受约束组合不可采用到生产 Step 02/03/05。改变 capabilities 或正文回取方式属于新的试验设计，不在本次唯一修正额度内。

按照计划的停止条件，本报告完成后停止：不接入生产、不合并 main、不运行产品 LLM 业务样例、不生成九章、不自动试第二个工具。

## 2. 冻结输入与工具身份

| 项目 | 实际值 | 本地证据 |
| --- | --- | --- |
| jshERP 输入 | commit `8c30ce7861570458920175e200bb2a6442713580` | [projection manifest](../../.workspace/jdtls-source-navigation-feasibility/input/projection-manifest.json) |
| 固定源码 archive | SHA-256 `5e517b68d0365e00dd090211e999913dc85edf9de95983639f25f9162995be2d` | [archive](../../.workspace/jdtls-source-navigation-feasibility/input/jshERP-8c30ce7861570458920175e200bb2a6442713580.tar) |
| unmanaged 投影 | 273 个 `src/main/java` 文件；manifest SHA-256 `aff02e1b443ef777cdd2783ae1d1d5dcfe6e688229b48d3b531f204970c83c9e` | [projection manifest](../../.workspace/jdtls-source-navigation-feasibility/input/projection-manifest.json) |
| JDT LS | 官方 milestone `1.61.0`，bundle `1.61.0.202609031315`，source commit `08eafe6ff60c7159ef88571d47b6a9ef82fef94e` | [tool manifest](../../.workspace/jdtls-source-navigation-feasibility/tool-manifest.json) |
| JDT LS archive | SHA-256 `338e7e73d61836651ba2453919a0d34fa763eb4e7c03342092309bffb8934c64` | [downloaded archive](../../.workspace/jdtls-source-navigation-feasibility/tools/downloads/jdt-language-server-1.61.0-202609031315.tar.gz) |
| 启动环境 | Oracle JDK `26.0.1+8-34`；LSP4J/JSON-RPC `1.0.0`；Gson `2.14.0` | [research README](README.md) |
| 客户构建 | 未运行 Maven/Gradle/plugin/test/script/application；`referencedLibraries=[]` | [initialize request journal](../../.workspace/jdtls-source-navigation-feasibility/runtime/logs/requests.jsonl) |

投影中 `UserController.java` 的 Git blob 为 `aa46f148b32818078a93e051253d58006fde2695`，SHA-256 为 `afb1b350543031fa4b2f3c8a0570a75f738b471e6d6d54d982c74043e9f18c67`。修正会话的 `didOpen` 文本计算出相同 SHA-256，说明该请求发送的是批准 commit 的 Controller bytes，而不是 clone 当前 HEAD。

### tool manifest 的已知一致性缺陷

活动 [tool manifest](../../.workspace/jdtls-source-navigation-feasibility/tool-manifest.json) 的 SHA-256 为 `81c956b4c095103647e682f9a1d0856ab2224e2864214b6f4967b4deab792ac9`，其 `runtimeRecordedAt=2026-09-12T12:45:56.462739Z` 与修正会话的本地日志时间相符；但其 `lastTrial.forbiddenActivity` 仍保留早先 Buildship 失败会话的 `services.gradle.org` 文本。其 client `cacheState` 也仍写着 `NOT_PRESENT_APPROVED_TO_DOWNLOAD`，而两个固定 LSP4J 1.0.0 JAR 已存在于本地 Maven cache 并用于最终 harness。前一字段与活动 [verdict](../../.workspace/jdtls-source-navigation-feasibility/results/verdict/verdict.json) 的 `forbiddenBuildActivityDetected=false`、活动 diagnostics 和活动 runtime log 冲突；后一字段是未随安装更新的旧状态。

本报告不静默修正该 ignored manifest，也不把冲突字段当作修正会话事实。修正会话结论以活动 packet、verdict、diagnostics、request journal 和 runtime log 的一致交集为准；早先 Buildship 失败只从独立保留目录读取。

## 3. 一次允许的运行时修正

早先保留会话到达 `ServiceReady`，但 Buildship 在项目 import 设置生效前尝试访问 `services.gradle.org`，sandbox 返回 `UnknownHostException`。该运行违反“不得尝试网络/Gradle 活动”的 Task 3 门槛，现完整保存在 [failed-runs/task3-buildship-network-attempt-20260912T095556-0230](../../.workspace/jdtls-source-navigation-feasibility/failed-runs/task3-buildship-network-attempt-20260912T095556-0230/)；其 verdict SHA-256 为 `6fd54b70b044cc2d0c7e1e002c7b8738b2c39a3ff788644ec79bc7173f8d942a`。

唯一允许的修正没有改变 walker 语义：harness 从已校验 JDT LS core bundle 的 `gradle/checksums/versions.json` 写入隔离的 `XDG_CACHE_HOME/tooling/gradle/versions.json`。嵌入源与活动 cache 的 SHA-256 均为 `c0d581d312d4073916fd2aafe1fc8f297f8f1dac886179a21f70b0cd7fe57b83`。

修正后只执行了一次新的 combined registration-to-financial 会话。它到达 `ServiceReady`；活动日志、stderr、diagnostics 和 verdict 中没有早先的 `services.gradle.org`、`UnknownHostException` 或 Gradle-version-download 诊断。process-descendant 枚举仍因 sandbox 记录为 `PROCESS_TREE_UNAVAILABLE_SANDBOX`，所以这里不扩大成对所有子进程活动的完备证明。

## 4. 修正会话实际发生的协议交互

[全局 request journal](../../.workspace/jdtls-source-navigation-feasibility/runtime/logs/requests.jsonl) 的 SHA-256 为 `a295c417647f37655f8faabd76b7fa025203be724adb4f467c036b097db8800d`，依次记录：

| 顺序 | kind / method | 实际内容与结果边界 |
| --- | --- | --- |
| 1 | request `initialize` | `rootUri` 指向固定投影；`capabilities={}`；Maven/Gradle import、annotation processing、autobuild、source downloads 和 build-configuration update 均禁用；`sourcePaths=["src"]`、`referencedLibraries=[]`。 |
| 2 | notification `initialized` | 已发送。 |
| 3 | notification `workspace/didChangeConfiguration` | 重发同一 settings。 |
| 4 | notification `textDocument/didOpen` | 发送 `UserController.java` 全文，UTF-8 23,216 bytes。 |
| 5 | request `textDocument/documentSymbol` | 请求已由 client 等待完成，随后 journal 才写入该条；packet 随后得到 `FULL_BODY_NOT_FOUND`。 |

必须明确：现有 journal 只保存 `kind`、`method` 和请求/通知参数，**不保存 server response body**。本地没有可供逐字引用的 `initialize` 或 `documentSymbol` 原始响应；diagnostics 保存的是 server 的状态/诊断通知，不是上述 request 的响应。因此，本报告不声称存在一份已归档的 flat `SymbolInformation` JSON。

可以确认 client 收到了不抛异常的 `documentSymbol` 返回，否则该请求不会在 `JdtSession.request` 返回后入 journal，也不会继续写出 packet；但响应的逐字段值不可从现存 wire artifact 复原。后续没有发出以下请求：

- `textDocument/declaration`
- `textDocument/definition`
- `textDocument/implementation`
- `textDocument/prepareCallHierarchy`
- `callHierarchy/outgoingCalls`

## 5. 为什么拿不到完整方法体

原因由三类现存证据共同限定，而不是由缺失的 raw response 冒充证明：

1. request journal 证明初始化严格使用计划规定的 `capabilities={}`。
2. 活动 packet 证明 `documentSymbol` 后 walker 找不到覆盖 `UserController.java:357-367` 的范围。
3. 对固定 JDT LS core bundle 的只读 bytecode 审阅证明：
   - `ClientPreferences.isHierarchicalDocumentSymbolSupported()` 只有在 client 明确声明 hierarchical document-symbol support 时才返回 true；空 capabilities 返回 false。
   - `DocumentSymbolHandler.documentSymbol()` 在该分支调用 flat `getOutline()`，返回 `SymbolInformation`。
   - flat outline 通过 `JDTUtils.toLocation(IJavaElement)` 设置 `location`；该重载固定使用 `LocationType.NAME_RANGE`。

walker 已能读取 flat `location.range`，但 [TrialRunner.java](src/main/java/org/sourceanalysis/research/jdtls/TrialRunner.java) 的 `findBody` 合同要求某个 symbol range 完整包住入口的 357–367 行。方法名称范围不能覆盖 annotation、signature 和方法体，因此没有可切出的完整正文，立即写入 `FULL_BODY_NOT_FOUND` 并返回。

这就是本次精确的能力结论：**在规定的空 capabilities 与 `documentSymbol.range` 正文回取合同下，JDT LS 1.61.0 的 flat symbol 只提供名称范围，不能满足完整方法体采集。** 它不是依赖缺失、source-root 漂移或 Buildship 网络问题，也不是对尚未执行的 declaration/definition 绑定能力的否定。

## 6. 实际源码 packet 与两条入口结果

### 6.1 注册入口

活动 [registration packet](../../.workspace/jdtls-source-navigation-feasibility/results/registration/packet.json) 的 SHA-256 为：

```text
3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424
```

其实际有效负载是：

```json
{
  "methods": [],
  "calls": [],
  "diagnostics": [
    "FULL_BODY_NOT_FOUND:jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java:357:367"
  ],
  "packetVersion": "jdtls-source-navigation-feasibility-packet-v1",
  "snapshotCommit": "8c30ce7861570458920175e200bb2a6442713580",
  "entry": {
    "method": "POST",
    "path": "/user/registerUser",
    "file": "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
    "range": {"startLine": 357, "endLine": 367}
  },
  "limits": {"maxMethods": 30, "maxDepth": 4, "timeoutSeconds": 600}
}
```

实测流程只能标为：

```text
POST /user/registerUser
  -> didOpen 固定 UserController bytes
  -> textDocument/documentSymbol
  -> 未得到可覆盖 357–367 的完整正文范围
  -> FULL_BODY_NOT_FOUND
  -> STOP（methods=0, calls=0）
```

独立 [registration oracle](checks/registration-oracle.json) 原本用于在 packet 落盘和 hash 后检查以下完整正文；它不是 navigator 输入，也不是实测输出：

| 应由成功 packet 自行取得的内容 | 预期固定范围 | 本次实际状态 |
| --- | --- | --- |
| Controller `registerUser` 正文 | `UserController.java:357-367` | 未取得 |
| `validateCaptcha(String code, String uuid)` | `UserService.java:297-319` | 未请求、未取得 |
| `checkLoginName(UserEx userEx)` | `UserService.java:776-805` | 未请求、未取得 |
| `registerUser(UserEx ue, Integer manageRoleId, HttpServletRequest request)` | annotation `607`，正文 `608-661` | 未请求、未取得 |
| 三个调用点的 actual arguments 与目标 formal parameters 对应 | Controller `363-365` 到上述 Service signatures | 未枚举、未绑定 |
| `checkcode_flag`、验证码读取/删除/错误、重复登录名、默认管理账号限制、用户/租户/角色写入与默认值 | 三个完整 Service bodies | 未进入 packet |

因此本报告没有“完整关键方法摘录”可展示。把 oracle 或人工打开的固定源码正文复制成“JDT LS 采集结果”会伪造来源边界，故明确保留为 `NOT_RECOVERED`。

### 6.2 财务入口

计划要求 registration primary gate 通过后，才在同一 JDT session 进入 `GET /accountHead/getFinancialBillNoByBillId`。实际 registration gate 文件为 `FAIL`，所以财务 phase 为 `NOT_ENTERED`：

```text
GET /accountHead/getFinancialBillNoByBillId
  -> NOT_ENTERED（registration primary gate failed）
  -> 无 didOpen / documentSymbol / binding 请求
  -> 无 financial packet
```

因此没有取得 `AccountHeadController.java:181-196`、`AccountHeadService.java:442-444`、Mapper interface 边界或可选 XML 150–155。活动 `results/financial/` 目录为空，不存在可 hash 或评审的第二份 packet。

## 7. 与旧材料和旧报告的实际比较

协调者最初给出的 run ID `analysis-run--76cef9…` 是转录错误且路径不存在；确认后的实际历史材料是 [business-materials.jsonl](../../.workspace/fixed-material-plan-neutral-owners-v6/stores/runs/analysis-run--76cef36fb4437a0ad4f8811f2e151343bf7e0e68ed3e7f36a64c840b82083abb/steps/06-flow-interpretation/modules/10-business-material-builder/business-materials.jsonl)，SHA-256 `5105f2a2962477d3f051e45ccad651070697743d4811796cd9569e272ffc6912`。

该文件第 151 行的 `/user/registerUser` material 实际包含：

- `S731`：`UserController.java:357-367` 的完整 Controller 片段。
- `S732`–`S739`：同一 Controller 中的结果构造、取登录名、验证码实参和三个 `userService` 调用片段。
- 全部 source refs 都指向 `UserController.java`；没有 `UserService.java` 片段。
- `technicalProofRefs=[]`；没有 Service full body，也没有 actual/formal 参数配对。

历史 [Luna review response](../../.workspace/live-luna-automatic-user-account-group-v7-report-reader-language-round2-host-session/02-BUSINESS_REPORT_REVIEW-response.json) 的 SHA-256 为 `1a4c59350cb8a053cf8d035923336efe9a6088bc06338516eb6dfe12001e83fc`。它据此把以下内容诚实保留为未知或待确认：

- 被调用 Service 的内部规则和实际持久化效果；
- 登录名检查、验证码校验及失败响应的具体规则；
- 注册是否真正保存账户、重复注册如何处理、管理角色标识的含义。

修正后的 JDT packet 仍是 `methods=[]`、`calls=[]`，甚至没有把已有 Controller 片段写入 packet，因此它没有补回任何 Service 正文，没有提供 actual/formal 对应，也没有关闭旧报告的任何上述待确认项。不能把本次试验描述为“改善了旧源码包”或“改善了旧业务报告”。

## 8. 成功 packet 原本能支持什么（预期用途，不是实测模型输出）

如果未来另行批准的试验能由工具自行采集并校验完整 packet，它可以把 Controller 的 actual arguments 与真实 Service formal parameters 并列，并提供完整 Service 条件、异常、return 与副作用调用原文。这样 Step 05 或人工读者才有材料判断源码中明确写出的验证码开关/错误分支、登录名重复限制、注册默认值与持久化调用，并把无法越过的 Mapper/外部边界标为边界或 gap。

这只说明完整源码 packet 的下游用途：

- 不等同于运行时实际调用路径；
- 不证明持久化在某次运行成功；
- 不自动形成业务语义、Proof、完整调用图或九章报告；
- 本次没有用 Luna 或其他模型验证上述用途。

## 9. 证据索引、限制与未执行项

### 活动修正会话

| 证据 | SHA-256 / 结果 |
| --- | --- |
| [registration packet](../../.workspace/jdtls-source-navigation-feasibility/results/registration/packet.json) | `3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424` |
| [registration request slice](../../.workspace/jdtls-source-navigation-feasibility/results/registration/requests.jsonl) | `6c1543a08b6c2b8d2c5dde974f9dd330e2883328c2d7552895922e5b6e927e7e` |
| [registration diagnostics](../../.workspace/jdtls-source-navigation-feasibility/results/registration/diagnostics.json) | `1d901181b371ef47213428bd3a73f58513570e6066ebf2e681539c70ded2af45` |
| [machine-readable verdict](../../.workspace/jdtls-source-navigation-feasibility/results/verdict/verdict.json) | `501c43145e35c6bf50c10b2a254d0b99e5018a7e1dc00939663df57a3cbda270` |
| shaded research JAR | `ace06783d07f4a791e416da18a926be1c1b236a6de0d31862a3cfe031fdd60eb`（Task 4 记录；Task 6 未重建） |

### 保留的修正前会话

| 证据 | SHA-256 / 结果 |
| --- | --- |
| [global requests](../../.workspace/jdtls-source-navigation-feasibility/failed-runs/task3-buildship-network-attempt-20260912T095556-0230/runtime/logs/requests.jsonl) | `3f3d9c3cb122c3ed4e07d65847f86a33d8b2c843021c03f7737b59c5e4018804` |
| [registration packet](../../.workspace/jdtls-source-navigation-feasibility/failed-runs/task3-buildship-network-attempt-20260912T095556-0230/results/registration/packet.json) | `3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424` |
| [verdict](../../.workspace/jdtls-source-navigation-feasibility/failed-runs/task3-buildship-network-attempt-20260912T095556-0230/results/verdict/verdict.json) | `6fd54b70b044cc2d0c7e1e002c7b8738b2c39a3ff788644ec79bc7173f8d942a` |
| [tool manifest](../../.workspace/jdtls-source-navigation-feasibility/failed-runs/task3-buildship-network-attempt-20260912T095556-0230/tool-manifest.json) | `ca8a48ff0dadedfb6d3e1bb2e344243a742cb20e87b43edb297e4f590d04ef3e` |

活动与保留会话恰好写出相同 registration packet bytes；这只说明正文范围失败结果相同，不抹去两次会话的环境差异。修正前 diagnostics 有 Buildship 网络尝试，修正后没有。

已知限制和未执行项：

- request journal 未归档 raw server responses；不能重放或逐字段核对 `documentSymbol` response。
- sandbox 未提供 process-tree 可见性；不能声称对子进程的完备审计。
- active tool manifest 的 `lastTrial.forbiddenActivity` 为陈旧字段，不能单独代表修正会话。
- active tool manifest 的 LSP4J `cacheState` 仍是安装前状态，不能单独代表最终依赖状态。
- packet 的实际 `timeoutSeconds=600`，而计划的 walker 文本上限写作 15 分钟；该差异没有导致入口处即时发生的正文范围失败，但属于必须保留的执行偏差。
- 原 Task 6 文本把 ignored `results/verdict/verdict.md` 写作产物；实际机器 verdict 是 `verdict.json`，用户确认的唯一正式人类文档是本文件 `REPORT.md`。
- 早先 [Tasks 2–5 report](../../../../../.superpowers/sdd/jdtls-source-navigation-feasibility-plan/tasks-2-5-report.md) 与 [harness progress](../../progress/jdtls-harness.md) 只描述修正前 Buildship 运行，并写着尚未执行修正会话；它们已被 [debug progress](../../progress/jdtls-debug.md) 与活动 artifacts 的后续实测状态取代，不能作为最终状态使用。
- declaration、definition、implementation、call hierarchy 均未执行。
- financial、Mapper interface、可选 Mapper XML、结构 fixture 均未执行。
- 未运行客户构建、客户测试、客户应用或 annotation processor。
- Task 6 只做只读证据复核，没有重跑 JDT、Maven test/package 或 materialization。
- 未改生产代码、八步设计、Schema、Fact/Proof、EntryContext 或 publication。
- 未调用产品模型，未生成新的业务样例或九章，未试其他分析工具。

## 10. 复核命令与停止点

产生本报告所依据的历史修正会话命令是：

```bash
java -jar backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/target/jdtls-source-navigation-feasibility.jar probe \
  --cases registration,financial \
  --trial-root backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility
```

该命令已经消耗修正后的唯一会话额度，**不得在本试验中重跑**。本次 reviewer 使用下列只读方式复核：

```bash
(
  cd backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/results/registration
  shasum -a 256 -c packet.sha256
)

jq -e '
  .snapshotCommit == "8c30ce7861570458920175e200bb2a6442713580"
  and (.methods | length) == 0
  and (.calls | length) == 0
' backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/results/registration/packet.json

jq -e '
  .status == "FAIL"
  and .reasonClass == "JDT_NAVIGATION_CAPABILITY"
  and .reasonDetail == "DOCUMENT_SYMBOL_FULL_RANGE_UNAVAILABLE_UNDER_PRESCRIBED_EMPTY_CAPABILITIES"
  and .financial.phase == "NOT_ENTERED"
' backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/results/verdict/verdict.json

git diff --check
git diff --cached --check
```

Task 4 已记录最终 harness test 为 14 tests、0 failures、0 errors，offline package 为 `BUILD SUCCESS`；Task 6 没有用一次新构建代替实际 packet 失败结论。

**采用建议：不采用当前受约束的 JDT LS 方案；保留研究 harness 与失败证据供评审。准确下一动作是停止并与用户共同审阅本报告和实际 packet。** 只有用户在评审后另行批准，才能设计新的 JDT capabilities/body-range 试验或讨论其他工具；本任务不自动继续。
