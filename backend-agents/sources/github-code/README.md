# GitHub Code Agent

目标是把一份不再变化的代码仓库整理成准确、可追溯、明确写出未知项的九章 Markdown 候选。程序用完整 ProofPack 证明源码事实，再把更小的非重叠 EvidenceCapsule 交给模型，编译一条入口根 Flow 及其 Outcome，并用固定模板组织事实句；模型只为已经证明的局部流程选择冻结 BusinessTermRegistry 中的 term key 和有限 claim key。没有 eligible 业务术语时，程序使用 total TechnicalDisplayRegistry，而不是接收开放模型命名。

当前代码还只是 POC。它能校验声明文件和摘录是否被改动、测试两种受控生成方式、稳定输出九个章节并保存归档；它还不能证明每句代码声明都由所指源码支持。固定 DepotHead 样例因此已被拒绝，没有可接受 Candidate。目标架构与 POC 能力必须分开阅读。

## 建议阅读顺序

1. 先看[垂直主线](DESIGN.md#2-垂直主线从冻结源码到可审阅候选)，确认冻结源码怎样依次形成可审阅 Candidate，以及安全/资源门禁为什么横切全链。
2. 再看[M1–M8 完整 walkthrough](DESIGN.md#3-八个深模块的端到端-walkthrough)，沿同一个合成“库存预留”仓库检查每个模块的输入、算法、模型角色、输出、失败和测试。
3. 然后看[可行性证明与 AssuranceLedger](DESIGN.md#4-可行性证明与可重算-assuranceledger)，区分能力包络内的程序保证、受支持模型解释和静态代码不能证明的政策。
4. 接着直接读[完整九章 Markdown 结果](DESIGN.md#5-synthetic-样例的完整九章-markdown-结果)，确认内部 ID/SHA/transport 字段没有泄漏到业务正文，五项未证政策留在“待确认事项”。
5. 最后查[当前 POC 成熟度矩阵](DESIGN.md#9-附录-b当前-poc-成熟度矩阵)和[00 POC 实现记录](docs/stages/00-mvp.md)，了解哪些目标能力仍是 PARTIAL、POC_ONLY 或 NOT_IMPLEMENTED，以及 DepotHead 为什么 2 个 Fact 通过、3 个失败。

九章名称、顺序和“恰好一次”的共同合同见[共享 NineSectionProfile](../../../shared/source-agent-contracts/README.md)。progress/ 只记录任务恢复状态，不替代设计。

## 当前 POC 能力

| 能力 | 状态 | 边界 |
| --- | --- | --- |
| 人工 Manifest 文件/Evidence 完整性 | IMPLEMENTED | 校验 size/SHA、行段 SHA 与 known IDs；只证明 hash/reference integrity，不是远程 Git capture |
| Fact→Evidence semantic closure | NOT IMPLEMENTED | 不逐 atom 检查 Fact 自身声明 span；有效 SHA/ID 不足以准入 |
| provider-free baseline | IMPLEMENTED（执行路径） | 不调用模型；缺 semantic closure 与语义原子守恒，不能据此准入当前样例 |
| recorded R1/R2 | IMPLEMENTED（单 Flow 执行路径） | 同一 Candidate 内解释/精度复核；不调用 live model，也不能补足 Fact 证据 |
| 九章渲染 | PARTIAL | 固定标题和字节确定性；不等于读者内容充分 |
| 八文件归档 | IMPLEMENTED | 原子安装、逐字节幂等、冲突拒绝 |
| archived validate | PARTIAL | 主要检查布局、sidecar identity、正文 SHA、九标题 |
| archived Trace | PARTIAL | 读取存档 locator；不重开冻结源码重验 hash |
| `inspect`/`discover` | DIAGNOSTIC（01 输入） | 未冻结目录输出，不得进入 Candidate/Markdown/正式 Trace |
| `SourceAnalyzer` CodeFact seam | PARTIAL（02 输入） | 无 CLI、Proof 或 generation 接线 |
| runtime receipt admission | PARTIAL（04 输入） | 独立 seam，未接 generate/CLI；Provider 身份字段待拆分 |
| future local-loopback HTTP Adapter、自动 Flow 编译 | NOT IMPLEMENTED | 本分析 core 的目标能力，不能从总体设计推断当前可用；不承诺远程 HTTP 服务 |
| 远程 Git Capture | OUTSIDE AGENT CORE | 必须由另一个显式授权的上游 Capture workflow/Adapter 固定 revision 并交付离线 `FrozenRepositoryRequest`；不是当前或目标 M1–M8 的网络能力 |

标准 MyBatis mapper DOCTYPE 可以解析，但外部 DTD、entity、schema 和网络解析全部禁用。模块不执行客户 Maven、插件、测试、脚本、应用、SQL 或 MyBatis runtime。

## 六个 CLI

以下命令只描述可复现的现有 Interface。调用方必须在生成前另行完成 Fact semantic Evidence audit；当前 core 不会代为阻断语义不闭合的 Manifest，固定 DepotHead 样例不得用于这些命令的当前验收。

构建 shaded JAR：

```bash
mvn -q -DskipTests package
java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar --help
```

用模板路径生成 recorded Candidate：

```bash
java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar generate \
  --manifest <flow-manifest.json> \
  --snapshot-root <frozen-source-root> \
  --workspace <new-or-empty-workspace> \
  --recorded-r1 <r1.json> \
  --recorded-r2 <r2.json>
```

生成 provider-free baseline：

```bash
java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar baseline \
  --manifest <flow-manifest.json> \
  --snapshot-root <frozen-source-root> \
  --workspace <new-or-empty-workspace>
```

只读 archived Candidate：

```bash
java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar validate \
  --workspace <workspace> \
  --candidate-id <candidate-id-from-candidate.json>

java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar trace \
  --workspace <workspace> \
  --candidate-id <candidate-id-from-candidate.json> \
  --item-key <trace-item-key>
```

目录诊断：

```bash
java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar inspect \
  --repository-root <local-source-root>

java -jar target/github-code-to-markdown-0.1.0-SNAPSHOT.jar discover \
  --repository-root <local-source-root>
```

`inspect` 和 `discover` 不读取 Manifest/commit/file SHA，也不冻结 locator。它们的 JSON 只能用于诊断；正式准入必须重新绑定并校验 Manifest/Evidence。

## 归档文件

首次成功归档后的 workspace 恰好包含：

```text
document.md
candidate.json
evidence-pack.json
r1-interpretation.json
r2-precision-review.json
trace-index.json
generation-receipt.json
validation-receipt.json
```

`validate` 检测正文篡改时只写 invalid receipt，不修复 Markdown。`validation-receipt.json` 当前会被后续验证替换，不是追加 lineage。

## 固定 POC 样例

本地忽略工作区保留 jshERP commit `8c30ce7861570458920175e200bb2a6442713580` 的 DepotHead 小样：3 个文件、5 个 Evidence、5 个 LockedFact、1 个 Flow。文件/摘录哈希与 known-ID references 有效，但逐 atom 审计只有 2 个 Fact 通过、3 个失败，整个 Flow semantic closure 失败。该样例已降级为拒绝输入，不得用于当前验收、Candidate 内容依据或 Reader Selection；详见 [00-mvp](docs/stages/00-mvp.md)。

`.workspace/mvp-depothead-baseline-v2/` 是 Renderer 源码变化前的历史产物，不代表当前源码或当前验收，禁止把它作为发布依据或在普通文档工作中重新生成。

## 直接测试

```bash
mvn -q -Dtest=ManifestEvidenceVerificationTest,InterpretationAdmissionGateTest,NineSectionRenderingDeterminismTest,DeterministicBaselineGenerationTest,CandidateArchivePersistenceTest,CodeMdCliPersistenceTest,CodeMdCliValidateTest,TraceLocatorTest,RepositoryDiscovererTest,CodeMdCliDiscoveryTest,CodeFactAnalyzerTest,ModelRuntimeReceiptAdmissionTest test
```

这些测试只使用合成 fixture 或 recorded provider，不联网、不调用模型、不执行客户项目。
