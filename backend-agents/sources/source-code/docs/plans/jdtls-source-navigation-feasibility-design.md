# JDT LS 入口驱动源码上下文可行性设计

> **目标：仅给一个 Controller 入口，自动展开仓库内可导航调用并组织完整方法正文，保留实参、形参、条件和返回；在外部库、无源码、歧义、回调/异步或反射边界明确停下。**

本设计取代“固定空 client capabilities 是否一次跑通”的试验口径，但不改写[历史报告](../../research/jdtls-source-navigation-feasibility/REPORT.md)的原始测量。它只设计一次复用现有 research harness 的后续试验，不设计生产接入。

## 1. 要回答的三个问题

1. **JDT 实际返回什么？** 保存成对的真实请求/响应，展示 `DocumentSymbol`、`Location` 或 `LocationLink`。JDT 不直接返回完整调用图、实参、业务文字或整包源码。
2. **比现有 JavaParser 路径改善什么？** 比较的是现有自研 resolver + graph/material assembly，不是 JavaParser 工具本身的能力。旧实现只登记 explicit imports，遇到 `com.jsh.erp.service.*` 时会把 `UserService` 推到错误包，自动材料因此只有 Controller。新试验让 JDT 负责声明绑定，JavaParser 只在 JDT 返回位置附近做语法切片。
3. **为什么有助于业务解释？** 只有新 packet 实际含 Service/helper 正文后，读者或后续 LLM 才能依据其中条件、异常、默认值和持久化调用写出更具体、仍受源码约束的业务说明。本试验不调用 LLM，只写一段“这些实测代码可支持怎样表述”的人工示例。

历史自动材料是 [business-materials.jsonl](../../.workspace/fixed-material-plan-neutral-owners-v6/stores/runs/analysis-run--76cef36fb4437a0ad4f8811f2e151343bf7e0e68ed3e7f36a64c840b82083abb/steps/06-flow-interpretation/modules/10-business-material-builder/business-materials.jsonl)（SHA-256 `5105f2a2…6912`）第 151 条：`S731`–`S739` 全来自 `UserController.java`，没有 `UserService.java`。验证码、登录名、默认用户、保存、租户和角色细节来自之后的人工源码检查，只能作为 packet 落盘后的独立完整性参照；它们不是旧材料内容、navigator 输入或遍历规则。

## 2. 当前测量应如何解释

旧试验以 `capabilities={}` 初始化。JDT 走 flat `SymbolInformation`，其中 `location.range` 是 `NAME_RANGE`；walker 把它误当完整方法范围，入口即 `FULL_BODY_NOT_FOUND`。当时没有发送 definition/declaration/implementation，请求日志也没有保存响应正文。

因此旧结果只证明**该 client 协商与正文回取合同不成立**，不能证明 JDT 无法导航。后续结论必须分开：

| 结论 | 含义 |
| --- | --- |
| `PASS` | 单个真实入口自动形成有用且闭合的静态源码 packet；两案例都 PASS 才完成预定验证，一例通过只能写“部分可行” |
| `INCOMPLETE` | 有源码材料，但关键仓库内实现缺失、候选被丢弃或资源上限截断 |
| `LIMITATION` | 在正确协商、真实 definition 请求和健全 harness 下，JDT 对具名构造仍稳定不能定位 |
| `INCONCLUSIVE` | client/protocol/harness、权限、安全环境、索引或输入身份有故障，尚未有效测量工具能力 |

可诊断 client 故障允许修复后重新测量；每次 attempt 都保留。30–45 分钟仍无进展时写出具体诊断和下一决策点，不把时间耗尽伪装成 `LIMITATION`，也不设“一次配置修正后永久禁跑”的规则。

## 3. “完整静态源码上下文”

它是入口根的**静态可读材料**，不是实际运行时所有路径的证明：

- 从已知 frozen entry file/range 直接解析并保存 Controller 完整方法；入口正文不以 `documentSymbol` 成败为门。
- 对每个已展开方法，保存 annotations/signature、formal parameters、完整正文和精确 file/range。
- 枚举正文中选定的 method call、constructor call、显式 `this/super` constructor call 和 method reference；每个 call site 都有结果：已展开、全部候选、外部/无源码边界、未解析、循环或资源停止。
- 保存调用原文和 actual arguments；目标可定位时并列 formals。普通定长参数才按 ordinal 配对；varargs 显示 fixed 部分与 vararg bucket，constructor 同样不伪造一一对应。
- 完整正文天然保留 lexical `if/switch/try/catch/return/throw` 上下文；不为试验重建 CFG、数据流或五张图。
- 仓库内已知 Service/helper 未取得时不能 `PASS`。仓库 receiver 因依赖缺失而 unresolved 也不能改名成合法 external boundary。
- 外部 JAR/JDK、只有声明而无源码实现的真实接口边界可以停止。歧义响应保留全部候选位置/签名，不任选一个；回调、异步调度、method reference 执行、反射目标明确标为未展开。
- `maxMethods`、深度或时间导致截断时，受影响案例为 `INCOMPLETE`；cycle stop 只有在同一方法正文已经保存时才闭合。

## 4. 最小数据流

```text
known Controller file/range
  -> JavaParser syntax AST: exact entry body + lexical call-site denominator
  -> call sites in lexical order: real textDocument/definition exchanges
  -> JDT Location/LocationLink positions
  -> hierarchical DocumentSymbol + syntax AST around returned position
  -> exact frozen body, formals, calls, conditions and returns
  -> breadth-first repository expansion or explicit boundary
  -> packet persisted + hashed
  -> independent oracle/checker reads packet
  -> side-by-side research report
```

JavaParser never chooses a target name, package, overload or implementation. It only finds syntax nodes and exact callable ranges in a file already named by the entry or JDT response. `prepareCallHierarchy`/`outgoingCalls` may be supplementary evidence but cannot replace per-site definition results or supply actual arguments.

## 5. 协议与正文回取

Client 初始化必须真实声明所需 LSP 能力，而不是继续发送空对象；字段来自 [LSP 3.17 document symbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol) 与 [go to definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) 合同：

```json
{
  "capabilities": {
    "textDocument": {
      "documentSymbol": {"hierarchicalDocumentSymbolSupport": true},
      "declaration": {"linkSupport": true},
      "definition": {"linkSupport": true},
      "implementation": {"linkSupport": true},
      "callHierarchy": {"dynamicRegistration": false}
    }
  }
}
```

`textDocument/documentSymbol` 必须同时接受 hierarchical `DocumentSymbol[]` 和意外的 flat `SymbolInformation[]`；definition/declaration/implementation 必须接受 `Location`、`Location[]`、`LocationLink[]` 或 `null`。每次 request 在发送前记录完整 params，完成后在同一 exchange 记录未裁剪 result/error，形成可配对、可 hash 的 raw client-observed JSON；notification 另记。

目标正文回取顺序：先用 `LocationLink.targetSelectionRange`，否则用 `targetRange`/`Location.range` 的起点作 anchor；在 hierarchical symbols 中以 `selectionRange` 匹配对应 callable，并保留其 `range`；再从 frozen UTF-8 文件的 JavaParser AST 选择包含 anchor 的最小 method/constructor，使用 AST 精确行列切完整正文。两者不一致写 diagnostic，但不能因为 JDT 只给 name range 再次卡死。LSP 保留 0-based UTF-16、end-exclusive 原值；packet 同时给 1-based source range，不能丢 column。

## 6. 遍历与边界

入口解析后，在通用递归前按同一 lexical call-site 列表的源码顺序发出真实 `textDocument/definition`，外部调用也保留响应；报告展示其中第一个实际返回仓库目标的 exchange。选择与顺序不得引用 oracle、预期 Service 名或路径。随后 breadth-first 按 source position 处理，避免深层 utility 抢完配额而漏掉同级 Service。

每个 call record 至少含 caller、kind、source range、text、actuals、原始 exchange refs、resolution 和 disposition；每个 candidate 含来源请求、URI/range、repository/external 分类、可得 signature/formals/body。实现时必须修复当前 `calls.add(callNode)` 遗漏，否则所有调用仍会在 packet 中消失。

边界规则：

- 唯一仓库 callable：保存全文并入队。
- 多个仓库 callable：全部保留并切出每个候选的完整正文，分别带 candidate 标记进入 breadth-first 队列；不宣称唯一运行分派。只有候选没有源码时才在该候选处停止。
- interface/abstract target：再问 implementation；无可追源码则保存声明并明确停止。
- JAR/JDK URI 或确定不在投影：保留原 target 为 `EXTERNAL_LIBRARY`，不能像当前 `locations()` 那样过滤后变成 unresolved。
- repository receiver 却没有 target：`REPOSITORY_TARGET_UNRESOLVED`，案例不完整。
- callback/async/reflection：保存可见注册/调用正文，标动态目标未展开。

## 7. 两个真实案例

注册和财务共享同一个已验证 projection、JDT data/index 与 session，但结果互相独立。只要 session 健康，即使注册 unresolved，也继续财务，最后分别保存。每例各自给 `PASS|INCOMPLETE|LIMITATION|INCONCLUSIVE`；两例都 `PASS` 才写“预定验证完成”，一例 `PASS` 则只写“部分可行”：

- `results/registration/packet.json`：从 `POST /user/registerUser` 自动遍历所有可导航仓库调用，不只人工已知的三个方法。
- `results/financial/packet.json`：从 `GET /accountHead/getFinancialBillNoByBillId` 到 Service，并在 Mapper interface/可选安全 XML 处明确停止。

Oracle/checker 只在 packet 原子落盘并 hash 后读取。它验证 frozen identity、入口、方法体完整度、核心仓库实现未遗漏、call-site outcome closure 和 actual/formal 诚实性；人工已知业务关键词只用于之后报告说明“实测正文支持什么”，不进入 navigator，也不作为算法的隐藏 target list。

## 8. 报告就是验收面

新的正式报告开头依次回答三个问题，而不是先铺工具 ID：

1. 展示一组实际 raw definition request/response，并紧接程序据其切出的完整方法正文，分栏说明 JDT 返回与 harness 补充。
2. 用实际旧 registration material 对实际新 packet 做 before/after：旧为 Controller-only；新结果只能展示本次 packet 真正取得的 Service/helper。
3. 根据新 packet 中实际代码写一段可供未来 LLM 使用的业务叙述，并逐条连到正文；若 packet 没有某细节，不得从人工 oracle 补写。

随后展示 registration 与 financial 两份实际 packet；某案例因基础故障未产出时必须写 `UNAVAILABLE`、attempt 路径和原因，不能用预期 JSON 代替。报告不生成九章、不运行模型、不声称运行时成功或全仓完备。

## 9. 复用、非目标与预估

复用现有 materializer、固定 JDT/JDK/LSP4J、isolated Buildship cache、projection、session、packet checker 和结果目录；只修 research harness 的协商、记录、body materialization、遍历与报告。不新增生产 Module、public Interface、Schema、第二工具或 customer build。

后续实现预计 **2–4 小时**，包含一轮有意义的协议/harness 修复和两案例测量。JDT 对缺失依赖、接口实现与 unmanaged workspace 的真实表现仍有不确定性，应作为测量结果报告，不能提前折入“必然通过”的时间承诺。

实现角色：Luna/xhigh 写直接 tests，Terra/xhigh 改 research code，Sol/xhigh 诊断真实失败，Sol/ultra 或 Astra/ultra 审设计与最终证据。生成式产品内容清单为 **none**。
