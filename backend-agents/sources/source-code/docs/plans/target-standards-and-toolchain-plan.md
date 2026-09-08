# 目标实现标准与工具链实施计划（核心包已批准）

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task, and use `superpowers:verification-before-completion` before claiming any task complete.

**状态：** `CORE PLAN APPROVED — IN-SCOPE EXECUTION DEFAULT AUTHORIZED`

**当前成熟度（2026-09-01）：** 目录/Maven/package结构切换、pre-reset代码物理删除、项目JDK 17 Toolchain和通用`SOURCE_ANALYSIS/v1`头门禁已经落地；八个业务分析步骤、三类canonical store和公共runtime仍按本计划后续任务实施。后文“当前基线”与Wire Reset操作描述保留其制定时语境，不得被解读为旧路径或旧代码仍然存在。

**目标：** 在不改变已批准八个分析步骤业务架构、持久化 DAG、身份公式和公开接口的前提下，冻结目标实现使用的成熟开源依赖、Maven 质量门、通用文件标准、代理协作边界、直接测试 selector、发布门和连续工期预算。

**架构：** `verified-source-inventory → application-discovery → program-graphs → proven-code-facts → business-flows → flow-interpretation → repository-knowledge → nine-section-document → validation → Java/CLI/HTTP adapters`。三个深存储 seam、policy registry、`analysis-run-request-v2`、语义 receipt-last、九章文档五项 semantic → archive → receipt → root manifest → M4 的顺序均来自权威设计。本计划只规划实现方法，不另造跨分析步骤决策。显式 `executeStep` 可让一个新执行消费经验证的上游分析步骤 publications；它不是同一 run 恢复。

**目标身份与package registry：** 工程目录为`backend-agents/sources/source-code/`，Maven坐标为`org.sourceanalysis:source-code-analysis-agent`，display name为`Source Code Analysis Agent`，Java root为`org.sourceanalysis.app`。八个分析package依次为`org.sourceanalysis.app.analysis.inventory`、`org.sourceanalysis.app.analysis.discovery`、`org.sourceanalysis.app.analysis.graph`、`org.sourceanalysis.app.analysis.fact`、`org.sourceanalysis.app.analysis.flow`、`org.sourceanalysis.app.analysis.interpretation`、`org.sourceanalysis.app.analysis.knowledge`、`org.sourceanalysis.app.analysis.document`；横切根精确为`org.sourceanalysis.app.capture.localgit`、`org.sourceanalysis.app.artifact`、`org.sourceanalysis.app.evidence`、`org.sourceanalysis.app.runtime`、`org.sourceanalysis.app.validation`、`org.sourceanalysis.app.adapter.cli`、`org.sourceanalysis.app.adapter.http`、`org.sourceanalysis.app.adapter.provider`。未来数据库只保留文档根`org.sourceanalysis.db.analysis`，本计划不创建数据库代码，也不创建通用`common`/`shared`根。

**技术栈：** Java 17、Maven 3.9.11、Jackson 2、隔离 Git CLI、JavaParser Symbol Solver、Maven Model Reader、Tomlj、networknt JSON Schema Validator、Picocli；JUnit/AssertJ/jqwik/ArchUnit/Awaitility；Enforcer/Toolchains/Compiler/Surefire/Failsafe/Spotless/SpotBugs/PMD/Dependency/CycloneDX/OWASP/Javadoc/Shade。

**权威规格：** `docs/DESIGN.md`、`docs/analysis-steps/01-verified-source-inventory.md` 至 `docs/analysis-steps/08-nine-section-document.md`。若本计划与这些文件冲突，以完成独立审查并发布后的权威规格为准，实施必须停止并由 Sol/ultra 报告，不得在代码中自行选择。

---

## 0. 已批准范围、默认授权与非目标

用户已批准第12节的核心技术包。标为 **APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED** 的依赖、插件、版本、profile、格式规则和执行顺序不再等待重复确认；在后续明确的项目 work unit 内，精确 Maven Central 下载、POM 修改、格式化、范围内测试、提交和非强制推送均默认授权。执行仍须满足当前任务范围、最近 AGENTS 指令、平台强制最小权限与安全审批；默认授权不是当前 docs-only work unit 越界修改实现的许可。

标为 **DEFERRED — CALLER-DRIVEN** 或 **ENVIRONMENT-GATED** 的工具不是用户审批阻塞项：只有出现具体调用者或所需feed/credentials/cache/JDK事实时才按本计划启用，否则不把闲置工具放进POM/CI。force push、破坏性 Git 操作、客户代码执行、真实模型/Provider 调用、客户或其他外部源码访问、未列坐标/动态版本下载、远程 schema/config 解析仍不在既定范围。全仓测试仍受 scoped AGENTS 限制；只默认授权当前改变的直接 selector。

本计划不做以下事情：

- 不改变八个分析步骤的模块表、公开输出数量、内容身份、receipt/root 公式或九章文档 DAG。
- 不以工具库替代证据、Proof、Flow、coverage、registry lineage、Candidate、validation 或显式 analysis step 输入验证的领域规则。
- 不实现进程重启后的同一 run、队列、worker 或 Provider 调用恢复；该范围仅见 `docs/supplements/runtime-recovery-todo.md`，不得据此编码。
- 不从 Maven、JavaParser 或 JGit 自动解析客户代码时触发远程解析、构建、class loading、注解处理器或客户代码执行。
- 不新增通用 `common`、`shared`、`misc` 或 `utils` 层，不自造 JSON/TOML/XML/Git/Maven parser、Java formatter、Markdown linter 或 JSON Schema evaluator。
- 不把已批准版本视为已经下载、已经兼容或已经通过安全审计；这些事实由后文执行 gate 和测试建立。

已批准核心包包括基础依赖、测试库、Maven插件/profile、通用格式规则和实施工作流。JGit alternate adapter、Taplo、markdownlint-cli2与Maven Wrapper为caller-driven deferred项；OWASP漏洞库更新为feed/credentials/cache environment gate；新JDK安装仅在project-local Toolchains找不到合格JDK 17时触发。它们都不再等待额外用户确认。

## 1. 当前基线与不可变前提

| 项目 | 当前事实 | 本计划建议 | 状态 |
| --- | --- | --- | --- |
| Java | 当前 POM `maven.compiler.release=17`；preflight shell Java为26.0.1；本机已有Homebrew JDK 17.0.19 | 保持 Java 17 bytecode/API；Compiler/测试/Javadoc由project-local Toolchains选择JDK 17，不随shell `JAVA_HOME`漂移 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven | preflight Maven 3.9.16，当前POM未冻结wrapper/runtime上限 | Enforcer要求Maven `[3.9.11,4)`，当前3.9.16满足；Wrapper 3.3.4仅在出现可复现分发调用者时按需加入 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Jackson | 当前 POM 2.21.4 | 保持一个 Jackson 2 BOM/版本源；任何库带入的 Jackson 均收敛到同一 2.21.4，不混用 Jackson 3 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Picocli | 当前 POM 4.7.7 | 保持 4.7.7；只做 CLI 参数/usage/exit-code adapter，不承载领域验证 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| JavaParser | 当前 POM仅 `javaparser-core` 3.28.2 | 改用同版 `javaparser-symbol-solver-core` 3.28.2 作为应用发现与程序图 adapter；避免 core 与 solver 版本分裂 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Test | 当前 POM JUnit 5.13.4 | 保持 JUnit 5.13.4，增加职责互斥的 AssertJ/jqwik/ArchUnit/Awaitility | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| 重型验证 | 尚未冻结 | 同一时刻最多一个 Maven 重型命令；最多两个活跃代理；默认 direct selector + offline | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |

只读preflight另确认Node `v25.9.0`、npm `11.12.1`与`xmllint/libxml2 2.9.13`已安装，Taplo和markdownlint-cli2未安装。这些只是环境事实：当前docs-only gate不安装或执行deferred CLI；未来若出现具体Markdown/TOML调用者，仍先走本计划的官方版本/完整性门。`xmllint`即使已存在，也只允许`--noout --nonet`诊断，不能替代JDK secure JAXP/Maven Model Reader。

实施开始前必须先完成 `source-analysis-naming-and-delivery-plan.md` 规定的 docs-only 命名发布、独立 Sol/ultra 审查和文档机械 gate，并只提交/推送设计文档。该 publish gate 是代码工作的硬前置，不因核心包已批准或默认授权而跳过。

Toolchain foundation必须提交project-tracked `.mvn/toolchains.xml`，不得创建或修改用户级`~/.m2/toolchains.xml`。当前local host的exact配置使用Homebrew稳定symlink `/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`（preflight解析到`/usr/local/Cellar/openjdk@17/17.0.19/libexec/openjdk.jdk/Contents/Home`）：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>17</version>
    </provides>
    <configuration>
      <jdkHome>/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
~~~

每条Maven命令都必须显式传`-t .mvn/toolchains.xml`；toolchain file缺失、symlink失效、选择结果不是JDK 17或Compiler没有使用该toolchain均fail closed。其他host不得回退用户home配置：先在该host的独立toolchain maintenance work unit验证一个JDK 17绝对home并更新project-local文件，再运行任何build。AnalysisStep docs里的`mvn -Dtest=...`只标识direct selector，实际执行必须使用本计划第5节完整前缀。

## 2. 开源依赖决策矩阵

### 2.1 生产依赖

| 能力 | 建议坐标/版本 | 采用方式与边界 | 不采用方案 | 状态 |
| --- | --- | --- | --- | --- |
| Git capture | 系统 Git CLI；运行前记录并验证 `git --version`，最低兼容线在首次实现任务用 fixture gate 固定 | VerifiedSourceInventory 继续调用已批准的本地、只读、隔离 Git plumbing 命令；显式参数、固定对象格式、清空 hooks/config/env、禁网络、禁 worktree read；stdout 作为受限 adapter 输入 | 自写 `.git`/pack/index parser；libgit2 JNI；让 JGit 替代已批准 capture 行为 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| JGit | `org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r` | 仅当某个已列测试出现Java内read-only object/model cross-check具体调用者时加入窄adapter；不得成为VerifiedSourceInventory canonical capture，不得联网。没有调用者时不入POM | 为“可能有用”而引入闲置依赖；自造 Git plumbing | DEFERRED — CALLER-DRIVEN |
| Java 语法/符号 | `com.github.javaparser:javaparser-symbol-solver-core:3.28.2` | 应用发现与程序图只读取 `VerifiedSnapshot` 提供的 canonical UTF-8 bytes；自定义 TypeSolver 仅解析快照内 source roots；不扫描活动目录、不下载依赖、不加载客户 class | regex Java parser、compiler plugin 执行、反射客户代码 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven 模型 | `org.apache.maven:maven-model:3.9.11` | 用 `MavenXpp3Reader`/model classes 严格读取快照内 POM；禁 model builder 的 parent/plugin/repository resolution；所有未解析 property/profile 形成 typed signal/GAP | 自写 XML-to-POM 映射；运行客户 Maven；隐式解析远程 parent | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| TOML | `org.tomlj:tomlj:1.1.1` | 严格 parser；保存 parse positions，拒绝 duplicate/invalid keys；领域层再映射为 closed records | 自写 tokenizer/parser；把 TOML 当 properties | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| JSON Schema | `com.networknt:json-schema-validator:2.0.1` | Draft 2020-12；只加载内容寻址的本地 schema bundle，关闭远程 `$ref`；排除不使用的 YAML；与项目 Jackson 2.21.4 做 dependency convergence 和行为测试 | 3.x（Jackson 3 线）与 Jackson 2 混装；手写通用 schema evaluator；联网取 schema | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| JSON/JSONL codec | 保持 Jackson 2.21.4 | 严格 duplicate/unknown field、整数、UTF-8、final-LF；`CanonicalJsonCodec` 只实现权威领域 canonicalization/identity framing | 第二套 JSON parser；宽松反序列化；通用 pretty-printer 改写 golden bytes | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| CLI | `info.picocli:picocli:4.7.7` | 参数、help、exit code、stdin/stdout adapter；所有业务校验委托 public application seam | 自写 argv parser；CLI 直接打开 store path 或拼路径 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |

版本事实核验日期为 2026-08-31；来源仅使用官方项目页或 Maven Central：

- [Eclipse JGit Maven Central](https://central.sonatype.com/artifact/org.eclipse.jgit/org.eclipse.jgit)
- [JavaParser Symbol Solver Maven Central](https://central.sonatype.com/artifact/com.github.javaparser/javaparser-symbol-solver-core)
- [Apache Maven 3.9.11](https://central.sonatype.com/artifact/org.apache.maven/maven/3.9.11)
- [Tomlj Maven Central](https://central.sonatype.com/artifact/org.tomlj/tomlj)
- [networknt JSON Schema Validator Maven Central](https://central.sonatype.com/artifact/com.networknt/json-schema-validator/2.0.1)
- [Picocli Maven Central](https://central.sonatype.com/artifact/info.picocli/picocli)

### 2.2 测试依赖

| 库/坐标 | 固定建议版本 | 唯一职责 | 禁止重叠 | 状态 |
| --- | --- | --- | --- | --- |
| JUnit Jupiter / `org.junit.jupiter:junit-jupiter` | 5.13.4 | test engine、lifecycle、parameterized/dynamic tests | 不用自建 runner；暂不升级 JUnit 6 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| AssertJ Core / `org.assertj:assertj-core` | 3.27.7 | 可读的对象/集合/异常断言与 custom assertion | 不用它做随机生成、轮询或架构扫描 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| jqwik / `net.jqwik:jqwik` | 1.10.1 | 只覆盖 identity framing、UTF-8 ordering、safe key/path grammar、descriptor root、analysis-step-publication lineage 的性质测试 | 不把 golden contract 改成随机期望；不用于 Provider/model 行为 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| ArchUnit JUnit5 / `com.tngtech.archunit:archunit-junit5` | 1.5.0 | 验证 package/moduleKey 映射、adapter→application→domain 依赖方向、public seam path-free、analysis step 间无反向依赖 | 不重复 PMD/SpotBugs 的代码级规则 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Awaitility / `org.awaitility:awaitility` | 4.3.0 | 只用于 single-process async execution、loopback HTTP 状态与 atomic publication 的有界等待测试 | 不用 sleep；不掩盖非并发 deterministic failure | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |

测试默认不引入 Mockito。I/O、Provider、clock/fault injection 使用窄手写 fake/recording adapter；identity、store、manifest、receipt 不 mock。若未来出现只有 mocking framework 才能隔离的第三方 API，必须给出具体 seam、调用者和新增审批，不得预先加依赖。

默认授权下载时的新增库allow-list必须逐字使用第2.1/2.2节版本：`com.github.javaparser:javaparser-symbol-solver-core`, `org.apache.maven:maven-model`, `org.tomlj:tomlj`, `com.networknt:json-schema-validator`, `org.assertj:assertj-core`, `net.jqwik:jqwik`, `com.tngtech.archunit:archunit-junit5`, `org.awaitility:awaitility`。`org.eclipse.jgit:org.eclipse.jgit`只在出现具体alternate-adapter调用者后按需加入。现有`info.picocli:picocli`, Jackson 2.21.4和`org.junit.jupiter:junit-jupiter`仍须出现在下载清单中以建立完整可审计cache，但不重复添加第二个版本源。所有坐标禁止`LATEST`、`RELEASE`、版本范围和SNAPSHOT。

版本来源：

- [AssertJ Core Maven Central](https://central.sonatype.com/artifact/org.assertj/assertj-core/3.27.7)
- [jqwik Maven Central](https://central.sonatype.com/artifact/net.jqwik/jqwik)
- [ArchUnit JUnit5 Maven Central](https://central.sonatype.com/artifact/com.tngtech.archunit/archunit-junit5/versions)
- [Awaitility Maven Central](https://central.sonatype.com/artifact/org.awaitility/awaitility)

## 3. Maven 构建与质量工具矩阵

所有已批准核心插件用POM `pluginManagement`/properties提供唯一版本源；CI只运行check/report，不运行apply。首次解析精确坐标属于既定范围默认授权但仍服从平台网络控制；依赖已缓存后开发默认加`-o`，避免构建暗中联网。

| 工具 | 固定建议版本 | 责任与配置 | 本地默认 | CI/发布 | 网络/重量 | 状态 |
| --- | --- | --- | --- | --- | --- | --- |
| Maven Enforcer | 3.6.3 | Maven `[3.9.11,4)`、运行 Maven 的 JDK ≥17、dependency convergence、`bannedDependencies` 不允许 Jackson 3、禁止 snapshot deps | `validate` 自动 check | 每次 PR 必跑 | 首次解析后离线；轻 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven Toolchains | 3.3.0 | 从显式`-t .mvn/toolchains.xml`选择`jdk` version 17；禁止读写用户级toolchains；缺file/home/match明确失败，不回退shell JDK | `validate`/编译前 check | 每次 PR 必跑 | 无网络；轻；本机JDK 17已存在 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven Compiler | 3.15.0 | `release=17`、UTF-8、`-parameters`；先不启用会因第三方/generated code 产生噪声的全量 `-Werror` | compile/testCompile | 每次 PR 必跑 | 无额外网络；中 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Surefire | 3.5.5 | `*Test` unit/contract selector；固定 locale/timezone/encoding；Provider/network disabled；POM 明确把默认 false 的 `skipUTs` property 映射到 Surefire `skipTests` | direct selector | PR targeted suite | 无网络；按 selector 轻/中 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Failsafe | 3.5.5 | 仅 `*IT` filesystem/process/adapter integration；`integration-test` + `verify` | 显式 `-Dit.test=` | 分析步骤 gate/最终验收 | 无网络；中/重，必须串行 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Spotless Maven | 3.10.1 | Java 使用 google-java-format 1.36.1；POM/Markdown 不自动重排领域 golden | `spotless:check`；apply 必须显式 | PR 只 check | 首次解析后离线；轻/中 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| google-java-format | 1.36.1 | Spotless 唯一 Java formatter engine | 仅由 Spotless 调用 | 锁定版本 check | 无运行时网络；轻 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| SpotBugs Maven | 4.10.4.0 | bytecode correctness：null/dropped result/resource/threading/equals/hash/serialization 风险；只配置经证实排除 | `-Pquality` | PR/分析步骤 gate | 中/重，串行 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven PMD | 3.28.0 | 源码层 narrow rule set：复杂度、空 catch、异常吞噬、危险 API、设计边界；关闭与 SpotBugs 重叠规则。显式`jdkToolchain.version=17`，使PMD type resolver读取JDK 17类库而非启动Maven的shell JDK | `-Pquality` | PR/分析步骤 gate | 中/重，串行 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven Dependency Plugin | 3.11.0 | used-undeclared/unused-declared；精确列出反射/ServiceLoader 例外；Jackson tree audit | `dependency:analyze-only`/`dependency:tree` | POM 变更必跑 | 缓存后离线；中 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| CycloneDX Maven | 2.9.3 | 生成包含 compile/runtime 的 SBOM；不改变运行 artifact | supply-chain profile 显式 | release/final acceptance | 缓存后离线；中 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| OWASP Dependency Check | 13.0.0 | 只做dependency CVE audit；仅在NVD/API feed、所需credentials与持久cache位置可用时启用；环境未就绪时不伪称安全通过 | 默认 skip | 定时 CI/release，环境门满足后 | 网络且重，必须串行/缓存 DB | ENVIRONMENT-GATED |
| Maven Javadoc | 3.12.0 | public contracts/seams 的 doclint；不要求 private implementation 生成站点 | 显式 profile | release/final acceptance | 缓存后离线；中 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| Maven Shade | 3.6.2 | 仅打 CLI 可执行包；固定 main class、reproducible output、service resource transformer；不 relocate domain/public API | 不进测试默认生命周期 | release package + smoke test | 缓存后离线；中/重 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |

批准下载时的插件 allow-list 必须逐字使用以下坐标和上表版本，不接受无版本或动态范围：`org.apache.maven.plugins:maven-enforcer-plugin`, `org.apache.maven.plugins:maven-toolchains-plugin`, `org.apache.maven.plugins:maven-compiler-plugin`, `org.apache.maven.plugins:maven-surefire-plugin`, `org.apache.maven.plugins:maven-failsafe-plugin`, `com.diffplug.spotless:spotless-maven-plugin`, `com.google.googlejavaformat:google-java-format`, `com.github.spotbugs:spotbugs-maven-plugin`, `org.apache.maven.plugins:maven-pmd-plugin`, `org.apache.maven.plugins:maven-dependency-plugin`, `org.cyclonedx:cyclonedx-maven-plugin`, `org.owasp:dependency-check-maven`, `org.apache.maven.plugins:maven-javadoc-plugin`, `org.apache.maven.plugins:maven-shade-plugin`。

版本来源：

- [Maven Enforcer 3.6.3](https://maven.apache.org/enforcer/maven-enforcer-plugin/enforce-mojo.html)
- [Maven Toolchains 3.3.0](https://maven.apache.org/plugins/maven-toolchains-plugin/usage.html)
- [Maven Compiler 3.15.0](https://maven.apache.org/plugins/maven-compiler-plugin/download.cgi)
- [Maven Surefire/Failsafe 3.5.5](https://maven.apache.org/surefire/maven-failsafe-plugin/plugin-management.html)
- [Spotless Maven Maven Central](https://central.sonatype.com/artifact/com.diffplug.spotless/spotless-maven-plugin)
- [google-java-format Maven Central](https://central.sonatype.com/artifact/com.google.googlejavaformat/google-java-format/1.36.1)
- [SpotBugs Maven Maven Central](https://central.sonatype.com/artifact/com.github.spotbugs/spotbugs-maven-plugin)
- [Maven PMD 3.28.0](https://maven.apache.org/components/plugins/maven-pmd-plugin/download.cgi)
- [Maven Dependency Plugin Maven Central](https://central.sonatype.com/artifact/org.apache.maven.plugins/maven-dependency-plugin)
- [CycloneDX Maven Maven Central](https://central.sonatype.com/artifact/org.cyclonedx/cyclonedx-maven-plugin)
- [OWASP Dependency Check Maven Maven Central](https://central.sonatype.com/artifact/org.owasp/dependency-check-maven)
- [Maven Javadoc 3.12.0](https://maven.apache.org/plugins/maven-javadoc-plugin/download.cgi)
- [Apache Maven 当前插件列表（含 Shade 3.6.2）](https://maven.apache.org/plugins/)

### 3.1 重叠消除决策

以下决策均为 **APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED**：

1. SpotBugs 独占 bytecode/dataflow correctness；PMD 不重复 null/resource/equals 类规则。
2. PMD 独占少量源码设计、复杂度、空 catch、危险 API 规则；不启用全量默认 ruleset 后再大量 suppress。
3. CPD 不作为 blocking gate。重复领域字段往往是跨 schema 的显式合同；误删比重复更危险。若以后需要 CPD，只能生成 report、限定 Java production code、排除 records/fixtures，并另行审批。
4. ArchUnit 只验证包依赖、moduleKey/fixture mapping 和 path-free architecture；不复制 PMD 命名/复杂度规则。
5. PMD必须使用project-tracked JDK 17 Toolchain；仅设置`targetJdk`不足以避免PMD在较新的Maven shell JDK上解析不兼容的class file。该插件支持独立`jdkToolchain`配置，优先于通用Toolchains选择。[官方PMD Toolchains说明](https://maven.apache.org/plugins/maven-pmd-plugin/examples/targetJdk.html)
6. Enforcer 检 dependency convergence/构建环境；Dependency Plugin 检 declared/used；CycloneDX 描述清单；OWASP 在获批 feed 上做漏洞匹配。四者不互相代替。
7. Spotless 是唯一 Java formatter。PMD/Checkstyle 不承担排版；本计划不新增 Checkstyle，避免与 google-java-format 和 PMD 重复。

## 4. 通用文件标准：成熟工具优先，领域算法自有

### 4.1 一条普适规则

Java、JSON/JSONL、TOML/config、Markdown、XML 必须先由成熟、版本固定、fail-closed 的 formatter/linter/parser/schema 工具完成通用语法工作。实现不得自造通用 tokenizer、parser、pretty-printer、schema validator、XML resolver、Git pack reader 或 Java formatter。

允许且必须由本项目自定义的只有领域语义：

- 长度前缀/domain separator/UTF-8 byte-order/descriptor list 的身份 framing；
- artifact policy、root、receipt、manifest 与 immutable publication；
- safe value grammar 和 closed analysis step/module/address mapping；
- source evidence/excerpt、Proof closure、graph/Flow/coverage/accounting；
- registry/meaning lineage、ReaderItem、Candidate、validation 与显式 analysis step execution；
- Provider lifecycle、预算、单次调用失败语义与 single-process execution orchestration。

通用工具只能验证/格式化外层语法，不能重新定义这些 canonical bytes 或领域 ID。

### 4.2 各格式的精确策略

| 格式 | parser/schema/linter/formatter | check/apply 和 canonical 边界 | 状态 |
| --- | --- | --- | --- |
| Java | JavaParser 仅分析客户 source；项目 source 由 javac + Spotless/google-java-format + PMD/SpotBugs | CI `spotless:check`；本地 apply 必须显式。JavaParser AST 不作为项目 source formatter | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| JSON | Jackson strict parser + networknt Draft 2020-12 | 输入拒 duplicate/unknown/remote ref；一般 docs 可人工排版。参与 identity 的 bytes 只由 `CanonicalJsonCodec` 生成/验证，不被 Spotless 或通用 formatter 改写 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| JSONL | 每行 Jackson strict + 对应 line schema；UTF-8/LF/final-LF/非空权限由 policy registry | linter 逐行验证并报告 line number；不整体 pretty-print；identity 顺序由领域合同控制 | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |
| TOML/config | runtime 用 Tomlj；authoring 可选 Taplo format/lint | Tomlj parse errors fail closed。只有出现持续TOML authoring/lint调用者时才采用Taplo，并先从官方发布页验证stable版本、下载来源与SHA-256；未采用不降级到自写 parser | DEFERRED — CALLER-DRIVEN |
| Markdown | 可选 markdownlint-cli2 只检查 docs 风格/fence/link；严格 renderer golden 用专用 byte assertions | 不自动重排 strict Markdown golden、模板或含 identity bytes 的 fence。只有出现持续Markdown lint调用者时才采用，并先从官方npm元数据/发布页核验版本与registry integrity | DEFERRED — CALLER-DRIVEN |
| XML/POM | JDK secure JAXP + Maven Model Reader；schema 已提供时用本地 XSD；禁 DTD/XXE/external entity | POM 模型读取不远程 resolve；客户 XML 的空白可能有语义，不自动 reflow。若机器已有 `xmllint`，仅可 `--noout --nonet` 诊断，不作为唯一 parser | APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED |

Taplo 与 markdownlint-cli2 不在本计划中伪造版本。它们的确定门是：出现具体且持续的authoring/lint调用者 → 仅查官方发布源 → 记录版本、下载URL、SHA-256/registry integrity → 在progress中记录 → 单独小改动启用 → docs fixtures全量验证。该门是采用/完整性门，不是额外用户审批；未触发时核心Java构建不依赖这两个CLI。

## 5. Maven profiles 与精确命令合同

以下命令是已批准的默认执行合同。后续范围内任务可按平台权限直接解析依赖并执行；当前 Round-3 明确为 docs-only，因此本 work unit 不运行它们。

| 场景 | 精确默认命令 | 约束 |
| --- | --- | --- |
| 单个 RED/GREEN selector | `mvn -t .mvn/toolchains.xml -o -Dtest=FrozenRequestAdmissionTest test` | 命令示例使用真实类；其他任务只能替换为第 6 节同一 work unit 的一个精确类名，不得用 wildcard 或空 selector 回退全套 |
| 多个同任务 selector | `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest,CanonicalAnalysisStepArtifactStoreTest test` | 只允许同一 work unit 直接覆盖者 |
| 单个 integration selector | `mvn -t .mvn/toolchains.xml -o -DskipUTs -Dit.test=CanonicalAnalysisStepArtifactStoreAtomicInstallIT verify` | Failsafe `*IT`；文件系统/process 测试串行 |
| 格式 check | `mvn -t .mvn/toolchains.xml -o spotless:check` | CI 与提交前；不改文件 |
| 格式 apply | `mvn -t .mvn/toolchains.xml -o spotless:apply` | 仅实现代理在自己拥有的 Java 文件上显式运行；之后重跑 direct tests |
| 静态质量 | `mvn -t .mvn/toolchains.xml -o -Pquality -DskipTests verify` | Enforcer + compile + SpotBugs + PMD + dependency analyze；重型串行 |
| Jackson 收敛 | `mvn -t .mvn/toolchains.xml -o dependency:tree -Dincludes=com.fasterxml.jackson.*` | 输出必须只有 Jackson 2.21.4 版本线；再跑 JSON/schema selector |
| SBOM | `mvn -t .mvn/toolchains.xml -o -Psupply-chain -Ddependency-check.skip=true cyclonedx:makeBom` | 不访问漏洞库；输出纳入 release review，不提交临时 target |
| 漏洞审计 | `mvn -t .mvn/toolchains.xml -Psecurity org.owasp:dependency-check-maven:13.0.0:check` | 仅在NVD/API feed、所需credentials与持久cache位置已配置时；不得与其他Maven命令并行 |
| Javadoc | `mvn -t .mvn/toolchains.xml -o -Prelease -DskipTests javadoc:javadoc` | public seam doclint；失败不得被静默 skip |
| CLI package/smoke | `mvn -t .mvn/toolchains.xml -o -Prelease -DskipTests package` 后运行固定本地 help/invalid-request fixture | Shade 只在 release profile；无 source/provider/network |

命令示例中的类名均是本计划声明的真实 selector。执行其他 work unit 时只能逐字使用第 6/7 节列出的类名；若该 selector 尚不存在，Luna 的第一步就是创建该精确类并确认针对预期缺失行为 RED。

默认生命周期政策：

- `test` 不绑定 SpotBugs、PMD、CycloneDX、OWASP、Javadoc、Shade，保证 direct selector 快速且可离线。
- `quality` 是独立、串行 gate；只在 direct selectors GREEN 后运行。
- `security` 和 `supply-chain` 分离：SBOM 可离线，漏洞 DB 需要明确网络审批，二者不得混写成一个模糊“安全通过”。
- `release` 才运行 Javadoc、Shade 与本地 smoke；不触发 source capture 或 Provider。
- 首次范围内解析保存`mvn -t .mvn/toolchains.xml dependency:go-offline`的实际输出、selected JDK 17和本地cache事实；未来命令加`-o`。cache miss只可按默认授权解析已列精确坐标，并仍服从平台网络控制。

## 6. 八个分析步骤输出与直接 selector 冻结表

下表不改变权威 schema；它只把实现 work unit 与可观察输出关联。所有 selector/任务划分均为 **APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED**。

| Work unit | 直接 selector | reader-visible analysis step/run 输出 |
| --- | --- | --- |
| Foundation/artifact/runtime | `CanonicalJsonCodecTest`, `CanonicalArtifactPolicyRegistryTest`, `AnalysisStepAddressTest`, `CanonicalModuleArtifactStoreTest`, `CanonicalAnalysisStepArtifactStoreTest`, `CanonicalRunManifestStoreTest`, `RunExecutionStateTest`, `SourceAnalysisArchitectureTest`; integration：`CanonicalAnalysisStepArtifactStoreAtomicInstallIT` | 无分析步骤输出；冻结 typed refs、policy、store/run-manifest seam及`QUEUED/RUNNING/FINISHED/FAILED`单进程状态 |
| Verified source inventory | `FrozenRequestAdmissionTest`, `VerifiedSourceIndexerTest`, `VerifiedSourceInventoryPublicationSpecifierTest` | `source-input.json`, `verified-snapshot.json`, `source-inventory.jsonl`, `verified-source-inventory-receipt.json`（3 semantic + receipt） |
| Application discovery | `ApplicationProfileDetectorTest`, `SpringHttpEntryDiscovererTest`, `MapperCapabilityCatalogerTest`, `ApplicationDiscoveryPublicationSpecifierTest` | `application-profile.json`, `entry-points.jsonl`, `mapper-catalog.jsonl`, `capability-report.json`, `application-discovery-receipt.json`（4 + receipt） |
| Program graphs | `CodeStructureGraphBuilderTest`, `CallGraphBuilderTest`, `ControlFlowGraphBuilderTest`, `DataFlowGraphBuilderTest`, `EvidenceGraphBuilderTest`, `ProgramGraphsPublicationSpecifierTest` | 五 graph JSON + `graph-index.json` + `graph-gaps.jsonl` + `program-graphs-receipt.json`（7 + receipt） |
| Proven code facts | `FactCandidateEnumeratorTest`, `AtomicProofBuilderTest`, `ProvenCodeFactsPublicationSpecifierTest` | `proven-facts.json`, `proof-pack.json`, `gap-ledger.json`, `fact-accounting.json`, `proven-code-facts-receipt.json`（4 + receipt） |
| Business flows | `EntryRootedFlowCompilerTest`, `EvidenceCapsuleProjectorTest`, `BusinessFlowsPublicationSpecifierTest` | `flow-slices.json`, `flow-coverage.json`, `entry-dispositions.jsonl`, `evidence-capsules.jsonl`, `flow-gaps.jsonl`, `business-flows-receipt.json`（5 + receipt） |
| Flow interpretation | `RegistryProposalTaskCompilerTest`, `RegistryProposalRunnerTest`, `RepositoryInterpretationRegistryFreezerTest`, `FiniteKeyFlowTaskCompilerTest`, `InterpretationRunnerTest`, `CrossFlowCandidateCompilerTest`, `BusinessProcessTaskCompilerTest`, `BusinessProcessInterpretationRunnerTest`, `FlowInterpretationPublicationSpecifierTest` | 九项local registry/task/round/candidate/disposition payload + 五项cross-Flow process payload + `flow-interpretation-receipt.json`（14 + receipt） |
| Repository knowledge | `ProposalAdmissionEngineTest`, `AnchoredKnowledgeMergerTest`, `RepositoryKnowledgePublicationSpecifierTest` | `knowledge-admission-decisions.jsonl`, `repository-business-knowledge.json`, `knowledge-conflicts.jsonl`, `knowledge-accounting.json`, `merged-gaps.json`, `repository-knowledge-receipt.json`（5 + receipt） |
| Nine-section document | `NineSectionPlannerTest`, `PlanOnlyRendererTest`, `TypedTraceCompilerTest`, `CandidateRunArchiverTest` | 五 semantic → `nine-section-archive-manifest.json` → `nine-section-document-receipt.json` → root `run-manifest.json`；然后 observation-only M4 module receipt |
| Exterior validation | `IndependentRunValidatorTest` | run 外 validation publication/receipt；不改 Candidate 或 analysis step publications |
| Public/adapters | `RepositoryAnalysisAgentContractTest`, `RepositoryAnalysisCliAdapterTest`, `RepositoryAnalysisLoopbackHttpAdapterTest`; integration：`RepositoryAnalysisLoopbackHttpAdapterIT` | 同一 public request/query/result seam；`executeStep`只接精确上游publication refs并创建新执行；应用发现至仓库知识的分析步骤区间可`FINISHED`但result/root manifest为空，执行到九章文档才有四值result；CLI/loopback HTTP 不增业务分支、不接收 caller paths |

程序图的“五 graph JSON”精确为 `code-structure-graph.json`, `call-graph.json`, `control-flow-graph.json`, `data-flow-graph.json`, `evidence-graph.json`。流程解释的十四项精确为 `registry-proposal-tasks.jsonl`, `registry-proposal-rounds.jsonl`, `registry-proposal-dispositions.jsonl`, `repository-interpretation-registry.json`, `flow-model-tasks.jsonl`, `model-rounds.jsonl`, `generation-receipts.jsonl`, `interpretation-candidates.jsonl`, `flow-interpretation-dispositions.jsonl`, `process-evidence-groups.jsonl`, `process-model-tasks.jsonl`, `process-model-rounds.jsonl`, `business-process-hypotheses.jsonl`, `process-interpretation-dispositions.jsonl`。

全 run reader-visible 基数保持：47 个 semantic 分析步骤 payload + 8 个语义 receipt + 1 个 `nine-section-archive-manifest.json` + 1 个 root run manifest = 57。Module artifacts/receipts 和 exterior validation publication 不混入这 57 项；新的分析步骤执行产生普通目标 publication，不新增第58种正式输出。

## 7. 按分析步骤执行地图

### Task 0：先完成并发布权威设计（代码硬前置）

**文件：** `docs/DESIGN.md`, `docs/analysis-steps/01-...md` 至 `08-...md`, 各代理自有 `progress/<task-slug>.md`

- [ ] Sol/ultra 完成 `sources/source-code`、Maven/Java/package registry、八个语义分析步骤、Wire Reset 与 semantic receipt 设计，不改批准业务架构。
- [ ] 运行 JSON/JSONL parse、ID grammar/reference、upstream closure、moduleKey/address、九章文档 DAG、fence、link/path/stale-name scan、`git diff --check`。
- [ ] 独立 Sol/ultra 做固定 P0/P1 architecture + exact-fixture review；P0/P1 为零才能通过。
- [ ] 父任务只提交/推送 authoritative docs 与各自 progress；确认 origin/main 含该 commit。
- [ ] 在任何 POM/code/test task 开始前记录 docs commit SHA。

### Task 1：Wire Reset 与工具链/artifact/runtime foundation

**计划文件：** `.mvn/toolchains.xml`, `pom.xml`, `src/main/java/org/sourceanalysis/app/artifact/`, `.../evidence/`, `.../runtime/`, `.../validation/`, 对应 `src/test/java` 与 `src/test/resources/analysis/foundation/`。实施先把整个 Agent 移到 `backend-agents/sources/source-code/`，设置 `org.sourceanalysis:source-code-analysis-agent`，删除 pre-reset 包/fixture，并建立旧 wire fail-closed 测试；不保留兼容 reader。

- [ ] Luna/xhigh 先创建 `SourceAnalysisArchitectureTest`, `PreResetWireRejectionTest`, `CanonicalJsonCodecTest`, `CanonicalArtifactPolicyRegistryTest`, `AnalysisStepAddressTest` 的最小 RED；每个 fixture 对应已发布 schema/identity，不创建平行合同。首个3–5小时slice只从`CanonicalJsonCodecTest#encodesCanonicalObjectWithUtf8ByteOrderedKeys`这一个行为开始，拥有`CanonicalJsonCodec`、`ImmutableBytes`与typed identity/address primitives；不创建store publication、receipt/manifest、runtime/validation/evidence record、JSONL/RAW_UTF8 writer或业务分析能力。
- [ ] Terra/xhigh 只实现让这些foundation selectors逐行为GREEN的codec/policy/value/address/package rules。
- [ ] Luna/xhigh 再创建 `CanonicalModuleArtifactStoreTest`, `CanonicalAnalysisStepArtifactStoreTest`, `CanonicalRunManifestStoreTest`, `RunExecutionStateTest` 的 atomic/collision/reopen/partial-install 与四状态 RED。
- [ ] Terra/xhigh 实现三个 public deep seams、最小 single-process execution state 与共享 private atomic filesystem machinery；无 caller path/prefix。
- [ ] Sol/ultra 只处理 unexpected RED、合同歧义、identity/DAG 偏差；需要架构变化即停止并请求用户批准。
- [ ] 串行运行本任务列出的direct selectors、`spotless:check`、`-Pquality -DskipTests verify`、dependency convergence；双轴 review 后由父任务提交/推送。

### Task 2：已验证源码清单

- [ ] Luna/xhigh 按 M1→M2→M3 顺序创建/收紧三个 exact selectors；包含隔离 Git CLI environment、complete/bounded denominator、media/symlink/gitlink、M3 三 semantic、receipt-last/partial-install。
- [ ] Terra/xhigh 依次只改 `org.sourceanalysis.app.capture.localgit` 与 `org.sourceanalysis.app.analysis.inventory`；runtime module keys仍是`request-admission`, `source-index`, `publish`，每个 selector 单独 GREEN。
- [ ] Sol/ultra debug 只在 capture/object-format/identity 或跨 store 合同问题介入。
- [ ] 输出四文件 strict golden；analysis step review/quality gate；独立 review；父任务提交/推送。

### Task 3：应用发现

- [ ] M1 `ApplicationProfileDetectorTest` 先 RED/GREEN，固定 closed signals、profile ref、coverage input。
- [ ] M1 完成后，最多两个代理并行：Luna 为 M2 建 RED，同时 Terra 完成已审查的 M1 GREEN 修正；M2 与 M3 不在同一 mutable fixture 文件并发编辑。
- [ ] M2 `SpringHttpEntryDiscovererTest` 与 M3 `MapperCapabilityCatalogerTest` 分别 RED/GREEN，统一 typed `SourceLocatorV1`/`SourceExcerptV1`/`CapabilitySiteV2`。
- [ ] M4 `ApplicationDiscoveryPublicationSpecifierTest` 验四 semantic、全 denominator、receipt-last；analysis step gate/review/提交/推送。

### Task 4：程序图

- [ ] 按 M1→M5 graph dependency 顺序逐 selector RED/GREEN；JavaParser solver 只能读 verified bytes 和 frozen profiles。
- [ ] M2/M3/M4 可在共同 M1 IDs/schema 固定后做“Luna 下一模块 RED / Terra 前一模块 GREEN”的双代理流水，不并发重写共享 golden。
- [ ] M5 evidence graph 证明每 node/edge locator/provenance；M6 publication 证明七 semantic direct lineage、root/receipt。
- [ ] 七输出 + receipt gate；独立 review；父任务提交/推送。

### Task 5：已证明代码事实

- [ ] Luna 为 candidate/atom/disposition closure 建 `FactCandidateEnumeratorTest` RED；Terra GREEN。
- [ ] Luna 为 Proof rule/root/closed atom/Gap 守恒建 `AtomicProofBuilderTest` RED；Terra GREEN。
- [ ] M3 publication 只组合四 semantic；analysis step store receipt-last；analysis step gate/review/提交/推送。

### Task 6：业务流程

- [ ] `EntryRootedFlowCompilerTest` 覆盖每 ApplicationDiscovery entry 唯一 COMPILED/GAP/EXCLUDED、outcomes/branches/calls/facts accounting，以及从当前Fact/Proof/Evidence/source closure有限提取的`processJoinSignals`；不得从名称或仓库归属猜`DOMAIN_SPECIFIC`。
- [ ] `EvidenceCapsuleProjectorTest` 覆盖 raw continuous excerpts、signal-basis projection obligations、minimal closure、每 compiled flow 恰一 capsule且Flow/Capsule signals逐字相等。
- [ ] `BusinessFlowsPublicationSpecifierTest` 覆盖M1 v2、M2 v5、public Flow v2/Capsule v3、五 semantic、signal ID coverage与0Flow非空 accounting；同步store和R0/R1/R2 local readers，不保留旧wire alias；analysis step gate/review/提交/推送。

### Task 7：流程解释

- [ ] R0 compiler/runner/freeze 三 selector 严格串行冻结 registry；Provider fake 记录 exact request bytes、configured/expected/observed runtime、round与generation receipt identity。
- [ ] R1/R2 task compiler 保存 canonical provider input bytes 或其直接内容寻址 ref，作为可观察业务输入与下游复用格式；runner 只从已验证artifact重开，不依赖内存草稿。
- [ ] M6 `CrossFlowCandidateCompilerTest`只从已发布、Proof闭合的signals和finite Registry cues编`C`条候选edge与覆盖全部Flow的`G`个evidence group；generic-only anchor不得形成`SHARED_ANCHOR`，全部positive/counter bases与blocking集合必须闭合。
- [ ] M7 `BusinessProcessTaskCompilerTest`对全部`A`个owner shard与`S⊆A`个model-safe shard守恒，确定性地把path-bearing persisted material投影为path-free packet；预算超限形成typed no-model Gap而不截断。
- [ ] M8 `BusinessProcessInterpretationRunnerTest`覆盖P1/P2 request、round、receipt、review与GAP/FAILED/NOT_RUN分支；started Provider failure无retry/switch/resume。
- [ ] M9 publication 验十四 semantic、planned=`E+2R+2S`、actual=`E+R+acceptedLocalR1+S+acceptedProcessP1`、local R0/R1/R2 shard denominator=`E/R/R`、`|process dispositions|=A`及六类hypothesis分区；不把 Provider nondeterminism 混入 identity 规则。
- [ ] Provider/runtime failure integration 属重型 selector，单独串行；started调用失败必须令run失败且不得自动重试/切换；analysis step gate/review/提交/推送。

### Task 8：仓库知识

- [ ] Admission selector 覆盖 KEEP/NARROW/DROP/NEEDS_EVIDENCE；NARROW 保持 `selectedKey` 不变，只收窄 decision/basis/meaning eligibility。
- [ ] Merge selector 覆盖 typed anchors、owner、conflict、facts/meanings/gaps/accounting 单一仓库快照。
- [ ] Publication selector 覆盖五 semantic、lineage 与 receipt；analysis step gate/review/提交/推送。

### Task 9：九章文档与 validation

- [ ] Planner/renderer/trace 三 selector 逐个 RED/GREEN；renderer 仅吃 plan bytes，确定性复现九个 H2 UTF-8/LF bytes；trace 保存完整 registry→meaning→proof→source 链。
- [ ] Archiver selector 验证 five semantic → archive → analysis step receipt → root manifest → observation-only M4；任一partial install不得被误认成功，完整安装可fresh reopen且DAG不成环。
- [ ] `IndependentRunValidatorTest` 从明确的八个 typed analysis step publication refs、run manifest、request/schema/source refs 重开；只写幂等 validation artifacts。
- [ ] 九章文档与 exterior validator 分开 review，但在同一完整 DAG gate 后才提交/推送。

### Task 10：public seam、CLI 与 loopback HTTP adapters

- [ ] `RepositoryAnalysisAgentContractTest` 先冻结 request-v2、`AnalysisStepExecutionRequest`、sealed `ANALYSIS_STEP | VALIDATION` publication views、无 caller path；证明仓库知识新执行可直接消费经验证的流程解释 refs且不触发前六个分析步骤，并验证仓库知识-only `FINISHED/analysisResult=null/no run manifest`与九章文档完成态互斥。
- [ ] CLI adapter 只做 Picocli parsing/rendering；invalid request/exit code/stdout-stderr golden；不另开 store 或 source path。
- [ ] Loopback HTTP adapter 复用同一 application seam；schema/status/idempotency mapping；无网络型 integration，仅本机 ephemeral port 且串行。
- [ ] Shade release 包 local `--help`、invalid request、fixture query smoke；不 capture、不 Provider、不外网。
- [ ] 双轴 review、quality/release gate；父任务提交/推送。

### Task 11：最终验收

- [ ] 从 clean checkout 和已批准 JDK/Maven toolchain 开始；先离线 direct/regression selectors，再串行 integration/quality/release。
- [ ] 对 57 个 reader-visible outputs、module/analysis step/run receipts和validation artifacts做 fresh-process reopen，并验证FlowInterpretation→RepositoryKnowledge显式新执行。
- [ ] 重跑 identity property tests、partial-install matrix、bounded/complete analysis-result matrix、CLI/HTTP contract tests。
- [ ] 生成 SBOM；只有漏洞DB feed、所需credentials与持久cache环境门满足后才运行并报告OWASP result，否则明确记为“环境未就绪，未执行”，不能写PASS。
- [ ] 独立 Standards/Spec 双轴 review；零 P0/P1；父任务提交/推送最终验收文档/必要修正。

## 8. 代理协作、progress 与发布纪律

所有条目均为 **APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED**：

1. 同时最多两个活跃代理，包括 orchestrator；任何 Maven 重型命令开始前，另一代理不得运行 Maven。
2. Luna/xhigh 负责 RED：先把权威记录/identity/partial-install/negative case 变成最小直接测试；不得先写 production。
3. Terra/xhigh 负责 GREEN：只编辑该 selector 所属 implementation/package；不得顺手改下一 analysis step、POM 或权威 schema。
4. Sol/xhigh 负责 unexpected RED/debug；Sol/ultra 负责跨模块一致性和 review，并可在既定目标内作有界local/adjacent合同决定而不逐项重请批准；不得把本地 bug 包装成新架构。只有需要改变已批准的material架构边界、信任强度、accounting/守恒合同或公开API时才停止并请求用户决定。
5. 每个代理在编辑前创建自己唯一的 progress，例如应用发现 M2 使用 `progress/source-analysis-application-discovery-http-entry.md`；记录 owner、范围、exact selectors、RED/GREEN 输出、文件、阻塞、下一动作。代理不得共写同一 progress。
6. 每个模块安全边界更新 progress；每个 analysis step 完成后运行 direct selectors、doc/golden gates、质量 gate、独立 review。
7. 代码任务只在 authoritative docs 已发布到 origin/main 后开始。每个 toolchain/analysis step/adapters work unit 独立 review、独立 commit、独立 push；不得把不相关 dirty changes 混入。
8. commit/非强制push在既定项目范围内默认授权，但仍由拥有完整diff和发布gate的父任务统一完成；实施子代理不私自混入或发布局部状态。
9. 测试范围遵循 scoped AGENTS：默认只跑新增或直接覆盖当前改变的 selector；全仓 suite 需要用户在当轮明确要求。

## 9. 连续工期、关键路径与并行窗口

以下为 **APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED** 的工程估算，假定规格在进入 Task 1 前稳定、最多两个代理、重型 Maven 串行，且依赖cache、feed/JDK等环境门等待时间不计入。估算是连续 wall-clock elapsed，不是两名代理工时相加。

| 分析步骤 | 连续 elapsed | 包含 |
| --- | ---: | --- |
| 工具链 foundation | 8–12 小时 | 批准后的 POM、profiles、codec/policy、三 stores、最小四状态execution、architecture tests、quality gate |
| 已验证源码清单至业务流程 | 58–82 小时 | 3+4+6+3+3 个模块的 RED/GREEN、goldens、analysis step review/commit/push |
| 流程解释至九章文档 + validation + adapters | 38–54 小时 | 9+3+4 模块、一个 exterior validator、显式analysis step execution、public/CLI/HTTP、Provider失败门 |
| 最终验收 | 10–14 小时 | clean/offline fresh-reopen、57-output reopen、quality/release/SBOM、独立双轴 review |
| **active v0总计** | **114–162 连续小时** | 约 15–21 个 8 小时工作日；不含等待用户审批、下载或外部 CI 排队，也不含同一run自动恢复TODO |

关键路径不可压缩为并行 analysis step：

`authoritative docs review/push → toolchain/contracts/stores → VerifiedSourceInventory → ApplicationDiscovery → ProgramGraphs → ProvenCodeFacts → BusinessFlows → FlowInterpretation → RepositoryKnowledge → NineSectionDocument → validation → public adapters → final acceptance`。

允许的双代理并行仅发生在稳定接口两侧：

- Luna 为“下一模块”写 RED 时，Terra 为“上一模块已审查 RED”写 GREEN；不得并发修改同一 fixture/schema/progress。
- ApplicationDiscovery M2/M3 的独立输入 fixture、ProgramGraphs 已冻结 M1 后的相邻 graph module、CLI/HTTP adapter RED 可与 core public seam 的最终 GREEN 交错。
- 文档/fixture mechanical validation 可与非重型代码阅读并行；不能与重型 Maven 并行。
- SpotBugs、PMD、Failsafe、OWASP、Shade/release、完整 analysis step partial-install matrix 必须串行。

若实际连续 elapsed 超过某分析步骤上界 25%，owner 必须先更新 progress，列明是合同缺口、fixture 返工、工具兼容还是环境等待，再由 Sol/ultra 判断是否需要重新估算；不得通过跳过 gate 追赶时间。

## 10. 默认授权、按需/环境门与平台边界

| 未来动作 | 当前授权状态 | 执行边界 |
| --- | --- | --- |
| Maven Central 核心库下载 | 默认授权 | 只下载第2节精确坐标/版本；服从平台网络审批，不访问客户或其他外部源码，不使用动态版本 |
| Maven 核心插件下载 | 默认授权 | 只下载第3节精确坐标/版本；OWASP feed与deferred工具不随核心插件隐式启用 |
| POM、格式化与范围内测试 | 默认授权 | 必须属于当前明确work unit；只跑新增或直接覆盖测试，重型Maven串行；不得借格式化改写canonical/golden bytes |
| commit 与非强制 push | 默认授权 | 仅提交当前owner的已验证文件；先审查dirty worktree；禁止force push、破坏性reset或混入无关改动 |
| OWASP 漏洞源 | ENVIRONMENT-GATED | feed、所需credentials与持久cache未就绪时保持skip，不得声称漏洞审计通过；就绪后按计划串行启用，无需重复询问 |
| JDK 安装 | ENVIRONMENT-GATED | 当前已有可用JDK 17并由project-local Toolchains选择，所以不安装；仅在目标host确实没有合格JDK 17时选定vendor/build/checksum并安装 |
| Maven Wrapper | DEFERRED — CALLER-DRIVEN | 当前Maven 3.9.16满足约束；只有出现可复现分发/CI调用者时生成并验证wrapper |
| JGit alternate adapter | DEFERRED — CALLER-DRIVEN | 继续只用隔离Git CLI；出现具体Java内cross-check调用者时才加窄adapter，否则不加闲置JGit |
| Taplo | DEFERRED — CALLER-DRIVEN | TOML runtime仍由Tomlj；出现持续authoring/lint调用者后按官方版本/完整性门采用，不自造formatter |
| markdownlint-cli2 | DEFERRED — CALLER-DRIVEN | 继续现有fence/link/JSON机械gate；出现持续lint调用者后按官方版本/registry-integrity门采用，不自造Markdown formatter |

平台若对网络、安装、凭证或Git写入弹出强制审批，执行者按最小精确目标申请；不得把用户的默认授权解释为绕过平台控制。创建、审阅和维护本计划本身不产生下载、POM或实现变更。

## 11. 自审结论

### 11.1 覆盖性

- 已覆盖 Git、Java 符号、Maven model、TOML、JSON Schema、CLI 和五类测试库。
- 已覆盖 Enforcer、Toolchains、Compiler、Surefire/Failsafe、Spotless/gjf、SpotBugs、PMD、dependency analysis、CycloneDX、OWASP、Javadoc、Shade。
- 已覆盖 Java、JSON/JSONL、TOML/config、Markdown、XML 的 parser/linter/formatter/schema 规则与 canonical-byte 例外。
- 已覆盖 八个分析步骤、exterior validation、显式analysis step execution、public/CLI/HTTP、57 项 analysis step/run outputs、直接 selectors、docs-only publish gate、review/commit/push。
- 已覆盖双代理限制、重型 Maven 串行、Luna RED/Terra GREEN/Sol debug、每代理独立 progress、连续工期与审批等待分离。

### 11.2 工具重叠

- Java 排版只有 Spotless/gjf；源码规则只有 narrow PMD；bytecode correctness 只有 SpotBugs；architecture 只有 ArchUnit。
- Enforcer、Dependency Plugin、CycloneDX、OWASP 分别负责环境/收敛、声明使用、清单、漏洞匹配；没有互相替代或重复宣称。
- CPD 明确不作为 blocking gate；Checkstyle 不引入；Mockito 不预装。
- Jackson/networknt/Tomlj/Maven model/JavaParser 各自只处理其标准语法，领域 identity/evidence/flow 仍由明确模块负责。

### 11.3 版本信心

- 固定版本均已在 2026-08-31 从 Maven Central 或 Apache Maven 官方页核验。
- networknt 选 2.0.1 是为了保持 Jackson 2 线；最终兼容性必须由 Enforcer tree + JSON Schema contract test 建立，而非依赖元数据推断。
- Taplo、markdownlint-cli2、Git CLI最低兼容行为、未来JDK vendor build不猜版本；caller-driven adoption或环境兼容性均有精确验证门。
- 核心决定标为`APPROVED CORE — IN-SCOPE EXECUTION DEFAULT AUTHORIZED`，非核心项标为`DEFERRED — CALLER-DRIVEN`或`ENVIRONMENT-GATED`；没有保留额外用户审批阻塞，也没有写成已安装、已兼容或已通过安全审计。

### 11.4 无含糊待办

计划没有把缺失决策留给实现代理自由发挥。命令模板使用第6/7节已列出的真实selector类名；deferred/environment-gated工具都有明确触发条件、版本/完整性门和未触发时的skip行为。

## 12. 已批准核心包

用户已批准以下核心包；其范围内下载、POM、格式化、直接测试、提交与非强制推送不再重复询问，仍服从第10节边界：

1. 保持隔离Git CLI为VerifiedSourceInventory canonical capture，禁止自造Git plumbing；JGit暂不入POM，只有具体cross-check调用者时按需加入并验证。
2. 采用 JavaParser Symbol Solver 3.28.2、Maven Model 3.9.11、Tomlj 1.1.1、networknt 2.0.1、Picocli 4.7.7，并保持 Jackson 2.21.4 收敛。
3. 采用 JUnit 5.13.4、AssertJ 3.27.7、jqwik 1.10.1、ArchUnit 1.5.0、Awaitility 4.3.0；不预装 Mockito。
4. 采用第 3 节 Maven 核心质量矩阵；PMD/SpotBugs 分工，CPD 非阻塞，CI check-only。
5. 采用第4节通用格式规则；Taplo、markdownlint-cli2、Maven Wrapper按具体调用者启用，OWASP feed/JDK安装按环境事实启用，不再重复请求用户批准。
6. 采用 docs-only publish gate、最多双代理、重型 Maven 串行、逐 analysis step RED/GREEN/review/commit/push 和 114–162 小时 active v0 连续 elapsed 预算；同一run自动恢复的56–96小时独立留在TODO，不进入实现计划。

批准后的第一项动作仍不是改 POM：先从各Agent自己的progress工作断点完成当前权威设计修正、独立复审并发布 docs；随后才开始只含工具链 foundation 的独立实现 work unit。
