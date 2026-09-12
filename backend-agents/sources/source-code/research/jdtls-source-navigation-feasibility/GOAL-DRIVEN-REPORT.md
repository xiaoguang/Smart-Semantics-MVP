# JDT 目标驱动源码导航验证报告

> 结论：**两例均通过本次有限验证。** 只给出 Controller 入口位置时，JDT LS 可以定位到仓库内的 Service / Mapper 声明；薄客户端随即读取这些位置覆盖的方法完整正文，并把调用、实参、形参和未展开边界存进同一个材料包。
>
> 这证明的是“JDT 可以帮助我们把模型原先看不到的 Service 实现自动取出来”，不是“JDT 已经理解业务”，更不是“它能保证所有 Java 项目都得到完整运行时调用链”。

本报告是对旧 [REPORT.md](REPORT.md) 的**新目标驱动试验**，不覆盖旧试验。旧试验在 `documentSymbol` 的正文范围处停止，尚未发送 definition / implementation / call-hierarchy 请求，因此不能作为 JDT 导航能力的否定结论。

## 1. 本次到底验证了什么

输入只有固定源码 commit `8c30ce7861570458920175e200bb2a6442713580`、源码根目录和两个 Controller 方法的位置：

```text
POST /user/registerUser
  UserController.registerUser

GET /accountHead/getFinancialBillNoByBillId
  AccountHeadController.getFinancialBillNoByBillId
```

客户端没有收到任何预期的 Service 路径、方法名或业务答案。它的工作次序是：

```text
入口方法的位置
  → JDT prepareCallHierarchy / outgoingCalls
  → 对每个正文中的调用点请求 definition；接口或抽象声明再请求 implementation
  → 按 JDT 返回的源码位置切出完整方法正文
  → 对新得到的仓库内方法重复，直到外部、无源码、非方法位置或深度/数量上限
```

JavaParser 在这里仅做两件事：从已定位的方法正文列出**语法调用点与实参**，以及按 JDT 位置切出完整方法。它不解析类型、不猜测 Service 包名、不选择实现。

本次没有运行客户 Maven、Gradle、插件、测试或应用；没有调用业务模型；没有改生产流水线。

## 2. JDT 实际返回了什么

每一条真实 LSP 请求和响应均保存到本次运行目录。以注册入口为例：

1. `textDocument/prepareCallHierarchy` 返回 `UserController.registerUser(UserEx, HttpServletRequest)` 的文件 URI、方法范围和 selection range。
2. `callHierarchy/outgoingCalls` 返回两个 Service 目标：

```json
{
  "to": {
    "name": "validateCaptcha(String, String) : void",
    "detail": "com.jsh.erp.service.UserService",
    "uri": ".../service/UserService.java"
  },
  "fromRanges": [{"start": {"line": 362, "character": 8}}]
}
```

```json
{
  "to": {
    "name": "checkLoginName(UserEx) : void",
    "detail": "com.jsh.erp.service.UserService",
    "uri": ".../service/UserService.java"
  },
  "fromRanges": [{"start": {"line": 363, "character": 8}}]
}
```

3. JDT 的 outgoing-call 响应没有列出 `registerUser`。客户端没有把这当作“调用不存在”，而是对该**实际调用点的方法名位置**请求 `textDocument/definition`。JDT 返回的是标准 LSP4J `Either` 包装中的位置：

```json
{
  "left": [{
    "uri": ".../service/UserService.java",
    "range": {
      "start": {"line": 607, "character": 16},
      "end": {"line": 607, "character": 28}
    }
  }],
  "right": null
}
```

这正是 `UserService.registerUser` 的方法名。客户端按这个位置读取完整方法，因而没有把一种返回包装形式误判为 JDT 找不到实现。

原始交换记录：

- [注册入口的请求与响应](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/registration/raw-exchanges.jsonl)
- [财务入口的请求与响应](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/financial/raw-exchanges.jsonl)
- [全局请求与响应](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/raw-exchanges.jsonl)

## 3. 自动形成的连贯代码材料

完整机器材料在以下两个实际文件中；每个方法只存一次，但每个调用边都保留，因此循环和共享辅助方法不会被复制为大量正文。

- [注册 packet.json](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/registration/packet.json)（76 个已读取方法、228 个调用点）
- [财务 packet.json](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/financial/packet.json)（3 个已读取方法、5 个调用点）

每个 packet 的一个方法记录包含路径、完整 `snippet`、形参、词法调用与实参；每个调用边再记录 JDT 给出的一个或多个目标及其形参。例如注册 Controller 的真实材料是：

```json
{
  "id": "M01",
  "path": "jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java",
  "signature": "Object registerUser(UserEx, HttpServletRequest)",
  "formalParameters": ["UserEx ue", "HttpServletRequest request"],
  "calls": [
    {
      "text": "userService.validateCaptcha(ue.getCode(), ue.getUuid())",
      "actualArguments": ["ue.getCode()", "ue.getUuid()"]
    },
    {
      "text": "userService.checkLoginName(ue)",
      "actualArguments": ["ue"]
    },
    {
      "text": "userService.registerUser(ue, manageRoleId, request)",
      "actualArguments": ["ue", "manageRoleId", "request"]
    }
  ]
}
```

与之关联的目标记录具有实际形参和完整正文。例如：

```text
M01 userService.validateCaptcha(ue.getCode(), ue.getUuid())
  → M41 UserService.validateCaptcha(String code, String uuid)
  → 读取 checkcode_flag；开关为 1 时校验验证码；缺失、过期或不匹配会抛异常

M01 userService.checkLoginName(ue)
  → M71 UserService.checkLoginName(UserEx userEx)
  → 查询同名用户；新增时已有同名、或修改时名称属于其他用户，会抛异常

M01 userService.registerUser(ue, manageRoleId, request)
  → M81 UserService.registerUser(UserEx ue, Integer manageRoleId, HttpServletRequest request)
  → 禁止默认管理员登录名；设定初始属性与状态；调用 UserMapper、租户更新、角色关系与 TenantMapper
```

以上箭头是程序的**源码导航关系**，最后一行的中文只概述实际方法体可见的代码，不表示数据库事务在任何特定运行中一定提交成功。

<details>
<summary>注册 Service 的实际正文要点（完整正文在 packet 的 M41、M71、M81）</summary>

```java
// M41: UserService.validateCaptcha
PlatformConfig platformConfig = platformConfigService.getInfoByKey("checkcode_flag");
if(platformConfig!=null && "1".equals(platformConfig.getPlatformValue())) {
    // 读取、删除验证码；缺失、错误时抛出 BusinessRunTimeException
}

// M71: UserService.checkLoginName
list=this.getUserListByloginName(loginName);
if(list!=null&&list.size()>0) {
    // 新增或名称属于其他用户时抛出 BusinessRunTimeException
}

// M81: UserService.registerUser
if(BusinessConstants.DEFAULT_MANAGER.equals(ue.getLoginName())) { ... }
ue.setIsystem(BusinessConstants.USER_NOT_SYSTEM);
ue.setStatus(BusinessConstants.USER_STATUS_NORMAL);
userMapper.insertSelective(ue);
userService.updateUserTenant(user);
userBusinessService.insertUserBusiness(ubObj, null);
tenantMapper.insertSelective(tenant);
```

</details>

财务查询走的是同一程序，而不是案例专用规则：

```text
M01 AccountHeadController.getFinancialBillNoByBillId(Long billId, HttpServletRequest request)
  └─ accountHeadService.getFinancialBillNoByBillId(billId)
     → M11 AccountHeadService.getFinancialBillNoByBillId(Long billId)
        └─ accountHeadMapperEx.getFinancialBillNoByBillId(billId)
           → M21 AccountHeadMapperEx.getFinancialBillNoByBillId(@Param("billId") Long billId)
           → Mapper 接口边界：本实验按规则停止，不把代理实现或 SQL 执行效果编造出来
```

Controller 正文还明确：正常时将 `list` 放进响应并设 `code=200`；捕获异常时设 `code=500` 与“获取数据失败”。这些内容都在 packet 的 M01 正文中，而不是从方法名推断。

## 4. 相对旧材料究竟补回了什么

旧 [REPORT.md](REPORT.md) 记录的历史业务材料仅有 `UserController.java:357–367` 的 Controller 片段及三个 `userService` 调用；它没有任何 `UserService.java` 片段和实参/形参配对。因此模型最多能说“系统调用注册”，无法从材料知道验证码的开关、登录名冲突规则、默认属性或后续关系创建。

本次自动 material 增加如下真实内容：

| 旧材料能看到 | 本次 JDT 自动定位并读取 | 对后续业务解释的具体帮助 |
| --- | --- | --- |
| 调用 `validateCaptcha` | `UserService.validateCaptcha` 完整正文 | 可以说明验证码检查受配置开关约束，且哪些输入会阻止后续注册。 |
| 调用 `checkLoginName` | `UserService.checkLoginName` 完整正文 | 可以说明名称冲突的判断，而不只是“检查名称”。 |
| 调用 `registerUser` | `UserService.registerUser` 完整正文 | 可以看到默认状态、用户保存调用、租户更新、角色关系和租户创建相关代码。 |
| 调用财务查询 | Service 与 Mapper 接口声明 | 可以说明 `billId` 如何原样穿过 Controller 与 Service 到 Mapper 边界，以及正常/异常响应。 |

这不是把旧材料事后人工补全：packet 的三项 Service 路径来自 `outgoingCalls` 或 `definition` 的原始 JDT 响应。试验程序的入口配置中没有这些路径或方法名称。

## 5. 为什么这些内容足以改善业务解释，但还不等于业务结论

模型随后应当阅读的不是 76 个方法的无序转储，而是按上图截出的一个小型材料组：入口方法、三条直接业务调用、三段 Service 正文、关键辅助方法、Mapper 边界，以及每段的 `文件:行号` 短引用。

有了这组材料，模型有源码依据来解释如下**候选业务语义**：

> 用户注册先按照配置校验验证码，再避免使用已被其他用户占用的登录名；通过这些条件后，系统初始化用户状态，并调用持久化、租户和角色关系相关接口。

这段话比“调用注册方法”多出的每一项都能指回 M41、M71 或 M81 的方法正文。它仍应由模型标注不能从静态代码确定的地方，例如：实际岗位、一次请求是否最终提交成功、Mapper 代理对应的 SQL 细节、用户与租户在制度上的精确关系。

所以 JDT 的价值不是替模型写业务报告，而是让模型不再凭 Controller 方法名猜 Service 的业务意图。

## 6. 未找到、未展开和限制

注册 packet 的 228 个词法调用中，110 个标记为未解析或非方法位置；财务 packet 有 2 个未解析的日志/异常调用。这些并未被静默删除。主要原因包括：

- Java / 第三方库实现不在固定仓库源码内，例如日志、Fastjson、Servlet；
- 找到的是实体类或异常类，而不是一个可展开的方法正文；
- 本试验故意不运行客户构建，因此没有完整外部 classpath；
- Mapper 代理与 XML / SQL 映射不是普通 Java 实现，财务样例按此边界停止。

因此此次结论是 **“在两个验证入口上可行，且需以多候选、边界和未解析记录的方式接入”**，不是“JDT 自动得到完整真实运行调用图”。生产方案若采用 JDT，仍需在每个材料包中保留这些边界，并把 MyBatis XML 作为独立、可选的附加材料。

## 7. 采用建议

建议后续架构采用三项非常小的职责分离：

```text
JDT：定位仓库内定义/实现候选和调用层次
材料组织器：读取完整方法、保留实参/形参、去重、限制规模并标注边界
Luna：根据连贯源码解释业务活动和跨活动过程
```

不建议为了这一点继续增强 JavaParser 的类型解析规则，也不应让 Fact / Proof 是否闭合决定模型能否读到 Service 正文。五张图可保留作来源辅助；JDT 所得的完整方法组应成为业务阅读材料的主要来源。

## 8. 可复核性

| 项目 | 实际值 |
| --- | --- |
| JDT LS | 1.61.0 |
| 固定输入 | `8c30ce7861570458920175e200bb2a6442713580` |
| 输出运行目录 | `run-20260912T115800-0230` |
| 注册 packet SHA-256 | 见 [packet.sha256](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/registration/packet.sha256) |
| 财务 packet SHA-256 | 见 [packet.sha256](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/financial/packet.sha256) |
| 产品模型 / 客户构建 | 均未调用 |

研究程序、自动测试和这份报告没有接入生产，也没有合入 `main`。下一步应由我们共同审阅两份真实 packet：它们是否已经给模型足够的 Service 实现；若是，再单独设计最小的生产接入，而不是直接改现有流水线。
