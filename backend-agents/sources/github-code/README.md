# GitHub Code Agent

目标是把一份不再变化的代码仓库整理成准确、可追溯、明确写出未知项的九章 Markdown 候选。程序负责证明源码事实和组织文档；模型只解释已经证明的一条局部业务流程。

当前代码还只是 POC。它能校验声明文件和摘录是否被改动、测试两种受控生成方式、稳定输出九个章节并保存归档；它还不能证明每句代码声明都由所指源码支持。固定 DepotHead 样例因此已被拒绝，没有可接受 Candidate。目标架构与 POC 能力必须分开阅读。

## 三条阅读路线

| 你想回答的问题 | 最短路线 | 能得到什么 |
| --- | --- | --- |
| 五分钟看懂长期目标 | 读[总体设计](DESIGN.md)第 1 节，再按需看第 2 节十二个模块 | 输入、输出、为什么不把全仓交给模型、程序/模型分工、Gap 和唯一目标数据流。 |
| 看一个真实成败案例 | 读[总体设计](DESIGN.md)第 4 节，再看[00 POC 实现记录](docs/stages/00-mvp.md)第 13 节 | DepotHead 的 3 文件、5 Evidence、5 LockedFact、1 Flow 为什么 hash/reference 通过却只有 2 Fact 通过、3 Fact 失败。 |
| 查当前工程实现 | 读[00 POC 实现记录](docs/stages/00-mvp.md)第 4–18 节，再看 src/main、src/test 和下面的 CLI | 真实 Interface、两条 POC 测试路径、Manifest/R1/R2/identity/archive 合同、命令、测试和缺口。 |

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
| HTTP、远程 Git、自动 Flow 编译 | NOT IMPLEMENTED | 目标能力，不能从总体设计推断可用 |

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
