# 06 一次只解释一个流程

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

Stage 05 已经证明“代码发生什么”，但新客户仓库的业务词、业务主张和待确认问题不可能预先穷举。Stage 06 因而分成两个受控动作：模型先在每个 Flow 独立的 `R0_REGISTRY_PROPOSAL` 中提出有 Capsule basis 的 bounded label/purpose，程序验证并冻结全仓唯一 `RepositoryInterpretationRegistry`；随后同一 Flow 的 R1/R2 只能选择该 registry 的 finite provisional keys。Stage 07 仍由程序决定是否准入。

仓库有 `N` 个 eligible FlowSlice 时，先建立 `N` 个隔离 R0 slots。全部 R0 disposition 终态后恰冻结一个 registry；R0 READY 的 Flow 才各有 R1/R2 两个 slots。正常分支是 `N + 2N = 3N` slots/calls。每次模型调用只见一个 Flow 的同一 EvidenceCapsule，DepotHead 只是 N 中一个例子；单 Flow 成功不能结束阶段或 run。

## 2. 具体输入与 DepotHead 例子

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：下文用未来已闭合的 DepotHead Flow 连贯展示目标流程。当前 fixed slice 仍是 0 Flow、0 Capsule、0 R0/R1/R2 task/call；技术合同中的 unknown 只以 nullable、UNRESOLVED、Gap 或 fatal 表达。

每个 Flow 的三个 rounds 都只能从它唯一的 EvidenceCapsule 取得源码事实、atom、Gap、Outcome 和 locator。另有严格 output schemas、prompt bundle、runtime policy、预算，以及可选的组织级 registry seed；seed 不是事实来源，也不能绕过 R0。一个合理的故事是：

~~~json
{
  "flowSliceId": "flow:post-depothead-batch-set-status",
  "r0Proposal": {
    "kind": "BUSINESS_TERM",
    "label": "批量审核或反审核",
    "purpose": "描述同一入口依据输入状态批量改变单据状态",
    "basisAtomIds": ["atom:input-field", "atom:value-source"],
    "basisGapIds": ["gap:runtime-status-policy"]
  },
  "frozenProvisionalKey": "TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "r1Selection": "TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "r2Decision": "KEEP"
}
~~~

R0 label/purpose 是 **MODEL_INTERPRETATION proposal data**，不是 Fact。程序以 UTF-8/NFC、字符/字节上限、kind、basis闭包和 seed exact-match 规则验证；不会把故事值补成代码事实。

## 3. 程序怎样工作

1. 重验 Stage05 全量 Flow/Capsule 双射、coverage、artifact roots、Fact/atom/Gap/Outcome/projection refs 与预算；EvidenceCapsule 是 R0/R1/R2 唯一源码语义来源。
2. 按完整 eligible flowSliceId denominator 建稳定 R0 shards；每 Flow 编一个 `RegistryProposalTask`，独立 session，只包含 Capsule canonical JSON、bounded schema、prompt/runtime hashes 与可选 seed 的只读 keys/display data。
3. 对每个 R0 slot 执行 durable started-before-content lifecycle。strict parse 后按固定顺序验证 kind → UTF-8/NFC → 控制/双向覆盖字符 → 长度/字节 → basis non-empty/同Capsule闭包 → seed exact-match → proposal identity；R0 不得创建 Fact、locator、Flow、Markdown或指令。
4. 全部 R0 slots 都有 READY/GAP/FAILED disposition 后，程序按 `(kind, flowSliceId, evidenceCapsuleId, basisAtomIds, basisGapIds, normalizedLabel, normalizedPurpose, sourceSeedKey)` 排序；为每个 valid proposal 生成全长 SHA-256 provisional key，并原子冻结恰一个 `RepositoryInterpretationRegistry`。不同 Flow 的同 label 不合并。
5. 只为 R0 READY Flow 编 R1/R2 tasks。allowlist 恰为该 Flow 的 frozen provisional keys；R1 提交完整 finite-key interpretation proposals，R2 对同一 proposal set 逐项 KEEP/NARROW/DROP/NEEDS_EVIDENCE，不得新增、遗漏或扩大 basis。
6. R0/R1/R2 共用同一 lifecycle 状态机，但 slot identity 分别为 `flowSliceId/r0`、`flowSliceId/r1`、`flowSliceId/r2`；started 或 ambiguous slot 永不重放，只有 durable confirmed-no-start 可在 1..3 上限内恢复。
7. 程序形成每 Flow 的 registry proposal disposition 和最终 `FlowInterpretationDisposition`。R0 GAP/FAILED 不执行 R1/R2，但仍有最终 GAP/FAILED；R1/R2 READY 产生恰一个 Candidate；没有 Flow 可从 denominator 消失。
8. 重算 `N R0 dispositions + 1 registry + N final dispositions`、slot/call/round/proposal equations、shard disjoint union 和全部 refs；原子安装十个 Stage06 公共文件。0 Flow 时所有 JSONL 为0行、registry仍是非空0-item object、Provider调用为0。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| registry-proposal-tasks.jsonl | 每 Flow 恰一个 R0 exact task、Capsule/input/schema/prompt/runtime hashes |
| registry-proposal-rounds.jsonl | R0 canonical response、transport/semantic SHA 与 task ref |
| registry-proposal-dispositions.jsonl | 每个 eligible Flow 恰一 R0 READY/GAP/FAILED 处置 |
| repository-interpretation-registry.json | 全部 R0 终态后程序冻结的唯一 registry、provisional keys 与 proposal lineage |
| flow-model-tasks.jsonl | 仅 R0 READY Flow 的 R1/R2 exact finite-key tasks |
| model-rounds.jsonl | R1/R2 canonical response 与 nested task refs |
| generation-receipts.jsonl | R0/R1/R2 configured/expected/observed runtime 与 lifecycle receipts |
| interpretation-candidates.jsonl | R1/R2 strict parsed proposals/candidates；不含最终 admission |
| flow-interpretation-dispositions.jsonl | 每个 eligible Flow 恰一 READY_FOR_ADMISSION/GAP/FAILED 最终处置 |
| stage-receipt.json | upstream roots、registry root、controls、artifact set、coverage 与3-round call accounting |

Artifact cardinality 固定：`N Flow = N RegistryProposalDisposition = N FlowInterpretationDisposition`，且每 run 恰一个 registry。正常全部 READY 时恰有 `N` R0 tasks/rounds/calls、`2N` R1/R2 tasks/rounds/calls、`N` candidates。少于 `3N` 只能因 typed R0或R1/R2 failure/Gap而发生，并由 slot/disposition accounting解释。Stage06不生成Markdown。

### 4.1 人类 walkthrough：模块用什么文件接力

~~~jsonl
{"module":"RegistryProposalTaskCompiler","artifact":"modules/01-registry-task-compiler/registry-proposal-task-set.json","takesFrom":["Stage05Reference","optional organization seed","R0 prompt/schema/runtime policy"],"says":{"flowSliceId":"flow:post-depothead-batch-set-status","capsule":"capsule:post-depothead-batch-set-status","round":"R0_REGISTRY_PROPOSAL","basisSource":"same Capsule only"}}
{"module":"LifecycleBoundRegistryProposalRunner","artifact":"modules/02-registry-proposal-runner/registry-proposal-execution-set.json","takesFrom":["registry-proposal-task-set.json","durable ledger","Provider"],"says":{"proposalId":"registry-proposal:depothead-batch-audit","label":"批量审核或反审核","basis":["atom:input-field","atom:value-source"],"disposition":"READY_FOR_FREEZE"}}
{"module":"RepositoryInterpretationRegistryFreezer","artifact":"modules/03-registry-freezer/repository-interpretation-registry.json","takesFrom":["registry-proposal-task-set.json","registry-proposal-execution-set.json"],"says":{"registryCount":1,"provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","lineage":"registryProposalId → provisionalKey"}}
{"module":"FiniteKeyFlowTaskCompiler","artifact":"modules/04-flow-task-compiler/flow-task-set.json","takesFrom":["Stage05Reference","repository-interpretation-registry.json"],"says":{"flowSliceId":"flow:post-depothead-batch-set-status","rounds":["R1","R2"],"allowedKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}
{"module":"LifecycleBoundInterpretationRunner","artifact":"modules/05-interpretation-runner/model-execution-set.json","takesFrom":["flow-task-set.json","durable ledger","Provider"],"says":{"r1":"select provisional key","r2":"KEEP without expansion","interpretationProposalId":"interpretation-proposal:depothead-batch-audit","candidate":"flow-interpretation:depothead-v1"}}
{"module":"InterpretationArtifactPublisher","artifact":"modules/06-publish/stage06-publication.json","takesFrom":["all five prior module artifacts"],"says":{"flowCount":1,"taskSlots":3,"providerCalls":3,"publicFileCount":10,"nextStage":"07-admit-and-merge-business-knowledge"}}
~~~

六个模块通过 typed artifact 接力；R0 proposal先成为 provisional key，R1/R2只引用它，Stage07才决定 meaning。当前0 Flow分支仍使用相同 schemas，registry item 数、tasks、rounds、calls均为0。

## 5. 下游怎样消费而不返工

Stage07只读十个 canonical files，验证 `registryProposalId → provisionalKey → interpretationProposalId → selectedKey` 后再决定 `meaningId`。它不调用Provider、不回读源码、不从组织seed或label猜Fact，也不信任模型自报KEEP。Stage08把同一 lineage写入 ReaderItem 和 Trace。

| Stage07 开始前必须成立 | Stage06 成功后保证 |
| --- | --- |
| Stage05完整Flow/Capsule双射与controls匹配 | R0/R1/R2的唯一源码证据均来自对应Capsule；跨Flow不共享context/basis |
| 十文件、六个module artifacts、registry与lifecycle refs闭合 | 每Flow有R0 disposition和最终disposition；全run恰一immutable registry |
| 全部R0终态后才freeze，freeze后才有R1/R2 tasks | 每个selectedKey存在于同Flow registry allowlist，并可追到R0 proposal与Capsule basis |
| R0/R1/R2 shards各自不交叠且union与其denominator相等 | 正常N Flow=3N calls；少调用均有typed原因，single-flow PASS不冒充全仓完成 |

缺 registry freeze、跨Flow basis、R1/R2绕过registry、started重放或 accounting不闭合时，Stage07必须拒绝整个Stage06 set。

## 6. 成功、Gap、fatal 与恢复

- **成功**：全部eligible Flow各有R0 disposition；一个registry已冻结；全部Flow各有最终disposition；READY分支具有完整R1/R2和candidate。0 Flow合法且0 calls。
- **带Gap成功**：某Flow R0无合法proposal、confirmed-no-start耗尽、或可证明隔离的post-start transport failure；该Flow保留typed GAP/FAILED，R1/R2不启动或停止，Stage07以technical fallback处理。其他Flow bytes不变。
- **fatal**：Capsule/basis/ref不闭合、R0 open field越界、非法Unicode/控制字符、seed不精确匹配、freeze前启动R1、registry identity collision、跨Flow合并/引用、R2扩张、runtime drift、ledger/accounting破坏，或无法证明失败仅影响一个Flow。
- **恢复**：重开已持久化module artifacts和slot ledger。started前且有durable no-start receipt的同一slot最多3次；started/ambiguous永不重放。registry只可从完全相同的R0 terminal set重算出相同bytes；一旦freeze原子安装，resume必须复用并验hash，不得追加item。上游Stage01–05永久保留。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 冻结Flow/Capsule、task、schema、prompt、runtime | 是 | 否 |
| R0提出bounded label/purpose及Capsule basis refs | 验证/冻结 | 是，仅本Flow |
| 生成provisional key和RepositoryInterpretationRegistry | 是 | 否 |
| R1/R2选择/审查finite provisional keys | schema/ref验证 | 是，仅本Flow |
| 发现Fact/locator/Flow或写Markdown | 上游/下游负责 | 禁止 |
| 最终准入meaning | Stage07程序负责 | 否 |
| 自动换Provider/API key或重放started | 禁止 | 无权 |

模型目标为 gpt-5.6-luna / xhigh / read-only。真实调用需要另行授权；本文档工作没有调用模型。

## 8. 技术合同

### 8.0 固定模块合同

顺序固定为 `RegistryProposalTaskCompiler` → `LifecycleBoundRegistryProposalRunner` → `RepositoryInterpretationRegistryFreezer` → `FiniteKeyFlowTaskCompiler` → `LifecycleBoundInterpretationRunner` → `InterpretationArtifactPublisher`。只有M2/M5持有Provider Interface；M3是唯一可生成provisional key并冻结registry的模块。

#### M1 RegistryProposalTaskCompiler

- **解决的问题**：把每个Flow的同一Capsule编成唯一、bounded、不可跨Flow的R0 task。
- **精确上游输入及前置**：valid Stage05Reference、全量Flow/Capsule/coverage、R0 prompt/schema、ModelRuntimePolicy、budget、required-nullable OrganizationRegistrySeedRef；双射和refs已重验。
- **确定性顺序 / LLM**：固定denominator→flowSliceId排序/shard→序列化Capsule允许字段→附可选seed只读entries→hash input/schema/prompt/runtime→生成每Flow一个session/slot；0LLM。
- **目标输出与DepotHead示例**：`RegistryProposalTaskSet{flowCount,tasks[],taskShardReceipts[],runtimePolicy,organizationRegistrySeedRef?,zeroFlowDisposition?}`；例中一个DepotHead Flow得到一个R0 task，input含两个basis atoms和一个Gap。
- **不变量**：N Flow=N R0 tasks；task sourceArtifactId恰为对应Capsule；不同Flow session不同；无Path/raw源码/Capsule外refs；seed不得替代basis。
- **Gap/fatal/恢复**：0Flow合法；Capsule/ref/schema/policy/budget错误fatal；重编bytes相同才可复用未开始slot。
- **下游保证**：M2只执行排序后的exact tasks，无需决定source、schema、session或runtime。
- **非目标**：不调用模型、不生成label/key/Fact/Flow/Markdown。
- **公共测试seam/验收**：`compileRegistryProposalTasks(stage05, seedRef, prompt, schema, policy)`；覆盖0/1/2 Flow、seed absent/present、跨Flow ref、shard、hash/order determinism。
- **Luna/xhigh 测试指南**：`Stage06RegistryProposalTaskCompilerTest`；fixture/golden在`src/test/resources/target/stage06/registry-task-compiler/`。一个行为一个RED：0Flow→单Flowtask→双Flow隔离→seed只读→bad ref/hash/shard→determinism；首RED因public compiler/schema缺失。只fake artifact reader；禁止mock canonicalizer/identity、网络/live Provider/客户Maven。命令：`mvn -Dtest=Stage06RegistryProposalTaskCompilerTest test`。偏离必须按DESIGN 13.11 STOP。
- **Terra/xhigh 实现指南**：仅观察预期RED后改`target/stage06/registrytaskcompiler/`，实现public records/Interface和`stage06-registry-proposal-task-set-v1`；按验证→排序→shard→canonical task顺序。每slice以selector GREEN和手写bytes相等退出并更新本stage审计；不得读取Capsule外数据、发明字段或兼容旧2N设计。合同冲突交Sol/ultra Design Authority。

#### M2 LifecycleBoundRegistryProposalRunner

- **解决的问题**：安全执行R0，验证open label/purpose而不让它变成事实或指令，并给每Flow终态处置。
- **精确上游输入及前置**：M1完整task set、唯一Provider Adapter、durable RoundSlotLedger/EventSink、R0 response limits；slot未started/terminal或有confirmed-no-start状态。
- **确定性顺序 / LLM**：reserve→persist attempt→preflight→upstream start→persist/ACK started→bounded content→strict parse→kind/NFC/control/bidi/length/basis/seed validation→proposal identity→Flow disposition。LLM只产生R0 data。
- **目标输出与DepotHead示例**：`RegistryProposalExecutionSet{rounds,receipts,validatedProposals,flowDispositions,slotStates,shardReceipts,callCounts}`；例proposal label“批量审核或反审核”引用同Capsule atoms/Gap。
- **不变量**：每Flow恰一R0 disposition；valid proposal basis union非空且同Capsule；label/purpose不进入prompt控制面；started ACK在content前；一Flow失败不改其他Flow bytes。
- **Gap/fatal/恢复**：无合法proposal或可隔离Provider failure可为Flow GAP/FAILED；schema/ref/Unicode/seed/runtime/ledger破坏fatal。only confirmed-no-start重试≤3；started/ambiguous不重放。
- **下游保证**：M3获得完整terminal R0 set和程序验证过的proposal data；无需Provider。
- **非目标**：不生成provisional key、不merge跨Flow、不准入meaning、不创建Fact/locator/Flow/Markdown。
- **公共测试seam/验收**：`runRegistryProposals(taskSet, provider, ledger, eventSink)`；只mockProvider transport/EventSink，覆盖success、novel label、basis缺失、seed mismatch、controls/bidi、budget、started顺序、retry、multi-flow隔离。
- **Luna/xhigh 测试指南**：`Stage06LifecycleBoundRegistryProposalRunnerTest`；scripted transcripts/ledger/goldens在`src/test/resources/target/stage06/registry-proposal-runner/`。RED顺序：0call→validnovel term→invalidbasis→Unicode/control→seed mismatch→ACK order→3 no-start→post-start→双Flow隔离；每个RED断言code/call/event。禁止live Provider、mock ledger/canonicalizer。命令：`mvn -Dtest=Stage06LifecycleBoundRegistryProposalRunnerTest test`。异常RED才交Sol/xhigh debug；合同变更STOP交Sol/ultra。
- **Terra/xhigh 实现指南**：预期RED后仅改`target/stage06/registryproposalrunner/`，实现public runner/records与`stage06-registry-proposal-execution-set-v1`；严格执行lifecycle和validation顺序，复用M1 exact task。每slice GREEN后更新审计；不得自动修label、扩大basis、换Provider或把R0 data写成Fact。需要改变边界时按13.11暂停。

#### M3 RepositoryInterpretationRegistryFreezer

- **解决的问题**：把所有terminal R0结果确定性冻结为全run唯一、可寻址finite registry。
- **精确上游输入及前置**：M1 task set、M2 execution set、Stage05 Flow/Capsule IDs；每个eligible Flow有唯一R0 disposition，所有proposal已程序验证，R0 shard/accounting闭合。
- **确定性顺序 / LLM**：join task/round/proposal/disposition→重验basis/seed→按固定tuple排序→canonical item material→kind前缀+全长SHA-256 provisionalKey→检查碰撞/唯一性→canonical registry→atomic install；0LLM。
- **目标输出与DepotHead示例**：`RepositoryInterpretationRegistry{repositoryInterpretationRegistryId,eligibleFlowSliceIds,items[],flowDispositions[],proposalAccounting,organizationRegistrySeedRef?,closed}`；例proposal映射`TERM_P_<hex64>`并保留flow/capsule/basis。
- **不变量**：每run恰一registry；同label跨Flow不merge；provisionalKey绑定kind+flow+capsule+basis+normalized values+seed lineage；R0失败Flow无item但有disposition；freeze后immutable。
- **Gap/fatal/恢复**：合法0item registry可带Gap；缺/重复Flow disposition、unknown basis、collision、非canonical sort或partial R0 fatal。crash前重算相同bytes，atomic install后只验hash复用。
- **下游保证**：M4得到唯一finite keys和逐key lineage，不需解读open output或seed。
- **非目标**：不调用LLM、不合并业务同义词、不决定admission、不生成R1/R2/Markdown。
- **公共测试seam/验收**：`freeze(taskSet, executionSet)`覆盖0item、novel item、同label双Flow不同key、seed lineage、shuffle/shard determinism、missing disposition、collision、crash/resume。
- **Luna/xhigh 测试指南**：`Stage06RepositoryInterpretationRegistryFreezerTest`；frozen R0 artifacts和independent registry goldens在`src/test/resources/target/stage06/registry-freezer/`。逐RED：empty→oneitem→same-label-two-flow→seed→shuffle→missing/duplicate/collision→atomic resume；每RED因对应public behavior缺失失败。仅mockatomic store；hash/canonical/accounting禁止mock。命令：`mvn -Dtest=Stage06RepositoryInterpretationRegistryFreezerTest test`。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改`target/stage06/registryfreezer/`，实现public freezer/registry records和`stage06-repository-interpretation-registry-v1`；严格join→validate→sort→full-hash key→collision→atomic。逐slice GREEN并更新审计；不得智能merge/改label/补proposal。跨阶段identity需求STOP交Sol/ultra。

#### M4 FiniteKeyFlowTaskCompiler

- **解决的问题**：在registry已冻结后，为R0 READY Flow编译只含同Flow finite keys的R1/R2 tasks。
- **精确上游输入及前置**：valid Stage05Reference、M3 registry、R1/R2 prompt/output schemas、runtime policy/budget；registry closed且ID/root匹配，Capsule与R0 task相同。
- **确定性顺序 / LLM**：取R0 READY denominator→按flow排序→从registry筛同flow keys→编R1 exact task→编同session R2 review task→hash/shard/accounting；0LLM。
- **目标输出与DepotHead示例**：`FlowTaskSet{repositoryInterpretationRegistryId,eligibleR1R2FlowSliceIds,tasks[],taskShardReceipts[],runtimePolicy}`；例R1/R2 allowlist只含DepotHead provisional key。
- **不变量**：registry freeze先于task；M个R0 READY Flow恰2M tasks；R0非READY为0 R1/R2；同Flow session相同、跨Flow不同；source evidence仍只来自同一Capsule。
- **Gap/fatal/恢复**：同Flow registry无key可编空allowlist并由R1产生fallback信号；registry/ref/prompt/runtime错误fatal。重编bytes相同才复用未started slots。
- **下游保证**：M5无需决定allowlist、轮次、session或source，只按task执行。
- **非目标**：不重开R0、不新增key、不访问Provider、不准入meaning。
- **公共测试seam/验收**：`compileFiniteKeyTasks(stage05, registry, prompt, schema, policy)`覆盖0ready、single、multi-flow key隔离、empty key、R0 failed skip、registry/hash/order mutation。
- **Luna/xhigh 测试指南**：`Stage06FiniteKeyFlowTaskCompilerTest`；fixtures/goldens在`src/test/resources/target/stage06/flow-task-compiler/`。RED顺序：0ready→single R1/R2→双Flow keys隔离→failed skip→freeze/hash/ref/order反例；禁止mockregistry parser/canonical identity及Provider。命令：`mvn -Dtest=Stage06FiniteKeyFlowTaskCompilerTest test`。偏离STOP。
- **Terra/xhigh 实现指南**：仅预期RED后改`target/stage06/flowtaskcompiler/`，实现public compiler/FlowTaskSet与`stage06-flow-task-set-v2`；复用M3 registry和Stage05 Capsule，不从seed/R0 raw response旁路取词。每slice selector GREEN后更新审计；不得加第三轮或开放字段。

#### M5 LifecycleBoundInterpretationRunner

- **解决的问题**：执行finite-key R1/R2并生成可由Stage07程序重放的candidate和全部Flow最终disposition。
- **精确上游输入及前置**：M4 task set、M2 R0 dispositions、M3 registry、Provider、durable ledger/event sink；R1/R2 slots有效，R0 READY集合与task denominator相等。
- **确定性顺序 / LLM**：对每Flow reserve/persist/preflight/start/ACK/content/parse R1，再同session执行R2；验证selectedKey同Flow、proposal/basis closure和R2一一保持/收窄；join R0非READY Flow为final GAP/FAILED；重算全Flowclosure。
- **目标输出与DepotHead示例**：`ModelExecutionSet{rounds,receipts,interpretationProposals,candidates,flowDispositions,slotStates,shardReceipts,callCounts}`；例保存`registryProposalId→provisionalKey→interpretationProposalId→selectedKey`和一个candidate。
- **不变量**：全部N Flow恰一final disposition；R0 READY且R1/R2成功才candidate；R2不得新增/遗漏/扩大；started ACK before content；observed runtime=expected；跨Flow零污染。
- **Gap/fatal/恢复**：empty selection、可隔离Provider unavailable进入FlowGap/failure；unknown key/basis、R2 closure/runtime/ledger/accounting破坏fatal。confirmed-no-start≤3；started/ambiguous永不重放。
- **下游保证**：M6获得N个final dispositions和完整R0→registry→R1/R2 lineage；无Provider再访问。
- **非目标**：不修改registry、不最终KEEP/DROP、不生成knowledge/Markdown。
- **公共测试seam/验收**：`runInterpretations(taskSet, registry, r0Dispositions, provider, ledger, eventSink)`；只mocktransport/EventSink，覆盖0ready、R1/R2 success、unknown/cross-flow key、R2 expansion、runtime drift、retry/post-start、双Flow交错及一Flow失败。
- **Luna/xhigh 测试指南**：`Stage06LifecycleBoundInterpretationRunnerTest`；transcripts/ledger/goldens在`src/test/resources/target/stage06/interpretation-runner/`。逐RED：0ready→single R1/R2→lineage→cross-flow key→R2 expansion→ACK/retry/post-start→双Flow失败隔离；断言exact code/count/bytes。禁止live Provider或mockcore。命令：`mvn -Dtest=Stage06LifecycleBoundInterpretationRunnerTest test`。异常由Sol/xhigh调试，合同冲突STOP。
- **Terra/xhigh 实现指南**：RED后仅改`target/stage06/interpretationrunner/`，实现public runner/records与`stage06-model-execution-set-v2`；严格复用M4 tasks/M3 registry/M2 dispositions和共享lifecycle，不换Provider或改expected。每slice GREEN并更新审计；任何schema/model boundary偏离交Sol/ultra。

#### M6 InterpretationArtifactPublisher

- **解决的问题**：封闭R0、registry、R1/R2、lifecycle和每Flow处置，发布Stage07可纯重放的十文件集合。
- **精确上游输入及前置**：M1–M5 artifacts、Stage05 root、prompt/schema/runtime controls；全部R0 slots terminal、registry frozen、全部Flow final disposition、refs/accounting已验证。
- **确定性顺序 / LLM**：验证各denominator/shard→join六module identities→重算N/ready/task/round/call/proposal equations→canonical十文件→staging force/SHA→atomic install；0LLM。
- **目标输出与DepotHead示例**：`Stage06Reference`与十个files；例flow=1、R0=1、R1/R2=2、总slots/rounds/calls=3、registry=1、candidate=1。
- **不变量**：N个R0 dispositions、1registry、N final dispositions；正常3N；0Flow/0call；任何single-flow PASS不完成stage；publisher不丢proposal或重解析模型语义。
- **Gap/fatal/恢复**：typed perFlow unavailable可发布带Gap；missing/duplicate/unclosed/identity/accounting/canonical/install错误fatal。恢复只重放M1–M5，不调用Provider。
- **下游保证**：Stage07只读十文件即可验证完整lineage与admission，Stage08可追Trace。
- **非目标**：不决定meaning、不mergeknowledge、不生成Markdown、不补round。
- **公共测试seam/验收**：`publish(registryTasks, registryExecution, registry, flowTasks, modelExecution, controls)`覆盖0set、one/multi Flow正常3N、R0 failure少2calls、missing/overlap/ref/count spoof、乱序、crash/collision。
- **Luna/xhigh 测试指南**：`Stage06InterpretationArtifactPublisherTest`；六module files与ten-output goldens在`src/test/resources/target/stage06/artifact-publisher/`。RED：0set→oneFlow3N→multiFlow→R0failure accounting→missing/ref/hash/count→order→crash/resume；只mockartifact store，closure/canonical不可mock。命令：`mvn -Dtest=Stage06InterpretationArtifactPublisherTest test`；禁网络/live Provider。偏离按13.11。
- **Terra/xhigh 实现指南**：预期RED后仅改`target/stage06/artifactpublisher/`，实现public publisher/Stage06Reference与`stage06-interpretation-publication-v2`；只读M1–M5，join→equations→十files→atomic。每slice GREEN/Stage07 replay后更新审计；不得调用Provider、压回六文件或旧2N合同。

### 8.0.1 模块 artifact wire schemas

全部使用 DESIGN 13.3 ModuleArtifact envelope；`!`为required non-null，`?`为required nullable。所有array canonical排序写在下表；未知field/version拒绝，字段/语义/identity/sort变化必须先改设计并升version。

| module artifact | schema / type | upstream | 核心payload与canonical排序 |
| --- | --- | --- | --- |
| `modules/01-registry-task-compiler/registry-proposal-task-set.json` | `stage06-registry-proposal-task-set-v1` / `STAGE06_REGISTRY_PROPOSAL_TASK_SET` | Stage05+R0 prompt/schema/runtime+optional seed IDs/SHAs | `taskSetId! flowCount! tasks[]! taskShardReceipts[]! runtimePolicy! organizationRegistrySeedRef? zeroFlowDisposition?`；tasks按flowSliceId，seed entries按seedKey |
| `modules/02-registry-proposal-runner/registry-proposal-execution-set.json` | `stage06-registry-proposal-execution-set-v1` / `STAGE06_REGISTRY_PROPOSAL_EXECUTION_SET` | M1+ledger/event policy | `executionSetId! taskSetId! rounds[]! receipts[]! validatedProposals[]! flowDispositions[]! slotStates[]! shardReceipts[]! callCounts!`；每个 disposition 必含 `registryProposalDispositionId`；round/receipt/disposition按flow，proposal按`registryProposalId`，events按ordinal |
| `modules/03-registry-freezer/repository-interpretation-registry.json` | `stage06-repository-interpretation-registry-v1` / `STAGE06_REPOSITORY_INTERPRETATION_REGISTRY` | M1+M2+Stage05 | `repositoryInterpretationRegistryId! eligibleFlowSliceIds[]! items[]! flowDispositions[]! proposalAccounting! organizationRegistrySeedRef? closed!`；items按proposalKind/flow/provisionalKey |
| `modules/04-flow-task-compiler/flow-task-set.json` | `stage06-flow-task-set-v2` / `STAGE06_FLOW_TASK_SET` | Stage05+M3+R1/R2 prompt/schema/runtime | `flowTaskSetId! repositoryInterpretationRegistryId! eligibleR1R2FlowSliceIds[]! tasks[]! taskShardReceipts[]! runtimePolicy!`；tasks按flow后R1/R2 |
| `modules/05-interpretation-runner/model-execution-set.json` | `stage06-model-execution-set-v2` / `STAGE06_MODEL_EXECUTION_SET` | M2+M3+M4+ledger/event policy | `executionSetId! flowTaskSetId! repositoryInterpretationRegistryId! rounds[]! receipts[]! interpretationProposals[]! candidates[]! flowDispositions[]! slotStates[]! shardReceipts[]! callCounts!`；round按flow/R1/R2，其余stable ID |
| `modules/06-publish/stage06-publication.json` | `stage06-interpretation-publication-v2` / `STAGE06_INTERPRETATION_PUBLICATION` | M1–M5 IDs/SHAs | `stageStatus! registryProposalTaskSetId! registryProposalExecutionSetId! repositoryInterpretationRegistryId! flowTaskSetId! executionSetId! flowCount! r0TaskSlotCount! r1R2TaskSlotCount! totalRoundCount! providerCallCount! coverage! stageArtifactRoot! publishedArtifacts[10]! nextStage!`；`coverage` 必含 eligible、R0 ready/gapped/failed、R1/R2 eligible/gapped/failed、final candidate/gapped/failed Flow ID sets、registry disposition IDs、四类 shard receipt IDs 与 `closed=true`；published按path |

Technical examples below are schema-valid target illustrations; placeholder digests are valid-shaped, not current runtime facts.

~~~jsonl
{"schemaVersion":"stage06-registry-proposal-task-set-v1","artifactType":"STAGE06_REGISTRY_PROPOSAL_TASK_SET","artifactId":"registry-proposal-tasks:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":6,"module":"RegistryProposalTaskCompiler","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"5555555555555555555555555555555555555555555555555555555555555555"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"taskSetId":"registry-task-set:depothead-v1","flowCount":1,"tasks":[{"taskSpecId":"task:depothead-r0","taskKind":"R0_REGISTRY_PROPOSAL","flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","isolatedSessionKey":"session:depothead-r0","inputJson":{"basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"],"outcomePathIds":["outcome:no-eligible-document","outcome:status-updated"]},"inputJsonSha256":"1111111111111111111111111111111111111111111111111111111111111111","outputSchemaSha256":"2222222222222222222222222222222222222222222222222222222222222222","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd","expectedRuntime":{"upstreamProvider":"OPENAI","model":"gpt-5.6-luna","reasoningEffort":"xhigh","sandbox":"read-only"}}],"taskShardReceipts":[{"shardId":"r0-task-shard:depothead","denominatorFlowSliceIds":["flow:post-depothead-batch-set-status"],"taskSpecIds":["task:depothead-r0"],"status":"SUCCEEDED_WITH_GAPS","gapIds":["gap:runtime-status-policy"]}],"runtimePolicy":{"configuredAdapterId":"adapter:openai-responses-v1","configuredAuthMode":"HOST_ACCOUNT","expectedUpstreamProvider":"OPENAI","expectedModel":"gpt-5.6-luna","expectedReasoningEffort":"xhigh","expectedSandbox":"read-only","maxPreStartAttempts":3,"maxProviderWallClockMillis":120000},"organizationRegistrySeedRef":null,"zeroFlowDisposition":null}}
{"schemaVersion":"stage06-registry-proposal-execution-set-v1","artifactType":"STAGE06_REGISTRY_PROPOSAL_EXECUTION_SET","artifactId":"registry-proposal-execution:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":6,"module":"LifecycleBoundRegistryProposalRunner","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"registry-proposal-tasks:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"executionSetId":"registry-execution:depothead-v1","taskSetId":"registry-task-set:depothead-v1","rounds":[{"modelRoundId":"round:depothead-r0","taskSpecId":"task:depothead-r0","canonicalResponseSha256":"3333333333333333333333333333333333333333333333333333333333333333","semanticResponseSha256":"4444444444444444444444444444444444444444444444444444444444444444","startedReceiptId":"started:depothead-r0"}],"receipts":[{"receiptId":"receipt:depothead-r0","taskSpecId":"task:depothead-r0","attemptId":"attempt:depothead-r0","startedEventId":"started:depothead-r0","startedEventOrdinal":1,"observedRuntime":{"upstreamProvider":"OPENAI","model":"gpt-5.6-luna","reasoningEffort":"xhigh","sandbox":"read-only"}}],"validatedProposals":[{"registryProposalId":"registry-proposal:depothead-batch-audit","taskSpecId":"task:depothead-r0","flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","proposalKind":"BUSINESS_TERM","normalizedLabel":"批量审核或反审核","normalizedPurpose":"描述同一入口依据输入状态批量改变单据状态","basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"],"sourceSeedKey":null}],"flowDispositions":[{"registryProposalDispositionId":"registry-proposal-disposition:depothead-ready","flowSliceId":"flow:post-depothead-batch-set-status","disposition":"READY_FOR_FREEZE","registryProposalIds":["registry-proposal:depothead-batch-audit"],"gapIds":["gap:runtime-status-policy"],"failureRef":null,"reasonCode":null}],"slotStates":[{"taskSpecId":"task:depothead-r0","state":"STARTED_CONSUMED","attemptCount":1}],"shardReceipts":[{"shardId":"r0-execution-shard:depothead","denominatorFlowSliceIds":["flow:post-depothead-batch-set-status"],"dispositionFlowSliceIds":["flow:post-depothead-batch-set-status"],"status":"SUCCEEDED_WITH_GAPS"}],"callCounts":{"providerPreflightCount":1,"providerCallCount":1,"startedEventCount":1,"roundCount":1}}}
{"schemaVersion":"stage06-repository-interpretation-registry-v1","artifactType":"STAGE06_REPOSITORY_INTERPRETATION_REGISTRY","artifactId":"repository-interpretation-registry:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":6,"module":"RepositoryInterpretationRegistryFreezer","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"registry-proposal-execution:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"registry-proposal-tasks:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","eligibleFlowSliceIds":["flow:post-depothead-batch-set-status"],"items":[{"provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","registryProposalId":"registry-proposal:depothead-batch-audit","flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","proposalKind":"BUSINESS_TERM","normalizedLabel":"批量审核或反审核","normalizedPurpose":"描述同一入口依据输入状态批量改变单据状态","basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"],"sourceSeedKey":null}],"flowDispositions":[{"registryProposalDispositionId":"registry-proposal-disposition:depothead-ready","flowSliceId":"flow:post-depothead-batch-set-status","disposition":"READY_FOR_FREEZE","registryProposalIds":["registry-proposal:depothead-batch-audit"]}],"proposalAccounting":{"registryProposalIds":["registry-proposal:depothead-batch-audit"],"provisionalKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"],"rejectedProposalIds":[],"closed":true},"organizationRegistrySeedRef":null,"closed":true}}
{"schemaVersion":"stage06-flow-task-set-v2","artifactType":"STAGE06_FLOW_TASK_SET","artifactId":"flow-task-set:4444444444444444444444444444444444444444444444444444444444444444","producer":{"stage":6,"module":"FiniteKeyFlowTaskCompiler","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"repository-interpretation-registry:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"artifactId":"stage05-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"5555555555555555555555555555555555555555555555555555555555555555"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"flowTaskSetId":"flow-task-set:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","eligibleR1R2FlowSliceIds":["flow:post-depothead-batch-set-status"],"tasks":[{"taskSpecId":"task:depothead-r1","taskKind":"FLOW_INTERPRETATION","flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","isolatedSessionKey":"session:depothead-r1r2","round":"R1","allowedKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]},{"taskSpecId":"task:depothead-r2","taskKind":"FLOW_INTERPRETATION_REVIEW","flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","isolatedSessionKey":"session:depothead-r1r2","round":"R2","allowedKeys":["TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]}],"taskShardReceipts":[{"shardId":"r1r2-task-shard:depothead","denominatorFlowSliceIds":["flow:post-depothead-batch-set-status"],"taskSpecIds":["task:depothead-r1","task:depothead-r2"],"status":"SUCCEEDED_WITH_GAPS","gapIds":["gap:runtime-status-policy"]}],"runtimePolicy":{"configuredAdapterId":"adapter:openai-responses-v1","configuredAuthMode":"HOST_ACCOUNT","expectedUpstreamProvider":"OPENAI","expectedModel":"gpt-5.6-luna","expectedReasoningEffort":"xhigh","expectedSandbox":"read-only","maxPreStartAttempts":3,"maxProviderWallClockMillis":120000}}}
{"schemaVersion":"stage06-model-execution-set-v2","artifactType":"STAGE06_MODEL_EXECUTION_SET","artifactId":"model-execution:5555555555555555555555555555555555555555555555555555555555555555","producer":{"stage":6,"module":"LifecycleBoundInterpretationRunner","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"flow-task-set:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"registry-proposal-execution:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"repository-interpretation-registry:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"executionSetId":"interpretation-execution:depothead-v2","flowTaskSetId":"flow-task-set:depothead-v2","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","rounds":[{"modelRoundId":"round:depothead-r1","taskSpecId":"task:depothead-r1","startedReceiptId":"started:depothead-r1"},{"modelRoundId":"round:depothead-r2","taskSpecId":"task:depothead-r2","startedReceiptId":"started:depothead-r2"}],"receipts":[{"receiptId":"receipt:depothead-r1","taskSpecId":"task:depothead-r1","startedEventOrdinal":2},{"receiptId":"receipt:depothead-r2","taskSpecId":"task:depothead-r2","startedEventOrdinal":3}],"interpretationProposals":[{"interpretationProposalId":"interpretation-proposal:depothead-batch-audit","registryProposalId":"registry-proposal:depothead-batch-audit","flowSliceId":"flow:post-depothead-batch-set-status","provisionalKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","selectedKey":"TERM_P_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","basisAtomIds":["atom:input-field","atom:value-source"],"basisGapIds":["gap:runtime-status-policy"],"r2Decision":"KEEP"}],"candidates":[{"candidateId":"flow-interpretation:depothead-v1","flowSliceId":"flow:post-depothead-batch-set-status","evidenceCapsuleId":"capsule:post-depothead-batch-set-status","r1RoundId":"round:depothead-r1","r2RoundId":"round:depothead-r2","interpretationProposalIds":["interpretation-proposal:depothead-batch-audit"]}],"flowDispositions":[{"flowSliceId":"flow:post-depothead-batch-set-status","disposition":"READY_FOR_ADMISSION","candidateId":"flow-interpretation:depothead-v1","gapIds":["gap:runtime-status-policy"],"failureRef":null,"reasonCode":null}],"slotStates":[{"taskSpecId":"task:depothead-r1","state":"STARTED_CONSUMED","attemptCount":1},{"taskSpecId":"task:depothead-r2","state":"STARTED_CONSUMED","attemptCount":1}],"shardReceipts":[{"shardId":"r1r2-execution-shard:depothead","denominatorFlowSliceIds":["flow:post-depothead-batch-set-status"],"dispositionFlowSliceIds":["flow:post-depothead-batch-set-status"],"status":"SUCCEEDED_WITH_GAPS"}],"callCounts":{"providerPreflightCount":2,"providerCallCount":2,"startedEventCount":2,"roundCount":2}}}
{"schemaVersion":"stage06-interpretation-publication-v2","artifactType":"STAGE06_INTERPRETATION_PUBLICATION","artifactId":"stage06-publication:6666666666666666666666666666666666666666666666666666666666666666","producer":{"stage":6,"module":"InterpretationArtifactPublisher","moduleVersion":"v2"},"upstreamArtifacts":[{"artifactId":"flow-task-set:4444444444444444444444444444444444444444444444444444444444444444","sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"artifactId":"model-execution:5555555555555555555555555555555555555555555555555555555555555555","sha256":"5555555555555555555555555555555555555555555555555555555555555555"},{"artifactId":"registry-proposal-execution:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"registry-proposal-tasks:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"repository-interpretation-registry:3333333333333333333333333333333333333333333333333333333333333333","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":"dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","registryProposalTaskSetId":"registry-task-set:depothead-v1","registryProposalExecutionSetId":"registry-execution:depothead-v1","repositoryInterpretationRegistryId":"interpretation-registry:depothead-v1","flowTaskSetId":"flow-task-set:depothead-v2","executionSetId":"interpretation-execution:depothead-v2","flowCount":1,"r0TaskSlotCount":1,"r1R2TaskSlotCount":2,"totalRoundCount":3,"providerCallCount":3,"coverage":{"eligibleFlowSliceIds":["flow:post-depothead-batch-set-status"],"r0ReadyFlowSliceIds":["flow:post-depothead-batch-set-status"],"r0GappedFlowSliceIds":[],"r0FailedFlowSliceIds":[],"r1r2EligibleFlowSliceIds":["flow:post-depothead-batch-set-status"],"r1r2GappedFlowSliceIds":[],"r1r2FailedFlowSliceIds":[],"registryProposalDispositionIds":["registry-proposal-disposition:depothead-ready"],"candidateFlowSliceIds":["flow:post-depothead-batch-set-status"],"gappedFlowSliceIds":[],"failedFlowSliceIds":[],"registryProposalTaskShardReceiptIds":["r0-task-shard:depothead"],"registryProposalExecutionShardReceiptIds":["r0-execution-shard:depothead"],"flowTaskShardReceiptIds":["r1r2-task-shard:depothead"],"interpretationExecutionShardReceiptIds":["r1r2-execution-shard:depothead"],"closed":true},"stageArtifactRoot":"stage-root:0606060606060606060606060606060606060606060606060606060606060606","publishedArtifacts":[{"path":"flow-interpretation-dispositions.jsonl","sha256":"0101010101010101010101010101010101010101010101010101010101010101"},{"path":"flow-model-tasks.jsonl","sha256":"0202020202020202020202020202020202020202020202020202020202020202"},{"path":"generation-receipts.jsonl","sha256":"0303030303030303030303030303030303030303030303030303030303030303"},{"path":"interpretation-candidates.jsonl","sha256":"0404040404040404040404040404040404040404040404040404040404040404"},{"path":"model-rounds.jsonl","sha256":"0505050505050505050505050505050505050505050505050505050505050505"},{"path":"registry-proposal-dispositions.jsonl","sha256":"0606060606060606060606060606060606060606060606060606060606060606"},{"path":"registry-proposal-rounds.jsonl","sha256":"0707070707070707070707070707070707070707070707070707070707070707"},{"path":"registry-proposal-tasks.jsonl","sha256":"0808080808080808080808080808080808080808080808080808080808080808"},{"path":"repository-interpretation-registry.json","sha256":"0909090909090909090909090909090909090909090909090909090909090909"},{"path":"stage-receipt.json","sha256":"1010101010101010101010101010101010101010101010101010101010101010"}],"nextStage":"07-admit-and-merge-business-knowledge"}}
~~~

### 8.1 Interface 与 records

~~~java
interface PerFlowInterpretationStage {
    Stage06Reference interpret(Stage05Reference flows,
                               OrganizationRegistrySeedRef organizationSeed,
                               ModelRuntimePolicy runtimePolicy);
}
~~~

~~~text
RegistryProposalTask
  taskSpecId, taskKind=R0_REGISTRY_PROPOSAL
  flowSliceId, evidenceCapsuleId, isolatedSessionKey
  inputJson/inputJsonSha256, outputSchemaSha256, promptBundleSha256, expectedRuntime

BusinessRegistryProposal
  registryProposalId, taskSpecId, flowSliceId, evidenceCapsuleId
  proposalKind=BUSINESS_TERM|CLAIM|QUESTION
  normalizedLabel, normalizedPurpose
  basisAtomIds[], basisGapIds[], sourceSeedKey?

RegistryProposalDisposition
  registryProposalDispositionId, flowSliceId
  disposition=READY_FOR_FREEZE|GAP|FAILED
  registryProposalIds[], gapIds[], failureRef?, reasonCode?

RepositoryInterpretationRegistryItem
  provisionalKey, registryProposalId, flowSliceId, evidenceCapsuleId, proposalKind
  normalizedLabel, normalizedPurpose
  basisAtomIds[], basisGapIds[], sourceSeedKey?

RepositoryInterpretationRegistry
  repositoryInterpretationRegistryId, eligibleFlowSliceIds[], items[], flowDispositions[]
  proposalAccounting, organizationRegistrySeedRef?, closed

FlowModelTask
  taskSpecId, taskKind, flowSliceId, evidenceCapsuleId, isolatedSessionKey
  round=R1|R2, allowedKeys[], input/schema/prompt hashes, expectedRuntime

InterpretationProposal
  interpretationProposalId, registryProposalId, flowSliceId
  provisionalKey, selectedKey, basisAtomIds[], basisGapIds[], r2Decision

FlowInterpretationCandidate
  candidateId, flowSliceId, evidenceCapsuleId
  r1RoundId, r2RoundId, interpretationProposalIds[]

FlowInterpretationDisposition
  flowSliceId, disposition=READY_FOR_ADMISSION|GAP|FAILED
  candidateId?, gapIds[], failureRef?, reasonCode?
~~~

`sourceSeedKey` required-nullable；非null时必须命中冻结seed entry且kind/label/purpose exact-match，仍要求Capsule basis非空。provisional key精确为 `<kind-prefix>_P_<lowercase hex SHA-256(canonical registry item material)>`，kind-prefix为TERM/CLAIM/QUESTION；hash material不含self key但含flow/capsule/proposal/basis/value/seed lineage。

### 8.2 R0、freeze、R1/R2 精确边界

- R0每Flow最多返回profile限定数量的proposal；raw response每项只允许`proposalKind,label,purpose,basisAtomIds,basisGapIds,sourceSeedKey`，extra field拒绝。程序完成Unicode/空白/长度/seed/basis校验后才发布`normalizedLabel`、`normalizedPurpose`，不得把未经验证的story值写入registry。
- registry freeze必须等待全部R0 dispositions；不同Flow不按label/purpose合并，也不把seed未被引用项自动纳入。
- R1只提交同Flow registry finite keys及basis；R2逐项覆盖R1，只能KEEP、NARROW、DROP、NEEDS_EVIDENCE，不能新增/遗漏/换identity/扩大basis。
- R0/R1/R2是三个Provider slots；R1/R2同属一个Flow product Candidate。ReaderCandidateRound是Stage08整份Candidate审阅，不能冒充第四次Flow解释。

### 8.3 Lifecycle、identity 与恢复

slot identity绑定run/flow/round/task/input/schema/prompt/runtime/budget。每slot必须先durable `THREAD_STARTED` ACK再读content；started后空/非法response、process failure、identity mismatch或后续stage failure都消耗slot。generation receipt绑定provider/preflight/attempt/started event，避免循环identity。

registry identity绑定完整terminal R0 set和canonical items；freeze前crash可从M1/M2重算，freeze后必须复用exact registry root。resume不得以组织seed变化、相似label或旧v1 R1/R2 artifacts迁移当前run。

### 8.4 预算和安全

Profile分别固定R0 tasks/response bytes/proposals/label codepoints+UTF-8 bytes/purpose codepoints+UTF-8 bytes/basis refs/registry items，以及R1/R2 tasks/response/proposals/总Provider wall-clock。超限不截断JSON或偷偷少Flow。

模型只读单Flow Capsule canonical JSON；无Path、文件工具、客户执行、网络source或secret。R0文本先按UTF-8 strict decode和NFC验证（输入已NFC则不做语义改写），拒绝C0/C1 controls、unpaired surrogate、bidi override/isolate、NUL和换行注入；值始终是quoted JSON data。prompt/system/schema不接受Capsule excerpt或seed改变指令优先级。

### 8.5 稳定 failure codes

STAGE06_INPUT_INVALID、FLOW_CAPSULE_SET_INVALID、CAPSULE_CLOSURE_BROKEN、REGISTRY_PROPOSAL_TASK_INVALID、REGISTRY_PROPOSAL_RESPONSE_INVALID、REGISTRY_PROPOSAL_REFERENCE_INVALID、REGISTRY_PROPOSAL_TEXT_INVALID、REGISTRY_SEED_MISMATCH、REGISTRY_FREEZE_INCOMPLETE、REGISTRY_IDENTITY_COLLISION、MODEL_TASK_INVALID、MODEL_TASK_HASH_MISMATCH、MODEL_RESPONSE_INVALID、MODEL_RESPONSE_IDENTITY_MISMATCH、MODEL_REFERENCE_INVALID、MODEL_REVIEW_NOT_CLOSED、MODEL_REVIEW_EXPANDED、MODEL_RUNTIME_IDENTITY_MISMATCH、PROVIDER_PREFLIGHT_FAILED_NO_START、PROVIDER_STARTED_EVENT_INVALID、PROVIDER_FAILURE_AFTER_START、PRESTART_ATTEMPTS_EXHAUSTED、FLOW_INTERPRETATION_UNAVAILABLE、INTERPRETATION_COVERAGE_BROKEN、STAGE06_RESOURCE_LIMIT_EXCEEDED。

Provider failure只有在durable lifecycle证明slot/started/no-start和Flow隔离闭合时，才成为perFlow GAP/FAILED；否则stage fatal。

### 8.6 测试 seam 与验收

- 0Flow→0R0/R1/R2 tasks/rounds/calls、一个0-item registry和十文件。
- 单Flow正常精确3slots/rounds/calls；双Flow正常精确6，并保持session/basis/key隔离。
- novel repo label可通过R0进入registry，但不得产生Fact/locator/Flow/Markdown；同label双Flow产生不同key。
- optional seed absent/present exact match均通过；未引用、值不等、缺Capsule basis均不得进入registry。
- R0 illegal Unicode/control/bidi/extra field/budget/ref失败；R1/R2 unknown或cross-flow key失败；R2扩张失败。
- 一Flow R0或R1/R2安全失败不改变其他Flow artifacts；started不重放，confirmed-no-start ceiling为3。
- 改shard size、并发完成顺序、restart point，最终registry/Stage06 bytes一致。
- 缺/重叠shard、partial freeze、count spoof、single-flow PASS冒充complete均fail closed。

只有`N R0 dispositions + 1 registry + N final dispositions`闭合，正常分支call count=3N且所有异常少调用有typed accounting，Stage06才可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- 仍只有Stage06可调用LLM；R0是内部模块，不是第九阶段。
- Stage05 EvidenceCapsule是R0/R1/R2唯一源码语义来源；组织seed仅optional hint且不能绕过R0/basis。
- R0只能创建registry proposal data；不得创建Fact、locator、Flow、Markdown或跨Flow知识。
- 全部R0终态后程序恰冻结一个registry；R1/R2只能使用同Flow provisional keys。
- 正常N Flow=3N slots/calls；不得为兼容旧2N设计压缩轮次。started/ambiguous永不重放。
- Stage07才做meaning admission，Stage08才做全仓九章Markdown；不得perFlow渲染或拼接。
- 任何字段、key identity、failure、model边界或跨stage lineage变更必须按DESIGN 13.11 STOP并交Sol/ultra Design Authority；跨阶段/业务目标由用户确认。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **部分具备（scripted R1/R2）** | 现有Stage03/04有perFlow finite-key R1/R2、runtime identity、lifecycle receipts和deterministic replay的bounded tests |
| **R0/freeze缺失** | 当前没有RegistryProposalTask、bounded open proposal validator、全仓RepositoryInterpretationRegistry freezer或proposal→provisionalKey lineage；现有2N主线不符合批准后的3N合同 |
| **真实DepotHead当前零调用** | Stage05仍为0Capsule，当前jshERP slice必须是0 R0/R1/R2 task/call；不能绕过Stage05让模型读Service |
| **artifact缺失** | 没有独立run/stages/06十文件或六个module artifact sets；live Provider不在本次授权内 |

本次只更新设计；上述差距是后续Luna/Terra实施项，不是修改当前Java/测试/JSON产物的许可。
