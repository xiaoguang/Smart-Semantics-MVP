# 04 证明代码事实

> 总体设计权威：[GitHub Code Agent 总体设计](../DESIGN.md)。

## 1. 为什么存在

程序图说明“解析到了哪些 node/edge”，但一个业务事实往往由多段关系共同成立。例如“批量设置状态会把请求 status 写入 jsh_depot_head.status”至少涉及 HTTP 参数、Service 数据流、entity property、Mapper binding、XML statement、表和列。

本阶段先枚举完整 Fact 的 required atoms，再逐 atom 建 Proof。任何 required atom 不闭合，整条复合 Fact 都不准入；已证明的 sibling atom 也不能拼成一条语义残缺的事实。

## 2. 具体输入与 DepotHead 例子

输入是 persisted snapshot、capability report、五张 graph、Fact/Gap profiles、schema/tool hashes 和预算。

> Walkthrough 示例声明 — **TARGET_ILLUSTRATIVE_NOT_CURRENT_OUTPUT**：本文件用连贯目标值展示 candidate→Proof/Gap artifact 接力，不把当前 DepotHead rejection 改写成 success；技术 unknown 只用 nullable/UNRESOLVED/Gap/fatal 表达。

DepotHead 的 **REAL_SOURCE** 在 Controller :43,:178-191、Service :741-822、Mapper Java :23、DepotHead :63,:301-307、Example :149-151 和 Mapper XML :3,:70-93,:385-497。

目标候选 Fact：

~~~text
kind: PERSISTED_STATUS_UPDATE
required atoms:
  ENTRY_ROUTE = POST /depotHead/batchSetStatus
  INPUT_FIELD = status
  SERVICE_HANDLER = DepotHeadService.batchSetStatus
  ELIGIBLE_ID_SET = dhIds
  MAPPER_METHOD = DepotHeadMapper.updateByExampleSelective
  TABLE = jsh_depot_head
  COLUMN = status
  VALUE_SOURCE = request status
  WHERE_KEY = id
  WHERE_OPERATOR = IN
~~~

这是 candidate denominator，不是当前 admitted Fact。当前五图/dataflow 不足时，正确输出是 rejection/Gap。

## 3. 程序怎样工作

1. 按版本化 Fact registry 枚举所有 candidate Facts 及 required atoms，先固定分母。
2. 为 atom 选择明确 role：KIND、ATTRIBUTE、CONDITION、LITERAL 或 RELATIONSHIP。
3. 从 Evidence graph 取 source nodes，从结构/call/control/data graphs 取 required edges 和 rule applications。
4. 重开 snapshot，重验 file/span SHA、parsed node identity 和 edge endpoints。
5. 构造无环 Proof：Fact/atom identity 先于 Proof identity。
6. 仅当复合 candidate 的所有 required atoms 都 CLOSED 时生成 CodeFact。
7. 根因 atom 使用具体 rejection code；同一复合 Fact 的 sibling 使用 COMPOSITE_FACT_REJECTED。
8. 生成 capability/fact/expectation Gaps，重算 accounting，原子安装 Stage 04。

## 4. 生成的可观察产物

| 文件 | 唯一职责 |
| --- | --- |
| proven-facts.json | admitted CodeFacts、atoms 和 candidate dispositions |
| proof-pack.json | Proof nodes/edges/rules 与逐 atom CLOSED proofs |
| gap-ledger.json | capability gaps、fact rejections、expectation gaps |
| fact-accounting.json | candidate/admitted/rejected Fact/atom 分母与守恒方程 |
| stage-receipt.json | upstream graph roots、control hashes、artifact set |

目标 DepotHead output 可以诚实是：

~~~json
{
  "candidateFactKey": "DEPOTHEAD_STATUS_PERSISTENCE",
  "disposition": "REJECTED_WITH_REASON",
  "reasonCode": "DATA_FLOW_BINDING_UNPROVEN",
  "missingAtomKeys": ["VALUE_SOURCE", "WHERE_KEY", "WHERE_OPERATOR"]
}
~~~

在未来五图闭合后，同一 candidate 才可能成为 ADMITTED_WITH_PROOF；不得预先改成 success 示例。

目标 Stage 04 出口始终包含五个命名文件和非空 candidate/accounting 记录。`proven-facts.json` 可以有 0 个 admitted Fact，但不能没有 candidate dispositions；对当前 DepotHead，至少要留下 `DEPOTHEAD_STATUS_PERSISTENCE` rejection、缺失 atom 和对应 Gap，保证“没有证明成功”本身可观察、可追因。

### 4.1 人类 walkthrough：模块用什么文件接力

为了展示完整目标故事，下面采用“未来五图已闭合”的分支；当前实现仍按本章前述 rejection/Gap 输出。

~~~jsonl
{"module":"FactCandidateEnumerator","artifact":"modules/01-candidates/fact-candidate-set.json","takesFrom":["Stage02Reference","Stage03Reference"],"says":{"candidateKey":"DEPOTHEAD_STATUS_PERSISTENCE","entryId":"entry:post-depothead-batch-set-status","requiredAtoms":["ENTRY_ROUTE","INPUT_FIELD","SERVICE_HANDLER","ELIGIBLE_ID_SET","MAPPER_METHOD","TABLE","COLUMN","VALUE_SOURCE","WHERE_KEY","WHERE_OPERATOR"]}}
{"module":"AtomicProofBuilder","artifact":"modules/02-proofs/proof-decision-set.json","takesFrom":["fact-candidate-set.json","five graphs","verified source"],"says":{"factId":"fact:depothead-status-persistence","decision":"ADMITTED_WITH_PROOF","closedAtomCount":10,"statusValuePath":"request status→jsh_depot_head.status","wherePath":"request ids→WHERE id IN"}}
{"module":"FactLedgerPublisher","artifact":"modules/03-publish/stage04-publication.json","takesFrom":["fact-candidate-set.json","proof-decision-set.json"],"says":{"admittedFactIds":["fact:depothead-status-persistence"],"remainingGapIds":["gap:runtime-status-policy"],"publicFiles":["proven-facts.json","proof-pack.json","gap-ledger.json","fact-accounting.json","stage-receipt.json"]}}
~~~

`fact:depothead-status-persistence` 只在十个 atom 都闭合时出现；当前少 VALUE/WHERE 证明时，M2 改为 rejection并保留相同 candidate key，M3 不能为了让 Stage05 有 Flow 而伪造 factId。

## 5. 下游怎样消费而不返工

Stage 05 只读取 proven-facts.json、proof-pack.json、gap-ledger.json、fact-accounting.json 和五图 roots。它按 factId/atomId/proofId 引用，不能：

- 借用另一个 Fact 的 span；
- 使用 Manifest 中“附近”但未引用的 Evidence；
- 把模型一致、candidateId 或 Trace locator 当 Proof；
- 在 Flow 编译时重新发明 Fact kind。

Stage 06 的 Capsule 只携带可读投影；完整 ProofPack 仍留在程序侧。

### 下游前置条件与后置保证

| Stage 05 开始前必须成立 | Stage 04 成功后保证 |
| --- | --- |
| Stage 03 五图 roots、Evidence/source reopen 与 Stage 04 controls 完全一致 | 每个 candidate Fact 和 required atom 恰一条 disposition，分母/accounting 不缩水 |
| 五个 Stage 04 files 及 receipt root 重验闭合 | 每个 admitted Fact 的每个 atom 都有独立 CLOSED Proof；rejected candidate 有根因与 closure requirement |
| 无 conflicting admitted Facts、断 Proof 或 source drift | Stage 05 只需按 IDs 消费 Facts/Proof/Gaps，不需要重新判定事实 |

Stage 05 遇到未准入 candidate、REJECTED atom 或 expectation Gap，只能据此形成 Flow Gap/coverage；不能在流程编译时升级为 Fact。

## 6. 成功、Gap、fatal 与恢复

- **成功**：允许同时有 admitted Facts、rejections 和 Gaps；全部 dispositions/accounting 闭合，阶段目录原子安装。
- **带 Gap 成功**：Fact 无法唯一证明、profile 外语义或受控静态 absence；Gap 必须写缺什么、影响什么、如何关闭。
- **fatal**：Proof source/edge/reference 漂移、duplicate canonical ID、CLOSED Proof 缺 required node/edge、conflicting Facts 同时 admitted、accounting 不守恒或 artifacts 不一致。
- **恢复**：重验全部 graph roots 和 Stage 04 artifact root；相同 controls 才继续。fatal 不删除 Stage 01–03。

## 7. 程序与模型责任

| 责任 | 程序 | LLM |
| --- | --- | --- |
| 枚举 candidate Fact/atoms | 是，版本化 registry | 否 |
| 构造/验证 Proof | 是 | 否 |
| 决定 admitted/rejected | 是 | 否 |
| 把未知补成事实 | 否，写 Gap | 否 |
| 业务命名 | 否，留到 Stage 06 | 否 |

产品运行时模型调用数固定为 0。

## 8. 技术合同

### 8.0 固定模块合同

模块执行顺序固定为 `FactCandidateEnumerator` → `AtomicProofBuilder` → `FactLedgerPublisher`。每一步先安装自己的 canonical module artifact，下一步只读该 artifact；不得靠共享 mutable collections 传 candidate、Proof 或 Gap。

#### M1 FactCandidateEnumerator

- **解决的问题**：在看证明结果前先冻结应该尝试证明的 Fact/required atom 分母，防止失败项消失。
- **精确上游输入及前置**：valid Stage02 entry/capability refs、Stage03 five-graph/index/Gap refs、版本化 Fact registry/profile/budget；graph roots/controls 已重验。
- **确定性顺序 / LLM**：按 entry/site/registry key 排序 → 匹配适用 Fact templates → 实例化 subject/required atoms/roles/expected evidence kinds → 记录不适用 reason；0 LLM。
- **目标输出与 DepotHead 示例**：`FactCandidateSet{candidateFacts,requiredAtoms,denominator,sourceGraphRoots}`；例子含 `DEPOTHEAD_STATUS_PERSISTENCE` 和 route/input/service/table/column/value/where 十个 atom keys。
- **必须保持的不变量**：candidate key+subject 唯一；每种 Fact kind 的 required atoms 完整且版本化；枚举不受后续 Proof 成败影响。
- **Gap / fatal / 恢复**：profile 外但可定位的 candidate 标成 CapabilityGap；registry/schema/reference/accounting 冲突 fatal；恢复重放相同 graphs/profile，不复用未安装 draft。
- **给下游的后置保证**：M2 得到不可变 candidate/atom IDs、required evidence/edge roles 和完整 denominator，无权删减。
- **明确非目标**：不选择具体 proof path、不 admission Fact、不解释业务名称。
- **公共测试 seam 与验收**：`enumerate(entries, graphIndex, factRegistry)` 覆盖 DepotHead template、registry deletion、同名 decoy、输入乱序；Proof 删除不能改变枚举 bytes/counts。
- **Luna/xhigh 测试指南**：创建 `Stage04FactCandidateEnumeratorTest`，冻结Stage02/03 artifacts、Fact registry和手写candidate golden于 `src/test/resources/target/stage04/candidate-enumerator/`。逐RED：DepotHead十atom denominator、registry不适用、同名decoy、unsupported Gap、graph order determinism、Proof deletion不改分母；首RED因seam/schema缺失。只fakeartifact reader，registry matching/canonical不可mock。命令：`mvn -Dtest=Stage04FactCandidateEnumeratorTest test`；禁网络/客户运行。偏离按DESIGN 13.11。
- **Terra/xhigh 实现指南**：RED后只改 `target/stage04/candidateenumerator/`，实现 public `FactCandidateEnumerator/FactCandidateSet` 与 `stage04-fact-candidate-set-v1`；只读Stage02/03 artifacts，registry match→instantiate atoms→denominator。逐RED GREEN，Proof结果不得反向影响枚举；禁止fixture硬编码/改required atoms。上游或registry语义不足MUST STOP交Sol/ultra，完成更新审计。

#### M2 AtomicProofBuilder

- **解决的问题**：逐 atom 证明 source bytes 与 semantic graph/rule path都闭合，并执行 composite all-or-nothing admission。
- **精确上游输入及前置**：M1 candidate artifact、Stage03 five graphs/evidence、Stage01 source handles、Proof rule registry/budget；candidate IDs、graph endpoints、source roots 完全一致。
- **确定性顺序 / LLM**：按 candidate/atom key → 解析 required graph/evidence roles → 重开 span/hash → 建无环 proof closure → 先得 atom dispositions → 再按 all-atoms rule 得 Fact disposition；0 LLM。
- **目标输出与 DepotHead 示例**：`ProofDecisionSet{proofs,codeFacts,factDispositions,atomDispositions,rootCauseRejections}`；当前例 VALUE_SOURCE/WHERE atoms 为 `DATA_FLOW_BINDING_UNPROVEN`，整个 Fact rejected；未来全闭合才有 factId。
- **必须保持的不变量**：一个 admitted atom 恰一个 CLOSED Proof；Fact 任一 required atom失败则无 admitted sibling；Proof refs 只指 M1/Stage03/Stage01 identities。
- **Gap / fatal / 恢复**：可解释的不闭合是 rejection/Gap；source/edge/reference drift、proof status伪 CLOSED、conflicting admitted facts fatal；恢复重建全部 candidate decisions，不混用旧 proof。
- **给下游的后置保证**：M3 获得每个 candidate/atom 的唯一 disposition、closed proofs 或具体根因，能直接守恒计数。
- **明确非目标**：不把 evidence locator、Trace、模型或文本相似当 Proof，不生成 Flow。
- **公共测试 seam 与验收**：`prove(candidateSet, graphs, source, rules)` 对十个 DepotHead atoms逐段 deletion、source/rule mutation、conflict和正向 closure；任一缺项绝不 admission composite Fact。
- **Luna/xhigh 测试指南**：创建 `Stage04AtomicProofBuilderTest`，fixtures/goldens放 `src/test/resources/target/stage04/atomic-proof/`。一个行为一个RED：十atom全闭合正例、每段deletion rejection、source/rule drift fatal、sibling all-reject、conflicting facts、root replay；expected Proof paths独立手写。只fake source reopen，禁止mockgraph traversal/Proof/canonical。命令：`mvn -Dtest=Stage04AtomicProofBuilderTest test`；无网络/模型。异常RED按13.10，偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅拥有 `target/stage04/atomicproof/`，实现 public `AtomicProofBuilder/ProofDecisionSet` 与 `stage04-proof-decision-set-v1`；M1+Stage03+source→role match→reopen→Proof→atom→composite decision。逐atom GREEN再composite GREEN；不得借Evidence/模型/Trace或保留sibling。需改Fact/graph跨stage contract则MUST STOP并交用户流程，审计同步。

#### M3 FactLedgerPublisher

- **解决的问题**：把 Proof decisions、Gaps 与分母汇成 Stage 04 公共 artifact set，并验证没有 orphan/silent loss。
- **精确上游输入及前置**：M1 candidate artifact、M2 proof decision artifact、capability/expectation gaps、Stage01–03 roots、Stage04 controls；所有 IDs/references 局部有效。
- **确定性顺序 / LLM**：归类 CapabilityGap/FactRejection/ExpectationGap → 计算 Fact/atom equations → conflict/reference validation → canonical 五文件 → atomic install；0 LLM。
- **目标输出与 DepotHead 示例**：五个 exact files；当前例 `proven-facts.json` 保存 rejected disposition、`gap-ledger.json` 保存 missing value/where requirements、receipt 记录 admittedFactCount=0。
- **必须保持的不变量**：每 candidate/atom 恰一 disposition；Gap 有 affected IDs/missing/impact/closure；stage public files引用同一 M1/M2 roots，publisher不改 decision。
- **Gap / fatal / 恢复**：合法 rejection/Gaps 可 SUCCEEDED_WITH_GAPS；orphan/double disposition、accounting/reference/canonical/install/collision 错误 fatal；恢复只认完整 receipt。
- **给下游的后置保证**：Stage05 能只读 Facts/Proof/Gaps/accounting，并确定哪些 atom可进入 Flow、哪些必须留 Gap。
- **明确非目标**：不重新证明、不把 rejection 升级、不调用模型、不选择 Flow ownership。
- **公共测试 seam 与验收**：`publishCandidatesAndProofs(candidateSet, decisions, gaps)` 覆盖 orphan/double owner/count-equal-but-ID-different、乱序和 crash；只有 ID-set equations 全闭合返回 Stage04Reference。
- **Luna/xhigh 测试指南**：创建 `Stage04FactLedgerPublisherTest`，module artifacts/goldens在 `src/test/resources/target/stage04/fact-ledger-publisher/`。RED顺序：current DepotHead rejection five-file set、positive admitted set、orphan/double disposition、count spoof/ID mismatch、Gap closure fields、乱序/crash/collision；首RED因publisher缺失。只mockartifact-store fault，不能mockaccounting/canonical。命令：`mvn -Dtest=Stage04FactLedgerPublisherTest test`；禁网络/customer Maven。偏离按13.11。
- **Terra/xhigh 实现指南**：RED后仅改 `target/stage04/factledgerpublisher/`，实现 public `FactLedgerPublisher/Stage04Reference` 与 `stage04-fact-publication-v1`；只读M1/M2 artifacts，classify gaps→ID-set equations→five files→atomic. 每slice GREEN，Stage05只凭reference可用；不得重新prove/升级rejection。任何field/accounting跨stage改动MUST STOP交Sol/ultra/用户，完成更新审计。

### 8.0.1 模块 artifact wire schemas

使用 DESIGN 13.3 envelope；`!`=required non-null，`?`=required nullable。

| artifact | schemaVersion / artifactType | 精确 upstream | payload/排序 |
| --- | --- | --- | --- |
| `modules/01-candidates/fact-candidate-set.json` | `stage04-fact-candidate-set-v1` / `STAGE04_FACT_CANDIDATE_SET` | Stage02+Stage03 IDs/SHAs | `candidateSetId!`、`graphIds!`、`candidates[]!{candidateFactKey!,entryId!,kind!,subjectNodeIds[]!,requiredAtoms[]!{atomKey!,role!,valueType!,expectedEvidenceKinds[]!}}`、`denominator!`；candidates按key，atoms按registry order |
| `modules/02-proofs/proof-decision-set.json` | `stage04-proof-decision-set-v1` / `STAGE04_PROOF_DECISION_SET` | M1+Stage01+Stage03 IDs/SHAs | `candidateSetId!`、`codeFacts[]!`、`atomProofs[]!{atomId!,proofId!,status!,requiredEvidenceNodeIds[]!,requiredProgramEdgeIds[]!,ruleIds[]!}`、`factDispositions[]!`、`atomDispositions[]!`、`rootCauseRejections[]!`；各数组按ID/key，Fact内atoms按registry order |
| `modules/03-publish/stage04-publication.json` | `stage04-fact-publication-v1` / `STAGE04_FACT_PUBLICATION` | M1+M2 IDs/SHAs | `stageStatus!`、`admittedFactIds[]!`、`rejectedCandidateKeys[]!`、`gapIds[]!`、`stageArtifactRoot!`、`publishedArtifacts[5]!`、`nextStage!`；ID arrays排序，files按path |

atom value使用8.1 typed union；静态unknown不允许story value，必须使atom disposition=`REJECTED_WITH_REASON`并引用Gap。一个candidate admitted时其requiredAtoms与atomProofs一一对应；rejected candidate的`admittedFactId` required nullable为null。success envelope failureRef=null；integrity fatal写ModuleFailure。field/atom registry/source rule/sort/identity变化先设计并升version。

~~~jsonl
{"schemaVersion":"stage04-fact-candidate-set-v1","artifactType":"STAGE04_FACT_CANDIDATE_SET","artifactId":"fact-candidates:1111111111111111111111111111111111111111111111111111111111111111","producer":{"stage":4,"module":"FactCandidateEnumerator","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"stage02-publication:4444444444444444444444444444444444444444444444444444444444444444","sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"artifactId":"stage03-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"candidateSetId":"candidate-set:depothead-v1","graphIds":{"codeStructure":"graph:code-structure-depothead","call":"graph:call-depothead","controlFlow":"graph:control-flow-depothead","dataFlow":"graph:data-flow-depothead","evidence":"graph:evidence-depothead"},"candidates":[{"candidateFactKey":"DEPOTHEAD_RUNTIME_STATUS_POLICY","entryId":"entry:post-depothead-batch-set-status","kind":"RUNTIME_POLICY_EXPECTATION","subjectNodeIds":["method:service-batch-set-status"],"requiredAtoms":[{"atomKey":"RUNTIME_POLICY","role":"ATTRIBUTE","valueType":"ENUM_REF","expectedEvidenceKinds":["RUNTIME_ATTESTATION"]}]},{"candidateFactKey":"DEPOTHEAD_STATUS_PERSISTENCE","entryId":"entry:post-depothead-batch-set-status","kind":"PERSISTED_STATUS_UPDATE","subjectNodeIds":["method:service-batch-set-status","column:jsh-depot-head-status"],"requiredAtoms":[{"atomKey":"ENTRY_ROUTE","role":"KIND","valueType":"STRING","expectedEvidenceKinds":["CODE_STRUCTURE","EVIDENCE"]},{"atomKey":"INPUT_FIELD","role":"ATTRIBUTE","valueType":"SYMBOL_REF","expectedEvidenceKinds":["DATA_FLOW","EVIDENCE"]},{"atomKey":"SERVICE_HANDLER","role":"RELATIONSHIP","valueType":"SYMBOL_REF","expectedEvidenceKinds":["CALL","EVIDENCE"]},{"atomKey":"ELIGIBLE_ID_SET","role":"CONDITION","valueType":"SYMBOL_REF","expectedEvidenceKinds":["CONTROL_FLOW","DATA_FLOW","EVIDENCE"]},{"atomKey":"MAPPER_METHOD","role":"RELATIONSHIP","valueType":"SYMBOL_REF","expectedEvidenceKinds":["CALL","EVIDENCE"]},{"atomKey":"TABLE","role":"ATTRIBUTE","valueType":"SYMBOL_REF","expectedEvidenceKinds":["CODE_STRUCTURE","EVIDENCE"]},{"atomKey":"COLUMN","role":"ATTRIBUTE","valueType":"SYMBOL_REF","expectedEvidenceKinds":["CODE_STRUCTURE","DATA_FLOW","EVIDENCE"]},{"atomKey":"VALUE_SOURCE","role":"RELATIONSHIP","valueType":"SYMBOL_REF","expectedEvidenceKinds":["DATA_FLOW","EVIDENCE"]},{"atomKey":"WHERE_KEY","role":"ATTRIBUTE","valueType":"SYMBOL_REF","expectedEvidenceKinds":["DATA_FLOW","EVIDENCE"]},{"atomKey":"WHERE_OPERATOR","role":"LITERAL","valueType":"ENUM_REF","expectedEvidenceKinds":["DATA_FLOW","EVIDENCE"]}]}],"denominator":{"candidateFactKeys":["DEPOTHEAD_RUNTIME_STATUS_POLICY","DEPOTHEAD_STATUS_PERSISTENCE"],"candidateAtomKeys":["COLUMN","ELIGIBLE_ID_SET","ENTRY_ROUTE","INPUT_FIELD","MAPPER_METHOD","RUNTIME_POLICY","SERVICE_HANDLER","TABLE","VALUE_SOURCE","WHERE_KEY","WHERE_OPERATOR"]}}}
{"schemaVersion":"stage04-proof-decision-set-v1","artifactType":"STAGE04_PROOF_DECISION_SET","artifactId":"proof-decisions:2222222222222222222222222222222222222222222222222222222222222222","producer":{"stage":4,"module":"AtomicProofBuilder","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"fact-candidates:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"stage01-publication:3333333333333333333333333333333333333333333333333333333333333333","sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},{"artifactId":"stage03-publication:6666666666666666666666666666666666666666666666666666666666666666","sha256":"3333333333333333333333333333333333333333333333333333333333333333"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"candidateSetId":"candidate-set:depothead-v1","codeFacts":[{"factId":"fact:depothead-status-persistence","kind":"PERSISTED_STATUS_UPDATE","subjectNodeIds":["method:service-batch-set-status","column:jsh-depot-head-status"],"atomIds":["atom:column","atom:eligible-id-set","atom:entry-route","atom:input-field","atom:mapper-method","atom:service-handler","atom:table","atom:value-source","atom:where-key","atom:where-operator"]}],"atomProofs":[{"atomId":"atom:column","proofId":"proof:column","status":"CLOSED","requiredEvidenceNodeIds":["evidence:status-column-span"],"requiredProgramEdgeIds":["structure-edge:table-declares-status"],"ruleIds":["sql-column-declaration-v1"]},{"atomId":"atom:eligible-id-set","proofId":"proof:eligible-id-set","status":"CLOSED","requiredEvidenceNodeIds":["evidence:dhids-guard"],"requiredProgramEdgeIds":["cfg-edge:guard-true-update"],"ruleIds":["java-if-cfg-v1"]},{"atomId":"atom:entry-route","proofId":"proof:entry-route","status":"CLOSED","requiredEvidenceNodeIds":["evidence:controller-route"],"requiredProgramEdgeIds":["structure-edge:controller-route"],"ruleIds":["spring-route-merge-v1"]},{"atomId":"atom:input-field","proofId":"proof:input-field","status":"CLOSED","requiredEvidenceNodeIds":["evidence:request-status"],"requiredProgramEdgeIds":["data-edge:request-to-service-status"],"ruleIds":["java-argument-binding-v1"]},{"atomId":"atom:mapper-method","proofId":"proof:mapper-method","status":"CLOSED","requiredEvidenceNodeIds":["evidence:xml-statement"],"requiredProgramEdgeIds":["call-edge:mapper-xml","call-edge:service-mapper"],"ruleIds":["java-static-receiver-call-v1","mybatis-namespace-signature-binding-v1"]},{"atomId":"atom:service-handler","proofId":"proof:service-handler","status":"CLOSED","requiredEvidenceNodeIds":["evidence:controller-call"],"requiredProgramEdgeIds":["call-edge:controller-service"],"ruleIds":["java-static-receiver-call-v1"]},{"atomId":"atom:table","proofId":"proof:table","status":"CLOSED","requiredEvidenceNodeIds":["evidence:table-update"],"requiredProgramEdgeIds":["structure-edge:statement-table"],"ruleIds":["sql-update-table-v1"]},{"atomId":"atom:value-source","proofId":"proof:value-source","status":"CLOSED","requiredEvidenceNodeIds":["evidence:status-column-span"],"requiredProgramEdgeIds":["data-edge:property-to-placeholder","data-edge:record-status-to-column","data-edge:request-to-service-status","data-edge:service-status-to-property"],"ruleIds":["cross-layer-value-flow-v1"]},{"atomId":"atom:where-key","proofId":"proof:where-key","status":"CLOSED","requiredEvidenceNodeIds":["evidence:controller-call","evidence:eligible-dhids","evidence:id-criterion","evidence:id-where"],"requiredProgramEdgeIds":["data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:request-ids-to-service-ids","data-edge:service-ids-to-eligible-dhids"],"ruleIds":["mybatis-example-criterion-v1"]},{"atomId":"atom:where-operator","proofId":"proof:where-operator","status":"CLOSED","requiredEvidenceNodeIds":["evidence:controller-call","evidence:eligible-dhids","evidence:id-criterion","evidence:id-where"],"requiredProgramEdgeIds":["data-edge:eligible-dhids-to-criterion","data-edge:ids-to-where","data-edge:request-ids-to-service-ids","data-edge:service-ids-to-eligible-dhids"],"ruleIds":["sql-in-operator-v1"]}],"factDispositions":[{"candidateFactKey":"DEPOTHEAD_RUNTIME_STATUS_POLICY","disposition":"REJECTED_WITH_REASON","admittedFactId":null,"reasonCode":"RUNTIME_ATTESTATION_REQUIRED"},{"candidateFactKey":"DEPOTHEAD_STATUS_PERSISTENCE","disposition":"ADMITTED_WITH_PROOF","admittedFactId":"fact:depothead-status-persistence","reasonCode":null}],"atomDispositions":[{"atomKey":"RUNTIME_POLICY","disposition":"REJECTED_WITH_REASON","proofId":null,"reasonCode":"RUNTIME_ATTESTATION_REQUIRED"},{"atomKey":"COLUMN","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:column","reasonCode":null},{"atomKey":"ELIGIBLE_ID_SET","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:eligible-id-set","reasonCode":null},{"atomKey":"ENTRY_ROUTE","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:entry-route","reasonCode":null},{"atomKey":"INPUT_FIELD","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:input-field","reasonCode":null},{"atomKey":"MAPPER_METHOD","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:mapper-method","reasonCode":null},{"atomKey":"SERVICE_HANDLER","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:service-handler","reasonCode":null},{"atomKey":"TABLE","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:table","reasonCode":null},{"atomKey":"VALUE_SOURCE","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:value-source","reasonCode":null},{"atomKey":"WHERE_KEY","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:where-key","reasonCode":null},{"atomKey":"WHERE_OPERATOR","disposition":"ADMITTED_WITH_PROOF","proofId":"proof:where-operator","reasonCode":null}],"rootCauseRejections":[{"candidateFactKey":"DEPOTHEAD_RUNTIME_STATUS_POLICY","atomKey":"RUNTIME_POLICY","reasonCode":"RUNTIME_ATTESTATION_REQUIRED","gapId":"gap:runtime-status-policy"}]}}
{"schemaVersion":"stage04-fact-publication-v1","artifactType":"STAGE04_FACT_PUBLICATION","artifactId":"stage04-publication:3333333333333333333333333333333333333333333333333333333333333333","producer":{"stage":4,"module":"FactLedgerPublisher","moduleVersion":"v1"},"upstreamArtifacts":[{"artifactId":"fact-candidates:1111111111111111111111111111111111111111111111111111111111111111","sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"artifactId":"proof-decisions:2222222222222222222222222222222222222222222222222222222222222222","sha256":"2222222222222222222222222222222222222222222222222222222222222222"}],"controls":{"toolchainSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","profileSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb","schemaBundleSha256":"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc","promptBundleSha256":null},"completion":{"status":"SUCCEEDED_WITH_GAPS","gapRefs":["gap:runtime-status-policy"],"failureRef":null},"payload":{"stageStatus":"SUCCEEDED_WITH_GAPS","admittedFactIds":["fact:depothead-status-persistence"],"rejectedCandidateKeys":["DEPOTHEAD_RUNTIME_STATUS_POLICY"],"gapIds":["gap:runtime-status-policy"],"stageArtifactRoot":"stage-root:0404040404040404040404040404040404040404040404040404040404040404","publishedArtifacts":[{"path":"fact-accounting.json","sizeBytes":900,"sha256":"1111111111111111111111111111111111111111111111111111111111111111"},{"path":"gap-ledger.json","sizeBytes":1100,"sha256":"2222222222222222222222222222222222222222222222222222222222222222"},{"path":"proof-pack.json","sizeBytes":3000,"sha256":"3333333333333333333333333333333333333333333333333333333333333333"},{"path":"proven-facts.json","sizeBytes":2000,"sha256":"4444444444444444444444444444444444444444444444444444444444444444"},{"path":"stage-receipt.json","sizeBytes":1200,"sha256":"5555555555555555555555555555555555555555555555555555555555555555"}],"nextStage":"05-compile-business-flows"}}
~~~

### 8.1 Interface 与 records

~~~java
interface CodeFactProver {
    Stage04Reference prove(Stage03Reference graphs);
}
~~~

~~~text
CodeFact
  factId
  kind
  subjectNodeIds[]
  atoms[]

FactAtom
  atomId
  role
  name
  value {type, canonical}
  proofId

Proof
  proofId
  factId
  atomId
  rootEvidenceNodeId
  requiredEvidenceNodeIds[]
  requiredProgramEdgeIds[]
  ruleIds[]
  status=CLOSED

FactDisposition
  candidateFactKey
  atomKey
  disposition
  admittedFactId?
  proofId?
  reasonCode?

Gap
  gapId
  kind
  code
  affectedEntryIds[]
  affectedCandidateFactKeys[]
  evidenceRefs[]
  missingRequirement
  impact
  closureRequirement
~~~

Atom value type 只允许 STRING、INTEGER、DECIMAL、BOOLEAN、SYMBOL_REF 或 ENUM_REF。任意 prose 不进入 atom。

### 8.2 Proof 与 identity

无环顺序：

1. graph/evidence node/edge IDs 已由 Stage 03 固定；
2. atomId 依赖 snapshot、candidateFactKey、role/name/value；
3. factId 依赖 snapshot、kind、subject nodes 和排序 atoms，不含 proofId；
4. proofId 依赖 factId、atomId、Evidence/program-edge closure 和 rule IDs；
5. proofPackId、provenFactSetId、gapLedgerId、accountingId 最后计算。

Proof 必须同时证明 source bytes 和 semantic rule path。file/span hash 正确只证明完整性，不自动证明语义。

### 8.3 Accounting

~~~text
candidateFactCount = admittedFactCount + rejectedFactCount
candidateAtomCount = admittedAtomDispositionCount + rejectedAtomCount
provenFactAtomCount = admittedAtomDispositionCount
~~~

一个复合 Fact 任一 atom 失败时，其所有 atoms 都是 REJECTED_WITH_REASON；不得把可独立证明的 sibling 留成孤立 admitted atom。

### 8.4 Gap 分类

- CapabilityGap：parser/profile 无法确定某个 site。
- FactRejection：candidate atom/Fact 的 Proof 不闭合。
- ExpectationGap：版本化 profile 声明应检查的问题，经 bounded search 没有静态答案。

Gap 不是负面业务事实，也不是 placeholder。它必须保存 searched scope 或缺失 requirement。

### 8.5 预算、安全与 failure codes

预算覆盖 candidate Facts、atoms、Proof nodes/edges、reopen bytes、rule applications、Gaps 和 conflict set。超限不能缩小 candidate denominator。

只读 Stage 03 artifacts 和 verified source handles；不运行客户代码或模型。所有 locator/path/hash 由程序产生。

稳定 code：

FACT_PROFILE_INVALID、FACT_KIND_UNSUPPORTED、REQUIRED_ATOM_MISSING、DATA_FLOW_BINDING_UNPROVEN、COMPOSITE_FACT_REJECTED、PROOF_PACK_REFERENCE_BROKEN、PROOF_PROGRAM_EDGE_BROKEN、PROOF_SOURCE_REOPEN_MISMATCH、PROOF_NOT_CLOSED、CONFLICTING_FACTS、FACT_ACCOUNTING_INVARIANT_BROKEN、STAGE04_RESOURCE_LIMIT_EXCEEDED。

### 8.6 测试 seam 与验收

- 先枚举 denominator，再 mutation route prefix、Service call、setter/property、Mapper binding、XML table/column/where 任一段。
- 删除 Fact 自己引用的 span/edge 必须 rejection；另一个 Fact 的 Evidence 不能补。
- 同名 decoy、注释字符串和目标 JSON 不得形成 Proof。
- Proof source span、graph endpoint、rule ID、fact/atom reference mutation fatal。
- conflicting canonical Fact 双方都不 admitted。
- 等价 root/input order 产生相同 records/IDs/canonical bytes。
- fixed DepotHead slice 在通用能力补齐前继续输出 Gap/rejection，而不是硬编码 positive golden。

验收要求同时有：完整 composite Fact 正例、每个 required atom 的独立 deletion/mutation 反例、conflicting Fact、不同 root replay，以及当前 DepotHead rejection baseline。只有 positive case 全 atoms CLOSED 才 admitted，任一反例都不会留下 sibling admitted atom，五个 artifacts 可独立重验，Stage 04 才算可交付。

### 8.7 已冻结裁决：实现者不得自由推断

- candidate Fact/required atoms 来自版本化 registry，并在证明前固定；实现者不能为某 fixture 临时删 atom 或新增硬编码 Fact。
- 复合 Fact 是全有或全无：任一 required atom 不闭合，整条 Fact 及 sibling dispositions 都 rejected。
- span hash、graph edge 和 rule path 三者共同构成 Proof；locator、Trace、模型一致或字符串相似都不能替代。
- Gap 必须有 missing requirement、impact 和 closure requirement；不能把 Gap 写成负面业务事实或空 placeholder。
- conflicting Facts、accounting、reference 或 source-integrity 问题是 fatal，不能降为“低置信度”Fact。
- Proof 内部数据结构和 rule engine 实现可自行选择；Fact registry/version、atom roles、identity 顺序、admission 与失效规则不得改变。

## 9. 当前实现差距审计

| 状态 | 当前事实 |
| --- | --- |
| **已验证（有限 profile、内存态）** | 现有 Stage01 M3 有 CodeFact、ProofPack、GapLedger、逐 atom accounting 与 mutation tests |
| **真实 DepotHead 仍有缺口** | 当前 Fact registry 和 generic dataflow 不足以证明完整 status→table column 与 ids→where chain；旧 POC 5 个 LockedFact 只有 2 个通过独立审计 |
| **尚未符合目标** | Stage04 artifacts 尚未作为独立 run stage 立即持久化；Fact prover 仍依赖当前 RepositoryModel/FlowView shape，而非五个一等图文件 |
| **设计裁决** | 目标要求补通用五图/dataflow/Fact profile，不能复制旧 LockedFact、借未引用 Evidence 或把 current Gap 改写成 success |

Stage 04 的成功允许有 Gap；但只有 admitted-with-Proof 的 Fact 才能进入 Stage 05 Flow。
