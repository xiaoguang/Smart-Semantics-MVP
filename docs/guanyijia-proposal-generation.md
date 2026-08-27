# 管伊佳证据提案生成：首个安全 seam

`scripts/evidence/guanyijia-generate.mjs` 当前仅提供可注入、无副作用的首个
Task 3 seam：`createProposalGeneration` 与 `validateProposalCitations`。导入模块
不会读取环境、文件或网络，不会启动 Codex，也不会执行模型、Git、数据库或 LLM。

生成操作要求 dependencies own 的 plain-record `env` data-property snapshot；缺失、
`null`、array、string、prototype/object-accessor 形态或不能稳定枚举的 snapshot 都
在 auth 前拒绝，绝不以环境空对象作为 fallback。该 snapshot 随 construction 冻结，
并先拒绝任何 API/provider credential 环境变量，再要求 injected auth port 精确报告
ChatGPT 登录。两项检查都发生在 raw manifest、raw artifact 和 model adapter 之前。
`CAPTURE_REQUEST` 只是 Task 2 的 metadata-only 预检输出，不是
`RawSourceSnapshotManifest`；它在 artifact/model 路径前以
`CAPTURE_OUTPUT_REQUIRED` 拒绝。

`validateProposalCitations` 有两个互斥的精确输入形状。旧的
`{ rawManifest, proposal, readArtifact }` 形状保留给首个单 manifest seam：它接受
schema-version 1 的 self-digested raw snapshot manifest 与 `FILE_LINES` observed
locator。Observed citation 的 identity 是 `artifactRef + exact locator`；proposal
内重复 identity 在任何 raw read 前拒绝。它验证 manifest SHA-256、artifact SHA-256、
artifactRef、完整 locator 和由验证 UTF-8 bytes 按 inclusive line range 提取的 excerpt
逐字节相等。它不 trim、大小写折叠、Unicode normalization 或模糊匹配。

严格 pure 形状为 `{ proposal, admittedSources, resolvedCitations,
invocationEnvelope }`，其 pinned schema 位于
`scripts/evidence/schemas/guanyijia-proposal.schema.json`。它不接收 reader、路径或
adapter，因此不会做 I/O。该形状只接受固定顺序的三个真实根源
`guanyijia_mysql`、`guanyijia_github`、`guanyijia_official_docs`；每个 source
proposal 的 source/snapshot identity 必须和 admitted source 相等。

`invocationEnvelope` 是与 model proposal 分离的完整 private admission object。它有
自己的 canonical self-digest，且精确固定 `CODEX_CLI`、`codex-cli
0.148.0-alpha.15`、Luna/xhigh、ChatGPT session、read-only/no-tools/never-approval/
ephemeral policy 与一次尝试。它的 inputs 必须逐字段等于 admitted sources；raw-manifest
digest、citation-material digest 与 prompt-input digest 都从 canonical material 重新导出，
不能只比较两个 untrusted digest 字段是否相同。

固定生成指令是已提交的 UTF-8 文件
`scripts/evidence/prompts/guanyijia-proposal-instructions.txt`。它的审查 pin 为
`sha256:c32a5cfb1340bf8fd6bfb262e3d396352e1be3da6f156e7ea9e4f65c09ea4691`；这不是版本
标签或可由调用方提供的值。proposal schema 实际固定文件 bytes 以及 Codex CLI 二进制也
各有静态 trust pin。未来的 effectful CLI composition root 必须在构造 envelope 前核验
实际 prompt/schema/binary bytes；此 pure validator 则从不读取这些文件，任何字节或 pin
变化都必须经审查同时更新。

`promptInputSha256` 的 canonical packet 使用独立的
`kind: GUANYIJIA_PROPOSAL_PROMPT_INPUT` domain，并固定包含 `schemaVersion`、`storyKey`、
`runId`、prompt-template SHA、schema SHA、完整 admitted sources、citation-material SHA
与完整 resolved citation material，随后对 recursively sorted JSON 加一个 LF 求 SHA-256。
所以它必然不同于仅对 citation material 求得的 digest，且会绑定实际交给固定指令的来源和
引用输入。envelope 必须逐项匹配这些重算值。proposal 仅能回显这个已验证 envelope 的
`envelopeSha256`，不能用 source-only formula 自行建立 invocation authenticity。

纯 validator 在每一层拒绝未知字段：proposal、admitted source、source proposal、
statement、value、affected-object reference、citation、resolved tuple 和 locator 都是
白名单形状。它支持且只支持完整的 `FILE_LINES`、`SOURCE_SYMBOL`、`SQL`、
`DOCUMENT_SECTION` locator。proposal ID 与 evidenceRef 全局唯一；每个 observed
citation 必须按 source、snapshot、artifact、digest、locator 和 excerpt 精确等于唯一
independently resolved tuple，且不存在 duplicate 或 unused resolution。`USER_CONFIRMED`
不属于输入 enum；generated target 仍只能是明确的 pending `GAP`/`PENDING_ASSET`。
这只证明引用来自已入场的证据，不证明 prose conclusion、完整性或可发布性。

同一底层 resolved tuple（不含 `evidenceRef` alias）也只能出现一次，不能用不同 ref
重复计数。全为 valid generated-target statements 的三源 proposal 可以提供空
`resolvedCitations`；只要存在 observed citation，resolved tuple 与 observed evidence
就必须为完整双射。runtime 同时实施 pinned schema 的 65,536 字符上限与每个 source
最多 200 statements、每个 statement 最多 200 evidence/affected refs 的上限，避免
schema/runtime 出现不同 acceptance language。字符串上限按 JSON Schema 的 Unicode code
point 计数（不是 JavaScript UTF-16 code unit）；一个 astral emoji 计为一个字符。

Generated target 不是观察到的事实，也不读取 raw artifact。它只允许 statement
明确为 `semanticKind: GAP | PENDING_ASSET`、`evidenceStatus: GAP`、
`provenance: INFERRED`，并使用唯一的
`{ kind: GENERATED_TARGET, confirmationStatus: PENDING_HUMAN_CONFIRMATION }`
locator。target citation 不能携带 source/snapshot/artifact 等 raw identity，且不能
和 observed evidence 混合；任意其他状态、confirmation label 或字段都会在 raw
read 前拒绝。每个 generated `evidenceRef` 在整个 proposal 内（同一或不同
statement）必须唯一，重复值同样在 raw read 前拒绝。

`FILE_LINES` extraction 以 verified UTF-8 bytes 的 `0x0a` 进行分行。开头的
UTF-8 BOM (`EF BB BF`) 会作为 U+FEFF 数据保留，不会被 decoder 吞掉；只有 LF
separator 被移除。无效 UTF-8 仍会 fail closed。

两个入口都只接受 own、enumerable、data-property 的 plain records/arrays；symbol、
non-enumerable、accessor、prototype 与 descriptor-trap payload 在任何 reader 或 adapter
前以新建的单行 domain error 拒绝，且 discriminator accessor 不会执行。legacy form 会在 shape admission 后
clone manifest/proposal，adapter 在 await 期间不能改变已验证结果；pure form 也返回
validated clone。legacy async validation 在同一个 public sanitizing boundary 内 await：
即使 expected-field Proxy getter 在内部阶段抛出，也会重建阶段固定的 domain error，绝不
泄漏原始 message。legacy form 的所有 I/O 都由 caller 注入的 adapter 提供；pure form 没有 I/O。
两者都不会把 locator 作为文件路径、SQL、shell 参数或网络请求。当前实现不提供
CLI、package script、真实 Codex composition root 或 live generation。真实 capture
output admission、catalog/index resolver construction、pinned Codex/auth protocol、
immutable proposal/run-manifest writer、publication 和 public Bundle projection 仍是
后续 Task 3 slices；不得把该模块用于 live generation。

所有 injected adapter boundary 都会按调用阶段重新构造 fixed allowlisted
`ProposalGenerationError`。它们绝不重抛 adapter 提供的 Error、subclass、code 或
message；因此外来 newline、ANSI、path 或 secret 不能穿过 public seam。

## Controlled Codex executor (fake-port-only)

`createControlledCodexExecutor` 是唯一新增的 execution seam。factory 精确接收 own
data-property `{ env, executionAdmission, codexSession }`：`executionAdmission` 只暴露
`admit(...)`，`codexSession` 只暴露 `run(...)`。导入模块、严格 pure citation validation，
以及 factory construction 都不会调用任一 port。此模块不导入 child-process 或 filesystem，
不提供 production adapter、CLI entry 或 live execution。

Factory 先对 dependency/port own data descriptors 做一致的安全 snapshot：受控 property
read 必须与已枚举 descriptor 值一致，Proxy getter、accessor、隐藏字段、symbol、prototype 或
任何 snapshot 异常都在 public factory boundary 重建为 fixed single-line domain error。每一个
execution-admission result 在取用字段前也只作一次 descriptor-safe deep clone；clone 失败或
后续 shape 不精确即拒绝。executor 从不把 port 返回对象保留到 await 之后。

每一次 operation 都必须提交精确的
`{ authorization, invocationEnvelope, admittedSources, resolvedCitations }`。authorization
不是 boolean 或任意字符串：它必须是
`{ kind: CHATGPT_GENERATION_AUTHORIZATION, flag: --authorize-chatgpt-generation,
expectedGenerationInputSha256 }`，并在任何 port 前精确等于重算后的 admitted
`promptInputSha256`。缺失、重复、错字、extra key、credential/provider env（包括空值）或
恶意 descriptor payload 都 fail closed；输入材料在第一个 await 前 clone/freeze。

授权后 executor 会将完整 envelope、三源顺序、source/citation material、raw/citation/
prompt/schema digest domain 与 envelope self-digest 通过既有 private admission 复核。它随后
经 admission port 取得受控 binary/prompt/schema/cwd/staging facts，并重新 hash prompt/schema
bytes，要求分别匹配静态 reviewed pin。cwd 必须是不同于 schema/staging target 的绝对、空
`0700` directory；child env 只传递 nonblank `CODEX_HOME`、`PATH`、`TMPDIR` 和可选
`LANG`/`LC_ALL`，不复制任何其他 ambient key。

所有受控 path 必须是 canonical absolute POSIX lexical spelling：root 以外不接受空 segment、
`.`、`..` 或重复 slash。因此 `/x/y` 和 `/x/./y` 既不能作为同一个 schema/last-message
target 混用，也不能绕过 cwd/schema/staging 的 semantic-distinctness 判断。required child env
value 还必须含有至少一个 non-whitespace character；仅空格、tab 或 newline 同样拒绝。

session port 仅可接收同一 absolute executable、cwd、allowlisted child env、`shell: false`
和 stdin transport。它的无 retry 固定顺序是 `--version`、`login status`、一次 `exec`、
再一次 `login status`；version 与两次 auth 都以 exact UTF-8 bytes 比较，不 trim 或 regex。
`exec` 固定为 Luna/xhigh/openai/ChatGPT/read-only/never-approval/ephemeral/no-tools policy，
禁用 inventory 固定排序，prompt 与证据材料只存在唯一 canonical stdin packet，绝不进入 argv。
stdin、stdout、stderr、last-message、timeout 和 audit event 都有固定上限。last-message
fatal-UTF-8 JSON proposal 必须通过 strict pure citation validation，且 post-auth 仍为 exact
ChatGPT mode 才会返回 cloned proposal、canonical proposal SHA 和最小 execution facts。

每个 session call 会收到新建的 stdin byte copy；executor 保留自己的 immutable logical
stdin digest，并在 exec 返回后比对 port-side copy。port 若修改传入 buffer，立即以 execution
failure 停止，不会使用被改写的数据计算 returned digest 或继续 post-auth。

所有 port throw、malformed port result、timeout/signal/nonzero、version/auth drift、overflow、
audit mismatch、JSON parse 或 proposal/citation failure 都重新构造 allowlisted single-line
`ProposalGenerationError`；原始 path、argv、prompt/stdin、stdout/stderr、last-message、stack 或
secret 不会向 caller 泄漏。该 seam 仍没有真实 spawn、Codex、network、raw reader、capture、Git、
database、writer 或 publication 行为。
