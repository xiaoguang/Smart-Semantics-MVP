# GitHub Code Agent 总体设计

## 1. 设计结论、边界与贯穿样例

### 1.1 目标产物与事实等级

GitHub Code Agent 把一份身份明确、字节不可变的 Java 仓库快照，整理成一份未发布、可审阅、可追溯的九章 Markdown Candidate。它不是远程 Git 连接器，不发布文档，也不证明生产运行时与企业政策；Selection、冻结、package 和发布仍由下游显式流程负责。需要 GitHub 网络访问时，另一个显式授权的上游 Capture workflow 通过自己的 Capture Adapter 取得固定 revision 并交付 `FrozenRepositoryRequest`；M1–M8 分析 core 永远只消费该离线产物。

目标方案只有一条准入主线。程序先验证来源，再在声明的能力范围内理解代码、用完整 ProofPack 证明事实、编译流程并投影模型最小证据包；模型只为一条已证明的局部流程选择冻结 `BusinessTermRegistry` 中的 term key 和有限 claim key，无 eligible term 时程序使用 total `TechnicalDisplayRegistry`；程序随后准入 typed interpretation、组装仓库知识、用固定事实句模板规划九章、渲染并归档。任何阶段都不能用后面的流畅输出补救前面的事实失败。

文档中的陈述分三类，不能混写：

1. **程序保证**：冻结字节、受支持语法的解析结果、唯一绑定、逐原子 Proof、覆盖、章节处置和 Trace 闭包。
2. **受支持的模型解释**：例如选择 `TERM_RESERVATION_FLOW`，由冻结 registry 把它显示为“库存预留”；term key 必须对该 anchor eligible 且有允许范围内的 atom lineage，有限 claim key 必须通过 registry grounding，并经过程序 KEEP 或 NARROW。任意 open label/gloss 不计作 grounding，也不进入自动正文。
3. **静态代码不能证明的事项**：运行时隔离级别、外部重试、数据库触发器、部署配置和企业政策。它们必须留作 Gap，不能进入确定事实。

本文描述的是**目标设计**。当前 Java 代码仍是阶段 00 POC；现状只在附录的成熟度矩阵中陈述，不能从目标模块反推为已实现能力。

### 1.2 为什么程序做得比模型多

模型擅长在经过审阅的有限业务词表中为局部对象、活动和关系选择合适 term key，并可另交隔离的人工审阅措辞候选；它不擅长稳定完成编译器、哈希校验器、覆盖分析器和证明系统的职责。全仓一次性提示会把无关文件、重复实现、不支持构造和多个流程混在一个上下文里，也无法保证调用唯一绑定、SQL 映射正确、分支完整或每个声明都由所指源码支持。

因此程序承担来源冻结、能力识别、Java/config/XML/SQL 解析、符号与调用绑定、Fact/ProofPack/Gap、流程切分、模型 Evidence 最小化、有限业务术语/claim 准入、total technical fallback、知识归并、章节所有权、语义原子处置、Markdown 渲染、身份、Trace 和恢复。模型每次只得到一个最小 `EvidenceCapsule` 和它可选的有限 term/claim keys；完整 ProofDependency closure 留在程序侧。小 Capsule 限制了可引用词汇和事实集合，也让不同模型或不同轮次面对相同的局部材料，从结构上减少跨模型漂移。

### 1.3 SYNTHETIC 贯穿样例：库存预留

以下仓库是本文专用的 **SYNTHETIC / 合成 Java 17、Spring MVC、MyBatis 样例**。它不是当前 POC fixture，没有被构建、执行、扫描或提交给模型。下列行号是本设计固定的样例 locator；后文所有模块都使用同一组行号。

`pom.xml:1-16`：

```xml
1  <project>
2    <modelVersion>4.0.0</modelVersion>
3    <properties>
4      <maven.compiler.release>17</maven.compiler.release>
5    </properties>
6    <dependencies>
7      <dependency>
8        <groupId>org.springframework</groupId>
9        <artifactId>spring-webmvc</artifactId>
10     </dependency>
11     <dependency>
12       <groupId>org.mybatis</groupId>
13       <artifactId>mybatis</artifactId>
14     </dependency>
15   </dependencies>
16 </project>
```

`src/main/java/example/inventory/ReservationController.java:1-18`：

```java
1  package example.inventory;
2
3  import org.springframework.web.bind.annotation.PostMapping;
4  import org.springframework.web.bind.annotation.RequestBody;
5  import org.springframework.web.bind.annotation.RequestMapping;
6  import org.springframework.web.bind.annotation.RestController;
7
8  @RestController
9  @RequestMapping("/reservations")
10 final class ReservationController {
11   private final ReservationService service;
12   ReservationController(ReservationService service) { this.service = service; }
13   @PostMapping
14   ReservationReceipt reserve(@RequestBody ReservationRequest request) {
15     return service.reserve(request.sku(), request.quantity());
16   }
17 }
18 record ReservationRequest(String sku, int quantity) {}
```

`src/main/java/example/inventory/ReservationService.java:1-24`：

```java
1  package example.inventory;
2
3  import org.springframework.stereotype.Service;
4
5  @Service
6  final class ReservationService {
7    private final InventoryMapper mapper;
8    ReservationService(InventoryMapper mapper) { this.mapper = mapper; }
9    ReservationReceipt reserve(String sku, int quantity) {
10     if (quantity <= 0) throw new InvalidQuantity();
11     InventoryRow inventory = mapper.findBySku(sku);
12     int available = inventory.onHand() - inventory.reserved();
13     if (available < quantity) throw new InsufficientInventory();
14     int updateCount = mapper.addReservation(sku, quantity, inventory.version());
15     if (updateCount != 1) throw new ConcurrentInventoryChange();
16     return new ReservationReceipt(sku, quantity);
17   }
18 }
19 record InventoryRow(String sku, int onHand, int reserved, int version) {}
20 record ReservationReceipt(String sku, int quantity) {}
21 final class InvalidQuantity extends RuntimeException {}
22 final class InsufficientInventory extends RuntimeException {}
23 final class ConcurrentInventoryChange extends RuntimeException {}
24
```

`src/main/java/example/inventory/InventoryMapper.java:1-12`：

```java
1  package example.inventory;
2
3  import org.apache.ibatis.annotations.Mapper;
4  import org.apache.ibatis.annotations.Param;
5
6  @Mapper
7  interface InventoryMapper {
8    InventoryRow findBySku(@Param("sku") String sku);
9    int addReservation(@Param("sku") String sku,
10                      @Param("quantity") int quantity,
11                      @Param("version") int version);
12 }
```

`src/main/resources/mappers/InventoryMapper.xml:1-16`：

```xml
1  <?xml version="1.0" encoding="UTF-8" ?>
2  <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
3    "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
4  <mapper namespace="example.inventory.InventoryMapper">
5    <select id="findBySku" resultType="example.inventory.InventoryRow">
6      SELECT sku, on_hand AS onHand, reserved_qty AS reserved, version
7      FROM inventory
8      WHERE sku = #{sku}
9    </select>
10   <update id="addReservation">
11     UPDATE inventory
12     SET reserved_qty = reserved_qty + #{quantity}, version = version + 1
13     WHERE sku = #{sku} AND version = #{version}
14   </update>
15 </mapper>
16
```

`src/main/resources/application.yml:1`：

```yaml
1  mybatis.mapper-locations: classpath*:mappers/*.xml
```

在受支持的静态路径中，这个入口有四个终点：数量不合法异常、可用数量不足异常、更新记录数不等于一时的异常、预留成功。异常类名 `ConcurrentInventoryChange` 不能单独证明实际竞争发生、异常如何映射为对外响应或调用方收到什么。代码也没有给出预留过期/释放、多仓范围、单位换算、外部重试或商品不存在时的业务政策；后文只在版本化 Gap expectation 被触发且搜索范围可说明时，把这五项保留为待确认问题。

## 2. 垂直主线：从冻结源码到可审阅候选

下面的纵向文字流是唯一目标主线；箭头表示下游只能消费上游已经验证的产物。

```text
FrozenRepositoryRequest
        │
        ▼
M1  Snapshot freeze / verification
        │  VerifiedSnapshot
        ▼
M2  Repository understanding
        │  RepositoryModel + CapabilityReport
        ▼
M3  Proven fact extraction
        │  ProvenFactSet + Proof (canonical ProofPack) + GapLedger
        ▼
M4  Business-flow compilation and minimal evidence packaging
        │  FlowSlice[] + CoverageReport + EvidenceCapsule[modelEvidenceSpans]
        ▼
M5  Bounded per-flow interpretation and deterministic admission
        │  AdmittedFlowMeaning[] + interpretation gaps
        ▼
M6  Repository-level business knowledge assembly
        │  RepositoryBusinessModel
        ▼
M7  Deterministic nine-section planning
        │  NineSectionDocumentModel + atom disposition/coverage
        ▼
M8  Orchestration, rendering, trace, immutable archive and recovery
        │
        ├── document.md
        ├── canonical JSON / append-only JSONL sidecars
        └── generation, validation and recovery receipts
```

安全、资源上限和“不执行客户代码”是覆盖 M1–M8 的横切门禁，不是第九个顺序阶段：

- 所有仓库路径、源码、XML、SQL、配置、模型响应和归档都视为不可信输入；路径越界或无法执行安全策略时 fail closed。
- v0 只读字节和语法树，不运行客户 Maven/Gradle、插件、测试、脚本、应用、注解处理器、SQL 或 MyBatis runtime。
- 标准 MyBatis `DOCTYPE` 可以作为语法出现，但 external DTD、general/parameter entity、schema 和所有网络解析必须被禁用；无法确认开关生效即停止。
- 每个 Profile 固定文件数、总字节、单文件字节、AST 节点、递归深度、SQL 长度、Flow 数、Capsule 字节/token 和模型轮次预算；超限形成范围 Gap 或致命资源错误，绝不转为无界模型输入。
- 模型只接受 M4 生成的冻结 Capsule，只返回严格 Schema 数据；它没有客户代码执行权、任意 prompt 入口或隐式网络能力。

## 3. 八个深模块的端到端 walkthrough

### M1 Snapshot freeze / verification → `VerifiedSnapshot`

#### 为什么必须存在

后续任何 locator、Proof 或 Trace 都依赖“同一条路径仍然指向同一组字节”。如果来源仍是分支、工作树或可替换文件，最精确的代码解释也无法重验。M1 把远程来源获取与离线分析分开：它只验证显式交付的不可变快照，不负责 clone、pull 或刷新。

#### 精确输入、前置条件与样例片段

输入是 `FrozenRepositoryRequest`：来源身份、固定 revision、上游 Capture receipt 或等价离线来源证明、受信工作区内的快照根、声明文件表、版本化验证策略、能力 Profile 名称和资源预算。前置条件是来源捕获已经由另一个显式 workflow 完成，所有必需文件都被声明，调用者没有要求 M1 解析 live branch 或访问网络。

以下值仅用于解释 identity 形状。六个重复字符 SHA 和 `sizeBytes` 都是 **ILLUSTRATIVE_NOT_CALCULATED**，不是本任务计算出的摘要或字节数：

```json
{
  "exampleOnly": true,
  "files": [
    {"path": "pom.xml", "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "sizeBytes": 369},
    {"path": "src/main/java/example/inventory/InventoryMapper.java", "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "sizeBytes": 400},
    {"path": "src/main/java/example/inventory/ReservationController.java", "sha256": "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc", "sizeBytes": 675},
    {"path": "src/main/java/example/inventory/ReservationService.java", "sha256": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd", "sizeBytes": 937},
    {"path": "src/main/resources/application.yml", "sha256": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "sizeBytes": 59},
    {"path": "src/main/resources/mappers/InventoryMapper.xml", "sha256": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", "sizeBytes": 668}
  ],
  "identityValueMeaning": "ALL_SHA_AND_SIZE_VALUES_ARE_ILLUSTRATIVE_NOT_CALCULATED",
  "origin": {"repositoryUrl": "https://example.invalid/synthetic/inventory-reservation.git", "revision": "synthetic-revision-1"},
  "profile": "java17-springmvc-mybatis-static-v0",
  "verificationPolicy": "frozen-snapshot-v1"
}
```

#### 确定性算法

1. 按版本化策略解析并规范化路径；拒绝绝对声明路径、`.`/`..`、反斜杠混用、路径逃逸和策略禁止的 symlink。
2. 将文件表按规范相对路径排序，拒绝重复、大小不符、缺失、非普通文件、字符集违规和未声明的必需输入。
3. 流式读取原始字节并重算 SHA-256；locator 所需文本文件还必须按严格 UTF-8 解码并建立稳定行索引。
4. 把来源 identity、声明文件路径/摘要、验证策略版本和 Profile identity 纳入 canonical snapshot identity；算法版本变化产生新 identity。
5. 写出验证回执。任何失败都发生在解析器或模型之前。

#### 模型角色

无。模型不得选择文件、验证摘要、刷新来源或解释 identity。

#### 具体输出

```json
{
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "exampleOnly": true,
  "expectedFileCount": 6,
  "expectedFilesVerifiedIfExecuted": 6,
  "observedFilesVerified": null,
  "originRevision": "synthetic-revision-1",
  "snapshotId": "snapshot:illustrative-not-calculated",
  "verificationPolicy": "frozen-snapshot-v1"
}
```

#### 下游后置条件与能力要求

M2 只读 `VerifiedSnapshot` 暴露的文件句柄和行索引；它不能自行打开工作树、增加文件或改变路径解释。下游必须保留 snapshot identity，并在归档验证时能够重开相同字节。

#### 支持范围、失败与 Gap

M1 支持本地、显式声明、只读的普通 UTF-8 源文件和二进制摘要校验。远程 Git clone/fetch、凭据和 revision capture 属于上游 Capture Adapter，不是 M1 能力。来源缺失、摘要漂移、路径逃逸、身份不完整或安全策略无法执行是致命错误；局部文件超预算但可安全隔离时登记范围 Gap。绝不回退到 `main`、相似路径或上次缓存。

#### 验证与测试策略

用合成 fixture 覆盖稳定 canonicalization、文件/字节漂移、路径穿越、大小不符、重复路径、非法 UTF-8、symlink 各位置和预算边界；断言失败发生在任何解析/Provider Adapter 调用前。属性测试验证路径规范化和文件排序的幂等性。

#### 为什么这个组合足够深

接口只有“验证一个冻结请求”，实现却隐藏路径安全、摘要、字符集、行索引、identity 和预算。删除 M1 会让每个解析器、Trace 查询和恢复路径重复这些规则；集中后，调用者只学习 `VerifiedSnapshot`，获得高 leverage，所有来源完整性变化也保持 locality。

### M2 Repository understanding → `RepositoryModel + CapabilityReport`

#### 为什么必须存在

文件可读不等于程序理解其语义。M2 必须先声明它能理解哪些构造，再对 Java、配置、XML 和静态 SQL 建立统一仓库模型；这样未知动态行为不会被文件名或框架惯例冒充为确定绑定。

#### 精确输入、前置条件与样例片段

输入仅是 M1 的 `VerifiedSnapshot`、版本化 `CapabilityProfile` 和预算。前置条件是六个文件全部验证通过，Profile 精确声明 Java 17、Spring MVC 注解、构造器注入、直接方法调用、MyBatis interface/XML 绑定和静态 `SELECT/UPDATE` 为支持构造。

具体输入包括：`pom.xml:4,8-13` 的 Java/框架信号、`src/main/resources/application.yml:1` 的 mapper 路径、三个 Java 文件各自 line 1 的 package、Controller `8-15`、Service 的 Mapper receiver `7-8`/方法 `9-16`/record `19-20`、Mapper Java `6-11`，以及 Mapper XML `4-14`。正文为人阅读时可以在上下文明确后缩写成 “Service `14`”；所有存储、identity、Proof、ProofPack、Capsule、Trace 和 receipt 中的 machine locator 必须始终使用规范仓库相对路径与行段，禁止 basename locator。

#### 确定性算法

1. 从 Maven 声明检测 Java release 和依赖能力；依赖出现只启用候选解析器，不自动证明框架行为。
2. 解析配置并将 mapper location 解析到 M1 已声明文件，不能借配置读取快照外资源。
3. 为 Java 构建 AST、package/import/type/symbol 表；对注解、构造器注入、receiver 声明、record declaration/component/accessor、条件、算术表达式、throw、return 和直接调用建立精确 locator。未使用 import 的同 package type 也必须经 package identity 解析，不能按 simple name 猜测。
4. 合并类级与方法级 Spring MVC route，得到 `POST /reservations`；注解未知或表达式动态时不猜测。
5. 按 receiver 静态类型和方法参数唯一绑定三条跨层直接调用：Controller→Service、Service→Mapper `findBySku`、Service→Mapper `addReservation`。绑定 Service→Mapper 时必须经过 `src/main/java/example/inventory/ReservationService.java:7-8` 的 `InventoryMapper mapper` receiver declaration；record accessor 和构造器不进入“跨层直接调用”分母。
6. 从 `src/main/resources/application.yml:1` 解析 Mapper 文件集合，再按 XML namespace line 4 → Mapper Java package line 1/interface declaration line 6–11 → statement id 唯一绑定两个 interface 方法与两个 XML statement；这形成独立的 Mapper-statement binding 分母，不能与 Java 跨层 call 分母混算。
7. 对 SELECT line 5 的 `resultType="example.inventory.InventoryRow"` 做 FQN binding：解析显式 FQN，匹配 `src/main/java/example/inventory/ReservationService.java:1` 的 `package example.inventory` 与 line 19 的 `record InventoryRow` declaration，得到精确 Java type。simple name、相似文件名或同名 record 都不能替代这条 edge。
8. 在已闭合的 resultType/FQN edge 上解析静态 SQL 的表、投影 alias、赋值、参数和谓词，并把 `sku`、`on_hand AS onHand`、`reserved_qty AS reserved`、`version` 分别绑定到精确 type `example.inventory.InventoryRow` 的 `sku/onHand/reserved/version` components。这个 result-mapping binding 是后续可用量公式和 loaded-version predicate Proof 的依赖节点，不另造业务 Fact atom。
9. 继续把 Mapper line 8 的 `InventoryRow` 返回类型经同 package 规则绑到该 FQN，再通过已唯一绑定的 `findBySku` call 把它传到 Service line 11 的 `InventoryRow inventory` 局部变量。只有这条 return/local receiver edge 闭合后，line 14 的 `inventory.version()` 才能绑定到上述精确 record component，不得按 accessor 名称猜测。
10. 构建方法内控制流，识别三个顺序 guard 和四个可达终点；保留每条边的条件极性。
11. 对每个读取到但不支持、无法唯一绑定或超预算的构造登记 `CapabilityGap`，并计算明确分母的能力覆盖。

#### 模型角色

无。模型不能补调用图、选择歧义 binding、推断动态 SQL 或宣称运行结果。

#### 具体输出

```json
{
  "capabilityReport": {
    "profile": "java17-springmvc-mybatis-static-v0",
    "reachableSemanticSites": 13,
    "supportedSemanticSites": 13,
    "unsupportedReachableSites": []
  },
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "repositoryModel": {
    "callBindings": [
      {"from": "src/main/java/example/inventory/ReservationController.java:15", "status": "EXACT", "to": "src/main/java/example/inventory/ReservationService.java:9"},
      {"from": "src/main/java/example/inventory/ReservationService.java:11", "status": "EXACT", "to": "src/main/java/example/inventory/InventoryMapper.java:8"},
      {"from": "src/main/java/example/inventory/ReservationService.java:14", "status": "EXACT", "to": "src/main/java/example/inventory/InventoryMapper.java:9-11"}
    ],
    "entry": {"httpMethod": "POST", "locator": "src/main/java/example/inventory/ReservationController.java:8-15", "route": "/reservations"},
    "mapperDocumentBinding": {
      "configLocator": "src/main/resources/application.yml:1",
      "interfaceDeclarationLocator": "src/main/java/example/inventory/InventoryMapper.java:6-11",
      "interfacePackageLocator": "src/main/java/example/inventory/InventoryMapper.java:1",
      "namespaceLocator": "src/main/resources/mappers/InventoryMapper.xml:4",
      "resolvedInterfaceFqn": "example.inventory.InventoryMapper",
      "status": "EXACT"
    },
    "mapperStatementBindings": [
      {"method": "findBySku", "namespaceLocator": "src/main/resources/mappers/InventoryMapper.xml:4", "statementLocator": "src/main/resources/mappers/InventoryMapper.xml:5-9", "status": "EXACT"},
      {"method": "addReservation", "namespaceLocator": "src/main/resources/mappers/InventoryMapper.xml:4", "statementLocator": "src/main/resources/mappers/InventoryMapper.xml:10-14", "status": "EXACT"}
    ],
    "resultMappings": [
      {"recordComponent": "example.inventory.InventoryRow.sku", "recordComponentLocator": "src/main/java/example/inventory/ReservationService.java:19", "resultTypeBindingRef": "result-type:inventory-row", "selectExpression": "sku", "selectLocator": "src/main/resources/mappers/InventoryMapper.xml:6", "status": "EXACT"},
      {"recordComponent": "example.inventory.InventoryRow.onHand", "recordComponentLocator": "src/main/java/example/inventory/ReservationService.java:19", "resultTypeBindingRef": "result-type:inventory-row", "selectExpression": "on_hand AS onHand", "selectLocator": "src/main/resources/mappers/InventoryMapper.xml:6", "status": "EXACT"},
      {"recordComponent": "example.inventory.InventoryRow.reserved", "recordComponentLocator": "src/main/java/example/inventory/ReservationService.java:19", "resultTypeBindingRef": "result-type:inventory-row", "selectExpression": "reserved_qty AS reserved", "selectLocator": "src/main/resources/mappers/InventoryMapper.xml:6", "status": "EXACT"},
      {"recordComponent": "example.inventory.InventoryRow.version", "recordComponentLocator": "src/main/java/example/inventory/ReservationService.java:19", "resultTypeBindingRef": "result-type:inventory-row", "selectExpression": "version", "selectLocator": "src/main/resources/mappers/InventoryMapper.xml:6", "status": "EXACT"}
    ],
    "resultTypeBinding": {
      "boundMapperMethodLocator": "src/main/java/example/inventory/InventoryMapper.java:8",
      "boundServiceLocalLocator": "src/main/java/example/inventory/ReservationService.java:11",
      "javaPackageLocator": "src/main/java/example/inventory/ReservationService.java:1",
      "recordDeclarationLocator": "src/main/java/example/inventory/ReservationService.java:19",
      "resolvedJavaType": "example.inventory.InventoryRow",
      "resultTypeBindingId": "result-type:inventory-row",
      "resultTypeLocator": "src/main/resources/mappers/InventoryMapper.xml:5",
      "resultTypeValue": "example.inventory.InventoryRow",
      "status": "EXACT"
    },
    "terminalLocators": ["src/main/java/example/inventory/ReservationService.java:10", "src/main/java/example/inventory/ReservationService.java:13", "src/main/java/example/inventory/ReservationService.java:15", "src/main/java/example/inventory/ReservationService.java:16"]
  }
}
```

本 fixture 的 `InventoryRow` 与 `InventoryMapper` 都在 `example.inventory`，因此不存在 Java import node；精确绑定依赖各自 package declaration，而不是省略 type-resolution edge。若 type 来自显式 import、静态 import 或其他 package，M2 必须把对应 import locator/node 纳入 binding graph 和后续 ProofPack；CapabilityProfile 不支持的 wildcard/shadowing 情形不能猜测。

本例能力分母是预期的 `13/13`：route 合并、Controller→Service 调用、数量 guard、Service→查询 Mapper 调用、查询 statement binding/SQL、查询 result mapping、可用量表达式、库存不足 guard、Service→更新 Mapper 调用、更新 statement binding/赋值、更新谓词、updateCount guard 和成功 return。绑定必须分开报告：Java 跨层 direct call 预期 `3/3`，Mapper method→statement 预期 `2/2`；result mapping 是 Proof dependency，不能拿来抬高任一分母。

#### 下游后置条件与能力要求

M3 收到的每个 supported observation 都有唯一规则版本、规范仓库相对 locator 和明确 binding；任何歧义都只能以 Gap 形式出现。M3 必须从 `RepositoryModel` 构造 Proof，不能把 observation 直接当作 Fact；可用量公式必须先依赖 `XML resultType FQN → Java package + record declaration`，再依赖 `on_hand/reserved_qty` alias→精确 record component。版本谓词必须依赖 Mapper config/namespace/interface binding、`resultType FQN → Java package + InventoryRow declaration → SELECT version → InventoryRow.version → Mapper findBySku return → Service InventoryRow local → inventory.version() call argument → InventoryMapper receiver/method/@Param("version") → UPDATE statement/predicate`，任一 edge 都不能隐含。

#### 支持范围、失败与 Gap

v0 支持 Java 17 的所列直接构造、Spring MVC 同类固定字符串 route、MyBatis interface/XML 与静态 SQL。JPA、消息、调度、批处理、反射、AOP、SpEL、WebFlux、运行时注册和动态 SQL 均不支持；遇到它们时隔离受影响路径并登记 Gap。解析器配置缺失、XML 外部解析未被可靠禁用或本应唯一的 binding 多解且影响入口闭包时，停止受影响流程。

#### 验证与测试策略

对每类支持构造做正反 fixture：类/方法 route 合并、重载与同名歧义、构造器 receiver type、3 条 direct call、2 条 Mapper statement binding、配置定位、namespace/interface FQN、`resultType` FQN、四个 SELECT projection→精确 record component 映射、静态 SQL、CDATA、标准 MyBatis DOCTYPE、恶意 entity、条件极性和四终点 CFG。缺少 `onHand/reserved` alias 时可用量 Proof 必须失败；逐项删除/改名 application mapper location、XML namespace、Mapper Java package/interface、XML `resultType`、Service Java package、`InventoryRow` declaration/component、SELECT `version`、Mapper `findBySku` 返回类型、Service `InventoryRow inventory` 局部声明、`inventory.version()` 实参、Service Mapper receiver、Mapper `@Param("version")`、UPDATE statement binding 或 XML version predicate 时，A16 Proof 必须失败。另加同 simple name/different package、错误 FQN 和 import-shadowing fixture，拒绝 basename machine locator，直接断言 `RepositoryModel` 与 `CapabilityReport`，不调用后续模块。

#### 为什么这个组合足够深

Maven/配置/Java/XML/SQL 分开解析，却只通过一个 `understand(VerifiedSnapshot, CapabilityProfile)` seam 对外。跨文件 binding 和能力分母必须在同一个仓库视图里解决；拆成一串浅 parser 会迫使 M3 重建全局关联。集中后，新语法规则只在 M2 内变化，调用者稳定获得统一模型，兼具 leverage 与 locality。

### M3 Proven fact extraction → `ProvenFactSet + Proof + GapLedger`

#### 为什么必须存在

AST 节点和正确摘要仍不能证明一句复合声明。M3 把每个候选 Fact 拆成语义原子，并要求 Fact 自己引用的源码 span 与确定性规则逐原子闭合；这是“字节完整”与“声明成立”之间不可省略的门禁。

#### 精确输入、前置条件与样例片段

输入是 M2 的 `RepositoryModel`、`CapabilityReport`、M1 的只读源码 span，以及随请求冻结的版本化 `GapExpectationProfile`。前置条件是受支持 observation 有唯一 locator/binding，resultType/FQN、receiver、Mapper config/namespace/statement 等编译依赖已显式建图，受影响的 capability gap 已隔离；Gap profile 必须明确 expectation、技术 trigger、搜索范围规则、reason identity 和 reader question template key，不能让实现或模型临时发明领域问题。

例如 Service `12-15` 与 Mapper XML `5-14` 是业务语义 span；Service package `src/main/java/example/inventory/ReservationService.java:1`、Mapper receiver `7-8`、Mapper package/interface、application config 与 XML namespace 则是确定性 binding dependency。`available = inventory.onHand() - inventory.reserved()` 只有在 XML line 5 的 `resultType` 经 Java package/record declaration 精确绑定、且 `on_hand AS onHand`、`reserved_qty AS reserved` 映射到该精确 type 的 components 后才能闭合。

本例冻结的 Gap expectation 只包含五项：`LIFECYCLE_SYMMETRY`、`WAREHOUSE_KEY_SCOPE`、`UNIT_SEMANTICS`、`RETRY_HANDLING`、`MISSING_ROW_HANDLING`。每项由 Profile 定义，不由源码关键词自动扩展到新的政策主题。

#### 确定性算法

1. 按版本化 Fact 规则把 observation 规范化为候选 Fact，并先登记全部 candidate atoms；枚举 `kind`、`attributes`、`conditions`、`literal values` 和 `relationships`，不能只登记最终通过项。
2. 为每个原子构建 Proof DAG：`semantic source span → parsed node → symbol/SQL binding → normalized atom`；复合 Fact 必须引用所有语义节点和 binding 节点。
3. 从 DAG 求传递 `ProofDependencyClosure`。它包含证明成立所需但不适合作为模型阅读材料的 package/import、type/receiver declaration、配置、XML namespace、resultType/FQN 和 statement binding 节点；节点以 canonical locator + parsed-node identity 去重，依赖 edge 带版本化 rule ID，任何 edge 不得靠 prose 隐含。
4. 把一个 snapshot 中的 closure 节点、edge、Proof 与 Fact→Proof references 规范化成不可变 `ProofPack`；每个 admitted Fact/atom 引用 `proofPackId + proofId`。ProofPack 是完整确定性证明载荷，不是模型 Capsule，也不进入模型 prompt。
5. 重开 M1 字节，验证 ProofPack 中每个 locator、excerpt digest、parsed-node identity 和规则版本；Proof 不能只引用 Evidence ID。
6. 对每个 Fact 执行 semantic closure：每个声明原子恰有受支持 Proof，且其 `ProofDependencyClosure` 中每个必需节点/edge 都闭合。ProofPack 中依赖 span 可以重叠；它们按 node identity 去重，不受模型 excerpt 非重叠规则约束。
7. 每个 candidate atom 必须被处置为 `ADMITTED_WITH_PROOF` 或 `REJECTED_WITH_REASON`；保持 `candidate = admitted + rejected`。复合 Fact 有任一必需 atom 被拒绝时，Fact 不进入 `ProvenFactSet`，但失败 atom 与 reason 仍保留在 accounting 中。
8. 逐项运行 `GapExpectationProfile`：先验证技术 trigger，再按 `searchedScopeRuleId` 穷举受支持 AST/CFG/SQL site，记录 observation evidence 和零匹配/缺分支等 absence evidence。只输出“在该 searched scope 没有受支持证据”的 Gap，绝不输出企业政策不存在的负面事实。
9. 对相同规范 Fact 去重，对冲突 Fact 保留冲突并停止使用；模型置信或描述相似度不参与准入。Gap 的业务问句留给 M7 的版本化 template，只能在声明的术语槽使用冻结 BusinessTermRegistry 的已准入 term value，或在无 term 时使用 TechnicalDisplayRegistry 的唯一 fallback，不得改写 provenance。

#### 模型角色

无。模型自报概率、解释一致性或自然语言质量都不能成为 Proof。

#### 具体输出

本设计预期本例产生 8 个 Fact、20 个语义原子并全部闭合；这些仍是 walkthrough 预期，不是 scanner 执行结果：

| Fact | 原子 | 证明范围 |
| --- | --- | --- |
| `F01 HTTP_ENTRY` | `A01 POST`；`A02 /reservations`；`A03 ReservationRequest body`；`A04 Controller 调用 Service.reserve` | Controller `8-15` + route/call binding |
| `F02 QUANTITY_GUARD` | `A05 quantity <= 0`；`A06 InvalidQuantity` | Service `9-10` |
| `F03 INVENTORY_LOAD` | `A07 findBySku`；`A08 sku 等值谓词` | Service `11` + Mapper Java `8` + XML `5-9` |
| `F04 AVAILABLE_FORMULA` | `A09 available = onHand - reserved` | Service `12` + XML `5-8` alias + `InventoryRow` `19` + result-mapping binding |
| `F05 INSUFFICIENT_GUARD` | `A10 available < quantity`；`A11 InsufficientInventory` | Service `13` |
| `F06 OPTIMISTIC_UPDATE` | `A12 inventory 表`；`A13 reserved_qty 加 quantity`；`A14 version 加 1`；`A15 sku 谓词`；`A16 version 谓词` | Service `14` + Mapper Java `9-11` + XML `10-14` |
| `F07 UPDATE_COUNT_GUARD` | `A17 updateCount != 1`；`A18 ConcurrentInventoryChange` | Service `14-15` |
| `F08 SUCCESS_RESULT` | `A19 回执包含 sku`；`A20 回执包含 quantity` | Service `16,20` |

A16 的 loaded-version Proof 不是“SQL 恰好出现 version”这一跳。它先闭合 mapper document：application config → XML namespace → Mapper Java package/interface；再闭合返回类型：XML line 5 `resultType="example.inventory.InventoryRow"` → Service Java package line 1 → record declaration/component line 19；最后闭合值流：SELECT line 6 `version` → `InventoryRow.version` → Mapper line 8 的 `InventoryRow` 返回类型 → Service line 11 的 `InventoryRow inventory` 局部变量 → Service line 14 `inventory.version()` 调用实参，同时经 Service line 7–8 receiver 将 `addReservation` 调用唯一绑定到 Mapper method，并经 Mapper line 4 import 解析 `@Param("version")` 注解 → XML UPDATE statement line 10–14/predicate line 13。任一节点或 edge 缺失，A16 都必须 rejected-with-reason。

一个完整 ProofPack 中 A16 closure 的紧凑形状如下。这里每条 edge 都有显式两端；没有出现在 `dependencyNodes`/`dependencyEdges` 的编译关系不能被称为已证明：

```json
{
  "atomId": "A16",
  "dependencyEdges": [
    {"from": "N01", "ruleId": "CONFIG_RESOLVES_MAPPER_DOCUMENT_V1", "to": "N02"},
    {"from": "N02", "ruleId": "XML_NAMESPACE_TO_JAVA_PACKAGE_V1", "to": "N03"},
    {"from": "N03", "ruleId": "JAVA_PACKAGE_TO_INTERFACE_DECLARATION_V1", "to": "N04"},
    {"from": "N05", "ruleId": "RESULTTYPE_FQN_TO_JAVA_PACKAGE_V1", "to": "N06"},
    {"from": "N06", "ruleId": "JAVA_PACKAGE_TO_RECORD_DECLARATION_V1", "to": "N07"},
    {"from": "N04", "ruleId": "MAPPER_FIND_METHOD_TO_SELECT_STATEMENT_V1", "to": "N05"},
    {"from": "N05", "ruleId": "RESULTTYPE_APPLIES_TO_SELECT_V1", "to": "N08"},
    {"from": "N08", "ruleId": "SELECT_PROJECTION_TO_RECORD_COMPONENT_V1", "to": "N07"},
    {"from": "N05", "ruleId": "SELECT_RESULTTYPE_TO_BOUND_FIND_CALL_RESULT_V1", "to": "N15"},
    {"from": "N07", "ruleId": "RECORD_DECLARATION_TO_LOCAL_VARIABLE_TYPE_V1", "to": "N15"},
    {"from": "N15", "ruleId": "LOCAL_RECEIVER_TO_RECORD_ACCESSOR_V1", "to": "N09"},
    {"from": "N10", "ruleId": "SIMPLE_RECEIVER_USES_ENCLOSING_PACKAGE_V1", "to": "N06"},
    {"from": "N06", "ruleId": "SAME_PACKAGE_TYPE_LOOKUP_V1", "to": "N03"},
    {"from": "N10", "ruleId": "RECEIVER_DECLARATION_TO_CALL_SITE_V1", "to": "N13"},
    {"from": "N13", "ruleId": "CALL_SITE_TO_MAPPER_METHOD_V1", "to": "N04"},
    {"from": "N09", "ruleId": "ACCESSOR_EXPRESSION_TO_CALL_ARGUMENT_V1", "to": "N13"},
    {"from": "N13", "ruleId": "CALL_ARGUMENT_POSITION_TO_MAPPER_PARAM_V1", "to": "N11"},
    {"from": "N16", "ruleId": "IMPORT_RESOLVES_MYBATIS_PARAM_ANNOTATION_V1", "to": "N11"},
    {"from": "N04", "ruleId": "MAPPER_METHOD_TO_PARAM_V1", "to": "N11"},
    {"from": "N04", "ruleId": "MAPPER_METHOD_TO_XML_STATEMENT_V1", "to": "N12"},
    {"from": "N02", "ruleId": "NAMESPACE_STATEMENT_TO_UPDATE_V1", "to": "N12"},
    {"from": "N12", "ruleId": "UPDATE_STATEMENT_CONTAINS_PREDICATE_V1", "to": "N14"},
    {"from": "N11", "ruleId": "MYBATIS_PARAM_TO_SQL_PREDICATE_V1", "to": "N14"}
  ],
  "dependencyNodes": [
    {"locator": "src/main/resources/application.yml:1", "nodeId": "N01", "role": "MAPPER_LOCATION_CONFIG"},
    {"locator": "src/main/resources/mappers/InventoryMapper.xml:4", "nodeId": "N02", "role": "MAPPER_NAMESPACE"},
    {"locator": "src/main/java/example/inventory/InventoryMapper.java:1", "nodeId": "N03", "role": "MAPPER_INTERFACE_PACKAGE"},
    {"locator": "src/main/java/example/inventory/InventoryMapper.java:6-11", "nodeId": "N04", "role": "MAPPER_INTERFACE_METHOD"},
    {"locator": "src/main/resources/mappers/InventoryMapper.xml:5", "nodeId": "N05", "role": "RESULT_TYPE_FQN"},
    {"locator": "src/main/java/example/inventory/ReservationService.java:1", "nodeId": "N06", "role": "RESULT_TYPE_JAVA_PACKAGE"},
    {"locator": "src/main/java/example/inventory/ReservationService.java:19", "nodeId": "N07", "role": "RESULT_RECORD_COMPONENT_INVENTORY_ROW_VERSION"},
    {"locator": "src/main/resources/mappers/InventoryMapper.xml:6", "nodeId": "N08", "role": "SELECT_VERSION"},
    {"locator": "src/main/java/example/inventory/ReservationService.java:14", "nodeId": "N09", "role": "LOADED_VERSION_ACCESSOR"},
    {"locator": "src/main/java/example/inventory/ReservationService.java:7-8", "nodeId": "N10", "role": "MAPPER_RECEIVER_DECLARATION"},
    {"locator": "src/main/java/example/inventory/InventoryMapper.java:9-11", "nodeId": "N11", "role": "MAPPER_VERSION_PARAMETER"},
    {"locator": "src/main/resources/mappers/InventoryMapper.xml:10-14", "nodeId": "N12", "role": "UPDATE_STATEMENT"},
    {"locator": "src/main/java/example/inventory/ReservationService.java:14", "nodeId": "N13", "role": "MAPPER_ADD_RESERVATION_CALL_SITE"},
    {"locator": "src/main/resources/mappers/InventoryMapper.xml:13", "nodeId": "N14", "role": "UPDATE_VERSION_PREDICATE"},
    {"locator": "src/main/java/example/inventory/ReservationService.java:11", "nodeId": "N15", "role": "BOUND_INVENTORY_ROW_LOCAL_FROM_FIND_BY_SKU"},
    {"locator": "src/main/java/example/inventory/InventoryMapper.java:4", "nodeId": "N16", "role": "MYBATIS_PARAM_IMPORT"}
  ],
  "factId": "F06",
  "proofId": "proof:A16",
  "proofPackId": "proof-pack:reservation",
  "status": "CLOSED",
  "value": "selected version is passed as the update version predicate"
}
```

F06 的 canonical Fact record 只保存 `proofPackId=proof-pack:reservation` 与 A12–A16 各自 `proofId`；ProofPack 保存上述完整 dependency closure。A13 的 Proof 仍以 XML line 10–12 的静态赋值节点闭合，但不能借 A13 的语义 span 代替 A16 的 resultType、receiver 或 namespace edges。

Candidate accounting 与 admitted Proof closure 分开报告；以下都是本设计对合成 fixture 的**预期值，不是执行结果**：

```json
{
  "candidateAtomAccounting": {
    "admittedAtoms": 20,
    "candidateAtoms": 20,
    "rejectedAtomDispositions": [],
    "rejectedAtoms": 0
  },
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "factAtomProofClosure": {"admittedAtoms": 20, "closedProofs": 20}
}
```

`20/20` 只表示 admitted atoms 的 Proof closure 子门禁；`20 = 20 + 0` 的 candidate accounting 才保证本例没有失败 atom 消失。负向 fixture 必须出现非零 `rejectedAtoms` 和逐项 reason，不能通过缩小分母取得 100%。

`GapLedger` 同时记录 `GAP_EXPECTATION_PROFILE_V1` 产生的五个有确定 provenance 的非事实问题。每项的 absence evidence 都只约束自己的 searched scope：

- **G01 / `LIFECYCLE_SYMMETRY`** — `triggerRuleId = RESERVATION_LIFECYCLE_TRIGGER_V1`；searched scope 是六个冻结文件中全部受支持 Java methods 与 Mapper methods/statements；observation 是 `Reservation*`/`addReservation`；absence evidence 是没有匹配到受支持 expiry/release counterpart；`reasonCode = NO_SUPPORTED_LIFECYCLE_COUNTERPART_IN_SEARCHED_SCOPE`；`questionTemplateKey = ASK_RESERVATION_EXPIRY_RELEASE_POLICY`。
- **G02 / `WAREHOUSE_KEY_SCOPE`** — `triggerRuleId = INVENTORY_KEY_SCOPE_TRIGGER_V1`；searched scope 是入口参数、查询/更新参数和 SQL predicates；observation 是查询按 `sku`、更新按 `sku + version`；absence evidence 是没有匹配到 warehouse key/predicate；`reasonCode = NO_WAREHOUSE_KEY_IN_FLOW_KEY_SCOPE`；`questionTemplateKey = ASK_WAREHOUSE_SCOPE_POLICY`。
- **G03 / `UNIT_SEMANTICS`** — `triggerRuleId = QUANTITY_ARITHMETIC_TRIGGER_V1`；searched scope 是 request/record fields、算术节点与 Mapper 参数；observation 是 `int quantity` 直接比较/累加；absence evidence 是没有匹配到 unit field 或 conversion call；`reasonCode = NO_UNIT_SEMANTICS_IN_QUANTITY_SCOPE`；`questionTemplateKey = ASK_QUANTITY_UNIT_POLICY`。
- **G04 / `RETRY_HANDLING`** — `triggerRuleId = NON_SINGLE_UPDATE_TRIGGER_V1`；searched scope 是 `ReservationService.reserve` 的入口根 CFG；observation 是 `updateCount != 1` 后 throw；absence evidence 是没有匹配到 loop/catch/reinvoke；`reasonCode = NO_RETRY_HANDLING_IN_ENTRY_FLOW_SCOPE`；`questionTemplateKey = ASK_NON_SINGLE_UPDATE_RETRY_POLICY`。
- **G05 / `MISSING_ROW_HANDLING`** — `triggerRuleId = ROW_LOAD_DEREFERENCE_TRIGGER_V1`；searched scope 是 `findBySku` return 到首次 dereference 的 CFG；observation 是 `InventoryRow` 随后被 dereference；absence evidence 是没有匹配到 null/Optional/missing-row branch；`reasonCode = NO_MISSING_ROW_BRANCH_IN_LOAD_SCOPE`；`questionTemplateKey = ASK_MISSING_ROW_POLICY`。

一条 canonical Gap 形状如下：

```json
{
  "absenceEvidence": {"matchedRetryNodes": 0, "searchRuleId": "ENTRY_CFG_RETRY_SEARCH_V1"},
  "expectationId": "RETRY_HANDLING",
  "gapId": "G04",
  "observationEvidence": ["src/main/java/example/inventory/ReservationService.java:14-15"],
  "questionTemplateKey": "ASK_NON_SINGLE_UPDATE_RETRY_POLICY",
  "reasonCode": "NO_RETRY_HANDLING_IN_ENTRY_FLOW_SCOPE",
  "searchedScope": [{"rootLocator": "src/main/java/example/inventory/ReservationService.java:9-16", "scopeRuleId": "ENTRY_ROOTED_SUPPORTED_CFG_V1"}],
  "triggerRuleId": "NON_SINGLE_UPDATE_TRIGGER_V1"
}
```

#### 下游后置条件与能力要求

M4 只能消费闭合的 Fact、不可变 ProofPack 和显式 Gap。本例预期后置条件是 8 个 admitted Facts、20 个 candidate atoms、20 个 admitted/Proof-closed atoms、0 个 rejected atoms，以及 5 个带完整 provenance 的 Gap。五个政策问题没有被提升为事实。M4 必须保持 Fact→ProofPack references、完整 `ProofDependencyClosure` 和 rejected-atom accounting，不能为了形成完整故事借用未引用 span，也不能把面向模型的摘录当作 ProofPack。

#### 支持范围、失败与 Gap

支持 M2 能唯一表达的 route、直接调用、条件、算术、throw/return、Mapper binding 和静态 SQL 原子。不支持动态 dispatch、运行时数据、隐式事务结果或外部系统语义。缺一个原子就拒绝整个复合 Fact；若该 Fact 是形成安全终点所必需，受影响 Flow fatal。可隔离的未知行为成为 Gap。

#### 验证与测试策略

为每种 Fact kind 建正反 closure fixture；关键负例包括 locator 偏一行、复合 route 缺类级前缀、Mapper call 指向错误 statement、SELECT alias 与 record component 不匹配、SQL 谓词缺一项、Fact 引用另一个 Fact 的 Evidence 和 excerpt hash 正确但语义错误。A09/A16 mutation matrix 逐项删除 config、namespace、Java package/import（若存在）、receiver/type declaration、resultType、record component、projection、Mapper `findBySku` 返回类型、Service 局部变量类型、accessor/call-argument、Mapper line 4 `@Param` import、Mapper param/statement 和 predicate edge；任何缺失都使对应 Proof closure 失败。另为五个 Gap expectation 测 trigger 未触发、搜索范围不闭合、零匹配 provenance 和 question template identity。测试重算每个 Proof DAG/ProofPack canonical bytes，并断言任何 atom loss 进入 `REJECTED_WITH_REASON` 而不是缩小分母。

#### 为什么这个组合足够深

外部接口只要求“从仓库模型提取可证明事实”，内部却统一规范化、传递 ProofDependency closure、ProofPack canonicalization、冲突和 Gap。若删除 M3，Flow compiler、解释准入、章节计划和 Trace 都要重复解析 package/config/namespace/resultType 等 binding 并判断事实是否成立；集中门禁让所有下游引用同一完整证明结果，错误和规则更新保持 locality。

### M4 Business-flow compilation and minimal evidence packaging → `FlowSlice[] + CoverageReport + EvidenceCapsule[]`

#### 为什么必须存在

事实集合还不是业务流程。模型既不能可靠发现入口到终点的所有路径，也不应看到全仓。M4 把一个入口根业务过程编译成一条 `FlowSlice`，把不同结束方式保留为该 Flow 内嵌的 `OutcomePath[]`，再从已经完整验证的 ProofPack 投影一个更小的模型阅读包与覆盖分母。Outcome 是 Flow 的分支，不是四条彼此独立的业务流程；`ProofDependencyClosure` 和模型最小 Evidence 是两种不同产物，不能用一个 span list 同时冒充二者。

#### 精确输入、前置条件与样例片段

输入是 M3 的 8 个闭合 Fact、20 个 admitted/Proof-closed atoms、不可变 `proof-pack:reservation`、0 个 rejected atoms、5 个 Gap，以及 M2 的入口、CFG 和 binding。前置条件是入口 `POST /reservations` 唯一，三个 guard 的真假边可达，Mapper statement/result mapping 唯一，所有进入 Flow 的 Fact→Proof references 与完整 `ProofDependencyClosure` 已验证，candidate/rejected accounting 未丢失。

具体入口是 Controller `13-15`；分支依次是 Service `10`、`13`、`15`；成功终点是 `16`。完整 ProofPack 另含 application config、Java package/type/receiver、Mapper interface、XML namespace/resultType 等确定性 binding dependencies。模型不需要读取这些编译细节；它只需要 Controller `8-15,18`、Service `9-16,19-20`、Mapper XML `6-8,11-13` 的非重叠业务语义摘录，加上 A01–A20 的 normalized Fact views 与只读 Proof IDs。

#### 确定性算法

1. 从版本化入口规则发现一个入口，以入口和稳定的业务调用闭包建立 `flow:reservation`；沿受支持 CFG 传播条件极性、调用、Fact 和副作用。
2. 在 throw、return 或显式不可继续节点封闭 Outcome；本例枚举四个互斥 `OutcomePath`，共享前缀只在 Flow 图中保存一次。
3. 验证每个支持范围分支恰好进入一个 Outcome，且每个 Outcome 的条件、终点和所需 Fact/ProofPack closure 完整；循环/递归超过 Profile 上限则形成 Gap 或阻止完整 Flow。
4. 重新验证每个 Flow Fact 引用的 `proofPackId/proofId`，但不把 ProofPack 节点复制进 Capsule。ProofPack 的完整性门禁先于任何模型投影，失败即停止。
5. 计算入口、Outcome、Java 跨层直接调用、Mapper statement binding 和 Fact atom 的 typed 覆盖分母；不可达代码必须带稳定排除原因，不能从报告中静默消失。
6. 按 `MODEL_EVIDENCE_PROJECTION_V1` 生成 `EvidenceCapsule`：包含 Flow/Outcome identity、allowed normalized Fact/atom views、只读 Proof references、Gap provenance 和 `modelEvidenceSpans`。package/import/config/namespace/receiver/resultType 等只为编译绑定服务的节点留在 ProofPack，不因模型看不到而削弱 Proof。
7. 对 `modelEvidenceSpans` 做独立删除检查：每个 excerpt 必须是非重叠、面向业务语义的连续 span；删除任一 span 都会违反“每个 allowed atom/Outcome 至少有一个直接语义摘录或终点摘录”的 projection policy。这个最小性只针对模型表示完整 Flow，不声称这些 excerpt 是传递 Proof closure。
8. 若入口过程超预算，只允许在稳定直接调用 seam 拆成 parent/child FlowSlice：child 必须有可静态绑定的调用/返回合同、完整 Outcome closure，且没有未建模的分支状态跨 seam；parent 以稳定 flow reference 引用 child。不得仅因有多个终点而拆 Flow。无法在这些规则内安全拆分时，在模型调用前登记 capability Gap 或 fatal。
9. 冻结一个 Capsule 的 allowlist、所有 Outcome identity、modelEvidence locator/excerpt digest、ProofPack reference、Gap provenance、projection 规则版本、预算和单一 task identity；稳定排序输出。

#### 模型角色

无。模型不发现、合并、扩大或切分 Flow，也不选择 Evidence。

#### 具体输出

```json
{
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "coverageReport": {
    "discoveredEntriesCovered": {"expectedDenominator": 1, "expectedNumerator": 1, "observedDenominator": null, "observedNumerator": null},
    "exactCrossLayerCallBindings": {"expectedDenominator": 3, "expectedNumerator": 3, "observedDenominator": null, "observedNumerator": null},
    "exactMapperStatementBindings": {"expectedDenominator": 2, "expectedNumerator": 2, "observedDenominator": null, "observedNumerator": null},
    "supportedOutcomePathsCovered": {"expectedDenominator": 4, "expectedNumerator": 4, "observedDenominator": null, "observedNumerator": null}
  },
  "flowSlices": [{
    "entry": "POST /reservations",
    "flowSliceId": "flow:reservation",
    "outcomePaths": [
      {"conditions": ["quantity <= 0"], "outcomePathId": "outcome:invalid-quantity", "terminal": "throw InvalidQuantity"},
      {"conditions": ["quantity > 0", "available < quantity"], "outcomePathId": "outcome:insufficient", "terminal": "throw InsufficientInventory"},
      {"conditions": ["quantity > 0", "available >= quantity", "updateCount != 1"], "outcomePathId": "outcome:update-count-not-one", "terminal": "throw ConcurrentInventoryChange"},
      {"conditions": ["quantity > 0", "available >= quantity", "updateCount == 1"], "outcomePathId": "outcome:accepted", "terminal": "return ReservationReceipt"}
    ]
  }]
}
```

唯一 Capsule 覆盖共享前缀和四个 Outcome；它不是把 F01–F08 逐终点递增打包：

```json
{
  "allowedAtomIds": ["A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09", "A10", "A11", "A12", "A13", "A14", "A15", "A16", "A17", "A18", "A19", "A20"],
  "evidenceCapsuleId": "capsule:reservation-complete-process",
  "flowSliceId": "flow:reservation",
  "gapIds": ["G01", "G02", "G03", "G04", "G05"],
  "minimality": {"relativeTo": "MODEL_EVIDENCE_PROJECTION_V1_COMPLETE_ENTRY_WITH_ALL_SUPPORTED_OUTCOMES", "requiresDirectSemanticExcerptPerAllowedAtomOrOutcome": true, "status": "EXPECTED_DELETION_CHECK_NOT_EXECUTED"},
  "modelEvidenceSpans": ["src/main/java/example/inventory/ReservationController.java:8-15", "src/main/java/example/inventory/ReservationController.java:18", "src/main/java/example/inventory/ReservationService.java:9-16", "src/main/java/example/inventory/ReservationService.java:19-20", "src/main/resources/mappers/InventoryMapper.xml:6-8", "src/main/resources/mappers/InventoryMapper.xml:11-13"],
  "normalizedFacts": [
    {"atomIds": ["A01", "A02", "A03", "A04"], "factId": "F01", "proofIds": ["proof:A01", "proof:A02", "proof:A03", "proof:A04"]},
    {"atomIds": ["A05", "A06"], "factId": "F02", "proofIds": ["proof:A05", "proof:A06"]},
    {"atomIds": ["A07", "A08"], "factId": "F03", "proofIds": ["proof:A07", "proof:A08"]},
    {"atomIds": ["A09"], "factId": "F04", "proofIds": ["proof:A09"]},
    {"atomIds": ["A10", "A11"], "factId": "F05", "proofIds": ["proof:A10", "proof:A11"]},
    {"atomIds": ["A12", "A13", "A14", "A15", "A16"], "factId": "F06", "proofIds": ["proof:A12", "proof:A13", "proof:A14", "proof:A15", "proof:A16"]},
    {"atomIds": ["A17", "A18"], "factId": "F07", "proofIds": ["proof:A17", "proof:A18"]},
    {"atomIds": ["A19", "A20"], "factId": "F08", "proofIds": ["proof:A19", "proof:A20"]}
  ],
  "outcomePathIds": ["outcome:invalid-quantity", "outcome:insufficient", "outcome:update-count-not-one", "outcome:accepted"],
  "proofPackRef": "proof-pack:reservation",
  "projectionPolicy": "MODEL_EVIDENCE_PROJECTION_V1"
}
```

`modelEvidenceSpans` 在同一文件内不重叠，并且只呈现完整入口过程的业务语义。它有意排除 `src/main/resources/application.yml:1`、Java package/receiver、Mapper interface、XML namespace line 4、resultType line 5 和 statement tags；这些不是可省略的 Proof，而是已经位于 `proof-pack:reservation` 的完整 binding dependencies。模型只看 SELECT/UPDATE 的字段、表、赋值与谓词行；resultType/FQN Proof 始终留在 ProofPack。ProofPack 与 Capsule 分别做 closure/deletion gate，任何一方不能替代另一方。

#### 下游后置条件与能力要求

M5 的模型只得到 `flow:reservation`、这一个模型最小 Capsule 和四个嵌套 Outcome，并且只创建一个模型任务；它看不到 ProofPack 的 package/config/binding nodes。M5 的确定性 admission 侧持有并重验 `proofPackRef`，拒绝 Capsule 外引用，不能让模型访问仓库根，也不能把 Outcome 重新解释成独立 Flow。typed 覆盖报告预期本例 1 个入口中 1 个、4 个 Outcome 中 4 个、3 个 Java 跨层直接调用中 3 个和 2 个 Mapper statement binding 中 2 个已处理；本 walkthrough 未执行，因此 observed fields 保持 null。

#### 支持范围、失败与 Gap

支持有限、无反射的同步 MVC→Service→MyBatis 直接路径和顺序条件分支。不支持异步回调、AOP 隐式分支、消息、调度、动态 SQL、无限递归或运行时 dispatch；受影响 Outcome 成为 capability Gap。入口或必要 Outcome 无法闭合、Fact→ProofPack dependency 缺失、ProofPack/Capsule digest 漂移、model projection allowlist 不闭合，或超预算且不满足稳定 seam 拆分规则时，在调用模型前停止该 Flow。

#### 验证与测试策略

测试一条 Flow 内四个 Outcome 的条件极性、互斥性、终点、Fact/ProofPack closure 和共享前缀；断言输出数组长度为 1、`OutcomePath[]` 长度为 4、Capsule 数为 1。ProofPack mutation 删除 package/config/namespace/resultType/receiver edge 时，Flow 在模型任务前失败，即使 `modelEvidenceSpans` 字节未变；逐个删除六个 model Evidence spans 时，projection deletion gate 失败，但不得把缺失 excerpt 误报成 Proof edge 失败。另断言模型 task payload 不含 ProofPack-only locator。加入死代码、嵌套 guard、歧义终点、可安全拆分/不可安全拆分和超预算负例。覆盖报告的每个 typed numerator/denominator 从 `RepositoryModel` 重算，不能由 fixture 写死。

#### 为什么这个组合足够深

Flow 编译和模型 Evidence 投影共享同一个核心问题：从已经证明的入口依赖图中保留完整 Outcome，同时只暴露模型理解所需的语义视图。一个 `compileFlows(ProvenFactSet, ProofPack)` interface 隐藏 CFG、Outcome、typed 覆盖、Proof reference validation、projection/minimality、拆分与预算；若拆开，调用者会搬运中间图并容易把终点误当 Flow，或把编译依赖泄漏给模型。按完整 Flow 返回小 Capsule、同时只引用 M3 的完整 ProofPack，让多个模型 Adapter、测试和审阅获得 leverage，故障仍定位在 Proof closure 或单 Flow projection 的明确一侧。

### M5 Bounded per-flow interpretation and deterministic admission → `AdmittedFlowMeaning[] / interpretation gaps`

#### 为什么必须存在

源码可以证明“字段怎样更新”，却未必直接给出最合适的业务术语。M5 不尝试用词法规则验证模型自由文本，而是让模型从冻结、有限的 `BusinessTermRegistry` 选择 eligible `businessTermKey`，并从有限 `ClaimVocabulary` 选择结构化 claim。程序只准入 term/claim keys 与 basis，不准入任意 factual prose。所有读者可见的条件、数值、因果和终点句子都由 M7 的版本化 `ReaderSentenceTemplate` 从 Proven atoms 确定性渲染；business term value 只能占据显式 `BusinessTermSlot`，无 eligible term 时由 total `TechnicalDisplaySlot` 取代。模型是受限选择器，不是事实发现器、自动正文命名者、fallback 作者、句子作者或自我审批者。

#### 精确输入、前置条件与样例片段

输入是 M4 唯一的 `flow:reservation`、包含四个 `OutcomePath` 的唯一 `EvidenceCapsule`、其已验证 `proofPackRef`、严格 JSON Schema、冻结 `BUSINESS_TERM_REGISTRY_V1`、冻结且对受支持 anchor kinds 全覆盖的 `TECHNICAL_DISPLAY_REGISTRY_V1`、固定 prompt/`ClaimVocabulary` 版本、Provider 运行策略和 G01–G05。前置条件是 Capsule digest、ProofPack/Fact closure、model projection、Gap provenance、term eligibility、technical fallback totality、allowlist、token 预算和单一 task identity 全部验证；live 路径还必须通过 logged-in Codex session 与本地状态可初始化的 preflight。

本例只创建一个任务。它包含 `flow:reservation`、四个 Outcome、A01–A20 的规范事实对象、六个 `modelEvidenceSpans` 摘录、只读 Proof IDs 和 G01–G05 provenance；它不包含 ProofPack 的 package/config/namespace/resultType/receiver dependency nodes、仓库其他文件、任意检索能力或可编辑 prompt。

#### 严格任务与 prompt 骨架

固定 system instruction 的语义如下；运行时只把 canonical Capsule 放入 `evidenceCapsule` 字段，不拼接其他仓库文本：

```yaml
taskKind: FLOW_MEANING_PROPOSAL
rules:
  - 只能为允许的 technicalAnchor 选择 eligible businessTermKey；只能选择 Schema 枚举的 claimKey 或 questionKey。
  - factual claim 必须引用非空 allowedAtomIds 子集；questionOnly 必须引用非空 allowedGapIds 子集。
  - 不得输出 factual sentence，不得新增条件、数值、因果、关系、终点、运行结果、企业政策或来源身份。
  - 不得输出 Markdown、路径、SHA、Candidate identity、prompt 文本或运行时身份。
  - openLabelCandidate 或 optionalGloss 如出现，类型固定为 HUMAN_REVIEW_REQUIRED，只能进入 sidecar。
output:
  additionalProperties: false
  required: [taskSpecId, flowSliceId, capsuleId, proposals]
  proposalType: [BUSINESS_TERM_SELECTION, STRUCTURED_CLAIM_SET, QUESTION_ONLY]
  termSelectionRequired: [proposalKey, proposalType, businessTermKey, targetAnchor, basisAtomIds]
  claimSetRequired: [proposalKey, proposalType, claimKeys, basisAtomIds]
  claimSetOptional: [businessTermKey, targetAnchor, openLabelCandidate, optionalGloss]
  questionRequired: [proposalKey, proposalType, questionKey, basisGapIds]
evidenceCapsule: canonical object supplied by M4
```

`BusinessTermRegistry` 是随请求冻结、可版本化审阅的可选业务术语集合。每个 entry 必须显式给出 `businessTermKey`（即稳定 key）、`anchorKind`、`localizedValue`、`eligibleAtomKinds`、`minimumBasisAtomIds`、整数 `priority` 和 `technicalFallbackPolicyKey`；缺一字段整个 registry 无效。模型只返回 key，程序验证 anchor kind、atom kinds 与 minimum basis closure，然后由 registry 提供显示值。

技术回退不依赖 BusinessTermRegistry 是否命中。独立冻结的 `TechnicalDisplayRegistry` 对 CapabilityProfile 可产生的每一种 anchor kind 都必须是 total function：`FLOW` 用 route+handler，`REQUEST/RESULT` 用已绑定 type simple name，`RECORD` 按 proven table→bound type 的固定顺序，`OUTCOME` 按 exception→return type 的固定顺序，`ACTIVITY` 按 admitted claim-template→stable technical anchor key 的固定顺序。每个 policy 只消费 Proven atom/type/technical anchor，输出确定性技术显示值；不做业务命名。CapabilityProfile 的 anchor constructor 保证每个 resolution order 至少有一个输入，否则该 anchor 根本不能进入 M5。启动 M5 前，程序枚举本次所有 anchors 并证明每个恰好匹配一个 fallback policy。对任意仓库，如果没有 eligible business term，M5 不造新词：M6/M7 使用这个 total technical fallback 并记录 `NEEDS_TERM_REGISTRY`；没有 fallback 则是 Profile/registry fatal，不能留下空术语槽。只有以后经过独立审批、冻结到新 registry/addendum 的 term 才能在一次新的冻结生成中自动显示，不能借本 Candidate 的 improvement 扩张 registry。

若保留 `openLabelCandidate` 或 `optionalGloss`，它们只能进入 sidecar，状态为 `HUMAN_REVIEW_REQUIRED`，不能形成 `AdmittedFlowMeaning`、不能进入 `BusinessTermSlot`、不计入 grounding/lineage 分子。人工觉得措辞合适也不能直接“点通过”当前自动正文；必须先把它提升到后续版本化 registry/addendum，再启动符合新冻结输入的生成。

`claimKey` 不是模型自由发明的谓词，而是严格 Schema 中的有限 `ClaimVocabulary`。程序从 Fact atoms 编译 `AllowedClaimRegistry`：例如 `INCREMENT_RESERVED_QUANTITY → {A13}`、`INCREMENT_VERSION → {A14}`、`NON_SINGLE_ROW_UPDATE_THROWS → {A17,A18}`。`DECREMENT_ON_HAND`、`CREATE_ORDER_RESERVATION` 和 `RETRY_AFTER_NON_SINGLE_ROW_UPDATE` 可作为词表中的审计值被解析，但本例 registry 没有充分 atom binding，所以不能准入。`questionKey` 则从 `GapExpectationProfile` 绑定到 Gap；例如 `ASK_MISSING_ROW_POLICY → {G05}`。程序只需要验证有限 key 与精确 basis，不对开放中文句子假装做语义证明。

#### 确定性算法和两轮约束

1. 程序从这一 Capsule 构造一个不可变 `ModelTask`，分别记录 configured Adapter、configured Auth Mode、expected upstream provider/model/reasoning/sandbox；这些字段永不合并。
2. `FlowInterpretationRound R1` 是初始候选。响应必须严格匹配 Schema、任务 identity 和 allowlist；proposal key 唯一，term/claim/question 的 basis 类型不能混用。
3. 程序先验证 `TechnicalDisplayRegistry` 对所有 anchors total，再验证语法、identity 和引用；按 proposal 类型处理时，business term key 必须存在于冻结 registry、anchor kind 相符、`eligibleAtomKinds` 覆盖 basis atoms 且满足 minimum basis；claim key 必须存在于有限词表且由 `AllowedClaimRegistry` 的最小 basis 支撑；question key 必须由 `GapQuestionRegistry` 与 `basisGapIds` 精确支撑。open label/gloss 只隔离归档。
4. 程序从结构化支持集重算 canonical decision：全部 term/claim keys 受支持为 `KEEP`；claim set 有非空受支持子集时为 `NARROW` 并只保留受支持 keys；没有受支持结构为 `DROP`；合法 question-only 为 `NEEDS_EVIDENCE`。没有 eligible term 的 anchor 不要求模型造 proposal，而是确定性解析 `technicalFallbackPolicyKey` 并登记 `NEEDS_TERM_REGISTRY`。程序从不接收或生成 open text 作为事实或自动显示术语。
5. `FlowInterpretationRound R2` 是同一 Reader Candidate、同一 Frozen Flow 的唯一精度复核。它必须逐一审查全部 R1 proposal，不能新增或遗漏；basis 只能保持或收窄；term/claim keys 只能保持或从 eligible set 中删减；review decision 只能为 `KEEP`、`NARROW`、`DROP`、`NEEDS_EVIDENCE`。R2 不能提交自动显示的 replacement text。
6. 程序对 R2 决定再次执行 Schema、identity、basis、business-term registry、claim registry 和 question registry 校验。合法 `KEEP`/`NARROW` 形成带稳定 ID 的 `AdmittedFlowMeaning`；`DROP` 不进入知识；`NEEDS_EVIDENCE`/`NEEDS_TERM_REGISTRY` 进入 interpretation gap。R2 与 canonical decision 不一致时保留 finding，并采用程序更保守的处置。
7. 任一 started session 都消耗这一 Flow 的对应 `FlowInterpretationRound`。空响应、非法 JSON、越界引用、身份不匹配或运行错误后不自动重试、不换 Provider、不回退 API key。

R1 和 R2 的**冻结证据基座完全相同**：VerifiedSnapshot identity、`flow:reservation`、唯一 EvidenceCapsule、ProofPack reference、Fact/Proof allowlist、Gap provenance、BusinessTermRegistry、TechnicalDisplayRegistry、task/prompt/Schema/vocabulary 版本和 runtime policy。R2 在同一基座之外额外接收 R1 candidate，只能做精度收窄，不能读新源码、增加 Evidence/Proof、增加 term/fallback 或改变 Flow/Outcome。

理想验收要求是：唯一任务及运行身份精确匹配；Schema 有效；所有 R1 proposal 被 R2 恰好处置一次；所有准入 term/claim keys 有非空、未扩张且类型正确的 basis；所有 supported anchors 有唯一 technical fallback；所有 question-only 有允许的 Gap basis；open label/gloss 全部隔离；没有 factual prose 或内部 identity 泄漏；所有未支持内容成为 DROP、NEEDS_EVIDENCE 或 NEEDS_TERM_REGISTRY。任何来源/任务/registry 漂移、technical fallback 不 total/不唯一、未知引用、basis 扩张、proposal 丢失/新增、运行身份缺失或不匹配、Schema/JSON 无效、open text 进入自动正文或把 Gap 写成事实，都是本 Flow 的 fatal error。

#### 模型角色

模型只选择和复核 eligible business term key、有限 claim key 与有限 question key；可以提交隔离的 open label/gloss 供未来人工词表工作。程序独立重算最终 decision；模型没有写 reader factual sentence 或自动显示 term value 的角色。

#### 具体输出

本例的 technical fallback 对六种支持 anchor kind 全覆盖；其中 `TABLE_OR_BOUND_TYPE_V1` 在 table atom 不存在时仍可退到 exact bound type，因此是 total，而不是“尽量猜一个名称”：

```json
{
  "registryVersion": "TECHNICAL_DISPLAY_REGISTRY_V1",
  "supportedAnchorKinds": ["ACTIVITY", "FLOW", "OUTCOME", "RECORD", "REQUEST", "RESULT"],
  "policies": [
    {"anchorKind": "FLOW", "policyKey": "FLOW_ROUTE_HANDLER_V1", "resolutionOrder": ["HTTP_METHOD_ROUTE_AND_HANDLER"]},
    {"anchorKind": "REQUEST", "policyKey": "BOUND_TYPE_SIMPLE_NAME_V1", "resolutionOrder": ["BOUND_TYPE_FQN"]},
    {"anchorKind": "RECORD", "policyKey": "TABLE_OR_BOUND_TYPE_V1", "resolutionOrder": ["SQL_TABLE", "BOUND_TYPE_FQN"]},
    {"anchorKind": "RESULT", "policyKey": "BOUND_TYPE_SIMPLE_NAME_V1", "resolutionOrder": ["BOUND_TYPE_FQN"]},
    {"anchorKind": "OUTCOME", "policyKey": "TECHNICAL_TERMINAL_V1", "resolutionOrder": ["THROW_TYPE", "RETURN_TYPE"]},
    {"anchorKind": "ACTIVITY", "policyKey": "ACTIVITY_CLAIM_OR_ANCHOR_V1", "resolutionOrder": ["ADMITTED_CLAIM_TEMPLATE", "TECHNICAL_ANCHOR_KEY"]}
  ]
}
```

按这些 policies，本 fixture 即使删掉全部 BusinessTerm entries，也会分别得到 `POST /reservations / ReservationService.reserve`、`ReservationRequest`、`inventory`、`ReservationReceipt`、各分支的 exception/return type，以及有限 claim-template（若无 claim 则用 `activity:inventory-update`）；这些是技术显示，不进入七个 admitted business meanings 的 grounding 分子。

本例冻结 BusinessTermRegistry 恰有七个可显示 term；localized value 是 registry 审批内容，不是模型响应，每个 entry 都包含算法实际读取的完整字段：

```json
{
  "registryVersion": "BUSINESS_TERM_REGISTRY_V1",
  "terms": [
    {"anchorKind": "FLOW", "businessTermKey": "TERM_RESERVATION_FLOW", "eligibleAtomKinds": ["DIRECT_CALL", "HTTP_METHOD", "HTTP_ROUTE", "SQL_ASSIGNMENT"], "localizedValue": "库存预留", "minimumBasisAtomIds": ["A01", "A02", "A04", "A13"], "priority": 100, "technicalFallbackPolicyKey": "FLOW_ROUTE_HANDLER_V1"},
    {"anchorKind": "REQUEST", "businessTermKey": "TERM_RESERVATION_REQUEST", "eligibleAtomKinds": ["REQUEST_BODY_TYPE"], "localizedValue": "预留请求", "minimumBasisAtomIds": ["A03"], "priority": 100, "technicalFallbackPolicyKey": "BOUND_TYPE_SIMPLE_NAME_V1"},
    {"anchorKind": "RECORD", "businessTermKey": "TERM_INVENTORY_RECORD", "eligibleAtomKinds": ["MAPPER_READ", "SQL_TABLE"], "localizedValue": "库存记录", "minimumBasisAtomIds": ["A07", "A12"], "priority": 100, "technicalFallbackPolicyKey": "TABLE_OR_BOUND_TYPE_V1"},
    {"anchorKind": "RESULT", "businessTermKey": "TERM_RESERVATION_RESULT", "eligibleAtomKinds": ["RETURN_COMPONENT"], "localizedValue": "预留结果", "minimumBasisAtomIds": ["A19", "A20"], "priority": 100, "technicalFallbackPolicyKey": "BOUND_TYPE_SIMPLE_NAME_V1"},
    {"anchorKind": "OUTCOME", "businessTermKey": "TERM_INVALID_QUANTITY_OUTCOME", "eligibleAtomKinds": ["CONDITION", "THROW"], "localizedValue": "数量不合法", "minimumBasisAtomIds": ["A05", "A06"], "priority": 100, "technicalFallbackPolicyKey": "TECHNICAL_TERMINAL_V1"},
    {"anchorKind": "ACTIVITY", "businessTermKey": "TERM_INCREMENT_RESERVED_AND_VERSION", "eligibleAtomKinds": ["SQL_ASSIGNMENT"], "localizedValue": "增加预留数量并递增版本", "minimumBasisAtomIds": ["A13", "A14"], "priority": 100, "technicalFallbackPolicyKey": "ACTIVITY_CLAIM_OR_ANCHOR_V1"},
    {"anchorKind": "OUTCOME", "businessTermKey": "TERM_UPDATE_COUNT_NOT_ONE_OUTCOME", "eligibleAtomKinds": ["CONDITION", "THROW"], "localizedValue": "更新记录数不为一", "minimumBasisAtomIds": ["A17", "A18"], "priority": 100, "technicalFallbackPolicyKey": "TECHNICAL_TERMINAL_V1"}
  ]
}
```

以下九个 R1 proposal 展示四种最终决定：

| proposal | R1 typed candidate | R2/程序决定 | 最终结果 |
| --- | --- | --- | --- |
| P01 | `BUSINESS_TERM_SELECTION`：`TERM_RESERVATION_FLOW`，basis A01,A02,A04,A13 | KEEP | registry 显示“库存预留” |
| P02 | `BUSINESS_TERM_SELECTION`：`TERM_RESERVATION_REQUEST`，basis A03 | KEEP | registry 显示“预留请求” |
| P03 | `BUSINESS_TERM_SELECTION`：`TERM_INVENTORY_RECORD`，basis A07,A12 | KEEP | registry 显示“库存记录” |
| P04 | `BUSINESS_TERM_SELECTION`：`TERM_RESERVATION_RESULT`，basis A19,A20 | KEEP | registry 显示“预留结果” |
| P05 | `BUSINESS_TERM_SELECTION`：`TERM_INVALID_QUANTITY_OUTCOME`，basis A05,A06 | KEEP | registry 显示“数量不合法” |
| P06 | `STRUCTURED_CLAIM_SET`：term `TERM_INCREMENT_RESERVED_AND_VERSION`；claims `INCREMENT_RESERVED_QUANTITY`、`INCREMENT_VERSION`、`DECREMENT_ON_HAND`、`CREATE_ORDER_RESERVATION`；basis A13,A14 | NARROW | 保留 term、前两个 claim keys 及 A13,A14；没有扣减 on-hand 或创建订单 claim |
| P07 | `STRUCTURED_CLAIM_SET`：term `TERM_UPDATE_COUNT_NOT_ONE_OUTCOME`；claim `NON_SINGLE_ROW_UPDATE_THROWS`；basis A17,A18 | KEEP | registry 显示“更新记录数不为一”；事实模板只呈现条件与 throw，不声称真实竞争发生或外部返回什么 |
| P08 | `STRUCTURED_CLAIM_SET`：claim `RETRY_AFTER_NON_SINGLE_ROW_UPDATE`；basis A17,A18 | DROP | registry 没有 retry basis；不产生读者事实 |
| P09 | `QUESTION_ONLY/ASK_MISSING_ROW_POLICY`，`basisGapIds: [G05]` | NEEDS_EVIDENCE | 保留为缺失行政策问题，不使用 A07/A08 伪装为答案依据 |

具体 R2 输出片段：

```json
{
  "flowSliceId": "flow:reservation",
  "reviews": [
    {"decision": "NARROW", "proposalKey": "P06", "retainedBasisAtomIds": ["A13", "A14"], "retainedBusinessTermKey": "TERM_INCREMENT_RESERVED_AND_VERSION", "retainedClaimKeys": ["INCREMENT_RESERVED_QUANTITY", "INCREMENT_VERSION"]},
    {"basisAtomIds": ["A17", "A18"], "decision": "DROP", "proposalKey": "P08"},
    {"basisGapIds": ["G05"], "decision": "NEEDS_EVIDENCE", "proposalKey": "P09"}
  ]
}
```

最终 9/9 proposal 都有 disposition；6 个 KEEP、1 个 NARROW、1 个 DROP、1 个 NEEDS_EVIDENCE，得到 7 个有稳定 lineage 的 `AdmittedFlowMeaning`。三个结构化 claim keys 计算 claim registry grounding；七个 business term keys 分别计算 frozen-registry resolution 与 atom lineage，不能计作 Proven Fact。任何 open label/gloss 都不在这七个 meanings 内。

#### 下游后置条件与能力要求

M6 只能消费 KEEP/NARROW 的 7 个 typed meanings、它们的 stable meaning ID、`flowSliceId`、`businessTermKey`、精确 `basisAtomIds`/claim keys，以及 DROP/NEEDS_EVIDENCE 记录。它必须以技术 anchor 合并对象，不能以 localized term value 作为 identity，也不能重新打开 Capsule、拼接 open text 或提升 Gap。

#### 支持范围、失败与 Gap

模型支持范围仅为有限 term/claim/question 选择和可选隔离 open text。数值、条件、因果、代码关系、终点句子、localized term value、technical fallback、来源位置、章节、Markdown 和政策均不由模型决定。eligible term 缺失时使用 total TechnicalDisplayRegistry fallback 并登记 `NEEDS_TERM_REGISTRY`；若 anchor kind 不受 Profile 支持，或技术 registry 对受支持 anchor 不 total/不唯一，则是 fatal。任务/运行身份、allowlist、registry 或 Schema 失败同样是 fatal，受影响 Flow 不进入 M6。

#### 验证与测试策略

自动测试使用 scripted/recorded Provider，覆盖未知 term/claim、term-anchor 不匹配、eligible atom kind/minimum basis/priority/fallback-policy 字段缺失、atom/Gap basis 类型混用、空 basis、额外字段、factual prose 字段、identity mismatch、R2 新增/遗漏/重复、basis/key 扩张、四种 decision、NARROW 再校验和内部 token 清洗。对六种支持 anchor kind 各做有/无 business term fixture，断言 TechnicalDisplayRegistry 始终产生唯一确定显示；缺 policy、双重匹配或未知 anchor fatal。专门断言 `openLabelCandidate`/gloss 只进 `HUMAN_REVIEW_REQUIRED` sidecar，七个自动业务术语全部来自 frozen registry。live Adapter 只做另行授权的运行验收；测试绝不调用模型或网络。用同一 Capsule 回放多个 recorded 响应，断言程序 admission、term/fallback resolution 与事实句渲染一致，从而测量而不是假定跨模型稳定性。

#### 为什么这个组合足够深

外部只暴露“解释一个冻结 Flow”，内部隐藏任务冻结、Provider Adapter、两轮协议、typed Schema、identity、BusinessTerm/TechnicalDisplay/Claim/Question registries 和 admission。解释与准入必须紧邻，否则 open prose 或空 fallback 会泄漏到调用者。小 interface 给多个 Provider/recorded Adapter 和所有 Flow 复用，registry 变化集中在一处；一个完整过程的小 Capsule 又把不同模型面对的证据与 eligible keys 固定为同一局部材料，减少跨模型漂移。

### M6 Repository-level business knowledge assembly → `RepositoryBusinessModel`

#### 为什么必须存在

一个仓库可能有多个入口根 Flow，它们会重复对象或选择不同 term keys。九章不能直接拼接 per-flow 输出；M6 以已证明技术 anchor 为 identity，把本例一条 Flow、四个 Outcome、事实、Gap 和七个 registry-backed typed meanings 合成一个无冲突、lineage 完整的仓库业务视图。它保存结构化知识，不保存模型写的 factual prose 或 open label。

#### 精确输入、前置条件与样例片段

输入是 M3 的 8 Facts/20 admitted atoms/0 rejected atoms/5 gaps、M4 的一条 `flow:reservation`（内含四个 Outcome）与覆盖报告，以及 M5 的 7 个准入 meanings、冻结 BusinessTermRegistry/TechnicalDisplayRegistry identities 和 P08/P09 两条未准入处置。前置条件是每个 meaning 都有 stable meaning ID、`flowSliceId`、frozen `businessTermKey` 和闭合 `basisAtomIds`，每个 Flow/Outcome identity 唯一，技术 anchor 可以稳定归一，而且每个受支持 anchor 已验证恰有一个 technical fallback。

例如 `flow:reservation` 的 insufficient、update-count-not-one 和 accepted Outcome 都经过同一个 `inventory` table anchor，而 invalid-quantity Outcome 在读取前结束。M6 只创建一个库存记录对象和一个 Flow；不能把四个 Outcome 复制成四个 Flow，也不能把被 NARROW 掉的“订单预留”或被 DROP 的 retry claim 带入知识。

#### 确定性算法

1. 以 Fact 的技术 anchor 与关系 identity 建立对象、活动、字段、公式、Flow、Outcome 和 Gap 节点；Proven value 不含模型语言。
2. 对每个 `AdmittedFlowMeaning` 重验 stable ID、`flowSliceId == flow:reservation`、target anchor、business term key、registry version、eligible atom kinds、minimum basis、integer priority、technical fallback policy、basis atoms 和可选 claim keys；把 resolved registry term 作为可替换 `ADMITTED_INTERPRETATION` attribute，绝不覆盖 Fact value。
3. 对相同 anchor 的 compatible eligible terms 只按显式整数 `priority` 合并：数值更高者是 primary，其他保留为 alias key；最高 priority 并列且 localized values 不同是 fatal conflict，不能用输入顺序或 key 字典序偷偷裁决。不兼容 term selection 同样进入 conflict。没有 eligible key 或移除 term 时必须通过独立 `TechnicalDisplayRegistry` 的唯一 policy 解析技术显示名，记录 `NEEDS_TERM_REGISTRY`，而不改变事实句。
4. 保存一条 reservation Flow 与四个 Outcome reference；共享前缀只保存一次。P08 留在 DROP disposition，P09 以 G05 interpretation gap 保存，二者都不成为业务行为。
5. 把 reader 可用知识分成 `PROVEN_FACT`、`ADMITTED_INTERPRETATION` 和 `GAP`；关系仅以 typed relation + atom basis 存储，不拼接模型 gloss。
6. 派生示例问题只能引用已准入对象、活动、条件或 Gap 的稳定 key，不得创造答案。
7. 验证 20 个 Fact atom 全部仍在仓库模型中、7 个 meanings 都有 Flow/term/basis lineage、5 个 Gap provenance 未被事实化，且冲突为空，才交给 M7。

#### 模型角色

无新增角色。M6 不再调用模型；M5 的提案只能被合并、保留 alias 或因冲突拒绝，不能自我升级。

#### 具体输出

```json
{
  "admittedMeanings": [
    {"basisAtomIds": ["A01", "A02", "A04", "A13"], "businessTermKey": "TERM_RESERVATION_FLOW", "flowSliceId": "flow:reservation", "meaningId": "meaning:reservation-flow", "resolvedTermValue": "库存预留", "targetAnchor": "flow:reservation"},
    {"basisAtomIds": ["A03"], "businessTermKey": "TERM_RESERVATION_REQUEST", "flowSliceId": "flow:reservation", "meaningId": "meaning:reservation-request", "resolvedTermValue": "预留请求", "targetAnchor": "type:ReservationRequest"},
    {"basisAtomIds": ["A07", "A12"], "businessTermKey": "TERM_INVENTORY_RECORD", "flowSliceId": "flow:reservation", "meaningId": "meaning:inventory-record", "resolvedTermValue": "库存记录", "targetAnchor": "table:inventory"},
    {"basisAtomIds": ["A19", "A20"], "businessTermKey": "TERM_RESERVATION_RESULT", "flowSliceId": "flow:reservation", "meaningId": "meaning:reservation-result", "resolvedTermValue": "预留结果", "targetAnchor": "type:ReservationReceipt"},
    {"basisAtomIds": ["A05", "A06"], "businessTermKey": "TERM_INVALID_QUANTITY_OUTCOME", "flowSliceId": "flow:reservation", "meaningId": "meaning:invalid-quantity", "resolvedTermValue": "数量不合法", "targetAnchor": "outcome:invalid-quantity"},
    {"basisAtomIds": ["A13", "A14"], "businessTermKey": "TERM_INCREMENT_RESERVED_AND_VERSION", "claimKeys": ["INCREMENT_RESERVED_QUANTITY", "INCREMENT_VERSION"], "flowSliceId": "flow:reservation", "meaningId": "meaning:update-reserved-version", "resolvedTermValue": "增加预留数量并递增版本", "targetAnchor": "activity:inventory-update"},
    {"basisAtomIds": ["A17", "A18"], "businessTermKey": "TERM_UPDATE_COUNT_NOT_ONE_OUTCOME", "claimKeys": ["NON_SINGLE_ROW_UPDATE_THROWS"], "flowSliceId": "flow:reservation", "meaningId": "meaning:update-count-exception", "resolvedTermValue": "更新记录数不为一", "targetAnchor": "outcome:update-count-not-one"}
  ],
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "factAccounting": {"admittedAtoms": 20, "candidateAtoms": 20, "proofClosed": 20, "rejectedAtomDispositions": [], "rejectedAtoms": 0},
  "flowSlices": [{"flowSliceId": "flow:reservation", "outcomePathIds": ["outcome:invalid-quantity", "outcome:insufficient", "outcome:update-count-not-one", "outcome:accepted"]}],
  "gaps": ["G01", "G02", "G03", "G04", "G05"],
  "proposalDisposition": {"DROP": ["P08"], "NEEDS_EVIDENCE": [{"basisGapIds": ["G05"], "proposalKey": "P09"}]},
  "technicalDisplayRegistryVersion": "TECHNICAL_DISPLAY_REGISTRY_V1",
  "termRegistryVersion": "BUSINESS_TERM_REGISTRY_V1"
}
```

#### 下游后置条件与能力要求

M7 得到一个稳定排序、无模型 factual prose/open label、事实/解释/Gap 类型明确的 `RepositoryBusinessModel`。一条 Flow 与四个 Outcome 保持嵌套；20 个 admitted Fact atoms、7 个 meanings 的 ID/Flow/term/basis lineage 和 5 个 Gap provenance 都完整。M7 必须能在 term 不 eligible 时通过 total TechnicalDisplayRegistry 渲染同样的事实结构。

#### 支持范围、失败与 Gap

支持同一 VerifiedSnapshot 内由稳定技术 anchor 连接的同步 Flow 合并。不支持跨仓库 identity、运行时别名、数据库外键之外的隐含企业关系或模型凭 open text/名称相似合并。事实冲突、meaning term lineage 缺失、同一 anchor 的不兼容/最高 priority 并列 registry entries，或 technical fallback 不 total/不唯一均为 fatal；term selection 缺失可保留由技术 registry 解析的名称并形成 `NEEDS_TERM_REGISTRY`。

#### 验证与测试策略

用单 Flow/四 Outcome fixture 断言不发生 Flow 膨胀，并用另一个多 Flow fixture 覆盖共享对象去重、数值更高 priority 胜出、最高 priority 并列冲突、Fact 优先级、Gap 守恒和随机输入顺序下的相同 canonical 输出。逐个删除七个 meaning 的 ID、Flow、term key、registry version、eligible atom kinds、minimum basis、priority、fallback policy 或 basis 应失败；删除 term eligibility 应得到唯一 technical fallback 与 `NEEDS_TERM_REGISTRY`，不改变 20 个 Fact。测试只跨 M6 interface，内部 map/set 实现可以替换。

#### 为什么这个组合足够深

M6 把跨 Flow 去重、Outcome nesting、term/interpretation lineage、冲突、类型优先级和问题派生隐藏在一个 `assembleRepositoryKnowledge(...)` seam 后。若直接让 M7 遍历模型响应，registry resolution 与防 open prose 泄漏规则会散落到九个章节。集中后，一个 anchor/registry/lineage 修复能服务所有章节和 Trace，产生明显 leverage 与 locality。

### M7 Deterministic nine-section planning → `NineSectionDocumentModel + atom disposition/coverage`

#### 为什么必须存在

九个正确标题不等于内容完整。M7 在渲染前为每个知识条目指定唯一章节 owner，为每个 Fact atom 和 admitted meaning 保存处置，并用版本化 `ReaderSentenceTemplate`、冻结 `BusinessTermRegistry` 与 total `TechnicalDisplayRegistry` 生成 reader AST。这样任意模型 prose 无法成为事实句或自动术语，即使没有 eligible business term 也不存在开放或空白显示槽；读者质量能由原子、meaning、term/fallback 和模板 lineage 重算，而不是用字数或 Markdown SHA 代替。

#### 精确输入、前置条件与样例片段

输入是 M6 的 `RepositoryBusinessModel`、共享 [NineSectionProfile](../../../shared/source-agent-contracts/README.md)，以及冻结的 `ReaderSentenceTemplateRegistry V1`、`BUSINESS_TERM_REGISTRY_V1`、`TECHNICAL_DISPLAY_REGISTRY_V1`、ownership rules 和 Gap question templates。前置条件是共享 Profile 恰好给出九章名称/顺序，仓库知识无 fatal conflict，一条 Flow/四个 Outcome、20 个 Fact atom、7 个 stable term-backed meanings 和 5 个 Gap provenance 全部存在，所有受支持 anchor 的 technical display totality 已证明。

本例的入口 method/route、对象、四个 Outcome、可用量公式、更新条件和五个 Gap 都是具体输入；M7 不再读取源码或询问模型。例如 `HTTP_ENTRY_V1` 的固定文本是 `调用入口：{httpMethod} {route}`，两个 slots 只能由 A01/A02 填入；`CONDITIONAL_THROW_V1` 的 condition/exception slots 只能引用相应 guard/throw atoms。

#### 确定性算法

1. 从共享 Profile 创建恰好九个 section slot，不能派生另一套标题。
2. 按版本化 ownership rule 为每个 Fact item 指定一个 owner；其他章节只能引用该 item，不复制新的 Fact identity。每个 atom 处置为 `READER_BODY`、`TECHNICAL_BASIS`、`GAP` 或 `REASONED_EXCLUSION`；无处置是 fatal。
3. 为每个 factual item 选择固定 template ID，并用 typed atom slots 填充 method、route、condition、value、field、operator、exception 和 return component。template 不允许 open-text fact slot；slot kind 与 atom kind 不兼容即 fatal。
4. admitted `businessTermKey` 只能进入 `BusinessTermSlot`；程序从冻结 registry 解析 localized value，并保留 meaning ID、Flow ID、term key、basis atoms、priority 和 fallback policy。若没有 eligible business term，程序改由 `TechnicalDisplayRegistry` 的唯一 policy 填入 `TechnicalDisplaySlot`；两种 slot 都是 closed typed slot，open label/gloss 不进入自动 AST。七个 meaning 各有 `interpretationDisposition`：唯一 owner、rendered item key、term key 和 basis；未呈现的 meaning 必须有 reasoned disposition。
5. 从 G01–G05 的 `questionTemplateKey`、reason identity 和 searched-scope summary 生成 bounded question：只能说“在所搜索的支持范围内没有证据，需确认……”，不能生成负面政策事实。若问题模板需要业务术语，只能引用 frozen registry term slot。
6. 生成确定性的段落、表格和列表 AST；正文用中文 template 与 registry-resolved term value，内部 ID、SHA、transport enum、prompt、open label/gloss 和 receipt 字段只进入 sidecar。
7. 验证九章基数/顺序、owner 唯一、atom/meaning 守恒、term registry identity/eligibility、Gap 可见性、引用无环、technical fallback 和 reader item 可回答性。

#### 模型角色

无。模型不选择章节、不排序、不处置原子/meaning、不选 factual template、不填 factual slot、不提供 localized term value，也不写 Markdown。

#### 具体输出

本例把 20 个 Fact atom 全部明确呈现在 reader body，0 个只藏在 technical basis，0 个 fact-as-gap，0 个 exclusion；五个独立 Gap 全部由第九章拥有：

| 章节 owner | Fact atom disposition | Gap/引用 |
| --- | --- | --- |
| 文档说明 | A01, A02, A03, A04 → READER_BODY | 显示 `调用入口：POST /reservations`、request body 与 Controller→Service 传递 |
| 业务目标 | 不拥有新 atom | 呈现 registry-resolved flow term，并明确它是受控业务术语 |
| 业务对象 | A07, A12, A19, A20 → READER_BODY | 显示 `findBySku`、`inventory` 与 return components |
| 业务活动 | A05, A06, A10, A11, A13, A14, A17, A18 → READER_BODY | 四个 Outcome 由固定 condition/terminal templates 生成 |
| 字段与维度 | A08, A15, A16 → READER_BODY | 显示查询 `sku` 以及更新 `sku + version` predicates |
| 对象关系 | 不拥有新 atom | 只引用 A03/A04/A07/A12/A19/A20 的 item |
| 指标口径 | A09 → READER_BODY | 明确没有其他已证明指标 |
| 示例问题 | 不拥有新 Fact；只引用已准入 item | 四个可回答问题 |
| 待确认事项 | 无 Fact atom | G01–G05 |

七个 meaning 也逐项处置；`renderedItemKey` 仅授权把 frozen registry value 插入 term slot，不授权生成该 item 的事实句：

| meaning ID | businessTermKey | owner / rendered item | basis |
| --- | --- | --- | --- |
| `meaning:reservation-flow` | `TERM_RESERVATION_FLOW` | 业务目标 / `goal.flow-term` | A01,A02,A04,A13 |
| `meaning:reservation-request` | `TERM_RESERVATION_REQUEST` | 业务对象 / `object.request-term` | A03 |
| `meaning:inventory-record` | `TERM_INVENTORY_RECORD` | 业务对象 / `object.inventory-term` | A07,A12 |
| `meaning:reservation-result` | `TERM_RESERVATION_RESULT` | 业务对象 / `object.result-term` | A19,A20 |
| `meaning:invalid-quantity` | `TERM_INVALID_QUANTITY_OUTCOME` | 业务活动 / `outcome.invalid-term` | A05,A06 |
| `meaning:update-reserved-version` | `TERM_INCREMENT_RESERVED_AND_VERSION` | 业务活动 / `activity.update-term` | A13,A14 |
| `meaning:update-count-exception` | `TERM_UPDATE_COUNT_NOT_ONE_OUTCOME` | 业务活动 / `outcome.update-count-term` | A17,A18 |

紧凑 `NineSectionDocumentModel`：

```json
{
  "atomDisposition": {"factAtoms": 20, "reasonedExclusion": 0, "readerBody": 20, "technicalBasis": 0, "unassigned": 0},
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "gapDisposition": {"ownedByPendingQuestions": 5, "total": 5},
  "interpretationDisposition": {"ownedAndRendered": 7, "total": 7, "unassigned": 0},
  "businessTermRegistry": "BUSINESS_TERM_REGISTRY_V1",
  "readerSentenceTemplateRegistry": "READER_SENTENCE_TEMPLATES_V1",
  "sections": ["文档说明", "业务目标", "业务对象", "业务活动", "字段与维度", "对象关系", "指标口径", "示例问题", "待确认事项"],
  "technicalDisplayRegistry": "TECHNICAL_DISPLAY_REGISTRY_V1"
}
```

#### 下游后置条件与能力要求

M8 得到完整、稳定、无需再次推断的渲染模型。它只能按 Document AST 渲染，并保留 Fact 与 term-backed meaning 两类 lineage；如果 atom/meaning/term-registry/Gap/owner/九章/template-slot 门禁未通过，不得生成可选 Candidate。

#### 支持范围、失败与 Gap

支持共享九章合同与本 Source Agent 的版本化知识、ownership、sentence template、BusinessTermRegistry、TechnicalDisplayRegistry 和 slot rules。少章、多章、改名、owner 冲突、open factual-text/term slot、内部 token/open label 进入 reader body、未处置 atom/meaning、未知/不 eligible term key、受支持 anchor 无唯一 technical fallback、未解析模板 token、Gap 被写成事实或静默信息丢失均为 fatal。没有已证明指标时，第七章应明确说明边界，而不是生成指标。

#### 验证与测试策略

golden structural tests 检查九章恰好一次、顺序、中文标题、`调用入口：POST /reservations` 和内部 token 禁止；mutation tests 删除/复制/换章一个 atom 或一个 meaning disposition，必须失败。另测七个 term keys 全部来自 frozen registry；逐种删除 eligible term 时必须由对应 technical policy 产生稳定显示并记录 `NEEDS_TERM_REGISTRY`，删除/重复 fallback policy 必须 fatal；继续测试 open label/gloss 泄漏、非法 term/factual/technical slot kind、Gap 问句越界和随机输入顺序下 canonical model 相同。reader gate 使用类型和原子/meaning/term/fallback 计数，不以行数、字数或文件大小作为质量替代。

#### 为什么这个组合足够深

M7 的接口只是“把仓库知识规划为九章”，实现隐藏 ownership、sentence/term registries、typed slots、交叉引用、atom/meaning disposition、Gap visibility 和 reader AST。若把这些规则留给 renderer，九章会各自重复准入逻辑并可能重新引入模型 prose。M7 让 CLI、Java、future local-loopback HTTP Adapter 和不同 renderer 都复用同一事实句/术语计划，章节或 registry 变化集中一处。

### M8 Orchestration, rendering, trace, immutable archive and recovery → Candidate artifacts

#### 为什么必须存在

即使 M1–M7 各自正确，不同入口仍可能漏掉门禁、以不同排序渲染、覆盖旧 Candidate 或在崩溃恢复时重复调用模型。M8 提供唯一外部 seam，把顺序、渲染、身份、Trace、归档、验证和恢复收在一个深模块内。

#### 精确输入、前置条件与样例片段

公共 Java interface 保持小而稳定：

```java
interface CodeToMarkdownAgent {
  CandidateReference generateCandidate(FrozenRepositoryRequest request);
  CandidateReference improveCandidate(ImprovementRequest request);
  ValidationReceipt validateCandidate(CandidateReference candidate);
  TraceView trace(TraceQuery query);
}
```

CLI、测试和 future local-loopback HTTP Adapter 都实现这个 seam；它们不能直接调用 renderer 或 archive。远程 Git Capture 是另一个上游 workflow/Adapter，不实现这个 interface，也不能绕过 M1。`generateCandidate` 的具体样例输入就是 M1 所示 synthetic request，另带固定的 Capability/Profile、存储位置和 M5 runtime policy。前置条件是调用者显式请求一份未发布 Candidate，并有权使用所配置的本地存储与（若启用）模型运行能力。

`improveCandidate(ImprovementRequest)` 是显式的 `ReaderCandidateRound 2` seam。请求必须引用一个不可变 Round 1 Candidate 和逐项 review finding；程序重验它与 parent 使用相同 snapshot identity、Capability/Profile、candidate atom accounting、Facts/ProofPack/Gaps、Flow/Outcome/Capsule、policies、BusinessTermRegistry、TechnicalDisplayRegistry 和 sentence-template registry。它只允许重新运行同一证据基座上的 M5 精度协议，或调整 M7 已允许的 term selection/section presentation；不能扩大 snapshot、scope、facts/Proof dependencies、flows、Outcome、Gap questions、registries 或政策。需要扩大的请求必须创建新的 series 和冻结产品生成，而不是 improvement。

一个 canonical ImprovementRequest 的 machine shape 是：

```json
{
  "expectedParentCandidateId": "candidate:round1-illustrative-not-calculated",
  "findingIds": ["finding:reader-order-001", "finding:term-usage-002"],
  "parentCandidateId": "candidate:round1-illustrative-not-calculated",
  "readerCandidateRound": 2,
  "requestId": "improvement:illustrative-not-calculated",
  "seriesId": "series:illustrative-not-calculated"
}
```

`findingIds` 是已归档 review findings 的精确、去重、稳定排序 ID；自由文本备注不进入 improvement identity。`readerCandidateRound` 的 Schema 只允许 `1|2`，而 ImprovementRequest 只允许 `2`。

每个 Round-1/Round-2 slot 都使用同一个持久化状态机，并绑定不可变 `canonicalRequestId` 与 `prestartAttemptCount`。状态语义如下：

| slot state | 持久语义 | 允许的下一步 |
| --- | --- | --- |
| `RESERVED` | 原子 create-if-absent 已建立唯一 slot且尚无 `thread.started`；idle 时 `prestartAttemptCount=0`，begun prestart attempt 会先把 count 增到 1–3 并追加 attempt receipt，但在成功/失败 event 前仍处于本状态。 | idle slot 可由同一 request 运行确定性门禁并开始 attempt；begun attempt 只等待可判定 event，崩溃后不能盲目重放；不同 request 冲突。 |
| `PRESTART_RETRYABLE` | 最新 attempt 的完整失败回执证明它在 `thread.started` 前结束，且没有 unresolved begun receipt；attempt count 为 1 或 2。 | 只有同一 request、有效 repair/preflight receipt 且 count < 3 才能恢复同一 slot；不能创建新 slot/Candidate。 |
| `STARTED_CONSUMED` | `thread.started` event 已与状态转移原子持久化；本 round 已消费。 | 只可完成归档或记录 terminal failure；不得重放 Provider。 |
| `COMPLETED` | Candidate 已原子归档并通过对应 validation gate。 | 同一 request 幂等读回同一 Candidate；其他 request 冲突。 |
| `TERMINAL_FAILED` | 第三次 prestart 失败、started 后失败、不可判定崩溃或其他 fatal 已归档。 | 同一 request 读回同一 failure；不重试，其他 request 冲突。 |

每次开始 prestart attempt 前先在同一 slot 原子递增 `prestartAttemptCount` 并追加 attempt receipt，因此上限 3 表示“原始 attempt + 最多两次 repaired restart”。明确证明未发生 `thread.started` 的第 1/2 次失败转到 `PRESTART_RETRYABLE`；第 3 次失败直接转到 `TERMINAL_FAILED`。合法 repair 只授权再次使用**同一个** slot，不重置 count，也不产生新的 Candidate identity。

#### 确定性算法

1. `generateCandidate` 为规范 request 建立 append-only series record，并以 create-if-absent 建立唯一 Round-1 slot，写入 `canonicalRequestId`、`readerCandidateRound=1`、`parentCandidateId=null`、`findingIds=[]`、`prestartAttemptCount=0` 和 `slotState=RESERVED`。同一 canonical request 只恢复该 slot；不同 request 不能占用它。
2. 建立 append-only orchestration receipt，先顺序执行不需要 Provider 的 M1→M4，每步只接收上一步通过的产物，并把 M3 完整 ProofPack 与 M4 model-minimal Capsule 分开归档/验证。M5 只能经第 5–6 步的 prestart/已消费转移运行；M5 准入后才执行 M6→M7。任何 prestart attempt 前发生的确定性 fatal 将当前 `RESERVED` slot 转 `TERMINAL_FAILED`、count 保持 0；不能留下可被误恢复的悬空 reservation。
3. `improveCandidate` 先验证 `readerCandidateRound=2`、parent 正是同 series 的 Round 1、parent identity/lineage、精确 finding IDs 与不可扩张 invariant；Round 2 Candidate 固定 parent 为该 Round 1。Round 2 不能作为 parent，因为 ImprovementRequest 的 parent predicate 明确要求 `parent.readerCandidateRound == 1`，Schema 也不存在 round 3。
4. 在任何 Provider 或渲染工作之前，以原子 create-if-absent 为 `seriesId/reader-round-2` 保留唯一 append-only slot，写入 canonical ImprovementRequest identity、`prestartAttemptCount=0`、`slotState=RESERVED`。现有 slot 的 request identity 不同则拒绝 `ROUND_2_SLOT_ALREADY_CONSUMED`；相同则只按状态机恢复。因此第三个 Candidate 和第二个不同 Round 2 在存储 invariant 上不可表示。
5. Round 1/2 都通过同一 prestart transition：从 `RESERVED` 或有完整 failure+repair receipt 的 `PRESTART_RETRYABLE` 原子增加 attempt count；若 preflight/Schema/local-state 初始化失败且可证明没有 `thread.started`，count < 3 时写 `PRESTART_RETRYABLE`，count == 3 时写 `TERMINAL_FAILED`。相同 request 可以在合法修复后恢复同一 slot；count 永不回退。
6. M5 通过 injected Provider Adapter 参与；M8 不暴露任意 prompt。收到 `thread.started` 时必须在接受任何内容前将当前 slot 原子转为 `STARTED_CONSUMED` 并绑定 started receipt。之后成功只能转 `COMPLETED`，任何模型/进程/Schema/内容失败只能转 `TERMINAL_FAILED`，不得重试或换 Provider。recorded Adapter 在测试中模拟完全相同的 event/state protocol。
7. 用固定 UTF-8、LF、转义、段落/表格排序规则把 `NineSectionDocumentModel` 的模板化 AST 渲染为 `document.md`；模型不参与 factual sentence、localized term/technical fallback value 或 Markdown。
8. 生成 canonical JSON 与 append-only JSONL sidecar。JSON object key 递归排序，数组按类型定义的稳定 key 排序；canonicalization/identity 算法版本化。
9. 计算 document content identity、Candidate content identity 和 lineage identity；Candidate identity 必须包含 `seriesId`、`readerCandidateRound`、`parentCandidateId` 和精确排序 `findingIds`。示例 SHA/ID 只解释形状，不构成真实运行结果。
10. 在 workspace 同级 staging 目录写满并校验全部 artifact，再原子安装到不可变 Candidate 目录；同 identity/同字节幂等，不同字节冲突，永不覆盖旧 Candidate。原子安装与 slot `COMPLETED` transition 绑定；不能出现 `COMPLETED` 指向半归档目录。
11. `validateCandidate` 重开归档绑定的 VerifiedSnapshot，重算文件/摘录摘要、candidate/rejected atom accounting、Fact/ProofPack dependency closure、model Evidence projection、atom/meaning/term/fallback disposition、sentence-template slots、series/round state-machine invariants、九章、document hash 和 Trace closure；只读 sidecar 不能替代源码重验。
12. `trace` 对 factual item 从 reader item 经过 template slots、Fact atom、`proofPackId/proofId` 和完整 dependency edges 回到 frozen locators；对 term/fallback item 从 reader item 经过 admitted meaning 或 technical policy、Flow 和 basis atoms 回到同一 ProofPack。Trace 是 lineage/location chain，不冒充 Proof，也不把 term/fallback 提升为事实。
13. 恢复时读取 append-only series/round state：`RESERVED` 且没有 begun-attempt receipt 才能由同一 request 安全开始；存在 begun receipt 但无法证明 `thread.started` 未发生时保守转 `TERMINAL_FAILED/AMBIGUOUS_PRESTART_CRASH`。`PRESTART_RETRYABLE` 只有**最新 attempt**有完整 prestart-failure receipt、没有 unresolved begun receipt、count < 3 且 repair 有效时才可恢复；否则同样 terminal。`STARTED_CONSUMED` 只尝试重验已落盘的完成产物；若不能证明已完整归档则转 terminal failure，绝不重放。`COMPLETED`/`TERMINAL_FAILED` 分别幂等返回同一 Candidate/failure。不同 request 在任何状态都冲突。

#### 模型角色

无新增角色。M8 只编排 M5 的受控 Adapter；模型不渲染 Markdown、不创建 ID/SHA/locator/receipt/Trace，也不决定恢复。

#### 具体输出、Trace 与回执

目标合同不固定长期文件数量；一个可恢复布局可以是：

```text
document.md
candidate.json
candidate-series.jsonl
repository-business-model.json
nine-section-document.json
evidence-capsules.json
proof-pack.json
proofs.jsonl
trace.jsonl
generation-receipts.jsonl
validation-receipts.jsonl
```

以下 sidecar 都是 **SYNTHETIC DESIGN EXAMPLE / EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED**。其中 identity 与 SHA 是明确标注的说明值：

```json
{
  "canonicalRequestId": "canonical-request:generate-illustrative-not-calculated",
  "candidateId": "candidate:round1-illustrative-not-calculated",
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "documentSha256": "1111111111111111111111111111111111111111111111111111111111111111",
  "exampleOnly": true,
  "findingIds": [],
  "hashMeaning": "ILLUSTRATIVE_NOT_CALCULATED",
  "parentCandidateId": null,
  "readerDocument": "document.md",
  "readerCandidateRound": 1,
  "roundSlotState": "COMPLETED",
  "seriesId": "series:illustrative-not-calculated",
  "status": "UNPUBLISHED_CANDIDATE"
}
```

若上述 ImprovementRequest 成功，Round 2 sidecar 的 lineage 只能是：

```json
{
  "canonicalRequestId": "canonical-request:improvement-illustrative-not-calculated",
  "candidateId": "candidate:round2-illustrative-not-calculated",
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "exampleOnly": true,
  "findingIds": ["finding:reader-order-001", "finding:term-usage-002"],
  "parentCandidateId": "candidate:round1-illustrative-not-calculated",
  "readerCandidateRound": 2,
  "roundSlotState": "COMPLETED",
  "seriesId": "series:illustrative-not-calculated"
}
```

每条 generation/validation/recovery receipt 都重复这四个 lineage fields。`candidate-series.jsonl` 为 Round 1 与 Round 2 分别保存同一状态机；下面是 Round-2 slot 在原子 reservation 刚完成时的 **shape example**，不是本 walkthrough 实际持久化的记录：

```json
{
  "canonicalRequestId": "canonical-request:improvement-illustrative-not-calculated",
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "exampleOnly": true,
  "findingIds": ["finding:reader-order-001", "finding:term-usage-002"],
  "improvementRequestId": "improvement:illustrative-not-calculated",
  "lastPrestartFailureReceiptId": null,
  "parentCandidateId": "candidate:round1-illustrative-not-calculated",
  "prestartAttemptCount": 0,
  "readerCandidateRound": 2,
  "roundSlot": "series:illustrative-not-calculated/reader-round-2",
  "seriesId": "series:illustrative-not-calculated",
  "slotState": "RESERVED",
  "startedReceiptId": null
}
```

后续状态变化只追加 transition receipt，不原地改写历史；例如同一 request 的第一次 prestart failure 与修复后 started event 的 machine shape 是：

```json
{"canonicalRequestId":"canonical-request:improvement-illustrative-not-calculated","event":"PRESTART_FAILED_CONFIRMED_NO_THREAD_STARTED","exampleOnly":true,"findingIds":["finding:reader-order-001","finding:term-usage-002"],"fromState":"RESERVED","parentCandidateId":"candidate:round1-illustrative-not-calculated","prestartAttemptCount":1,"readerCandidateRound":2,"repairReceiptId":null,"seriesId":"series:illustrative-not-calculated","toState":"PRESTART_RETRYABLE"}
{"canonicalRequestId":"canonical-request:improvement-illustrative-not-calculated","event":"thread.started","exampleOnly":true,"findingIds":["finding:reader-order-001","finding:term-usage-002"],"fromState":"PRESTART_RETRYABLE","parentCandidateId":"candidate:round1-illustrative-not-calculated","prestartAttemptCount":2,"readerCandidateRound":2,"repairReceiptId":"repair:illustrative-not-calculated","seriesId":"series:illustrative-not-calculated","toState":"STARTED_CONSUMED"}
```

两条 canonical Trace JSONL 记录分别把事实句和 admitted registry term 回到 Fact/meaning 与源码；事实 Trace 引用完整 ProofPack，而 `displayEvidence` 只是方便人工定位的语义 span，不代替 dependency closure。这些内部字段不会出现在读者正文：

```json
{"atomIds":["A10","A11"],"displayEvidence":[{"locator":"src/main/java/example/inventory/ReservationService.java:13"}],"itemKey":"business-activity.insufficient-inventory","proofIds":["proof:A10","proof:A11"],"proofPackId":"proof-pack:reservation","section":"业务活动","templateId":"CONDITIONAL_THROW_V1","traceKind":"FACT_SENTENCE"}
{"basisAtomIds":["A17","A18"],"businessTermKey":"TERM_UPDATE_COUNT_NOT_ONE_OUTCOME","flowSliceId":"flow:reservation","itemKey":"outcome.update-count-term","meaningId":"meaning:update-count-exception","section":"业务活动","traceKind":"ADMITTED_TERM"}
```

生成回执区分配置与观察身份；本例没有真正调用 Provider，所以示例明确保持未执行状态：

```json
{
  "configuredAuthMode": "CODEX_LOGGED_IN_SESSION",
  "configuredModelAdapter": "codex-flow-interpreter",
  "canonicalRequestId": "canonical-request:generate-illustrative-not-calculated",
  "exampleOnly": true,
  "executionState": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "expectedModel": "gpt-5.6-luna",
  "expectedReasoningEffort": "xhigh",
  "findingIds": [],
  "observedModel": null,
  "observedReasoningEffort": null,
  "observedSandbox": null,
  "observedUpstreamProvider": null,
  "parentCandidateId": null,
  "prestartAttemptCount": null,
  "readerCandidateRound": 1,
  "seriesId": "series:illustrative-not-calculated",
  "slotState": null
}
```

本 walkthrough 没有运行，因此 validation receipt 只能给出预期门禁，不能伪装成 PASS 或实际摘要匹配：

```json
{
  "canonicalRequestId": "canonical-request:generate-illustrative-not-calculated",
  "candidateId": "candidate:round1-illustrative-not-calculated",
  "checks": {
    "atomDisposition": "EXPECTED_NOT_EXECUTED",
    "factProofClosure": "EXPECTED_NOT_EXECUTED",
    "businessTermRegistry": "EXPECTED_NOT_EXECUTED",
    "meaningDisposition": "EXPECTED_NOT_EXECUTED",
    "modelEvidenceProjection": "EXPECTED_NOT_EXECUTED",
    "nineSectionCardinality": "EXPECTED_NOT_EXECUTED",
    "proofDependencyClosure": "EXPECTED_NOT_EXECUTED",
    "roundSlotStateMachine": "EXPECTED_NOT_EXECUTED",
    "snapshotReverification": "EXPECTED_NOT_EXECUTED",
    "technicalDisplayRegistry": "EXPECTED_NOT_EXECUTED",
    "traceClosure": "EXPECTED_NOT_EXECUTED"
  },
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "exampleOnly": true,
  "findingIds": [],
  "parentCandidateId": null,
  "prestartAttemptCount": null,
  "readerCandidateRound": 1,
  "seriesId": "series:illustrative-not-calculated",
  "slotState": null,
  "valid": null
}
```

#### 下游后置条件与能力要求

下游 Selection 只得到不可变 Candidate reference、验证回执和 TraceView；它不能看到可变 staging 或诊断输出。Candidate 仍是未发布提案，只有显式 Selection 才能进入共享合同的冻结链。CLI、Java 与 future local-loopback HTTP Adapter 必须产生逐字节相同的核心 artifact；upstream Capture 只负责交付冻结输入，不是第四个分析入口。

#### 支持范围、失败与 Gap

支持 Java Interface、离线 CLI、recorded test Adapter、显式 Reader Candidate improvement 和 future local-loopback HTTP Adapter；HTTP 不增加分析能力。来源/ProofPack/model projection/reader/identity/archive/Trace 失败或 improvement 扩张冻结基座均阻止 Candidate。带完整 provenance 的 Gap 问题可以随文档披露，但 `STARTED_CONSUMED` 后失败、运行身份不匹配或无法恢复 lineage 是 fatal。只有明确的 `PRESTART_RETRYABLE` 且 attempt count < 3 可以经有效 repair 恢复同一 slot。诊断 `inspect/discover/analyze` 输出未绑定 VerifiedSnapshot 时永远不能冒充 Candidate 输入。

#### 验证与测试策略

端到端合成测试覆盖相同 request 的 Java/CLI 字节一致、canonical JSON、ProofPack/Capsule 分离归档、原子 archive、幂等、冲突、tamper、fresh-process validate/trace、Fact/term/fallback-meaning lineage、snapshot 重验和崩溃恢复。`AssuranceLedger` schema tests 拒绝 fraction string、缺任一 expected/observed field、未执行却填 observed 数值或 denominator 非法。对 Round 1/2 共用的 slot fixture 枚举：absent→`RESERVED`；第一次/第二次可证明 prestart failure→`PRESTART_RETRYABLE`；有效 repair 只恢复同 slot 并递增 count；第三次 prestart failure→`TERMINAL_FAILED`；`thread.started` 原子→`STARTED_CONSUMED`；started 后成功→`COMPLETED`、任意失败→`TERMINAL_FAILED`；同 request 在 completed/failed 状态幂等读回；不同 request 全状态冲突；`RESERVED` begun-attempt crash 及无完整 started 状态保守 terminal；`STARTED_CONSUMED` crash 不重放。`improveCandidate` tests 还覆盖 Round1 parent/null、唯一 Round2 slot、相同 request 返回同一 Round2、不同 finding set 被拒绝、Round2 作为 parent 被拒绝、round 3 Schema 不可表达、concurrent create-if-absent 只有一个 winner，以及 snapshot/scope/fact/Proof/flow/registry/policy 扩张拒绝。未来 local-loopback HTTP Adapter 用 contract test 复用同一 interface fixture。测试不访问网络、模型或客户构建。

#### 为什么这个组合足够深

四个公共方法隐藏八模块顺序、ProofPack/Capsule 双链、append-only Candidate series/唯一 Round2 slot、持久状态机、Adapter、渲染、canonicalization、identity、archive、Trace、验证和恢复。删除 M8 会让每个入口重复并可能绕过“两份上限”或误重放 consumed round；保留这个 seam 则让新 Adapter 只翻译请求/响应，核心行为一次实现、所有入口受益，具备最高的 leverage 与故障 locality。

## 4. 可行性证明与可重算 AssuranceLedger

### 4.1 Capability-envelope theorem

**能力包络定理：** 给定一个通过 M1 的冻结仓库 `S`，若所有从声明入口可达、会影响文档语义的构造都属于版本化能力包络 `E`，所有需要唯一语义的 binding 在 `E` 内唯一，M1–M8 的确定性实现与验证器满足各自合同，且模型只贡献通过 M5 admission 的 typed term/claim keys，那么输出 Candidate 的每个 factual reader sentence 都由版本化 template 从 Proven atoms 确定性渲染，业务术语只来自冻结 BusinessTermRegistry 的 term slots、无业务 term 时显示只来自 total TechnicalDisplayRegistry，并能经 Trace/ProofPack 回到 `S` 中支持它的完整语义与编译依赖字节；所有已发现的支持范围入口、Flow、Outcome、候选事实原子、admitted meanings 和 Gap 都有显式处置。

本定理的假设是：

- `S` 包含本次请求声称要分析的完整冻结文件集，摘要算法和 canonicalization 正确实现；
- Java/XML/SQL parser 对 `E` 的语法语义实现正确，binding 和 CFG 规则没有实现 bug；
- `E` 明确排除动态行为，任何可达的包络外构造被检测并登记，不被静默忽略；
- Proof 规则是 sound 的：通过只表示完整 `ProofDependencyClosure` 蕴含所声明原子；package/import/type/receiver/config/namespace/resultType/statement edges 显式存在于 ProofPack，不以启发式名称相似或模型 excerpt 代替；
- M4 的 ProofPack validator 与 model Evidence projection validator 正确且彼此独立：小 Capsule 不被当作完整 Proof closure，完整 ProofPack 也不会泄漏进模型 prompt；
- M5 admission 校验器正确拒绝无 basis、越界 key 和 factual free-form prose；BusinessTermRegistry 是冻结且经过审阅的，TechnicalDisplayRegistry 对支持 anchors total/唯一，open label/gloss 不会进入自动正文；
- M7 的 `ReaderSentenceTemplate`/`BusinessTermSlot`/`TechnicalDisplaySlot` 不含开放 factual-text/term slot，并正确区分 Proven atom、registry term、technical fallback 与 Gap；
- M7/M8 的 atom/meaning disposition、render、identity、archive 与双 lineage Trace validator 正确实现。

这不是对任意 Java 程序的完备性定理，也不声称静态分析等价于生产运行。

### 4.2 前置/后置条件链

可行性来自可机械检查的组合，而不是“模型应该能理解”：

1. M1 把显式冻结请求变成 `VerifiedSnapshot`，所以 M2 的所有字节可重复读取。
2. M2 只在 `E` 内输出唯一 binding 的 `RepositoryModel`，显式闭合 config/namespace/interface、resultType/FQN/record component、receiver/method/parameter，并把包络外内容列入 `CapabilityReport`，所以 M3 不必猜测语义。
3. M3 先登记每个 candidate atom，再把它处置为 admitted-with-Proof 或 rejected-with-reason；每个 admitted atom 引用包含完整传递 binding edges 的 ProofPack；独立 GapExpectationProfile 产生有搜索 provenance 的问题，所以失败 atom、隐含编译 edge 和未知政策都不能静默消失。
4. M4 把入口编译为一条 Flow、内嵌全部四个支持范围 Outcome，先重验 ProofPack，再独立生成一个非重叠、模型最小 Capsule；所以 M5 既不漏终点，也不能看到无关仓库/Proof 编译材料，而事实强度不依赖 Capsule 大小。
5. M5 的一个任务只准入有 typed basis 的 BusinessTerm/ClaimVocabulary key；DROP 和 NEEDS_EVIDENCE 不成为事实，任意 open label/gloss 不进入 reader AST；无 eligible term 时由 total TechnicalDisplayRegistry 提供确定性 fallback，所以 M6 合并的是有类型、有 registry/fallback lineage 的知识。
6. M6 以技术 anchor 归并并守恒一条 Flow、四个 Outcome、所有 Fact/term-meaning/Gap，所以 M7 能为 atom 与 meaning 分配唯一 owner。
7. M7 证明九章、atom/meaning/Gap disposition 完整，并用固定 factual template + atom slots、frozen term registry + term slots 或 total technical fallback slots 建 reader AST，所以 M8 不需要再次推断或接受模型事实/术语 prose。
8. M8 重验 ProofPack/Capsule 双链、最多两份的 Candidate series、原子归档并提供 Fact/term-meaning 双 Trace，所以下游拿到的 Markdown 与被验证的计划、事实、完整 Proof、解释 lineage 和冻结字节是同一 Candidate lineage。

任一后置条件失败，链在该点停止；不存在“后面补写后继续”的旁路。因此，在定理假设和能力包络内，方案逻辑上可实现且可逐模块测试。

### 4.3 保证、解释与不可证明内容

| 类别 | 本例结论 |
| --- | --- |
| 确定性保证 | 在一次真实运行门禁通过后：声明文件身份；`POST /reservations`；3 个 Java 跨层直接调用全部唯一绑定、2 个 Mapper statement 全部唯一绑定；Mapper config/namespace/interface；`resultType FQN → Java package/InventoryRow declaration`；SELECT aliases 到该精确 type 的 record components；以及 `SELECT version → InventoryRow.version → findBySku return → Service InventoryRow local → inventory.version() call argument → receiver/method/Mapper version parameter → UPDATE version predicate` 的完整 loaded-version lineage；数量/库存/更新 guards；静态 SQL 赋值与谓词；candidate atom accounting 与 admitted ProofPack closure；1 个入口、一条 Flow 与 4 个 Outcome 全部覆盖；模型 Evidence projection、模板化事实句、九章与双 Trace 闭包。本 synthetic walkthrough 只列预期值，没有执行这些检查。 |
| 受支持模型解释 | 模型在七个 eligible `businessTermKey` 中的选择；“库存预留”“预留请求”“库存记录”“增加预留数量并递增版本”等显示值来自冻结 registry，具有 term/atom lineage，但不是编译器定理。有限 claim keys 可验证 claim registry grounding；没有 eligible business term 时显示来自 total TechnicalDisplayRegistry，不是模型提案；open label/gloss 只在 sidecar 等待后续 registry 审批，不进入自动正文。 |
| 静态代码不能证明 | 数据库隔离与触发器、生产配置、外部调用者是否重试、业务是否完整、预留过期/释放、多仓政策、单位换算和 missing-SKU 企业响应。 |

### 4.4 AssuranceLedger 定义

不使用模型自报概率，也不生成“92%”之类的假标量。`AssuranceLedger` 是一组带分子、分母、排除项和 finding 的可重算门禁。所有覆盖门禁使用同一个 `TypedCoverageMetricV1`：`expectedNumerator`、`expectedDenominator` 是设计 fixture 的非负整数，`observedNumerator`、`observedDenominator` 是运行后整数；未执行时两个 observed 字段都必须为 `null`。JSON 不存拼接后的 fraction string，也不把 expected 值复制到 observed；展示比例只能由消费者在 observed 两字段非 null 且 denominator 合法时计算。

- **source integrity** = 摘要/大小/路径全部重验通过的声明文件数 ÷ 声明文件总数；任何必需文件失败即 fatal。未执行 walkthrough 的 matched numerator 必须为 `null`，不能填预期值冒充重验。
- **supported-scope coverage** = 能力包络内已分析的可达语义 site 数 ÷ 检测到的可达语义 site 数；包络外 site 单列，不能从分母消失。
- **exact Java cross-layer call binding** = 唯一绑定的 Java 跨层直接调用数 ÷ 需要绑定的 Java 跨层直接调用数；歧义不能算成功。
- **exact Mapper-statement binding** = 唯一绑定的 Mapper method→XML statement 数 ÷ 需要绑定的 Mapper 调用数；与 Java call 分母分开。SELECT result mapping 是 Formula Proof 的显式依赖，不拿来混大这两个分母。
- **candidate fact-atom accounting** = candidate atoms = admitted atoms + rejected atoms，且每个 rejected atom 有 reasoned disposition；这是防止失败 atom 消失的独立门禁。
- **fact-atom proof closure** = 有闭合 Proof 的 admitted Fact atom 数 ÷ admitted Fact atom 总数；必须 100%，但只是一个子门禁，不能替代 candidate accounting。
- **ProofDependency closure** = 已重验的必需 ProofPack dependency node/edge 数 ÷ 声明为必需的 node/edge 数；resultType/FQN、receiver、config/namespace 等都在分母内。它与 admitted atom closure 分开报告。
- **model Evidence projection minimality** = 通过独立删除检查的必需 `modelEvidenceSpans` 数 ÷ projection policy 声明的 span 数；它证明模型包紧凑，不证明 Fact，不能替代 ProofDependency closure。
- **discovered-entry/flow/outcome coverage** = 已编译入口 ÷ 已发现入口、形成的入口根 Flow 数，以及已处置 Outcome ÷ 已发现的支持范围 Outcome；三个概念分别报告，Outcome 不算 Flow。
- **interpretation grounding/lineage** = claim-registry-grounded admitted structured claim keys ÷ admitted structured claim keys；另报 frozen-registry-resolved 且有 stable ID/Flow/term/basis 的 admitted meanings 数 ÷ admitted meanings 总数，以及有唯一 technical fallback resolution 的具体 anchors 数 ÷ 具体 supported anchors 总数。本 fixture 的后一分母是 9：Flow、Request、Record、Result、Activity 和 4 个 Outcomes。另外单独报告 6 种 supported anchor kind 是否各有唯一 policy；这是 policy-kind coverage，不是具体 anchor resolution totality。term 分子证明 key eligibility、registry resolution 与 lineage，不把业务术语升级为源码定理；fallback 分子只证明确定性显示 totality；open text 永不进入分母。所有 proposal 的 KEEP/NARROW/DROP/NEEDS_EVIDENCE disposition 另报。
- **Gap provenance** = 有 expectation、trigger、searched scope、observation/absence evidence、reason 和 question-template identity 的 Gap 数 ÷ emitted Gap 总数；它证明问题由受控搜索产生，不证明政策不存在。
- **section atom disposition** = 已分配正文、技术依据、Gap 或 reasoned exclusion 的 Fact atom 数 ÷ admitted Fact atom 总数；Gap ownership 另算。
- **interpretation disposition** = 有唯一 section owner、rendered term item、business term key 和 basis 的 admitted meanings 数 ÷ admitted meanings 总数。
- **Trace closure** = 分别报告能从 factual item 经 Fact/ProofPack dependency edges 回到已重验 locator 的 Fact atoms，以及能从 business term/technical fallback item 经 meaning 或 fallback policy、Flow/basis atoms 回到同一 ProofPack 的显示 lineage；两个分母都必须闭合。

### 4.5 SYNTHETIC 样例账本

```json
{
  "candidateFactAtomAccounting": {"expectedAdmittedAtoms": 20, "expectedCandidateAtoms": 20, "expectedRejectedAtoms": 0, "observedAdmittedAtoms": null, "observedCandidateAtoms": null, "observedRejectedAtoms": null, "rejectedAtomDispositionCount": {"expectedCount": 0, "observedCount": null}, "rejectedAtomDispositions": []},
  "designWalkthroughStatus": "EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED",
  "discoveredEntryFlowOutcomeCoverage": {
    "entries": {"expectedDenominator": 1, "expectedNumerator": 1, "observedDenominator": null, "observedNumerator": null},
    "flowSlices": {"expectedDenominator": 1, "expectedNumerator": 1, "observedDenominator": null, "observedNumerator": null},
    "outcomePaths": {"expectedDenominator": 4, "expectedNumerator": 4, "observedDenominator": null, "observedNumerator": null}
  },
  "exactJavaCrossLayerCallBinding": {"expectedAmbiguous": 0, "expectedDenominator": 3, "expectedNumerator": 3, "observedAmbiguous": null, "observedDenominator": null, "observedNumerator": null},
  "exactMapperStatementBinding": {"expectedAmbiguous": 0, "expectedDenominator": 2, "expectedNumerator": 2, "observedAmbiguous": null, "observedDenominator": null, "observedNumerator": null},
  "factAtomProofClosure": {"expectedDenominator": 20, "expectedNumerator": 20, "observedDenominator": null, "observedNumerator": null},
  "gapProvenance": {"expectedDenominator": 5, "expectedNumerator": 5, "observedDenominator": null, "observedNumerator": null},
  "interpretation": {
    "businessTermRegistryResolution": {"expectedDenominator": 7, "expectedNumerator": 7, "observedDenominator": null, "observedNumerator": null},
    "meaningTermLineage": {"expectedDenominator": 7, "expectedNumerator": 7, "observedDenominator": null, "observedNumerator": null},
    "openTextAutoRendered": {"expectedCount": 0, "observedCount": null},
    "proposalDisposition": {"expected": {"DROP": 1, "KEEP": 6, "NARROW": 1, "NEEDS_EVIDENCE": 1}, "observed": {"DROP": null, "KEEP": null, "NARROW": null, "NEEDS_EVIDENCE": null}},
    "structuredClaimRegistryGrounding": {"expectedDenominator": 3, "expectedNumerator": 3, "observedDenominator": null, "observedNumerator": null},
    "technicalDisplayPolicyKindCoverage": {"expectedDenominator": 6, "expectedNumerator": 6, "observedDenominator": null, "observedNumerator": null},
    "technicalDisplayResolutionTotality": {"expectedDenominator": 9, "expectedNumerator": 9, "observedDenominator": null, "observedNumerator": null}
  },
  "interpretationDisposition": {"expectedDenominator": 7, "expectedNumerator": 7, "observedDenominator": null, "observedNumerator": null},
  "loadedVersionProofDependencyClosure": {
    "edges": {"expectedDenominator": 23, "expectedNumerator": 23, "observedDenominator": null, "observedNumerator": null},
    "nodes": {"expectedDenominator": 16, "expectedNumerator": 16, "observedDenominator": null, "observedNumerator": null}
  },
  "metricSchemaVersion": "TYPED_COVERAGE_METRIC_V1",
  "modelEvidenceProjectionMinimality": {"expectedDenominator": 6, "expectedNumerator": 6, "observedDenominator": null, "observedNumerator": null},
  "namedGaps": ["reservation expiration/release", "multi-warehouse scope", "unit conversion", "retry policy", "missing-SKU policy"],
  "sectionAtomDisposition": {"expectedDenominator": 20, "expectedNumerator": 20, "expectedReaderBody": 20, "expectedTechnicalBasis": 0, "expectedUnassigned": 0, "observedDenominator": null, "observedNumerator": null, "observedReaderBody": null, "observedTechnicalBasis": null, "observedUnassigned": null},
  "sourceIntegrity": {"digestMatches": null, "expectedDenominator": 6, "expectedNumerator": 6, "observedDenominator": null, "observedNumerator": null},
  "supportedScopeCoverage": {"expectedDenominator": 13, "expectedNumerator": 13, "expectedUnsupported": 0, "observedDenominator": null, "observedNumerator": null, "observedUnsupported": null},
  "traceClosure": {
    "factAtoms": {"expectedDenominator": 20, "expectedNumerator": 20, "observedDenominator": null, "observedNumerator": null},
    "meanings": {"expectedDenominator": 7, "expectedNumerator": 7, "observedDenominator": null, "observedNumerator": null}
  }
}
```

这份 JSON 是 `EXPECTED_DESIGN_WALKTHROUGH_NOT_EXECUTED`，不是一个 passing receipt；illustrative hashes 也没有被实际匹配。未来一次真实运行中，只有所有独立分子/分母和 fatal gates 重算通过，才能称为“passing ledger”。即便通过，它也只表示：在声明的能力包络、冻结字节和检测到的入口范围内，Candidate 对源码可审阅、可逐原子追溯且没有静默丢失。它不表示与生产运行等价，不证明数据库实际影响行数、外部事务/重试、未部署代码、企业政策或整个组织的业务完整性。

## 5. SYNTHETIC 样例的完整九章 Markdown 结果

以下是目标 M7/M8 对该 fixture 应确定性产生的**完整正文**，由本设计 walkthrough 明确写出，并非产品运行生成。它恰好使用共享合同规定的九个 H2。内部 Fact/Evidence ID、SHA、Provider/transport enum、prompt、receipt 和源码 locator 均不进入正文；这些信息只出现在前述 sidecar/Trace 示例中。

---

# 库存预留代码说明

## 文档说明

本文说明当前冻结代码范围内可确认的一个入口过程。条件、数值、调用、更新和结束方式来自静态代码证明，并由固定句式呈现；“库存预留”“预留请求”“库存记录”等短名称来自冻结业务术语表中通过 anchor/basis 校验的 term keys，只占据术语槽，不是模型自由文本，也不是源码对企业政策的证明。

调用入口：POST /reservations。请求体类型是 `ReservationRequest`；Controller 把请求中的 `sku` 和 `quantity` 交给 `ReservationService.reserve` 处理。

## 业务目标

本文用术语表中的“库存预留”称呼这个入口过程。它接收一个商品与数量，并按代码中的校验、库存读取、数量更新和结束条件完成一次处理；该术语只帮助阅读，不补充过期、仓库、单位或重试政策。

## 业务对象

- **预留请求**：技术类型为 `ReservationRequest`，包含本次传入的 `sku` 和 `quantity`。
- **库存记录**：Service 调用 `findBySku` 读取商品记录；静态 SQL 指向 `inventory` 表。
- **预留结果**：成功分支返回 `ReservationReceipt`，其中两个返回分量分别是本次 `sku` 和本次 `quantity`。

## 业务活动

1. 当 `quantity <= 0` 时，代码抛出 `InvalidQuantity`；术语槽显示“数量不合法”。
2. 数量通过检查后，代码读取库存记录并计算可用数量。
3. 当 `available < quantity` 时，代码抛出 `InsufficientInventory`。
4. 继续执行时，静态 UPDATE 执行 `reserved_qty = reserved_qty + quantity` 和 `version = version + 1`；术语槽显示“增加预留数量并递增版本”。
5. 当 `updateCount != 1` 时，代码抛出 `ConcurrentInventoryChange`；术语槽显示“更新记录数不为一”。该类名不证明生产中实际发生了并发竞争，也不证明外部调用方接收的响应形态。
6. 当 `updateCount == 1` 时，代码返回预留结果。

因此，这是一条入口根流程，内含四种结束路径：抛出 `InvalidQuantity`、抛出 `InsufficientInventory`、抛出 `ConcurrentInventoryChange`、返回 `ReservationReceipt`。

## 字段与维度

| 业务字段 | 作用 |
| --- | --- |
| `sku` | 查询谓词是 `WHERE sku = requested sku`；UPDATE 也包含同一个 `sku` 等值谓词。 |
| `quantity` | 作为正数校验、可用数量比较和 `reserved_qty` 增量的输入。 |
| `onHand` / `reserved` | 作为可用数量公式的两个分量。 |
| `version` | SELECT 的 `version` 绑定到库存记录的 `version`；Service 通过 `inventory.version()` 传给 Mapper 的 `version` 参数，UPDATE 再以 `version = loaded version` 作为谓词，并在赋值中加一。 |
| `updateCount` | 代码以是否等于一选择抛出异常或返回结果。 |

## 对象关系

`ReservationRequest` 的 `sku` 由 Controller 交给 Service；Service 通过 `findBySku` 定位 `inventory` 表对应的库存记录。满足代码条件时，同一 `sku` 与读取到的 `version` 用于 UPDATE；成功分支再用本次 `sku` 与 `quantity` 构造 `ReservationReceipt`。

## 指标口径

当前代码明确给出的计算口径是：**available = onHand − reserved**。该值来自本次读取的库存记录，并与请求的 `quantity` 比较。本文不从这一个静态公式推导跨商品、跨仓库或时间维度的汇总指标。

## 示例问题

- `quantity <= 0` 时，代码抛出哪一种异常？
- `available < quantity` 时，代码是否执行 UPDATE？
- UPDATE 怎样改变 `reserved_qty` 和 `version`？
- `updateCount != 1` 时，代码抛出哪一种异常？

## 待确认事项

- **预留过期与释放**：在本次搜索的受支持 Java methods 与 Mapper statements 中，观察到增加预留数量，但没有找到受支持的 expiry/release counterpart 证据。需确认预留何时失效、由谁释放以及怎样恢复数量。
- **多仓范围**：在入口参数、查询/更新参数和 SQL predicates 的搜索范围内，只观察到 `sku` 与 `version` keys，没有找到 warehouse key 证据。需确认企业库存是否要求仓库维度。
- **单位换算**：在 request/record fields、算术节点与 Mapper 参数的搜索范围内，观察到整数数量直接比较和累加，没有找到 unit field 或 conversion call 证据。需确认库存单位、申请单位与换算规则。
- **重试政策**：在该入口根受支持 CFG 中，观察到 `updateCount != 1` 后抛出异常，没有找到 loop、catch 或 reinvoke 证据。需确认调用方、事务层或其他基础设施是否以及如何重试。
- **商品不存在政策**：在 `findBySku` 返回到首次 dereference 的受支持 CFG 范围内，没有找到 null、Optional 或 missing-row branch 证据。需确认缺失商品记录时的错误、补建与责任边界。

---

## 6. 生成治理：冻结输入、两类 R1/R2 与停止规则

### 6.1 两种 round 不能混用

`FlowInterpretationRound` 是同一 reader candidate 内，对同一 Frozen Flow 的 R1 初始解释与 R2 精度复核；R2 不是新产品 Candidate。`ReaderCandidateRound` 才表示产品内容替换，并由 append-only Candidate series 结构约束：Schema 只有 `1|2`；每个 `seriesId` 恰有一个 Round-1 slot 和至多一个原子保留的 Round-2 slot；Round 1 的 `parentCandidateId=null`，Round 2 的 parent 必须是同 series 的 Round 1，Round 2 永远不能成为 parent。因而第三份 Candidate 不是“约定不生成”，而是在 Schema、parent predicate 和存储键上都不可表示。

### 6.2 一次产品生成单元的完整合同

在任何真实模型调用前必须声明并冻结：

- 输入：VerifiedSnapshot identity、CapabilityProfile、ProvenFactSet/完整 ProofPack/Gap、一条含四个 OutcomePath 的 FlowSlice、一个只含 `modelEvidenceSpans` 的完整过程 EvidenceCapsule、NineSectionProfile、ReaderSentenceTemplate registry、冻结 BusinessTermRegistry/TechnicalDisplayRegistry、task/prompt/Schema/runtime policy 版本，以及 `seriesId`、`readerCandidateRound`、`parentCandidateId` 和精确稳定排序的 `findingIds`。
- 输出：一份不可变、未发布 Candidate 目录，包括 `document.md`、canonical sidecar、ProofPack、model Evidence Capsule、Trace、Candidate-series/round slot state 和 generation/validation receipt；所有相关 sidecar/receipt 重复同一组 series lineage fields，slot receipt 另带 canonical request、state 与 prestart attempt count。
- 预计时间：由实现按 Flow 数、Capsule token 和 Provider policy 在运行计划中给出；未估算不得启动。
- 理想验收：来源/Schema/身份/candidate accounting/ProofDependency closure/model Evidence projection/入口-Flow-Outcome typed 覆盖/structured-claim grounding/BusinessTermRegistry resolution/TechnicalDisplay totality 与 term lineage/atom 与 meaning disposition/九章/双 Trace/series-round state machine 全部通过；所有 factual sentences 来自固定 template slots，所有自动正文术语只来自已冻结 business term 或 technical display registry；正文没有内部 token、模型 factual prose、open label、虚构政策或静默原子丢失；人工审阅没有内容 finding。
- Fatal：来源或任务漂移、candidate atom 消失、ProofPack edge 不闭合、把 model Capsule 冒充 Proof closure、未覆盖的支持范围 Outcome、模型越界或身份不匹配、非法 Schema、basis 扩张、factual prose/open-label 字段进入自动正文、未知/不合格 business term key、technical fallback 不 total/不唯一、未处置 atom/meaning、九章错误、Trace/归档不闭合、Candidate series/parent/round/slot state 违例，以及把 Gap 写成事实。
- 确定性验证：重验 AssuranceLedger 的 typed expected/observed 分子分母、ProofPack、model projection、两类 registry resolution、canonical bytes、identity、Trace、reader gates 和 slot state transitions；人工审阅只评价可读性与是否需要未来 registry addendum，不能批准一个 fatal 技术错误，也不能把 sidecar 中的 `HUMAN_REVIEW_REQUIRED` open candidate 直接送入当前自动正文。

### 6.3 Reader Candidate Round 1 与 Round 2

`generateCandidate` 先以 canonical generate request identity 原子建立一个 append-only series record 和该 `seriesId` 的唯一 Round-1 slot，初态 `RESERVED`、`prestartAttemptCount=0`。Round 1 固定 `readerCandidateRound=1`、`parentCandidateId=null`、`findingIds=[]`；相同请求按 persisted state 幂等恢复/读回，不同请求不能占用同一 Round-1 slot。Reader Candidate Round 1 优先事实正确、来源身份、必需覆盖、Schema、结构和 Trace。只有全部理想要求通过才是 `IDEAL`；所有 fatal 通过但仍有明确 non-fatal warning 时可成为 `REVIEWABLE_WITH_WARNINGS`，由人决定是否选择。

Reader Candidate Round 2 只能经 `improveCandidate(ImprovementRequest)` 显式启动，用于 Round 1 的命名 fatal finding，或用户明确批准的系统性可读性 finding。请求必须携带同一 `seriesId`、`readerCandidateRound=2`、精确 Round-1 `parentCandidateId`、`expectedParentCandidateId` 和精确去重排序的归档 `findingIds`。程序先验证 parent 是同 series 的 Round 1，再在任何 Provider 或 renderer 工作前以原子 create-if-absent 保留唯一 `seriesId/reader-round-2` slot，初态 `RESERVED`、count 0，并绑定 canonical request identity：相同 request 只按该 slot 的 persisted state 恢复、读回 Round 2 或 terminal failure；不同 request/finding set 遭 `ROUND_2_SLOT_ALREADY_CONSUMED`；Round 2 作为 parent 被拒绝；Schema 无 round 3。它冻结相同 snapshot、scope、candidate atoms、Facts/ProofPack/Gaps、Flow/Outcome/Capsule、policies、BusinessTermRegistry、TechnicalDisplayRegistry 和 template registry，只修复允许的有限 term-key selection/section presentation，不扩张证据基座或 registry。sidecar-only open candidate 即使经人审阅，也只能进入未来新冻结的 registry/addendum 和新 series，不能进入当前 Round 2 自动正文。如果 Round 1 有 fatal，Round 2 前**必须**先有一份有效的 Sol/ultra prompt diagnosis；若只有经用户批准的可读性 finding，则该 diagnosis 可选且最多一次。diagnosis 的输入仅为冻结任务、Round 1 Candidate 和命名 finding；输出是 receipt-bound corrective addendum，不是产品 Candidate，不能新增事实或削弱门禁。

Round 2 后：没有 fatal 才可继续 Selection；仍有 fatal 就停止、保留 Candidate 和回执、不得创建第三份 Candidate。非 fatal warning 必须完整保留供人审阅。

### 6.4 失败、重启与 Provider 身份

Round 1/2 共用 M8 的 persisted slot state machine。每次尝试前先对同一 canonical request/slot 原子递增 `prestartAttemptCount` 并写 begun receipt；模型调用前同时 preflight logged-in ChatGPT/Codex session、output JSON Schema 和 subprocess 初始化本地状态所需权限。只有完整 failure receipt 明确证明未收到 `thread.started` 时，count 1/2 才转 `PRESTART_RETRYABLE`；有效 repair/preflight receipt 可恢复**同一 slot**，count 不重置。第 3 次 prestart failure 转 `TERMINAL_FAILED`。prestart failure 不产出产品 Candidate，但会消耗一个 prestart attempt；绝不通过新 slot 规避上限。

收到 `thread.started` 时，slot 与 started receipt 原子转 `STARTED_CONSUMED`。之后空响应、非法 Candidate JSON、进程/模型失败、身份不匹配或内容质量问题都转 `TERMINAL_FAILED`，不得自动重试、切换 Provider 或回退 API key；成功原子归档后才转 `COMPLETED`。同一 request 对 `COMPLETED`/`TERMINAL_FAILED` 只读回原结果。`RESERVED` crash 只有在没有 begun-attempt receipt 时可安全开始；存在 begun receipt 但无法证明没有 started event 时保守 terminal。`PRESTART_RETRYABLE` 若最新 begun attempt 没有完整 failure event 也保守 terminal，不能借较早 failure receipt 重放。`STARTED_CONSUMED` crash 只可发现并重验已完整落盘 Candidate，否则 terminal，永不重放。回执必须分别记录 configured Adapter、configured Auth Mode、observed upstream provider、observed model、reasoning effort 和 sandbox，并重复 `canonicalRequestId`、`prestartAttemptCount`、`slotState`、`seriesId`、`readerCandidateRound`、`parentCandidateId`、`findingIds`；任一必需观察缺失或不匹配时，保留失败回执且不渲染该响应。

本文只是由获批的 Sol/ultra 设计作者重写设计文档；没有执行产品 Candidate 生成、live model Provider、源码 scanner、客户 Maven 或网络操作。

## 7. DepotHead POC：完整性通过仍可能语义失败

真实 POC 反例来自 jshERP 固定 commit `8c30ce7861570458920175e200bb2a6442713580` 的 DepotHead 小样。当前 Manifest 声明 3 个文件、5 个 Evidence、5 个 LockedFact 和 1 个 Flow。文件 size/SHA、五段摘录 SHA 和已知 ID reference 都通过，但独立逐原子审计只有 2 个 Fact 通过、3 个失败，因此 Flow semantic closure 失败，准确度出口 **NOT MET**，不得接受 Candidate。

| 声明 Fact | 独立审计 | 原因 |
| --- | --- | --- |
| HTTP 入口 | FAIL | Controller `178-191` 有 POST 和 route suffix，但完整路径所需类级 `/depotHead` 在 line 43，Fact 自己未引用。 |
| 反审核资格 | PASS | Service `750-776` 内包含 requested/current/purchase 状态和失败异常。 |
| 审核与库存条件 | FAIL | Service `779-811` 缺 required current 状态和失败异常；相关字节在另一个 span，不能借用。 |
| 状态持久化请求 | FAIL | 声明 Service `813-839` 主要是日志/下一方法；Mapper 调用、accepted IDs 和 status 赋值实际在 `798-803`。 |
| 状态列映射 | PASS | Mapper `385-489` 支持 update、表、status 列与 record.status 映射。 |

这个反例证明：hash/reference integrity 只回答“锁定的字节是否改变、引用是否存在”，不回答“这些字节是否支持声明语义”。Candidate identity、九章标题或可查询 locator 同样不能补足失败的 Proof。详细当前记录见 [00 POC 实现记录](docs/stages/00-mvp.md)。

## 8. 附录 A：稳定 ARCH 身份到 M1–M8 的映射

稳定 ARCH identity 只为兼容现有阶段文档和引用保留；新的设计讨论应优先使用 M1–M8 的语义模块。

| 稳定 identity | 新模块位置 | 关系 |
| --- | --- | --- |
| ARCH-01 FrozenInputModule | M1 | 完整归入冻结与验证。 |
| ARCH-02 CapabilityScopeModule | M2 | 与仓库理解合并；能力报告仍是独立输出。 |
| ARCH-03 RepositoryAnalysisModule | M2 | Java/config/XML/SQL、symbol/call binding 归入同一仓库模型。 |
| ARCH-04 FactProofModule | M3 | 完整归入 Fact/Proof/Gap 门禁。 |
| ARCH-05 FlowCompilationModule | M4 | 与最小证据编译共享 Flow dependency graph。 |
| ARCH-06 EvidenceTraceModule | M4 + M8 | Capsule 编译在 M4；最终 reader Trace、重验和归档在 M8。 |
| ARCH-07 FlowInterpretationModule | M5 | 模型解释与任务运行策略。 |
| ARCH-08 KnowledgeAdmissionModule | M5 + M6 | per-flow admission 在 M5；repository knowledge merge 在 M6。 |
| ARCH-09 NineSectionAssemblyModule | M7 | 完整归入九章计划与 atom disposition。 |
| ARCH-10 CandidateStoreModule | M8 | 渲染、identity、不可变 archive、validation/recovery。 |
| ARCH-11 CodeToMarkdownAgent | M8 | Java/CLI/future local-loopback HTTP Adapter 共用一个 orchestration interface。 |
| ARCH-12 SafetyBoundaryModule | M1–M8 横切 | 安全、资源与不执行客户代码不是顺序产物。 |

## 9. 附录 B：当前 POC 成熟度矩阵

以下矩阵只描述当前实现，不表示目标 M1–M8 已落地。`PARTIAL` 表示存在经过验证的局部行为或 seam，但没有达到完整端到端保证；`POC_ONLY` 表示只有临时人工机制；`NOT_IMPLEMENTED` 表示尚无对应目标产物。详细事实由 [00 POC 实现记录](docs/stages/00-mvp.md) 维护。

| 架构模块 | 当前状态 | 已验证内容 | 缺失内容 | 详细阶段文档 |
| --- | --- | --- | --- | --- |
| ARCH-01 冻结仓库验证 | PARTIAL | 声明文件 size/SHA、Evidence 行段摘要和已知引用可重验；漂移在 provider 前失败。 | 完整 VerifiedSnapshot、统一 Java/CLI symlink policy、祖先链接规则、exact-field Schema、完整 origin identity。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-02 能力与范围识别 | PARTIAL | CLI 暴露 WALKING_SLICE_V0；目录诊断能报告部分文件/route/gap。 | 与 Candidate 绑定的 CapabilityProfile 和逐文件范围台账。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-03 代码与 SQL 分析 | PARTIAL | RepositoryDiscoverer 与 SourceAnalyzer 可诊断部分 Spring MVC、直接调用、MyBatis 绑定、静态 UPDATE 和 Java 条件。 | Symbol Solver/完整控制流与数据流、冻结输入绑定、自动 Candidate 接线。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-04 CodeFact/Proof/Gap | PARTIAL | 有 CodeFact、ConditionFact、Gap seam；POC 可解析 LockedFact。 | 完整 Proof、逐原子 semantic Evidence closure、Fact 自动准入；DepotHead 当前 3/5 失败。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-05 业务流程切分 | POC_ONLY | 可读取人工 Flow Manifest 中的 Flow 与 allowlist。 | 自动入口到终端 FlowSlice 编译、多 Flow 覆盖闭包。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-06 EvidenceCapsule 与 Trace 基础 | PARTIAL | 生成前重验摘录，计算 taskSpecId/capsuleId，归档 Evidence/locator 与 Trace index。 | 语义闭合 Capsule、归档后重开源码与摘要重验、完整 Fact/Proof/Item closure。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-07 单流程模型解释 | PARTIAL | scripted/recorded R1/R2 allowlist、proposal 完整性和 basis 收窄有直接测试；runtime receipt 有独立 seam。 | live Adapter、生成链接线、完整 Schema、Adapter/Auth/upstream provider 分离。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-08 程序准入与仓库知识 | NOT_IMPLEMENTED | 仅有局部 R1/R2 admission 结果供当前 renderer 使用。 | 统一解释准入、跨 Flow 知识组装、owner/冲突与语义原子台账。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-09 NineSectionPlan | PARTIAL | renderer 可确定性输出精确九个 H2。 | 通用 NineSectionPlan、章内知识类型、原子守恒和 reader gate。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-10 Candidate 与不可变归档 | PARTIAL | Candidate identity、document.md 加七个 JSON sidecar、原子安装、逐字节幂等、冲突拒绝和 fresh-process validate/trace。 | 完整 sidecar/源码/语义重验、追加 validation/runtime lineage、长期身份覆盖。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-11 公共 Interface 与 Adapter | PARTIAL | Java Interface 有 generate、generateBaseline、validate、trace；CLI 有六个离线命令。 | 目标 `generateCandidate`/`improveCandidate` 合同、future local-loopback HTTP Adapter、完整 Provider/存储 Adapter 和诊断到准入 seam；远程 Git Capture 属于另一个上游 workflow，不是本 core 的缺失分析能力。 | [00 POC 实现记录](docs/stages/00-mvp.md) |
| ARCH-12 安全与不执行客户代码 | PARTIAL | 当前模块无客户执行路径；MyBatis parser 禁用外部 DTD/entity/schema/network；walker/archive 有局部 symlink 防护。 | 统一路径/symlink 安全合同、live model sandbox 与更广 Adapter 的端到端验证。 | [00 POC 实现记录](docs/stages/00-mvp.md) |

### POC 临时术语与目标模块

| POC 术语 | 当前用途 | 目标替代 |
| --- | --- | --- |
| Flow Manifest | 人工列出文件、Evidence、LockedFact 和 Flow allowlist。 | M1–M4 自动产生的 VerifiedSnapshot、ProvenFactSet、FlowSlice 和 EvidenceCapsule。 |
| LockedFact | 只做当前结构/hash/reference 校验的人工事实载荷。 | M3 每个 candidate atom 有 admitted-with-Proof 或 rejected-with-reason 处置；受控未知政策另由 GapExpectationProfile 产生 Gap。 |
| provider-free baseline | 隔离 renderer/archive 的无模型测试路径。 | 不是目标旁路；目标单一主线在解释缺失时登记 Gap。 |
| recorded R1/R2 | 测试 allowlist、proposal 完整性与 basis 收窄。 | M5 的受冻结任务和运行身份约束的 FlowInterpretationRound。 |
| eight-file archive | POC 固定的一份 Markdown 与七个 JSON sidecar。 | M8 按 Candidate/Trace/不可变/追加 lineage 合同归档，不固定长期文件数。 |
