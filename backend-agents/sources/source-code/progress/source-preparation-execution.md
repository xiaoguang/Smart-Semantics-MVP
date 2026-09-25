# Progress: 源码准备实施统筹

- Status: IN_PROGRESS
- Agent role: direction, integration review and debug
- Model: Astra / ultra
- Started: 2026-09-25
- Last updated: 2026-09-25
- Scope: prepare-source、结构化问题、不可变刷新/排除、必要公共来源检查、模块内 Skill；无真实业务生成
- Owning plan: docs/plans/source-preparation-implementation-plan.md，按用户最新批准的实施表执行
- Approved inputs: 既有设计、程序和临时 fixtures；历史运行只读
- Current branch/worktree: 正式 source-code checkout，codex/source-preparation；启动 HEAD 5597c4a52d0f7ba276585ff2206c650ff2d2323a

## Completed

- 已读取适用 AGENTS、计划及详细设计；核对当前工作区存在上一轮 Activity 未提交补丁。
- 受保护 more-findings.md SHA256：59b8381e7e81e3105ed6c6a8d93ce1dbea0247735bf1e6227d8f842b0d1d7f8e。
- git ls-remote 实际确认 origin/main=5ceb11115edd23e64d464b8a41b2e3caad27b651；本地已有14个历史计划提交领先，未尝试reset或替换。
- 已在当前HEAD建立独立分支；保存原工作区binary diff到忽略的本轮工作目录。
- 已批准源码准备设计独立提交为 c939cf7；未提交 Activity 补丁未纳入该提交。

## Current state

- 步骤0和1完成；步骤2目录/Git捕获开始；步骤3–8未完成。
- 现有源码准备仅Git路径；新 prepare-source、结构化结果和持久排除尚未实现。
- 已有 Activity 源码/测试/设计改动属于前一计划，不撤销、不擅自混入本轮提交。
- Luna负责步骤1测试，Terra负责合同实现；先冻结最小API，RED后实施。
- 步骤1记录、请求、来源版本和SelectedSourceBasis已实现，两组32项直接测试通过，5项合同审查问题全部修正并复核；尚无可调用准备CLI。

## Changed files

- 本进度文件；后续本轮增量以独立任务报告登记。

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short / rev-parse | 已核对 | 正式 checkout，存在既有未提交改动 |
| shasum -a 256 docs/supplements/more-findings.md | 已记录 | 59b8381e…0d1d7f8e |
| /usr/libexec/java_home -V | 已核对 | JDK17应用工具链、JDK26可作21+质量宿主 |
| 三类旧直接测试（未clean） | 测试编译失败，未执行测试 | javap显示旧RepositoryRunConfiguration.class直接抛Unresolved compilation problem且缺record访问器；源码record仍完整 |
| clean + LocalGitCommitCaptureAdapterTest,VerifiedSourceInventoryPublicationSpecifierTest,PersistedVerifiedSourceTextReaderTest | PASS，12 tests / 0 failures / 0 errors | canonical pom，40.807秒；日志在.workspace/source-preparation-implementation-20260925/baseline-direct.log |
| 固定Step05状态校验值 | 已记录 | 968fc339c5dbd876ce16e7ca9a26ce78e406ad37ae2c4135ca55a069c4a09bb5 |
| SourcePreparationContractsTest（新增，实施前） | RED，exit 1 | 新合同类型尚不存在，测试编译拒绝；task-1-red.log；未混入旧代码测试失败 |
| SourcePreparationContractsTest（首批实现） | 9 executed / 8 errors | SourceIssue构造期Set.copyOf后的contains(null)触发NPE，已定位并让实现方修复 |
| SourcePreparationContractsTest（边界扩展） | 18 executed / 4 failures / 1 error | 原13项通过；新增未知排除、未枚举范围、未处理条目、继承目录身份、.git普通文件计数暴露边界问题，task-1-boundary-red.log |
| SourcePreparationContractsTest（边界修复后） | PASS，18 / 0 failures / 0 errors | task-1-green-2.log；canonical Maven；34.396秒 |
| 请求/版本/来源绑定第二批（实施前） | RED，exit 1 | 生产编译成功；新SourcePreparationRequest、SelectedSourceBasis及身份计算类型尚缺，task-1-second-red.log；已交Terra实现 |
| 第二批实施后，两组合计 | PASS，27 / 0 failures / 0 errors | task-1-second-green-2.log；20.983秒。此前两处测试fixture误用非hex字符已修正，未放宽生产校验 |
| 首批新文件定向Spotless格式化 | PASS，14 files / 7 changed / 7 already clean | JDK26宿主，绝对路径正则限定新增合同和首批测试；未格式化旧Activity补丁 |

## Decisions

- 遵循用户固定正式目录的要求，在该 checkout 建独立分支，不创建另一编辑目录。
- 测试仅本轮新增/直接覆盖范围；静态质量阶段跳过UT/IT；一个重型构建。
- 提前实现 SelectedSourceBasis 合同和真实准备 controls；CLI最后接线，不留反向依赖。
- 产品内容生成 none；JDT、Activity、Process真实执行均为0。
- 内部检查记录与readiness评估分开，正式wire只写生产者计算的一份结果；资源中止、已排除未知子目录、已刷新历史问题分别测试。
- 审查修复第1轮：补目录/链接/submodule计数与raw/summary未命中排除列表；修正.git未知政策范围、ABORTED、括号glob和排除条目编码。根节点首批签名遗漏的汇总维度按正式合同补齐，不转嫁为未来CLI临时计算。

## Blockers

- 无。基线构建产物问题已通过清理生成物解决；没有改动业务源码。

## Exact next action

- Luna将已裁决目录读取测试草稿移入正式src/test，root确认RED后派新Terra完成目录流式读取；随后固定Git读取。同一时间一个重型构建。Task1最终证据：task-1-fix-2-green.log（32/0/0，33.065秒）及task-1-fix-2-rereview.md（spec/quality PASS）。

## Resume checks

- 先读本文件、任务进度和git状态，不重复派发已完成任务。
- 核对受保护文件校验值和既有Activity差异，不reset/clean工作区。

## Plan closeout destinations

- Durable decisions: docs/modules/source-preparation/ 和主设计。
- Remaining issues: docs/supplements/implementation-lessons-and-followups.md。
- Verification and output references: 本轮源码准备交付记录。
