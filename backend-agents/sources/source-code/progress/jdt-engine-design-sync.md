# Progress: jdt-engine-design-sync

- Status: COMPLETE
- Agent role: 唯一设计者；JDT-first 实施计划与权威设计同步
- Model: gpt-6-astra / ultra
- Started: 2026-09-12T16:26:05Z
- Last updated: 2026-09-12T16:47:43Z
- Scope: 新建 JDT 优先、JavaParser 后适配实施计划；最小同步四份 Java 引擎设计及其直接权威说明；建立基线 `cec1997` 的 JavaParser 迁移前能力验收清单。仅文档。
- Approved inputs: 用户已批准“JDT 优先、JavaParser 后适配”两阶段实施计划；补充批准 Task 1 可从配置与中立 Interface 开始，任务覆盖 1.2–1.9 与 2.1–2.2，并使用 `## Task N`、Files/RED/GREEN/Verification 结构。
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` / `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`，HEAD `cec1997`

## Completed

- 完整读取 `AGENTS.md`、`progress/TEMPLATE.md` 与 `docs/modules/java-code-engines/` 下四份设计。
- 确认修改前基线与并行工作树状态；保留其他 Agent 的 `progress/jdt-java-engine-implementation.md` 修改。
- 新建 `docs/plans/jdt-first-java-engine-implementation-plan.md`：以可抽取的十个 `## Task N` 覆盖获批 1.2–1.9、2.1–2.2；每项具有精确 Files/RED/GREEN/Verification，阶段一独立于 JavaParser。
- 将 Step02 overload identity、独立 JDT Core helper、`jdt-syntax-v1` 生命周期、PROGRAM_GRAPHS module 7、Step03/04 actual set、Step05 context-first 与版本合同同步到四份引擎设计及直接权威文档。
- 建立 git `cec1997` JavaParser 迁移前能力清单，记录七个生产 owner 的 blob ID、行为 oracle 与目标测试 selector；明确本设计任务未运行测试。
- 明确复用现有 storage、CLI、Agent、Application、Activity、Process、Report 和 runtime seam；未将其列为重写对象。

## Current state

- 文档设计与只读检查已完成；未修改 Java、测试、schema resource 或运行产物，未运行模型/provider、客户构建、测试或 git commit。

## Changed files

- `docs/plans/jdt-first-java-engine-implementation-plan.md`（新增）
- `docs/modules/java-code-engines/README.md`
- `docs/modules/java-code-engines/contracts-and-configuration.md`
- `docs/modules/java-code-engines/integration-and-javaparser.md`
- `docs/modules/java-code-engines/jdt-engine.md`
- `docs/analysis-steps/02-application-discovery.md`
- `docs/analysis-steps/03-program-graphs.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/DESIGN.md`
- `docs/references/inherited-public-and-module-contracts.md`
- `AGENTS.md`
- `progress/jdt-engine-design-sync.md`（本文件）

未触碰共享工作树中另一 Agent 的 `progress/jdt-java-engine-implementation.md`。

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | 修改前为空；随后仅观察到其他 Agent 的 `progress/jdt-java-engine-implementation.md` |
| `git rev-parse --short HEAD` | PASS | `cec1997` |
| 本地 Markdown 链接存在性检查（12 个变更/新增文档） | PASS | 检查 80 个本地链接，`broken_local_links=0` |
| 新增锚点/任务结构检查 | PASS | Task 1–10 各含 Files/RED/GREEN/Verification；`5.1` actual-set 与 JavaParser baseline 锚点存在 |
| `git diff --check` | PASS | exit 0，无 whitespace error |
| 新增文件 trailing-whitespace 检查 | PASS | `git diff --no-index --check` 无诊断；exit 1 仅表示 `/dev/null` 与新增文件不同 |
| 测试/构建 | NOT RUN (by scope) | 用户要求只建立验收清单，不运行测试；亦未运行客户代码 |

## Decisions

- 第一阶段只实现 JDT，第二阶段 JavaParser 只恢复基线现有能力；不合并阶段。
- Step02 entry wire 统一升级：M2/M4 moduleVersion v3，HTTP entry draft/public descriptor/line v3；每个 entry 的 framed identity 与 payload 保存 `methodKey + methodRange`，其余 discovery schema 保持 v2。
- JDT Core helper 固定源码目录 `tools/jdt-syntax-helper/src/main/java/org/sourceanalysis/tools/jdtsyntax/`，产物 `tools/jdt-syntax-helper/target/source-code-analysis-jdt-syntax-helper.jar`，tool JDK 启动，JDT Core 3.47.0/Jackson 2.21.4，严格 `jdt-syntax-v1` JSONL 与 timeout/exit failure codes。
- `java-code-index` 固定为 `(PROGRAM_GRAPHS, 7, "java-code-index")`；JDT Step03/04 的 actual semantic set 分别为 index-only 与 accounting-only，receipts 如实列 actual descriptors；Step04 NOT_PRODUCED counts 为 null。
- Step05 context-first，目标 versions 沿用设计表；实际五个 semantic files 不因 strict enhancement 缺失而缩小。Writer/reader/exact-set allowlist/policy/fixture 必须原子同步。
- 不派生子 Agent；不运行测试、模型/provider、客户构建或 Git commit。

## Blockers

- 无。

## Exact next action

- 根 Agent 审阅本 diff；通过后按计划 Task 1 开始配置与中立 Interface 的 Luna RED。

## Resume checks

- 重新读取本文件、`git status --short` 与 `git rev-parse --short HEAD`。
- 确认不修改 Java、测试、schema 资源或运行产物。
