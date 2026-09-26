# Step02：Java 分析环境与依赖就绪

状态：详细设计、待实施。Step02先按[自动依赖准备](dependency-preparation.md)解析常规Maven环境，再由本页核验两个JDT工具的实际环境与诊断；现有Step03导航算法不重写。用户已确认：仍未就绪时保存报告、不导航，由Agent询问。不能把已列3个JAR的摘要正确，解释成项目只需要3个JAR。

## 1. 依据及不能承诺什么

已有定向排查中，补入本地可用依赖后，错误Mapper目标、`logger.error`误入`AjaxResult.error`等导航结果得到纠正。这支持先修编译环境，不支持“18个JAR已经覆盖所有编译依赖”或“所有调用今后绝不出错”。本轮文档没有重新运行这些试验。

Maven的直接依赖、传递依赖、scope、版本选择和profile共同决定编译类路径；只读依赖声明或列举缓存目录不能代替实际解析结果。[官方依赖机制](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)

受信任环境可导出解析后的classpath；例如官方 `dependency:build-classpath`目标可输出依赖路径，但单独的路径字符串不含本设计要求的源码、模块、JDK及profile身份，也不自动提供生成源码。[官方目标说明](https://maven.apache.org/plugins/maven-dependency-plugin/build-classpath-mojo.html)

## 2. 输入：使用实际解析结果，不猜依赖

新增 `java-compilation-environment-v1`。默认由同次Step02的声明式Maven准备器生成，也可由用户明确选择的受信任环境清单提供；两种来源共用本页核验。正式结果不含本机路径/凭据，私有定位与原始工具记录分开保存。常规依赖可在明确仓库策略下自动取得；客户Maven启动脚本、插件、扩展、hook、注解处理器仍不执行。此条替代上一版“程序不自动准备或下载”的目标限制。

每个分析模块必须记录：

| 内容 | 必须表达的事实 |
| --- | --- |
| 源码绑定 | `sourceVersionId`、有效范围摘要；导出清单对应的源码/构建描述摘要；导出来源及时间仅作溯源 |
| 模块 | 相对模块路径、生产/测试源码根、被分析source set；不能把多模块不同依赖的并集随意当一个模块 |
| 编译目标 | 实际Java语言级别、`--release`或source/target、实际目标JDK/platform；与运行JDT的工具JVM分开 |
| 构建选择 | 构建工具/版本、激活profile、影响依赖和生成源码的非秘密参数、解析完成状态 |
| 二进制依赖 | 已解析顺序、坐标/版本/分类器/作用域、每个实际二进制的摘要和大小、私有本地定位；包含传递编译依赖 |
| 模块依赖 | 使用同快照源码模块还是已编译模块产物；不能两者冲突遮蔽。源码根必须属于有效范围 |
| 生成内容 | 生成源码/字节码是否参与实际编译、是否已随固定输入保存。缺失时报告，不启动生成器补齐 |
| 导出完整性 | 每个source set的resolved/unresolved列表、覆盖范围和限制；“导出命令成功”不单独判定完整 |

`provided`等编译所需依赖不能因不随产品打包就删除；只看打包lib目录也不能证明完整。测试目录若纳入分析，其独立类路径必须匹配；不将测试依赖无条件混入生产模块。

首版支持的声明式Maven模块布局必须按原工程隔离依赖，并通过单模块及普通多模块fixture；不支持的布局明确阻断。现有单`VerifiedJavaProject.classpath`、单project投影和helper环境尚不能表达该目标，须修改环境载体/投影，不改导航算法。目标JDK不仅是languageLevel：LS的JRE容器映射和Core当前`setEnvironment(..., true)`均需验证并修正，不能默认使用工具JVM平台，详见[双工具环境合同](dependency-preparation.md#5-如何交给现有-jdt)。

## 3. Step02 内部处理顺序

1. 重开已选择的源码准备结果，确认可消费、来源和排除范围匹配。
2. 在实际权限内自动准备常规Maven依赖，或读取明确选择的PROVIDED环境；核对清单绑定的源码、模块和source set。逐项检查实际二进制存在、完整性、顺序及重复冲突；列出缺失/变化/未解析项目。未完成时保存报告返回，不先用部分依赖启动导航。
3. 生成 `javaAnalysisBasis`：来源版本/有效范围、模块拓扑、源码根、目标JDK、classpath内容与顺序、JDT/Core/helper版本、有效解析配置。机器绝对路径不代替内容身份。
4. 清单允许继续时，按模块生成受控project，打开同一个JDT LS会话；Core按文件的模块读取同一环境。禁用客户工程导入/构建脚本及注解处理；只用已准入文件和明确依赖，不让JDT自行补网络依赖。
5. 收集本次项目/文件诊断，保存原始code、severity、path/range、message和实际检查范围。完成性单列，不把无通知当零诊断。
6. 形成就绪结果，保存后才允许目录/Java调用收集。目录和导航共享本次会话、编译基础和缓存，不能检查一个classpath后再用另一个classpath执行。

缺失二进制、版本/源码不符、未解决类型、工程配置错误、诊断范围未确认均不得标`READY`。普通未使用变量等warning只报告，不因warning数大于零机械失败。无法可靠识别的error归`UNCLASSIFIED_ERROR`，不按中文message关键词猜。

## 4. 诊断怎样取得：必须先验证协议边界

当前 `JdtLanguageServerClient.SessionLanguageClient.publishDiagnostics()`为空。新增collector应复用同一客户端保存通知，精确关联本次会话、投影文件URI和不可变文档版本；外部/旧会话消息不能填充当前检查范围。

检查分母先从有效来源与编译清单确定，不能从“收到了哪些回调”反推：对每个模块/source set，取其源码根内所有被选定且未排除的`.java`文件，形成`expectedSourcePaths`。重复源码根按模块归属核验，文件不得被误分配到另一个classpath；清单声明需要但未提供的生成源码另列缺项，不能靠分母里没有它就判通过。范围之外的测试目录/模块列为未纳入分析，不伪称整个仓库编译通过。

必须满足以下不变量：

- `expectedSourcePaths = checkedSourcePaths ⊎ uncheckedSourcePaths`，两者无交集；所有未检查项有原因。
- `checked`只表示取得了**本次依赖加载及本次固定文档版本之后的最终诊断**，包括明确的空诊断结果。首条回调、旧回调、仅发送校验请求均不算完成。
- 回调不带版本时，必须由锁定工具版本的已验证完成协议建立对应关系；不能自己补一个版本字段便视为可靠。外部URI或上一会话结果仅作原始诊断，不计入当前分母。
- `diagnosticCollectionStatus=COMPLETE`要求未检查集合为空，且每模块项目级诊断已取得；`readiness=READY`还要求环境VERIFIED、没有阻断错误或生成内容缺项。零个Java文件只有明确的空分析范围才能完成，不等于未成功枚举文件。
- 已知缺依赖/编译错误为BLOCKED；未能确认诊断终态或范围为UNDETERMINED。两者都不能按“目前看见的错误为零”放行。

官方JDT LS有 `java/validateDocument` 扩展，返回的是通知式校验触发，不是同步“全仓校验完成”；`ServiceReady`或一次documentSymbol响应也不能证明所有文件诊断已经结束。[官方协议](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/lsp/JavaProtocolExtensions.java) · [官方实现](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/JDTLanguageServer.java)

实施先做一个**固定工具版本的就绪采集验证**：

- 在自有fixture中，明确开启待检查文档、触发该版本支持的诊断校验，保存每文件回调和结束条件；验证缺类型和合法文件都能报告，包括空诊断数组。
- 验证最终诊断是否对应依赖加载完成后的稳定文档；禁止用“等几秒没消息”作为完成依据。协议不能给出可靠完成条件时，该次结果为`UNDETERMINED`。
- 无法可靠取得完整诊断时，暂停本项验收，报告实测缺口，再讨论最小的受控JDT诊断适配；不能未经讨论改成运行客户build或重新开发Step03。
- 不为了多收诊断开启任意客户Eclipse builder、Maven importer、插件或annotation processor。JDT内部语法/类型分析与执行客户构建是不同操作，但工具配置必须实际验证能隔离。

这项协议验证尚未完成，是本设计的已知技术风险。不能在文档里把不确定的回调完成语义写成现有保证。

## 5. 输出与失败行为

Step02 module5保存实际`java-compilation-environment.json`及新增的`java-analysis-readiness.json`。前者含解析得到的模块/依赖和未完成范围，后者使用 `java-analysis-readiness-v1`：

```json
{
  "schemaVersion": "java-analysis-readiness-v1",
  "sourceBasis": "完整SelectedSourceBasis对象，示意",
  "javaAnalysisBasis": "按上文实际内容计算的标识，示意",
  "dependencyPreparationStatus": "RESOLVED",
  "compilationEnvironmentRef": {
    "fileName": "java-compilation-environment.json",
    "schemaVersion": "java-compilation-environment-v1",
    "sha256": "同module内实际环境文件的摘要，示意"
  },
  "environmentStatus": "VERIFIED",
  "diagnosticCollectionStatus": "COMPLETE",
  "readiness": "READY",
  "checkedModules": ["已核对的模块"],
  "expectedSourcePaths": ["应完成校验的全部有效Java文件"],
  "checkedSourcePaths": ["已完成校验的文件"],
  "uncheckedSourcePaths": [],
  "dependencyProblems": [],
  "requestedActions": [],
  "diagnostics": []
}
```

这是目标字段说明，非真实运行文件；正式schema中basis用类型化对象、问题用结构化记录，不能照抄示意字符串。`compilationEnvironmentRef`只指同module内具名文件及摘要；不内嵌包含本就绪文件的module receipt或publication身份，避免循环引用。外层module/step receipt在两文件定稿后统一赋予并核验。

`environmentStatus=VERIFIED|INVALID|INCOMPLETE`；`diagnosticCollectionStatus=COMPLETE|INCOMPLETE|NOT_STARTED`；`readiness=READY|BLOCKED|UNDETERMINED`。问题记录包括code、模块/路径/范围、操作、预期/实际、原始诊断和允许下一动作；沿用源码准备“保存结果不等于可继续”的原则，但不挪用源码文件readiness表示Java环境。依赖准备未完成时`javaAnalysisBasis`允许为空，不能用未完成清单生成可导航身份；环境与报告仍是有效的部分检查结果，不是部分调用索引。

**已确认，不提供降级导航开关。** 在已允许范围内能够补齐就继续，不逐JAR询问；仍缺依赖、输入不明或诊断未确定时，保存已完成报告及前端独立结果，阻止Java导航，不生成假空Java成功索引。CLI返回结构化问题及下一动作，Agent按Skill向用户解释；用户修正配置/提供材料后新建运行，旧报告保留。不能把运行`FAILED`理解成没有结果；`inspect/artifact`仍可查看已安装报告。单纯口头确认不能把未知环境变为READY。

## 6. 如何验证改动有效，不开发第二个导航器

对同一冻结源码、相同调用位置，使用现有Step03录制补齐前后原始响应及最终索引，具名覆盖：

1. Service→Mapper：错误的其他Mapper目标不得继续出现。
2. SLF4J `logger.error`：不得出现仓库`AjaxResult.error`错误边；外部方法仍可按外部边界保存。
3. 重载：实际不同参数调用与对应声明一致。
4. 嵌套调用：内外调用位置、实际参数、目标不串。
5. 合法接口/多态：保留真实声明及实现候选，不能以“只留一个”为正确。

核对索引后还要在Step04/05检查错误Mapper/SQL没有进入材料。不得只报告诊断条数下降或某个RPC变对。3–5个代表例全部通过提高可信度，但不推出全仓所有调用无错；有残留就列出并停止宣称该质量项已完成。是否需要额外Step03一致性检查只能在残留证据基础上另行讨论。

## 7. 它证明什么，不证明什么

完成上述检查能说明：分析使用了与选定编译环境匹配的实际依赖，并在已检查范围没有已知阻断诊断；代表性导航回归通过。它不能证明运行时Spring注入、反射、所有多态分派、全部业务规则都已确定。工具缺陷仍可能存在。

就绪检查属于Step02。回归借用Step03/04/05已有能力，不意味着重写这些能力。依赖变化使Java分析基础不同，需显式产生新索引；源码不变并不使旧错边自动失效或修正，见[运行与来源合同](cli-and-runtime.md)。
