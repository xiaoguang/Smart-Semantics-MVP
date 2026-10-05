# 本体独立配置：加载、绑定及操作边界

状态：2026-10-05，固定客户O0–O3已通过正式命令有限结束、保存和查询。O1六任务REVIEWED；O2财务和统计REVIEWED、采购关系因最终scope错误REJECTED；O3部分发布四文件，九项声明义务保留为八REVIEWED/一REJECTED，覆盖INCOMPLETE。18份真实请求的完整原文、用途、限制、同题目录及同包审阅已核对；模型遗漏和错误单列，不由Java补业务答案。最后复审两项框架缺陷均已RED→GREEN：8129直接回归20项全绿；最终质量6695通过（767文件格式清洁、SpotBugs零发现/错误、PMD通过，UT/IT跳过）。实现PR正在交付，不将模型拒绝和残余误读改写成业务成功。准确完整ID、实际成本和结果见[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)，剩余动作见[稳定性计划](../../plans/ontology-stability-implementation-plan.md)。不宣称自主全仓、任意小窗口兼容或模型零错误；旧MD路径和历史结果保持原状。

`OntologyConfiguration`严格读取`ontology-config-v1`、快照有效UTF-8 Prompt文本和非凭据Provider声明，并复用Provider声明校验，不读取认证环境或构造Provider。Task 5的formal typed-v3入口消费调用者提供的不可变OBJECT/ACTION/ANALYTIC/RELATE/REVIEW Prompt和独立字节/token限制快照；它不重新按资源路径读取Prompt。默认 Prompt 是本体专属 formal 资源，不复用实验 v1/v2 或旧 Activity/Process Prompt。正式运行时已有显式O0–O3和独立extract/relate路由的定向结果；实际适配器token能力、DISCOVERY路径、完整准入和失败观察仍须各自验收，不能由配置读取测试代替。

## 1. 配置输入

`ontology-config-v1`使用独立YAML。准确R4/O0和选择清单来自命令参数，不隐式寻找最新运行。配置只包括以下字段：

| 字段 | 内容和检查 |
| --- | --- |
| schemaVersion | 固定`ontology-config-v1` |
| storage | root、preparedSourceArchive、sourcePreparationPolicyRegistry、evidencePolicyRegistry、ontologyPolicyRegistry；可选upstreamArtifactPolicyRegistries。均为明确的绝对路径；后者是需重开的历史本体或技术上游owner策略的显式、无重复路径列表 |
| reading | maxUnitBytes、maxRequestBytes、maxOutputBytes、maxOutputTokens、maxRequests、maxReadingRounds、maxActionsPerRound、maxNavigationEntries；均为正整数，分别执行，不互换单位 |
| schemaSources（可选） | 同R0的源码相对路径数组；不接受宿主绝对路径、重复项或越界路径；为空不解析DDL |
| prompts（可选） | survey、prioritize、reading、object、action、analytic、relate、review的UTF-8文件覆盖，填写绝对文件路径；未知键拒绝。未覆盖键使用本体自有正式资源，不使用旧Activity/Process Prompt |
| modelJobs（可选） | maxConcurrentJobs、providers、routing；routing准确为survey/extract/relate三个键，使用现有Provider配置加载器，不要求旧activity/processGroup/repositorySummary/report路由 |

`modelJobs`可以省略，以便O0、O3和查询零模型运行。O1/O2确需派发请求时才要求对应路由及Provider。QUESTION的MODEL阅读使用O1既有extract路由及reading Prompt/限制，不额外要求survey；DISCOVERY的调查、选题和MODEL阅读使用survey路由，两者已分别通过正式scripted运行回归。每一任务的REVIEW保持其提取/关联绑定；多个绑定用于明确任务分配，不是失败回退。默认不自动重试。配置缺失不会触发旧业务路径。真实选材质量未获证明。

真实`codexSubscription`运行须在Provider声明中填写本机Codex可执行文件的绝对路径`executable`，并通过现有认证环境变量提供Codex home。配置加载可保持声明式且不登录；O1/O2在排队及计数前检查实际选用的Provider，路径缺失返回`ONTOLOGY_PROVIDER_EXECUTABLE_REQUIRED`，不可读或不可执行及认证环境问题按现有配置校验报告。scripted测试注入不要求本机可执行文件，O0/O3及查询不做此检查。示例路径是占位符，不会替用户寻找可执行文件或回退API key。

这些上限由调用者选择，示例数值不是已经证明的模型窗口。实际完整请求（Prompt、Schema、输入及实际草稿）在发送前检查；响应字节和输出token分别处理。未知token计数必须披露，不用字节减少代替模型窗口证明。输出token配置还受适配器实际能力限制；当前Codex订阅路径不能宣称强制执行该上限，须按[Provider边界](provider-boundary.md)保存并展示未执行状态。

## 2. 不混用存储策略身份

源码准备、技术证据、本体输出分别按其真实发布时的策略注册表重开。不能把新本体policy追加到旧证据policy文件后，用新注册表身份重开旧R4；canonical store检查准确policy身份。配置中的三份policy路径是三个用途，允许内容相同时复用文件，但程序仍检查实际receipt/controls。

本体输出使用`ontologyPolicyRegistry`；R4使用`evidencePolicyRegistry`；R0使用`sourcePreparationPolicyRegistry`。R4明确引用的历史R2可以有不同owner，补读按该R2保存请求的完整owner身份，从技术policy及显式上游列表核对原规则、receipt和controls；不能要求历史R2与新版R4策略相等，也不能去掉owner核验。公共store仍是既有canonical store，不创建第二套存储。配置路径不是来源证明，正式runtime必须验证已安装receipt、header及来源版本。

新运行使用`ontologyPolicyRegistry`；重开已保存O0–O3、选择上游或查询时，runtime只在它和可选`upstreamArtifactPolicyRegistries`的逐项明确列表中，按保存请求的`artifactPolicyRegistryRef`计算完整身份。必须恰好匹配一份才重开；缺失或重复匹配均拒绝。不扫描目录、不按文件名或当前目标策略回退/反推，也不改写历史策略文件。改变Prompt、模型或阶段容量不表示可以替换已保存O0的策略身份；更换本体策略后新准备链使用新owner，旧结果仍按自身准确owner读取，canonical store的一致性检查不放宽。

## 3. 保存真正使用的配置

加载后生成不可变配置快照。Prompt文件的实际UTF-8文本与摘要进入快照，不能只保存路径；Provider只保存现有不含凭据的声明身份。配置、上限、Schema/投影规则、本体Prompt与模型绑定分别生成准确引用，供OntologyInputs绑定。

排队后再次核对配置及Prompt内容；有变化需创建新运行。查询已结束运行使用已保存快照，不能因原Prompt文件或Maven/JDK目录消失而无法查看。认证信息只在实际模型执行时按既有Provider处理，不写进本体产物或普通配置快照。

这个复核针对**当前已排队运行自己的配置**，不是要求O0、O1、O2和O3使用相同配置摘要。每个上游按它自己的保存请求和controls核验，当前操作再绑定自己的配置。例如O0可以没有modelJobs，O1另用明确模型配置；O3仍可以没有modelJobs。后续始终消费准确的已保存O0身份，不用当前Prompt或模型配置重新计算、替换该身份。来源、Corpus和语义引用必须一致，跨阶段配置不同不等于允许改写旧材料。

O0保存的准备控制用于重开其材料约定和内容身份；O1/O2实际派发采用各自本次配置的请求字节、响应字节、输出token和运行请求次数上限。上游较宽的上限不覆盖当前阶段较窄的限制。每个运行单独计数，其全部依赖任务共享该运行额度；额度耗尽后保留成功任务，并登记剩余任务未执行，不自动启动新批次。模型窗口未得到实测时仍记录未知，不因通过字节检查而宣布支持某个模型窗口。

只有用户当前明确授予跨运行总额度时，宿主Agent才按该边界汇总准确O1/O2的实际观察；Java没有跨运行授权计数器，新运行也不能自行续期硬额度。v2分别记录预留、确认开始、确认结束和远端未知，`modelRequestsDispatched`不证明远端是否完成；未知不得当作零或安全重发依据。用户已授权有限配置内同范围程序缺陷修复时，不恢复被撤回的旧PoC累计额度阻断。范围、来源、模型或账户扩大仍需相应选择。查询及合法O0/O3不消耗模型额度。

范围、选材和发布清单使用`ontology-scope-v1`及新执行的`ontology-selection-v2`：保存问题、任务、EXPLICIT/MODEL选择性质及准确Corpus内E/U/K引用；跨运行选择保存完整运行ID并重开准确模块。清单不能包含人工对象、关系或指标答案，不能把旧PoC结果作为已审上游。完整定义字段由模型任务合同规定，不由配置代写。历史selection-v1继续按原合同读取，不猜objectSources、不修改旧记录。

scope字段保持v1，同题ACTION/ANALYTIC只依赖此前全部OBJECT。selection-v2的RELATE问题显式选择`objectSources.identificationRun/questionId`；PUBLISH显式O1集合必须包含每个所选O2的实际上游，不能要求独立O2拥有完全相等的全表。派发计数、PARTIAL及nextActions由[contracts§11.6](contracts.md)拥有，配置读取通过不替代这些运行验收。

### 3.1 可复制示例的边界

`tools/repository-run/ontology.example.yaml`选择本次v2本体policy，并示出显式历史owner列表。源码和技术policy仍应填所选R0/R4的真实owner，不是因为示例文件存在就认定匹配。示例不会认证或执行模型。

`ontology-scope.example.json`、`ontology-relate-selection.example.json`和`ontology-publish-selection.example.json`只演示准确字段，不是客户材料或运行结果。运行ID中全0/全1/全2十六进制值及E1/U1/Q1均为占位符；必须用实际返回的O0/O1/O2和准确Corpus、保存问题中的引用替换。RELATE示例的根identificationRuns与objectSources必须同步替换；PUBLISH示例必须列全每个选中O2的实际O1上游。不能按对象名寻找近似运行，不能把模板中的数字当模型已审答案。

新材料规则不增加配置字段：新v2 policy的O0写明确的`ontology-model-projection-v2`，历史O0按其保存规则读取。新规则及冻结包/模型v4已有直接验证；固定客户新O0已经保存。具体版本合同见[材料准备§9.2](material-preparation.md)，真实三例的请求成本仍需按实际请求记录核验。

## 4. 直接验收

- 无modelJobs的配置可加载并用于O0/O3；加载过程不读取认证环境、不验证登录、不创建Provider。
- 模型路由只接受本体三个键；缺项/未知Provider/重复分配按配置问题报告，不回退旧路由。
- 不同配置文件位置但相同实际Prompt文本有明确内容身份；同一路径内容变化使绑定变化。
- DDL相对路径、正整数单位及三份策略用途分别验证。
- 原RepositoryRunConfiguration、旧请求写入版本、旧MD Prompt和模型路由不修改。

独立读取器最新整类7项直接测试通过，包含正式便携示例加载；当前正式runtime的21项直接回归覆盖显式四操作、scripted DISCOVERY/QUESTION＋MODEL及有限DDL。真实历史及新R4准入、O0安装和查询也已通过：不再误用本体固定64文件上限，使用该R4请求实际保存的变长预算，不放宽来源或回执检查。每次运行的实际数量及失败性质见[验收记录](../../supplements/ontology-formal-acceptance-20261003.md)；测试编译/命令问题与产品断言失败分开记录。上述结果不等于任意配置、真实自主发现或模型质量通过。剩余状态以[实施差距](implementation-delta.md)为准。
