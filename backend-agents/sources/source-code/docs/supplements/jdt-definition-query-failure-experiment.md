# 两处 JDT 定义查询失败：定向实验

日期：2026-09-28～29。范围：核对造成七个入口未收集的查询、修正适配器的超时缓存，再重验七个入口并采集慢等待。实验记录保持各轮原结果；不重建正式索引。

## 结论

**缓存修正后，第一轮七入口真实重验6个返回实验context、1个首次等待超时；该失败入口随后在新会话单独重验成功，但最后一次带诊断的重验又超时。** 七个入口均有本轮保存的材料，但不是同一会话一次全部通过，第一入口仍不稳定。两次新慢请求分别为50.629秒和52.308秒，最终均正常返回。后续入口已能使用迟到结果，不再重复抛出旧超时。两份JFR均将慢请求定位到JDT定义查找之前的文档生命周期任务等待；具体后台任务调度根因仍未确认。详见本页“修正后的七入口真实重验”。

修正前，两处直接查询在两个新会话中均成功；生产收集路径随后复现了“30秒超时、51.404秒正常返回、后续仍读失败缓存”。该缓存缺陷已修正，先前43项直接相关测试通过。该轮超时在 `StringUtil.java`，修正后本轮则在 `LogService.java`，均不是原R1最初失败的两处位置。旧R1没有底层异常及耗时，不能追认它最初两次失败的根因。

原批次已查清的影响机制是：程序缓存了一次查询失败，后续五个入口遇到同一查询时直接重复抛出这次失败，没有重新询问 JDT；收集器随后将六个入口分别记为未收集。另一次失败影响统计入口。

原记录没有保存底层异常和请求耗时，不能区分超时与服务端异常。因此不能把本次结果写成“确定是偶发网络问题”“缺少 JAR”“所有JDT问题已经修复”或“旧正式覆盖已修复”。

本实验与嵌套调用候选串位是两件不同的问题。后者仍在 [backlog §21.1](implementation-lessons-and-followups.md#211-小项直接使用-jdt-的逐调用方法绑定)，本实验不验证或修改其选择算法。返回context不代表每条调用均准确，既有`UNRESOLVED_CALL`仍混合外部边界与未确定目标。

## 固定输入及原失败

使用[固定源码验收](technical-analysis-fixed-source-acceptance.md)的同一提交、R0、106-JAR 官方 classpath、有效 POM、目标 Java 8 和已安装 JDT。通过正式配置读取、源码重开、编译输入生成及 `openJdtSession` 创建隔离会话，没有手工补 JAR。

原 R1 为 `analysis-run:a4f749d142e233f0bf626821598d29822452f868da74674297022c4f32d19c26`。两次实验生成的编译输入与 R1 私有记录逐字节相同，SHA-256 均为：

```text
0ee9b7f57c7cb409433625753825438893e9796a4b1453b50edc532c3bee4bb9
```

| 查询 | 冻结源码中的实际语句 | 原请求位置（LSP 从 0 计数） | 原运行影响 |
| --- | --- | --- | --- |
| `MaterialExample.java` | 第15行：`oredCriteria = new ArrayList<>();` | line=14，character=27 | 一次失败、五次失败缓存命中；六个商品入口未收集 |
| `DepotHeadController.java` | 第648行：`BaseResponseInfo res = new BaseResponseInfo();` | line=647，character=35 | 一次失败；采购销售统计入口未收集 |

原会话共发出28,114次物理导航查询。上述失败分别是其中第11,075次和第27,110次；不是七次独立物理失败。

七个入口分别是：

- `POST /material/batchSetStatus`
- `DELETE /material/delete`
- `POST /material/batchUpdate`
- `POST /material/importExcel`
- `GET /material/checkIsNameExist`
- `DELETE /material/deleteBatch`
- `GET /depotHead/getBuyAndSaleStatistics`

原导航记录为 `/private/var/folders/ss/74phyrrn46q1qxmrst23qr9m0000gn/T/source-analysis-jdt-navigation-6551876865004409246.jsonl`，两次失败位于第26769和109205行。失败行只有通用 `JDT_QUERY_FAILED` 和消息，没有具体 cause、响应或耗时。原 JDT 工作目录已按既有关闭流程清理，不能再取得该会话的工作区日志。

## 实验方式

诊断驱动保存在 `.workspace/jdt-two-query-diagnosis-20260928/DefinitionFailureProbe.java`。它调用现有 `openDocument` 和 `definitions`，不调用全仓 `catalog/collect`，不创建新的正式 R1/R2/R3。

- 第一个新会话：先查询 Material，再查询统计入口。
- 第二个新会话：交换顺序，先查询统计入口，再查询 Material。
- 每个会话再对两处各调用一次适配器，检查既有缓存；缓存命中不算新的物理请求。
- 用观察代理保存真实请求、响应、异常和耗时；关闭前保存工作区日志、查询日志及 stderr。
- 两个会话依次运行，未并行启动 JDT。

## 实际结果

下表耗时为物理 definition 请求发出至 future 完成，不含读取材料和 JDT 启动。

| 查询 | 会话1 | 会话2 | 两次实际返回 |
| --- | --- | --- | --- |
| Material 的 `new ArrayList<>()` | 成功，306毫秒 | 成功，394毫秒 | 空定义位置列表 `[]`，不是请求失败 |
| 统计入口的 `new BaseResponseInfo()` | 成功，324毫秒 | 成功，1,034毫秒 | `BaseResponseInfo.java` 第7行的构造方法位置 |

每个会话均为2次物理查询、2次缓存命中；合计4次物理查询，均无查询异常。会话1从开始到关闭约53秒，会话2约67秒，包含准备与启动。第一次驱动在会话已关闭后有残留客户端线程，第二次驱动在完成保存和关闭后明确退出；没有因此追加第三轮实验。

Material 源码明确导入 `java.util.ArrayList`；但是空位置列表本身不证明适配器已经识别了 JDK 身份。本实验只确认查询正常返回，不把空列表改称为“已找到项目内实现”。

原始结果：

- [会话1请求、响应和统计](../../.workspace/jdt-two-query-diagnosis-20260928/attempt-1/events.jsonl)
- [会话2请求、响应和统计](../../.workspace/jdt-two-query-diagnosis-20260928/attempt-2/events.jsonl)
- 两个目录均保存 `compilation-input.json`、`source-analysis-navigation-query-journal.jsonl`、`jdt-workspace.log`、`jdt-stderr.log`。

## 排查时确认的程序行为（修复前）

1. [查询等待与日志](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/JdtLanguageServerClient.java)：`awaitQuery` 将 `ExecutionException` 和 `TimeoutException` 包装为同一种查询失败；异常对象仍带 cause，但 `appendExchange` 未把 cause 和耗时保存下来。原配置每次查询超时为30秒；这不证明原失败实际等了30秒。
2. 同一类的 `cachedNavigationQuery` 缓存成功和失败。重复查询调用 `replay()`，一次失败因此可中止多个入口。
3. [入口收集](../../src/main/java/org/sourceanalysis/app/analysis/code/jdt/EntryCodeCollector.java)未在该查询点保留可继续的入口部分结果；异常传播到 [入口执行器](../../src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsExecution.java) 后，该入口被记为 `NOT_COLLECTED`。这解释了为什么一次工具请求失败会造成整个入口无包，而非仅少一条定义。

## 不能由实验推出什么

- 没有复现原来的全仓索引、语法辅助进程、数万次导航及其时序/负载。因此只能说“新会话中的两处定向请求未复现”，不能保证全量重跑不会失败。
- 诊断驱动额外观察 future、排空 stderr，可能改变时序或管道压力。未做对照，不能把这一点认定为修复原因。
- 前两次直接查询使用 `/var/folders/...` 路径，生产收集器使用 `toRealPath()` 后的 `/private/var/folders/...` URI；物理文件一致不等于 LSP 文档身份字符串完全相同。下述入口实验使用生产代码构造 URI，并取消运行期间的 stderr 排空，以减少这两个差别。
- 新工作区日志有 Buildship 启动时读取 Gradle 版本信息失败的 `UnknownHostException: services.gradle.org`。这属于工具启动中的后台网络尝试，尽管现有配置关闭了 Gradle 导入；并非本次主动运行 Maven 或下载依赖。两处查询仍成功，故不能把这个警告认定为原查询失败原因，也不能声称工具启动完全没有联网尝试。
- 没有修改嵌套调用归属，没有重建七个入口材料，旧 R3 的七条未收集记录仍然有效。

## 后续实验：通过生产收集器观察七个入口

用户确认继续排查后，诊断驱动通过同一个正式 `JavaCodeSession.catalog/collect` 收集原七个入口。保留原始入口 ID、methodKey、源码位置、30秒查询超时和缓存行为；不创建正式 R1/R2/R3、不重跑其余入口。声明目录实际读到273个Java文件、9,189个方法。

### 已复现的超时与迟到成功

当前实验的第89次物理请求为 `StringUtil.java` 的定义查询（LSP line=290、character=30），对应第291行：

```java
List<Long> idList=new ArrayList<Long>();
```

| 相对本次查询开始 | 实际行为 |
| --- | --- |
| 0秒 | `/material/batchSetStatus` 的收集器发出 definition 请求 |
| 约30秒 | `awaitQuery` 抛出 `TimeoutException`；程序缓存失败并使该入口结束 |
| 51.404秒 | 同一个请求的 future 正常完成，结果为 `[]`，没有远端异常 |
| 之后 | `/material/delete`、`/material/batchUpdate`、`/material/deleteBatch` 再遇到同一查询，仍直接抛出原缓存异常 |

这证实“等待超过30秒”不等于“JDT已经停止或查询最终失败”。适配器只在同步等待返回时写成功缓存；一旦超时转入失败分支，之后的正常响应不会更新该缓存。正常空位置列表本来可以由现有收集器作为未解析调用记录，不会触发这条查询异常。

原失败的两个位置在此次生产调用路径中都正常返回：`MaterialExample.java#14:27` 用时6毫秒，返回 `[]`；`DepotHeadController.java#647:35` 用时15毫秒，返回 `BaseResponseInfo` 构造方法位置。因而本次不能说原两处源码本身必然触发异常。

### 慢在哪里：证据边界

- 第89次定义查询等待时，第90、91次调用层次请求仍分别在5毫秒、140毫秒内返回。因此不能解释为整个JSON-RPC读线程卡死或JDT进程退出。
- 同时发出的下一次定义查询用时21.244秒，在第89次结束约42毫秒后完成。这提示定义查询路径可能有共享等待，但没有定位到具体内部阶段。
- 本轮运行期间不排空 stderr；已完成入口观察到的待读字节为945，没有发现持续增长到管道满的证据。不能把 stderr 饱和当作已确认根因。
- 代码核对显示，导航等待持有客户端对象锁，而诊断回调使用另一把 `diagnosticLock`；没有发现“诊断回调必须拿导航锁，导致互相等待”的直接路径。
- 官方对应版本的 [JDTLanguageServer](https://raw.githubusercontent.com/eclipse-jdtls/eclipse.jdt.ls/v1.61.0/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/JDTLanguageServer.java) 在定义查询前等待文档生命周期任务，随后进入 [JDTUtils](https://raw.githubusercontent.com/eclipse-jdtls/eclipse.jdt.ls/v1.61.0/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/JDTUtils.java) 的 `codeSelect` 等查找。没有这些内部阶段的实测耗时，不能把任何一项指定为51秒的根因。
- `jcmd`线程诊断因附加权限/附加套接字超时未取得Java栈；系统原生2秒采样已保存，但未获得足以定位该慢查询的Java方法栈。没有使用可能中断目标的强制调试方式。

### 七个入口的最终结果

| 原入口 | 本轮结果 | 已保存实验材料 | 入口耗时 |
| --- | --- | --- | --- |
| `/material/batchSetStatus` | 失败：第89次物理请求超时 | 完整异常，无成功context | 50.377秒 |
| `/material/delete` | 失败：复用第89次请求的失败缓存 | 完整异常，无成功context | 66.254秒 |
| `/material/batchUpdate` | 失败：复用同一失败缓存 | 完整异常，无成功context | 5.697秒 |
| `/material/importExcel` | 收集返回，仍有调用限制 | 227方法、780调用、308条未解析限制 | 419.909秒 |
| `/material/checkIsNameExist` | 收集返回，仍有调用限制 | 22方法、68调用、41条未解析限制 | 25.801秒 |
| `/material/deleteBatch` | 失败：复用同一失败缓存 | 完整异常，无成功context | 8.302秒 |
| `/depotHead/getBuyAndSaleStatistics` | 收集返回，仍有调用限制 | 85方法、304调用、150条未解析限制 | 136.528秒 |

总计2,891次物理请求、705次缓存命中；全部2,891个future最终正常完成，无远端异常，其中1个定义请求超过30秒。入口结果是3个返回context、4个异常，不等于3个入口的全部调用关系均已正确或完整。总墙钟830.734秒（含输入核验、会话启动、声明读取、串行收集及关闭），没有增加新的自动重试。

原始请求、迟到响应、入口异常和三个实验context保存在 [seven-entries-1](../../.workspace/jdt-two-query-diagnosis-20260928/seven-entries-1/events.jsonl)。本轮工作区日志、导航journal和原生采样已保存；运行期间没有读取stderr，关闭后尝试保存该流得到`Stream closed`，因此本轮没有完整stderr文件。七次入口结束时均实测待读字节为945；不能把这一数字当完整错误内容。

诊断附加尝试发生在超时已经出现之后；没有取得可解释慢查询的Java线程栈。原生采样及观察代理也可能影响后续时序，所以51.404秒是本次实测值，不是该调用的固定耗时。本轮不是正式技术发布，旧R1/R3仍保持原覆盖。

## 已实施的最小修正及验证

用户确认后，仅修改现有`JdtLanguageServerClient`的查询缓存和私有诊断。首次查询即保存原请求；等待超时不再将其覆盖为最终失败。后续相同查询等待这个原请求或使用其最终结果，实际RPC仍最多一次。原调用者等到超时仍报错，不自动恢复该入口、不提高超时上限。迟到的空列表按成功查询处理，远端错误和格式错误仍是失败。

私有日志分别保存`WAIT_TIMEOUT`、`WAIT_INTERRUPTED`和最终`QUERY_EXCHANGE`，增加`elapsedNanos`及可取得的`failureCause`。异步回调只捕获结果和实际完成时间；最终exchange在调用者观察时保存。没有后续观察可能只有等待记录，不能据此推断最终失败。关闭后不写日志、不重新开启会话。源码导航选择、多态候选及入口部分材料保存合同不变。

Luna先用受控LSP响应复现RED，再由Terra实现。直接回归命令：

```bash
mvn -Dmaven.repo.local=/private/tmp/source-analysis-technical-m2 -o \
  -t .mvn/toolchains.local.xml -DskipITs \
  -Dtest=JdtProjectSessionTest,JdtNavigationResolverTest,EntryCodeCollectorTest test
```

Surefire结果：`JdtProjectSessionTest`37项、`JdtNavigationResolverTest`2项、`EntryCodeCollectorTest`4项，共43项通过，失败/错误/跳过均为0。新增3项覆盖迟到成功、第二个调用者继续等同一请求及迟到远端错误/日志区分；原有空响应、格式错误、缓存键隔离和会话关闭断言继续通过。独立静态审查未发现本次差异内的阻断问题。

验证记录边界：43项命令的工具包装遗漏了长运行的会话编号，未保存Maven最终退出码；以上计数来自完整Surefire报告。独立的3项回归命令保存了`BUILD SUCCESS`；最终对两个变更Java文件的定向`spotless:check`退出码为0。未运行全仓套件或完整质量生命周期。

上述代码修正和自动化验证阶段没有启动真实JDT、客户Maven/构建、Activity或业务过程模型。测试使用本模块的模拟LSP进程；其后新增的真实验证记录如下。旧正式索引及固定源码未改写，旧R3的7项未收集没有被实验结果替换。

## 修正后的七入口真实重验（2026-09-29）

### 输入、执行与输出

本轮复用同一固定源码、原7条入口seed、106个JAR和Java8目标。程序生成的编译输入摘要仍为本页前述`0ee9…bb9`。使用当前已编译的生产`catalog/collect`及缓存修正，查询超时仍为30秒，不自动重试入口，不修改导航候选算法。

唯一新增的工具配置是JDT进程启动JFR记录；使用JDK自带`RecordingFile`读取记录，不在生产代码中增加监控系统。输入核验、JDT启动、声明目录读取、七入口串行收集及关闭共909.890秒；JDT声明目录仍为273个Java文件、9,189个方法。

| 入口 | 本轮收集结果 | 已保存方法／调用 | 入口耗时 |
| --- | --- | --- | --- |
| `POST /material/batchSetStatus` | 首次definition等待超时，没有成功context | — | 49.063秒 |
| `DELETE /material/delete` | 返回context，仍有未解析调用限制 | 68／248 | 163.485秒 |
| `POST /material/batchUpdate` | 返回context，仍有未解析调用限制 | 20／66 | 15.774秒 |
| `POST /material/importExcel` | 返回context，仍有未解析调用限制 | 227／780 | 327.536秒 |
| `GET /material/checkIsNameExist` | 返回context，仍有未解析调用限制 | 22／68 | 25.253秒 |
| `DELETE /material/deleteBatch` | 返回context，仍有未解析调用限制 | 68／247 | 47.769秒 |
| `GET /depotHead/getBuyAndSaleStatistics` | 返回context，仍有未解析调用限制 | 85／304 | 154.217秒 |

2,992次物理查询全部最终正常返回，没有远端异常；1,373次缓存命中，只有1次物理请求超过30秒。这里“全部最终返回”不等于入口全成功：第一个入口已经因调用者等待超时结束，迟到响应没有自动重新执行它。

原两处definition在本轮分别用时9毫秒（`MaterialExample#14:27`）和12毫秒（`DepotHeadController#647:35`），均正常。上一轮慢51.404秒的`StringUtil#290:30`本轮仅7毫秒。因此不能把慢等待归因于这些源码表达式必然难以解析。

原始输出：[attempt-1请求与入口结果](../../.workspace/jdt-seven-entry-revalidation-20260929/attempt-1/events.jsonl)。同目录保存6份`entry-*.json`、编译输入、导航journal、JDT日志、JFR及选定入口清单。它们是诊断产物，尚未安装为正式R1/R2/R3；不改变旧R3的覆盖记录，也不能当成所有调用关系准确的证明。

### 50.629秒请求及已证实的缓存效果

第55次请求是在`LogService.java`第133行查找`userService.getUserId(request)`的定义（LSP`132:38`）。30.006秒时原入口等待超时；50.629秒时原请求正常返回`UserService.java`第860行目标，没有重新发请求。

导航journal第55行保存`WAIT_TIMEOUT`，第186行是该键后续`CACHE_HIT`，第187行保存最终`QUERY_EXCHANGE SUCCESS`及`elapsedNanos=50629851486`。这说明缓存修正确实用于客户源码：后续消费者拿到了原请求的迟到成功，而不是旧超时。**它只解决失败被缓存扩大传播，不消除JDT慢等待，也不自动修复首次失败入口。**

### 慢等待已定位到哪一层

JFR在UTC`03:11:03.702`取得的线程栈显示，两条定义请求均位于：

```text
JDTLanguageServer.waitForLifecycleJobs
→ JobHelpers.waitForJobs
→ JobManager.join
→ Object.wait
```

它们正在等待文档生命周期任务完成，尚未进入后面的目标定义解析。这个等待期间，调用层次请求仍可正常返回。JFR长`FileRead`来自JSON-RPC的标准输入等待，不是读取客户源码或下载JAR的耗时，不能误判为磁盘/依赖瓶颈。

已安装Eclipse Jobs中，一个空闲worker（Worker-3）在被唤醒后约0.31毫秒退出；此前它已间歇等待约61秒，其余worker当时均在等待。安装版代码确有“闲置超过60秒且还有其他空闲线程便退出”的分支。这提示需要检查任务调度，但**仅凭线程结束不能证明是该分支，更不能证明验证任务漏唤醒**。JFR没有保存所唤醒任务的身份与队列状态，另需工具自带jobs trace。

原51.404秒那一轮没有JFR，所以本轮50.629秒的内部路径不能倒推成那次历史请求已经得到逐栈证明。JFR与普通日志墙钟存在约200毫秒偏移；不得拿亚秒级先后差拼接因果。

### 剩余一个入口的单独重验

随后只执行`/material/batchSetStatus`，仍使用同一输入及30秒查询上限；`attempt-4-jobs`成功返回40个方法、138处调用、87条现有`UNRESOLVED_CALL`限制。入口收集90.185秒，含准备及关闭的总时长209.705秒；350次物理查询均正常，最慢9.457秒。先前50.629秒的同一LogService definition本次27毫秒。

这证明该入口不是源码必然无法收集；**不证明间歇长等待已经修复**。完整材料为[该入口context](../../.workspace/jdt-seven-entry-revalidation-20260929/attempt-4-jobs/entry-34246eb700b585ac41d9982822d5f1602c710a42666fb411d60b534329548f76.json)，[本次请求与结果](../../.workspace/jdt-seven-entry-revalidation-20260929/attempt-4-jobs/events.jsonl)保留独立会话身份。它不是自动重试，也未拼进第一轮或正式覆盖。

采集限制：`attempt-2-jobs`和`attempt-3-jobs`在JDT会话初始化之前失败，原因是诊断驱动自行读取启动提示时分别遇到JFR、Equinox额外输出；不是客户入口失败。检查LSP4J既有协议读取行为后删除了该多余前缀读取，attempt-4正常运行。安装版Equinox不会仅凭`-Dosgi.tracefile`为`DebugOptions`设置文件；其trace writer在未设置文件时使用stdout。但不能据此说本次已经产生Jobs trace：attempt-4未保存stdout，后述attempt-5保存了stdout仍未取得任务记录。不得把诊断采集失败隐去或计入客户查询错误。

### 最后一次采集：同一StringUtil位置再次慢52.308秒

`attempt-5-jobs-stdout`只运行同一个首入口，在原有JFR及工具debug配置之外增加透明的、带缓冲的stdout原文保存，不过滤或重写LSP协议。第89次definition仍为`StringUtil.java#290:30`，与修复前51.404秒的位置相同。

- 调用者30秒超时，使入口本次失败（入口整体用时52.903秒）。
- 该物理请求52.308秒后正常返回`[]`；89次物理请求均最终正常返回，无远端错误。
- JFR在UTC`03:40:40.276`显示definition线程仍在`waitForLifecycleJobs -> JobManager.join`。该线程记录的join等待合计约52.34秒，期间Eclipse各worker均在`WorkerPool.sleep`，不是忙于解析该Java表达式。
- Worker-7在收到LSP读线程唤醒后约0.66毫秒退出；其他worker的60秒等待在稍后结束。这再次出现与第一轮相似的线程调度时序，但仍缺少任务身份、队列状态和退出分支栈，不能把“退休导致漏唤醒”写成已证实根因。
- `jdt-workspace.log`在返回附近记录`Reconciled 1. Took 0 ms`；这支持区分排队等待与实际更新耗时，不用跨时钟的亚秒差证明因果。

[请求及迟到响应](../../.workspace/jdt-seven-entry-revalidation-20260929/attempt-5-jobs-stdout/events.jsonl) · [JFR等待栈提取](../../.workspace/jdt-seven-entry-revalidation-20260929/attempt-5-jobs-stdout/slow-window.txt)。同目录保留完整JFR、stdout、JDT工作区日志和失败入口记录。

真实stdout证明配置文件被加载，但没有所需的Jobs schedule/start/end记录。因此本轮的**确定结论**是“反复出现的JDT文档生命周期等待；客户端超时先结束入口；查询随后正常返回”。**尚未确定**的是“具体哪个任务为何迟迟未执行，以及属于工具缺陷、配置问题还是其他调度条件”。不能把本轮说成已彻底查明并修复51秒延迟，也不能把它说成单次偶发且无需处理。诊断会影响时序，本轮没有用其推导失败概率。

### 交付与剩余范围

七个原缺口都有成功返回的实验材料；首入口有成功也有重复超时，稳定性未通过。旧R1/R2/R3没有重写，不自动安装拼接结果，不自动重跑全仓；没有运行Maven、客户构建、Activity或业务模型。本轮未修改生产代码。缓存修正的效果已有实测；后台长等待、首次失败入口如何接续、错误候选和外部调用分类仍分别保留，不能相互代替。

受保护`more-findings.md`和旧R3 JSONL的SHA-256与执行前相同。后续若继续，应优先取得现有Eclipse工具的任务状态/版本修复证据；本轮不自建调度器、不改变导航算法、不仅靠扩大超时宣布问题解决。
